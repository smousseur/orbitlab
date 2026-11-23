package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.jme3.math.FastMath;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;

/**
 * The tube of a re-entry wake, for a unit length and a unit radius: {@link #RINGS} rings along
 * {@code +Y}, from the root at the origin to the tail at {@code y = 1}, of {@link #SECTORS}
 * vertices each.
 *
 * <p>Each vertex is {@code (cos φ, s, sin φ)}: its unit radial direction, and its ring's place
 * {@code s} from the root (0) to the tail (1). The tube has no size of its own: {@code
 * ReentryWake.vert} places each ring along the wake's length and scales it to the wake's radius
 * there — in metres up close, never under a floor in pixels far away. One mesh therefore serves
 * every wake and never needs rebuilding. It is a solid of revolution rather than a band facing the
 * eye, so it looks the same from any side, a disc when seen from behind.
 *
 * <p>Every triangle winds counter-clockwise seen from outside, so a material culling front faces
 * draws the tube's far wall only.
 */
public final class ReentryWakeMesh {

  /** Rings from the root to the tail: enough for the taper and the fades to read smooth. */
  static final int RINGS = 17;

  /** Vertices around each ring. With {@link #RINGS}, 16 × 16 × 2 = 512 triangles. */
  static final int SECTORS = 16;

  private ReentryWakeMesh() {}

  /**
   * Builds the unit tube.
   *
   * @return the mesh
   */
  public static Mesh unit() {
    float[] positions = new float[3 * RINGS * SECTORS];
    for (int ring = 0; ring < RINGS; ring++) {
      float s = (float) ring / (RINGS - 1);
      for (int sector = 0; sector < SECTORS; sector++) {
        float phi = FastMath.TWO_PI * sector / SECTORS;
        int v = vertexOf(ring, sector);
        positions[3 * v] = FastMath.cos(phi);
        positions[3 * v + 1] = s;
        positions[3 * v + 2] = FastMath.sin(phi);
      }
    }
    short[] indices = new short[3 * 2 * (RINGS - 1) * SECTORS];
    int i = 0;
    for (int ring = 0; ring < RINGS - 1; ring++) {
      for (int sector = 0; sector < SECTORS; sector++) {
        short here = (short) vertexOf(ring, sector);
        short next = (short) vertexOf(ring, sector + 1);
        short above = (short) vertexOf(ring + 1, sector);
        short aboveNext = (short) vertexOf(ring + 1, sector + 1);
        indices[i++] = here;
        indices[i++] = above;
        indices[i++] = next;
        indices[i++] = next;
        indices[i++] = above;
        indices[i++] = aboveNext;
      }
    }
    Mesh mesh = new Mesh();
    mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
    mesh.setBuffer(VertexBuffer.Type.Index, 3, indices);
    mesh.updateCounts();
    mesh.updateBound();
    return mesh;
  }

  private static int vertexOf(int ring, int sector) {
    return ring * SECTORS + Math.floorMod(sector, SECTORS);
  }
}
