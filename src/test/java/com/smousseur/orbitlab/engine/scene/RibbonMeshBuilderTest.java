package com.smousseur.orbitlab.engine.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

/**
 * The ribbon mesh layout (spec {@code docs/graphics-effects/ribbon-lines.md} §10).
 *
 * <p>The shader is not testable here and is not tested here: what is testable is the mesh the
 * shader reads, and that is where the errors that cost the most live — a tangent taken from the
 * wrong neighbours, an arc length that is not normalised, a NaN on a zero-length segment.
 * Everything below asserts on buffer contents, never on pixels.
 */
class RibbonMeshBuilderTest {

  private static final float EPS = 1e-5f;

  /** A gentle open arc: four distinct points, no repeated position, no sharp turn. */
  private static float[] arc() {
    return new float[] {0, 0, 0, 1, 1, 0, 2, 0, 0, 3, 1, 0};
  }

  private static Mesh ribbon(float[] xyz, boolean closed) {
    int points = xyz.length / 3;
    Mesh mesh = RibbonMeshBuilder.allocate(points, closed, false);
    RibbonMeshBuilder.write(mesh, xyz, points, closed);
    return mesh;
  }

  private static float[] read(Mesh mesh, VertexBuffer.Type type) {
    FloatBuffer fb = (FloatBuffer) mesh.getBuffer(type).getData();
    float[] out = new float[fb.limit()];
    fb.position(0);
    fb.get(out);
    fb.position(0);
    return out;
  }

  @Test
  void anOpenPolylineOfNPointsGivesTwoNVerticesAndTwoNMinusOneTriangles() {
    Mesh mesh = ribbon(arc(), false);

    assertEquals(8, mesh.getVertexCount());
    assertEquals(6, mesh.getTriangleCount());
    assertEquals(Mesh.Mode.TriangleStrip, mesh.getMode());
  }

  @Test
  void thePairOfAPointCarriesTheSamePositionAndOppositeSides() {
    float[] xyz = arc();
    Mesh mesh = ribbon(xyz, false);
    float[] pos = read(mesh, VertexBuffer.Type.Position);
    float[] uv = read(mesh, VertexBuffer.Type.TexCoord);

    for (int i = 0; i < 4; i++) {
      // Both vertices of a pair sit on the polyline point itself: the ribbon is centred on the
      // trajectory, and the offset only exists once the vertex shader has run (§7.1).
      for (int c = 0; c < 3; c++) {
        assertEquals(xyz[i * 3 + c], pos[(2 * i) * 3 + c], EPS);
        assertEquals(xyz[i * 3 + c], pos[(2 * i + 1) * 3 + c], EPS);
      }
      assertEquals(1f, uv[(2 * i) * 2], EPS);
      assertEquals(-1f, uv[(2 * i + 1) * 2], EPS);
    }
  }

  @Test
  void anInteriorTangentIsCollinearWithTheChordOfItsNeighbours() {
    float[] xyz = arc();
    Mesh mesh = ribbon(xyz, false);
    float[] n = read(mesh, VertexBuffer.Type.Normal);

    // Point 1: p[2] - p[0] = (2, 0, 0) -> unit x.
    assertUnitAlong(n, 1, 1, 0, 0);
    // Point 2: p[3] - p[1] = (2, 0, 0) -> unit x.
    assertUnitAlong(n, 2, 1, 0, 0);
  }

  @Test
  void anEndTangentIsTheOnlySegmentItHas() {
    float[] xyz = arc();
    Mesh mesh = ribbon(xyz, false);
    float[] n = read(mesh, VertexBuffer.Type.Normal);

    // p[1] - p[0] = (1, 1, 0), and p[3] - p[2] = (1, 1, 0).
    float s = (float) (1 / Math.sqrt(2));
    assertUnitAlong(n, 0, s, s, 0);
    assertUnitAlong(n, 3, s, s, 0);
  }

  @Test
  void aClosedPolylineRepeatsItsFirstPairToShutTheBand() {
    float[] xyz = {0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0};
    Mesh mesh = ribbon(xyz, true);

    assertEquals(10, mesh.getVertexCount());
    assertEquals(8, mesh.getTriangleCount());

    float[] pos = read(mesh, VertexBuffer.Type.Position);
    float[] n = read(mesh, VertexBuffer.Type.Normal);
    for (int c = 0; c < 3; c++) {
      assertEquals(pos[c], pos[8 * 3 + c], EPS);
      assertEquals(pos[3 + c], pos[9 * 3 + c], EPS);
      // The closing pair has to carry the first pair's tangent too, or the seam pinches.
      assertEquals(n[c], n[8 * 3 + c], EPS);
      assertEquals(n[3 + c], n[9 * 3 + c], EPS);
    }
  }

