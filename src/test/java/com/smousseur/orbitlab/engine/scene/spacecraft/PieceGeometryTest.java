package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.engine.AssetFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Where a piece's nose, base and axis are and how thick its body is, read off its loaded mesh. */
class PieceGeometryTest {

  @BeforeAll
  static void initAssetFactory() {
    try {
      AssetFactory.init(new DesktopAssetManager(true));
    } catch (OrbitlabException alreadyInitialized) {
      // Another test class got there first: its asset manager serves just as well.
    }
  }

  @Test
  void finsNearTheTopDoNotWidenTheBody() {
    Geometry body = new Geometry("body", new Cylinder(21, 16, 1f, 10f, false));
    body.rotate(-FastMath.HALF_PI, 0f, 0f);
    body.setLocalTranslation(0f, 5f, 0f);
    Node piece = new Node("piece");
    piece.attachChild(body);
    piece.attachChild(fin(2f));
    piece.attachChild(fin(-2f));

    PieceGeometry geometry = PieceGeometry.measure(piece);

    assertEquals(10f, geometry.noseHeightUnits(), 1e-4f);
    assertEquals(1f, geometry.bodyRadiusUnits(), 1e-4f);
  }

  /** A body 10 units long and 1 wide, its base 4 units under the origin, its axis at (2, -1). */
  @Test
  void anOffCentreBodyMeasuresItsBaseAndItsAxis() {
    Geometry body = new Geometry("body", new Cylinder(21, 16, 1f, 10f, false));
    body.rotate(-FastMath.HALF_PI, 0f, 0f);
    body.setLocalTranslation(2f, 1f, -1f);
    Node piece = new Node("piece");
    piece.attachChild(body);

    PieceGeometry geometry = PieceGeometry.measure(piece);

    assertEquals(6f, geometry.noseHeightUnits(), 1e-4f);
    assertEquals(-4f, geometry.baseHeightUnits(), 1e-4f);
    assertEquals(10f, geometry.bodyLengthUnits(), 1e-4f);
    assertEquals(2f, geometry.axisXUnits(), 1e-4f);
    assertEquals(-1f, geometry.axisZUnits(), 1e-4f);
    assertEquals(1f, geometry.bodyRadiusUnits(), 1e-4f);
  }

  /**
   * The Falcon Heavy booster, at the 70 m stack it is drawn in: its nose at 0.6531 of the stack,
   * and a 1.94 m body under 2.88 m of legs and grid fins, both read on the asset. Authored base at
   * the origin, its legs and fins put the box's axis 7 cm off {@code +Y}.
   */
  @Test
  void theFalconHeavyBoosterMeasuresItsBodyNotItsLegs() {
    Spatial booster = load("models/vehicles/heavy_falcon/heavy_falcon-booster.gltf", 70f);

    PieceGeometry geometry = PieceGeometry.measure(booster);

    assertEquals(0.6531f * 70f, geometry.noseHeightUnits(), 0.01f);
    assertEquals(1.94f, geometry.bodyRadiusUnits(), 0.01f);
    assertEquals(0f, geometry.baseHeightUnits(), 0.01f);
    assertEquals(-0.071f, geometry.axisXUnits(), 0.01f);
    assertEquals(-0.008f, geometry.axisZUnits(), 0.01f);
  }

  /** The Ariane 64 booster, at its 63 m stack: nose at 0.3624, a 2.19 m body on {@code +Y}. */
  @Test
  void theAriane64BoosterMeasuresItsBody() {
    Spatial booster = load("models/vehicles/ariane_64/ariane_64-booster.gltf", 63f);

    PieceGeometry geometry = PieceGeometry.measure(booster);

    assertEquals(0.3624f * 63f, geometry.noseHeightUnits(), 0.01f);
    assertEquals(2.19f, geometry.bodyRadiusUnits(), 0.01f);
    assertEquals(0f, geometry.baseHeightUnits(), 0.01f);
    assertEquals(0f, geometry.axisXUnits(), 0.01f);
    assertEquals(0f, geometry.axisZUnits(), 0.01f);
  }

  /**
   * The Landsat 8 mesh at its 3 m catalog size, normalized as the application draws it: its origin
   * sits 2.38 m above its base and its axis runs 0.90 m off {@code +Y}, read on the asset.
   */
  @Test
  void theLandsat8PayloadMeasuresTheBaseAndAxisItIsAuthoredWith() {
    Spatial landsat =
        AssetFactory.get().loadModelNormalized("models/payloads/landsat8/landsat8.gltf", 3.0f);

    PieceGeometry geometry = PieceGeometry.measure(landsat);

    assertEquals(0.616f, geometry.noseHeightUnits(), 0.01f);
    assertEquals(-2.384f, geometry.baseHeightUnits(), 0.01f);
    assertEquals(0f, geometry.axisXUnits(), 0.01f);
    assertEquals(-0.900f, geometry.axisZUnits(), 0.01f);
    assertEquals(1.087f, geometry.bodyRadiusUnits(), 0.01f);
  }

  /** The GOES mesh at its 2.5 m catalog size: base 0.48 m under its origin, axis 0.71 m off. */
  @Test
  void theGoesPayloadMeasuresTheBaseAndAxisItIsAuthoredWith() {
    Spatial goes = AssetFactory.get().loadModelNormalized("models/payloads/goes/goes.gltf", 2.5f);

    PieceGeometry geometry = PieceGeometry.measure(goes);

    assertEquals(1.586f, geometry.noseHeightUnits(), 0.01f);
    assertEquals(-0.484f, geometry.baseHeightUnits(), 0.01f);
    assertEquals(-0.559f, geometry.axisXUnits(), 0.01f);
    assertEquals(-0.434f, geometry.axisZUnits(), 0.01f);
    assertEquals(1.402f, geometry.bodyRadiusUnits(), 0.01f);
  }

  /** A thin fin reaching from 1 to 3 units out along x, over the top unit of the 10-unit body. */
  private static Geometry fin(float centreX) {
    Geometry fin = new Geometry("fin", new Box(1f, 0.5f, 0.1f));
    fin.setLocalTranslation(new Vector3f(centreX, 9.5f, 0f));
    return fin;
  }

  private static Spatial load(String path, float scale) {
    Spatial model = new DesktopAssetManager(true).loadModel(path);
    model.setLocalScale(scale);
    return model;
  }
}
