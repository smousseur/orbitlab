package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryGuard;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan.Departure;
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
 * The parking coast of a lunar mission: from insertion round to the point the translunar injection
 * has to <em>ignite</em> at, and no further (MIS-4 / L4 §3.2, MIS-4 / L6 §5.1).
 *
 * <p><b>It stops at ignition and not at the injection point</b>, which is what centres the finite
 * burn (spec L6 §2, decision α). Centring requires the burn duration to be known before igniting,
 * so this coast reads the propulsion of the active stage and subtracts {@link
 * TranslunarInjectionPlan#ignitionLead} from the injection date. The consequence to hold: {@code
 * configuredEndDate} means "ignition" here, half a burn short of the geometric departure point.
 *
 * <p><b>Its duration cannot be a constructor argument</b>, which is what closes the reuse of {@link
 * CoastingStage#CoastingStage(String, Double)} — that {@code maxTime} is final and read at {@code
 * configure}. Where the injection point lies depends on the launch date and on the ascent actually
 * flown, so it is only knowable at {@link #enter}. {@link TranslunarInjectionPlan#departureFrom}
 * and {@code ignitionLead} are both in closed form — no propagation, the lunar ephemeris alone — so
 * resolving them once per pass costs nothing (spec {@code docs/lunar-flyby/03-conception-L1.md}
 * §2.2).
 *
 * <p><b>It overrides {@code propagateStandalone}, and that is the whole reason the class
 * exists.</b> A plain coast does not, so in {@code MissionOptimizer}'s stage walk it collapses to
 * zero duration: the injection would then be resolved from the state at <em>parking insertion</em>
 * — wrong phase, wrong date, and nothing raised. That trap is what L1 §6 left to this lot.
 * Repairing {@link CoastingStage} itself would have been the tempting shortcut and is refused:
 * every coast of every mission in the repository collapses the same way, GEO carries one mid-chain,
 * and moving them all would move the ascent references MIS-7 re-recorded (spec {@code
 * docs/lunar-flyby/06-conception-L4.md} §1.1).
 */
public class ParkingCoastStage extends CoastingStage {
  private static final Logger logger = LogManager.getLogger(ParkingCoastStage.class);

  /**
   * The ignition point this coast ends on, resolved at {@link #enter} and read by both {@link
   * #configure} and {@link #propagateStandalone}. Absolute rather than a duration, so the two
   * passes cannot disagree on the date arithmetic.
   */
  private AbsoluteDate ignitionDate;

  /**
   * @param name the human-readable name of this stage
   */
  public ParkingCoastStage(String name) {
    super(name, null);
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    Departure departure = TranslunarInjectionPlan.departureFrom(previousState);
    ActiveStageInfo active = mission.getVehicle().resolveActiveStage(previousState.getMass());
    double lead = TranslunarInjectionPlan.ignitionLead(previousState, departure, active);
    this.ignitionDate = departure.injectionDate().shiftedBy(-lead);
    logger.info(
        "[{}] coasting {} s to ignition, {} s ahead of the injection point at {} (β = {}° at"
            + " arrival)",
        getName(),
        FastMath.round(departure.coastDuration() - lead),
        String.format(Locale.ROOT, "%.1f", lead),
        departure.injectionDate(),
        String.format(Locale.ROOT, "%.3f", FastMath.toDegrees(departure.planeMisalignment())));
    return previousState;
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

  /**
   * {@inheritDoc}
   *
   * <p>Flown at 8×8 gravity, as {@code AnalyticParkingInsertionStage} already propagates its own
   * burns: this is the state the injection is planned from, so a Newtonian point-mass field here
   * would place the departure on a phase the flown trajectory never has.
   */
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
