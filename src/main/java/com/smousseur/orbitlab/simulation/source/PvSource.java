package com.smousseur.orbitlab.simulation.source;

import com.smousseur.orbitlab.core.SolarSystemBody;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

/**
 * Source of position and velocity coordinates for solar system bodies in the ICRF frame.
 *
 * <p>Implementations may compute coordinates from ephemeris datasets, Orekit models, or
 * sliding-window buffers. All returned coordinates are expressed in the ICRF reference frame.
 */
@FunctionalInterface
public interface PvSource {
  /**
   * Returns the position and velocity of the given body at the specified date in the ICRF frame.
   *
   * @param body the solar system body to query
   * @param date the date at which to compute coordinates
   * @return the position and velocity coordinates in ICRF
   */
  PVCoordinates pvIcrf(SolarSystemBody body, AbsoluteDate date);

  /**
   * Heliocentric absolute position in ICRF axes: planetICRF(t) - sunICRF(t). Implementation MUST
   * interpolate from sliding-window buffers only (no Orekit call on render thread).
   */
  default Vector3D positionHelioIcrf(SolarSystemBody body, AbsoluteDate date) {
    if (body == SolarSystemBody.SUN) {
      return Vector3D.ZERO;
    }
    PVCoordinates pvBody = pvIcrf(body, date);
    PVCoordinates pvSun = pvIcrf(SolarSystemBody.SUN, date);
    return pvBody.getPosition().subtract(pvSun.getPosition());
  }
}
