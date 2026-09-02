package com.smousseur.orbitlab.engine.scene.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrame;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

/**
 * The graticule is drawn from the mesh's own UV parameterisation, so it rides with the texture: a
 * model turned away from the reference carries its grid with it, which is precisely what makes the
 * offset visible instead of invisible (L2 of {@code docs/orientation-planetes/01-decoupage.md}).
 */
class GraticuleMeshTest {

  private static final float RADIUS = 10f;

  @Test
  void everyVertexSitsOnTheSphere() {
    FloatBuffer positions = positionsOf(GraticuleMesh.build(reference(), RADIUS));

    for (int i = 0; i < positions.limit() / 3; i++) {
      Vector3f point =
          new Vector3f(positions.get(i * 3), positions.get(i * 3 + 1), positions.get(i * 3 + 2));
      assertEquals(RADIUS, point.length(), 1e-3, "vertex " + i);
    }
  }

  /** It has to be a grid of the frame it was given, not of the axes. */
  @Test
  void theGridIsBuiltOnTheMeasuredFrameNotOnTheAxes() {
    MeshFrame turned = new MeshFrame(new Vector3f(0f, 1f, 0f), new Vector3f(0f, 0f, 1f), 0f, -360f);
    FloatBuffer positions = positionsOf(GraticuleMesh.build(turned, RADIUS));

    assertTrue(
        contains(positions, new Vector3f(0f, RADIUS, 0f)),
        "pole vertex must sit on the frame pole");
    assertTrue(
        contains(positions, new Vector3f(0f, 0f, RADIUS)),
        "column 0 must sit on the frame prime meridian");
  }

  /**
   * A mirrored map runs the other way round the pole. The grid must run with it — a graticule that
   * assumed a chirality would draw a correct-looking grid over a wrong texture, which is the one
   * failure this instrument cannot afford.
   */
  @Test
  void theGridFollowsTheMeshChirality() {
    Vector3f quarterTurn = TexturePainting.directionOf(reference(), 0.25, 0.5).multLocal(RADIUS);
    Vector3f mirroredQuarterTurn =
        TexturePainting.directionOf(mirrored(), 0.25, 0.5).multLocal(RADIUS);

    assertTrue(contains(positionsOf(GraticuleMesh.build(reference(), RADIUS)), quarterTurn));
    assertTrue(contains(positionsOf(GraticuleMesh.build(mirrored(), RADIUS)), mirroredQuarterTurn));
    assertEquals(-1.0, quarterTurn.normalize().dot(mirroredQuarterTurn.normalize()), 1e-3);
  }

  /** Lines, and one colour per vertex, so the equator and column 0 can be told from the rest. */
  @Test
  void isALineMeshWithAColourPerVertex() {
    Mesh mesh = GraticuleMesh.build(reference(), RADIUS);

    assertEquals(Mesh.Mode.Lines, mesh.getMode());
    FloatBuffer colours = mesh.getFloatBuffer(VertexBuffer.Type.Color);
    assertNotNull(colours);
    assertEquals(positionsOf(mesh).limit() / 3 * 4, colours.limit());
  }

  private static MeshFrame reference() {
    return new MeshFrame(new Vector3f(0f, 0f, 1f), new Vector3f(-1f, 0f, 0f), 0f, -360f);
  }

  private static MeshFrame mirrored() {
    return new MeshFrame(new Vector3f(0f, 0f, 1f), new Vector3f(-1f, 0f, 0f), 0f, 360f);
  }

  private static FloatBuffer positionsOf(Mesh mesh) {
    return mesh.getFloatBuffer(VertexBuffer.Type.Position);
  }

  private static boolean contains(FloatBuffer positions, Vector3f expected) {
    for (int i = 0; i < positions.limit() / 3; i++) {
      Vector3f point =
          new Vector3f(positions.get(i * 3), positions.get(i * 3 + 1), positions.get(i * 3 + 2));
      if (point.distance(expected) < 1e-3f) {
        return true;
      }
    }
    return false;
  }
}
