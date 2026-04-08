package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.body.BodyView;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link SpacecraftPresenter}. No Orekit / no JME3 scene graph — the view is
 * a stub that records the last position and rotation set on it.
 */
class SpacecraftPresenterTest {

  private static final float POSITION_TOLERANCE = 1e-3f;

  private RecordingBodyView view;
  private SpacecraftPresenter presenter;
  private RenderContext ctx;

  @BeforeEach
  void setUp() {
    view = new RecordingBodyView();
    presenter = new SpacecraftPresenter("test-sc", view);
    ctx = RenderContext.planet(SolarSystemBody.EARTH);
  }

  @Test
  void updatePoseAppliesScaledPositionInJmeAxes() {
    // 7000 km along GCRF X (roughly LEO altitude + Earth radius).
    Vector3D pos = new Vector3D(7_000_000.0, 0.0, 0.0);
    Vector3D vel = new Vector3D(0.0, 7_500.0, 0.0);

    presenter.updatePose(pos, vel, 0.016f, ctx);

    Vector3f expected =
        JmeVectorAdapter.toVector3f(
            ctx.axisConvention().icrfToJme(RenderTransform.scaleMetersToUnits(pos, ctx)));
    assertNotNull(view.lastPosition);
    assertEquals(expected.x, view.lastPosition.x, POSITION_TOLERANCE);
    assertEquals(expected.y, view.lastPosition.y, POSITION_TOLERANCE);
    assertEquals(expected.z, view.lastPosition.z, POSITION_TOLERANCE);
  }

  @Test
  void firstNonZeroVelocitySetsRotationImmediately() {
    presenter.updatePose(Vector3D.ZERO, new Vector3D(0.0, 7_500.0, 0.0), 0.016f, ctx);

    assertNotNull(view.lastRotation, "rotation must be set on the first non-zero velocity sample");
    // The quaternion must be normalized (slerp input invariant).
    float norm = quaternionNorm(view.lastRotation);
    assertEquals(1f, norm, 1e-4f);
  }

  @Test
  void secondUpdateSlerpsTowardNewTarget() {
    Vector3D velA = new Vector3D(0.0, 7_500.0, 0.0);
    Vector3D velB = new Vector3D(7_500.0, 0.0, 0.0);

    presenter.updatePose(Vector3D.ZERO, velA, 0.016f, ctx);
    Quaternion afterFirst = new Quaternion(view.lastRotation);

    presenter.updatePose(Vector3D.ZERO, velB, 0.016f, ctx);
    Quaternion afterSecond = new Quaternion(view.lastRotation);

    // With smoothing half-life 0.15s and dt=0.016s, alpha ≈ 0.07 — small but non-zero.
    // The resulting rotation must differ from the first sample (slerp moved it) AND not be
    // identical to the raw target (slerp did not snap to it).
    assertTrue(dot(afterFirst, afterSecond) < 0.9999999f, "second rotation should differ from first");
    assertTrue(
        dot(afterFirst, afterSecond) > 0.9f,
        "second rotation should still be close to first after one frame of smoothing");
  }

  @Test
  void zeroVelocityPreservesPreviousRotation() {
    Vector3D initialVel = new Vector3D(0.0, 7_500.0, 0.0);
    presenter.updatePose(Vector3D.ZERO, initialVel, 0.016f, ctx);
    Quaternion firstRotation = new Quaternion(view.lastRotation);

    // Clear the view's last rotation, then call with zero velocity.
    view.lastRotation = null;
    presenter.updatePose(new Vector3D(1.0, 0.0, 0.0), Vector3D.ZERO, 0.016f, ctx);

    // Position must still be applied.
    assertNotNull(view.lastPosition);
    // Rotation must still be written, and must equal the previously computed rotation
    // (slerp skipped, last orientation preserved).
    assertNotNull(view.lastRotation);
    assertEquals(1f, Math.abs(dot(firstRotation, view.lastRotation)), 1e-4f);
  }

  @Test
  void zeroVelocityOnFirstCallDoesNotSetRotation() {
    presenter.updatePose(new Vector3D(1.0, 0.0, 0.0), Vector3D.ZERO, 0.016f, ctx);

    // Position is written on every call.
    assertNotNull(view.lastPosition);
    // Rotation was never initialised, so the view must not have been rotated.
    assertNull(view.lastRotation);
  }

  private static float quaternionNorm(Quaternion q) {
    return (float)
        Math.sqrt(
            q.getX() * q.getX() + q.getY() * q.getY() + q.getZ() * q.getZ() + q.getW() * q.getW());
  }

  private static float dot(Quaternion a, Quaternion b) {
    return a.getX() * b.getX() + a.getY() * b.getY() + a.getZ() * b.getZ() + a.getW() * b.getW();
  }

  /** Minimal {@link BodyView} stub that records the last position and rotation it is given. */
  private static final class RecordingBodyView implements BodyView {
    Vector3f lastPosition;
    Quaternion lastRotation;

    @Override
    public Spatial spatial() {
      return null;
    }

    @Override
    public Spatial nearSpatial() {
      return null;
    }

    @Override
    public void setPositionWorld(Vector3f position) {
      lastPosition = new Vector3f(position);
    }

    @Override
    public void setRotationWorld(Quaternion rotation) {
      lastRotation = new Quaternion(rotation);
    }

    @Override
    public void updateScreen(Camera cam) {}

    @Override
    public void setVisible(boolean visible) {}

    @Override
    public void detach() {}
  }
}
