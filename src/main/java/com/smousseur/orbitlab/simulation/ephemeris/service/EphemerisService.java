package com.smousseur.orbitlab.simulation.ephemeris.service;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.BodySample;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

import java.util.Optional;

public interface EphemerisService {
  Optional<BodySample> trySampleIcrf(SolarSystemBody body, AbsoluteDate t);

  default Optional<Vector3D> tryPositionHelioIcrf(SolarSystemBody body, AbsoluteDate t) {
    if (body == SolarSystemBody.SUN) {
      return Optional.of(Vector3D.ZERO);
    }
    Optional<BodySample> b = trySampleIcrf(body, t);
    Optional<BodySample> s = trySampleIcrf(SolarSystemBody.SUN, t);
    if (b.isEmpty() || s.isEmpty()) return Optional.empty();
    return Optional.of(b.get().pvIcrf().getPosition().subtract(s.get().pvIcrf().getPosition()));
  }
}
