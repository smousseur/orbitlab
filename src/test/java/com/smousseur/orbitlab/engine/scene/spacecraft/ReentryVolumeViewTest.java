package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.ReentryFxConfig;
import com.smousseur.orbitlab.engine.ReentryVolumeConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Where the plasma volume sits on a drawn piece, which way it faces, how far its box reaches, what
 * it tells its shader of the shock and of the body, and when it shows.
 */
class ReentryVolumeViewTest {

  /** A body whose +Y, along which every piece is authored, is turned onto -X. */
  private static final Quaternion QUARTER_TURN_ABOUT_Z =
      new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Z);

  /** The default box: 3 radii ahead of the nose, 20 beyond the base, 11 to each side. */
  private static final ReentryVolumeConfig VOLUME = ReentryVolumeConfig.defaults();

  /** A nose 10 units up and a body 2 units wide: 5 radii long. */
  private static final PieceGeometry PIECE = new PieceGeometry(10f, 2f);

  /**
   * A body authored as a payload is: its nose 10 units up, its base 4 units under the origin, its
   * axis through (1.5, -0.5). 2 units wide, it is 7 radii long.
   */
  private static final PieceGeometry OFF_AXIS_PIECE = new PieceGeometry(10f, 2f, -4f, 1.5f, -0.5f);

  private Node anchor3d;
  private Node body;
  private ReentryVolumeView view;

  @BeforeAll
  static void initAssetFactory() {
    try {
      AssetFactory.init(new DesktopAssetManager(true));
    } catch (OrbitlabException alreadyInitialized) {
      // Another test class got there first: its asset manager serves just as well.
    }
  }

  @BeforeEach
  void buildPiece() {
    anchor3d = new Node("anchor3d");
    body = new Node("bucket");
    body.setLocalTranslation(1f, 2f, 3f);
    body.setLocalRotation(QUARTER_TURN_ABOUT_Z);
    anchor3d.attachChild(body);
    view =
        new ReentryVolumeView(
            anchor3d, AssetFactory.get().createReentryVolume(ReentryFxConfig.defaults()), VOLUME);
  }

  /** The body's nose, 10 units up its +Y, turned a quarter about Z onto -X, off its seat. */
  @Test
  void theAnchorIsTheDrawnNose() {
    Vector3f anchor =
        ReentryVolumeView.anchorPoint(new Vector3f(1f, 2f, 3f), QUARTER_TURN_ABOUT_Z, PIECE);

    assertVectorEquals(new Vector3f(-9f, 2f, 3f), anchor);
  }

  /**
   * A body whose axis runs through (1.5, -0.5) of its mesh, as a payload's does: its nose (1.5, 10,
   * -0.5) turns a quarter about Z onto (-10, 1.5, -0.5), off its seat.
   */
  @Test
  void theAnchorIsTheDrawnNoseOnTheMeasuredAxis() {
    Vector3f anchor =
        ReentryVolumeView.anchorPoint(
            new Vector3f(1f, 2f, 3f), QUARTER_TURN_ABOUT_Z, OFF_AXIS_PIECE);

    assertVectorEquals(new Vector3f(-9f, 3.5f, 2.5f), anchor);
  }

  @Test
  void theFlowAxisTurnsOntoTheAxis() {
    Vector3f[] axes = {
      Vector3f.UNIT_Y, Vector3f.UNIT_X, new Vector3f(1f, 2f, -3f), Vector3f.UNIT_Y.negate()
    };
    for (Vector3f axis : axes) {
      Quaternion orientation = ReentryVolumeView.orientation(axis);

      assertVectorEquals(axis.normalize(), orientation.mult(Vector3f.UNIT_Y));
    }
  }

  @Test
  void itStaysHiddenUntilThePieceIsMeasured() {
    view.update(0.7f, Vector3f.UNIT_X, 4_000f, 25_000f, body, true);

    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
  }

  @Test
  void itStaysHiddenWhenColdWithoutAxisOrWithoutItsBody() {
    view.setPieceGeometry(PIECE);

    view.update(0f, Vector3f.UNIT_X, 4_000f, 25_000f, body, true);
    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
    view.update(0.7f, Vector3f.ZERO, 4_000f, 25_000f, body, true);
    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
    view.update(0.7f, Vector3f.UNIT_X, 4_000f, 25_000f, body, false);
    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
  }

  /** A model whose bound gave no body radius: the box would have no size and no length. */
  @Test
  void aPieceWithoutABodyKeepsItOut() {
    view.setPieceGeometry(new PieceGeometry(0f, 0f));

    view.update(0.7f, Vector3f.UNIT_X, 4_000f, 25_000f, body, true);

    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
  }

  @Test
  void aHotMeasuredVisiblePieceShowsItsVolumeOnItsNoseAlongTheAxis() {
    view.setPieceGeometry(PIECE);

    view.update(0.7f, Vector3f.UNIT_X, 4_000f, 25_000f, body, true);

    Node pivot = pivot();
    assertEquals(Spatial.CullHint.Inherit, pivot.getLocalCullHint());
    assertVectorEquals(new Vector3f(-9f, 2f, 3f), pivot.getLocalTranslation());
    assertVectorEquals(new Vector3f(2f, 2f, 2f), pivot.getLocalScale());
    assertVectorEquals(Vector3f.UNIT_X, pivot.getLocalRotation().mult(Vector3f.UNIT_Y));
    assertEquals(0.7f, (Float) material().getParamValue("Intensity"));
  }

  /** Drawn by the volume's own reduced-resolution pass, never by the near view's main one. */
  @Test
  void theBoxIsQueuedInTheTranslucentBucket() {
    assertEquals(RenderQueue.Bucket.Translucent, box().getQueueBucket());
  }

  /** 5 radii of body: from 3 radii ahead of the nose to 20 beyond the base, 11 to each side. */
  @Test
  void theBoxReachesFromAheadOfTheNoseToBeyondTheBase() {
    view.setPieceGeometry(PIECE);

    view.update(0.7f, Vector3f.UNIT_X, 4_000f, 25_000f, body, true);

    assertVectorEquals(
        new Vector3f(-11f, -25f, -11f), (Vector3f) material().getParamValue("BoxMin"));
    assertVectorEquals(new Vector3f(11f, 3f, 11f), (Vector3f) material().getParamValue("BoxMax"));
    BoundingBox bound = (BoundingBox) box().getModelBound();
    assertVectorEquals(new Vector3f(-11f, -25f, -11f), bound.getMin(null));
    assertVectorEquals(new Vector3f(11f, 3f, 11f), bound.getMax(null));
    assertEquals(5f, (Float) material().getParamValue("BodyLength"), 1e-6f);
  }

  /**
   * A payload's body runs from its nose down to its base, under the origin: 7 radii, and the box
   * reaches 20 radii beyond that base. The volume hangs on its drawn nose, on its own axis.
   */
  @Test
  void anOffAxisBodyRunsFromItsNoseToItsBase() {
    view.setPieceGeometry(OFF_AXIS_PIECE);

    view.update(0.7f, Vector3f.UNIT_X, 4_000f, 25_000f, body, true);

    assertEquals(7f, (Float) material().getParamValue("BodyLength"), 1e-6f);
    assertVectorEquals(
        new Vector3f(-11f, -27f, -11f), (Vector3f) material().getParamValue("BoxMin"));
    assertVectorEquals(new Vector3f(-9f, 3.5f, 2.5f), pivot().getLocalTranslation());
  }

  /** A silhouette change hands over a new measure: a body twice as long, a box 5 radii longer. */
  @Test
  void theBoxFollowsANewMeasure() {
    view.setPieceGeometry(PIECE);
    view.update(0.7f, Vector3f.UNIT_X, 4_000f, 25_000f, body, true);

    view.setPieceGeometry(new PieceGeometry(20f, 2f));
    view.update(0.7f, Vector3f.UNIT_X, 4_000f, 25_000f, body, true);

    assertVectorEquals(
        new Vector3f(-11f, -30f, -11f), (Vector3f) material().getParamValue("BoxMin"));
    assertEquals(10f, (Float) material().getParamValue("BodyLength"), 1e-6f);
    assertEquals(-30f, ((BoundingBox) box().getModelBound()).getMin(null).y, 1e-5f);
  }

  /**
   * The body's nose points along -X; the air-relative axis is 10 degrees off it. In the box's axes,
   * where the flow runs along +Y, the body's axis is 10 degrees off +Y.
   */
  @Test
  void theBodyAxisIsTheDrawnNoseDirectionInTheBoxsAxes() {
    view.setPieceGeometry(PIECE);
    float tilt = 10f * FastMath.DEG_TO_RAD;
    Vector3f axis = new Vector3f(-FastMath.cos(tilt), FastMath.sin(tilt), 0f);

    view.update(0.7f, axis, 4_000f, 25_000f, body, true);

    Vector3f bodyAxis = (Vector3f) material().getParamValue("BodyAxis");
    assertEquals(1f, bodyAxis.length(), 1e-5f);
    assertEquals(FastMath.cos(tilt), bodyAxis.dot(Vector3f.UNIT_Y), 1e-5f);
    Vector3f world = pivot().getLocalRotation().mult(bodyAxis);
    assertVectorEquals(new Vector3f(-1f, 0f, 0f), world);
  }

  /** A Falcon Heavy booster at its heating peak: Mach 15.1 at 24.5 km, against 290 m/s. */
  @Test
  void theShockComesFromTheSpeedAndTheAltitude() {
    view.setPieceGeometry(PIECE);

    view.update(0.7f, Vector3f.UNIT_X, 4_379f, 24_500f, body, true);

    Material material = material();
    assertEquals(0.775989f, (Float) material.getParamValue("XA"), 1e-5f);
    assertEquals(257.4783f, (Float) material.getParamValue("K"), 1e-2f);
    assertEquals(0.002589215f, (Float) material.getParamValue("A"), 1e-7f);
    assertEquals(1.5f, (Float) material.getParamValue("Rc"), 0f);
    assertEquals(0f, (Float) material.getParamValue("Viol"), 0f);
    assertEquals(1.033545f, (Float) material.getParamValue("Temp"), 1e-5f);
  }

  /**
   * The turbulence drifts with the simulated time, and keeps the share of itself the clock rate
   * allows: 1 - smoothstep(2, 10, 5) = 0.68359375 at five times, whole when paused.
   */
  @Test
  void theFlowFollowsTheSimulatedClock() {
    view.setFlow(123.5f, 5.0);

    assertEquals(123.5f, (Float) material().getParamValue("FlowSeconds"), 0f);
    assertEquals(0.68359375f, (Float) material().getParamValue("Turbulence"), 1e-6f);

    view.setFlow(130f, 0.0);

    assertEquals(130f, (Float) material().getParamValue("FlowSeconds"), 0f);
    assertEquals(1f, (Float) material().getParamValue("Turbulence"), 0f);
  }

  @Test
  void puttingItOutHidesALitVolume() {
    view.setPieceGeometry(PIECE);
    view.update(0.7f, Vector3f.UNIT_X, 4_000f, 25_000f, body, true);

    view.putOut();

    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
  }

  private Node pivot() {
    return (Node) anchor3d.getChild(ReentryVolumeView.PIVOT_NAME);
  }

  private Geometry box() {
    return (Geometry) pivot().getChild(0);
  }

  private Material material() {
    return box().getMaterial();
  }

  private static void assertVectorEquals(Vector3f expected, Vector3f actual) {
    assertEquals(0f, expected.distance(actual), 1e-5f, () -> expected + " vs " + actual);
  }
}
