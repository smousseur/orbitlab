package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
import com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnProblem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

public class GravityTurnStage extends MissionStage
    implements OptimizableMissionStage<GravityTurnProblem> {

  private static final Logger logger = LogManager.getLogger(GravityTurnStage.class);

  private final double targetInclination;
  private final double pitchKickAngle;
  private final double interstageCoastDuration;
  private final double launchLatitude;
  private final GravityTurnConstraints constraints;

  private OptimizationResult optimizationResult;

  public GravityTurnStage(
      String name,
      double pitchKickAngle,
      double interstageCoastDuration,
      GravityTurnConstraints constraints) {
    this(name, pitchKickAngle, interstageCoastDuration, 0.0, 0.0, constraints);
  }

  public GravityTurnStage(
      String name,
      double pitchKickAngle,
      double interstageCoastDuration,
      double launchLatitude,
      double targetInclination,
      GravityTurnConstraints constraints) {
    super(name);
    this.pitchKickAngle = pitchKickAngle;
    this.interstageCoastDuration = interstageCoastDuration;
    this.launchLatitude = launchLatitude;
    this.targetInclination = targetInclination;
    this.constraints = constraints;
  }

  @Override
  public String optimizationKey() {
    return getName();
  }

  @Override
  public void applyOptimization(OptimizationResult result) {
    this.optimizationResult = result;
  }

  @Override
  public GravityTurnProblem buildProblem(Mission mission) {
    SpacecraftState entryState = mission.getCurrentState();
    GravityTurnManeuver maneuver = createManeuver(mission, entryState.getMass());
    return new GravityTurnProblem(maneuver, entryState, constraints);
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    // The pitch kick that starts the gravity turn is applied in configure(), not here (bilan 11
    // §3.9). The ephemeris generator overrides enter()'s result with the pre-kick entry state saved
    // during optimization (opt.getEntryState()), so a kick applied here would be discarded; applying
    // it in configure() instead guarantees the replay flies the turn from the kicked velocity, the
    // same one the optimize pass uses. Entering is therefore a no-op.
    return previousState;
  }

  @Override
  public double maxStepSeconds(SpacecraftState entryState, Mission mission) {
    // Replay uses the same burn-2 invariant the optimizer's own propagator uses.
    return createManeuver(mission, entryState.getMass()).maxStepSeconds();
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    if (optimizationResult == null) {
      throw new OrbitlabException(
          "GravityTurnStage '" + getName() + "' requires optimization before execution");
    }

    // Apply the pitch kick here (bilan 11 §3.9): the generator replays the GT from the pre-kick
    // entry state it saved during optimization, so without this the turn would fly from an un-kicked
    // velocity — 3° off on Falcon Heavy — seeding the optimize-vs-ephemeris divergence. The optimize
    // pass applies the same kick inside propagateForOptimization, so both passes now start the turn
    // identically. The kick preserves date, position and mass; only the velocity heading changes.
    GravityTurnManeuver maneuver = createManeuver(mission, mission.getCurrentState().getMass());
    SpacecraftState state = maneuver.applyKick(mission.getCurrentState());
    // The generator set the propagator's initial state to the pre-kick state before calling us;
    // reset it to the kicked state so the flown (and sampled) trajectory starts kicked.
    propagator.setInitialState(state);
    // The plan is the single date computation shared by both passes: the optimize pass builds one
    // from the same variables inside propagateForOptimization, so the replay flies the same
    // schedule to the millisecond (spec 01 §4.3).
    AscentPlan plan = maneuver.plan(state, optimizationResult.bestVariables());

    maneuver.configure(propagator, plan);
    // Replay path: the optimized transition time is supposed to fit the loaded propellant, so a
    // depletion here is a real accounting bug — fail loud.
    DepletionGuard.arm(propagator, maneuver.getDepletionFloor(), getName());

    // Staging invariant (bilan 10 §5.3): the optimizer's lower bound guarantees MECO comes after
    // first-stage burnout plus the interstage coast, so the jettison scheduled at burn1Duration
    // always fires. Logged once per mission so a profile that ever loses its staging is visible.
    double stagingComplete = plan.stagingCompleteTime();
    if (plan.transitionTime() < stagingComplete) {
      throw new OrbitlabException(
          String.format(
              "GravityTurnStage '%s': MECO at %.2f s precedes staging completion at %.2f s "
                  + "(burn 1 %.2f s + interstage coast) — the first stage would never be "
                  + "jettisoned and would stay active for the rest of the mission",
              getName(), plan.transitionTime(), stagingComplete, plan.burn1Duration()));
    }
    logger.info(
        "[{}] staging: burn1 {}s to first-stage burnout, jettison, then burn2 {}s (MECO at {}s)",
        getName(),
        String.format(java.util.Locale.ROOT, "%.1f", plan.burn1Duration()),
        String.format(java.util.Locale.ROOT, "%.1f", plan.burn2Duration()),
        String.format(java.util.Locale.ROOT, "%.1f", plan.transitionTime()));

    // MECO event → transition to next stage
    AbsoluteDate mecoDate = plan.mecoDate();
    this.configuredEndDate = mecoDate;
    propagator.addEventDetector(
        new DateDetector(mecoDate)
            .withHandler(
                (s, detector, increasing) -> {
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                }));
  }

  private GravityTurnManeuver createManeuver(Mission mission, double entryMass) {
    Vehicle vehicle = mission.getVehicle();
    double launchAzimuth = Physics.getLaunchAzimuth(launchLatitude, targetInclination);
    return new GravityTurnManeuver(
        vehicle, entryMass, Math.toRadians(pitchKickAngle), launchAzimuth,
        interstageCoastDuration);
  }
}
