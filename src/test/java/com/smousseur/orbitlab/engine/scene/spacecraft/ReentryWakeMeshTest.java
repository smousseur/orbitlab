package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

/**
 * The wake's unit tube: rings along {@code +Y} from the root to the tail, each vertex carrying its
 * unit radial direction, which the shader scales to the wake's radius at that ring.
 */
class ReentryWakeMeshTest {

  private final Mesh mesh = ReentryWakeMesh.unit();
  private final Vector3f[] positions = positions();

  @Test
  void theTubeHasSeventeenRingsOfSixteenSectors() {
    assertEquals(272, mesh.getVertexCount());
    assertEquals(512, mesh.getTriangleCount());
  }

  @Test
  void everyVertexIsAUnitRadialDirectionAtItsRing() {
    for (Vector3f p : positions) {
      assertEquals(1f, (float) Math.hypot(p.x, p.z), 1e-6f, () -> "vertex " + p);
      float ring = p.y * 16f;
      assertEquals(Math.round(ring), ring, 1e-4f, () -> "vertex " + p + " lies between rings");
    }
  }

  @Test
  void ringsRunFromTheRootToTheTail() {
    float lowest = Float.MAX_VALUE;
    float highest = -Float.MAX_VALUE;
    for (Vector3f p : positions) {
      lowest = Math.min(lowest, p.y);
      highest = Math.max(highest, p.y);
    }
    assertEquals(0f, lowest, 0f);
    assertEquals(1f, highest, 1e-6f);
  }

  @Test
  void everyTriangleSpansOneSegment() {
    int[] triangle = new int[3];
    for (int t = 0; t < mesh.getTriangleCount(); t++) {
      mesh.getTriangle(t, triangle);
      float minY = Float.MAX_VALUE;
      float maxY = -Float.MAX_VALUE;
      for (int v : triangle) {
        minY = Math.min(minY, positions[v].y);
        maxY = Math.max(maxY, positions[v].y);
      }
      int index = t;
      assertEquals(1f / 16f, maxY - minY, 1e-6f, () -> "triangle " + index);
    }
  }

  /**
   * Counter-clockwise seen from outside, so the material can cull the front faces and keep the far
   * wall of the tube only.
   */
  @Test
  void frontFacesFaceOut() {
    int[] triangle = new int[3];
    for (int t = 0; t < mesh.getTriangleCount(); t++) {
      mesh.getTriangle(t, triangle);
      Vector3f a = positions[triangle[0]];
      Vector3f b = positions[triangle[1]];
      Vector3f c = positions[triangle[2]];
      Vector3f face = b.subtract(a).crossLocal(c.subtract(a));
      Vector3f centre = a.add(b).addLocal(c).divideLocal(3f);
      Vector3f outward = new Vector3f(centre.x, 0f, centre.z);
      int index = t;
      assertTrue(face.dot(outward) > 0f, () -> "triangle " + index + " faces into the tube");
    }
  }

  private Vector3f[] positions() {
    VertexBuffer buffer = mesh.getBuffer(VertexBuffer.Type.Position);
    if (buffer == null) {
      return new Vector3f[0];
    }
    FloatBuffer data = (FloatBuffer) buffer.getData();
    data.rewind();
    Vector3f[] vectors = new Vector3f[data.limit() / 3];
    for (int i = 0; i < vectors.length; i++) {
      vectors[i] = new Vector3f(data.get(), data.get(), data.get());
    }
    return vectors;
  }
}
