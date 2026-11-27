package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.List;

/**
 * Which nozzle clusters of the drawn mesh a burn lights, and how brightly.
 *
 * <p>A burn is flown by one active stage, whose role names the silhouette it fires from: the
 * parallel block — boosters and core together — on the full stack, the core alone once the boosters
 * are gone, the upper stage once the core is. A mesh swap is asynchronous, so the mesh drawn can
 * lag the burn by a few frames: until it is the silhouette the burn fires from, nothing is lit
 * rather than the wrong nozzles. The payload's own engine fires from the payload's mesh alone, once
 * the upper stage that carried it is gone.
 */
public final class PlumeLighting {

  private PlumeLighting() {}

  /**
   * The intensity of each cluster of the drawn mesh during a burn.
   *
   * @param clusters the drawn mesh's clusters ({@link LauncherNozzles#clustersOf})
   * @param burnRole the role of the stage flying the burn, or {@code null} between burns
   * @param coreThrottle the fraction of its thrust the core applies while the boosters burn
   * @param ramp the ignition and cut-off ramp, in {@code [0, 1]}
   * @return one intensity per cluster, in the clusters' order
   */
  public static float[] intensities(
      List<LauncherNozzles.Cluster> clusters, StageRole burnRole, float coreThrottle, float ramp) {
    float[] out = new float[clusters.size()];
    if (burnRole == null || !firesFrom(clusters, burnRole)) {
      return out;
    }
    for (int i = 0; i < out.length; i++) {
      out[i] = ramp * share(clusters.get(i).block(), burnRole, coreThrottle);
    }
    return out;
  }

  /** Whether the drawn mesh is the silhouette a burn of this role fires from. */
  private static boolean firesFrom(List<LauncherNozzles.Cluster> clusters, StageRole burnRole) {
    return switch (burnRole) {
      case BOOSTER -> has(clusters, StageRole.BOOSTER);
      case CORE -> has(clusters, StageRole.CORE) && !has(clusters, StageRole.BOOSTER);
      case UPPER -> has(clusters, StageRole.UPPER);
      case KICK -> has(clusters, StageRole.KICK);
    };
  }

  private static float share(StageRole block, StageRole burnRole, float coreThrottle) {
    if (burnRole == StageRole.BOOSTER) {
      return block == StageRole.CORE ? coreThrottle : block == StageRole.BOOSTER ? 1f : 0f;
    }
    return block == burnRole ? 1f : 0f;
  }

  private static boolean has(List<LauncherNozzles.Cluster> clusters, StageRole block) {
    for (LauncherNozzles.Cluster cluster : clusters) {
      if (cluster.block() == block) {
        return true;
      }
    }
    return false;
  }
}
