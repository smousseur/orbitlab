package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure transition-rule engine for the Mission Display Panel. Holds the per-mission status snapshot
 * needed to detect READY transitions and publishes nothing — callers mutate {@link MissionContext}
 * directly.
 *
 * <p>Extracted as a separate class so the rules are exercised by unit tests without the JME
 * lifecycle.
 */
final class MissionDisplayPanelRules {

  /** Last known status per mission name, used to detect transitions for R1/R9. */
  private final Map<String, MissionStatus> previousStatuses = new HashMap<>();

  /**
   * Applies R1 (mission entering READY auto-arms telemetry if none is set), R9 (telemetered mission
   * leaving READY clears focus), and R10 (deleted telemetered mission clears focus).
   *
   * @param mc the mission context to mutate
   */
  void applyStatusTransitionRules(MissionContext mc) {
    Set<String> currentNames = new HashSet<>();

    for (MissionEntry entry : mc.getMissions()) {
      String name = entry.mission().getName();
      currentNames.add(name);
      MissionStatus current = entry.mission().getStatus();
      MissionStatus prev = previousStatuses.get(name);
      if (prev != current) {
        onStatusTransition(mc, entry, prev, current);
        previousStatuses.put(name, current);
      }
    }

    // R10: detect deleted missions (present last frame, absent now)
    Iterator<Map.Entry<String, MissionStatus>> it = previousStatuses.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, MissionStatus> e = it.next();
      if (!currentNames.contains(e.getKey())) {
        if (e.getKey().equals(mc.getTelemetryFocusMissionName())) {
          mc.setTelemetryFocusMissionName(null);
        }
        it.remove();
      }
    }
  }

  private static void onStatusTransition(
      MissionContext mc, MissionEntry entry, MissionStatus prev, MissionStatus current) {
    String name = entry.mission().getName();

    // R9: telemetered mission leaves READY
    if (prev == MissionStatus.READY
        && current != MissionStatus.READY
        && name.equals(mc.getTelemetryFocusMissionName())) {
      mc.setTelemetryFocusMissionName(null);
    }

    // R1: mission enters READY and no telemetry is set → auto-on (and visible)
    if (current == MissionStatus.READY && mc.getTelemetryFocusMissionName() == null) {
      entry.setVisible(true);
      mc.setTelemetryFocusMissionName(name);
    }
  }

  /**
   * Applies R3 / R4 (telemetry focus toggle).
   *
   * @param mc the mission context to mutate
   * @param missionName the mission to focus, or {@code null} to clear the focus
   */
  void applyTelemetryFocus(MissionContext mc, String missionName) {
    if (missionName == null) {
      mc.setTelemetryFocusMissionName(null);
      return;
    }
    Optional<MissionEntry> target = mc.findMission(missionName);
    if (target.isEmpty() || target.get().mission().getStatus() != MissionStatus.READY) {
      return;
    }
    MissionEntry entry = target.get();
    if (!entry.isVisible()) {
      entry.setVisible(true); // R3: force visible
    }
    mc.setTelemetryFocusMissionName(missionName);
  }
}
