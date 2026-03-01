package com.smousseur.orbitlab.simulation.ephemeris.service;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.BodySample;
import org.hipparchus.util.Pair;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

import java.util.Optional;

public interface EphemerisService {
  Optional<BodySample> trySampleIcrf(SolarSystemBody body, AbsoluteDate t);

  default Optional<Pair<Vector3D, Rotation>> trySampleHelioIcrf(
      SolarSystemBody body, AbsoluteDate t) {
    if (body == SolarSystemBody.SUN) {
      return Optional.of(new Pair<>(Vector3D.ZERO, Rotation.IDENTITY));
    }
    Optional<BodySample> b = trySampleIcrf(body, t);
    Optional<BodySample> s = trySampleIcrf(SolarSystemBody.SUN, t);
    if (b.isEmpty() || s.isEmpty()) return Optional.empty();
    Vector3D position = b.get().pvIcrf().getPosition().subtract(s.get().pvIcrf().getPosition());
    return Optional.of(new Pair<>(position, b.get().rotationIcrf()));
  }
}
