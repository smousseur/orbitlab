package com.smousseur.orbitlab.engine.scene;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import java.util.Objects;

/**
 * Builds the camera-facing ribbon that replaces {@code LineStrip} / {@code LineLoop} everywhere in
 * the application (spec {@code docs/graphics-effects/ribbon-lines.md}).
 *
 * <p><b>What this class does and does not do.</b> It produces the <em>undisplaced</em> mesh: each
 * polyline point becomes two vertices carrying the same position and opposite {@code side} flags,
 * plus the tangent the shader needs. The transverse offset — the part that depends on the camera —
 * happens in {@code MatDefs/Fx/Ribbon.vert}, and it happens there so that a planetary orbit's
 * buffer stays static: the ten orbits are 40 960 points written once per window rebuild, and
 * expanding them on the CPU would turn that into 81 920 vertices rewritten every frame because the
 * camera moved a pixel (§2).
 *
 * <p><b>No index buffer, and none is possible.</b> A {@code TriangleStrip} of {@code 2N} vertices
 * is {@code 2(N−1)} triangles by construction. Indexing would be the obvious economy and it cannot
 * work here: the two vertices of a pair differ <em>only</em> by their {@code side} attribute, so an
 * index that made them one vertex would give them one side as well. The duplication is the form,
 * not a shortcut (§7.1).
 *
 * <p><b>No miter, no bevel, no round join — deliberately.</b> The tangent at a point is the chord
 * of its two neighbours, {@code p[i+1] − p[i−1]}, which makes two consecutive quads share both of
 * their vertices: there is no gap to fill and therefore no join to compute. What is left is a pinch
 * of {@code cos(θ/2)} in a turn, and our polylines are densely sampled smooth curves — 0,09° per
 * segment on a planetary orbit, ~20° in the worst decimated coast, i.e. 1,5 % of width lost at the
 * apex of a bend. Invisible. This paragraph exists so that the whole "polyline rendering"
 * literature on joins is not re-imported into a codebase whose data does not need it (§7.5).
 *
 * <p>Attribute layout, per vertex (§7.2):
 *
 * <ul>
 *   <li>{@code Position} — the polyline point, unmodified;
 *   <li>{@code Normal} — the unit tangent. The buffer is <em>detourned</em> from its usual meaning:
 *       it is the only three-component buffer available without moving to {@code TexCoord2…8}, and
 *       the shader that reads it is ours;
 *   <li>{@code TexCoord} — {@code (side = ±1, normalised arc length)}. The first component is
 *       consumed by the vertex shader, the second by the fragment shader; the two never collide;
 *   <li>{@code Color} — optional, one shade per run for mission trajectories.
 * </ul>
 */
public final class RibbonMeshBuilder {

  /** Both edges of the ribbon, one vertex each. */
  public static final int VERTICES_PER_POINT = 2;

  /**
   * Below this, a chord is treated as having no direction at all.
   *
   * <p>Positions are render units, and the two scales in play are 1 unit = 10⁹ m (far) and 1 unit =
   * 10³ m (near); this sits far under any real sample spacing in either, and above the rounding of
   * a {@code float} difference.
   */
  private static final float DEGENERATE = 1e-12f;

  private RibbonMeshBuilder() {}

  /**
   * Number of vertices a band of {@code pointCount} polyline points occupies.
   *
   * @param pointCount number of polyline points
   * @param closed whether the first pair is repeated at the end to shut the band
   * @return the vertex count
   */
  public static int vertexCount(int pointCount, boolean closed) {
    return VERTICES_PER_POINT * (pointCount + (closed ? 1 : 0));
  }

  /**
   * Number of triangles the strip yields — {@code vertexCount − 2}, as for any triangle strip.
   *
   * @param pointCount number of polyline points
   * @param closed whether the band is shut
   * @return the triangle count
   */
  public static int triangleCount(int pointCount, boolean closed) {
    return Math.max(0, vertexCount(pointCount, closed) - 2);
  }

