package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LauncherAssetsTest {

  @Test
  void modelPath_falconHeavy_returnsTheHistoricalMesh() {
    assertEquals(
        "models/vehicles/heavy_falcon/heavy_falcon.gltf",
        LauncherAssets.modelPath(Launchers.FALCON_HEAVY.id()));
  }

  @Test
  void modelPath_ariane62_returnsItsOwnMesh() {
    assertEquals(
        "models/vehicles/ariane/scene.gltf", LauncherAssets.modelPath(Launchers.ARIANE_62.id()));
  }

  /**
   * A launcher with no mesh must still be drawable — the mission is flyable either way, and a
   * missing row is a rendering gap, not a reason to blow up the render loop.
   */
  @Test
  void modelPath_unknownLauncher_fallsBackToTheDefault() {
    assertEquals(LauncherAssets.DEFAULT_MODEL_PATH, LauncherAssets.modelPath("SATURN_V"));
  }

  /** Null is the legacy path's "no launcher at all", and takes the same fallback. */
  @Test
  void modelPath_null_fallsBackToTheDefault() {
    assertEquals(LauncherAssets.DEFAULT_MODEL_PATH, LauncherAssets.modelPath(null));
  }

  /**
   * Fails when a launcher joins the catalog without its own mesh. The fallback keeps such a
   * launcher renderable, but silently: it would fly under another vehicle's shape, which is exactly
   * the confusion this table exists to prevent.
   */
  @Test
  void everyCatalogLauncher_hasItsOwnMesh() {
    Set<String> paths = new HashSet<>();
    for (LauncherModel launcher : Launchers.all()) {
      String path = LauncherAssets.modelPath(launcher.id());
      assertTrue(
          paths.add(path),
          () -> launcher.displayName() + " shares its 3D model with another launcher");
    }
  }
}
