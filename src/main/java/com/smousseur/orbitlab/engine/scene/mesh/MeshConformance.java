package com.smousseur.orbitlab.engine.scene.mesh;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

/**
 * Verdict of a measured {@link MeshFrame} against the export convention — pole on {@code +Z},
 * column {@code u = 0} on {@code −X}, exact equirectangular map, chirality {@code −360°/u} — which
 * is not a convention chosen on paper but the one {@code earth.gltf} and {@code moon.gltf} already
 * carry (see {@code docs/orientation-planetes/01-decoupage.md} §4.2).
 *
 * <p>The four cases are deliberately distinct rather than a boolean plus a number, because they
 * call for different actions: a rotation is fixable either in Blender or as an {@code align}
 * constant, a mirrored map takes a UV flip and no rotation will do, and a map that is not a
 * lat/long unwrap at all cannot be calibrated by any means — Uranus is the standing example.
 */
public sealed interface MeshConformance {

  /** The asset already carries the reference frame; nothing to correct, nothing to commit. */
  record Conforming() implements MeshConformance {}

  /**
   * The asset is a sound lat/long sphere turned away from the reference.
   *
   * @param angleDeg the shortest turn that brings it back, in {@code [0, 180]}
   * @param axis the axis of that turn, in the model's own axes
   */
  record NeedsRotation(float angleDeg, Vector3f axis) implements MeshConformance {}

  /**
   * The texture runs the wrong way round the pole. No rotation can fix this.
   *
   * @param azimuthDegreesPerU the measured chirality, for the report
   */
  record Mirrored(float azimuthDegreesPerU) implements MeshConformance {}

  /**
   * Not an equirectangular unwrap, so nothing measured on it means anything.
   *
   * @param residualDeg the measured residual, for the report
   */
  record NotALatLongMap(float residualDeg) implements MeshConformance {}

  /** The reference pole: the direction the texture's {@code v = 0} edge must point at. */
  Vector3f REFERENCE_POLE = new Vector3f(0f, 0f, 1f);

  /**
   * The reference prime meridian: the direction the texture's column {@code u = 0} must point at.
   */
  Vector3f REFERENCE_PRIME_MERIDIAN = new Vector3f(-1f, 0f, 0f);

  /**
   * Above this residual the mesh is not a lat/long unwrap and nothing measured on it means
   * anything. Placed between the two measured extremes rather than at an arbitrary round number:
   * Mercury, the coarsest sphere in the repo, sits at 0.9°, and Uranus, which genuinely is not a
   * lat/long map, at 49.3°.
   */
  float MAX_RESIDUAL_DEG = 2f;

  /**
   * Below this turn the asset counts as already conforming. A well-exported asset lands at 0; the
   * smallest real offset in the repo is Neptune's, whose sloppy node rotation leaves its meridian
   * 4.5° out on its own, so this threshold separates noise from anything worth correcting.
   */
  float MAX_ANGLE_DEG = 1f;

  /**
   * Judges a measured frame.
   *
   * @param frame the frame to judge
   * @return the verdict
   */
  static MeshConformance of(MeshFrame frame) {
    if (frame.equirectangularResidualDeg() > MAX_RESIDUAL_DEG) {
      return new NotALatLongMap(frame.equirectangularResidualDeg());
    }
    if (frame.azimuthDegreesPerU() > 0f) {
      return new Mirrored(frame.azimuthDegreesPerU());
    }

    Quaternion correction = alignment(frame);
    // q and −q are the same rotation; taking the one with a positive scalar part is what makes
    // toAngleAxis answer the shortest turn rather than its 360° complement.
    if (correction.getW() < 0f) {
      correction.set(
          -correction.getX(), -correction.getY(), -correction.getZ(), -correction.getW());
    }
    Vector3f axis = new Vector3f();
    float angleDeg = correction.toAngleAxis(axis) * FastMath.RAD_TO_DEG;
    return angleDeg <= MAX_ANGLE_DEG ? new Conforming() : new NeedsRotation(angleDeg, axis);
  }

  /**
   * The rotation that brings a measured frame onto the reference one.
   *
   * <p>Shared with {@code PlanetMeshCorrection}, which composes it with the body's own λ0: the
   * verdict below and the correction actually applied at render time must be the same computation,
   * or a body could be declared conforming while being drawn turned.
   *
   * @param frame the measured frame
   * @return the rotation taking it onto the reference
   */
  static Quaternion alignment(MeshFrame frame) {
    return basis(REFERENCE_PRIME_MERIDIAN, REFERENCE_POLE)
        .mult(basis(frame.primeMeridian(), frame.pole()).inverse());
  }

  /**
   * The rotation taking the world axes onto the orthonormal basis built from a frame.
   *
   * <p>The meridian is re-derived from the pole rather than trusted as given: a real mesh does not
   * measure exactly perpendicular — Mercury's is 3° out, its sphere being irregular enough to score
   * a 0.87° residual — and feeding a skewed triple to {@code fromAxes} yields a quaternion that is
   * not a rotation at all.
   */
  private static Quaternion basis(Vector3f primeMeridian, Vector3f pole) {
    Vector3f z = pole.normalize();
    Vector3f y = z.cross(primeMeridian).normalizeLocal();
    Vector3f x = y.cross(z).normalizeLocal();
    return new Quaternion().fromAxes(x, y, z);
  }
}
