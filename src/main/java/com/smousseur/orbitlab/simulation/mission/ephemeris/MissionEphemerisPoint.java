package com.smousseur.orbitlab.simulation.mission.ephemeris;

import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

/**
 * Immutable snapshot of a spacecraft's state at a specific point in time. Analogous to {@link
 * com.smousseur.orbitlab.simulation.ephemeris.BodySample} for celestial bodies, but with
 * mission-specific fields (stage, mass, altitude).
 */
public record MissionEphemerisPoint(
    AbsoluteDate time,
    Vector3D position,
    Vector3D velocity,
    String stageName,
    double mass,
    double altitudeMeters) {
  public MissionEphemerisPoint {
    Objects.requireNonNull(time, "time");
    Objects.requireNonNull(position, "position");
    Objects.requireNonNull(velocity, "velocity");
    Objects.requireNonNull(stageName, "stageName");
  }
}
