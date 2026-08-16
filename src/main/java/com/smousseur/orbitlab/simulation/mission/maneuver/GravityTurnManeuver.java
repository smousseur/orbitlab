package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.attitude.GravityTurnAttitudeProvider;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionStopTrigger;
import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryGuard;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentPlan;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentPropagation;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.forces.maneuvers.Maneuver;
import org.orekit.forces.maneuvers.propulsion.BasicConstantThrustPropulsionModel;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.events.handlers.EventHandler;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Decodes the gravity turn's CMA-ES variables into the dated {@link AscentPlan} the three ascent
 * phases fly, and holds the launcher-dependent quantities that plan is built from (burn 1 duration,
 * staging completion, depletion floor, integrator max step).
 *
 * <p>Stage resolution is automatic: the active stage and the next stage after jettison are
 * determined from the vehicle's reference mass via {@link Vehicle#resolveActiveStage(double)}.
 *
 * <p><b>The single-propagator ascent below is the pre-split reference, not a production path.</b>
 * {@link #configure} and {@link #propagateForOptimization} build one propagator carrying burn 1,
 * the jettison detector and burn 2 — the way the ascent was flown before it became {@code Gravity
 * turn (S1) → S1 separation → Gravity turn (S2)}. No mission uses them: they are kept because the
 * migration's non-regression fixtures are defined <em>against</em> them (spec {@code
 * docs/mission-stages/02-baseline-n2.md} §5), and étape 5 still has a behaviour change to measure
 * from that reference.
 */
public class GravityTurnManeuver {

  private static final Logger logger = LogManager.getLogger(GravityTurnManeuver.class);

  private final Vehicle vehicle;
  private final double entryMass;
  private final double pitchKickAngleRad;
  private final double launchAzimuth;
  private final double interstageCoastDuration;
  private final boolean commandedPlane;
  private final ActiveStageInfo activeStage;
  private final ActiveStageInfo nextStage;
  // Stored per-thread so parallel CMA-ES exploration runs can call propagateForOptimization()
  // concurrently without overwriting each other's tracker (matches TransferProblem.lastResult).
  private final ThreadLocal<MinAltitudeTracker> lastAltitudeTracker = new ThreadLocal<>();

  /**
   * Creates a gravity turn maneuver for the given vehicle and launch parameters.
   *
   * @param vehicle the vehicle performing the maneuver (must have at least two stages)
   * @param entryMass the actual spacecraft mass at gravity turn entry (kg); already reflects any
   *     propellant burnt during the vertical ascent
   * @param pitchKickAngleRad the initial pitch kick angle in radians
   * @param launchAzimuth the launch azimuth angle in radians (measured from north)
   * @param interstageCoastDuration unpowered coast between jettison and next-stage ignition (s)
   */
  public GravityTurnManeuver(
      Vehicle vehicle,
      double entryMass,
      double pitchKickAngleRad,
      double launchAzimuth,
      double interstageCoastDuration) {
    this(vehicle, entryMass, pitchKickAngleRad, launchAzimuth, interstageCoastDuration, false);
  }

  /**
   * Creates a gravity turn maneuver, optionally steering into the plane the azimuth defines.
   *
   * @param vehicle the vehicle performing the maneuver (must have at least two stages)
   * @param entryMass the actual spacecraft mass at gravity turn entry (kg); already reflects any
   *     propellant burnt during the vertical ascent
   * @param pitchKickAngleRad the initial pitch kick angle in radians
   * @param launchAzimuth the launch azimuth angle in radians (measured from north)
   * @param interstageCoastDuration unpowered coast between jettison and next-stage ignition (s)
   * @param commandedPlane {@code true} to steer the turn into the plane the azimuth defines, {@code
   *     false} to follow the plane the kick leaves behind — the historical, calibrated behaviour
   *     (spec {@code docs/earth-orbit/01-mission-terre-parametrable.md} §4.2)
   */
  public GravityTurnManeuver(
      Vehicle vehicle,
      double entryMass,
      double pitchKickAngleRad,
      double launchAzimuth,
      double interstageCoastDuration,
      boolean commandedPlane) {
    this.vehicle = vehicle;
    this.entryMass = entryMass;
    this.pitchKickAngleRad = pitchKickAngleRad;
    this.launchAzimuth = launchAzimuth;
    this.interstageCoastDuration = interstageCoastDuration;
    this.commandedPlane = commandedPlane;
    this.activeStage = vehicle.resolveActiveStage(entryMass);
    this.nextStage = vehicle.resolveActiveStage(activeStage.massAfterJettison());
  }

