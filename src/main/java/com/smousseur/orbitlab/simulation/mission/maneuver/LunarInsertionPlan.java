package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryGuard;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.attitudes.FrameAlignedProvider;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.ApsideDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * The lunar orbit insertion (MIS-5 / L4, spec {@code docs/lunar-orbit/06-conception-L4.md}) — where
 * a selenocentric approach reaches its perilune, how long before that the retrograde burn has to be
 * lit, and the burn that circularises there.
 *
 * <p><b>Two stages call it and neither knows the other</b>, exactly as {@code ParkingCoastStage}
 * and {@code TLIBurnStage} both call {@link TranslunarInjectionPlan}. The property that shape
 * protects is that <em>four</em> readings — two stages times the optimize and ephemeris passes —
 * must land on the same perilune, or the burn is calibrated on something other than what is flown.
 * It is the single-arithmetic rule {@code CoastingStage.cutoffFrom} was written for.
 *
 * <p>Far smaller than its translunar sibling, and for a reason worth stating: there is no Lambert
 * solve, no aim and no bisection on the perilune here. <b>The arrival is not chosen, it is
 * read.</b> What altitude the perilune has was decided upstream, by {@code TLIBurnStage}'s aim.
 */
public final class LunarInsertionPlan {
  private static final Logger logger = LogManager.getLogger(LunarInsertionPlan.class);

  /**
   * Max check interval of the perilune detector (s). Explicit, because the constructor that takes
   * an orbit cannot serve a hyperbola: it reads {@code getKeplerianPeriod()}, which is {@code
   * Infinity} when {@code a < 0}, and derives {@code maxCheck = Infinity / 3} and {@code threshold
   * = 1e-13 * Infinity} — both infinite (spec §1.2 pt 2).
   */
  private static final double APSIDE_MAX_CHECK_SECONDS = 60.0;

  /**
   * Date convergence of the perilune detector (s). Measured against a fine ternary search on the
   * radius: 8e-4 s at this setting, 70 ms for an 18 h approach. Tightening to 1e-6 buys the last
   * 8e-4 s for the same cost and is not needed — half a burn is 175 s.
   */
  private static final double APSIDE_THRESHOLD_SECONDS = 1.0e-3;

  /**
   * How far past the closed-form estimate the perilune is searched for.
   *
   * <p><b>This is the only legitimate use of the Keplerian closed form</b>, and the javadoc of
   * {@link #arrivalFrom} says why it is illegitimate everywhere else. As a bound it is excellent:
   * measured, it errs by at most 723 s on 64 169, so 1.1 %, and the worst short reading is 402 s
   * against the 19 250 s of slack this factor gives.
   */
  private static final double SEARCH_MARGIN = 1.3;

  /** Convergence on the post-burn semi-major axis (m). */
  private static final double SEMI_MAJOR_AXIS_TOLERANCE = 200.0;

  /** Convergence on the radial velocity at cut-off (m/s). */
  private static final double RADIAL_VELOCITY_TOLERANCE = 0.1;

  /** Iteration cap. Measured: three evaluations on every configuration tried. */
  private static final int MAX_ITERATIONS = 6;

  /** Offset of the burn start from the ignition state, as {@code TLIBurnStage} uses it (s). */
  private static final double BURN_START_OFFSET_SECONDS = 1.0e-3;

  private LunarInsertionPlan() {}

  /**
   * Where and when a selenocentric approach reaches its perilune.
   *
   * @param atPerilune the state at the flown perilune, in the approach's own frame
   * @param periluneAltitude the altitude above the lunar reference sphere there (m)
   */
  public record Arrival(SpacecraftState atPerilune, double periluneAltitude) {}

