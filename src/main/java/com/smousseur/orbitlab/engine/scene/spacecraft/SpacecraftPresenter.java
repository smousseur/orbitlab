package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.engine.scene.body.BodyView;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * MVC presenter that drives a {@link BodyView} by applying a spacecraft's position and orientation
 * from mission propagation data. Unlike {@link
 * com.smousseur.orbitlab.engine.scene.planet.PlanetPresenter} which fetches positions from
 * ephemeris, this presenter receives them directly from the mission's propagation output.
 *
 * <p>Orientation is driven by the velocity vector: the model's nose is aligned with the direction
 * of travel, interpolated frame-to-frame with a slerp to avoid jitter. When the velocity is ~0 the
 * last orientation is preserved.
 */
public final class SpacecraftPresenter {

  /** Smoothing half-life in seconds for the orientation slerp. */
  private static final float ROTATION_SMOOTHING_HALF_LIFE = 0.15f;

  /** Minimum squared velocity (m/s)^2 below which rotation is not updated. */
  private static final double MIN_VELOCITY_NORM_SQ = 1e-6;

  /**
   * Fixed correction applied to the {@link Quaternion#lookAt} result so that the
   * {@code heavy_falcon.gltf} model — whose nose points along its own +Y axis — is aligned with
   * the direction of travel. {@code lookAt} orients the local {@code -Z} along the given
   * direction, so we rotate the model -90° around its local X to bring +Y onto -Z.
   */
  private static final Quaternion MODEL_FORWARD_CORRECTION =
      new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X);

  private final String id;
  private final BodyView view;
  private final Quaternion currentRotation = new Quaternion();
  private final Quaternion targetRotation = new Quaternion();
  private boolean rotationInitialized;

  public SpacecraftPresenter(String id, BodyView view) {
    this.id = Objects.requireNonNull(id, "id");
    this.view = Objects.requireNonNull(view, "view");
  }

  /**
   * Returns the unique identifier of this presenter.
   *
   * @return the presenter id
   */
  public String id() {
    return id;
  }

  /**
   * Returns the underlying body view driven by this presenter.
   *
   * @return the body view
   */
  public BodyView view() {
    return view;
  }

  /**
   * Sets the visibility of the spacecraft view.
   *
   * @param v {@code true} to make the spacecraft visible, {@code false} to hide it
   */
  public void setVisible(boolean v) {
    view.setVisible(v);
  }

  /**
   * Updates the spacecraft's pose in the view. The position is provided in GCRF meters
   * (geocentric, Earth at origin). Since GCRF is geocentric and aligned on ICRF axes, only
   * render-unit scaling and ICRF-to-JME axis conversion are applied.
   *
   * <p>Rotation is derived from the velocity vector and smoothed with a spherical linear
   * interpolation. When the velocity magnitude is below a small epsilon the last computed
   * rotation is preserved — the position is still applied.
   *
   * @param positionGcrfMeters the spacecraft position in GCRF frame, in meters
   * @param velocityGcrf the spacecraft velocity in GCRF frame, in m/s (may be zero)
   * @param tpf the frame time in seconds used to drive the rotation smoothing
   * @param ctx the render context defining the scale (typically Planet with 1 unit = 1 km)
   */
  public void updatePose(
      Vector3D positionGcrfMeters, Vector3D velocityGcrf, float tpf, RenderContext ctx) {
    Objects.requireNonNull(positionGcrfMeters, "positionGcrfMeters");
    Objects.requireNonNull(velocityGcrf, "velocityGcrf");
    Objects.requireNonNull(ctx, "ctx");

    Vector3D scaled = RenderTransform.scaleMetersToUnits(positionGcrfMeters, ctx);
    Vector3D positionJmeAxes = ctx.axisConvention().icrfToJme(scaled);
    Vector3f p = JmeVectorAdapter.toVector3f(positionJmeAxes);
    view.setPositionWorld(p);

    if (velocityGcrf.getNormSq() > MIN_VELOCITY_NORM_SQ) {
      Vector3D dirIcrf = velocityGcrf.normalize();
      Vector3D dirJme = ctx.axisConvention().icrfToJme(dirIcrf);
      Vector3f forward = JmeVectorAdapter.toVector3f(dirJme);
      targetRotation.lookAt(forward, Vector3f.UNIT_Y);
      targetRotation.multLocal(MODEL_FORWARD_CORRECTION);
      targetRotation.normalizeLocal();

      if (!rotationInitialized) {
        currentRotation.set(targetRotation);
        rotationInitialized = true;
      } else {
        float alpha = 1f - (float) Math.pow(0.5, tpf / ROTATION_SMOOTHING_HALF_LIFE);
        Quaternion from = new Quaternion().set(currentRotation);
        currentRotation.slerp(from, targetRotation, alpha);
        currentRotation.normalizeLocal();
      }
    }

    if (rotationInitialized) {
      view.setRotationWorld(currentRotation);
    }
  }
}
