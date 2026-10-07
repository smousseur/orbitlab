package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSearch;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSolver;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import org.orekit.time.AbsoluteDate;

/**
 * Dates a lunar mission — the one place a lunar {@link MissionSpec} meets a <b>confirming</b>
 * {@link LunarLaunchWindowProblem}.
 *
 * <p><b>Two entries, one body</b>. A flyby and an orbit insertion are dated by the same criterion
 * at the same aimed perilune; what stopped the flyby's planner from serving both was its signature,
 * not its content.
 *
 * <p><b>This is where the confirmations are paid</b>, at the click that creates the mission and not
 * on every keystroke of the parameters step, whose timeline screens only: seven to ten seconds a
 * candidate, on the wizard's creation thread, against 40 ms for an Earth mission.
 *
 * <p><b>And where the ascent is flown.</b> The window prices and confirms from the parking orbit
 * the mission's ascent really reaches, which only a flight measures: once per dating, due east, at
 * the requested date, by the optimizer, budget and seed of the production compute — 44 to 57 s for
 * a 2 000 kg orbiter on a Falcon Heavy, 116 s for a thirty-tonne probe. Both searches read that one
 * ascent, the free azimuth included. An ascent that does not reach its parking orbit is the
 * opportunity's refusal.
 */
public final class LunarLaunchWindowPlanner {

  /**
   * How far past the requested date an opportunity is looked for. Two sidereal days: the geometry
   * offers two roots per turn of the node, so this bracket holds four whatever the phase — enough
   * that a confirmation refusing the first still leaves something to schedule.
   */
  private static final Duration SEARCH_SPAN = Duration.ofHours(48);

  /**
   * The cost above which an epoch is not offered (m/s), a little above the 3 124 m/s L4 measured
   * from Canaveral at 400 km.
   *
   * <p><b>Absolute here, unlike the timeline's relative margin, and that is the point.</b> A pad
   * below the lunar declination reaches no plane containing the Moon, and the criterion stays
   * finite there rather than refusing: without a ceiling the search would hand back the cheapest of
   * a set of dates nobody can fly.
   */
  static final double MAX_DELTA_V = 3_400.0;

  /** The margin that carves the slot out of the criterion (m/s) — the Earth problem's own. */
  private static final double MARGIN = 50.0;

  /**
   * Slots to confirm before picking the earliest. Four, because {@link
   * LunarLaunchWindowProblem#confirm} can refuse: a perilune the aim does not converge to, or a
   * depletion floor the active stage will not go under.
   */
  private static final int CANDIDATES = 4;

  private LunarLaunchWindowPlanner() {}

  /**
   * What a lunar mission is scheduled on: a window, and the plane chosen for it when due east had
   * none — or, when neither search found anything, why.
   *
   * @param window the window to fly, or {@code null} when none was found
   * @param plane the plane the free-azimuth search chose, or {@code null} for due east (and when
   *     nothing was found)
   * @param refusal why no window was found, or {@code null} when one was
   */
  public record Opportunity(LaunchWindow window, LaunchPlane plane, String refusal) {

    /**
     * @return {@code true} when there is a window to schedule on
     */
    public boolean found() {
      return window != null;
    }

    /**
     * @return {@code true} when the window was found at a free azimuth, on {@link #plane()}
     */
    public boolean hasPlane() {
      return plane != null;
    }
  }

  /**
   * The opportunity a lunar flyby is scheduled on: due east if it has a window within the span, the
   * free azimuth otherwise, and the reason when neither does.
   *
   * @param spec the mission being scheduled, on no plane yet
   * @param earliest the date the user asked for, read as a floor
   * @return the opportunity
   */
  public static Opportunity opportunity(MissionSpec.Lunar spec, AbsoluteDate earliest) {
    return opportunity(
        spec.configuration(),
        spec.atmosphere(),
        spec.latitude(),
        spec.longitude(),
        spec.altitude(),
        spec.parkingAltitude(),
        spec.periluneAltitude(),
        earliest);
  }

  /**
   * The opportunity a lunar orbit is scheduled on, on {@link #opportunity(MissionSpec.Lunar,
   * AbsoluteDate)}'s rule.
   *
   * @param spec the mission being scheduled, on no plane yet
   * @param earliest the date the user asked for, read as a floor
   * @return the opportunity
   */
  public static Opportunity opportunity(MissionSpec.LunarOrbit spec, AbsoluteDate earliest) {
    return opportunity(
        spec.configuration(),
        spec.atmosphere(),
        spec.latitude(),
        spec.longitude(),
        spec.altitude(),
        spec.parkingAltitude(),
        spec.orbitAltitude(),
        earliest);
  }

