package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionStopTrigger;
import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnProblem;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.forces.maneuvers.Maneuver;
import org.orekit.forces.maneuvers.propulsion.BasicConstantThrustPropulsionModel;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * First powered phase of the explicit ascent: the pitch kick, then the first stage burning to
 * flame-out, ending at the jettison date. It owns the CMA-ES problem of the whole gravity turn and
 * publishes the {@link AscentPlan} the two phases after it read.
 *
 * <p><b>Why it owns the optimization.</b> The turn is optimized as a whole — {@code transitionTime}
 * is the MECO of the <em>second</em> burn — so the problem must fly all three phases. It does so
 * through the same {@code StageChainRunner} the ephemeris pass uses (spec {@code
 * docs/mission-stages/01-separations-implicites.md} §5.4), which is what keeps the two passes on
 * the same sequence of integrator restarts. {@link #advancesByReplay()} then tells {@code
 * MissionOptimizer} not to advance the mission from {@code problem.propagate()}, since the loop
 * itself walks the two phases that follow.
 */
public class GravityTurnFirstBurnStage extends GravityTurnBurnStage
    implements OptimizableMissionStage<GravityTurnProblem> {

  private static final Logger logger = LogManager.getLogger(GravityTurnFirstBurnStage.class);

  /**
   * The optimization key of the whole gravity turn. Deliberately <b>not</b> the phase name: the
   * result map is keyed by string, and keeping the historical key leaves every result already
   * stored (including in a running session) valid across the split (spec §5.2).
   */
  public static final String OPTIMIZATION_KEY = "Gravity turn";

  private final double pitchKickAngleDeg;
  private final double interstageCoastDuration;
  private final double launchLatitude;
  private final double targetInclination;
  private final GravityTurnConstraints constraints;

  private OptimizationResult optimizationResult;

  /**
   * Creates the first burn phase of an ascent.
   *
   * @param name the human-readable phase name
   * @param planRef the reference the three ascent phases share
   * @param pitchKickAngleDeg the pitch kick opening the turn (degrees)
   * @param interstageCoastDuration the unpowered coast between jettison and second ignition (s)
   * @param launchLatitude the launch site latitude (degrees)
   * @param targetInclination the target orbit inclination (degrees)
   * @param constraints the apogee, velocity and flight-path-angle targets of the turn
   * @param instrumentation the guards this phase arms (replay or optimize)
   */
  public GravityTurnFirstBurnStage(
      String name,
      AscentPlanRef planRef,
      double pitchKickAngleDeg,
      double interstageCoastDuration,
      double launchLatitude,
      double targetInclination,
      GravityTurnConstraints constraints,
      AscentInstrumentation instrumentation) {
    super(name, planRef, instrumentation);
    this.pitchKickAngleDeg = pitchKickAngleDeg;
    this.interstageCoastDuration = interstageCoastDuration;
    this.launchLatitude = launchLatitude;
    this.targetInclination = targetInclination;
    this.constraints = constraints;
  }

  @Override
  public String optimizationKey() {
    return OPTIMIZATION_KEY;
  }

  @Override
  public boolean advancesByReplay() {
    return true;
  }

  @Override
  public void applyOptimization(OptimizationResult result) {
    this.optimizationResult = result;
  }

  @Override
  public GravityTurnProblem buildProblem(Mission mission) {
    SpacecraftState entryState = mission.getCurrentState();
    GravityTurnManeuver maneuver = createManeuver(mission, entryState.getMass());
    return new GravityTurnProblem(
        maneuver, entryState, constraints, new AscentChainPropagation(this, maneuver, mission));
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    // The pitch kick is applied in configure(), not here (bilan 11 §3.9): the ephemeris generator
    // may override enter()'s result with the entry state saved during optimization, so a kick
    // applied here could be discarded. Entering is therefore a no-op.
    return previousState;
  }

  @Override
  public double maxStepSeconds(SpacecraftState entryState, Mission mission) {
    // Deliberately computed rather than read from the plan: this phase is the one that publishes
    // the plan, and the MissionOptimizer loop asks for the step before configure() has run. The
    // value is the same by construction — AscentPlan.maxStepSeconds() is this very number.
    return createManeuver(mission, entryState.getMass()).maxStepSeconds();
  }

  @Override
  protected void prepare(NumericalPropagator propagator, Mission mission) {
    SpacecraftState entryState = propagator.getInitialState();
    GravityTurnManeuver maneuver = createManeuver(mission, entryState.getMass());

    // Apply the pitch kick here (bilan 11 §3.9): the generator replays the turn from the pre-kick
    // entry state it saved, so without this the ascent would fly from an un-kicked velocity — 3°
    // off on Falcon Heavy. The optimize pass runs this very phase, so both passes start
    // identically.
    // The kick preserves date, position and mass; only the velocity heading changes.
    SpacecraftState kickedState = maneuver.applyKick(entryState);
    propagator.setInitialState(kickedState);

    if (optimizationResult != null) {
      // Replay path: this is the single place the schedule is derived from the retained variables,
      // and the two phases after this one read it from the shared reference.
      AscentPlan plan = maneuver.plan(kickedState, optimizationResult.bestVariables());
      checkStagingInvariant(plan);
      logPlan(plan);
      planRef.set(plan);
    }
    // Optimize path: AscentChainPropagation published this evaluation's plan before running the
    // chain, on a ref private to that evaluation. Nothing to publish here.
  }

  @Override
  protected void configureBurn(NumericalPropagator propagator, AscentPlan plan) {
    // Flame-out semantics (spec 06 I4b): the first stage thrusts until its own depletion floor
    // rather than for a fixed window, so a varying propellant load needs no window recomputation.
    // The analytic burn1Duration stays the schedule prediction the jettison date hangs off.
    PropulsionSystem propulsion = plan.firstStage().propulsion();
    propagator.addForceModel(
        new Maneuver(
            null,
            new DepletionStopTrigger(plan.firstIgnitionDate(), plan.firstStage().depletionFloor()),
            new BasicConstantThrustPropulsionModel(
                propulsion.thrust(), propulsion.isp(), Vector3D.PLUS_I, "GT-burn1")));
  }

  @Override
  protected AbsoluteDate endDate(AscentPlan plan, SpacecraftState entryState) {
    // Hands over exactly where the single-propagator ascent fired its jettison DateDetector.
    return plan.jettisonDate();
  }

  /**
   * Builds a chain of three phases equivalent to this one and its siblings, private to one CMA-ES
   * evaluation: fresh stage instances, a fresh plan reference, and optimize-mode instrumentation
   * sharing {@code tracker}. Parallel explorations therefore never touch each other's schedule.
   *
   * @param planRef the reference this evaluation's plan is published on
   * @param tracker the altitude tracker both burns of this evaluation report to
   * @return the three ascent phases, in order
   */
  List<MissionStage> optimizationChain(AscentPlanRef planRef, MinAltitudeTracker tracker) {
    AscentInstrumentation instrumentation = AscentInstrumentation.optimizing(tracker);
    return List.of(
        new GravityTurnFirstBurnStage(
            getName(),
            planRef,
            pitchKickAngleDeg,
            interstageCoastDuration,
            launchLatitude,
            targetInclination,
            constraints,
            instrumentation),
        AscentSequence.silentSeparation(interstageCoastDuration),
        new GravityTurnSecondBurnStage(AscentSequence.SECOND_BURN_NAME, planRef, instrumentation));
  }

  private GravityTurnManeuver createManeuver(Mission mission, double entryMass) {
    Vehicle vehicle = mission.getVehicle();
    double launchAzimuth = Physics.getLaunchAzimuth(launchLatitude, targetInclination);
    return new GravityTurnManeuver(
        vehicle,
        entryMass,
        Math.toRadians(pitchKickAngleDeg),
        launchAzimuth,
        interstageCoastDuration);
  }

  /**
   * Refuses a MECO scheduled before staging completes.
   *
   * <p><b>What it no longer guards.</b> While the jettison was a {@code DateDetector} inside the
   * ascent, such a schedule ended the propagation before it fired and the first stage stayed
   * attached for the rest of the mission (bilan 10 §5.3). That cannot happen now: the jettison is a
   * phase, so it takes place whatever the MECO.
   *
   * <p><b>What it guards instead.</b> An assertion that should never fire. The optimizer's staging
   * penalty keeps retained solutions above this floor, so reaching here means a schedule arrived
   * from somewhere else — a hand-injected result, a replay of a stale optimization, a future caller
   * bypassing the problem. Such a schedule is degenerate rather than dangerous (zero-length second
   * burn, ascent ending at the jettison coast), but flying it silently would hide where it came
   * from. Removing it together with the penalty was tried and reverted; see {@code
   * GravityTurnProblem} and 02-baseline-n2.md §12.
   */
  private void checkStagingInvariant(AscentPlan plan) {
    if (plan.transitionTime() < plan.stagingCompleteTime()) {
      throw new OrbitlabException(
          String.format(
              Locale.ROOT,
              "Ascent phase '%s': MECO at %.2f s precedes staging completion at %.2f s "
                  + "(burn 1 %.2f s + interstage coast) — the second burn would have zero duration "
                  + "and the ascent would end at the jettison coast",
              getName(),
              plan.transitionTime(),
              plan.stagingCompleteTime(),
              plan.burn1Duration()));
    }
  }

  private void logPlan(AscentPlan plan) {
    logger.info(
        "[{}] staging: burn1 {}s to first-stage burnout, jettison, then burn2 {}s (MECO at {}s)",
        getName(),
        String.format(Locale.ROOT, "%.1f", plan.burn1Duration()),
        String.format(Locale.ROOT, "%.1f", plan.burn2Duration()),
        String.format(Locale.ROOT, "%.1f", plan.transitionTime()));
  }
}
