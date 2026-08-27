package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.hipparchus.util.MathUtils;
import org.orekit.attitudes.FrameAlignedProvider;
import org.orekit.control.heuristics.lambert.LambertBoundaryConditions;
import org.orekit.control.heuristics.lambert.LambertBoundaryVelocities;
import org.orekit.control.heuristics.lambert.LambertDifferentialCorrector;
import org.orekit.control.heuristics.lambert.LambertSolver;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * The translunar injection of a lunar mission: a patched-conic seed from a parking orbit, aimed so
 * the <em>flown</em> perilune reaches a target altitude (MIS-4 / L6, spec {@code
 * docs/lunar-flyby/08-conception-L6.md} §4).
 *
 * <p><b>The geometry {@link #parkingState} builds needs no launch window.</b> The transfer plane is
 * derived from where the Moon <em>will be</em> at arrival, then the parking orbit is derived from
 * that plane — rather than taking a parking orbit and waiting for the Moon to line up with it.
 * There is no ground site there, so nothing makes it illegitimate.
 *
 * <p><b>Two ways of getting a parking orbit coexist here, and that is deliberate</b> (MIS-4 / L1,
 * spec {@code docs/lunar-flyby/03-conception-L1.md} §5 pt 5). {@link #parkingState}
 * <em>fabricates</em> one to fit the Moon; {@link #departureFrom} takes a plane a launch site
 * <em>imposed</em> and finds the injection point inside it. Only the second one flies since L6 took
 * the PHY-4 demonstration away — the first is now a fixture and the non-regression reference of L1.
 * Everything downstream of the parking state — {@link #solve}, the bisection, the differential
 * corrector — is common to both, and the declination guard of {@link #transferPlaneNormal} belongs
 * to the first alone.
 *
 * <p><b>The increment sought is impulsive; what is flown to reach it is not</b> (spec L6 §2,
 * decision i). Lambert <em>is</em> the impulsive formulation, and for an impulse Tsiolkovsky is
 * exact rather than an approximation, so {@link #solve} keeps searching for one. What a finite burn
 * loses against it is energy, and {@link #inject} is where that is recovered: it returns a {@link
 * Burn} to light rather than a state reached, calibrating the commanded ΔV on the specific energy
 * the impulse would have delivered.
 *
 * <p><b>Two knobs, and both see the same departure</b> (spec L6 §9). The inner one is that
 * calibration; the outer one is the aim bisection, which evaluates each candidate offset by flying
 * the calibrated burn rather than the impulse. Aiming on the impulse and flying the burn was
 * implemented first and refuted by measurement: the two departure states differ by 31 km and 7.25
 * m/s, and the flyby missed its perilune by 3 451 km.
 *
 * @param parkingState the state on the parking orbit the impulse is applied to
 * @param deltaV the impulsive velocity increment, in the parking state's frame
 * @param arrivalDate the date the aim point is reached
 * @param aimPoint the targeted position at {@code arrivalDate}, in the parking state's frame
 * @param aimOffset the distance from the Moon's centre to {@code aimPoint} (m)
 * @param perileneAltitude the flown perilune altitude this plan achieves (m)
 * @param keplerianMissMeters how far the Keplerian two-body plan lands from its own aim point once
 *     flown under the perturbed force model (m), or {@code NaN} when it could not be measured
 */
public record TranslunarInjectionPlan(
    SpacecraftState parkingState,
    Vector3D deltaV,
    AbsoluteDate arrivalDate,
    Vector3D aimPoint,
    double aimOffset,
    double perileneAltitude,
    double keplerianMissMeters) {

  private static final Logger logger = LogManager.getLogger(TranslunarInjectionPlan.class);

  /**
   * Coast from injection to the aim point (s). The exact half-Hohmann to the mean lunar distance
   * measures 5.02 d and a real translunar injection aims at about 3 d on a faster ellipse whose
   * apogee overshoots the Moon; four days is the compromise between the ΔV and the propagation
   * length. Recalibrate it if the ΔV leaves the band {@code TranslunarInjectionPlanTest} pins.
   */
  public static final double TIME_OF_FLIGHT_SECONDS = 4.0 * 86_400.0;

  /**
   * Parking-orbit inclination (rad).
   *
   * <p><b>Thirty degrees and not 28.5° on purpose.</b> The parking plane has to contain the Moon's
   * direction at arrival, which requires {@code i >= |declination|}; the lunar declination measures
   * ±28.40° over 2026 and reaches ±28.6° over the 18.6-year cycle. A Cape-latitude 28.5° would
   * clear the worst case by a tenth of a degree, which is the kind of margin that fails silently.
   * Thirty leaves 1.4°, and it is <em>because</em> that margin is small that {@link
   * #transferPlaneNormal} refuses rather than returning a wrong plane.
   */
  public static final double PARKING_INCLINATION = FastMath.toRadians(30.0);

  /** Parking-orbit altitude (m), circular. The conventional translunar parking orbit. */
  public static final double PARKING_ALTITUDE = 185_000.0;

  /**
   * Angle travelled from injection to the aim point (rad).
   *
   * <p><b>Not 180°, and that is a singularity and not a preference.</b> A Lambert transfer of
   * exactly half a revolution leaves the orbital plane undetermined — the boundary positions are
   * collinear with the centre — so an injection placed at the exact antipode of the arrival
   * direction has no solvable transfer. One hundred and seventy degrees keeps the near-Hohmann
   * geometry the ΔV budget rests on while staying clear of the degeneracy, and a real translunar
   * injection is short of 180° for the same reason.
   */
  public static final double TRANSFER_ANGLE = FastMath.toRadians(170.0);

  /**
   * Bisection steps on the aim offset, after bracketing. Twenty halvings shrink a bracket of a few
   * thousand kilometres to well under the tolerance, and bisection's cost per digit is the price
   * paid for never diverging (spec §12).
   */
  private static final int AIM_ITERATIONS = 20;

  /** Doublings (or halvings) allowed while looking for the far side of the bracket. */
  private static final int BRACKET_STEPS = 8;

  /** Perilune residual the secant stops on (m), an order of magnitude inside the pinned band. */
  private static final double AIM_TOLERANCE_METERS = 1_000.0;

  /**
   * Sampling step of the perilune search (s), fine enough that the parabolic refinement is exact.
   */
  private static final double PERILUNE_SAMPLE_STEP = 60.0;

  /**
   * Passes of the departure fixed point (spec §2.2).
   *
   * <p>The two couplings it closes are strongly contracting: the injection point sweeps 244.9 °/h
   * at {@link #PARKING_ALTITUDE} against the 0.549 °/h of the arrival direction, a ratio of 0.0022
   * per pass, and the residual eccentricity of the parking orbit costs {@code O(e)} on the
   * angle-to-time conversion. Three passes are enough; five is the cap, not the count.
   */
  private static final int DEPARTURE_PASSES = 5;

  /**
   * Phase residual the departure fixed point stops on (rad). Also the width of the dead band the
   * first pass wraps into {@code [0, 2π)}: a state already sitting on the injection point must
   * yield a zero coast, not a whole extra revolution bought from a rounding sign.
   */
  private static final double DEPARTURE_TOLERANCE_RADIANS = 1.0e-9;

  /**
   * How far past the aim date the perilune search runs (s). Closest approach need not fall exactly
   * on the aim date — the Moon moves, so the aim point is near but not on the relative-velocity
   * perpendicular — and half a day either side brackets it comfortably.
   */
  private static final double PERILUNE_SEARCH_MARGIN_SECONDS = 0.5 * 86_400.0;

  /**
   * Secant iterations calibrating the finite burn on the impulsive energy (spec L6 §4.2). Six, the
   * count {@code AnalyticApogeeCircularizationStage} settled on for the same shape of problem: the
   * first step is the closed-form slope, the second corrects it, and the map is smooth enough that
   * what follows is decimals — both profiles of §6.3 converge in three.
   */
  private static final int BURN_SECANT_ITERATIONS = 6;

  /**
   * Convergence threshold on the calibration, expressed as the speed the residual specific energy
   * is worth at the injection point (m/s). One centimetre per second is two orders of magnitude
   * below the finite-burn loss the calibration exists to recover — measured at 2.2 m/s on a Falcon
   * Heavy upper stage and 23.5 m/s on an Ariane 62 ULPM, both at 22.7 t (spec L6 §6.3).
   */
  private static final double BURN_TOLERANCE_METERS_PER_SECOND = 0.01;

  /**
   * The parking state a translunar mission starts from.
   *
   * <p>Circular at {@link #PARKING_ALTITUDE}, inclined at {@link #PARKING_INCLINATION}, in the
   * plane that contains the Moon's direction at {@code injectionDate + TIME_OF_FLIGHT_SECONDS}, at
   * the point {@link #TRANSFER_ANGLE} short of that direction.
   *
   * <p><b>Nothing in production flies it since L6 removed the PHY-4 demonstration</b>, and it stays
   * because it is a reference and not a convenience (spec L6 §1.5): {@code
   * TranslunarDepartureFlightTest} measures the departure from an <em>imposed</em> plane against
   * the one fabricated here, which is the non-regression reference of L1, and {@code
   * TranslunarInjectionPlanTest} exercises the declination guard of {@link #transferPlaneNormal}
   * through it.
   *
   * @param injectionDate the date the impulse is applied
   * @param mass the spacecraft mass at injection (kg)
   * @return the parking state, in GCRF
   * @throws OrbitlabException when the lunar declination exceeds the parking inclination
   */
  public static SpacecraftState parkingState(AbsoluteDate injectionDate, double mass) {
    Frame gcrf = OrekitService.get().gcrf();
    AbsoluteDate arrival = injectionDate.shiftedBy(TIME_OF_FLIGHT_SECONDS);
    Vector3D moonDirection = moonPosition(arrival).normalize();
    Vector3D normal = transferPlaneNormal(moonDirection);

    // Motion is prograde about the plane normal, so travelling +TRANSFER_ANGLE from the injection
    // point reaches the Moon's direction: the injection point is that direction rotated back.
    Vector3D injectionDirection =
        new Rotation(normal, -TRANSFER_ANGLE, RotationConvention.VECTOR_OPERATOR)
            .applyTo(moonDirection);

    double radius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + PARKING_ALTITUDE;
    Vector3D position = injectionDirection.scalarMultiply(radius);
    Vector3D velocity =
        Vector3D.crossProduct(normal, injectionDirection)
            .scalarMultiply(FastMath.sqrt(Constants.WGS84_EARTH_MU / radius));

    return new SpacecraftState(
            new CartesianOrbit(
                new TimeStampedPVCoordinates(injectionDate, position, velocity),
                gcrf,
                Constants.WGS84_EARTH_MU))
        .withMass(mass);
  }

  /**
   * The unit normal of the <b>fabricated</b> parking plane of {@link #parkingState}: inclined at
   * {@link #PARKING_INCLINATION} and containing {@code moonDirection}.
   *
   * <p><b>It is not "the transfer plane" in any general sense, and since MIS-4 / L1 it is on the
   * demo's path alone</b> (spec §3.2). An injection from an imposed plane flies the plane it is
   * given; the declination guard below, and the 30° constant it compares against, belong to the
   * orbit this method builds and to nothing else.
   *
   * <p>Writing {@code n = cos(i)·z + sin(i)·(cos φ, sin φ, 0)} and imposing {@code n · uM = 0}
   * gives {@code cos(φ − α) = −cos(i)·uM_z / (sin(i)·p)} with {@code α = atan2(uM_y, uM_x)} and
   * {@code p} the equatorial norm of {@code uM}. A solution exists exactly when {@code |tan δ| <=
   * tan i}, i.e. when the lunar declination fits inside the inclination — which is the guard, and
   * the reason the inclination carries 1.4° of margin rather than 0.1°.
   *
   * <p>Two roots exist; the {@code +} branch is taken. The other is the mirror node and flies the
   * same transfer with the plane reflected, so choosing between them would be choosing a label.
   */
  static Vector3D transferPlaneNormal(Vector3D moonDirection) {
    double declination = FastMath.asin(moonDirection.getZ() / moonDirection.getNorm());
    if (FastMath.abs(declination) > PARKING_INCLINATION) {
      throw new OrbitlabException(
          String.format(
              "lunar declination %.2f° exceeds the %.2f° parking inclination: no orbit of that"
                  + " inclination contains the Moon's direction, so the transfer would need a very"
                  + " expensive out-of-plane component (spec"
                  + " docs/multi-corps/08-conception-L6.md §4.1)",
              FastMath.toDegrees(declination), FastMath.toDegrees(PARKING_INCLINATION)));
    }

    Vector3D unit = moonDirection.normalize();
    double equatorialNorm = FastMath.hypot(unit.getX(), unit.getY());
    double alpha = FastMath.atan2(unit.getY(), unit.getX());
    double cosOffset =
        -FastMath.cos(PARKING_INCLINATION)
            * unit.getZ()
            / (FastMath.sin(PARKING_INCLINATION) * equatorialNorm);
    // Clamped only against rounding at the feasibility boundary; the guard above owns the verdict.
    double phi = alpha + FastMath.acos(FastMath.max(-1.0, FastMath.min(1.0, cosOffset)));

    return new Vector3D(
            FastMath.sin(PARKING_INCLINATION) * FastMath.cos(phi),
            FastMath.sin(PARKING_INCLINATION) * FastMath.sin(phi),
            FastMath.cos(PARKING_INCLINATION))
        .normalize();
  }

  /**
   * Where and when a parking orbit whose plane is <b>imposed</b> injects — MIS-4 / L1 (spec {@code
   * docs/lunar-flyby/03-conception-L1.md} §2.4).
   *
   * <p><b>It carries no state, and that is the point.</b> Exposing the shifted Keplerian state
   * would invite injecting from it rather than from the state actually flown, and the two differ by
   * the half-degree of nodal regression the parking coast accumulates (spec §5 pt 1). Both
   * consumers need the direction only: L4 coasts and reads its own position, L2 builds a circular
   * orbit from the direction and the radius.
   *
   * @param coastDuration the parking coast from the given state to the injection point (s); the
   *     first passage, so at most one revolution and the 12 s the injection point itself drifts
   *     during it
   * @param injectionDate the date the impulse is applied
   * @param arrivalDate {@code injectionDate + }{@link #TIME_OF_FLIGHT_SECONDS}
   * @param injectionDirection the unit direction of the injection point, in the parking frame and
   *     inside the imposed plane
   * @param planeMisalignment the signed angle of the arrival direction above the parking plane
   *     (rad), positive towards the plane's normal
   */
  public record Departure(
      double coastDuration,
      AbsoluteDate injectionDate,
      AbsoluteDate arrivalDate,
      Vector3D injectionDirection,
      double planeMisalignment) {}

  /**
   * The injection point of a parking orbit whose plane is imposed, and the coast that reaches it —
   * <b>closed form, no propagation</b> (spec §2).
   *
   * <p><b>The arrival direction is projected into the plane rather than met in 3D.</b> Reading
   * "170° short of the arrival direction" literally has a solution only while {@code cos β ≥ |cos
   * 170°|}, i.e. below 10° of misalignment, which would buy a geometric refusal where a Kourou
   * plane reaches 33.9° (spec §2.1). The projection is always defined, it reduces <em>exactly</em>
   * to {@link #parkingState} at zero misalignment, and the true 3D transfer angle it produces —
   * {@code cos θ = cos 170° · cos β} — moves away from the 180° Lambert singularity as the
   * misalignment grows rather than towards it.
   *
   * <p><b>The coast is the first passage</b>, by construction and not by a guard: starting the
   * fixed point at zero and wrapping the first angle into {@code [0, 2π)} selects the next crossing
   * of the injection point rather than the nearest one. That bounds it at one revolution <em>plus
   * the drift of the injection point during that revolution</em> — the point moves forward with the
   * Moon at 0.549 °/h, which is 0.81° or 11.9 s of parking phase, measured at 10.5 s. A departure
   * taken just past its injection point therefore waits 5 302 s, still well inside the 7 200 s
   * restart window of the upper stage L6 will fly it on.
   *
   * @param parking the parking state whose plane is imposed
   * @return the departure geometry
   */
  public static Departure departureFrom(SpacecraftState parking) {
    Vector3D position = parking.getPosition();
    Vector3D velocity = parking.getPVCoordinates().getVelocity();
    Vector3D planeNormal = Vector3D.crossProduct(position, velocity).normalize();

    // Rebuilt from position and velocity alone: the orbit then carries no non-Keplerian
    // acceleration, so shiftedBy is a pure mean-anomaly advance instead of the quadratic
    // small-offset expansion Orekit applies to a state coming out of a numerical propagator —
    // absurd over the 5 292 s of a parking revolution (spec §2.3).
    KeplerianOrbit keplerian =
        new KeplerianOrbit(
            new PVCoordinates(position, velocity),
            parking.getFrame(),
            parking.getDate(),
            parking.getOrbit().getMu());
    double meanMotion = keplerian.getKeplerianMeanMotion();

    double coast = 0.0;
    double misalignment = 0.0;
    Vector3D injectionDirection = position.normalize();
    for (int pass = 0; pass < DEPARTURE_PASSES; pass++) {
      Vector3D arrivalDirection =
          moonPosition(parking.getDate().shiftedBy(coast + TIME_OF_FLIGHT_SECONDS)).normalize();
      misalignment = planeMisalignment(planeNormal, arrivalDirection);
      Vector3D inPlane =
          arrivalDirection
              .subtract(planeNormal.scalarMultiply(arrivalDirection.dotProduct(planeNormal)))
              .normalize();
      injectionDirection =
          new Rotation(planeNormal, -TRANSFER_ANGLE, RotationConvention.VECTOR_OPERATOR)
              .applyTo(inPlane);

      double travel =
          orientedAngle(keplerian.shiftedBy(coast).getPosition(), injectionDirection, planeNormal);
      if (pass == 0 && travel < -DEPARTURE_TOLERANCE_RADIANS) {
        travel += MathUtils.TWO_PI;
      }
      // Tested before the update rather than after it, so the direction returned is the one the
      // returned coast actually reaches, and a departure already at its injection point coasts for
      // exactly zero.
      if (FastMath.abs(travel) < DEPARTURE_TOLERANCE_RADIANS) {
        break;
      }
      coast += travel / meanMotion;
    }

    return new Departure(
        coast,
        parking.getDate().shiftedBy(coast),
        parking.getDate().shiftedBy(coast + TIME_OF_FLIGHT_SECONDS),
        injectionDirection,
        misalignment);
  }

  /** The angle from {@code from} to {@code to} measured about {@code axis}, in {@code (−π, π]}. */
  private static double orientedAngle(Vector3D from, Vector3D to, Vector3D axis) {
    return FastMath.atan2(Vector3D.crossProduct(from, to).dotProduct(axis), from.dotProduct(to));
  }

  /**
   * The signed angle of the arrival direction above an orbital plane (rad), positive towards the
   * plane's normal — the term L2 weighs its epochs on, computed once here rather than twice there.
   *
   * @param planeNormal the unit normal of the plane
   * @param arrivalDirection the unit direction of the Moon at arrival
   */
  private static double planeMisalignment(Vector3D planeNormal, Vector3D arrivalDirection) {
    // Clamped only against rounding: both arguments are unit vectors, so the product is in [-1, 1]
    // up to the last bit.
    return FastMath.asin(
        FastMath.max(-1.0, FastMath.min(1.0, planeNormal.dotProduct(arrivalDirection))));
  }

  /**
   * Solves the injection: a Lambert seed towards an offset aim point, reconverged under the
   * perturbed model, with a secant on the offset until the flown perilune reaches {@code
   * targetPerileneAltitude}.
   *
   * @param parking the parking state to inject from
   * @param targetPerileneAltitude the perilune altitude above the lunar sphere to reach (m)
   * @param exhaustVelocity the impulse's effective exhaust velocity {@code Isp·g0} (m/s), for the
   *     mass drop
   * @param context the flight context the transfer is flown in
   * @return the solved plan
   */
  public static TranslunarInjectionPlan solve(
      SpacecraftState parking,
      double targetPerileneAltitude,
      double exhaustVelocity,
      FlightContext context) {
    return solve(
        parking,
        targetPerileneAltitude,
        exhaustVelocity,
        (state, deltaV) -> applyImpulse(state, deltaV, exhaustVelocity),
        context);
  }

  /**
   * The same aim, converged against an arbitrary way of executing the candidate ΔV.
   *
   * <p><b>The bisection has to fly what the mission will fly</b> (spec L6 §4.2, revised after
   * measurement): the offset it converges is the one that puts the <em>flown</em> perilune on
   * target, so evaluating it on an impulse and then flying a finite burn converges the wrong
   * trajectory. The overload above keeps the impulsive execution, which is what the pinned cases of
   * L1 fly and what {@link #measurePlanVersusFlight} reports against; {@link #inject} passes the
   * calibrated burn.
   *
   * @param execution how a candidate ΔV becomes the state the transfer is flown from
   */
  private static TranslunarInjectionPlan solve(
      SpacecraftState parking,
      double targetPerileneAltitude,
      double exhaustVelocity,
      Execution execution,
      FlightContext context) {

    AbsoluteDate arrival = parking.getDate().shiftedBy(TIME_OF_FLIGHT_SECONDS);
    Vector3D moonAtArrival = moonPosition(arrival);
    Vector3D offsetDirection = aimOffsetDirection(parking, moonAtArrival, arrival);
    double targetRadius = lunarRadius() + targetPerileneAltitude;

    // Bracket, then bisect. A secant was tried first and it is not good enough: the map
    // offset -> perilune is monotone but its slope varies by an order of magnitude with the epoch,
    // and
    // a secant seeded on a unit slope wandered off on a geometry it had not been calibrated on —
    // measured a -53 km "perilune", i.e. an impact flown as if it were a plan (spec §12). Bisection
    // on
    // a bracket cannot do that: it is slower per digit and it always converges.
    Bracket bracket =
        bracket(
            parking,
            arrival,
            moonAtArrival,
            offsetDirection,
            targetRadius,
            execution,
            context,
            targetPerileneAltitude);

    Attempt best = bracket.closestTo(targetRadius);
    double low = bracket.low();
    double high = bracket.high();
    for (int iteration = 0;
        iteration < AIM_ITERATIONS && !within(best, targetRadius);
        iteration++) {
      double offset = 0.5 * (low + high);
      Attempt attempt =
          attempt(parking, arrival, moonAtArrival, offsetDirection, offset, execution, context);
      logAim("bisect " + iteration, offset, attempt, targetPerileneAltitude);

      if (FastMath.abs(attempt.perileneRadius() - targetRadius)
          < FastMath.abs(best.perileneRadius() - targetRadius)) {
        best = attempt;
      }
      if (attempt.perileneRadius() < targetRadius) {
        low = offset;
      } else {
        high = offset;
      }
    }

    double achievedAltitude = best.perileneRadius() - lunarRadius();
    if (!within(best, targetRadius)) {
      throw new OrbitlabException(
          String.format(
              "[TLI] the aim did not reach the %.0f km perilune: best is %.0f km after bracketing"
                  + " [%.0f, %.0f] km and %d bisections. Flying it would deliver a trajectory that"
                  + " is not the one asked for — and at a negative altitude, an impact presented as a plan",
              targetPerileneAltitude / 1000.0,
              achievedAltitude / 1000.0,
              bracket.low() / 1000.0,
              bracket.high() / 1000.0,
              AIM_ITERATIONS));
    }
    double miss = measurePlanVersusFlight(parking, best, arrival, exhaustVelocity, context);
    // The misalignment is logged and not guarded: a ΔV that jumps from 3 178 to 6 000 m/s because
    // the imposed plane misses the Moon by 23° must be readable in the line rather than deduced
    // (spec §3.4). Refusing on it belongs to the launch window, which can pick another date.
    logger.info(
        "TLI plan: dv={} m/s, offset={} km, perilune altitude={} km, plane misalignment={}°",
        FastMath.round(best.deltaV().getNorm()),
        FastMath.round(best.offset() / 1000.0),
        FastMath.round(achievedAltitude / 1000.0),
        String.format(
            Locale.ROOT,
            "%.2f",
            FastMath.toDegrees(
                planeMisalignment(
                    Vector3D.crossProduct(
                            parking.getPosition(), parking.getPVCoordinates().getVelocity())
                        .normalize(),
                    moonAtArrival.normalize()))));

    return new TranslunarInjectionPlan(
        parking, best.deltaV(), arrival, best.aimPoint(), best.offset(), achievedAltitude, miss);
  }

  /**
   * Applies this plan's impulse to its parking state: the velocity gains {@link #deltaV} and the
   * mass drops by Tsiolkovsky, which for an impulse is exact and not an approximation.
   *
   * @param exhaustVelocity the effective exhaust velocity {@code Isp·g0} (m/s)
   * @return the post-injection state
   */
  public SpacecraftState applyTo(SpacecraftState state, double exhaustVelocity) {
    return applyImpulse(state, deltaV, exhaustVelocity);
  }

  /**
   * The finite burn that delivers an injection: what to light, in which direction and for how long
   * (MIS-4 / L6 §4.1).
   *
   * <p><b>It replaces the post-impulse state the impulsive model used to return</b>, and the change
   * of kind is the lot: a caller no longer receives what it obtains but what it has to
   * <em>execute</em>. The state is reached by flying this, not by arithmetic on a velocity.
   *
   * @param plan the plan the aim converged to, and the impulsive reference the burn is calibrated
   *     against
   * @param direction the unit thrust direction — {@code plan.deltaV()} normalized, inertially
   *     fixed, which is what makes a scalar calibration sufficient (spec §2, decision a)
   * @param duration how long to thrust (s)
   * @param commandedDeltaV the ΔV the burn is commanded for, above the impulsive one by the
   *     finite-burn loss (m/s)
   * @param endMass the mass at burn end (kg), above the active stage's depletion floor
   */
  public record Burn(
      TranslunarInjectionPlan plan,
      Vector3D direction,
      double duration,
      double commandedDeltaV,
      double endMass) {}

  /**
   * How a candidate ΔV becomes the state the transfer is flown from — the seam the aim bisection
   * converges through, and the reason the finite layer needs no second aiming loop of its own.
   *
   * <p>It exists because the two knobs of decision B have to see the same departure: an aim
   * converged on an impulse and then flown as a burn misses by thousands of kilometres (spec §4.2).
   */
  @FunctionalInterface
  private interface Execution {
    /**
     * @param parking the state at the injection point
     * @param deltaV the candidate impulsive-equivalent increment
     * @return the state the transfer continues from
     */
    SpacecraftState stateAfter(SpacecraftState parking, Vector3D deltaV);
  }

  /**
   * Solves the injection from {@code parking}, calibrates the finite burn that delivers it, and
   * refuses on the active stage's depletion floor — the whole verdict on a translunar departure, in
   * one place.
   *
   * <p><b>It is shared rather than duplicated</b> (MIS-4 / L4 §7). {@code TLIBurnStage} flies this
   * on the mission's chain; {@code LunarLaunchWindowProblem.confirm} runs it on a screened epoch to
   * decide whether a date is a plan or a wish. Holding them together is what let L6 turn the
   * injection into a real burn without the window drifting behind it — a window still confirming
   * against the impulsive model would keep dating launches by a trajectory the mission no longer
   * flies.
   *
   * <p><b>The aim is converged on the burn, not on the impulse</b> (spec §4.2, revised after
   * measurement). {@link #solve}'s bisection evaluates each candidate offset by <em>flying the
   * calibrated finite departure</em>, so the perilune it converges to is the one this burn reaches.
   * Aiming with the impulse and flying the burn was tried and refuted: the departure states differ
   * by 31 km and 7.25 m/s, worth 3 451 km at the Moon. The plan's {@link #deltaV} therefore remains
   * an impulsive-equivalent aim — {@link #solve} is untouched in its own right, and its pinned
   * cases still fly the impulse — but the offset it carries was converged against the burn.
   *
   * <p><b>{@code parking} is the state at the injection point, not at ignition.</b> The burn is
   * centred on it, and the advance a caller has to stop its coast at is {@link #ignitionLead}.
   *
   * <p><b>The floor is judged on the commanded ΔV</b>, so a transfer that passed impulsively can be
   * refused here (spec §4.3). That is the under-delivery, paid in propellant rather than in miss
   * distance; the message quotes both figures so the surcharge is readable in the refusal.
   *
   * @param parking the state at the injection point the burn is centred on
   * @param targetPeriluneAltitude the perilune altitude above the lunar surface to aim for (m)
   * @param active the vehicle stage burning the injection — its propulsion sizes the burn, its
   *     depletion floor decides whether the transfer is within reach
   * @param context the environment the aim and the burn are flown in
   * @return the burn to light one {@link #ignitionLead} before {@code parking.getDate()}
   * @throws OrbitlabException when the aim does not converge, or when the burn would take the
   *     active stage below its depletion floor
   */
  public static Burn inject(
      SpacecraftState ignitionState,
      SpacecraftState parking,
      double targetPeriluneAltitude,
      ActiveStageInfo active,
      FlightContext context) {
    double exhaustVelocity = active.propulsion().isp() * Constants.G0_STANDARD_GRAVITY;
    TranslunarInjectionPlan plan =
        solve(
            parking,
            targetPeriluneAltitude,
            exhaustVelocity,
            (state, deltaV) ->
                calibrateBurn(ignitionState, state, deltaV, active, context, false).endState(),
            context);
    return refuseOrReturn(
        plan, calibrateBurn(ignitionState, parking, plan.deltaV(), active, context, true), active);
  }

  /**
   * Assembles the burn and pronounces the depletion floor on the <b>commanded</b> ΔV, so a transfer
   * that passed impulsively can be refused here (spec §4.3). That is the under-delivery, paid in
   * propellant rather than in miss distance; the message quotes both figures so the surcharge is
   * readable in the refusal.
   */
  private static Burn refuseOrReturn(
      TranslunarInjectionPlan plan, Calibrated calibrated, ActiveStageInfo active) {
    Burn burn =
        new Burn(
            plan,
            plan.deltaV().normalize(),
            calibrated.duration(),
            calibrated.commandedDeltaV(),
            calibrated.endState().getMass());

    double floor = active.depletionFloor();
    if (burn.endMass() < floor) {
      throw new OrbitlabException(
          String.format(
              Locale.ROOT,
              "the %.0f m/s injection burn (%.0f m/s impulsive) would leave %.0f kg, below the %.0f"
                  + " kg depletion floor of the active stage — it does not carry the propellant for"
                  + " this transfer",
              burn.commandedDeltaV(),
              plan.deltaV().getNorm(),
              burn.endMass(),
              floor));
    }
    return burn;
  }

  /**
   * The same verdict for a caller that has no coast of its own to have stopped at the ignition
   * point: the launch window, which prices an epoch rather than flying it.
   *
   * <p>It reconstructs the ignition state by coasting back one {@link #ignitionLead}, then plans
   * exactly what the stage plans. <b>Pricing on the same model the mission flies is not a
   * refinement, it is what keeps the window honest</b> (MIS-4 / L4 §7): the finite departure does
   * not merely cost more, it reaches <em>fewer</em> perilunes. Measured on the flyby's own window,
   * an epoch the impulsive aim converged at 100 km bottoms out at 132 km once flown finitely — the
   * whole map lifted above the target — so a window screening impulsively hands the mission a date
   * it cannot honour (spec L6 §9.6).
   *
   * <p><b>This only says something if the window screens with the launcher that will fly.</b> On a
   * 3 kN spacecraft motor the injection sweeps 75° of arc, the out-of-model regime §1.2 measured,
   * and the surcharge computed there is +264 m/s of fixture artefact.
   *
   * @param parking the state at the injection point
   * @param targetPeriluneAltitude the perilune altitude above the lunar surface to aim for (m)
   * @param active the vehicle stage burning the injection
   * @param context the environment the aim and the burn are flown in
   * @return the burn priced at {@code parking}, ignited one {@link #ignitionLead} before it
   * @throws OrbitlabException when the aim does not converge, or when the burn would take the
   *     active stage below its depletion floor
   */
  public static Burn inject(
      SpacecraftState parking,
      double targetPeriluneAltitude,
      ActiveStageInfo active,
      FlightContext context) {
    double lead = ignitionLead(parking, departureFrom(parking), active);
    NumericalPropagator backwards =
        OrekitService.get().createOptimizationPropagator(context, burnMaxStep(active));
    backwards.setInitialState(parking);
    SpacecraftState ignitionState = backwards.propagate(parking.getDate().shiftedBy(-lead));
    return inject(ignitionState, parking, targetPeriluneAltitude, active, context);
  }

  /**
   * How far ahead of the injection point the burn has to be lit for it to be centred on it (s) —
   * <b>closed form, no propagation</b> (spec L6 §4.5).
   *
   * <p>It is the second public entry of the finite layer, and it exists because centring requires
   * knowing the burn duration <em>before</em> igniting. The parking coast is what carries it: it
   * stops here rather than at the injection point, which is what spares the chain the case of a
   * burn starting before its own stage does (spec §2, decision α).
   *
   * <p>The ΔV is evaluated on the parking state advanced Keplerianly to the injection point,
   * rebuilt from position and velocity for the reason {@link #departureFrom} states. The residual
   * off-centring that leaves is the gap between the closed-form ΔV and the solved one — of the
   * order of 0.05 % of the duration, some 0.02 s on a Falcon Heavy upper stage.
   *
   * @param parking the parking state the coast starts from
   * @param departure the geometry {@link #departureFrom} resolved from that same state
   * @param active the vehicle stage that will burn the injection
   * @return the advance to subtract from {@link Departure#injectionDate()} (s)
   */
  public static double ignitionLead(
      SpacecraftState parking, Departure departure, ActiveStageInfo active) {
    KeplerianOrbit keplerian =
        new KeplerianOrbit(
            new PVCoordinates(parking.getPosition(), parking.getPVCoordinates().getVelocity()),
            parking.getFrame(),
            parking.getDate(),
            parking.getOrbit().getMu());
    SpacecraftState atInjection =
        new SpacecraftState(keplerian.shiftedBy(departure.coastDuration()))
            .withMass(parking.getMass());
    double deltaV = keplerianInjectionDeltaV(atInjection, departure.arrivalDate());
    PropulsionSystem propulsion = active.propulsion();
    return 0.5
        * Physics.computeBurnDurationCapped(
            deltaV,
            parking.getMass(),
            propulsion.isp(),
            propulsion.thrust(),
            active.remainingFuel(parking.getMass()));
  }

  /**
   * The outcome of one calibration: what to burn, and the state it leaves the vehicle in.
   *
   * @param duration how long to thrust (s)
   * @param commandedDeltaV the ΔV the burn is commanded for (m/s)
   * @param endState the state at cut-off, half a burn past the injection point
   */
  private record Calibrated(double duration, double commandedDeltaV, SpacecraftState endState) {}

  /**
   * Scales the commanded ΔV until the centred finite burn delivers the specific energy the impulse
   * would have (spec L6 §4.2) — the <b>inner</b> of the lot's two nested knobs.
   *
   * <p><b>Energy is the invariant, and a scalar knob is enough to reach it.</b> The thrust
   * direction is inertially fixed, so the delivered ΔV is exactly parallel to the commanded one —
   * only an amplitude is missing. Centring cancels the direction error to first order: measured at
   * 0.11 mrad on the 8.8° arc the flyby actually sweeps, which is 42 km at the Moon.
   *
   * <p><b>What it does not do is aim</b>, and that is why the outer knob has to fly what this
   * returns. Matching the energy fixes the semi-major axis, not the transfer: at cut-off the
   * vehicle sits 31 km from where the impulse would have left it — 30.8 of them along the track —
   * and 7.25 m/s faster, the two being exactly the same statement since a lower radius buys speed
   * at constant energy. An aim converged on the impulse and flown on this misses the perilune by 3
   * 451 km (spec §4.2, measured 2026-08-27).
   *
   * <p>Only the burn is propagated, never the four days of the transfer, which is what keeps it
   * cheap enough to run inside every evaluation of the aim.
   */
  private static Calibrated calibrateBurn(
      SpacecraftState ignitionState,
      SpacecraftState parking,
      Vector3D aimDeltaV,
      ActiveStageInfo active,
      FlightContext context,
      boolean report) {
    PropulsionSystem propulsion = active.propulsion();
    double exhaustVelocity = propulsion.isp() * Constants.G0_STANDARD_GRAVITY;
    double impulsive = aimDeltaV.getNorm();
    Vector3D direction = aimDeltaV.normalize();

    SpacecraftState reference = applyImpulse(parking, aimDeltaV, exhaustVelocity);
    double targetEnergy = specificEnergy(reference);
    double referenceSpeed = reference.getPVCoordinates().getVelocity().getNorm();
    // Authority of the knob, closed form: adding dv at speed v moves the specific energy by v·dv.
    // It seeds the secant and rescues it whenever two iterations land on the same residual.
    double slope = referenceSpeed * impulsive;
    double tolerance = BURN_TOLERANCE_METERS_PER_SECOND * referenceSpeed;

    double maxStep = burnMaxStep(active);
    double availableFuel = active.remainingFuel(ignitionState.getMass());

    double beta = 1.0;
    double betaPrevious = Double.NaN;
    double residualPrevious = Double.NaN;
    Calibrated best = null;
    double bestResidual = Double.POSITIVE_INFINITY;
    int iterations = 0;
    for (int iteration = 0; iteration < BURN_SECANT_ITERATIONS; iteration++) {
      iterations = iteration + 1;
      double commanded = beta * impulsive;
      double duration =
          Physics.computeBurnDurationCapped(
              commanded,
              ignitionState.getMass(),
              propulsion.isp(),
              propulsion.thrust(),
              availableFuel);
      SpacecraftState burnt =
          flyBurn(ignitionState, direction, duration, propulsion, maxStep, context);
      double residual = targetEnergy - specificEnergy(burnt);

      if (FastMath.abs(residual) < bestResidual) {
        bestResidual = FastMath.abs(residual);
        best = new Calibrated(duration, commanded, burnt);
      }
      if (FastMath.abs(residual) < tolerance) {
        break;
      }

      double next;
      if (Double.isNaN(residualPrevious) || FastMath.abs(residual - residualPrevious) < 1.0e-9) {
        next = beta + residual / slope;
      } else {
        next = beta - residual * (beta - betaPrevious) / (residual - residualPrevious);
      }
      betaPrevious = beta;
      residualPrevious = residual;
      beta = next;
    }

    if (report) {
      logger.info(
          "Injection burn calibrated in {} iteration(s): commanded {} m/s for {} m/s impulsive"
              + " (+{}), dt = {} s, residual {} m/s",
          iterations,
          String.format(Locale.ROOT, "%.1f", best.commandedDeltaV()),
          String.format(Locale.ROOT, "%.1f", impulsive),
          String.format(Locale.ROOT, "%.2f", best.commandedDeltaV() - impulsive),
          String.format(Locale.ROOT, "%.1f", best.duration()),
          String.format(Locale.ROOT, "%.4f", bestResidual / referenceSpeed));
    }
    return best;
  }

  /**
   * Flies the burn alone, from the state the mission really ignites at, and returns the state at
   * cut-off.
   *
   * <p><b>It starts where the caller says and nowhere else</b> (spec L6 §9.5). Reconstructing the
   * ignition point as "half a burn before the injection point" was tried and refuted: the parking
   * coast stops on an injection date resolved from <em>its</em> entry state, and {@link
   * #departureFrom} resolved half a burn later lands 2.46 s away. The burn was then calibrated
   * centred and flown off-centre, which put the flyby 1 150 km inside the Moon.
   */
  private static SpacecraftState flyBurn(
      SpacecraftState ignitionState,
      Vector3D direction,
      double duration,
      PropulsionSystem propulsion,
      double maxStep,
      FlightContext context) {
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, maxStep);
    propagator.setInitialState(ignitionState);
    FrameAlignedProvider attitude =
        new FrameAlignedProvider(
            new Rotation(direction, Vector3D.PLUS_I), ignitionState.getFrame());
    propagator.addForceModel(
        new ConstantThrustManeuver(
            ignitionState.getDate().shiftedBy(1.0e-3),
            duration,
            propulsion.thrust(),
            propulsion.isp(),
            attitude,
            Vector3D.PLUS_I));
    return propagator.propagate(ignitionState.getDate().shiftedBy(duration + 1.0e-3));
  }

  /**
   * The integrator max step every propagator hosting this burn must use, as {@code MissionStage}
   * requires: sized from the active stage's depletion floor, capped at {@code SAFE_MAX_STEP}.
   */
  private static double burnMaxStep(ActiveStageInfo active) {
    PropulsionSystem propulsion = active.propulsion();
    return OrekitService.burnLimitedMaxStep(
        new OrekitService.BurnSpec(propulsion.thrust(), propulsion.isp(), active.depletionFloor()));
  }

  /** The specific orbital energy of a state (J/kg), the invariant the calibration targets. */
  private static double specificEnergy(SpacecraftState state) {
    double speed = state.getPVCoordinates().getVelocity().getNorm();
    return 0.5 * speed * speed - state.getOrbit().getMu() / state.getPosition().getNorm();
  }

  /**
   * What the injection costs from a parking orbit one is <b>given</b> — closed form, one Lambert
   * solve, no propagation, microseconds.
   *
   * <p>It aims at the Moon's centre rather than at an offset aim point, which is the right call for
   * a <em>ranking</em> criterion: the offset is worth a handful of m/s against the three-odd km/s
   * of the injection, and resolving it is what costs the thirty propagations {@link #solve} spends.
   * The verdict on a screened epoch stays with {@link #inject}.
   *
   * <p>It is the direct companion of {@link #departureFrom} — pass it {@link
   * Departure#arrivalDate()} — and it carries two loads: the Lambert term of the lunar launch
   * window (MIS-4 / L2), and the ΔV {@link #ignitionLead} sizes the ignition advance from (L6
   * §4.5).
   *
   * @param parking the parking state to inject from, at the injection point
   * @param arrivalDate the date the Moon's centre is aimed at
   * @return the magnitude of the injection impulse (m/s)
   */
  public static double keplerianInjectionDeltaV(SpacecraftState parking, AbsoluteDate arrivalDate) {
    Vector3D seed =
        keplerianSeedVelocity(
            parking, boundaryConditions(parking, arrivalDate, moonPosition(arrivalDate)));
    return seed.subtract(parking.getPVCoordinates().getVelocity()).getNorm();
  }

  /**
   * An offset bracket: {@code low} undershoots the target perilune, {@code high} overshoots it.
   *
   * @param low the largest offset known to give a perilune below the target
   * @param high the smallest offset known to give a perilune above it
   * @param atLow the attempt flown at {@code low}
   * @param atHigh the attempt flown at {@code high}
   */
  private record Bracket(double low, double high, Attempt atLow, Attempt atHigh) {
    Attempt closestTo(double targetRadius) {
      return FastMath.abs(atLow.perileneRadius() - targetRadius)
              <= FastMath.abs(atHigh.perileneRadius() - targetRadius)
          ? atLow
          : atHigh;
    }
  }

  /**
   * Finds an offset bracket around the target perilune: outwards by doubling, then inwards by
   * halving if that found nothing.
   *
   * <p>The outward walk is the one L1 wrote and it goes first, unchanged, so every aim that
   * converged before this lot converges through the same sequence of attempts. Doubling rather than
   * stepping because the ratio between the aim offset and the achieved perilune depends on the
   * encounter geometry and is not close to one: at the epoch L1 measured, a 12 825 km aim gave a
   * 103 km perilune.
   *
   * <p><b>What L6 adds is the second walk, and the reason is that the map is not always
   * increasing</b> (§9.7). "Aiming further from the Moon's centre passes further from it" holds for
   * the impulsive departures L1 measured and is false elsewhere: flown finitely, one geometry gave
   * 132 km of perilune at 1 837 km of offset and 259 km at 230 km — decreasing, so the root lies
   * inwards. Trying outwards first and inwards only on failure keeps the first case bit-identical
   * and gives the second one a chance, at the cost of a few propagations on a search that was going
   * to fail anyway.
   *
   * <p><b>The inward walk is not floored at the lunar radius.</b> The floor read "below this the
   * aim point is inside the Moon and the perilune reading has no meaning", which confuses the
   * target with the reading: the aim point is a Lambert boundary condition and nothing more — the
   * flown trajectory misses it by some 26 000 km — while the perilune is measured on the flight.
   */
  private static Bracket bracket(
      SpacecraftState parking,
      AbsoluteDate arrival,
      Vector3D moonAtArrival,
      Vector3D offsetDirection,
      double targetRadius,
      Execution execution,
      FlightContext context,
      double targetPerileneAltitude) {

    Attempt at =
        attempt(parking, arrival, moonAtArrival, offsetDirection, targetRadius, execution, context);
    logAim("bracket 0", targetRadius, at, targetPerileneAltitude);

    Bracket outwards =
        walk(
            parking,
            arrival,
            moonAtArrival,
            offsetDirection,
            targetRadius,
            at,
            2.0,
            execution,
            context,
            targetPerileneAltitude,
            "bracket out ");
    if (outwards != null) {
      return outwards;
    }
    Bracket inwards =
        walk(
            parking,
            arrival,
            moonAtArrival,
            offsetDirection,
            targetRadius,
            at,
            0.5,
            execution,
            context,
            targetPerileneAltitude,
            "bracket in ");
    if (inwards != null) {
      return inwards;
    }
    return new Bracket(targetRadius, targetRadius, at, at);
  }

  /**
   * Walks the offset by a constant factor until two consecutive attempts straddle the target.
   *
   * @param factor {@code 2.0} to walk away from the Moon's centre, {@code 0.5} to walk towards it
   * @return the straddling bracket, or {@code null} when {@link #BRACKET_STEPS} did not find one
   */
  private static Bracket walk(
      SpacecraftState parking,
      AbsoluteDate arrival,
      Vector3D moonAtArrival,
      Vector3D offsetDirection,
      double startOffset,
      Attempt startAttempt,
      double factor,
      Execution execution,
      FlightContext context,
      double targetPerileneAltitude,
      String label) {

    double targetRadius = lunarRadius() + targetPerileneAltitude;
    double offset = startOffset;
    Attempt previous = startAttempt;
    for (int i = 0; i < BRACKET_STEPS; i++) {
      double previousOffset = offset;
      offset *= factor;
      Attempt next =
          attempt(parking, arrival, moonAtArrival, offsetDirection, offset, execution, context);
      logAim(label + i, offset, next, targetPerileneAltitude);
      if (straddles(previous, next, targetRadius)) {
        return order(previousOffset, previous, offset, next, targetRadius);
      }
      previous = next;
    }
    return null;
  }

  /** Whether the target perilune lies between what these two attempts flew. */
  private static boolean straddles(Attempt one, Attempt other, double targetRadius) {
    return (one.perileneRadius() - targetRadius) * (other.perileneRadius() - targetRadius) <= 0.0;
  }

  /**
   * Orders a straddling pair so that {@code low} is the end that undershoots the target — which is
   * all the bisection reads, and is unrelated to which offset is numerically the larger.
   */
  private static Bracket order(
      double firstOffset, Attempt first, double secondOffset, Attempt second, double targetRadius) {
    return first.perileneRadius() < targetRadius
        ? new Bracket(firstOffset, secondOffset, first, second)
        : new Bracket(secondOffset, firstOffset, second, first);
  }

  private static boolean within(Attempt attempt, double targetRadius) {
    return FastMath.abs(attempt.perileneRadius() - targetRadius) < AIM_TOLERANCE_METERS;
  }

  private static void logAim(
      String label, double offset, Attempt attempt, double targetPerileneAltitude) {
    logger.info(
        "TLI aim {}: offset {} km -> perilune altitude {} km (target {} km)",
        label,
        FastMath.round(offset / 1000.0),
        FastMath.round((attempt.perileneRadius() - lunarRadius()) / 1000.0),
        FastMath.round(targetPerileneAltitude / 1000.0));
  }

  /** One evaluation of the aim: the offset tried, and what it flew. */
  private record Attempt(
      double offset, Vector3D aimPoint, Vector3D deltaV, double perileneRadius) {}

  private static Attempt attempt(
      SpacecraftState parking,
      AbsoluteDate arrival,
      Vector3D moonAtArrival,
      Vector3D offsetDirection,
      double offset,
      Execution execution,
      FlightContext context) {

    Vector3D aimPoint = moonAtArrival.add(offsetDirection.scalarMultiply(offset));
    LambertBoundaryConditions conditions = boundaryConditions(parking, arrival, aimPoint);
    Vector3D keplerianVelocity = keplerianSeedVelocity(parking, conditions);

    // The Keplerian seed is flown as it is, and the differential corrector is deliberately NOT in
    // this
    // loop (spec §12). Its job is to make the perturbed trajectory pass through the aim point at
    // the
    // aim date — but the aim point is a free parameter here, tuned by the outer bisection until the
    // FLOWN perilune is right. Hitting an arbitrary intermediate target exactly buys nothing, while
    // costing most of the runtime and adding a failure mode. It runs once, after convergence,
    // purely
    // to report the plan-versus-flight gap L2 §4.2 asked for.
    Vector3D deltaV = keplerianVelocity.subtract(parking.getPVCoordinates().getVelocity());
    SpacecraftState injected = execution.stateAfter(parking, deltaV);

    return new Attempt(offset, aimPoint, deltaV, perileneRadius(injected, arrival, context));
  }

  /** The Lambert boundary problem this plan poses: parking position now, aim point at arrival. */
  static LambertBoundaryConditions boundaryConditions(
      SpacecraftState parking, AbsoluteDate arrival, Vector3D aimPoint) {
    return new LambertBoundaryConditions(
        parking.getDate(), parking.getPosition(), arrival, aimPoint, parking.getFrame());
  }

  /**
   * The Keplerian seed velocity at injection — closed form, no propagation.
   *
   * <p><b>The posigrade flag is read off the boundary positions, not assumed</b> (MIS-4 / L1, spec
   * §3.3). It was hardcoded {@code true} and justified by the transfer plane's normal having a
   * positive vertical component "by construction, {@link #PARKING_INCLINATION} being well under
   * 90°" — a constant that governs nothing once the parking plane is imposed, which turned the
   * justification into a tacit assumption on an input. The sign of {@code (r₁ × r₂)·z} says it
   * instead, and on the fabricated plane it says {@code true}.
   *
   * <p>Zero full revolutions, because the transfer is a single arc short of half a turn ({@link
   * #TRANSFER_ANGLE}). That one stays a constant: generalising it is MIS-6, where a second consumer
   * would finally fix the shape of the API.
   */
  static Vector3D keplerianSeedVelocity(
      SpacecraftState parking, LambertBoundaryConditions conditions) {
    boolean posigrade =
        Vector3D.crossProduct(conditions.getInitialPosition(), conditions.getTerminalPosition())
                .getZ()
            >= 0.0;
    return new LambertSolver(parking.getOrbit().getMu())
        .solve(posigrade, 0, conditions)
        .getInitialVelocity();
  }

  /**
   * The aim point this plan would target for a given offset, before any secant step — the seam the
   * propagation-free geometry test uses.
   */
  static Vector3D aimPointFor(SpacecraftState parking, AbsoluteDate arrival, double offset) {
    Vector3D moonAtArrival = moonPosition(arrival);
    return moonAtArrival.add(
        aimOffsetDirection(parking, moonAtArrival, arrival).scalarMultiply(offset));
  }

  /**
   * How far the Keplerian two-body plan lands from its own aim point once flown under the perturbed
   * force model — the measurement L2 §4.2 left to this lot, taken <b>once</b> on the converged
   * plan.
   *
   * <p>Two numbers are logged and one is returned. The returned one is the position miss, which is
   * the gap in the terms the question was asked in. The other is what the differential corrector
   * would have to change the injection velocity by to close it: an independent reading of the same
   * gap, and the reason it is only logged is that nothing in this lot flies the corrected velocity
   * (see {@code attempt}).
   *
   * <p><b>Never throws.</b> This is reporting, and reporting must not be able to fail a flight that
   * has already converged — the boundary discipline {@code AchievedOrbit.of} sets. Orekit's
   * corrector was measured throwing {@code NullPointerException} on some geometries; that costs a
   * log line here and nothing else.
   */
  private static double measurePlanVersusFlight(
      SpacecraftState parking,
      Attempt converged,
      AbsoluteDate arrival,
      double exhaustVelocity,
      FlightContext context) {

    SpacecraftState injected = applyImpulse(parking, converged.deltaV(), exhaustVelocity);
    LambertBoundaryConditions conditions =
        boundaryConditions(parking, arrival, converged.aimPoint());

    double miss;
    try {
      miss =
          propagator(context, injected)
              .propagate(arrival)
              .getPosition()
              .subtract(converged.aimPoint())
              .getNorm();
    } catch (RuntimeException e) {
      logger.warn("Plan-versus-flight gap unavailable ({})", e.getClass().getSimpleName());
      return Double.NaN;
    }

    try {
      LambertDifferentialCorrector corrector = new LambertDifferentialCorrector(conditions);
      corrector.setInitialMass(injected.getMass());
      LambertBoundaryVelocities corrected =
          corrector.solve(
              propagator(context, injected),
              converged.deltaV().add(parking.getPVCoordinates().getVelocity()));
      logger.info(
          "Plan versus flight (L2 §4.2): the Keplerian two-body seed lands {} km from its aim point"
              + " under the 8x8 + Moon + Sun model; closing that would take {} m/s on the injection"
              + " velocity",
          FastMath.round(miss / 1000.0),
          FastMath.round(
              corrected
                  .getInitialVelocity()
                  .subtract(converged.deltaV().add(parking.getPVCoordinates().getVelocity()))
                  .getNorm()));
    } catch (RuntimeException e) {
      logger.info(
          "Plan versus flight (L2 §4.2): the Keplerian two-body seed lands {} km from its aim point"
              + " under the 8x8 + Moon + Sun model. Orekit's differential corrector did not give the"
              + " equivalent velocity figure ({}), which costs nothing here — no flight uses it",
          FastMath.round(miss / 1000.0),
          e.getClass().getSimpleName());
    }
    return miss;
  }

  /**
   * Closest selenocentric distance reached, refined parabolically on the three samples bracketing
   * the sampled minimum — exact to the metre, where the 60 s ephemeris sampling of the flown coast
   * over-reads the perilune by up to 0.9 km (spec §4.3).
   *
   * <p><b>One geocentric propagation, no sphere-of-influence switching</b>, and that is licensed by
   * measurement rather than convenience: L4 §11.2 measured 9.55 m between the multi-arc flight and
   * the same flight in a single geocentric frame (spec §1.6).
   */
  private static double perileneRadius(
      SpacecraftState injected, AbsoluteDate arrival, FlightContext context) {
    double searchEnd = arrival.durationFrom(injected.getDate()) + PERILUNE_SEARCH_MARGIN_SECONDS;

    NumericalPropagator propagator = propagator(context, injected);
    DistanceTracker tracker = new DistanceTracker(injected.getFrame());
    propagator.getMultiplexer().add(PERILUNE_SAMPLE_STEP, tracker);
    propagator.propagate(injected.getDate().shiftedBy(searchEnd));

    return tracker.refinedMinimum();
  }

  /**
   * Tracks the selenocentric distance along a propagation, keeping the minimum and its neighbours.
   */
  private static final class DistanceTracker
      implements org.orekit.propagation.sampling.OrekitFixedStepHandler {

    private final Frame frame;
    private double before = Double.NaN;
    private double minimum = Double.POSITIVE_INFINITY;
    private double after = Double.NaN;
    private double previous = Double.NaN;
    private boolean minimumClosed;

    private DistanceTracker(Frame frame) {
      this.frame = frame;
    }

    @Override
    public void handleStep(SpacecraftState state) {
      double distance =
          state
              .getPosition()
              .subtract(
                  OrekitService.get()
                      .body(SolarSystemBody.MOON)
                      .getPosition(state.getDate(), frame))
              .getNorm();
      if (distance < minimum) {
        minimum = distance;
        before = previous;
        minimumClosed = false;
      } else if (!minimumClosed && !Double.isNaN(previous)) {
        after = distance;
        minimumClosed = true;
      }
      previous = distance;
    }

    /**
     * The vertex of the parabola through the three bracketing samples, or the sampled minimum when
     * the encounter was not bracketed (a trajectory still closing at the end of the search).
     */
    private double refinedMinimum() {
      if (Double.isNaN(before) || Double.isNaN(after)) {
        return minimum;
      }
      double denominator = before - 2.0 * minimum + after;
      if (FastMath.abs(denominator) < 1.0e-9) {
        return minimum;
      }
      double shift = 0.5 * (before - after) / denominator;
      return minimum - 0.25 * (before - after) * shift;
    }
  }

  /**
   * The aim-offset direction: in the transfer plane, perpendicular to the <b>arrival relative
   * velocity</b>, on the side the Moon is moving towards.
   *
   * <p><b>Perpendicular to the relative velocity, and not merely to the Earth-Moon direction.</b>
   * The first version used the latter, and it has a failure mode that only some geometries show:
   * when the relative velocity happens to lie nearly along that direction, sliding the aim point
   * along it barely changes how close the trajectory passes, and the achieved perilune acquires a
   * <em>floor</em> the aim cannot get under. Measured: at one epoch of a lunar month the perilune
   * would not go below 3 176 km even with the aim point on the lunar surface, so the bracket
   * collapsed (spec §12). Perpendicular to the relative velocity, the offset <em>is</em> the miss
   * distance to first order — which is what a B-plane aim point is, and why patched-conic targeting
   * is stated in those terms.
   *
   * <p>Keeping the offset <em>in</em> the transfer plane keeps the flyby two-dimensional and
   * readable; an out-of-plane offset would tilt the passage towards a lunar-polar flyby while
   * buying nothing.
   *
   * <p>The relative velocity depends on the aim point, so this would be circular. It is closed by
   * reading it off a provisional aim at the Moon's centre — one closed-form Lambert solve, no
   * propagation — and then holding the direction fixed, so the transfer plane never moves either.
   *
   * <p><b>The plane is the arc's own, and no longer a plane fabricated from the lunar direction</b>
   * (MIS-4 / L1, spec §3.1). That fabrication only agreed with the flown arc because {@link
   * #parkingState} had built the parking orbit from the very same normal; from an imposed plane the
   * two diverge, and the offset would be laid in a plane the spacecraft does not fly — tilting the
   * flyby against its own arc, which is precisely what this method exists to avoid. It is frozen on
   * the provisional centre aim for the same reason the relative velocity is: were it re-derived per
   * attempt, the offset direction would depend on the offset, and the monotonicity the bisection
   * rests on would no longer hold by construction.
   */
  private static Vector3D aimOffsetDirection(
      SpacecraftState parking, Vector3D moonAtArrival, AbsoluteDate arrival) {
    Vector3D normal = Vector3D.crossProduct(parking.getPosition(), moonAtArrival).normalize();
    TimeStampedPVCoordinates moon =
        OrekitService.get()
            .body(SolarSystemBody.MOON)
            .getPVCoordinates(arrival, OrekitService.get().gcrf());

    Vector3D arrivalVelocity =
        new LambertSolver(parking.getOrbit().getMu())
            .solve(true, 0, boundaryConditions(parking, arrival, moonAtArrival))
            .getTerminalVelocity();
    Vector3D relative = arrivalVelocity.subtract(moon.getVelocity());

    Vector3D inPlane = Vector3D.crossProduct(normal, relative).normalize();
    return inPlane.dotProduct(moon.getVelocity()) >= 0.0 ? inPlane : inPlane.negate();
  }

  /** The impulse, with the exact Tsiolkovsky mass drop. */
  private static SpacecraftState applyImpulse(
      SpacecraftState state, Vector3D deltaV, double exhaustVelocity) {
    double mass = state.getMass() * FastMath.exp(-deltaV.getNorm() / exhaustVelocity);
    return new SpacecraftState(
            new CartesianOrbit(
                new TimeStampedPVCoordinates(
                    state.getDate(),
                    state.getPosition(),
                    state.getPVCoordinates().getVelocity().add(deltaV)),
                state.getFrame(),
                state.getOrbit().getMu()))
        .withMass(mass);
  }

  private static NumericalPropagator propagator(FlightContext context, SpacecraftState initial) {
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, OrekitService.COAST_MAX_STEP);
    propagator.setInitialState(initial);
    return propagator;
  }

  private static Vector3D moonPosition(AbsoluteDate date) {
    return OrekitService.get()
        .body(SolarSystemBody.MOON)
        .getPosition(date, OrekitService.get().gcrf());
  }

  /** The lunar reference radius, read where L4 §1.4 found it rather than duplicated. */
  private static double lunarRadius() {
    return GravitationalContext.moon().shape().getEquatorialRadius();
  }
}
