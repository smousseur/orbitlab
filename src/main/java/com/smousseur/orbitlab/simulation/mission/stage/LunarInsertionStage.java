package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.ArcTransition;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryGuard;
import com.smousseur.orbitlab.simulation.mission.maneuver.LunarInsertionPlan;
import com.smousseur.orbitlab.simulation.mission.maneuver.LunarInsertionPlan.Burn;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * The lunar orbit insertion burn (MIS-5 / L4, spec {@code docs/lunar-orbit/06-conception-L4.md} §4)
 * — a constant-thrust retrograde burn lit half a burn short of the perilune, calibrated on the
 * orbit it actually delivers.
 *
 * <p>Twin of {@link TLIBurnStage}: {@code enter} plans and moves no mass, {@code configure} flies
 * the burn, {@code propagateStandalone} re-plans and flies it on its own propagator. {@code
 * ArcTransition.convert} heads {@code enter} here too — idempotent by reference, therefore free,
 * and it stops the stage assuming who precedes it.
 *
 * <p><b>It takes no target altitude, and that is a decision.</b> The lunar orbit altitude is aimed
 * exactly once, by {@link TLIBurnStage#TLIBurnStage(String, double)}; this stage circularises the
 * perilune it <em>reaches</em>. A second parameter would be a second truth about one target, and
 * nothing in the stage could act on a disagreement between them — chasing it would need a change of
 * plan, ignoring it would make the parameter an ornament. Checking that the two coincide is the
 * mission objective's job, which is where judging belongs.
 *
 * <p><b>It declares no sphere-of-influence transition, and could not:</b> {@code StageLegRunner}
 * refuses one on a propulsive stage.
 */
public class LunarInsertionStage extends MissionStage {

  /**
   * Settling coast after cut-off (s). The constant and the structural role of {@link
   * TLIBurnStage#SETTLING_COAST_SECONDS}: it gives the stage a cutoff to be judged against, and
   * separates the burn from the coast that follows in the recorded trail.
   */
  public static final double SETTLING_COAST_SECONDS = 60.0;

  /** The burn resolved at {@link #enter}, read by {@link #configure} on the mission's chain. */
  private Burn burn;

  /**
   * @param name the human-readable name of this stage
   */
  public LunarInsertionStage(String name) {
    super(name);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Derived from the mission's own context by the crossing rule rather than written out, for the
   * reason {@link LunarApproachCoastStage#gravitationalContext} gives.
   */
  @Override
  public GravitationalContext gravitationalContext(Mission mission) {
    return ArcTransition.across(mission.gravitationalContext(), SolarSystemBody.MOON);
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    SpacecraftState ignitionState =
        ArcTransition.convert(previousState, gravitationalContext(mission));
    FlightContext context = flightContext(ignitionState, mission);
    this.burn =
        LunarInsertionPlan.insert(ignitionState, activeStage(ignitionState, mission), context);
    LunarInsertionPlan.logBurn(getName(), ignitionState, burn, context);
    return ignitionState;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState state = mission.getCurrentState();
    ActiveStageInfo active = activeStage(state, mission);
    // The loud guard, unlike the sibling analytic stages: LunarInsertionPlan.requirePropellantFor
    // refuses a burn the stage cannot pay for, so a propellant-capped one never reaches this
    // propagator and the floor is unreachable by construction (docs/bugs.md BUG-15).
    DepletionGuard.arm(propagator, active.depletionFloor(), getName());
    LunarInsertionPlan.addBurn(
        propagator, state, burn.direction(), burn.duration(), active.propulsion());

    AbsoluteDate end = LunarInsertionPlan.cutoffDate(state, burn).shiftedBy(SETTLING_COAST_SECONDS);
    this.configuredEndDate = end;
    propagator.addEventDetector(
        new DateDetector(end)
            .withHandler(
                (s, detector, increasing) -> {
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                }));
  }

  @Override
  public double maxStepSeconds(SpacecraftState entryState, Mission mission) {
    return burnLimitedMaxStep(entryState, mission.getVehicle());
  }

  @Override
  public SpacecraftState propagateStandalone(SpacecraftState currentState, Mission mission) {
    SpacecraftState ignitionState =
        ArcTransition.convert(currentState, gravitationalContext(mission));
    FlightContext context = flightContext(ignitionState, mission);
    ActiveStageInfo active = activeStage(ignitionState, mission);
    Burn standalone = LunarInsertionPlan.insert(ignitionState, active, context);

    NumericalPropagator propagator =
        OrekitService.get()
            .createOptimizationPropagator(context, maxStepSeconds(ignitionState, mission));
    propagator.setInitialState(ignitionState);
    ReentryGuard.armQuiet(propagator, context.gravity());
    DepletionGuard.arm(propagator, active.depletionFloor(), getName());
    LunarInsertionPlan.addBurn(
        propagator,
        ignitionState,
        standalone.direction(),
        standalone.duration(),
        active.propulsion());
    return propagator.propagate(
        LunarInsertionPlan.cutoffDate(ignitionState, standalone).shiftedBy(SETTLING_COAST_SECONDS));
  }

  private static ActiveStageInfo activeStage(SpacecraftState state, Mission mission) {
    return mission.getVehicle().resolveActiveStage(state.getMass());
  }
}
