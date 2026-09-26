package com.smousseur.orbitlab.engine.scene.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.mesh.IndexBuffer;
import com.smousseur.orbitlab.simulation.OrekitService;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * The generated Earth globe: a geodetic latitude/longitude grid on WGS84, in the axes and texture
 * convention of the {@code earth.gltf} asset it replaces.
 */
class EllipsoidGlobeTest {

  private static final EllipsoidGlobe EARTH = EllipsoidGlobe.earth();
  private static final double A = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static final double F = Constants.WGS84_EARTH_FLATTENING;

  /**
   * One manager for the class: it caches the asset and hands out clones, so the 8192 × 4096 map —
   * over 100 MB of direct memory per load — is read once instead of once per test.
   */
  private static final AssetManager ASSETS = new DesktopAssetManager(true);

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void surfacePointsLieOnTheEllipsoid() {
    double b = 1.0 - F;
    for (double lat = -90.0; lat <= 90.0; lat += 7.5) {
      for (double az = -180.0; az < 180.0; az += 13.0) {
        Vector3D p = EARTH.surface(lat, az);
        double implicit =
            p.getX() * p.getX() + p.getY() * p.getY() + (p.getZ() / b) * (p.getZ() / b);
        assertEquals(1.0, implicit, 1e-14, () -> "at " + p);
      }
    }
  }

  /**
   * The asset's measured convention, read on its raw vertices: u = 0 on −X, u = 0.25 on +Y, u = 0.5
   * on +X, u = 0.75 on −Y, v = 0 on +Z. With L = 360u − 180 that is y = −sin L; the natural (cos L,
   * sin L) would mirror the map while keeping the pole and the prime meridian.
   */
  @Test
  void azimuthRunsTheWayTheAssetDoes() {
    assertDirection(new Vector3D(-1, 0, 0), EARTH.surface(0.0, -180.0));
    assertDirection(new Vector3D(0, 1, 0), EARTH.surface(0.0, -90.0));
    assertDirection(new Vector3D(1, 0, 0), EARTH.surface(0.0, 0.0));
    assertDirection(new Vector3D(0, -1, 0), EARTH.surface(0.0, 90.0));
    assertEquals(1.0 - F, EARTH.surface(90.0, 0.0).getZ(), 1e-15);
  }

  @Test
  void theNormalIsTheGeodeticUp() {
    Vector3D n = EARTH.normal(45.0, 30.0);
    assertEquals(1.0, n.getNorm(), 1e-15);
    assertEquals(Math.sin(Math.toRadians(45.0)), n.getZ(), 1e-15);
  }

  /**
   * Orekit's ellipsoid as the reference. The azimuth is the texture's, not Orekit's longitude: the
   * model runs y = −sin L, so L = −longitude.
   */
  @Test
  void geodeticConversionMatchesOrekit() {
    Frame frame = FramesFactory.getGCRF();
    OneAxisEllipsoid reference = new OneAxisEllipsoid(1.0, F, frame);
    double[] latitudes = {-89.9, -60.0, -28.5, 0.0, 5.23, 45.0, 80.0, 89.99};
    double[] azimuths = {-180.0, -52.77, 0.0, 117.1, 179.5};
    double[] heights = {-0.001, 0.0, 0.0005, 0.07};
    for (double lat : latitudes) {
      for (double az : azimuths) {
        for (double h : heights) {
          Vector3D p = EARTH.surface(lat, az).add(h, EARTH.normal(lat, az));
          GeodeticPoint expected = reference.transform(p, frame, AbsoluteDate.J2000_EPOCH);
          EllipsoidGlobe.Geodetic actual = EARTH.geodetic(p);
          String where = lat + "/" + az + "/" + h;
          assertEquals(Math.toDegrees(expected.getLatitude()), actual.latitudeDeg(), 1e-9, where);
          assertEquals(
              0.0,
              wrapDeg(actual.azimuthDeg() + Math.toDegrees(expected.getLongitude())),
              1e-9,
              where);
          assertEquals(expected.getAltitude(), actual.height(), 1.0e-3 / A, where);
        }
      }
    }
  }

  @Test
  void geodeticRecoversAPointLiftedAlongItsNormal() {
    Vector3D p = EARTH.surface(28.5, -80.6).add(5_000.0 / A, EARTH.normal(28.5, -80.6));
    EllipsoidGlobe.Geodetic g = EARTH.geodetic(p);
    assertEquals(28.5, g.latitudeDeg(), 1e-10);
    assertEquals(-80.6, g.azimuthDeg(), 1e-10);
    assertEquals(5_000.0, g.height() * A, 1e-6);
  }

  @Test
  void theEarthMeshHasTheExpectedGrid() {
    Mesh mesh = EARTH.toMesh();
    assertEquals(2 * 360 + 179 * 361, mesh.getVertexCount());
    assertEquals(2 * 360 * 179, mesh.getTriangleCount());
  }

