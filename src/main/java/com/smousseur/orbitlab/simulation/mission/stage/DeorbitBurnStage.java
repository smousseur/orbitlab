package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.orekit.attitudes.LofOffset;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.frames.LOFType;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.events.FunctionalDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * One burn of the disposal tail: a <b>tracking</b> retrograde burn on the engine of the vehicle
 * stage active at entry, for a fixed duration, cut off early if the osculating perigee reaches its
 * target.
 *
 * <p><b>Tracking, not inertially fixed.</b> The thrust follows the anti-velocity direction ({@code
 * LofOffset} TNW, body {@code −X}) for the whole burn. A direction frozen at the burn's centre —
 * the convention of {@link AnalyticTrimBurnStage} — never brought the Earth-observation payload
 * down on any measured orbit: a disposal burn lasts a large part of a revolution, and a fixed
 * direction spends most of it pushing off the velocity.
 *
 * <p><b>The perigee cutoff is the stage's own end, and the date is its backstop.</b> The tail plans
 * the last burn by flying it long and reading where the cutoff stopped it; it then schedules the
 * burn to end just past that point, so that the cutoff — not a date landing a hair before the
 * crossing — is what ends it when it is flown again.
 *
 * <p>No {@code propagateStandalone} override: this stage is never walked by the optimize pass. It
 * only exists in a disposal tail, which is flown by a {@code StageChainRunner} and nothing else.
 */
public class DeorbitBurnStage extends MissionStage {

  /**
   * Delay between the stage entry and ignition (s). A maneuver trigger sitting exactly on the
   * propagation's initial date is ambiguous to the event machinery; every burn stage of the mission
   * lights one millisecond in for that reason.
   */
  private static final double IGNITION_DELAY_SECONDS = 1.0e-3;

  /** How often the perigee cutoff is checked (s) — well under the fastest perigee drop measured. */
  private static final double CUTOFF_MAX_CHECK_SECONDS = 10.0;

  /**
   * Convergence of the perigee cutoff in time (s). The root is taken on the far side of the
   * crossing, so the perigee it stops on is at or below the target, by at most the drop over this
   * interval.
   */
  private static final double CUTOFF_THRESHOLD_SECONDS = 0.1;

  private final double durationSeconds;
  private final double cutoffPerigeeAltitude;

  /**
   * @param name the stage name
   * @param durationSeconds how long after the stage entry the burn ends if the cutoff does not fire
   *     first (s)
   * @param cutoffPerigeeAltitude the osculating perigee altitude that ends the burn, spherical
   *     above the equatorial radius of the stage's central body (m)
   */
  public DeorbitBurnStage(String name, double durationSeconds, double cutoffPerigeeAltitude) {
    super(name);
    if (!(durationSeconds > 0.0)) {
      throw new IllegalArgumentException("duration must be positive, got " + durationSeconds);
    }
    this.durationSeconds = durationSeconds;
    this.cutoffPerigeeAltitude = cutoffPerigeeAltitude;
  }

  /**
   * @return how long after the stage entry the burn ends if the cutoff does not fire first (s)
   */
  public double durationSeconds() {
    return durationSeconds;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState entry = mission.getCurrentState();
    ActiveStageInfo active = mission.getVehicle().resolveActiveStage(entry.getMass());
    PropulsionSystem propulsion = active.propulsion();

    propagator.addForceModel(
        new ConstantThrustManeuver(
            entry.getDate().shiftedBy(IGNITION_DELAY_SECONDS),
            durationSeconds,
            propulsion.thrust(),
            propulsion.isp(),
            new LofOffset(entry.getFrame(), LOFType.TNW),
            Vector3D.MINUS_I));
    DepletionGuard.armCappedBurn(propagator, active.depletionFloor(), getName());

    GravitationalContext context = gravitationalContext(mission);
    double cutoffRadius = context.equatorialRadius() + cutoffPerigeeAltitude;
    propagator.addEventDetector(
        new FunctionalDetector()
            .withFunction(s -> perigeeRadius(s, context.mu()) - cutoffRadius)
            .withMaxCheck(CUTOFF_MAX_CHECK_SECONDS)
            .withThreshold(CUTOFF_THRESHOLD_SECONDS)
            .withHandler(
                (s, detector, increasing) -> {
                  if (increasing) {
                    return Action.CONTINUE;
                  }
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                }));

    AbsoluteDate end = entry.getDate().shiftedBy(durationSeconds);
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

  private static double perigeeRadius(SpacecraftState state, double mu) {
    KeplerianOrbit orbit =
        new KeplerianOrbit(state.getPVCoordinates(), state.getFrame(), state.getDate(), mu);
    return orbit.getA() * (1.0 - orbit.getE());
  }
}
