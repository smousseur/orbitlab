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
  /**
   * Computes an ephemeris sample (position/velocity and rotation) for the given body at the
   * specified date in the ICRF frame.
   *
   * @param body the solar system body to sample
   * @param date the date at which to compute the sample
   * @return the body sample containing position/velocity and rotation data
   */
  BodySample sampleIcrf(SolarSystemBody body, AbsoluteDate date);

  /**
   * Propagates a Keplerian orbit to the given date and returns the resulting position/velocity
   * coordinates.
   *
   * @param date the target propagation date
   * @param propagator the Keplerian propagator to use
   * @return the propagated position and velocity coordinates
   */
  default PVCoordinates propagate(AbsoluteDate date, KeplerianPropagator propagator) {
    return propagator.propagate(date).getPVCoordinates();
  }

  /**
   * Returns the ICRF position of a body, falling back to Keplerian propagation if the ephemeris
   * source throws an {@link OrbitlabException} (e.g., date out of dataset range).
   *
   * @param body the solar system body to sample
   * @param date the date at which to compute the position
   * @param propagator the fallback Keplerian propagator
   * @return the position vector in ICRF
   */
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