  @Test
  void everyVertexLiesOnTheEllipsoidWithItsGeodeticNormal() {
    Mesh mesh = EARTH.toMesh();
    FloatBuffer positions = mesh.getFloatBuffer(VertexBuffer.Type.Position);
    FloatBuffer normals = mesh.getFloatBuffer(VertexBuffer.Type.Normal);
    FloatBuffer uvs = mesh.getFloatBuffer(VertexBuffer.Type.TexCoord);
    double b = 1.0 - F;
    for (int i = 0; i < mesh.getVertexCount(); i++) {
      double x = positions.get(3 * i);
      double y = positions.get(3 * i + 1);
      double z = positions.get(3 * i + 2);
      assertEquals(1.0, x * x + y * y + (z / b) * (z / b), 1e-6, "vertex " + i);
      Vector3D n = new Vector3D(normals.get(3 * i), normals.get(3 * i + 1), normals.get(3 * i + 2));
      assertEquals(1.0, n.getNorm(), 1e-6, "normal " + i);
      double lat = 90.0 - 180.0 * uvs.get(2 * i + 1);
      double az = 360.0 * uvs.get(2 * i) - 180.0;
      Vector3D expected =
          Math.abs(lat) > 89.999 ? new Vector3D(0, 0, Math.signum(lat)) : EARTH.normal(lat, az);
      assertEquals(0.0, Vector3D.angle(expected, n), 1e-6, "normal " + i);
    }
  }

  @Test
  void everyTriangleFacesOutward() {
    Mesh mesh = EARTH.toMesh();
    FloatBuffer positions = mesh.getFloatBuffer(VertexBuffer.Type.Position);
    IndexBuffer indices = mesh.getIndexBuffer();
    for (int t = 0; t < indices.size(); t += 3) {
      Vector3D a = vertex(positions, indices.get(t));
      Vector3D b = vertex(positions, indices.get(t + 1));
      Vector3D c = vertex(positions, indices.get(t + 2));
      Vector3D n = b.subtract(a).crossProduct(c.subtract(a));
      assertTrue(n.dotProduct(a.add(b).add(c)) > 0.0, "triangle " + t / 3 + " faces inward");
    }
  }

  /**
   * The orientation lock at vertex level: for texture columns both grids share (u = k/8) on the
   * equator (v = 0.5), the generated vertex points where the asset's does.
   */
  @Test
  void theMeshPutsEveryTextureColumnWhereTheAssetDoes() {
    Mesh asset = assetGlobe();
    Mesh generated = EARTH.toMesh();
    for (int k = 0; k <= 8; k++) {
      float u = k / 8f;
      Vector3D expected = vertexAt(asset, u, 0.5f);
      Vector3D actual = vertexAt(generated, u, 0.5f);
      assertEquals(0.0, Math.toDegrees(Vector3D.angle(expected, actual)), 0.01, "u = " + u);
    }
  }

  @Test
  void theMeshMeasuresTheFrameTheAssetCarries() {
    MeshFrame asset = MeshFrameProbe.probe(loadEarthAsset()).get(0).frame();
    MeshFrame generated = MeshFrameProbe.probe(EARTH.toMesh()).orElseThrow();
    assertTrue(angleDeg(asset.pole(), generated.pole()) < 0.05, "pole " + generated.pole());
    assertTrue(
        angleDeg(asset.primeMeridian(), generated.primeMeridian()) < 0.05,
        "prime meridian " + generated.primeMeridian());
    assertEquals(asset.azimuthDegreesPerU(), generated.azimuthDegreesPerU(), 1.0, "chirality");
    assertEquals(0.122, generated.equirectangularResidualDeg(), 0.01, "residual");
    assertInstanceOf(MeshConformance.Conforming.class, MeshConformance.of(generated));
  }

  @Test
  void theFacetDepthVanishesOnTheVertices() {
    for (int ring = 1; ring < 180; ring += 7) {
      for (int col = 0; col < 360; col += 11) {
        double depth = EARTH.facetDepth(90.0 - ring, -180.0 + col) * A;
        assertEquals(0.0, depth, 1e-6, "vertex " + ring + "/" + col);
      }
    }
  }

  /** One column is enough: every column is the same cell turned about the pole. */
  @Test
  void theDrawnSurfaceNeverRisesAboveTheEllipsoidAndSinksAt484MetresAtMost() {
    double deepest = 0.0;
    for (double lat = -89.99; lat < 90.0; lat += 0.01) {
      for (int k = 0; k < 20; k++) {
        double depth = EARTH.facetDepth(lat, -180.0 + 0.05 * k) * A;
        assertTrue(depth > -1e-6, "surface above the ellipsoid at " + lat);
        deepest = Math.max(deepest, depth);
      }
    }
    assertEquals(484.06, deepest, 0.5);
  }

  @Test
  void theFacetDepthMatchesTheDesignMeasurement() {
    assertEquals(341.4, EARTH.facetDepth(5.23, -52.77) * A, 0.5);
    assertEquals(422.0, EARTH.facetDepth(28.5, -80.6) * A, 0.5);
    assertEquals(241.5, EARTH.facetDepth(3.8, 117.1) * A, 0.5);
  }

