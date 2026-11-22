package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.ReentryFxConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Where the sheath sits on a drawn piece, which way it faces, and when it shows. */
class ReentrySheathViewTest {

  private static final Quaternion QUARTER_TURN_ABOUT_Z =
      new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Z);

  private Node anchor3d;
  private Node body;
  private ReentrySheathView view;

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
        new ReentrySheathView(
            anchor3d,
            ReentrySheathMesh.unit(0.5f, 2.5f, 1.6f),
            AssetFactory.get().createReentrySheath(ReentryFxConfig.defaults()));
  }

  /** The body's nose, 10 units up its +Y, turned a quarter about Z onto -X, off its seat. */
  @Test
  void theAnchorIsTheDrawnNose() {
    Vector3f anchor =
        ReentrySheathView.anchorPoint(new Vector3f(1f, 2f, 3f), QUARTER_TURN_ABOUT_Z, 10f);

    assertVectorEquals(new Vector3f(-9f, 2f, 3f), anchor);
  }

  @Test
  void theShellApexTurnsOntoTheAxis() {
    Vector3f[] axes = {
      Vector3f.UNIT_Y, Vector3f.UNIT_X, new Vector3f(1f, 2f, -3f), Vector3f.UNIT_Y.negate()
    };
    for (Vector3f axis : axes) {
      Quaternion orientation = ReentrySheathView.orientation(axis);

      assertVectorEquals(axis.normalize(), orientation.mult(Vector3f.UNIT_Y));
    }
  }

  @Test
  void itStaysHiddenUntilThePieceIsMeasured() {
    view.update(0.7f, Vector3f.UNIT_X, body, true);

    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
  }

  @Test
  void itStaysHiddenWhenColdWithoutAxisOrWithoutItsBody() {
    view.setPieceGeometry(new PieceGeometry(10f, 2f));

    view.update(0f, Vector3f.UNIT_X, body, true);
    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
    view.update(0.7f, Vector3f.ZERO, body, true);
    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
    view.update(0.7f, Vector3f.UNIT_X, body, false);
    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
  }

  @Test
  void aHotMeasuredVisiblePieceShowsItsSheathOnItsNoseFacingTheAxis() {
    view.setPieceGeometry(new PieceGeometry(10f, 2f));

    view.update(0.7f, Vector3f.UNIT_X, body, true);

    Node pivot = pivot();
    assertEquals(Spatial.CullHint.Inherit, pivot.getLocalCullHint());
    assertVectorEquals(new Vector3f(-9f, 2f, 3f), pivot.getLocalTranslation());
    assertVectorEquals(new Vector3f(2f, 2f, 2f), pivot.getLocalScale());
    assertVectorEquals(Vector3f.UNIT_X, pivot.getLocalRotation().mult(Vector3f.UNIT_Y));
    Geometry shell = (Geometry) pivot.getChild(0);
    assertEquals(0.7f, (Float) shell.getMaterial().getParamValue("Intensity"));
  }

  @Test
  void puttingItOutHidesALitSheath() {
    view.setPieceGeometry(new PieceGeometry(10f, 2f));
    view.update(0.7f, Vector3f.UNIT_X, body, true);

    view.putOut();

    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
  }

  private Node pivot() {
    return (Node) anchor3d.getChild(ReentrySheathView.PIVOT_NAME);
  }

  private static void assertVectorEquals(Vector3f expected, Vector3f actual) {
    assertEquals(0f, expected.distance(actual), 1e-5f, () -> expected + " vs " + actual);
  }
}