  @Test
  void aClosedPolylineTakesItsSeamTangentFromBothSidesOfTheSeam() {
    // A square: at point 0 the neighbours are p[3] = (0,1,0) and p[1] = (1,0,0), so the wrapped
    // chord is (1,-1,0). Read the other way round — from the single forward segment — it would be
    // (1,0,0), and the seam would kink.
    float[] xyz = {0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0};
    Mesh mesh = ribbon(xyz, true);
    float[] n = read(mesh, VertexBuffer.Type.Normal);

    float s = (float) (1 / Math.sqrt(2));
    assertUnitAlong(n, 0, s, -s, 0);
  }

  @Test
  void theArcLengthRisesFromZeroToOne() {
    Mesh mesh = ribbon(arc(), false);
    float[] uv = read(mesh, VertexBuffer.Type.TexCoord);

    assertEquals(0f, uv[1], EPS);
    assertEquals(0f, uv[3], EPS);
    assertEquals(1f, uv[uv.length - 3], EPS);
    assertEquals(1f, uv[uv.length - 1], EPS);

    for (int v = 1; v < mesh.getVertexCount(); v++) {
      assertTrue(
          uv[v * 2 + 1] >= uv[(v - 1) * 2 + 1] - EPS,
          "arc length must not decrease at vertex " + v);
    }
  }

  @Test
  void theAbscissaIsAnArcLengthAndNotAVertexIndex() {
    // Segments of 1, 3 and 6: an index would put point 1 at 1/3, the arc length puts it at 0,1.
    float[] xyz = {0, 0, 0, 1, 0, 0, 4, 0, 0, 10, 0, 0};
    Mesh mesh = ribbon(xyz, false);
    float[] uv = read(mesh, VertexBuffer.Type.TexCoord);

    assertEquals(0.1f, uv[2 * 2 + 1], EPS);
    assertEquals(0.4f, uv[4 * 2 + 1], EPS);
  }

  @Test
  void twoCoincidentPointsProduceNeitherNaNNorAnUnnormalisedTangent() {
    // The real case: the mission trajectory writes an interpolated tip that lands exactly on the
    // last sampled point as soon as the head reaches the end of the polyline (§10).
    float[] xyz = {0, 0, 0, 1, 0, 0, 2, 0, 0, 2, 0, 0};
    Mesh mesh = ribbon(xyz, false);

    for (float f : read(mesh, VertexBuffer.Type.Position)) {
      assertTrue(Float.isFinite(f), "position must be finite");
    }
    for (float f : read(mesh, VertexBuffer.Type.TexCoord)) {
      assertTrue(Float.isFinite(f), "texcoord must be finite");
    }
    float[] n = read(mesh, VertexBuffer.Type.Normal);
    for (int v = 0; v < mesh.getVertexCount(); v++) {
      double len =
          Math.sqrt(
              n[v * 3] * n[v * 3] + n[v * 3 + 1] * n[v * 3 + 1] + n[v * 3 + 2] * n[v * 3 + 2]);
      assertEquals(1.0, len, 1e-4, "tangent must stay unit-length at vertex " + v);
    }
  }

  @Test
  void aPolylineWithNoExtentAtAllStillProducesAFiniteMesh() {
    float[] xyz = {5, 5, 5, 5, 5, 5, 5, 5, 5};
    Mesh mesh = ribbon(xyz, false);

    float[] n = read(mesh, VertexBuffer.Type.Normal);
    for (float f : n) {
      assertTrue(Float.isFinite(f), "tangent must be finite");
    }
    for (float f : read(mesh, VertexBuffer.Type.TexCoord)) {
      assertTrue(Float.isFinite(f), "texcoord must be finite");
    }
  }

  @Test
  void aPrefixShorterThanTheAllocationIsTheOnlyThingDrawn() {
    // What the mission renderer does every frame: the buffers are sized once for the budget and
    // only the flown prefix is written.
    Mesh mesh = RibbonMeshBuilder.allocate(64, false, true);
    float[] xyz = arc();
    RibbonMeshBuilder.write(mesh, xyz, 3, false);

    assertEquals(6, mesh.getVertexCount());
    assertEquals(4, mesh.getTriangleCount());

    // And the same mesh must accept a longer prefix afterwards, without reallocating.
    RibbonMeshBuilder.write(mesh, xyz, 4, false);
    assertEquals(8, mesh.getVertexCount());
  }

  @Test
  void theColourBufferHoldsTwoSlotsPerPointWhenAsked() {
    Mesh mesh = RibbonMeshBuilder.allocate(4, false, true);

    assertEquals(
        8 * 4, mesh.getBuffer(VertexBuffer.Type.Color).getData().capacity(), "4 RGBA per vertex");
  }

  private static void assertUnitAlong(float[] normals, int point, float x, float y, float z) {
    for (int v : new int[] {2 * point, 2 * point + 1}) {
      assertEquals(x, normals[v * 3], EPS, "tangent x at point " + point);
      assertEquals(y, normals[v * 3 + 1], EPS, "tangent y at point " + point);
      assertEquals(z, normals[v * 3 + 2], EPS, "tangent z at point " + point);
    }
  }
}
