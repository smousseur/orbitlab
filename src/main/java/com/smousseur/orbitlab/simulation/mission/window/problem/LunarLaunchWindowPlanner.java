package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSearch;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSolver;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import org.hipparchus.util.FastMath;
import org.orekit.time.AbsoluteDate;

/**
 * Dates a lunar mission — the one place a {@link MissionSpec.Lunar} meets a <b>confirming</b>
 * {@link LunarLaunchWindowProblem} (MIS-4 / L5 §6.3).
 *
 * <p><b>This is where the 4.5 s of a confirmation are paid</b>, at the click that creates the
 * mission and not on every keystroke of the parameters step, whose timeline screens only (§4.1).
 * The price is a freeze of ten to fifteen seconds on the render thread, against 40 ms for an Earth
 * mission; it is written down as a limitation of the lot rather than hidden.
 *
 * <p><b>The mass at injection is recomputed, not carried.</b> {@code
 * PropellantBudget.loadsForLunar} is closed-form and deterministic, so reading it back off the
 * spec's own inputs costs microseconds and keeps one definition of the figure (§5.3).
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
   * finite there rather than refusing (L2 §1.3): without a ceiling the search would hand back the
   * cheapest of a set of dates nobody can fly.
   */
  private static final double MAX_DELTA_V = 3_400.0;

  /** The margin that carves the slot out of the criterion (m/s) — the Earth problem's own. */
  private static final double MARGIN = 50.0;

  /**
   * Slots to confirm before picking the earliest. Four, because {@link
   * LunarLaunchWindowProblem#confirm} can refuse: a perilune the aim does not converge to, or a
   * depletion floor the active stage will not go under.
   */
  private static final int CANDIDATES = 4;

  /** Due east, the azimuth this chain flies: at {@code i = φ} there is no branch to choose. */
  private static final double DUE_EAST = FastMath.PI / 2;

  private LunarLaunchWindowPlanner() {}

  /**
   * The first opportunity at or after {@code earliest} for a lunar mission.
   *
   * <p><b>The soonest, not the cheapest</b>, on {@code EarthLaunchWindowPlanner}'s reasoning: the
   * roots of consecutive turns are the same opportunity repeated, so ordering by cost would push
   * the launch half a day later for a metre per second.
   *
   * @param spec the mission being scheduled
   * @param earliest the date the user asked for, read as a floor
   * @return the window to fly, or empty when every candidate of the span was refused
   */
  public static Optional<LaunchWindow> nextOpportunity(
      MissionSpec.Lunar spec, AbsoluteDate earliest) {
    PropellantBudget.LunarLoads loads =
        PropellantBudget.loadsForLunar(
            spec.configuration().launcher(),
            spec.configuration().payload(),
            spec.parkingAltitude(),
            spec.latitude(),
            DUE_EAST);
    LunarLaunchWindowProblem problem =
        new LunarLaunchWindowProblem(
            spec.latitude(),
            spec.longitude(),
            spec.altitude(),
            spec.parkingAltitude(),
            spec.periluneAltitude(),
            spec.configuration().toVehicleStack(),
            loads.massAtInjection());
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