  @Test
  void theFacetDepthWrapsTheAzimuth() {
    assertEquals(EARTH.facetDepth(12.3, -170.2), EARTH.facetDepth(12.3, 189.8), 1e-15);
    assertEquals(EARTH.facetDepth(-40.0, 179.9), EARTH.facetDepth(-40.0, -180.1), 1e-15);
  }

  /** One grid for the mesh and the query: the point it answers lies on a triangle of the mesh. */
  @Test
  void theDepthPointLiesOnATriangleOfTheMesh() {
    Mesh mesh = EARTH.toMesh();
    FloatBuffer positions = mesh.getFloatBuffer(VertexBuffer.Type.Position);
    IndexBuffer indices = mesh.getIndexBuffer();
    Random random = new Random(42);
    List<double[]> samples = new ArrayList<>();
    samples.add(new double[] {89.7, 33.3});
    samples.add(new double[] {-89.7, -120.4});
    samples.add(new double[] {0.5, -179.5});
    for (int n = 0; n < 40; n++) {
      samples.add(
          new double[] {-89.5 + 179.0 * random.nextDouble(), -180.0 + 360.0 * random.nextDouble()});
    }
    for (double[] s : samples) {
      Vector3D drawn =
          EARTH
              .surface(s[0], s[1])
              .subtract(EARTH.facetDepth(s[0], s[1]), EARTH.normal(s[0], s[1]));
      assertTrue(
          liesOnATriangle(drawn, positions, indices), "off the mesh at " + s[0] + "/" + s[1]);
    }
  }

  /**
   * Plane distance under 5e-7 model units (3 m) — float vertex storage leaves ~1e-7, a wrong cell
   * is tens of metres out — and barycentric coordinates inside the triangle, edges tolerated.
   */
  static boolean liesOnATriangle(Vector3D p, FloatBuffer positions, IndexBuffer indices) {
    for (int t = 0; t < indices.size(); t += 3) {
      Vector3D a = vertex(positions, indices.get(t));
      if (a.distance(p) > 0.05) {
        continue;
      }
      Vector3D b = vertex(positions, indices.get(t + 1));
      Vector3D c = vertex(positions, indices.get(t + 2));
      Vector3D ab = b.subtract(a);
      Vector3D ac = c.subtract(a);
      Vector3D normal = ab.crossProduct(ac);
      double twiceArea = normal.getNorm();
      if (twiceArea == 0.0) {
        continue;
      }
      Vector3D ap = p.subtract(a);
      if (Math.abs(ap.dotProduct(normal) / twiceArea) > 5e-7) {
        continue;
      }
      double wb = ap.crossProduct(ac).dotProduct(normal) / (twiceArea * twiceArea);
      double wc = ab.crossProduct(ap).dotProduct(normal) / (twiceArea * twiceArea);
      if (wb >= -1e-4 && wc >= -1e-4 && wb + wc <= 1.0 + 1e-4) {
        return true;
      }
    }
    return false;
  }

  static Spatial loadEarthAsset() {
    return ASSETS.loadModel("models/planets/earth/earth.gltf");
  }

  static Mesh assetGlobe() {
    List<Geometry> geometries = new ArrayList<>();
    loadEarthAsset()
        .depthFirstTraversal(
            new SceneGraphVisitorAdapter() {
              @Override
              public void visit(Geometry geometry) {
                geometries.add(geometry);
              }
            });
    assertEquals(1, geometries.size(), "earth.gltf is expected to hold a single geometry");
    return geometries.get(0).getMesh();
  }

  static Vector3D vertex(FloatBuffer positions, int index) {
    return new Vector3D(
        positions.get(3 * index), positions.get(3 * index + 1), positions.get(3 * index + 2));
  }

  static Vector3D vertexAt(Mesh mesh, float u, float v) {
    FloatBuffer positions = mesh.getFloatBuffer(VertexBuffer.Type.Position);
    FloatBuffer uvs = mesh.getFloatBuffer(VertexBuffer.Type.TexCoord);
    for (int i = 0; i < mesh.getVertexCount(); i++) {
      if (Math.abs(uvs.get(2 * i) - u) < 1e-4f && Math.abs(uvs.get(2 * i + 1) - v) < 1e-4f) {
        return vertex(positions, i);
      }
    }
    throw new AssertionError("no vertex at uv (" + u + ", " + v + ")");
  }

  static double angleDeg(Vector3f a, Vector3f b) {
    return Math.toDegrees(Vector3D.angle(new Vector3D(a.x, a.y, a.z), new Vector3D(b.x, b.y, b.z)));
  }

  static void assertDirection(Vector3D expected, Vector3D actual) {
    assertEquals(0.0, Vector3D.angle(expected, actual), 1e-12, () -> "got " + actual);
  }

  static double wrapDeg(double degrees) {
    return degrees - 360.0 * Math.floor((degrees + 180.0) / 360.0);
  }
}
