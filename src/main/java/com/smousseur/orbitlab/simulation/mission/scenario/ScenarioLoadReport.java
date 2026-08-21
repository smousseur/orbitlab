package com.smousseur.orbitlab.simulation.mission.scenario;

import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import java.util.List;
import java.util.Objects;
import org.orekit.time.AbsoluteDate;

/**
 * What reading a scenario produced: the missions that came back, the clock they were saved with,
 * and the ones that did not come back, each with its reason.
 *
 * <p>Rejection is <b>per mission</b> (spec {@code docs/scenario/01-persistance-missions.md} §7): a
 * scenario of six missions with one broken brings back five, not zero. The only whole-file refusal
 * is a format version this build does not know, and that one is an exception rather than a report —
 * there is nothing partial to salvage from a file whose shape is unknown.
 *
 * <p>The entries are <b>built but not installed</b>. Nothing here touches {@code MissionContext},
 * the renderers or the clock; the caller swaps the session. That is what makes the invariant of
 * §6.3 fall out of the signature alone: the file is entirely converted before the first current
 * mission is destroyed, because {@code restore} has already returned by then.
 *
 * @param missions the missions rebuilt from the file, in file order
 * @param clockDate the simulation clock to restore, or {@code null} when the file carried none this
 *     build could read — the caller then keeps the clock it has
 * @param rejections the missions that could not be rebuilt
 */
public record ScenarioLoadReport(
    List<MissionEntry> missions, AbsoluteDate clockDate, List<Rejection> rejections) {

  public ScenarioLoadReport {
    Objects.requireNonNull(missions, "missions");
    Objects.requireNonNull(rejections, "rejections");
    missions = List.copyOf(missions);
    rejections = List.copyOf(rejections);
  }

  /**
   * @return {@code true} when the file carried a clock worth restoring
   */
  public boolean hasClockDate() {
    return clockDate != null;
  }

  /**
   * @return {@code true} when at least one mission of the file did not come back
   */
  public boolean hasRejections() {
    return !rejections.isEmpty();
  }

  /**
   * One mission the file described and this build could not rebuild.
   *
   * <p>The reason is the raw failure, formatted by {@code MissionEntry.describeFailure} — never a
   * table of friendly messages, which would be an association resolved at runtime and would sooner
   * or later state something false about a mission.
   *
   * @param missionName the name the file gave it
   * @param reason the failure, as thrown
   */
  public record Rejection(String missionName, String reason) {}
}