  /**
   * Decodes raw CMA-ES optimization variables into the fully dated ascent schedule. The burn
   * durations are derived from the propellant remaining at gravity turn entry, and every date the
   * ascent hangs off is fixed here — this is the single place they are computed, which is what lets
   * the ascent be flown either as one propagation or as three phases without drifting (spec 01
   * §5.1).
   *
   * @param entryState the state at gravity turn entry; only its date is read, and the pitch kick
   *     preserves it, so the pre-kick and kicked states give the same plan
   * @param variables the raw optimization variable array (transitionTime, exponent)
   * @return the ascent plan
   */
  public AscentPlan plan(SpacecraftState entryState, double[] variables) {
    double transitionTime = variables[0];
    double exponent = variables[1];

    // Burn1 duration until propellant exhaustion
    double burn1Duration = getBurn1Duration();

    // Burn2 duration after jettison and interstage coast, until transitionTime
    double burn2Duration = transitionTime - burn1Duration - interstageCoastDuration;
    burn2Duration = FastMath.max(0.0, burn2Duration);

    return new AscentPlan(
        entryState.getDate(),
        transitionTime,
        exponent,
        burn1Duration,
        interstageCoastDuration,
        burn2Duration,
        maxStepSeconds(),
        activeStage,
        nextStage,
        commandedPlaneNormal(entryState));
  }

  /**
   * The unit normal of the plane this ascent steers into, or {@code null} when none is commanded.
   *
   * <p>Built once, at the kick, from the site direction {@code r̂₀} and the commanded azimuth (spec
   * §4.1):
   *
   * <pre>
   *   û_A = cos A · n̂ + sin A · ê      the commanded horizontal direction
   *   ĥ   = (r̂₀ × û_A).normalize()     the normal of the plane it opens
   * </pre>
   *
   * <p>Only the <em>position</em> of the entry state is read, and the pitch kick preserves it, so
   * the pre-kick and post-kick states yield the same plane — which is what lets the optimize pass
   * (which plans from the pre-kick state) and the replay pass (which plans from the kicked one) fly
   * the same ascent.
   */
  private Vector3D commandedPlaneNormal(SpacecraftState entryState) {
    if (!commandedPlane) {
      return null;
    }
    Vector3D site = entryState.getPosition();
    Vector3D horizontal = Physics.localHorizontalDirection(site, launchAzimuth);
    return Vector3D.crossProduct(site.normalize(), horizontal).normalize();
  }

  /**
   * Applies the initial pitch kick to the spacecraft state, marking the entry into the gravity
   * turn. The kick rotates the velocity vector by the configured pitch angle along the launch
   * azimuth.
   *
   * @param state the spacecraft state before the pitch kick
   * @return the spacecraft state after the pitch kick has been applied
   */
  public SpacecraftState applyKick(SpacecraftState state) {
    return Physics.applyPitchKick(state, pitchKickAngleRad, launchAzimuth);
  }

