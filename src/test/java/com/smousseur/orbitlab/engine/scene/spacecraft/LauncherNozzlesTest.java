package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.engine.PlumeLook;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The nozzle table against the assets it describes: every silhouette that fires has its clusters,
 * and every cluster sits where its mesh draws it — a launcher's in the stack's units, a payload's
 * in its mounted native frame. Re-exporting a mesh makes this red, which is the point — the table
 * is a measurement of the mesh, and a new mesh owes it a new one.
 */
class LauncherNozzlesTest {

  /**
   * About 3.5 cm on the 70 m Falcon Heavy, 3.2 cm on the 63 m Ariane 64: well under the rims of a
   * nozzle, so a moved or resized cluster cannot pass, and above the rounding of the table, which
   * is written to the fourth decimal of a stack unit.
   */
  private static final float TOLERANCE = 0.0005f;

  /** How far around the expected centre a cluster's vertices are picked up, in cluster radii. */
  private static final float PICK_RADII = 1.2f;

  private static final String FH = LauncherAssets.modelPath(Launchers.FALCON_HEAVY.id());
  private static final String A64 = LauncherAssets.modelPath(Launchers.ARIANE_64.id());
  private static final String GOES = "models/payloads/goes/goes.gltf";
  private static final String LANDSAT8 = "models/payloads/landsat8/landsat8.gltf";
  private static final String LRO = "models/payloads/lro/lro.gltf";

  private final DesktopAssetManager assets = new DesktopAssetManager(true);

  @Test
  void everySilhouetteThatFiresHasItsClusters() {
    Map<String, List<StageRole>> expected =
        Map.of(
            FH,
            List.of(StageRole.CORE, StageRole.BOOSTER, StageRole.BOOSTER),
            suffixed(FH, "-after_boosters"),
            List.of(StageRole.CORE),
            suffixed(FH, "-after_s1"),
            List.of(StageRole.UPPER),
            A64,
            List.of(
                StageRole.CORE,
                StageRole.BOOSTER,
                StageRole.BOOSTER,
                StageRole.BOOSTER,
                StageRole.BOOSTER),
            suffixed(A64, "-after_boosters"),
            List.of(StageRole.CORE),
            suffixed(A64, "-after_s1"),
            List.of(StageRole.UPPER),
            GOES,
            List.of(StageRole.KICK),
            LANDSAT8,
            List.of(StageRole.KICK),
            LRO,
            List.of(StageRole.KICK));

    assertEquals(expected.keySet(), LauncherNozzles.meshPaths());
    for (Map.Entry<String, List<StageRole>> mesh : expected.entrySet()) {
      assertEquals(
          mesh.getValue(),
          LauncherNozzles.clustersOf(mesh.getKey()).stream().map(c -> c.block()).toList(),
          mesh.getKey());
    }
  }

  @Test
  void eachEngineHasItsOwnLook() {
    assertEquals(PlumeLook.MERLIN, LauncherNozzles.clustersOf(FH).get(0).look());
    assertEquals(
        PlumeLook.MVAC, LauncherNozzles.clustersOf(suffixed(FH, "-after_s1")).get(0).look());
    assertEquals(PlumeLook.VULCAIN, LauncherNozzles.clustersOf(A64).get(0).look());
    assertEquals(PlumeLook.P120C, LauncherNozzles.clustersOf(A64).get(1).look());
    assertEquals(
        PlumeLook.VINCI, LauncherNozzles.clustersOf(suffixed(A64, "-after_s1")).get(0).look());
    for (String payload : List.of(GOES, LANDSAT8, LRO)) {
      assertEquals(PlumeLook.PAYLOAD, LauncherNozzles.clustersOf(payload).get(0).look(), payload);
    }
  }

  @Test
  void everyClusterSitsWhereItsMeshDrawsIt() {
    List<Executable> checks = new ArrayList<>();
    for (String path : LauncherNozzles.meshPaths()) {
      if (PayloadMounts.mountOf(path).isPresent()) {
        continue;
      }
      Spatial model = assets.loadModel(path);
      model.updateGeometricState();
      List<NozzleSlices.Tagged> vertices = NozzleSlices.vertices(model);
      for (LauncherNozzles.Cluster cluster : LauncherNozzles.clustersOf(path)) {
        NozzleSlices.Reading measured =
            NozzleSlices.sliceOf(
                NozzleSlices.over(
                    vertices, cluster.exitX(), cluster.exitZ(), PICK_RADII * cluster.radius()));
        String label =
            path + " " + cluster.block() + " at (" + cluster.exitX() + ", " + cluster.exitZ() + ")";
        checks.add(
            () -> assertEquals(cluster.exitY(), measured.exitY(), TOLERANCE, label + " exit"));
        checks.add(
            () -> assertEquals(cluster.exitX(), measured.centreX(), TOLERANCE, label + " x"));
        checks.add(
            () -> assertEquals(cluster.exitZ(), measured.centreZ(), TOLERANCE, label + " z"));
        checks.add(
            () -> assertEquals(cluster.radius(), measured.radius(), TOLERANCE, label + " radius"));
      }
    }
    assertTrue(checks.size() >= 4 * 12, "every cluster of the table is checked");
    assertAll(checks);
  }

  /** A payload's engine exits at the origin of its mounted frame, by construction of the mount. */
  @Test
  void everyPayloadClusterSitsOnItsMountedEngine() {
    List<Executable> checks = new ArrayList<>();
    for (String path : PayloadMounts.meshPaths()) {
      Spatial model = assets.loadModel(path);
      PayloadMounts.mountOf(path).orElseThrow().applyTo(model);
      model.updateGeometricState();
      NozzleSlices.Reading measured = NozzleSlices.sliceOf(NozzleSlices.payloadEngine(path, model));
      List<LauncherNozzles.Cluster> clusters = LauncherNozzles.clustersOf(path);
      checks.add(() -> assertEquals(1, clusters.size(), path + " clusters"));
      LauncherNozzles.Cluster cluster = clusters.isEmpty() ? null : clusters.get(0);
      checks.add(() -> assertEquals(0f, cluster.exitX(), 0f, path + " x"));
      checks.add(() -> assertEquals(0f, cluster.exitY(), 0f, path + " exit"));
      checks.add(() -> assertEquals(0f, cluster.exitZ(), 0f, path + " z"));
      checks.add(
          () -> assertEquals(measured.radius(), cluster.radius(), TOLERANCE, path + " radius"));
    }
    assertEquals(5 * 3, checks.size(), "every payload cluster is checked");
    assertAll(checks);
  }

  @Test
  void aMeshThatIsNotMeasuredHasNoCluster() {
    assertTrue(LauncherNozzles.clustersOf("models/vehicles/unknown/unknown.gltf").isEmpty());
    assertTrue(LauncherNozzles.clustersOf(null).isEmpty());
  }

  private static String suffixed(String path, String suffix) {
    return path.replace(".gltf", suffix + ".gltf");
  }
}
