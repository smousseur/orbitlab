package com.smousseur.orbitlab.app.view;

import org.hipparchus.geometry.euclidean.threed.Vector3D;

import java.util.Objects;

/**
 * Defines how vectors expressed in Orekit ICRF axes are mapped into JME world axes.
 *
 * <p>Convention chosen for OrbitLab:
 * <ul>
 *   <li>JME is Y-up</li>
 *   <li>We map ICRF Z (celestial north) to JME Y (up)</li>
 *   <li>We keep a right-handed basis by applying: jmeZ = -icrfY</li>
 * </ul>
 *
 * <p>So: (jmeX, jmeY, jmeZ) = (icrfX, icrfZ, -icrfY)
 */
public enum AxisConvention {
  ICRF_TO_JME_Y_UP {
    @Override
    public Vector3D icrfToJme(Vector3D icrf) {
      Objects.requireNonNull(icrf, "icrf");
      return new Vector3D(icrf.getX(), icrf.getZ(), -icrf.getY());
    }

    @Override
    public Vector3D jmeToIcrf(Vector3D jme) {
      Objects.requireNonNull(jme, "jme");
      // inverse of (x, z, -y) is: icrfX=jmeX, icrfY=-jmeZ, icrfZ=jmeY
      return new Vector3D(jme.getX(), -jme.getZ(), jme.getY());
    }
  };

  public abstract Vector3D icrfToJme(Vector3D icrf);

  public abstract Vector3D jmeToIcrf(Vector3D jme);
}
