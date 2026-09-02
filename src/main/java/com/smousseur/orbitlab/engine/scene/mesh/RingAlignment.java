package com.smousseur.orbitlab.engine.scene.mesh;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

/**
 * The turn that brings a ring's plane onto its globe's equator — the whole of what a ring can be
 * wrong about (see {@code docs/orientation-planetes/01-decoupage.md} §2.2 and {@code docs/bugs.md},
 * BUG-20).
 *
 * <p><b>An angle alone is not a correction.</b> Thirteen degrees about the wrong axis leaves the
 * ring just as far out as before, so the axis travels with the angle, exactly as it does in the
 * verdict for a globe.
 *
 * <p>This is also the one correction in the chantier that cannot be applied in code: {@code
 * PlanetMeshCorrection} turns the whole model, globe and ring together, so it cannot change the
 * angle <em>between</em> them. Only a re-export can.
 *
 * @param angleDeg the turn, in {@code [0, 90]} — never more, since a plane tilted past a right
 *     angle is the same plane tilted back the other way
 * @param axis the axis of that turn, in the model's own axes. Undefined and left on an arbitrary
 *     unit vector when the ring is already aligned, there being no turn to make
 */
public record RingAlignment(float angleDeg, Vector3f axis) {

  /**
   * Below this the ring counts as equatorial. Real ring systems sit within hundredths of a degree
   * of their planet's equator, so anything a modeller could plausibly leave behind is far above it.
   */
  public static final float MAX_TILT_DEG = 0.5f;

  /**
   * The turn taking a ring's plane onto a globe's equator.
   *
   * @param ring the measured ring plane
   * @param globePole the pole of the globe in the same model, in the same axes
   * @return the correction
   */
  public static RingAlignment between(RingPlane ring, Vector3f globePole) {
    Vector3f normal = ring.normal().normalize();
    Vector3f pole = globePole.normalize();
    // A plane has no preferred side, so the measured normal may point either way. Flipping it to
    // the near side is what keeps a 13 degree defect from being reported as 167.
    if (normal.dot(pole) < 0f) {
      normal.negateLocal();
    }
    float angleDeg = FastMath.acos(FastMath.clamp(normal.dot(pole), -1f, 1f)) * FastMath.RAD_TO_DEG;
    Vector3f axis = normal.cross(pole);
    return new RingAlignment(
        angleDeg, axis.lengthSquared() < 1e-12f ? new Vector3f(1f, 0f, 0f) : axis.normalizeLocal());
  }

  /** Whether the ring already lies in its globe's equatorial plane. */
  public boolean isAligned() {
    return angleDeg <= MAX_TILT_DEG;
  }
}
