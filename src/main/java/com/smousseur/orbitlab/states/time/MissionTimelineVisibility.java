package com.smousseur.orbitlab.states.time;

import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import java.util.Optional;

/**
 * The conditions under which the mission timeline is on screen (spec {@code
 * docs/navigation/02-timeline-mission.md} §10.1), extracted from the app state so they can be
 * tested without a JME lifecycle.
 *
 * <p>Conditions 2 to 5 — a telemetry focus, {@code READY}, visible, and carrying an ephemeris — are
 * word for word the test in {@code TelemetryWidgetAppState.update}. That is deliberate and is what
 * removes the need for a disabled state on the toggle: <b>the track is openable exactly when the
 * telemetry widget is on screen</b>, so the button is present or absent, never greyed (§11). Two
 * HUD widgets talking about two different missions would be a reading trap.
 *
 * <p>The ephemeris is checked explicitly rather than being assumed from {@code READY}: the status
 * is a fact about the mission, the missing ephemeris is a fact about the entry, and it is the
 * second one the track cannot absorb.
 *
 * <p>The focus itself is not driven here. {@code MissionDisplayPanelRules} owns it — R1 arms a
 * mission reaching {@code READY}, R9 disarms one leaving it, R10 handles deletion — and the track
 * follows.
 */
public final class MissionTimelineVisibility {

  private MissionTimelineVisibility() {}

  /**
   * The followed mission, when it satisfies conditions 2 to 5.
   *
   * @param context the mission context
   * @return the entry the track would show, or empty when there is nothing to show
   */
  public static Optional<MissionEntry> availableMission(MissionContext context) {
    Optional<MissionEntry> focus = context.getTelemetryFocusMission();
    if (focus.isEmpty()) {
      return Optional.empty();
    }
    MissionEntry entry = focus.get();
    if (entry.mission().getStatus() != MissionStatus.READY
        || !entry.isVisible()
        || entry.getEphemeris().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(entry);
  }

  /**
   * Whether there is a mission to show — and therefore whether the toggle exists at all.
   *
   * @param context the mission context
   * @return {@code true} when conditions 2 to 5 hold
   */
  public static boolean isAvailable(MissionContext context) {
    return availableMission(context).isPresent();
  }

  /**
   * The mission actually on screen: the available one, gated by the session's toggle.
   *
   * <p>The toggle is condition 1 and is held by the app state, not by {@code MissionEntry}: it is a
   * session display preference and survives a change of followed mission. It also stays true while
   * conditions 2 to 5 are false, so returning to a followed mission finds the track as it was left,
   * without a second click.
   *
   * @param context the mission context
   * @param toggleEnabled whether the user has the track open
   * @return the entry to render, or empty
   */
  public static Optional<MissionEntry> shownMission(MissionContext context, boolean toggleEnabled) {
    return toggleEnabled ? availableMission(context) : Optional.empty();
  }
}