  /**
   * The insertion burn, to light at the state {@link #insert} was handed.
   *
   * @param direction the inertially fixed thrust direction
   * @param duration how long to thrust (s)
   * @param commandedDeltaV the ΔV the burn is commanded for (m/s)
   * @param impulsiveDeltaV the ΔV the impulse it replaces would have cost (m/s)
   * @param endMass the mass at cut-off (kg)
   * @param periluneAltitude the altitude the orbit is circularised at (m)
   */
  public record Burn(
      Vector3D direction,
      double duration,
      double commandedDeltaV,
      double impulsiveDeltaV,
      double endMass,
      double periluneAltitude) {}

  /**
   * Flies the approach and reads the perilune it actually reaches.
   *
   * <p><b>The perilune is detected, not computed, and that is the finding the lot is built on</b>
   * (spec §1.2 pt 1). Read off the hyperbolic anomaly of the state at the sphere, the time to
   * periapsis is wrong by −402 to +723 s — one to four half-burns — and the Keplerian perilune
   * altitude is wrong by hundreds of kilometres, to the point of predicting 100 km for a trajectory
   * that impacts. The error is the Earth's tide integrated over the approach: it decays smoothly
   * with the starting radius (+38.8 s from 30 000 km, +0.7 s from 10 000 km, +0.0 s from 2 000 km),
   * which is what tells it apart from a fixture artefact. At the sphere the tide and the Moon's own
   * pull are of the same order — that is what a sphere of influence <em>is</em>.
   *
   * @param selenocentric the approach state, in the lunar frame
   * @param context the environment the approach is flown in
   * @return the arrival
   * @throws OrbitlabException when the perilune is already behind, when the trajectory impacts
   *     before reaching it, or when the perilune it reaches is below the surface
   */
  public static Arrival arrivalFrom(SpacecraftState selenocentric, FlightContext context) {
    KeplerianOrbit osculating = new KeplerianOrbit(selenocentric.getOrbit());
    double estimate = -osculating.getMeanAnomaly() / osculating.getKeplerianMeanMotion();
    if (estimate <= 0.0) {
      throw new OrbitlabException(
          String.format(
              Locale.ROOT,
              "the perilune of this approach is %.0f s behind, not ahead: there is nothing to insert"
                  + " into",
              -estimate));
    }

    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, OrekitService.COAST_MAX_STEP);
    propagator.setInitialState(selenocentric);
    ReentryGuard.armQuiet(propagator, context.gravity());

    SpacecraftState[] hit = new SpacecraftState[1];
    propagator.addEventDetector(
        new ApsideDetector(selenocentric.getOrbit())
            .withMaxCheck(APSIDE_MAX_CHECK_SECONDS)
            .withThreshold(APSIDE_THRESHOLD_SECONDS)
            .withHandler(
                (state, detector, increasing) -> {
                  if (!increasing) {
                    return Action.CONTINUE;
                  }
                  hit[0] = state;
                  return Action.STOP;
                }));
    propagator.propagate(selenocentric.getDate().shiftedBy(estimate * SEARCH_MARGIN));

    if (hit[0] == null) {
      throw new OrbitlabException(
          String.format(
              Locale.ROOT,
              "no perilune within %.0f s of the approach: the re-entry guard stopped the search, so"
                  + " this hyperbola meets the surface rather than grazing it",
              estimate * SEARCH_MARGIN));
    }

