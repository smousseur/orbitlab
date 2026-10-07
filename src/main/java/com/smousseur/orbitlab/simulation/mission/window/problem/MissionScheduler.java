package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.time.AbsoluteDate;

/**
 * Dates a mission the wizard creates or edits, and gives a lunar one its plane — the planning half
 * of the wizard's two submit paths, kept out of the AppState so it runs without one.
 *
 * <p><b>The typed date becomes a floor.</b> A pad meets a given node once per sidereal day and the
 * rest of the day costs kilometres per second, so "launch on the 4th at 12:00" can only mean "on
 * the 4th at 12:00 or as soon after as the geometry allows".
 *
 * <p><b>A lunar mission is specified twice.</b> Its plane is not known until its window is: the
 * spec is first built on no plane — {@link #unplannedSpec}, which drops a plane an edit carried
 * over from an earlier date — and the window searched from it. When due east has no window and the
 * free azimuth has one, the spec is built again from the same values plus the chosen plane, budget
 * included, and applied to the entry before the date is set. The mission therefore never shows a
 * date beside a plane chosen for another one.
 *
 * <p><b>Two paths, and they do not cost the same</b>. An Earth window is closed form throughout —
 * some ninety evaluations of an angle between two vectors, 40 ms measured. A lunar one first flies
 * the mission's ascent to measure the parking orbit it reaches, 44 to 57 s, then confirms each
 * refined candidate by flying the aim, seven to ten seconds apiece. Measured on the production
 * configuration — Falcon Heavy, a 2 000 kg orbiter — a Canaveral mission is scheduled in 70 to 85
 * s, its due-east search confirming three or four candidates; a Kourou one in 106 to 108 s, its
 * due-east search screening every epoch out above the ceiling before the free azimuth confirms
 * five. It runs on the wizard's creation thread, not on the render thread.
 */
public final class MissionScheduler {
  private static final Logger logger = LogManager.getLogger(MissionScheduler.class);

  private MissionScheduler() {}

  /**
   * The date a mission is scheduled at, or why it has none.
   *
   * @param date the date to schedule — the requested one when the mission has no window to sit
   *     through, or when none was found
   * @param refusal why no window was found, or {@code null} when the date is one
   */
  public record Schedule(AbsoluteDate date, String refusal) {

    /**
     * @return {@code true} when no window was found, and the mission cannot fly the date it keeps
     */
    public boolean refused() {
      return refusal != null;
    }
  }

  /**
   * The spec a mission is created or edited with, before its window is known: the wizard values on
   * no lunar plane, whatever plane they carried.
   *
   * @param values the raw wizard values
   * @param type the mission type
   * @return the spec to create the entry with, or apply to it
   */
  public static MissionSpec unplannedSpec(Map<String, Object> values, MissionType type) {
    return MissionFactory.specFromWizardValues(withoutLunarPlane(values), type);
  }

  /**
   * Finds the date of the entry's mission, and for a lunar one re-specifies the entry on the plane
   * its window chose.
   *
   * @param entry the entry, carrying the spec {@link #unplannedSpec} built
   * @param values the wizard values that spec was built from
   * @param requested the date read from the wizard, taken as a floor
   * @return the date to schedule, or the requested one with the reason no window was found
   */
  public static Schedule schedule(
      MissionEntry entry, Map<String, Object> values, AbsoluteDate requested) {
    MissionSpec spec =
        entry
            .spec()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Mission [" + entry.id().shortForm() + "] carries no spec to schedule"));
    return switch (spec) {
      case MissionSpec.EarthOrbit earthOrbit ->
          new Schedule(earthWindow(earthOrbit, requested), null);
      case MissionSpec.Geo ignored -> new Schedule(requested, null);
      case MissionSpec.Lunar lunar ->
          lunar(entry, values, LunarLaunchWindowPlanner.opportunity(lunar, requested), requested);
      case MissionSpec.LunarOrbit lunarOrbit ->
          lunar(
              entry,
              values,
              LunarLaunchWindowPlanner.opportunity(lunarOrbit, requested),
              requested);
    };
  }

  /**
   * The verdict shared by the two lunar profiles: the window's date, on the plane it was found on.
   */
  private static Schedule lunar(
      MissionEntry entry,
      Map<String, Object> values,
      LunarLaunchWindowPlanner.Opportunity opportunity,
      AbsoluteDate requested) {
    if (!opportunity.found()) {
      logger.warn("{}; keeping the requested date {}", opportunity.refusal(), requested);
      return new Schedule(requested, opportunity.refusal());
    }
    LaunchWindow window = opportunity.window();
    if (opportunity.hasPlane()) {
      LaunchPlane plane = opportunity.plane();
      MissionSpec current = entry.spec().orElseThrow();
      Map<String, Object> planed = withoutLunarPlane(values);
      planed.put(MissionFactory.LUNAR_PLANE_INCLINATION, plane.targetInclinationDeg());
      planed.put(MissionFactory.LUNAR_PLANE_BRANCH, plane.nodeBranch().name());
      MissionSpec onPlane =
          MissionFactory.specFromWizardValues(planed, current.type())
              .withAtmosphere(current.atmosphere());
      if (!entry.applySpec(onPlane)) {
        return new Schedule(
            requested,
            entry.getLastError().orElse("the mission could not be composed on its plane"));
      }
    }
    logger.info(
        "Lunar launch window: {} (requested {}, slot {} at {} m/s){}",
        window.date(),
        requested,
        window.duration(),
        Math.round(window.best().deltaV()),
        opportunity.hasPlane()
            ? String.format(
                Locale.ROOT,
                ", free azimuth: i = %.3f° %s",
                opportunity.plane().targetInclinationDeg(),
                opportunity.plane().nodeBranch())
            : "");
    return new Schedule(window.date(), null);
  }

  /**
   * The next opening of an Earth target plane, or the requested date when no plane is waited for.
   *
   * @param earthOrbit the mission being scheduled
   * @param requested the date read from the wizard, taken as a floor
   * @return the date to schedule
   */
  private static AbsoluteDate earthWindow(
      MissionSpec.EarthOrbit earthOrbit, AbsoluteDate requested) {
    if (!earthOrbit.hasTargetRaan()) {
      return requested;
    }
    Optional<LaunchWindow> window = EarthLaunchWindowPlanner.nextOpportunity(earthOrbit, requested);
    if (window.isEmpty()) {
      logger.warn(
          "No launch window found for RAAN {}° within a day of {}; keeping the requested date",
          earthOrbit.targetRaan(),
          requested);
      return requested;
    }
    logger.info(
        "Launch window for RAAN {}°: {} (requested {}, slot {} at {} m/s)",
        earthOrbit.targetRaan(),
        window.get().date(),
        requested,
        window.get().duration(),
        Math.round(window.get().best().deltaV()));
    return window.get().date();
  }

  private static Map<String, Object> withoutLunarPlane(Map<String, Object> values) {
    Map<String, Object> copy = new HashMap<>(values);
    copy.remove(MissionFactory.LUNAR_PLANE_INCLINATION);
    copy.remove(MissionFactory.LUNAR_PLANE_BRANCH);
    return copy;
  }
}
