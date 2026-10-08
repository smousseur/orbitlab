package com.smousseur.orbitlab.engine.scene.hover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.RibbonMeshBuilder;
import java.lang.management.ManagementFactory;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The projection of orbit ribbons onto the screen, on a headless camera and meshes written by
 * {@link RibbonMeshBuilder} exactly as the application writes them.
 */
class OrbitScreenProjectorTest {

  private static final float PIXEL_TOLERANCE = 1e-2f;

  /** A ring in the plane z = 0, in front of a camera that sits on +z looking at the origin. */
  private static final float[] SQUARE_RING = {10f, 0f, 0f, 0f, 10f, 0f, -10f, 0f, 0f, 0f, -10f, 0f};

  private final OrbitScreenProjector projector = new OrbitScreenProjector();
  private Camera camera;
  private Node root;

  @BeforeEach
  void setUp() {
    camera = new Camera(1280, 720);
    camera.setFrustumPerspective(45f, 1280f / 720f, 1f, 1000f);
    camera.setLocation(new Vector3f(0f, 0f, 100f));
    camera.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
    root = new Node("root");
  }

  @Test
  void projectsAPointToTheSamePixelAsTheCamera() {
    Geometry mars = attach(SolarSystemBody.MARS, null, SQUARE_RING);

    ProjectedOrbit orbit = single(projector.project(camera, Map.of(SolarSystemBody.MARS, mars), 1));

    for (int i = 0; i < SQUARE_RING.length / 3; i++) {
      Vector3f expected = camera.getScreenCoordinates(point(SQUARE_RING, i));
      assertEquals(expected.x, orbit.x(i), PIXEL_TOLERANCE, "x of point " + i);
      assertEquals(expected.y, orbit.y(i), PIXEL_TOLERANCE, "y of point " + i);
    }
  }

  @Test
  void skipsAnOrbitWhoseParentIsCulled() {
    Geometry mars = attach(SolarSystemBody.MARS, null, SQUARE_RING);
    Map<SolarSystemBody, Geometry> orbits = Map.of(SolarSystemBody.MARS, mars);

    mars.getParent().setCullHint(Spatial.CullHint.Always);
    root.updateGeometricState();
    assertTrue(projector.project(camera, orbits, 1).isEmpty());

    mars.getParent().setCullHint(Spatial.CullHint.Inherit);
    root.updateGeometricState();
    assertEquals(1, projector.project(camera, orbits, 1).size());
  }

  @Test
  void appliesTheTranslationOfAParentNode() {
    Vector3f earth = new Vector3f(5f, 3f, 0f);
    Geometry moon = attach(SolarSystemBody.MOON, earth, SQUARE_RING);

    ProjectedOrbit orbit = single(projector.project(camera, Map.of(SolarSystemBody.MOON, moon), 1));

    for (int i = 0; i < SQUARE_RING.length / 3; i++) {
      Vector3f expected = camera.getScreenCoordinates(point(SQUARE_RING, i).add(earth));
      assertEquals(expected.x, orbit.x(i), PIXEL_TOLERANCE, "x of point " + i);
      assertEquals(expected.y, orbit.y(i), PIXEL_TOLERANCE, "y of point " + i);
    }
  }

  @Test
  void invalidatesPointsAtOrBeforeTheNearPlane() {
    float[] ring = {
      10f, 0f, 0f, // in front
      0f, 0f, 150f, // behind the camera
      -10f, 0f, 0f, // in front
      0f, 10f, 99.5f, // in front of the camera, but closer than the near plane
      0f, -10f, 0f // in front
    };
    Geometry mars = attach(SolarSystemBody.MARS, null, ring);

    ProjectedOrbit orbit = single(projector.project(camera, Map.of(SolarSystemBody.MARS, mars), 1));

    assertTrue(Float.isNaN(orbit.x(1)) && Float.isNaN(orbit.y(1)), "behind the camera");
    assertTrue(Float.isNaN(orbit.x(3)) && Float.isNaN(orbit.y(3)), "inside the near plane");
    for (int i : new int[] {0, 2, 4}) {
      assertFalse(Float.isNaN(orbit.x(i)), "point " + i + " is in front");
    }
  }

  @Test
  void invalidatesPointsAtOrBeyondTheFarPlane() {
    float[] ring = {
      10f, 0f, 0f, // in front
      0f, 400f, -950f, // in the field of view, 1 050 units deep, beyond the far plane at 1 000
      -10f, 0f, 0f, // in front
      0f, -10f, 0f // in front
    };
    Geometry pluto = attach(SolarSystemBody.PLUTO, null, ring);

    ProjectedOrbit orbit =
        single(projector.project(camera, Map.of(SolarSystemBody.PLUTO, pluto), 1));

    assertTrue(Float.isNaN(orbit.x(1)) && Float.isNaN(orbit.y(1)), "beyond the far plane");
    for (int i : new int[] {0, 2, 3}) {
      assertFalse(Float.isNaN(orbit.x(i)), "point " + i + " is inside the frustum");
    }
  }

