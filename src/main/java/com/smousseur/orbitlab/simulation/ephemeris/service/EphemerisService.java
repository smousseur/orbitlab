package com.smousseur.orbitlab.simulation.ephemeris.service;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.BodySample;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service interface for retrieving interpolated ephemeris data for celestial bodies.
 *
 * <p>Provides non-blocking access to body position, velocity, and rotation samples in the
 * ICRF frame. Implementations typically delegate to a {@link SlidingWindowEphemerisBuffer}.
 */
public interface EphemerisService {
  /**
   * Attempts to retrieve an interpolated body sample at the given time in the ICRF frame.
   *
   * @param body the celestial body to query
   * @param t the time at which to sample
   * @return the body sample, or empty if the data is not currently available
   */
  Optional<BodySample> trySampleIcrf(SolarSystemBody body, AbsoluteDate t);

  /**
   * Attempts to retrieve a heliocentric position and rotation for the given body at time {@code t}.
   *
   * <p>The position is computed by subtracting the Sun's ICRF position from the body's ICRF
   * position. For the Sun itself, returns zero position with identity rotation.
   *
   * @param body the celestial body to query
   * @param t the time at which to sample
   * @return a map entry of (heliocentric position, rotation), or empty if data is unavailable
   */
  default Optional<Map.Entry<Vector3D, Rotation>> trySampleHelioIcrf(
      SolarSystemBody body, AbsoluteDate t) {
    if (body == SolarSystemBody.SUN) {
      return Optional.of(new AbstractMap.SimpleImmutableEntry<>(Vector3D.ZERO, Rotation.IDENTITY));
    }
    Optional<BodySample> b = trySampleIcrf(body, t);
    Optional<BodySample> s = trySampleIcrf(SolarSystemBody.SUN, t);
    if (b.isEmpty() || s.isEmpty()) return Optional.empty();
    Vector3D position = b.get().pvIcrf().getPosition().subtract(s.get().pvIcrf().getPosition());
    return Optional.of(new AbstractMap.SimpleImmutableEntry<>(position, b.get().rotationIcrf()));
  }
}
