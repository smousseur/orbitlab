package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;

/**
 * The shell of a re-entry sheath: a paraboloid of revolution about {@code +Y} — a detached bow
 * shock standing off the nose — in units of the piece's body radius, so one mesh is scaled onto
 * every piece.
 *
 * <p>The nose is the origin. The apex stands {@code standoff} ahead of it, and the shell opens
 * backward as {@code y(ρ) = standoff − length·(ρ / opening)²}, reaching its rim {@code length}
 * behind the apex at radius {@code opening}. The normals are the surface's own gradient, pointing
 * out of the cup, and every triangle winds counter-clockwise seen from outside, so a back-face cull
 * keeps the outer surface only. The texture coordinate's {@code u} is the radial parameter {@code ρ
 * / opening}: 0 at the apex, 1 at the rim — what the shader grades the glow along.
 */
public final class ReentrySheathMesh {

  /** Sectors around the axis. */
  static final int SECTORS = 24;

  /** Rings from the apex to the rim. With {@link #SECTORS}, 24 + 7 × 48 = 360 triangles. */
  static final int RINGS = 8;

  private ReentrySheathMesh() {}

  /**
   * Builds a shell for a unit body radius.
   *
   * @param standoff how far ahead of the nose the apex stands, in body radii
   * @param length how far back from its apex the rim is, in body radii
   * @param opening the rim's radius, in body radii
   * @return the mesh
   */
  public static Mesh unit(float standoff, float length, float opening) {
    int vertexCount = 1 + RINGS * SECTORS;
    float[] positions = new float[3 * vertexCount];
    float[] normals = new float[3 * vertexCount];
    float[] texCoords = new float[2 * vertexCount];
    normals[1] = 1f;
    positions[1] = standoff;
    float slope = 2f * length / (opening * opening);
    Vector3f normal = new Vector3f();
    for (int ring = 1; ring <= RINGS; ring++) {
      float s = (float) ring / RINGS;
      float rho = s * opening;
      float y = standoff - length * s * s;
      for (int sector = 0; sector < SECTORS; sector++) {
        float theta = FastMath.TWO_PI * sector / SECTORS;
        float x = rho * FastMath.cos(theta);
        float z = rho * FastMath.sin(theta);
        int v = vertexOf(ring, sector);
        positions[3 * v] = x;
        positions[3 * v + 1] = y;
        positions[3 * v + 2] = z;
        normal.set(slope * x, 1f, slope * z).normalizeLocal();
        normals[3 * v] = normal.x;
        normals[3 * v + 1] = normal.y;
        normals[3 * v + 2] = normal.z;
        texCoords[2 * v] = s;
      }
    }
    Mesh mesh = new Mesh();
    mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
    mesh.setBuffer(VertexBuffer.Type.Normal, 3, normals);
    mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, texCoords);
    mesh.setBuffer(VertexBuffer.Type.Index, 3, indices());
    mesh.updateCounts();
    mesh.updateBound();
    return mesh;
  }

  private static short[] indices() {
    short[] indices = new short[3 * (SECTORS + (RINGS - 1) * SECTORS * 2)];
    int i = 0;
    for (int sector = 0; sector < SECTORS; sector++) {
      indices[i++] = 0;
      indices[i++] = (short) vertexOf(1, sector + 1);
      indices[i++] = (short) vertexOf(1, sector);
    }
    for (int ring = 1; ring < RINGS; ring++) {
      for (int sector = 0; sector < SECTORS; sector++) {
        short inner = (short) vertexOf(ring, sector);
        short innerNext = (short) vertexOf(ring, sector + 1);
        short outer = (short) vertexOf(ring + 1, sector);
        short outerNext = (short) vertexOf(ring + 1, sector + 1);
        indices[i++] = inner;
        indices[i++] = outerNext;
        indices[i++] = outer;
        indices[i++] = inner;
        indices[i++] = innerNext;
        indices[i++] = outerNext;
      }
    }
    return indices;
  }

  private static int vertexOf(int ring, int sector) {
    return 1 + (ring - 1) * SECTORS + Math.floorMod(sector, SECTORS);
  }
}
