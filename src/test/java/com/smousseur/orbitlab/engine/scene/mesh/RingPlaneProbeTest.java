package com.smousseur.orbitlab.engine.scene.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A ring is not a sphere and never becomes one, so {@link MeshFrameProbe#probe(Mesh)} rejects it —
 * correctly. But something is measurable on it, and it is the only thing a ring can be wrong about:
 * the plane it lies in. See {@code docs/orientation-planetes/01-decoupage.md} §2.2.
 */
class RingPlaneProbeTest {

  @Test
  void recoversThePlaneOfAFlatAnnulus() {
    RingPlane ring = MeshFrameProbe.probeRing(annulus(new Quaternion(), 2f, 3f)).orElseThrow();

    assertEquals(
        1.0, Math.abs(ring.normal().dot(Vector3f.UNIT_Z)), 1e-3, "normal " + ring.normal());
    assertEquals(0.0, ring.flatness(), 1e-6);
  }

  /** The plane has to follow the mesh, not the axes: an asset's ring arrives already turned. */
  @Test
  void thePlaneFollowsATurnedRing() {
    Quaternion turn =
        new Quaternion().fromAngleAxis(0.4f, new Vector3f(1f, 2f, 3f).normalizeLocal());

    RingPlane ring = MeshFrameProbe.probeRing(annulus(turn, 1f, 2f)).orElseThrow();

    assertEquals(
        1.0,
        Math.abs(ring.normal().dot(turn.mult(Vector3f.UNIT_Z))),
        1e-3,
        "normal " + ring.normal());
  }

  /**
   * A sphere is not flat, and must not be handed a plane. Without this the probe would report a
   * meaningless normal for every globe in the repo, and the tilt check built on it would compare
   * noise against noise.
   */
  @Test
  void refusesAGeometryThatIsNotFlat() {
    Optional<RingPlane> ring = MeshFrameProbe.probeRing(sphere());

    assertTrue(ring.isEmpty(), "a sphere has no plane, got " + ring);
  }

  /** A flat annulus of the given radii, lying in the XY plane and then turned. */
  private static Mesh annulus(Quaternion turn, float inner, float outer) {
    List<Vector3f> points = new ArrayList<>();
    for (int step = 0; step < 96; step++) {
      float angle = FastMath.TWO_PI * step / 96f;
      for (float radius : new float[] {inner, (inner + outer) * 0.5f, outer}) {
        points.add(
            turn.mult(
                new Vector3f(radius * FastMath.cos(angle), radius * FastMath.sin(angle), 0f)));
      }
    }
    return meshOf(points);
  }

  private static Mesh sphere() {
    List<Vector3f> points = new ArrayList<>();
    for (int ring = 1; ring < 12; ring++) {
      float colatitude = FastMath.PI * ring / 12f;
      for (int step = 0; step < 24; step++) {
        float azimuth = FastMath.TWO_PI * step / 24f;
        points.add(
            new Vector3f(
                FastMath.sin(colatitude) * FastMath.cos(azimuth),
                FastMath.sin(colatitude) * FastMath.sin(azimuth),
                FastMath.cos(colatitude)));
      }
    }
    return meshOf(points);
  }

  private static Mesh meshOf(List<Vector3f> points) {
    Mesh mesh = new Mesh();
    mesh.setBuffer(
        VertexBuffer.Type.Position,
        3,
        BufferUtils.createFloatBuffer(points.toArray(Vector3f[]::new)));
    return mesh;
  }
}
