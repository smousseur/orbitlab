package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.planet.GroundCorrection;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import java.util.BitSet;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * The vertices of one trajectory ribbon that can come within {@link
 * GroundCorrection#BLEND_HEIGHT_METERS} of the Earth's ground, and the displacement that brings
 * each onto the drawn globe this frame.
 *
 * <p>They are found once per trail, the test being a norm the Earth's rotation leaves unchanged,
 * and corrected on every frame: the ribbon is inertial and the globe turns under it, so a
 * correction frozen at a vertex's own date would sink it into whatever facet the rotation has since
 * carried beneath it.
 */
final class TrailGroundCorrection {

  private final TrajectoryPolyline trail;
  private final BitSet nearGround;

  /**
   * Finds the vertices of {@code trail} that can come near the ground.
   *
   * @param trail the ribbon's polyline
   */
  TrailGroundCorrection(TrajectoryPolyline trail) {
    this.trail = Objects.requireNonNull(trail, "trail");
    this.nearGround = new BitSet(trail.size());
    for (int i = 0; i < trail.size(); i++) {
      // The arc test comes first: a trail with no Earth arc has no Earth table to read from.
      if (trail.arcs().get(trail.arcOf(i)).arc().body() == SolarSystemBody.EARTH
          && GroundCorrection.mayReach(trail.positionAt(i, SolarSystemBody.EARTH))) {
        nearGround.set(i);
      }
    }
  }

  /**
   * Whether no vertex of the trail can come near the ground.
   *
   * @return {@code true} when the ribbon never needs a correction
   */
  boolean isEmpty() {
    return nearGround.isEmpty();
  }

  /**
   * Whether vertex {@code index} can come near the ground.
   *
   * @param index the vertex index
   * @return {@code true} when the vertex may need a correction
   */
  boolean isNearGround(int index) {
    return nearGround.get(index);
  }

  /**
   * The displacement bringing vertex {@code index} onto the drawn ground this frame. A vector, so
   * it applies unchanged in any render body's frame, all of them ICRF-oriented.
   *
   * @param index a vertex for which {@link #isNearGround} holds
   * @param correction the correction of the frame
   * @return the displacement, in metres
   */
  Vector3D displacementAt(int index, GroundCorrection correction) {
    Vector3D position = trail.positionAt(index, SolarSystemBody.EARTH);
    return correction.correct(position).subtract(position);
  }
}
