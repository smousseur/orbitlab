package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryGuard;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan.Burn;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan.Departure;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.attitudes.FrameAlignedProvider;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * The translunar injection burn (MIS-4 / L6, spec {@code docs/lunar-flyby/08-conception-L6.md}
 * §5.2) — a constant-thrust burn centred on the injection point, calibrated to deliver the energy
 * the impulsive plan aims with.
 *
 * <p><b>The stage enters at ignition and finds the injection point ahead of it</b>, by calling
 * {@link TranslunarInjectionPlan#departureFrom} on its own entry state. Half a burn early, the
 * remaining travel is 1.6° on a Falcon Heavy upper stage and 9.3° on an Ariane 62 ULPM — both far
 * above the tolerance {@code departureFrom} stops at, and both positive, so the point found is the
 * one just ahead and not the next revolution's (spec §5.2).
 *
 * <p><b>That is not the date the parking coast stopped against, and nothing tries to make it
 * one</b> (spec §9.5). {@code departureFrom} is a fixed point on the state it is handed, so the
 * injection date it resolves from a parking insertion and the one it resolves here differ —
 * measured at 2.46 s. The design had assumed that calling the same closed form was enough for the
 * two stages to agree; it is not, and a burn calibrated centred on one date and flown from the
 * other put the flyby 1 150 km inside the Moon. What makes the disagreement harmless is that {@code
 * inject} is handed <b>this stage's entry state</b> and calibrates the burn that ignites there, so
 * the aim converges on the departure the mission really flies whatever off-centring is left.
 *
 * <p><b>{@code enter} moves no mass.</b> It coasts ballistically to the injection point, plans
 * there so {@link TranslunarInjectionPlan#solve} sees the geometry the impulsive model saw, and
 * returns the entry state unchanged. The burn is flown by {@code configure}, which is the whole
 * difference with the impulsive stage this replaces: an impulse was state arithmetic, a finite burn
 * is not.
 *
 * <p><b>It overrides {@code propagateStandalone}, and that is the structural cost of the lot.</b>
 * The impulsive stage inherited the default because applying its impulse in {@code enter} was the
 * entire flight; here the optimizer pass and the ephemeris pass have to fly the same burn on their
 * own propagator.
 *
 * <p><b>It declares no sphere-of-influence transition</b>, and now could not: a propulsive stage is
 * refused one (L4 §3.3, enforced in {@code StageLegRunner}). The crossing belongs to the coast that
 * follows, which is where it happens — a translunar transfer crosses ballistically.
 */
public class TLIBurnStage extends MissionStage {
  private static final Logger logger = LogManager.getLogger(TLIBurnStage.class);

  /**
   * Settling coast after cut-off (s). Short, and its role is structural rather than physical: it
   * gives the stage a cutoff to be judged against and separates the burn from the coast that
   * follows in the recorded trail.
   */
  public static final double SETTLING_COAST_SECONDS = 60.0;

  private final double targetPerileneAltitude;

  /** The burn resolved at {@link #enter}, read by {@link #configure} on the mission's chain. */
  private Burn burn;

  /**
   * @param name the human-readable name of this stage
   * @param targetPerileneAltitude the perilune altitude above the lunar sphere to aim for (m)
   */
  public TLIBurnStage(String name, double targetPerileneAltitude) {
    super(name);
    this.targetPerileneAltitude = targetPerileneAltitude;
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    this.burn = plan(previousState, mission);
    logBurn(previousState, burn);
    return previousState;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState state = mission.getCurrentState();
    addBurn(propagator, state, burn, mission.getVehicle());

    AbsoluteDate end = state.getDate().shiftedBy(burn.duration() + SETTLING_COAST_SECONDS);
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

  /**
   * {@inheritDoc}
   *
   * <p>Flown at 8×8 gravity like the ballistic run to the injection point and like the parking
   * coast before it: the burn is planned from a state that field produced, and a Newtonian
   * point-mass flight here would deliver it on a phase the flown trajectory never has.
   */
  @Override
  public SpacecraftState propagateStandalone(SpacecraftState currentState, Mission mission) {
    Burn standalone = plan(currentState, mission);
    FlightContext context = flightContext(currentState, mission);
    NumericalPropagator propagator =
        OrekitService.get()
            .createOptimizationPropagator(context, maxStepSeconds(currentState, mission));
    propagator.setInitialState(currentState);
    ReentryGuard.armQuiet(propagator, context.gravity());
    addBurn(propagator, currentState, standalone, mission.getVehicle());
    return propagator.propagate(
        currentState.getDate().shiftedBy(standalone.duration() + SETTLING_COAST_SECONDS));
  }

  /**
   * Coasts from the ignition state to the injection point and solves the burn there.
   *
   * @param ignitionState the state this stage is entered with, half a burn short of the injection
   * @param mission the parent mission
   * @return the burn to light at {@code ignitionState}'s date
   */
  private Burn plan(SpacecraftState ignitionState, Mission mission) {
    FlightContext context = flightContext(ignitionState, mission);
    Departure departure = TranslunarInjectionPlan.departureFrom(ignitionState);

    NumericalPropagator ballistic =
        OrekitService.get()
            .createOptimizationPropagator(context, maxStepSeconds(ignitionState, mission));
    ballistic.setInitialState(ignitionState);
    ReentryGuard.armQuiet(ballistic, context.gravity());
    SpacecraftState atInjection = ballistic.propagate(departure.injectionDate());

    ActiveStageInfo active = mission.getVehicle().resolveActiveStage(ignitionState.getMass());
    return TranslunarInjectionPlan.inject(
        ignitionState, atInjection, targetPerileneAltitude, active, context);
  }

  private void addBurn(
      NumericalPropagator propagator, SpacecraftState state, Burn plan, Vehicle vehicle) {
    ActiveStageInfo active = vehicle.resolveActiveStage(state.getMass());
    DepletionGuard.arm(propagator, active.depletionFloor(), getName());

    PropulsionSystem propulsion = active.propulsion();
    FrameAlignedProvider attitude =
        new FrameAlignedProvider(new Rotation(plan.direction(), Vector3D.PLUS_I), state.getFrame());
    propagator.addForceModel(
        new ConstantThrustManeuver(
            state.getDate().shiftedBy(1.0e-3),
            plan.duration(),
            propulsion.thrust(),
            propulsion.isp(),
            attitude,
            Vector3D.PLUS_I));
  }

  /**
   * The finite-burn record of the flight: the arc swept, and what the burn costs above the impulse
   * it replaces (spec §6.1). That surcharge is the loss the lot exists to measure, and nothing in
   * the repository produced it before.
   */
  private void logBurn(SpacecraftState ignitionState, Burn plan) {
    double impulsive = plan.plan().deltaV().getNorm();
    double period = ignitionState.getOrbit().getKeplerianPeriod();
    logger.info(
        "[{}] finite injection: dt={} s ({}° of arc), commanded {} m/s for {} m/s impulsive (+{}),"
            + " mass {} -> {} kg, aiming a {} km perilune (plan says {} km)",
        getName(),
        FastMath.round(plan.duration()),
        String.format(Locale.ROOT, "%.1f", 360.0 * plan.duration() / period),
        FastMath.round(plan.commandedDeltaV()),
        FastMath.round(impulsive),
        String.format(Locale.ROOT, "%.1f", plan.commandedDeltaV() - impulsive),
        FastMath.round(ignitionState.getMass()),
        FastMath.round(plan.endMass()),
        FastMath.round(targetPerileneAltitude / 1000.0),
        FastMath.round(plan.plan().perileneAltitude() / 1000.0));
  }
}
