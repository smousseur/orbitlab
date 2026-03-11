package com.smousseur.orbitlab.simulation.ephemeris.service;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.BodySample;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;

public interface EphemerisService {
  Optional<BodySample> trySampleIcrf(SolarSystemBody body, AbsoluteDate t);

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