  /**
   * Configures the given propagator with the gravity turn thrust maneuver and MECO event. This is
   * THE single source of truth for gravity turn configuration.
   *
   * @param propagator the propagator to configure
   * @param plan the ascent plan produced by {@link #plan}
   */
  public void configure(NumericalPropagator propagator, AscentPlan plan) {
    GravityTurnAttitudeProvider attitudeProvider =
        new GravityTurnAttitudeProvider(
            plan.kickDate(),
            plan.transitionTime(),
            plan.exponent(),
            plan.commandedPlaneNormal());
    propagator.setAttitudeProvider(attitudeProvider);

    // Burn 1 — active stage propulsion, flame-out semantics (spec 06 I4b): the engine thrusts
    // until stage 1's depletion floor instead of a date window, so the load can vary (outer
    // propellant-sizing loop) without recomputing the window. The analytic burn1Duration remains
    // the schedule prediction for the jettison and burn 2 dates below.
    PropulsionSystem propulsion1 = plan.firstStage().propulsion();
    Maneuver burn1 =
        new Maneuver(
            null,
            new DepletionStopTrigger(plan.firstIgnitionDate(), plan.firstStage().depletionFloor()),
            new BasicConstantThrustPropulsionModel(
                propulsion1.thrust(), propulsion1.isp(), Vector3D.PLUS_I, "GT-burn1"));
    propagator.addForceModel(burn1);

    // Jettison — mass drops to the reference mass of all stages above
    double massAfterJettison = plan.massAfterJettison();
    DateDetector jettisonDetector =
        new DateDetector(plan.jettisonDate())
            .withHandler(
                new EventHandler() {
                  @Override
                  public Action eventOccurred(
                      SpacecraftState s, EventDetector detector, boolean increasing) {
                    return Action.RESET_STATE;
                  }

                  @Override
                  public SpacecraftState resetState(
                      EventDetector detector, SpacecraftState oldState) {
                    return oldState.withMass(massAfterJettison);
                  }
                });
    propagator.addEventDetector(jettisonDetector);
    // Burn 2 — next stage propulsion (after jettison and interstage coast)
    PropulsionSystem propulsion2 = plan.secondStage().propulsion();
    ConstantThrustManeuver burn2 =
        new ConstantThrustManeuver(
            plan.secondIgnitionDate(),
            plan.burn2Duration(),
            propulsion2.thrust(),
            propulsion2.isp(),
            Vector3D.PLUS_I);
    propagator.addForceModel(burn2);
  }

  /**
   * Returns the depletion floor guarding this maneuver: the post-jettison stack floor. A single
   * detector at this floor covers both burns — during burn 1 the mass stays above stage 1's own
   * floor, which is above this one. Burn 2's window is transition-time-driven, not fuel-capped, so
   * this is where a wrong mass accounting would burn nonexistent propellant (spec 06 I4a).
   */
  public double getDepletionFloor() {
    return nextStage.depletionFloor();
  }

  /**
   * Integrator max step keeping the late-ignition invariant for this maneuver. Burn 2 (the next
   * stage) ignites after the interstage coast, so a coast-sized trial step at its ignition mass
   * ({@link ActiveStageInfo#massAfterJettison()}, the full post-jettison stack) must not drive the
   * mass negative. Burn 1 fires immediately (no preceding coast, so no late-ignition hazard) and is
   * excluded. See {@link OrekitService#burnLimitedMaxStep}.
   *
   * @return the integrator max step in seconds
   */
  public double maxStepSeconds() {
    PropulsionSystem propulsion2 = nextStage.propulsion();
    return OrekitService.burnLimitedMaxStep(
        new OrekitService.BurnSpec(
            propulsion2.thrust(), propulsion2.isp(), activeStage.massAfterJettison()));
  }

  /**
   * Propagates the trajectory for optimization purposes (creates its own propagator). Returns a
   * penalizing fallback state on error.
   */
  public SpacecraftState propagateForOptimization(
      SpacecraftState initialState, double[] variables) {
    SpacecraftState kickedState = applyKick(initialState);
    AscentPlan plan = plan(kickedState, variables);

    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(plan.maxStepSeconds());
    propagator.setInitialState(kickedState);
    configure(propagator, plan);
    // Quiet guard: infeasible candidates crossing the floor are truncated (and thus penalized by
    // the cost function) instead of burning nonexistent propellant.
    DepletionGuard.armQuiet(propagator, getDepletionFloor());
    // Same rationale one level down: a candidate whose turn is too aggressive flies back into the
    // ground, and the tracker below only *records* that — it does not stop.
    ReentryGuard.armQuiet(propagator);
    MinAltitudeTracker tracker = new MinAltitudeTracker(0.0, Double.POSITIVE_INFINITY);
    propagator.addEventDetector(tracker);
    this.lastAltitudeTracker.set(tracker);
    AbsoluteDate endDate = plan.mecoDate();

    try {
      return propagator.propagate(endDate);
    } catch (Exception e) {
      logger.debug("Gravity turn propagation failed (penalty applied): {}", e.getMessage());
      return kickedState; // penalty
    }
  }

