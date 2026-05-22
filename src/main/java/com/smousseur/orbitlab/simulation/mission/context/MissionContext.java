package com.smousseur.orbitlab.simulation.mission.context;

import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.ui.mission.MissionColorPalette;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe registry of active missions. Exposed via {@code ApplicationContext.missionContext()}.
 *
 * <p>Uses {@link CopyOnWriteArrayList} because missions are added/removed infrequently (user
 * action) but iterated every frame by the orchestrator.
 */
public final class MissionContext {
  private final List<MissionEntry> missions = new CopyOnWriteArrayList<>();
  private volatile String selectedMissionName;
  private volatile String telemetryFocusMissionName;
  private volatile MissionType selectedMissionType = MissionType.LEO;

  /**
   * Adds a mission to the registry and returns its entry.
   *
   * @param mission the mission to register
   */
  public void addMission(Mission mission) {
    MissionEntry entry = new MissionEntry(mission);
    assignColor(entry);
    missions.add(entry);
  }

  public void addMission(MissionEntry entry) {
    if (entry.getColor() == null) {
      assignColor(entry);
    }
    missions.add(entry);
  }

  private void assignColor(MissionEntry entry) {
    List<ColorRGBA> inUse =
        missions.stream().map(MissionEntry::getColor).filter(Objects::nonNull).toList();
    entry.setColor(MissionColorPalette.pickFree(inUse));
  }

  /**
   * Removes a mission by name.
   *
   * @param missionName the name of the mission to remove
   */
  public void removeMission(String missionName) {
    missions.removeIf(entry -> entry.mission().getName().equals(missionName));
  }

  /**
   * Returns an unmodifiable snapshot of all mission entries.
   *
   * @return the list of mission entries
   */
  public List<MissionEntry> getMissions() {
    return Collections.unmodifiableList(missions);
  }

  /**
   * Finds a mission entry by name.
   *
   * @param name the mission name to search for
   * @return an optional containing the entry, or empty if not found
   */
  public Optional<MissionEntry> findMission(String name) {
    return missions.stream().filter(entry -> entry.mission().getName().equals(name)).findFirst();
  }

  /**
   * Returns the name of the currently selected mission, or {@code null} if none is selected.
   *
   * @return the selected mission name
   */
  public String getSelectedMissionName() {
    return selectedMissionName;
  }

  /**
   * Sets the name of the currently selected mission.
   *
   * @param name the mission name to select, or {@code null} to deselect
   */
  public void setSelectedMissionName(String name) {
    this.selectedMissionName = name;
  }

  /**
   * Gets selected mission type.
   *
   * @return the selected mission type
   */
  public MissionType getSelectedMissionType() {
    return selectedMissionType;
  }

  /**
   * Sets selected mission type.
   *
   * @param selectedMissionType the selected mission type
   */
  public void setSelectedMissionType(MissionType selectedMissionType) {
    this.selectedMissionType = selectedMissionType;
  }

  /**
   * Returns the currently selected mission entry, if any.
   *
   * @return an optional containing the selected entry
   */
  public Optional<MissionEntry> getSelectedMission() {
    String name = selectedMissionName;
    if (name == null) return Optional.empty();
    return findMission(name);
  }

  /**
   * Returns the name of the mission currently displaying telemetry, or {@code null} if none.
   *
   * @return the telemetry focus mission name
   */
  public String getTelemetryFocusMissionName() {
    return telemetryFocusMissionName;
  }

  /**
   * Sets the mission whose telemetry should be displayed.
   *
   * @param name the mission name, or {@code null} to clear the focus
   */
  public void setTelemetryFocusMissionName(String name) {
    this.telemetryFocusMissionName = name;
  }

  /**
   * Returns the mission entry currently displaying telemetry, if any.
   *
   * @return an optional containing the telemetry focus entry
   */
  public Optional<MissionEntry> getTelemetryFocusMission() {
    String name = telemetryFocusMissionName;
    if (name == null) return Optional.empty();
    return findMission(name);
  }
}
