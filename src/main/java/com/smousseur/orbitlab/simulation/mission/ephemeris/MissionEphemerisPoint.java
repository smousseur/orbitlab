package com.smousseur.orbitlab.simulation.mission.ephemeris;

import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

/**
 * Immutable snapshot of a spacecraft's state at a specific point in time. Analogous to {@link
 * com.smousseur.orbitlab.simulation.ephemeris.BodySample} for celestial bodies, but with
 * mission-specific fields (stage, mass, altitude).
 *
 * @param propulsive whether the stage that produced this sample burns propellant, captured from
 *     {@link com.smousseur.orbitlab.simulation.mission.MissionStage#isPropulsive()} at sampling
 *     time. Carried on the sample rather than looked up later because the drawable polyline never
 *     sees a {@code MissionStage}, and because {@code stageName} — a free-form per-mission string —
 *     cannot be classified reliably after the fact.
 */
public record MissionEphemerisPoint(
    AbsoluteDate time,
    Vector3D position,
    Vector3D velocity,
    String stageName,
    boolean propulsive,
    double mass,
    double altitudeMeters) {
  public MissionEphemerisPoint {
    Objects.requireNonNull(time, "time");
    Objects.requireNonNull(position, "position");
    Objects.requireNonNull(velocity, "velocity");
    Objects.requireNonNull(stageName, "stageName");
  }
}
