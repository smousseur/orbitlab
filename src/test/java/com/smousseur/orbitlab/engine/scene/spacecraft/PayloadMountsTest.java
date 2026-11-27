package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The payload mounts against the meshes they turn: once mounted, each mesh's engine exit lies on
 * the origin's horizontal plane, centred on the vertical axis, with the fraction of the mesh the
 * table states above it. Re-exporting a mesh makes this red — the mount is a measurement of the
 * mesh, and a new mesh owes it a new one.
 */
class PayloadMountsTest {

  /**
   * A fraction of the mounted mesh's largest extent: half a millimetre on a metre-sized mesh, well
   * under an engine's rim, and above the rounding of the table, written to the fourth decimal.
   */
  private static final float TOLERANCE_OF_EXTENT = 0.0005f;

  private final DesktopAssetManager assets = new DesktopAssetManager(true);

  @Test
  void everyPayloadMeshIsMounted() {
    for (PayloadModel payload : Payloads.all()) {
      PayloadAssets.forPayload(payload.id())
          .ifPresent(
              asset ->
                  assertTrue(
                      PayloadMounts.mountOf(asset.meshPath()).isPresent(),
                      asset.meshPath() + " has no mount"));
    }
    assertEquals(
        Set.of(
            "models/payloads/goes/goes.gltf",
            "models/payloads/landsat8/landsat8.gltf",
            "models/payloads/lro/lro.gltf"),
        PayloadMounts.meshPaths());
  }

  @Test
  void everyMountedEngineExitsAtTheOriginOnTheAxis() {
    List<Executable> checks = new ArrayList<>();
    for (String path : PayloadMounts.meshPaths()) {
      Spatial model = assets.loadModel(path);
      PayloadMounts.Mount mount = PayloadMounts.mountOf(path).orElseThrow();
      mount.applyTo(model);
      model.updateGeometricState();
      float extent = largestExtent((BoundingBox) model.getWorldBound());
      float tolerance = TOLERANCE_OF_EXTENT * extent;
      List<NozzleSlices.Tagged> vertices = NozzleSlices.vertices(model);
      NozzleSlices.Reading engine = NozzleSlices.sliceOf(NozzleSlices.payloadEngine(path, model));
      float top = NozzleSlices.read(vertices).topY();
      checks.add(() -> assertEquals(0f, engine.exitY(), tolerance, path + " exit plane"));
      checks.add(() -> assertEquals(0f, engine.centreX(), tolerance, path + " exit x"));
      checks.add(() -> assertEquals(0f, engine.centreZ(), tolerance, path + " exit z"));
      checks.add(
          () ->
              assertEquals(
                  mount.heightAboveExitFraction() * extent,
                  top,
                  tolerance,
                  path + " height above exit"));
    }
    assertEquals(4 * 3, checks.size(), "every mount is checked");
    assertAll(checks);
  }

  @Test
  void aModelScaledToItsDrawnSizeIsMountedAtThatSize() {
    String path = "models/payloads/landsat8/landsat8.gltf";
    Spatial model = assets.loadModel(path);
    model.setLocalScale(3f);
    PayloadMounts.mountOf(path).orElseThrow().applyTo(model);
    model.updateGeometricState();
    float tolerance = TOLERANCE_OF_EXTENT * largestExtent((BoundingBox) model.getWorldBound());

    NozzleSlices.Reading engine = NozzleSlices.sliceOf(NozzleSlices.payloadEngine(path, model));

    assertAll(
        () -> assertEquals(0f, engine.exitY(), tolerance, "exit plane"),
        () -> assertEquals(0f, engine.centreX(), tolerance, "exit x"),
        () -> assertEquals(0f, engine.centreZ(), tolerance, "exit z"));
  }

  @Test
  void aLoadedPayloadModelIsMountedInPlace() {
    Spatial model = assets.loadModel(PayloadAssets.LRO_MESH);

    Spatial mounted = PayloadMounts.mount(PayloadAssets.LRO_MESH, model);

    assertSame(model, mounted);
    assertEquals(
        PayloadMounts.mountOf(PayloadAssets.LRO_MESH).orElseThrow().rotation(),
        model.getLocalRotation());
  }

  @Test
  void aLoadedModelWithoutAMountIsLeftAsLoaded() {
    Node model = new Node("launcher piece");
    model.setLocalTranslation(1f, 2f, 3f);

    PayloadMounts.mount(LauncherAssets.DEFAULT_MODEL_PATH, model);

    assertEquals(new Vector3f(1f, 2f, 3f), model.getLocalTranslation());
    assertEquals(new Quaternion(), model.getLocalRotation());
  }

  @Test
  void aMeshThatIsNotAPayloadHasNoMount() {
    assertTrue(PayloadMounts.mountOf(LauncherAssets.DEFAULT_MODEL_PATH).isEmpty());
    assertTrue(PayloadMounts.mountOf(null).isEmpty());
  }

  private static float largestExtent(BoundingBox box) {
    return 2f * Math.max(box.getXExtent(), Math.max(box.getYExtent(), box.getZExtent()));
  }
}