  @Test
  void keepsEveryKthPointAndTheClosingOne() {
    float[] ring = new float[10 * 3];
    for (int i = 0; i < 10; i++) {
      double a = 2 * Math.PI * i / 10;
      ring[i * 3] = (float) (10 * Math.cos(a));
      ring[i * 3 + 1] = (float) (10 * Math.sin(a));
    }
    Geometry mars = attach(SolarSystemBody.MARS, null, ring);
    Map<SolarSystemBody, Geometry> orbits = Map.of(SolarSystemBody.MARS, mars);

    ProjectedOrbit everyPoint = single(projector.project(camera, orbits, 1));
    assertEquals(11, everyPoint.pointCount(), "ten points and the seam that repeats the first");
    assertClosed(everyPoint);
    float ninthPointX = everyPoint.x(9);

    ProjectedOrbit everyThird = single(projector.project(camera, orbits, 3));
    assertEquals(5, everyThird.pointCount(), "points 0, 3, 6, 9 and the seam");
    assertEquals(ninthPointX, everyThird.x(3), PIXEL_TOLERANCE);
    assertClosed(everyThird);

    ProjectedOrbit everyFifth = single(projector.project(camera, orbits, 5));
    assertEquals(3, everyFifth.pointCount(), "points 0, 5 and the seam, which falls on the stride");
    assertClosed(everyFifth);
  }

  @Test
  void listsOrbitsInBodyOrder() {
    Map<SolarSystemBody, Geometry> orbits = new LinkedHashMap<>();
    orbits.put(SolarSystemBody.MARS, attach(SolarSystemBody.MARS, null, SQUARE_RING));
    orbits.put(SolarSystemBody.EARTH, attach(SolarSystemBody.EARTH, null, SQUARE_RING));

    List<ProjectedOrbit> projected = projector.project(camera, orbits, 1);

    assertEquals(SolarSystemBody.EARTH, projected.get(0).body());
    assertEquals(SolarSystemBody.MARS, projected.get(1).body());
  }

  /**
   * Once the outputs have grown to an orbit's size, a call allocates nothing — measured as the
   * bytes the thread allocates during the second call, on a ring whose coordinates alone weigh 32
   * KiB.
   */
  @Test
  void allocatesNothingOnceItsOutputsHaveGrown() {
    int points = 4_096;
    float[] ring = new float[points * 3];
    for (int i = 0; i < points; i++) {
      double a = 2 * Math.PI * i / points;
      ring[i * 3] = (float) (10 * Math.cos(a));
      ring[i * 3 + 1] = (float) (10 * Math.sin(a));
    }
    Map<SolarSystemBody, Geometry> orbits = new EnumMap<>(SolarSystemBody.class);
    orbits.put(SolarSystemBody.MARS, attach(SolarSystemBody.MARS, null, ring));
    com.sun.management.ThreadMXBean threads =
        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    long thread = Thread.currentThread().threadId();

    List<ProjectedOrbit> first = projector.project(camera, orbits, 1);
    ProjectedOrbit firstOrbit = first.get(0);
    threads.getThreadAllocatedBytes(thread);
    long before = threads.getThreadAllocatedBytes(thread);
    List<ProjectedOrbit> second = projector.project(camera, orbits, 1);
    long allocated = threads.getThreadAllocatedBytes(thread) - before;

    assertSame(first, second);
    assertSame(firstOrbit, second.get(0));
    assertTrue(allocated < 1_024, "the second call allocated " + allocated + " bytes");
  }

  /**
   * Hangs a ribbon under its own orbit node, as {@code SceneGraph.OrbitLayer} does, optionally
   * translated as the Moon's node is onto the Earth.
   */
  private Geometry attach(SolarSystemBody body, Vector3f nodeTranslation, float[] xyz) {
    int points = xyz.length / 3;
    Mesh mesh = RibbonMeshBuilder.allocate(points, true, false);
    RibbonMeshBuilder.write(mesh, xyz, points, true);
    Geometry geometry = new Geometry("OrbitLine-" + body.name(), mesh);
    Node orbitNode = new Node("Orbit-" + body.name());
    if (nodeTranslation != null) {
      orbitNode.setLocalTranslation(nodeTranslation);
    }
    orbitNode.attachChild(geometry);
    root.attachChild(orbitNode);
    root.updateGeometricState();
    return geometry;
  }

  private static ProjectedOrbit single(List<ProjectedOrbit> projected) {
    assertEquals(1, projected.size());
    return projected.get(0);
  }

  private static Vector3f point(float[] xyz, int i) {
    return new Vector3f(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]);
  }

  private static void assertClosed(ProjectedOrbit orbit) {
    int last = orbit.pointCount() - 1;
    assertEquals(orbit.x(0), orbit.x(last), PIXEL_TOLERANCE, "the ring closes on its first point");
    assertEquals(orbit.y(0), orbit.y(last), PIXEL_TOLERANCE, "the ring closes on its first point");
  }
}
