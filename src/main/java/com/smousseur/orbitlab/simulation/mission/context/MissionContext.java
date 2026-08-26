package com.smousseur.orbitlab.simulation.mission.context;

import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionId;
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
 * <p>Missions are keyed by {@link MissionId}, never by name: names are display labels and may be
 * duplicated.
 *
 * <p>Uses {@link CopyOnWriteArrayList} because missions are added/removed infrequently (user
 * action) but iterated every frame by the orchestrator.
 */
public final class MissionContext {
  private final List<MissionEntry> missions = new CopyOnWriteArrayList<>();
  private volatile MissionId selectedMissionId;
  private volatile MissionId telemetryFocusMissionId;
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
   * Removes a mission by id.
   *
   * @param missionId the id of the mission to remove
   */
  public void removeMission(MissionId missionId) {
    missions.removeIf(entry -> entry.id().equals(missionId));
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
   * Finds a mission entry by id.
   *
   * @param missionId the mission id to search for, may be {@code null}
   * @return an optional containing the entry, or empty if not found
   */
  public Optional<MissionEntry> findMission(MissionId missionId) {
    if (missionId == null) return Optional.empty();
    return missions.stream().filter(entry -> entry.id().equals(missionId)).findFirst();
  }

  /**
   * Reports whether a mission name is already used. Names are not unique keys — this only backs the
   * wizard's "name already taken" advice, which suggests a suffix instead of rejecting the mission.
   *
   * @param name the candidate mission name
   * @return {@code true} if a registered mission already carries this name
   */
  public boolean isNameInUse(String name) {
    return missions.stream().anyMatch(entry -> entry.mission().getName().equals(name));
  }

  /**
   * Returns the id of the currently selected mission, or {@code null} if none is selected.
   *
   * @return the selected mission id
   */
  public MissionId getSelectedMissionId() {
    return selectedMissionId;
  }

  /**
   * Sets the currently selected mission.
   *
   * @param missionId the mission to select, or {@code null} to deselect
   */
  public void setSelectedMissionId(MissionId missionId) {
    this.selectedMissionId = missionId;
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
   * Sets the selected mission type.
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
    return findMission(selectedMissionId);
  }

  /**
   * Returns the id of the mission currently displaying telemetry, or {@code null} if none.
   *
   * @return the telemetry focus mission id
   */
  public MissionId getTelemetryFocusMissionId() {
    return telemetryFocusMissionId;
  }

  /**
   * Sets the mission whose telemetry should be displayed.
   *
   * @param missionId the mission id, or {@code null} to clear the focus
   */
  public void setTelemetryFocusMissionId(MissionId missionId) {
    this.telemetryFocusMissionId = missionId;
  }

  /**
   * Returns the mission entry currently displaying telemetry, if any.
   *
   * @return an optional containing the telemetry focus entry
   */
  public Optional<MissionEntry> getTelemetryFocusMission() {
    return findMission(telemetryFocusMissionId);
  }
}
