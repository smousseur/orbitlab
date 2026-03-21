package com.smousseur.orbitlab.simulation.mission;

import java.util.Collections;
import java.util.List;
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

  /**
   * Adds a mission to the registry and returns its entry.
   *
   * @param mission the mission to register
   * @return the created mission entry
   */
  public MissionEntry addMission(Mission mission) {
    MissionEntry entry = new MissionEntry(mission);
    missions.add(entry);
    return entry;
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
    return missions.stream()
        .filter(entry -> entry.mission().getName().equals(name))
        .findFirst();
  }
}
