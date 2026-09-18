package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.app.view.AxisConvention;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;

/**
 * PHY-5 / L7, BUG-28 — a debris that has impacted must keep its whole pose on
 * the <em>turning</em> globe (with the ground track's impact marker), not at the frozen inertial
 * pose of the impact instant, which the rotating Earth drifts out from under. Tests the pure frame
 * algebra of {@link MissionRenderer#rotateWithGlobe} on both a position and a direction; the
 * on-screen placement itself is judged by eye.
 */
class MissionRendererDebrisIconTest {

  /** A surface point on the equator, in GCRF (ICRF axes, metres). */
  private static final Vector3D IMPACT = new Vector3D(6_378_137.0, 0.0, 0.0);

  @Test
  void landedPoseStaysPutWhenTheGlobeHasNotTurned() {
    Quaternion drawn = new Quaternion().fromAngleAxis(0.9f, Vector3f.UNIT_Y);
    Vector3D pinned = MissionRenderer.rotateWithGlobe(IMPACT, drawn, drawn);

    assertTrue(
        pinned.subtract(IMPACT).getNorm() < 1.0e-3,
        () -> "same drawn rotation must leave the impact point unmoved, drifted " + pinned);
  }

  @Test
  void aHeadingDirectionTurnsWithTheGlobe() {
    // The orientation half of the fix: a direction (velocity/roll) rides the globe by the same
    // operator that carries the position, so the mesh co-rotates rather than holding a fixed
    // inertial attitude while the ground turns under it.
    Vector3D heading = new Vector3D(0.0, 1.0, 0.0); // eastward, in the equatorial plane
    Quaternion atImpact = new Quaternion();
    Quaternion atNow = new Quaternion().fromAngleAxis((float) (Math.PI / 2.0), Vector3f.UNIT_Y);
    Vector3D turned = MissionRenderer.rotateWithGlobe(heading, atImpact, atNow);

    assertTrue(
        Math.abs(turned.getNorm() - 1.0) < 1.0e-6,
        () -> "a direction keeps unit length: " + turned);
    assertTrue(
        turned.subtract(heading).getNorm() > 1.0,
        () -> "a quarter turn must swing the heading well away from east, got " + turned);
  }

  @Test
  void landedPoseRidesTheTurningGlobeToTheSameGroundPoint() {
    Quaternion atImpact = new Quaternion(); // identity
    Quaternion atNow = new Quaternion().fromAngleAxis((float) (Math.PI / 2.0), Vector3f.UNIT_Y);
    Vector3D pinned = MissionRenderer.rotateWithGlobe(IMPACT, atImpact, atNow);

    // It moved with the globe (a quarter turn carries an equatorial point ~sqrt(2) radii away)...
    assertTrue(
        pinned.subtract(IMPACT).getNorm() > IMPACT.getNorm(),
        () -> "the icon must ride the turning globe, not stay frozen; moved to " + pinned);
    // ...without leaving the surface (rotation preserves the radius)...
    assertTrue(
        Math.abs(pinned.getNorm() - IMPACT.getNorm()) < 1.0,
        () -> "a rotation must preserve the radius, got " + pinned.getNorm());
    // ...and it is the SAME body-fixed ground point the impact marker sits on: de-rotating each by
    // its own drawn rotation yields the one seam-local coordinate.
    AxisConvention axes = AxisConvention.ICRF_TO_JME_Y_UP;
    Vector3f seamAtImpact =
        atImpact.inverse().mult(JmeVectorAdapter.toVector3f(axes.icrfToJme(IMPACT)));
    Vector3f seamAtNow = atNow.inverse().mult(JmeVectorAdapter.toVector3f(axes.icrfToJme(pinned)));
    assertTrue(
        seamAtNow.distance(seamAtImpact) < 1.0,
        () -> "icon and impact marker must be the same ground point, seam " + seamAtNow);
  }
}