  /**
   * Allocates an empty ribbon mesh with room for {@code maxPoints} polyline points.
   *
   * <p>The buffers are sized once and rewritten in place afterwards, which is what lets the mission
   * renderer flush a growing prefix every frame without allocating.
   *
   * @param maxPoints capacity, in polyline points
   * @param closed whether the band will be shut, which costs one extra pair
   * @param withColor whether to allocate a per-vertex colour buffer. Mission trajectories need one
   *     (a shade per phase run); planetary orbits do not — their colour is uniform and stays a
   *     material parameter, which is also what keeps their buffer at 2,6 Mo instead of 3,9
   * @return the empty mesh, in {@link Mesh.Mode#TriangleStrip}
   */
  public static Mesh allocate(int maxPoints, boolean closed, boolean withColor) {
    if (maxPoints < 2) {
      throw new IllegalArgumentException("A ribbon needs at least 2 points, got " + maxPoints);
    }
    int vertices = vertexCount(maxPoints, closed);

    Mesh mesh = new Mesh();
    mesh.setMode(Mesh.Mode.TriangleStrip);
    mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(vertices * 3));
    mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(vertices * 3));
    mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(vertices * 2));
    if (withColor) {
      mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(vertices * 4));
    }
    return mesh;
  }

  /**
   * Grows the mesh's buffers if {@code pointCount} no longer fits, leaving the mesh instance itself
   * alone so the geometry holding it keeps its reference.
   *
   * <p>For the orbits this fires at most once: the dataset read at startup and the window the
   * runtime recomputes rarely hold the same number of points.
   *
   * @param mesh a mesh from {@link #allocate}
   * @param pointCount the number of polyline points that must fit
   * @param closed whether the band is shut, which costs one extra pair
   */
  public static void ensureCapacity(Mesh mesh, int pointCount, boolean closed) {
    Objects.requireNonNull(mesh, "mesh");
    int needed = vertexCount(pointCount, closed);
    if (mesh.getBuffer(VertexBuffer.Type.Position).getData().capacity() >= needed * 3) {
      return;
    }
    boolean withColor = mesh.getBuffer(VertexBuffer.Type.Color) != null;
    mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(needed * 3));
    mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(needed * 3));
    mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(needed * 2));
    if (withColor) {
      mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(needed * 4));
    }
  }

  /**
   * Writes {@code pointCount} points of {@code xyz} into the mesh's geometry buffers.
   *
   * <p>Only a prefix need be written: {@link Mesh#updateCounts()} derives the vertex count from the
   * position buffer's limit, so a shorter write simply draws less. That is exactly how the mission
   * trajectory grows, and why the colour buffer is filled to capacity rather than to the drawn
   * length — an attribute buffer only has to be <em>at least</em> as long as the position buffer.
   *
   * @param mesh a mesh from {@link #allocate}
   * @param xyz the polyline, three floats per point, in render units
   * @param pointCount how many points of {@code xyz} to use
   * @param closed whether to repeat the first pair at the end and wrap the tangents across the seam
   */
  public static void write(Mesh mesh, float[] xyz, int pointCount, boolean closed) {
    Objects.requireNonNull(mesh, "mesh");
    Objects.requireNonNull(xyz, "xyz");
    if (pointCount < 2) {
      throw new IllegalArgumentException("A ribbon needs at least 2 points, got " + pointCount);
    }
    if (xyz.length < pointCount * 3) {
      throw new IllegalArgumentException(
          "xyz holds " + xyz.length / 3 + " points, " + pointCount + " requested");
    }

    VertexBuffer pv = mesh.getBuffer(VertexBuffer.Type.Position);
    int capacity = pv.getData().capacity() / 3;
    if (vertexCount(pointCount, closed) > capacity) {
      throw new IllegalArgumentException(
          "Ribbon was allocated for "
              + capacity / VERTICES_PER_POINT
              + " points, "
              + pointCount
              + " requested");
    }
    VertexBuffer nv = mesh.getBuffer(VertexBuffer.Type.Normal);
    VertexBuffer tv = mesh.getBuffer(VertexBuffer.Type.TexCoord);
    FloatBuffer pb = (FloatBuffer) pv.getData();
    FloatBuffer nb = (FloatBuffer) nv.getData();
    FloatBuffer tb = (FloatBuffer) tv.getData();
    pb.clear();
    nb.clear();
    tb.clear();

    float total = arcLength(xyz, pointCount, closed);
    float[] tangent = new float[3];
    float running = 0f;

    for (int i = 0; i < pointCount; i++) {
      if (i > 0) {
        running += distance(xyz, i - 1, i);
      }
      tangentAt(xyz, pointCount, i, closed, tangent);
      putPair(pb, nb, tb, xyz, i, tangent, total <= 0f ? 0f : running / total);
    }
    if (closed) {
      // The seam pair repeats point 0 exactly — same position, same tangent — so the ribbon shuts
      // without a visible joint. Its arc length is 1, not 0: the abscissa has to keep rising or the
      // dashes of §11.4 would restart backwards on the last segment.
      tangentAt(xyz, pointCount, 0, true, tangent);
      putPair(pb, nb, tb, xyz, 0, tangent, 1f);
    }

    pb.flip();
    nb.flip();
    tb.flip();

    mesh.updateCounts();
    mesh.updateBound();
    pv.setUpdateNeeded();
    nv.setUpdateNeeded();
    tv.setUpdateNeeded();
  }

  /**
   * Fills a ribbon's colour buffer to capacity from one colour per polyline point.
   *
   * @param mesh a mesh allocated with a colour buffer
   * @param rgba four floats per point
   * @param pointCount how many points of {@code rgba} to use; the remaining capacity is padded with
   *     the last of them, so a later, longer prefix never draws a black vertex
   */
  public static void writeColors(Mesh mesh, float[] rgba, int pointCount) {
    Objects.requireNonNull(mesh, "mesh");
    Objects.requireNonNull(rgba, "rgba");
    VertexBuffer cv = mesh.getBuffer(VertexBuffer.Type.Color);
    if (cv == null) {
      throw new IllegalStateException("Mesh was allocated without a colour buffer");
    }
    FloatBuffer cb = (FloatBuffer) cv.getData();
    cb.clear();
    for (int i = 0; i < pointCount && cb.remaining() >= 2 * 4; i++) {
      for (int side = 0; side < VERTICES_PER_POINT; side++) {
        cb.put(rgba[i * 4]).put(rgba[i * 4 + 1]).put(rgba[i * 4 + 2]).put(rgba[i * 4 + 3]);
      }
    }
    int last = Math.max(0, pointCount - 1);
    while (cb.remaining() >= 4) {
      cb.put(rgba[last * 4])
          .put(rgba[last * 4 + 1])
          .put(rgba[last * 4 + 2])
          .put(rgba[last * 4 + 3]);
    }
    cb.flip();
    cv.setUpdateNeeded();
  }

  private static void putPair(
      FloatBuffer pb, FloatBuffer nb, FloatBuffer tb, float[] xyz, int i, float[] t, float arc) {
    float x = xyz[i * 3];
    float y = xyz[i * 3 + 1];
    float z = xyz[i * 3 + 2];
    pb.put(x).put(y).put(z);
    nb.put(t[0]).put(t[1]).put(t[2]);
    tb.put(1f).put(arc);
    pb.put(x).put(y).put(z);
    nb.put(t[0]).put(t[1]).put(t[2]);
    tb.put(-1f).put(arc);
  }

  /**
   * Unit tangent at point {@code i}, written into {@code out}.
   *
   * <p>The chord of the two neighbours is what removes the joins (§7.5). The fallbacks below are
   * not defensive padding: a mission trajectory really does write a tip that lands on the last
   * sampled point once the head reaches the end of the polyline, and a chord of length zero
   * normalises to NaN — which is a ribbon that vanishes, or a triangle sent to infinity.
   */
  private static void tangentAt(float[] xyz, int count, int i, boolean closed, float[] out) {
    int prev = closed ? (i - 1 + count) % count : i - 1;
    int next = closed ? (i + 1) % count : i + 1;

    if (prev >= 0 && next < count && chord(xyz, prev, next, out)) {
      return;
    }
    // Forward segment, then backward: an endpoint has only one of the two, and a duplicated point
    // kills whichever chord it sits on.
    if (next < count && chord(xyz, i, next, out)) {
      return;
    }
    if (prev >= 0 && chord(xyz, prev, i, out)) {
      return;
    }
    // A whole run of coincident points. Reach further out, but keep the polyline's own direction:
    // a tangent read backwards flips the two edges of the pair, and the quad between it and its
    // neighbour crosses over into a bow tie.
    for (int j = i + 1; j < count; j++) {
      if (chord(xyz, i, j, out)) {
        return;
      }
    }
    for (int j = i - 1; j >= 0; j--) {
      if (chord(xyz, j, i, out)) {
        return;
      }
    }
    // Every point of the polyline is the same point. Any direction is as good as any other; what
    // matters is that it is a direction at all.
    out[0] = 1f;
    out[1] = 0f;
    out[2] = 0f;
  }

  /** Normalised {@code p[b] − p[a]} into {@code out}; false when the two points coincide. */
  private static boolean chord(float[] xyz, int a, int b, float[] out) {
    float dx = xyz[b * 3] - xyz[a * 3];
    float dy = xyz[b * 3 + 1] - xyz[a * 3 + 1];
    float dz = xyz[b * 3 + 2] - xyz[a * 3 + 2];
    double len = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
    if (len < DEGENERATE) {
      return false;
    }
    out[0] = (float) (dx / len);
    out[1] = (float) (dy / len);
    out[2] = (float) (dz / len);
    return true;
  }

  private static float arcLength(float[] xyz, int count, boolean closed) {
    float total = 0f;
    for (int i = 1; i < count; i++) {
      total += distance(xyz, i - 1, i);
    }
    if (closed) {
      total += distance(xyz, count - 1, 0);
    }
    return total;
  }

  private static float distance(float[] xyz, int a, int b) {
    float dx = xyz[b * 3] - xyz[a * 3];
    float dy = xyz[b * 3 + 1] - xyz[a * 3 + 1];
    float dz = xyz[b * 3 + 2] - xyz[a * 3 + 2];
    return (float) Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
  }
}
