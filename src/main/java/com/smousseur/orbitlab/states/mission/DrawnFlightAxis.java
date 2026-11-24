package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.frames.Transform;
import org.orekit.utils.PVCoordinates;

/**
 * The axis an object is drawn along: the direction it flies through the air near the ground, its
 * inertial velocity in space, and a smoothed blend of the two in between.
 *
 * <p>Drawing along the inertial velocity lays a launcher flat on its pad. There the inertial
 * velocity is the Earth's rotation — 463 m/s east at Kourou — while the vehicle climbs at a few
 * metres per second, thrusting at the zenith. The velocity relative to the air, which turns with
 * the Earth, is the one a vehicle in the atmosphere actually flies along: measured on the
 * production ascents, it lies within 0.05° of the vertical through the vertical climb, then pitches
 * over with the gravity turn. In space it is the wrong axis: on a polar orbit it stays up to 3.6°
 * off the inertial velocity, and the nose would leave the tangent of its own trail. Hence the blend
 * by altitude — the air's below {@value #AIR_CEILING_M} m, the Kármán line, the inertial one above
 * {@value #INERTIAL_FLOOR_M} m, the top of the domain the re-entry plasma glows in, a smoothstep in
 * between.
 *
 * <p>The air turns with the terrestrial frame, so the velocity relative to it is the velocity
 * expressed in ITRF, turned back into the arc's frame. ITRF rather than a rotation about the
 * inertial Z axis: the two poles lie 0.145° apart, about 1.2 m/s of wind at Kourou, which a
 * velocity of a few metres per second just off the pad cannot absorb.
 *
 * <p>Render-only: no sample is touched, and neither is anything the propagation or the gates read.
 */
final class DrawnFlightAxis {

  /** Below this altitude (m) the object is drawn along the air: the Kármán line. */
  static final double AIR_CEILING_M = 100_000.0;

  /** Above this altitude (m) it is drawn along its inertial velocity. */
  static final double INERTIAL_FLOOR_M = 120_000.0;

  /**
   * Below this speed relative to the air (m/s) its direction is undefined — an object at rest on
   * its pad — and the object stands on the local vertical.
   */
  private static final double MIN_AIR_SPEED = 0.01;

  /** Below this inertial speed (m/s) a velocity has no direction either. */
  private static final double MIN_SPEED = 1e-9;

  private DrawnFlightAxis() {}

  /**
   * The unit axis to draw a flying object along.
   *
   * @param point the object's sample: its position, velocity, altitude and arc are read
   * @return the unit axis, in the frame of the sample's arc
   */
  static Vector3D of(MissionEphemerisPoint point) {
    Vector3D inertial = directionOf(point.velocity(), point.position());
    if (point.arc().body() != SolarSystemBody.EARTH) {
      return inertial;
    }
    double toInertial = inertialWeight(point.altitudeMeters());
    if (toInertial >= 1.0) {
      return inertial;
    }
    Vector3D air;
    try {
      air = airRelativeVelocity(point);
    } catch (RuntimeException e) {
      // Orekit throws when the Earth-orientation data cannot serve the date; the inertial axis is
      // what every object was drawn along before, so it is the safe fallback for a display object.
      return inertial;
    }
    Vector3D alongAir =
        air.getNorm() < MIN_AIR_SPEED ? point.position().normalize() : air.normalize();
    if (toInertial <= 0.0) {
      return alongAir;
    }
    Vector3D blend = new Vector3D(1.0 - toInertial, alongAir, toInertial, inertial);
    return blend.getNorm() < MIN_SPEED ? alongAir : blend.normalize();
  }

  /** The velocity relative to the air turning with the Earth, in the frame of the sample's arc. */
  private static Vector3D airRelativeVelocity(MissionEphemerisPoint point) {
    Transform toTerrestrial =
        point.arc().frame().getTransformTo(OrekitService.get().itrf(), point.time());
    PVCoordinates terrestrial =
        toTerrestrial.transformPVCoordinates(new PVCoordinates(point.position(), point.velocity()));
    return toTerrestrial.getRotation().applyInverseTo(terrestrial.getVelocity());
  }

  /**
   * How far above the air an altitude lies, as the drawn axis weighs it: 0 up to the air's ceiling,
   * 1 from the inertial floor on, a smoothstep in between. Shared with {@link CoRotatingTrail}, so
   * the trail leaves the ground in step with the nose.
   *
   * @param altitude the geodetic altitude, in metres
   * @return the weight of the inertial frame, in [0, 1]
   */
  static double inertialWeight(double altitude) {
    double t = (altitude - AIR_CEILING_M) / (INERTIAL_FLOOR_M - AIR_CEILING_M);
    double clamped = Math.max(0.0, Math.min(1.0, t));
    return clamped * clamped * (3.0 - 2.0 * clamped);
  }

  private static Vector3D directionOf(Vector3D velocity, Vector3D position) {
    return velocity.getNorm() < MIN_SPEED ? position.normalize() : velocity.normalize();
  }
}
