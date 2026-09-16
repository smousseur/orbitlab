package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

class DebrisGroundTrackTest {

  private static Quaternion spinY(float radians) {
    return new Quaternion().fromAngleAxis(radians, Vector3f.UNIT_Y);
  }

  @Test
  void anEarthFixedPointGivesAConstantSeamLocal() {
    // A point fixed to the ground, seen at three instants as the globe turns under it: its inertial
    // position is the drawn rotation applied to the fixed ground point. Its seam-local coordinate
    // must come out the same every time — that is what makes the track stick to the ground.
    Vector3f ground = new Vector3f(6400f, 120f, -30f);
    AbsoluteDate t0 = AbsoluteDate.J2000_EPOCH;
    AbsoluteDate t1 = t0.shiftedBy(120.0);
    AbsoluteDate t2 = t0.shiftedBy(300.0);
    Map<AbsoluteDate, Quaternion> rotations =
        Map.of(t0, spinY(0.0f), t1, spinY(0.02f), t2, spinY(0.05f));
    Function<AbsoluteDate, Optional<Quaternion>> rotationAt =
        d -> Optional.ofNullable(rotations.get(d));
    List<Vector3f> gcrf =
        List.of(
            rotations.get(t0).mult(ground),
            rotations.get(t1).mult(ground),
            rotations.get(t2).mult(ground));

    DebrisGroundTrack track =
        DebrisGroundTrack.tryBuild(gcrf, List.of(t0, t1, t2), rotationAt).orElseThrow();

    for (Vector3f vertex : track.seamLocalVertices()) {
      assertTrue(
          vertex.distance(ground) < 1e-2f, "each seam-local vertex is the fixed ground point");
    }
  }

  @Test
  void theImpactIsTheLastGroundPoint() {
    Vector3f ground = new Vector3f(6371f, 0f, 0f);
    AbsoluteDate t = AbsoluteDate.J2000_EPOCH;
    DebrisGroundTrack track =
        DebrisGroundTrack.tryBuild(
                List.of(new Vector3f(6400f, 50f, 0f), ground),
                List.of(t, t.shiftedBy(60.0)),
                d -> Optional.of(spinY(0.0f)))
            .orElseThrow();
    assertEquals(0.0f, track.impactSeamLocal().distance(ground), 1e-4f);
  }

  @Test
  void aMissingRotationYieldsNoTrack() {
    AbsoluteDate t = AbsoluteDate.J2000_EPOCH;
    Optional<DebrisGroundTrack> track =
        DebrisGroundTrack.tryBuild(
            List.of(new Vector3f(1f, 2f, 3f), new Vector3f(4f, 5f, 6f)),
            List.of(t, t.shiftedBy(1.0)),
            d -> d.equals(t) ? Optional.of(spinY(0.0f)) : Optional.empty());
    assertTrue(track.isEmpty(), "a track cannot be built until every rotation is available");
  }
}
