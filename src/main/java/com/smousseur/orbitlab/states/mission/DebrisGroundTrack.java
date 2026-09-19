package com.smousseur.orbitlab.states.mission;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.orekit.time.AbsoluteDate;

/**
 * A jettisoned debris' fall re-expressed in the Earth's rotating (drawn-globe) frame, so the curve
 * sticks to the ground and its last point is the impact lat/lon.
 *
 * <p>Each sample's inertial position {@code D(t)} becomes {@code Q(t)⁻¹ · D(t)}, where {@code Q(t)}
 * is the globe's <em>drawn</em> rotation at that instant ({@link
 * com.smousseur.orbitlab.engine.scene.planet.PlanetDrawnRotation}). Hung under the Earth
 * rotating-frame node (which applies the current {@code Q}), the sample at the current time returns
 * to its true inertial position — so it coincides with the debris mesh and does not jump at
 * separation — while every other sample rides the globe as if fixed to it.
 */
final class DebrisGroundTrack {

  private final List<Vector3f> seamLocalVertices;

  private DebrisGroundTrack(List<Vector3f> seamLocalVertices) {
    this.seamLocalVertices = seamLocalVertices;
  }

  /**
   * Builds a ground track from a fall's inertial samples, or empty while any sample's drawn
   * rotation is not yet available (the ephemeris buffer has not reached it) — the caller retries
   * next frame.
   *
   * @param gcrfJme the fall samples, already in near-view JME units about the geocentre
   * @param times the sample dates, parallel to {@code gcrfJme}
   * @param rotationAt the globe's drawn rotation at a date
   * @return the ground track, or empty if a rotation is missing
   */
  static Optional<DebrisGroundTrack> tryBuild(
      List<Vector3f> gcrfJme,
      List<AbsoluteDate> times,
      Function<AbsoluteDate, Optional<Quaternion>> rotationAt) {
    List<Vector3f> local = new ArrayList<>(gcrfJme.size());
    for (int i = 0; i < gcrfJme.size(); i++) {
      Optional<Quaternion> rotation = rotationAt.apply(times.get(i));
      if (rotation.isEmpty()) {
        return Optional.empty();
      }
      local.add(rotation.get().inverse().mult(gcrfJme.get(i)));
    }
    return Optional.of(new DebrisGroundTrack(List.copyOf(local)));
  }

  /** The fall vertices, in the Earth rotating-frame's own coordinates. */
  List<Vector3f> seamLocalVertices() {
    return seamLocalVertices;
  }

  /** The impact point — the last vertex — in the Earth rotating-frame's coordinates. */
  Vector3f impactSeamLocal() {
    return seamLocalVertices.get(seamLocalVertices.size() - 1);
  }
}
