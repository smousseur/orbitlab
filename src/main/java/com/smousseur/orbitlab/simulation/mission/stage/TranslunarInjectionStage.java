package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * The impulsive translunar injection of PHY-4 / L6 (spec {@code
 * docs/multi-corps/08-conception-L6.md} §3.3): {@link TranslunarInjectionPlan} solved on entry, its
 * ΔV applied at once, then a short settling coast.
 *
 * <p><b>It takes the shape of {@link StageSeparationStage}, not that of the analytic burn
 * stages</b>, and the three consequences are the reason:
 *
 * <ul>
 *   <li>{@code propagateStandalone} inherits the default {@code enter(...)}, so the optimize pass
 *       and the ephemeris pass apply the same impulse through the same code, with nothing to keep
 *       in sync;
 *   <li>the settling coast gives the stage a {@code configuredEndDate}, which is the only place in
 *       this mission where {@code MissionEphemerisGenerator}'s truncation check has any purchase —
 *       a trailing coast sized by the horizon reports no shortfall by construction (spec §1.9);
 *   <li>{@link #isPropulsive()} stays {@code true}, so the performance report derives the ΔV from
 *       the mass delta rather than reading the impulse as a jettison.
 * </ul>
 *
 * <p><b>It declares no sphere-of-influence transition</b>, and could not: a propulsive stage is
 * refused one (L4 §3.3, enforced in {@code StageLegRunner}). The crossing belongs to the coast that
 * follows, which is where it happens — a translunar transfer crosses ballistically.
 */
public class TranslunarInjectionStage extends MissionStage {
  private static final Logger logger = LogManager.getLogger(TranslunarInjectionStage.class);

  /**
   * Settling coast after the impulse (s). Short, and its role is structural rather than physical:
   * it gives the stage a cutoff to be judged against and a visible phase in the trail, at the 1 s
   * propulsive sampling step.
   */
  public static final double SETTLING_COAST_SECONDS = 60.0;

  private final double targetPerileneAltitude;

  /**
   * @param name the human-readable name of this stage
   * @param targetPerileneAltitude the perilune altitude above the lunar sphere to aim for (m)
   */
  public TranslunarInjectionStage(String name, double targetPerileneAltitude) {
    super(name);
    this.targetPerileneAltitude = targetPerileneAltitude;
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    ActiveStageInfo active = mission.getVehicle().resolveActiveStage(previousState.getMass());
    TranslunarInjectionPlan.Injected injected =
        TranslunarInjectionPlan.inject(
            previousState, targetPerileneAltitude, active, flightContext(previousState, mission));

    logger.info(
        "[{}] impulsive injection: dv={} m/s, mass {} -> {} kg, aiming a {} km perilune (plan says"
            + " {} km)",
        getName(),
        FastMath.round(injected.plan().deltaV().getNorm()),
        FastMath.round(previousState.getMass()),
        FastMath.round(injected.state().getMass()),
        FastMath.round(targetPerileneAltitude / 1000.0),
        FastMath.round(injected.plan().perileneAltitude() / 1000.0));

    return injected.state();
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    AbsoluteDate end = mission.getCurrentState().getDate().shiftedBy(SETTLING_COAST_SECONDS);
    this.configuredEndDate = end;
    propagator.addEventDetector(
        new DateDetector(end)
            .withHandler(
                (state, detector, increasing) -> {
                  mission.transitionToNextStage(state);
                  return Action.STOP;
                }));
  }
}
