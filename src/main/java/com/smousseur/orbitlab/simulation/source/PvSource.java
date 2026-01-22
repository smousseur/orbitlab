package com.smousseur.orbitlab.simulation.source;

import com.smousseur.orbitlab.core.SolarSystemBody;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

@FunctionalInterface
public interface PvSource {
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