  /**
   * Due east first, the free azimuth only when due east has nothing.
   *
   * <p><b>Due east keeps the priority even where the free azimuth would be a few m/s cheaper</b>:
   * it is the plane every mission was flown and calibrated on, and a site that has a due-east
   * window — Canaveral, most days — keeps the very date and trajectory it had. The second search
   * costs a second round of confirmations, paid only where the first came back empty.
   *
   * <p><b>The reason given when both are empty is the free search's</b>, the last one run: the
   * refusal of its latest confirmation, or the absence of any epoch under the ceiling. When the
   * ascent itself does not reach its parking orbit, neither search runs and the reason is that.
   */
  private static Opportunity opportunity(
      LaunchConfiguration configuration,
      AtmosphereModel atmosphere,
      double latitude,
      double longitude,
      double altitude,
      double parkingAltitude,
      double periluneAltitude,
      AbsoluteDate earliest) {
    ParkingInsertion insertion;
    try {
      insertion =
          ParkingInsertion.fly(
              configuration, atmosphere, latitude, longitude, altitude, parkingAltitude, earliest);
    } catch (OrbitlabException refused) {
      return new Opportunity(null, null, refused.getMessage());
    }
    Optional<LaunchWindow> dueEast =
        earliestWindow(
            new LunarLaunchWindowProblem(
                latitude,
                longitude,
                altitude,
                parkingAltitude,
                periluneAltitude,
                configuration.toVehicleStack(),
                insertion),
            earliest);
    if (dueEast.isPresent()) {
      return new Opportunity(dueEast.get(), null, null);
    }
    LunarLaunchWindowProblem freeAzimuth =
        new LunarLaunchWindowProblem(
            latitude,
            longitude,
            altitude,
            parkingAltitude,
            periluneAltitude,
            configuration.toVehicleStack(),
            insertion,
            LunarLaunchWindowProblem.PlaneChoice.FREE_AZIMUTH);
    Optional<LaunchWindow> window = earliestWindow(freeAzimuth, earliest);
    if (window.isPresent()) {
      return new Opportunity(window.get(), freeAzimuth.launchPlaneAt(window.get().date()), null);
    }
    return new Opportunity(
        null,
        null,
        String.format(
            Locale.ROOT,
            "no lunar launch window within %d h of %s, neither due east nor at a free azimuth: %s",
            SEARCH_SPAN.toHours(),
            earliest,
            freeAzimuth
                .latestRefusal()
                .orElse(
                    String.format(
                        Locale.ROOT,
                        "no epoch is priced under the %.0f m/s ceiling",
                        MAX_DELTA_V))));
  }

  /**
   * The first <b>due-east</b> opportunity at or after {@code earliest} for a lunar flyby — the
   * first of {@link #opportunity}'s two searches, kept on its own for the callers that measure due
   * east alone.
   *
   * <p><b>The soonest, not the cheapest</b>, on {@code EarthLaunchWindowPlanner}'s reasoning: the
   * roots of consecutive turns are the same opportunity repeated, so ordering by cost would push
   * the launch half a day later for a metre per second.
   *
   * @param spec the mission being scheduled
   * @param earliest the date the user asked for, read as a floor
   * @return the window to fly, or empty when every candidate of the span was refused
   * @throws OrbitlabException when the ascent does not reach its parking orbit
   */
  public static Optional<LaunchWindow> nextOpportunity(
      MissionSpec.Lunar spec, AbsoluteDate earliest) {
    return nextOpportunity(
        spec.configuration(),
        spec.atmosphere(),
        spec.latitude(),
        spec.longitude(),
        spec.altitude(),
        spec.parkingAltitude(),
        spec.periluneAltitude(),
        earliest);
  }

  /**
   * The first <b>due-east</b> opportunity at or after {@code earliest} for a lunar orbit insertion.
   *
   * <p><b>The aimed perilune is the lunar orbit altitude</b>, which is why the window needs no lot
   * of its own: the flyby's criterion — can a shot on this date reach that perilune — is exactly
   * the verdict an insertion needs, and {@code confirm()} already flies the aim to give it.
   *
   * <p><b>The mass needs no separate formula either.</b> The configuration's payload is the {@code
   * Spacecraft} as it will fly, insertion propellant included, so the ascent the shared body flies
   * carries it to parking without knowing which lunar profile it is serving.
   *
   * @param spec the mission being scheduled
   * @param earliest the date the user asked for, read as a floor
   * @return the window to fly, or empty when every candidate of the span was refused
   * @throws OrbitlabException when the ascent does not reach its parking orbit
   */
  public static Optional<LaunchWindow> nextOpportunity(
      MissionSpec.LunarOrbit spec, AbsoluteDate earliest) {
    return nextOpportunity(
        spec.configuration(),
        spec.atmosphere(),
        spec.latitude(),
        spec.longitude(),
        spec.altitude(),
        spec.parkingAltitude(),
        spec.orbitAltitude(),
        earliest);
  }

  /**
   * The shared body of the two entries above: fly the ascent, build the confirming problem on the
   * insertion it reached, search, take the soonest.
   */
  private static Optional<LaunchWindow> nextOpportunity(
      LaunchConfiguration configuration,
      AtmosphereModel atmosphere,
      double latitude,
      double longitude,
      double altitude,
      double parkingAltitude,
      double periluneAltitude,
      AbsoluteDate earliest) {
    return earliestWindow(
        new LunarLaunchWindowProblem(
            latitude,
            longitude,
            altitude,
            parkingAltitude,
            periluneAltitude,
            configuration.toVehicleStack(),
            ParkingInsertion.fly(
                configuration,
                atmosphere,
                latitude,
                longitude,
                altitude,
                parkingAltitude,
                earliest)),
        earliest);
  }

  /** One confirming search over the span, and its soonest window. */
  private static Optional<LaunchWindow> earliestWindow(
      LunarLaunchWindowProblem problem, AbsoluteDate earliest) {
    LaunchWindowSearch search =
        new LaunchWindowSearch(
            earliest,
            SEARCH_SPAN,
            problem.coarseStep(),
            problem.refinementPrecision(),
            MAX_DELTA_V,
            MARGIN,
            CANDIDATES);
    return new LaunchWindowSolver(problem)
        .solve(search).stream().min(Comparator.comparing(LaunchWindow::date));
  }
}
