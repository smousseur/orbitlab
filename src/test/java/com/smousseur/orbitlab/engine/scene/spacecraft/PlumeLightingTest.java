package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Which nozzle clusters of the drawn mesh a burn lights, and how brightly. */
class PlumeLightingTest {

  private static final String FH = LauncherAssets.modelPath(Launchers.FALCON_HEAVY.id());
  private static final List<LauncherNozzles.Cluster> FULL = LauncherNozzles.clustersOf(FH);
  private static final List<LauncherNozzles.Cluster> AFTER_BOOSTERS =
      LauncherNozzles.clustersOf(FH.replace(".gltf", "-after_boosters.gltf"));
  private static final List<LauncherNozzles.Cluster> AFTER_S1 =
      LauncherNozzles.clustersOf(FH.replace(".gltf", "-after_s1.gltf"));
  private static final List<LauncherNozzles.Cluster> GOES =
      LauncherNozzles.clustersOf("models/payloads/goes/goes.gltf");

  private static final float THROTTLE = 0.81f;

  /** The block burns boosters and core together: every cluster of the full stack fires. */
  @Test
  void aBoosterBurnLightsTheBoostersAndTheCoreTogether() {
    assertArrayEquals(
        new float[] {THROTTLE, 1f, 1f},
        PlumeLighting.intensities(FULL, StageRole.BOOSTER, THROTTLE, 1f),
        1e-6f);
  }

  @Test
  void aCoreBurnLightsTheCoreAlone() {
    assertArrayEquals(
        new float[] {1f},
        PlumeLighting.intensities(AFTER_BOOSTERS, StageRole.CORE, THROTTLE, 1f),
        1e-6f);
  }

  @Test
  void anUpperBurnLightsTheUpperStage() {
    assertArrayEquals(
        new float[] {1f},
        PlumeLighting.intensities(AFTER_S1, StageRole.UPPER, THROTTLE, 1f),
        1e-6f);
  }

  @Test
  void aKickBurnLightsThePayloadsEngine() {
    assertArrayEquals(
        new float[] {1f}, PlumeLighting.intensities(GOES, StageRole.KICK, THROTTLE, 1f), 1e-6f);
  }

  /** The payload fires alone: the upper stage that carried it is gone, its nozzle with it. */
  @Test
  void aKickBurnWaitingForThePayloadLightsNothing() {
    assertArrayEquals(
        new float[] {0f}, PlumeLighting.intensities(AFTER_S1, StageRole.KICK, THROTTLE, 1f), 0f);
  }

  @Test
  void aLauncherBurnLightsNoPayloadEngine() {
    assertArrayEquals(
        new float[] {0f}, PlumeLighting.intensities(GOES, StageRole.UPPER, THROTTLE, 1f), 0f);
  }

  /**
   * A mesh swap is asynchronous: until the silhouette the burn calls for is the one drawn, nothing
   * fires rather than the wrong nozzles.
   */
  @Test
  void aBurnWaitingForItsSilhouetteLightsNothing() {
    assertArrayEquals(
        new float[] {0f},
        PlumeLighting.intensities(AFTER_BOOSTERS, StageRole.BOOSTER, THROTTLE, 1f),
        0f);
    assertArrayEquals(
        new float[] {0f, 0f, 0f},
        PlumeLighting.intensities(FULL, StageRole.CORE, THROTTLE, 1f),
        0f);
    assertArrayEquals(
        new float[] {0f},
        PlumeLighting.intensities(AFTER_BOOSTERS, StageRole.UPPER, THROTTLE, 1f),
        0f);
  }

  @Test
  void noBurnLightsNothing() {
    assertArrayEquals(
        new float[] {0f, 0f, 0f}, PlumeLighting.intensities(FULL, null, THROTTLE, 1f), 0f);
  }

  @Test
  void theIgnitionRampScalesEveryCluster() {
    assertArrayEquals(
        new float[] {0.5f * THROTTLE, 0.5f, 0.5f},
        PlumeLighting.intensities(FULL, StageRole.BOOSTER, THROTTLE, 0.5f),
        1e-6f);
  }
}
