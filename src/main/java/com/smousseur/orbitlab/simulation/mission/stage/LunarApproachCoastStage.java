package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.ArcTransition;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryGuard;
import com.smousseur.orbitlab.simulation.mission.maneuver.LunarInsertionPlan;
import com.smousseur.orbitlab.simulation.mission.maneuver.LunarInsertionPlan.Arrival;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * The selenocentric approach of a lunar mission: from the sphere of influence to the point the
 * insertion burn has to <em>ignite</em> at, and no further (MIS-5 / L4, spec {@code
 * docs/lunar-orbit/06-conception-L4.md} §3).
 *
 * <p>Twin of {@link ParkingCoastStage}, down to the absolute {@code ignitionDate} resolved in
 * {@link #enter} and read by both {@link #configure} and {@link #propagateStandalone} — the
 * single-date arithmetic rule {@code CoastingStage.cutoffFrom} exists for. It overrides {@code
 * propagateStandalone} for the reason its twin does: without it the stage walk would collapse the
 * approach to zero seconds, and the insertion would be planned <em>at the sphere</em>, eighteen
 * hours and 66 000 km early.
 *
 * <p><b>One difference: {@code enter} converts the state, and returns the converted one.</b> On
 * both passes the state arrives geocentric — {@code StageChainRunner} calls {@code enter} before
 * the {@code ArcTransition.convert} at the head of {@code StageLegRunner.fly}, and the optimize
 * pass never converts at all (spec {@code docs/lunar-orbit/03-conception-L1.md} §8 pt 1). Returning
 * the unconverted state, as {@link ParkingCoastStage} does, would leave {@code
 * Mission.getCurrentState()} geocentric while {@code fly} propagates selenocentrically: two truths
 * about one instant. Returning the converted one makes {@code fly}'s own convert an identity by
 * reference, so both passes publish the same thing.
 *
 * <p><b>This is the one place in the lot where a mistake does not raise:</b> {@code
 * createOptimizationPropagator} takes its frame from the initial state, so a GCRF state integrated
 * with a lunar µ at the centre produces no exception and no warning — only a wrong trajectory.
 *
 * <p><b>It declares no sphere-of-influence transition.</b> It starts <em>on</em> the sphere, so
 * arming a boundary here would cut it at the first step; the crossing belongs to the coast before
 * it, once.
 */
public class LunarApproachCoastStage extends CoastingStage {
  private static final Logger logger = LogManager.getLogger(LunarApproachCoastStage.class);

  /**
   * The ignition point this coast ends on, resolved at {@link #enter}. Absolute rather than a
   * duration, so the two passes cannot disagree on the date arithmetic.
   */
  private AbsoluteDate ignitionDate;

  /**
   * @param name the human-readable name of this stage
   */
  public LunarApproachCoastStage(String name) {
    super(name, null);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Inherits the mission's central body no longer: it is the crossing rule of {@link
   * ArcTransition#across} that says what a lunar arc is, and repeating {@code
   * moon().withPerturbers(EARTH, SUN)} here would be a second place saying it — one that would
   * drift the day the mission declares a third perturber.
   */
  @Override
  public GravitationalContext gravitationalContext(Mission mission) {
    return ArcTransition.across(mission.gravitationalContext(), SolarSystemBody.MOON);
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    SpacecraftState selenocentric =
        ArcTransition.convert(previousState, gravitationalContext(mission));
    FlightContext context = flightContext(selenocentric, mission);
    Arrival arrival = LunarInsertionPlan.arrivalFrom(selenocentric, context);
    ActiveStageInfo active = mission.getVehicle().resolveActiveStage(selenocentric.getMass());
    double lead = LunarInsertionPlan.ignitionLead(arrival, active);
    this.ignitionDate = arrival.atPerilune().getDate().shiftedBy(-lead);
    logger.info(
        "[{}] coasting {} s to ignition, {} s ahead of a {} km perilune at {}",
        getName(),
        FastMath.round(ignitionDate.durationFrom(selenocentric.getDate())),
        String.format(Locale.ROOT, "%.1f", lead),
        String.format(Locale.ROOT, "%.1f", arrival.periluneAltitude() / 1000.0),
        arrival.atPerilune().getDate());
    return selenocentric;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    this.configuredEndDate = ignitionDate;
    propagator.addEventDetector(
        new DateDetector(ignitionDate)
            .withHandler(
                (state, detector, increasing) -> {
                  mission.transitionToNextStage(state);
                  return Action.STOP;
                }));
  }

  @Override
  public SpacecraftState propagateStandalone(SpacecraftState currentState, Mission mission) {
    SpacecraftState entryState = enter(currentState, mission);
    FlightContext context = flightContext(entryState, mission);
    NumericalPropagator propagator =
        OrekitService.get()
            .createOptimizationPropagator(context, maxStepSeconds(entryState, mission));
    propagator.setInitialState(entryState);
    ReentryGuard.armQuiet(propagator, context.gravity());
    return propagator.propagate(ignitionDate);
  }
}