  /**
   * Returns the altitude tracker attached to the most recent {@link #propagateForOptimization} call
   * on the calling thread, or {@code null} if no propagation has been performed on this thread yet.
   * Stored per-thread so parallel CMA-ES exploration runs don't overwrite each other's tracker.
   *
   * @return the last altitude tracker for this thread, or {@code null}
   */
  public MinAltitudeTracker getLastAltitudeTracker() {
    return lastAltitudeTracker.get();
  }

  /**
   * Exposes this maneuver as the historical single-propagator way of flying a gravity-turn
   * candidate, for {@link
   * com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnProblem}. The
   * alternative is {@link
   * com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentChainPropagation}, which flies the
   * three explicit phases instead; the problem is indifferent to which it is handed.
   *
   * @return this maneuver, seen as an ascent propagation
   */
  public AscentPropagation asPropagation() {
    return new AscentPropagation() {
      @Override
      public SpacecraftState propagate(SpacecraftState entryState, double[] variables) {
        return propagateForOptimization(entryState, variables);
      }

      @Override
      public MinAltitudeTracker lastAltitudeTracker() {
        return getLastAltitudeTracker();
      }
    };
  }

  /**
   * Returns the duration of burn 1, computed from the propellant remaining in the active stage at
   * gravity turn entry. The first stage fires until propellant exhaustion.
   *
   * @return the burn 1 duration in seconds
   */
  public double getBurn1Duration() {
    PropulsionSystem prop1 = activeStage.propulsion();
    double massFlowRate1 = prop1.thrust() / (prop1.isp() * Constants.G0_STANDARD_GRAVITY);
    return activeStage.remainingFuel(entryMass) / massFlowRate1;
  }

  /**
   * Earliest MECO that still completes first-stage staging: burn 1 run to depletion plus the
   * interstage settling coast. Burn 2 has zero duration exactly at this time.
   *
   * <p><b>It used to be a safety invariant; it is now the edge of a useless plateau.</b> While the
   * jettison was a {@link DateDetector} inside {@link #configure}, a transition time below this
   * value ended the propagation before the detector fired: burn 1 truncated, the first stage never
   * dropped, and it stayed active for every downstream phase — silent and knife-edge, on the GEO
   * profile CMA-ES once settled at 149.6 s against a 150.0 s burn 1, stranding 3.3 t in S1, after
   * which the "S2 separation" jettisoned S1 in its place and S2 flew the payload kick motor's burns
   * (bilan 10 §5.3). The ascent now drops the stage in a phase of its own, so that cannot happen.
   *
   * <p>What remains below this value is a region where the transition time controls nothing: every
   * candidate flies the same ascent and ends at the same jettison coast. The optimizer's staging
   * penalty still keeps CMA-ES out of it — not as a guard rail any more, but because exploring a
   * plateau costs budget for nothing (measured: +47 % evaluations on the LEO profile when it was
   * removed, spec {@code docs/mission-stages/02-baseline-n2.md} §12).
   *
   * @return the earliest transition time that completes staging, in seconds
   */
  public double getStagingCompleteTime() {
    return getBurn1Duration() + interstageCoastDuration;
  }

  /**
   * Returns the vehicle performing this maneuver.
   *
   * @return the vehicle
   */
  public Vehicle getVehicle() {
    return vehicle;
  }
}
