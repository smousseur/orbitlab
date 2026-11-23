package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
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
import com.smousseur.orbitlab.engine.ReentryWakeConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Where the wake starts on a piece — the open rim of its sheath — which way it trails, how large it
 * is told to be, and when it shows: on the ribbon's rule, and only once the piece's model is no
 * longer drawn.
 */
class ReentryWakeViewTest {

  /**
   * A sheath whose apex stands one radius ahead of the nose and whose rim, two radii wide, lies 2.5
   * radii behind the apex; a wake with half a second of persistence, quoted at 4 km/s.
   */
  private static final ReentryFxConfig FX =
      new ReentryFxConfig(
          1e5,
          1.1e7,
          new ColorRGBA(1f, 0.45f, 0.1f, 1f),
          new ColorRGBA(1f, 0.97f, 0.9f, 1f),
          0.6f,
          1.0f,
          2.5f,
          2.0f,
          new ReentryWakeConfig(0.5f, 2f, 40f, 4_000f));

  /** The near viewport's scale: one unit is a kilometre. */
  private static final float UNITS_PER_METER = 1e-3f;

  /** A body whose +Y, along which every piece is authored, is turned onto -X. */
  private static final Quaternion QUARTER_TURN_ABOUT_Z =
      new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Z);

  /** The way the body's nose points, which the air-relative velocity follows here. */
  private static final Vector3f AXIS = Vector3f.UNIT_X.negate();

  /** The piece's 3D model is drawn this frame: the camera is close enough for it. */
  private static final boolean MODEL_DRAWN = true;

  /** The piece has shrunk to its icon this frame: its 3D model is not drawn. */
  private static final boolean IN_ICON = false;

  private Node anchor3d;
  private Node body;
  private ReentryWakeView view;

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
        new ReentryWakeView(
            anchor3d, ReentryWakeMesh.unit(), AssetFactory.get().createReentryWake(FX), FX);
  }

  @Test
  void itStaysHiddenUntilThePieceIsMeasured() {
    view.update(0.7f, AXIS, 6_000f, UNITS_PER_METER, body, IN_ICON, true);

    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
  }

  @Test
  void itStaysHiddenWhenColdOrWithoutAxis() {
    view.setPieceGeometry(new PieceGeometry(10f, 2f));

    view.update(0f, AXIS, 6_000f, UNITS_PER_METER, body, IN_ICON, true);
    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
    view.update(0.7f, Vector3f.ZERO, 6_000f, UNITS_PER_METER, body, IN_ICON, true);
    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
  }

  @Test
  void itStaysHiddenWhenTheRibbonRuleHidesItEvenWhileHot() {
    view.setPieceGeometry(new PieceGeometry(10f, 2f));

    view.update(0.7f, AXIS, 6_000f, UNITS_PER_METER, body, IN_ICON, false);

    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
  }

  /**
   * Up close the piece's model is drawn, and the wake gives way: it is the far relay of a re-entry,
   * shown only where the model has shrunk to its icon.
   */
  @Test
  void itStaysHiddenWhileTheModelIsDrawnEvenWhileHot() {
    view.setPieceGeometry(new PieceGeometry(10f, 2f));

    view.update(0.7f, AXIS, 6_000f, UNITS_PER_METER, body, MODEL_DRAWN, true);

    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
  }

  /**
   * The nose is 10 units up the body's +Y, turned onto -X, off its seat: (-9, 2, 3). The sheath's
   * rim lies 1.5 radii (3 units) behind it along the axis, at (-6, 2, 3), and the wake starts
   * there, trailing toward +X, back past the body.
   */
  @Test
  void aHotMeasuredShownPieceTrailsItsWakeFromTheRimOfItsSheath() {
    view.setPieceGeometry(new PieceGeometry(10f, 2f));

    view.update(0.7f, AXIS, 6_000f, UNITS_PER_METER, body, IN_ICON, true);

    Node pivot = pivot();
    assertEquals(Spatial.CullHint.Inherit, pivot.getLocalCullHint());
    assertVectorEquals(new Vector3f(-6f, 2f, 3f), pivot.getLocalTranslation());
    assertVectorEquals(Vector3f.UNIT_XYZ, pivot.getLocalScale());
    assertVectorEquals(Vector3f.UNIT_X, pivot.getLocalRotation().mult(Vector3f.UNIT_Y));
  }

  /**
   * As wide at its root as the sheath's rim (two radii of 2 units on either side), and as long as
   * the body plus half a second at 6 km/s (10 + 3 units); the reference wake is half a second at 4
   * km/s.
   */
  @Test
  void theTubeIsAsWideAsTheSheathRimAndRunsPastTheBodyByThePersistence() {
    view.setPieceGeometry(new PieceGeometry(10f, 2f));

    view.update(0.7f, AXIS, 6_000f, UNITS_PER_METER, body, IN_ICON, true);

    Material material = tube().getMaterial();
    assertEquals(0.7f, (Float) material.getParamValue("Intensity"));
    assertEquals(8f, (Float) material.getParamValue("WidthUnits"), 1e-6f);
    assertEquals(13f, (Float) material.getParamValue("LengthUnits"), 1e-6f);
    assertEquals(2f, (Float) material.getParamValue("RefLengthUnits"), 1e-6f);
  }

  @Test
  void theTubeIsTransparentAndExemptFromFrustumCulling() {
    Geometry tube = tube();

    assertEquals(RenderQueue.Bucket.Transparent, tube.getQueueBucket());
    assertEquals(Spatial.CullHint.Never, tube.getLocalCullHint());
  }

  @Test
  void puttingItOutHidesALitWake() {
    view.setPieceGeometry(new PieceGeometry(10f, 2f));
    view.update(0.7f, AXIS, 6_000f, UNITS_PER_METER, body, IN_ICON, true);

    view.putOut();

    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
  }

  private Node pivot() {
    return (Node) anchor3d.getChild(ReentryWakeView.PIVOT_NAME);
  }

  private Geometry tube() {
    return (Geometry) pivot().getChild(0);
  }

  private static void assertVectorEquals(Vector3f expected, Vector3f actual) {
    assertEquals(0f, expected.distance(actual), 1e-5f, () -> expected + " vs " + actual);
  }
}
