package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import org.junit.jupiter.api.Test;

/** The closed truncated cone a plume is marched in, wound so its outside faces the camera. */
class PlumeHullMeshTest {

  /**
   * Back faces are culled while the camera is outside the hull: a triangle wound the other way
   * would leave a hole where the jet should be.
   */
  @Test
  void everyFaceIsWoundOutward() {
    Mesh mesh = PlumeHullMesh.create();
    PlumeHullMesh.reshape(mesh, 1f, 3f, 10f);

    FloatBuffer positions = (FloatBuffer) mesh.getBuffer(VertexBuffer.Type.Position).getData();
    ShortBuffer indices = (ShortBuffer) mesh.getBuffer(VertexBuffer.Type.Index).getData();
    Vector3f centre = new Vector3f(0f, -5f, 0f);
    for (int t = 0; t + 2 < indices.limit(); t += 3) {
      Vector3f a = vertex(positions, indices.get(t));
      Vector3f b = vertex(positions, indices.get(t + 1));
      Vector3f c = vertex(positions, indices.get(t + 2));
      Vector3f normal = b.subtract(a).crossLocal(c.subtract(a));
      Vector3f outward = a.add(b).addLocal(c).divideLocal(3f).subtractLocal(centre);
      assertTrue(normal.dot(outward) > 0f, "triangle " + t / 3 + " is wound inward");
    }
  }

  @Test
  void theHullSpansTheJetFromTheExitToItsTail() {
    Mesh mesh = PlumeHullMesh.create();
    PlumeHullMesh.reshape(mesh, 1f, 3f, 10f);

    BoundingBox bound = (BoundingBox) mesh.getBound();
    assertEquals(0f, bound.getMax(null).y, 1e-5f);
    assertEquals(-10f, bound.getMin(null).y, 1e-5f);
    assertEquals(3f, bound.getMax(null).x, 1e-5f);
  }

  private static Vector3f vertex(FloatBuffer positions, int index) {
    return new Vector3f(
        positions.get(3 * index), positions.get(3 * index + 1), positions.get(3 * index + 2));
  }
}