    double altitude = hit[0].getPosition().getNorm() - context.gravity().equatorialRadius();
    if (altitude <= 0.0) {
      throw new OrbitlabException(
          String.format(
              Locale.ROOT,
              "the approach reaches its perilune %.1f km below the surface: it cannot be inserted"
                  + " into an orbit",
              -altitude / 1000.0));
    }
    return new Arrival(hit[0], altitude);
  }

  /**
   * How far ahead of the perilune the burn has to be lit for it to be centred on it (s) — <b>closed
   * form, no propagation</b>, the shape of {@link TranslunarInjectionPlan#ignitionLead}.
   *
   * <p><b>The closed form is right about the lead and wrong about the date</b>, and the same
   * measurement shows both (spec §2.3). At 175 s of range the vehicle is some 430 km from the
   * perilune, where the tide is nil: measured, the perilune re-read from the ignition state falls
   * within 1e-4 s of what this lead announced.
   *
   * @param arrival the arrival {@link #arrivalFrom} resolved
   * @param active the vehicle stage that will burn the insertion
   * @return the advance to subtract from the perilune date (s)
   */
  public static double ignitionLead(Arrival arrival, ActiveStageInfo active) {
    SpacecraftState state = arrival.atPerilune();
    PropulsionSystem propulsion = active.propulsion();
    return 0.5
        * Physics.computeBurnDurationCapped(
            impulsiveDeltaV(state),
            state.getMass(),
            propulsion.isp(),
            propulsion.thrust(),
            active.remainingFuel(state.getMass()));
  }

  /**
   * Calibrates the retrograde burn that circularises the approach at its own perilune.
   *
   * <p><b>Two targets, and together they say the orbit is circular</b>: the post-burn semi-major
   * axis equals the perilune radius, and the radial velocity at cut-off is zero — an apside at
   * radius {@code r} with {@code a = r} <em>is</em> a circle. <b>Two knobs</b>: β, a scale on the
   * aimed circular speed, and ζ, a rotation of the thrust <em>within the plane</em>, about {@code r
   * × v}.
   *
   * <p><b>It is a Newton and not a secant, because both slopes are closed form</b> (spec §1.2 pt
   * 4): {@code ∂a/∂β = −2r} and {@code ∂v_r/∂ζ = +Δv}, measured at 99.65 % and 99.41 % of the flown
   * values. The Jacobian is treated as diagonal: {@code ∂a/∂ζ} is 8.3 km/rad, negligible, and
   * {@code ∂v_r/∂β} is a 14 % contraction the next iteration absorbs. Measured across three
   * orientations and three altitudes: three evaluations, an achieved band of 0.30 km and {@code e ≤
   * 8.2e-5}.
   *
   * <p><b>Not the far apside</b>, which is what {@code AnalyticApogeeCircularizationStage}
   * converges — that stage burns at apogee, where the far apside is the free one. At a circular
   * target the criterion is degenerate: measured, the apolune "converged" from 117.4 to 100.9 km
   * while the perilune fell from 97.7 to 51.2 km.
   *
   * <p><b>The burn is lit at {@code ignitionState}, not re-centred on the perilune</b>, which is
   * {@code TLIBurnStage}'s shape and for a measured reason: re-centring gives the same orbit to the
   * millimetre and clamps in four cases out of six, the calibrated half-burn (175.7 s) exceeding
   * the closed-form lead (175.05 s) by 0.65 s. Whatever off-centring the lead leaves is absorbed
   * here, because the loop measures the burn that ignites at this very state.
   *
   * @param ignitionState the state this burn is lit at, half a burn short of the perilune
   * @param active the vehicle stage burning the insertion
   * @param context the environment the burn is flown in
   * @return the burn to light at {@code ignitionState}'s date
   * @throws OrbitlabException when the approach has no usable perilune, or when the burn would take
   *     the active stage below its depletion floor
   */
  public static Burn insert(
      SpacecraftState ignitionState, ActiveStageInfo active, FlightContext context) {
    Arrival arrival = arrivalFrom(ignitionState, context);
    SpacecraftState atPerilune = arrival.atPerilune();
    Vector3D position = atPerilune.getPosition();
    Vector3D velocity = atPerilune.getPVCoordinates().getVelocity();
    Vector3D momentum = Vector3D.crossProduct(position, velocity).normalize();
    double radius = position.getNorm();
    double circularSpeed = FastMath.sqrt(atPerilune.getOrbit().getMu() / radius);
    double impulsive = velocity.getNorm() - circularSpeed;

    PropulsionSystem propulsion = active.propulsion();
    double maxStep =
        OrekitService.burnLimitedMaxStep(
            new OrekitService.BurnSpec(
                propulsion.thrust(), propulsion.isp(), active.depletionFloor()));

    double carriedPropellant = active.remainingFuel(ignitionState.getMass());
    double exhaustSpeed = propulsion.isp() * Constants.G0_STANDARD_GRAVITY;

    // Refused before propagating anything, and that is not only an economy: the calibration flies
    // burns capped at the depletion floor, and on a tank an order of magnitude too small the capped
    // burn leaves a trajectory the integrator cannot follow — it throws "minimal step size reached"
    // from inside the loop, which is an unreadable way to say the vehicle cannot do this.
    requirePropellantFor(
        impulsive, "impulsive", ignitionState, carriedPropellant, active, exhaustSpeed);

    double beta = 0.0;
    double zeta = 0.0;
    Vector3D direction = velocity.normalize().negate();
    double commanded = impulsive;
    double duration = 0.0;
    SpacecraftState cutoff = null;

    for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
      commanded = impulsive + beta * circularSpeed;
      duration =
          Physics.computeBurnDurationCapped(
              commanded,
              ignitionState.getMass(),
              propulsion.isp(),
              propulsion.thrust(),
              carriedPropellant);
      direction =
          new Rotation(momentum, zeta, RotationConvention.VECTOR_OPERATOR)
              .applyTo(velocity.normalize().negate());
      cutoff = simulateBurn(ignitionState, direction, duration, propulsion, maxStep, context);

      KeplerianOrbit post =
          new KeplerianOrbit(
              cutoff.getPVCoordinates(),
              cutoff.getFrame(),
              cutoff.getDate(),
              atPerilune.getOrbit().getMu());
      double axisBias = post.getA() - radius;
      double radialSpeed =
          Vector3D.dotProduct(
              cutoff.getPosition().normalize(), cutoff.getPVCoordinates().getVelocity());
      if (FastMath.abs(axisBias) <= SEMI_MAJOR_AXIS_TOLERANCE
          && FastMath.abs(radialSpeed) <= RADIAL_VELOCITY_TOLERANCE) {
        break;
      }
      beta += axisBias / (2.0 * radius);
      zeta -= radialSpeed / commanded;
    }

    // The same verdict on the COMMANDED delta-V, which the finite loss puts above the impulsive
    // one the guard above cleared. It closes the narrow band where the impulse fits and the burn
    // that replaces it does not.
    requirePropellantFor(
        commanded, "commanded", ignitionState, carriedPropellant, active, exhaustSpeed);
    return new Burn(
        direction, duration, commanded, impulsive, cutoff.getMass(), arrival.periluneAltitude());
  }

  /**
   * Adds the insertion burn to a propagator, starting {@link #BURN_START_OFFSET_SECONDS} after
   * {@code state}.
   *
   * <p>Shared by the calibration loop and by the stage that flies the result, so the burn the loop
   * measured and the burn the mission flies are assembled by one expression rather than two. The
   * depletion guard is left to the caller, which is the only one that has a name to log under.
   *
   * @param propagator the propagator to add the burn to
   * @param state the ignition state
   * @param direction the inertially fixed thrust direction
   * @param duration how long to thrust (s)
   * @param propulsion the propulsion doing the thrusting
   */
  public static void addBurn(
      NumericalPropagator propagator,
      SpacecraftState state,
      Vector3D direction,
      double duration,
      PropulsionSystem propulsion) {
    propagator.addForceModel(
        new ConstantThrustManeuver(
            state.getDate().shiftedBy(BURN_START_OFFSET_SECONDS),
            duration,
            propulsion.thrust(),
            propulsion.isp(),
            new FrameAlignedProvider(new Rotation(direction, Vector3D.PLUS_I), state.getFrame()),
            Vector3D.PLUS_I));
  }

  /**
   * The date a burn lit at {@code ignitionState} cuts off, the cutoff both passes of the insertion
   * stage are judged against.
   *
   * @param ignitionState the ignition state
   * @param burn the calibrated burn
   * @return the cut-off date
   */
  public static AbsoluteDate cutoffDate(SpacecraftState ignitionState, Burn burn) {
    return ignitionState.getDate().shiftedBy(BURN_START_OFFSET_SECONDS + burn.duration());
  }

  /**
   * Logs what the finite burn costs above the impulse it replaces — the record spec §4.2 asks the
   * lot to produce. Measured on the fabricated approach: +3.45 m/s on 823.76, 0.41 %, 2.2 kg, over
   * 17.83° of arc.
   *
   * @param name the calling stage's name
   * @param ignitionState the ignition state
   * @param burn the calibrated burn
   * @param context the environment the burn is flown in, which is what turns the burn's altitude
   *     back into the radius the achieved period is read from
   */
  public static void logBurn(
      String name, SpacecraftState ignitionState, Burn burn, FlightContext context) {
    double mu = ignitionState.getOrbit().getMu();
    double radius = context.gravity().equatorialRadius() + burn.periluneAltitude();
    double period = 2.0 * FastMath.PI * FastMath.sqrt(radius * radius * radius / mu);
    logger.info(
        "[{}] lunar insertion: dt={} s ({}° of arc), commanded {} m/s for {} m/s impulsive (+{}),"
            + " mass {} -> {} kg, circularising a {} km perilune",
        name,
        FastMath.round(burn.duration()),
        String.format(Locale.ROOT, "%.2f", 360.0 * burn.duration() / period),
        FastMath.round(burn.commandedDeltaV()),
        FastMath.round(burn.impulsiveDeltaV()),
        String.format(Locale.ROOT, "%.2f", burn.commandedDeltaV() - burn.impulsiveDeltaV()),
        FastMath.round(ignitionState.getMass()),
        FastMath.round(burn.endMass()),
        String.format(Locale.ROOT, "%.1f", burn.periluneAltitude() / 1000.0));
  }

  /** The propellant a ΔV costs from a given mass, by Tsiolkovsky (kg). */
  private static double propellantFor(double deltaV, double mass, double exhaustSpeed) {
    return mass * (1.0 - FastMath.exp(-deltaV / exhaustSpeed));
  }

  /**
   * Refuses a ΔV the active stage does not carry the propellant for.
   *
   * <p><b>Judged on the propellant the burn needs, not on the mass it would leave</b>, and that is
   * what makes the verdict reachable: {@code Physics.computeBurnDurationCapped} stops a burn at the
   * depletion floor, so a mass below that floor cannot occur however undersized the tank is.
   */
  private static void requirePropellantFor(
      double deltaV,
      String kind,
      SpacecraftState ignitionState,
      double carriedPropellant,
      ActiveStageInfo active,
      double exhaustSpeed) {
    double needed = propellantFor(deltaV, ignitionState.getMass(), exhaustSpeed);
    if (needed <= carriedPropellant) {
      return;
    }
    throw new OrbitlabException(
        String.format(
            Locale.ROOT,
            "the %.0f m/s %s insertion ΔV needs %.0f kg of propellant and the active stage carries"
                + " %.0f kg above its %.0f kg depletion floor — it cannot fly this capture",
            deltaV,
            kind,
            needed,
            carriedPropellant,
            active.depletionFloor()));
  }

  /** The impulsive retrograde ΔV that would circularise a state at its own radius (m/s). */
  private static double impulsiveDeltaV(SpacecraftState state) {
    double speed = state.getPVCoordinates().getVelocity().getNorm();
    return speed - FastMath.sqrt(state.getOrbit().getMu() / state.getPosition().getNorm());
  }

  /** Flies one candidate burn and returns the state at cut-off. */
  private static SpacecraftState simulateBurn(
      SpacecraftState ignitionState,
      Vector3D direction,
      double duration,
      PropulsionSystem propulsion,
      double maxStep,
      FlightContext context) {
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, maxStep);
    propagator.setInitialState(ignitionState);
    ReentryGuard.armQuiet(propagator, context.gravity());
    addBurn(propagator, ignitionState, direction, duration, propulsion);
    return propagator.propagate(
        ignitionState.getDate().shiftedBy(BURN_START_OFFSET_SECONDS + duration));
  }
}
