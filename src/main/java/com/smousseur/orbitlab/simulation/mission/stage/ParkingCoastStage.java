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
 * has to <em>ignite</em> at, and no further.
 *
 * <p><b>It stops at ignition and not at the injection point</b>, which is what centres the finite
 * burn. Centring requires the burn duration to be known before igniting, so this coast reads the
 * propulsion of the active stage and subtracts {@link TranslunarInjectionPlan#ignitionLead} from
 * the injection date. The consequence to hold: {@code configuredEndDate} means "ignition" here,
 * half a burn short of the geometric departure point.
 *
 * <p><b>Its duration cannot be a constructor argument</b>, which is what closes the reuse of {@link
 * CoastingStage#CoastingStage(String, Double)} — that {@code maxTime} is final and read at {@code
 * configure}. Where the injection point lies depends on the launch date and on the ascent actually
 * flown, so it is only knowable at {@link #enter}.
 *
 * <p><b>{@link #enter} flies the coast to find that point, and each pass then flies it again.</b>
 * The closed form {@link TranslunarInjectionPlan#departureFrom} predicts it on a two-body orbit,
 * and the vehicle reaches it earlier: 0.74 to 7.30 s on the parking coasts of eight Canaveral
 * windows, the longest coasts the latest. Stopped against that prediction, the burn was centred as
 * far past its point, and 7.3 s was enough to refuse the injection. {@link
 * TranslunarInjectionPlan#injectionPassage} finds the passage the flight really makes, so the coast
 * is paid twice per pass. The half burn stays in closed form: read from the insertion or from the
 * point itself, it moves by at most 0.03 s.
 *
 * <p><b>The coast never runs backwards.</b> When the first passage comes less than half a burn
 * after the insertion, igniting for it would mean igniting before the coast began; the coast waits
 * for the next passage instead, a revolution later.
 *
 * <p><b>It overrides {@code propagateStandalone}, and that is the whole reason the class
 * exists.</b> A plain coast does not, so in {@code MissionOptimizer}'s stage walk it collapses to
 * zero duration: the injection would then be resolved from the state at <em>parking insertion</em>
 * — wrong phase, wrong date, and nothing raised. That trap is what L1 §6 left to this lot.
 * Repairing {@link CoastingStage} itself would have been the tempting shortcut and is refused:
 * every coast of every mission in the repository collapses the same way, GEO carries one mid-chain,
 * and moving them all would move the ascent references MIS-7 re-recorded.
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
    AbsoluteDate injection =
        TranslunarInjectionPlan.injectionPassage(
                previousState, lead, flightContext(previousState, mission))
            .getDate();
    this.ignitionDate = injection.shiftedBy(-lead);
    logger.info(
        "[{}] coasting {} s to ignition, {} s ahead of the injection point at {}, {} s from the"
            + " Keplerian prediction (β = {}° at arrival)",
        getName(),
        FastMath.round(ignitionDate.durationFrom(previousState.getDate())),
        String.format(Locale.ROOT, "%.1f", lead),
        injection,
        String.format(Locale.ROOT, "%+.2f", injection.durationFrom(departure.injectionDate())),
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
