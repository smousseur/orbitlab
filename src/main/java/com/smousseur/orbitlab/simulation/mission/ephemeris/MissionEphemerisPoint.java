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
 * @param altitudeMeters the geodetic altitude above the reference shape of {@code arc}'s body — not
 *     necessarily the Earth's
 * @param arc the frame {@code position} and {@code velocity} are expressed in (PHY-4 / L3, spec
 *     {@code docs/multi-corps/05-conception-L3.md} §2). Carried <b>on the point</b> rather than in
 *     a side table because four consumers hold a bare point and each needs the frame: {@code
 *     FloatingOriginAppState}, the spacecraft anchor, {@code CameraTransitionAppState} and the
 *     telemetry widget. Reading it here means they read the same field of the same object, which
 *     turns the bit-for-bit agreement they depend on from a coincidence into a property.
 */
public record MissionEphemerisPoint(
    AbsoluteDate time,
    Vector3D position,
    Vector3D velocity,
    String stageName,
    boolean propulsive,
    double mass,
    double altitudeMeters,
    TrajectoryArc arc) {
  public MissionEphemerisPoint {
    Objects.requireNonNull(time, "time");
    Objects.requireNonNull(position, "position");
    Objects.requireNonNull(velocity, "velocity");
    Objects.requireNonNull(stageName, "stageName");
    Objects.requireNonNull(arc, "arc");
  }
}
