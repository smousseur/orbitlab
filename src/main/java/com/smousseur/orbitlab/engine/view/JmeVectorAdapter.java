package com.smousseur.orbitlab.engine.view;

import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

import java.util.Objects;

/**
 * Bridge between double-precision Orekit/Hipparchus vectors and JME float vectors.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Apply RenderContext scale (meters <-> JME units)</li>
 *   <li>Apply axis convention (ICRF axes <-> JME axes)</li>
 *   <li>Convert Vector3D (double) <-> Vector3f (float)</li>
 * </ul>
 */
public final class JmeVectorAdapter {

  private JmeVectorAdapter() {}

  /**
   * Orekit absolute ICRF meters -> JME Vector3f (JME units, JME axes).
   *
   * @param objectIcrfMeters absolute object position in ICRF meters
   * @param targetIcrfMeters absolute target position in ICRF meters (required for PLANET view, ignored for SOLAR)
   */
  public static Vector3f toJmePosition(
      Vector3D objectIcrfMeters, Vector3D targetIcrfMeters, RenderContext ctx) {

    Objects.requireNonNull(objectIcrfMeters, "objectIcrfMeters");
    Objects.requireNonNull(ctx, "ctx");

    Vector3D renderUnitsJmeAxes = RenderTransform.toRenderUnitsJmeAxes(objectIcrfMeters, targetIcrfMeters, ctx);
    return toVector3f(renderUnitsJmeAxes);
  }

  /**
   * JME Vector3f (JME units, JME axes) -> absolute ICRF meters.
   *
   * @param jmeUnitsJmeAxes position in JME units/axes
   * @param targetIcrfMeters absolute target position in ICRF meters (required for PLANET view, ignored for SOLAR)
   */
  public static Vector3D toIcrfMeters(
      Vector3f jmeUnitsJmeAxes, Vector3D targetIcrfMeters, RenderContext ctx) {

    Objects.requireNonNull(jmeUnitsJmeAxes, "jmeUnitsJmeAxes");
    Objects.requireNonNull(ctx, "ctx");

    Vector3D renderUnitsJmeAxes = new Vector3D(jmeUnitsJmeAxes.x, jmeUnitsJmeAxes.y, jmeUnitsJmeAxes.z);
    return RenderTransform.toIcrfMetersFromRenderUnitsJmeAxes(renderUnitsJmeAxes, targetIcrfMeters, ctx);
  }

  /** Vector3D (double) -> Vector3f (float). Kept in one place for auditability. */
  public static Vector3f toVector3f(Vector3D v) {
    Objects.requireNonNull(v, "v");
    return new Vector3f((float) v.getX(), (float) v.getY(), (float) v.getZ());
  }

  /** Vector3f (float) -> Vector3D (double). */
  public static Vector3D toVector3D(Vector3f v) {
    Objects.requireNonNull(v, "v");
    return new Vector3D(v.x, v.y, v.z);
  }
}
