package com.smousseur.orbitlab.engine.scene.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;

class MeshFrameProbeTest {

  @Test
  void recoversPoleAndPrimeMeridianOfACanonicalSphere() {
    Mesh sphere = uvSphere(16, 32, new Quaternion());

    MeshFrame frame = MeshFrameProbe.probe(sphere).orElseThrow();

    assertDirection(Vector3f.UNIT_Z, frame.pole(), "pole");
    assertDirection(Vector3f.UNIT_X.negate(), frame.primeMeridian(), "primeMeridian");
  }

  @Test
  void reportsZeroResidualForAnExactEquirectangularMap() {
    Mesh sphere =
        uvSphere(16, 32, new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y));

    MeshFrame frame = MeshFrameProbe.probe(sphere).orElseThrow();

    assertEquals(0.0, frame.equirectangularResidualDeg(), 0.1);
  }

  @Test
  void reportsALargeResidualForAMapThatIsNotEquirectangular() {
    Mesh equalArea =
        uvSphere(
            16,
            32,
            new Quaternion(),
            colatitudeFraction -> (1.0 - Math.cos(colatitudeFraction * Math.PI)) / 2.0);

    MeshFrame frame = MeshFrameProbe.probe(equalArea).orElseThrow();

    assertTrue(
        frame.equirectangularResidualDeg() > 5.0f,
        "an equal-area unwrap must not pass for a lat/long map, got "
            + frame.equirectangularResidualDeg());
  }

  @Test
  void reportsTheChiralityOfTheReferenceConvention() {
    Mesh sphere = uvSphere(16, 32, new Quaternion());

    MeshFrame frame = MeshFrameProbe.probe(sphere).orElseThrow();

    assertEquals(-360.0, frame.azimuthDegreesPerU(), 1.0);
  }

  @Test
  void reportsTheOppositeChiralityForAMirroredMap() {
    Mesh mirrored =
        uvSphere(16, 32, new Quaternion(), colatitudeFraction -> colatitudeFraction, true);

    MeshFrame frame = MeshFrameProbe.probe(mirrored).orElseThrow();

    assertEquals(360.0, frame.azimuthDegreesPerU(), 1.0);
  }

  /**
   * Builds a unit UV sphere in the reference convention — pole on {@code +Z}, {@code u = 0}
   * pointing at {@code −X}, and {@code u} increasing retrograde about the pole at −360°/u, which is
   * the chirality all eleven planetary assets share — then turns every vertex by {@code
   * orientation}.
   */
  private static Mesh uvSphere(int rings, int segments, Quaternion orientation) {
    return uvSphere(rings, segments, orientation, colatitudeFraction -> colatitudeFraction, false);
  }

  private static Mesh uvSphere(
      int rings, int segments, Quaternion orientation, DoubleUnaryOperator vForColatitudeFraction) {
    return uvSphere(rings, segments, orientation, vForColatitudeFraction, false);
  }

  /**
   * Same, with {@code v} driven by an arbitrary function of the colatitude fraction, so a map that
   * is deliberately <em>not</em> equirectangular can be built while the geometry stays a sphere.
   */
  private static Mesh uvSphere(
      int rings,
      int segments,
      Quaternion orientation,
      DoubleUnaryOperator vForColatitudeFraction,
      boolean mirrored) {
    int count = (rings + 1) * (segments + 1);
    float[] positions = new float[count * 3];
    float[] texCoords = new float[count * 2];
    Vector3f meridianAxis = new Vector3f(-1f, 0f, 0f);
    Vector3f quadratureAxis = new Vector3f(0f, -1f, 0f);

    int vertex = 0;
    for (int ring = 0; ring <= rings; ring++) {
      float colatitudeFraction = (float) ring / rings;
      float v = (float) vForColatitudeFraction.applyAsDouble(colatitudeFraction);
      float colatitude = colatitudeFraction * FastMath.PI;
      for (int segment = 0; segment <= segments; segment++) {
        float u = (float) segment / segments;
        float azimuth = (mirrored ? u : -u) * FastMath.TWO_PI;
        Vector3f point =
            new Vector3f(0f, 0f, FastMath.cos(colatitude))
                .addLocal(meridianAxis.mult(FastMath.sin(colatitude) * FastMath.cos(azimuth)))
                .addLocal(quadratureAxis.mult(FastMath.sin(colatitude) * FastMath.sin(azimuth)));
        orientation.multLocal(point);

        positions[vertex * 3] = point.x;
        positions[vertex * 3 + 1] = point.y;
        positions[vertex * 3 + 2] = point.z;
        texCoords[vertex * 2] = u;
        texCoords[vertex * 2 + 1] = v;
        vertex++;
      }
    }

    Mesh mesh = new Mesh();
    mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
    mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(texCoords));
    return mesh;
  }

  private static void assertDirection(Vector3f expected, Vector3f actual, String what) {
    assertEquals(1.0, expected.dot(actual.normalize()), 1e-4, what + " = " + actual);
  }
}
