package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

/** The sheath's paraboloid shell, in units of the piece's body radius, nose at the origin. */
class ReentrySheathMeshTest {

  private static final float STANDOFF = 0.5f;
  private static final float LENGTH = 2.5f;
  private static final float OPENING = 1.6f;

  private final Mesh mesh = ReentrySheathMesh.unit(STANDOFF, LENGTH, OPENING);
  private final Vector3f[] positions = vectors(VertexBuffer.Type.Position);
  private final Vector3f[] normals = vectors(VertexBuffer.Type.Normal);
  private final float[] radial = radialCoordinates();

  @Test
  void theApexStandsOffTheNoseOnTheAxis() {
    int apex = highest();

    assertEquals(0f, positions[apex].x, 1e-6f);
    assertEquals(STANDOFF, positions[apex].y, 1e-6f);
    assertEquals(0f, positions[apex].z, 1e-6f);
    assertEquals(0f, radial[apex], 1e-6f);
  }

  @Test
  void theRimIsALengthBehindTheApexAtTheOpeningRadius() {
    int rimCount = 0;
    for (int i = 0; i < positions.length; i++) {
      if (radial[i] == 1f) {
        rimCount++;
        assertEquals(STANDOFF - LENGTH, positions[i].y, 1e-5f);
        assertEquals(OPENING, Math.hypot(positions[i].x, positions[i].z), 1e-5);
      }
    }
    assertEquals(24, rimCount);
  }

  @Test
  void everyVertexLiesOnTheParaboloid() {
    for (Vector3f p : positions) {
      double rho = Math.hypot(p.x, p.z);
      double expected = STANDOFF - LENGTH * (rho / OPENING) * (rho / OPENING);
      assertEquals(expected, p.y, 1e-5, () -> "vertex " + p);
    }
  }

  @Test
  void theShellHasAFewHundredTriangles() {
    assertEquals(360, mesh.getTriangleCount());
  }

  @Test
  void normalsPointOutOfTheCupAndAwayFromTheAxis() {
    for (int i = 0; i < positions.length; i++) {
      Vector3f n = normals[i];
      Vector3f p = positions[i];
      assertEquals(1f, n.length(), 1e-5f);
      assertTrue(n.y > 0f, () -> "normal " + n + " points into the cup");
      assertTrue(n.x * p.x + n.z * p.z >= -1e-6f, () -> "normal " + n + " points at the axis");
    }
  }

  @Test
  void frontFacesFaceOut() {
    int[] triangle = new int[3];
    for (int t = 0; t < mesh.getTriangleCount(); t++) {
      mesh.getTriangle(t, triangle);
      Vector3f a = positions[triangle[0]];
      Vector3f face =
          positions[triangle[1]].subtract(a).crossLocal(positions[triangle[2]].subtract(a));
      Vector3f outward =
          normals[triangle[0]].add(normals[triangle[1]]).addLocal(normals[triangle[2]]);
      int index = t;
      assertTrue(face.dot(outward) > 0f, () -> "triangle " + index + " winds inward");
    }
  }

  private int highest() {
    int best = 0;
    for (int i = 1; i < positions.length; i++) {
      if (positions[i].y > positions[best].y) {
        best = i;
      }
    }
    return best;
  }

  private Vector3f[] vectors(VertexBuffer.Type type) {
    return BufferUtils.getVector3Array((FloatBuffer) mesh.getBuffer(type).getData());
  }

  private float[] radialCoordinates() {
    FloatBuffer data = (FloatBuffer) mesh.getBuffer(VertexBuffer.Type.TexCoord).getData();
    float[] s = new float[data.limit() / 2];
    for (int i = 0; i < s.length; i++) {
      s[i] = data.get(2 * i);
    }
    return s;
  }
}
