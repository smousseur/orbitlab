package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.control.heuristics.lambert.LambertBoundaryConditions;
import org.orekit.control.heuristics.lambert.LambertBoundaryVelocities;
import org.orekit.control.heuristics.lambert.LambertDifferentialCorrector;
import org.orekit.control.heuristics.lambert.LambertSolver;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * The analytically injected translunar burn of PHY-4 / L6 (spec {@code
 * docs/multi-corps/08-conception-L6.md} §4): a patched-conic seed from a parking orbit, aimed so
 * the <em>flown</em> perilune reaches a target altitude.
 *
 * <p><b>This is an acceptance fixture, not a mission stage of the product.</b> A real {@code
 * TLIBurnStage} — with a launch window, a finite burn and an optimizer — is {@code MIS-4}, which
 * the découpage puts outside PHY-4. What this class exists to do is give the sphere-of-influence
 * machinery of L4 and the two-scale rendering of L5 one real trajectory to be judged on.
 *
 * <p><b>The geometry is built so that no launch window has to be searched.</b> The transfer plane
 * is derived from where the Moon <em>will be</em> at arrival, then the parking orbit is derived
 * from that plane — rather than taking a parking orbit and waiting for the Moon to line up with it.
 * There is no ground site here, so nothing makes that illegitimate, and it is what keeps the
 * découpage's "PHY-4 does not depend on MIS-2" true.
 *
 * <p><b>Why the burn is impulsive.</b> Lambert <em>is</em> the impulsive formulation, and for an
 * impulse Tsiolkovsky is exact rather than an approximation. A finite burn would under-deliver and
 * miss the Moon in energy as much as in miss distance, which would need two nested knobs instead of
 * one (spec §8). This lot has nothing to prove about modelling an engine and a great deal to prove
 * about switching central body.
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
   * How far past the aim date the perilune search runs (s). Closest approach need not fall exactly
   * on the aim date — the Moon moves, so the aim point is near but not on the relative-velocity
   * perpendicular — and half a day either side brackets it comfortably.
   */
  private static final double PERILUNE_SEARCH_MARGIN_SECONDS = 0.5 * 86_400.0;

  /**
   * The parking state a translunar mission starts from.
   *
   * <p>Circular at {@link #PARKING_ALTITUDE}, inclined at {@link #PARKING_INCLINATION}, in the
   * plane that contains the Moon's direction at {@code injectionDate + TIME_OF_FLIGHT_SECONDS}, at
   * the point {@link #TRANSFER_ANGLE} short of that direction.
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
   * The unit normal of the transfer plane: inclined at {@link #PARKING_INCLINATION} and containing
   * {@code moonDirection}.
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
            exhaustVelocity,
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
          attempt(
              parking, arrival, moonAtArrival, offsetDirection, offset, exhaustVelocity, context);
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
                  + " is not the one asked for — and at a negative altitude, an impact presented as"
                  + " a plan (spec docs/multi-corps/08-conception-L6.md §10)",
              targetPerileneAltitude / 1000.0,
              achievedAltitude / 1000.0,
              bracket.low() / 1000.0,
              bracket.high() / 1000.0,
              AIM_ITERATIONS));
    }
    double miss = measurePlanVersusFlight(parking, best, arrival, exhaustVelocity, context);
    logger.info(
        "TLI plan: dv={} m/s, offset={} km, perilune altitude={} km",
        FastMath.round(best.deltaV().getNorm()),
        FastMath.round(best.offset() / 1000.0),
        FastMath.round(achievedAltitude / 1000.0));

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
   * What the injection costs at an epoch, on the Lambert seed alone — <b>closed form, no
   * propagation, microseconds</b>.
   *
   * <p>The seam MIS-2 screens epochs through ({@code TranslunarInjectionPlanWindowProblem}). It
   * aims at the Moon's centre rather than at an offset aim point, which is the right call for a
   * <em>ranking</em> criterion: the offset is worth a handful of m/s against the three-odd km/s of
   * the injection, and resolving it is what costs the thirty propagations {@link #solve} spends.
   * The verdict on a screened epoch stays with {@code solve}.
   *
   * @param injectionDate the date the impulse is applied
   * @param mass the spacecraft mass at injection (kg)
   * @return the magnitude of the injection impulse (m/s)
   * @throws OrbitlabException when the geometry at that epoch admits no transfer plane
   */
  public static double keplerianInjectionDeltaV(AbsoluteDate injectionDate, double mass) {
    SpacecraftState parking = parkingState(injectionDate, mass);
    AbsoluteDate arrival = injectionDate.shiftedBy(TIME_OF_FLIGHT_SECONDS);
    Vector3D seed =
        keplerianSeedVelocity(parking, boundaryConditions(parking, arrival, moonPosition(arrival)));
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
   * Finds an offset bracket around the target perilune by walking outwards from the target radius.
   *
   * <p>The map is monotone — aiming further from the Moon's centre passes further from it — so one
   * side of the bracket is the target radius itself and the other is found by doubling. Doubling
   * rather than stepping because the ratio between the aim offset and the achieved perilune depends
   * on the encounter geometry and is not close to one: at the epoch this lot measured, a 12 825 km
   * aim gave a 103 km perilune.
   */
  private static Bracket bracket(
      SpacecraftState parking,
      AbsoluteDate arrival,
      Vector3D moonAtArrival,
      Vector3D offsetDirection,
      double targetRadius,
      double exhaustVelocity,
      FlightContext context,
      double targetPerileneAltitude) {

    Attempt at =
        attempt(
            parking,
            arrival,
            moonAtArrival,
            offsetDirection,
            targetRadius,
            exhaustVelocity,
            context);
    logAim("bracket 0", targetRadius, at, targetPerileneAltitude);

    if (at.perileneRadius() >= targetRadius) {
      // Already above the target: walk inwards, floored at the lunar radius. Below that the aim
      // point
      // is inside the Moon and the perilune reading has no meaning.
      double high = targetRadius;
      Attempt atHigh = at;
      double low = 0.5 * (lunarRadius() + targetRadius);
      for (int i = 0; i < BRACKET_STEPS; i++) {
        Attempt low_ =
            attempt(
                parking, arrival, moonAtArrival, offsetDirection, low, exhaustVelocity, context);
        logAim("bracket in " + i, low, low_, targetPerileneAltitude);
        if (low_.perileneRadius() < targetRadius) {
          return new Bracket(low, high, low_, atHigh);
        }
        high = low;
        atHigh = low_;
        low = 0.5 * (lunarRadius() + low);
      }
      return new Bracket(low, high, atHigh, atHigh);
    }

    double low = targetRadius;
    Attempt atLow = at;
    double high = 2.0 * targetRadius;
    for (int i = 0; i < BRACKET_STEPS; i++) {
      Attempt high_ =
          attempt(parking, arrival, moonAtArrival, offsetDirection, high, exhaustVelocity, context);
      logAim("bracket out " + i, high, high_, targetPerileneAltitude);
      if (high_.perileneRadius() >= targetRadius) {
        return new Bracket(low, high, atLow, high_);
      }
      low = high;
      atLow = high_;
      high *= 2.0;
    }
    return new Bracket(low, high, atLow, atLow);
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
      double exhaustVelocity,
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
    SpacecraftState injected = applyImpulse(parking, deltaV, exhaustVelocity);

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
   * <p>Posigrade because the transfer plane's normal has a positive vertical component by
   * construction ({@link #PARKING_INCLINATION} is well under 90°), and zero full revolutions
   * because the transfer is a single arc short of half a turn ({@link #TRANSFER_ANGLE}).
   */
  static Vector3D keplerianSeedVelocity(
      SpacecraftState parking, LambertBoundaryConditions conditions) {
    return new LambertSolver(parking.getOrbit().getMu())
        .solve(true, 0, conditions)
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
   */
  private static Vector3D aimOffsetDirection(
      SpacecraftState parking, Vector3D moonAtArrival, AbsoluteDate arrival) {
    Vector3D moonDirection = moonAtArrival.normalize();
    Vector3D normal = transferPlaneNormal(moonDirection);
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

  private static NumericalPropagator propagator(
      FlightContext context, SpacecraftState initial) {
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
