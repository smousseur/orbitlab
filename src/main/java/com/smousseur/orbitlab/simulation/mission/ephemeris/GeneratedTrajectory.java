package com.smousseur.orbitlab.simulation.mission.ephemeris;

import java.util.List;
import java.util.Objects;

/**
 * What a replay produces: the mission's own trajectory, and the separation events a {@link
 * DebrisGenerator} turns into debris.
 *
 * <p>The jettisons are carried out of the replay rather than propagated inside it: the debris cost
 * stays off the mission's own sampling path, and the separation from the optimizer — which never
 * sees any of this — is a property of where this record is built (the replay pass only).
 *
 * @param ephemeris the mission's own sampled trajectory
 * @param jettisons the separation events captured during the replay, in flight order
 */
public record GeneratedTrajectory(MissionEphemeris ephemeris, List<JettisonEvent> jettisons) {
  public GeneratedTrajectory {
    Objects.requireNonNull(ephemeris, "ephemeris");
    jettisons = List.copyOf(jettisons);
  }
}
