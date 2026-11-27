package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.jme3.math.FastMath;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * The closed truncated cone a nozzle cluster's plume is marched in: its top ring on the exit plane,
 * its bottom ring at the jet's tail, both capped, wound counter-clockwise seen from outside. The
 * shader finds the jet inside it analytically; the hull only decides which pixels march, so it is
 * reshaped to the jet's cone as the air thins rather than built once around the widest shape, which
 * on a sea-level jet would march five times its width of empty pixels.
 */
final class PlumeHullMesh {

  /** Sides of the rings: the hull only has to enclose the cone, which 24 sides do within 1 %. */
  static final int SEGMENTS = 24;

  private PlumeHullMesh() {}

  /**
   * Builds a hull, unit-sized until {@link #reshape}d.
   *
   * @return the hull mesh
   */
  static Mesh create() {
    Mesh mesh = new Mesh();
    int vertices = 2 * SEGMENTS + 2;
    mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(3 * vertices));
    int topCentre = 2 * SEGMENTS;
    int bottomCentre = topCentre + 1;
    ShortBuffer indices = BufferUtils.createShortBuffer(4 * SEGMENTS * 3);
    for (int i = 0; i < SEGMENTS; i++) {
      int j = (i + 1) % SEGMENTS;
      int topI = i;
      int topJ = j;
      int bottomI = SEGMENTS + i;
      int bottomJ = SEGMENTS + j;
      put(indices, topI, topJ, bottomI);
      put(indices, bottomI, topJ, bottomJ);
      put(indices, topCentre, topJ, topI);
      put(indices, bottomCentre, bottomI, bottomJ);
    }
    indices.flip();
    mesh.setBuffer(VertexBuffer.Type.Index, 3, indices);
    reshape(mesh, 1f, 1f, 1f);
    return mesh;
  }

  /**
   * Fits the hull to a cone.
   *
   * @param mesh a hull from {@link #create}
   * @param topRadius the radius on the exit plane
   * @param bottomRadius the radius at the tail
   * @param length how far the tail lies under the exit plane
   */
  static void reshape(Mesh mesh, float topRadius, float bottomRadius, float length) {
    VertexBuffer buffer = mesh.getBuffer(VertexBuffer.Type.Position);
    FloatBuffer positions = (FloatBuffer) buffer.getData();
    positions.clear();
    for (int ring = 0; ring < 2; ring++) {
      float radius = ring == 0 ? topRadius : bottomRadius;
      float y = ring == 0 ? 0f : -length;
      for (int i = 0; i < SEGMENTS; i++) {
        float angle = FastMath.TWO_PI * i / SEGMENTS;
        positions.put(radius * FastMath.cos(angle)).put(y).put(radius * FastMath.sin(angle));
      }
    }
    positions.put(0f).put(0f).put(0f);
    positions.put(0f).put(-length).put(0f);
    positions.flip();
    buffer.updateData(positions);
    mesh.updateBound();
  }

  private static void put(ShortBuffer indices, int a, int b, int c) {
    indices.put((short) a).put((short) b).put((short) c);
  }
}
