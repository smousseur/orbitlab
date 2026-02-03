package com.smousseur.orbitlab.simulation.source;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.BodySample;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

/**
 * Computes exact samples (PV + rotation) from the underlying ephemeris model (e.g. JPL via Orekit).
 *
 * <p>Threading: implementations must be thread-safe or used only from worker threads.
 */
@FunctionalInterface
public interface EphemerisSource {
  BodySample sampleIcrf(SolarSystemBody body, AbsoluteDate date);

  default PVCoordinates propagate(AbsoluteDate date, KeplerianPropagator propagator) {
    return propagator.propagate(date).getPVCoordinates();
  }

  default Vector3D sampleIcrfSafe(
      SolarSystemBody body, AbsoluteDate date, KeplerianPropagator propagator) {
    Vector3D result;
    try {
      result = sampleIcrf(body, date).pvIcrf().getPosition();
    } catch (OrbitlabException e) {
      result = propagator.propagate(date).getPVCoordinates().getPosition();
    }

    return result;
  }
}
