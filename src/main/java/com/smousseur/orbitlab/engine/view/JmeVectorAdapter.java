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
   * Body-relative ICRF meters -> JME Vector3f (JME units, JME axes). Differs from {@link
   * #toJmePosition} only in that nothing is subtracted: the position is already expressed in a frame
   * centred on the render context's body, as a GCRF position is for the Earth.
   *
   * <p><b>Single conversion path, deliberately.</b> Two states convert the same spacecraft position
   * on every frame: {@link com.smousseur.orbitlab.states.mission.MissionOrchestratorAppState} places
   * the spacecraft anchor at it, and {@link
   * com.smousseur.orbitlab.states.camera.FloatingOriginAppState} offsets the near frame by its
   * negation. The two cancel to exactly zero — the spacecraft sitting on the near-view origin — only
   * because both go through this method and therefore produce the same {@code float} triple. Neither
   * may inline the scale and axis conversion again.
   *
   * @param positionIcrfMeters position in ICRF axes, in meters, relative to the context's body
   * @param ctx the render context giving the scale and axis convention
   * @return the position in JME units and JME axes
   */
  public static Vector3f toJmeBodyRelativePosition(Vector3D positionIcrfMeters, RenderContext ctx) {
    Objects.requireNonNull(positionIcrfMeters, "positionIcrfMeters");
    Objects.requireNonNull(ctx, "ctx");

    Vector3D scaled = RenderTransform.scaleMetersToUnits(positionIcrfMeters, ctx);
    return toVector3f(ctx.axisConvention().icrfToJme(scaled));
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
