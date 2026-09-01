package com.smousseur.orbitlab.engine.scene;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.core.SolarSystemBody;

/**
 * Provides the per-body corrective rotation applied to a planet's 3D mesh before it is oriented by
 * the physical body-fixed rotation (see {@code docs/bugs.md}, BUG-3).
 *
 * <p>The rendering chain used to apply a single global mesh correction for all eleven GLTF models
 * ({@code RenderTransform#toRenderQuaternion}), which is only correct if every asset shares exactly
 * the same axis convention and the same prime meridian. It does not: each model is exported
 * separately, so each one needs its own residual fix.
 *
 * <p>The value returned here is expressed <b>in the model's own axes, as loaded</b>: it is applied
 * first, before the global Y-up correction and before the physical rotation. It therefore answers
 * one question only — "how must this particular asset be turned so that it matches the convention
 * the other bodies follow?" — and stays meaningful even if the global correction changes. In that
 * convention the pole is the model's up axis and the prime meridian its front axis, so a residual
 * longitude offset is a rotation about the up axis alone.
 *
 * <p>Identity means "this asset is already correct as exported". Every body starts there: the
 * values are established one body at a time, by observation, and a body whose mesh is fixed in
 * Blender instead of here keeps the identity.
 */
public final class PlanetMeshCorrection {

  private PlanetMeshCorrection() {}

  /**
   * Returns the corrective rotation for the given body's 3D model.
   *
   * @param body the solar system body
   * @return the rotation to apply to the raw mesh, identity when the asset needs no correction
   */
  public static Quaternion correctionFor(SolarSystemBody body) {
    final Quaternion identity = new Quaternion();
    return switch (body) {
      case SUN, MERCURY, NEPTUNE, PLUTO ->
          new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y);
      case EARTH, MOON, JUPITER, VENUS, MARS, SATURN, URANUS -> identity;
    };
  }
}
