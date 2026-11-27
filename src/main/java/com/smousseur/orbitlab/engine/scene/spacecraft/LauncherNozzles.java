package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.smousseur.orbitlab.engine.PlumeLook;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The nozzle clusters of the launcher meshes that fire, for their exhaust plumes: where each
 * cluster's exit plane sits, how wide it is, which block of the stack it belongs to and how its
 * flame looks. It sits beside {@link LauncherStackGeometry} for the same reason: it is a property
 * of the drawn mesh, not of the catalog launcher, and nothing in the propagation depends on it.
 */
public final class LauncherNozzles {

  /**
   * One nozzle cluster of a mesh, in the frame the silhouettes are drawn in: one unit is the full
   * stack, the base at the origin.
   *
   * @param block the block of the stack the cluster belongs to
   * @param exitX the exit plane's centre x
   * @param exitY the exit plane's height
   * @param exitZ the exit plane's centre z
   * @param radius the cluster's radius across the exit plane
   * @param look how its flame looks
   */
  public record Cluster(
      StageRole block, float exitX, float exitY, float exitZ, float radius, PlumeLook look) {

    public Cluster {
      Objects.requireNonNull(block, "block");
      Objects.requireNonNull(look, "look");
    }
  }

  private static final String FH = LauncherAssets.modelPath(Launchers.FALCON_HEAVY.id());
  private static final String A64 = LauncherAssets.modelPath(Launchers.ARIANE_64.id());

  /**
   * Measured off the GLTF by the bottom slice of each block, the vertices within 0.003 stack unit
   * of its lowest one. On the Falcon Heavy the slice is the nine Merlins of a core, its named
   * engine node holding the centre one alone; their rims sit 0.0014 unit, 8.5 cm, above the centre
   * engine's, whose exit is the one kept. The Ariane 64's P120C rows are those of the current mesh,
   * whose boosters are oversized: re-measure on a mesh re-export.
   */
  private static final Map<String, List<Cluster>> CLUSTERS =
      Map.of(
          FH,
          List.of(
              merlins(StageRole.CORE, 0.0013f, 0.0000f),
              merlins(StageRole.BOOSTER, 0.0018f, 0.0574f),
              merlins(StageRole.BOOSTER, -0.0012f, -0.0572f)),
          suffixed(FH, "-after_boosters"),
          List.of(merlins(StageRole.CORE, 0.0013f, 0.0000f)),
          suffixed(FH, "-after_s1"),
          List.of(new Cluster(StageRole.UPPER, 0.0013f, 0f, 0.0001f, 0.0154f, PlumeLook.MVAC)),
          A64,
          List.of(
              vulcain(),
              p120c(-0.0529f, -0.0529f),
              p120c(0.0529f, 0.0529f),
              p120c(-0.0529f, 0.0529f),
              p120c(0.0529f, -0.0529f)),
          suffixed(A64, "-after_boosters"),
          List.of(vulcain()),
          suffixed(A64, "-after_s1"),
          List.of(new Cluster(StageRole.UPPER, 0f, 0f, 0.0001f, 0.0167f, PlumeLook.VINCI)));

  private LauncherNozzles() {}

  private static Cluster merlins(StageRole block, float x, float z) {
    return new Cluster(block, x, -0.0002f, z, 0.0252f, PlumeLook.MERLIN);
  }

  private static Cluster vulcain() {
    return new Cluster(StageRole.CORE, 0f, 0f, 0f, 0.0216f, PlumeLook.VULCAIN);
  }

  private static Cluster p120c(float x, float z) {
    return new Cluster(StageRole.BOOSTER, x, 0.0051f, z, 0.0188f, PlumeLook.P120C);
  }

  private static String suffixed(String path, String suffix) {
    return path.replace(".gltf", suffix + ".gltf");
  }

  /**
   * The nozzle clusters of a mesh.
   *
   * @param meshPath the GLTF asset path of the mesh drawn
   * @return its clusters, or an empty list for a mesh that fires none or is not measured
   */
  public static List<Cluster> clustersOf(String meshPath) {
    return meshPath == null ? List.of() : CLUSTERS.getOrDefault(meshPath, List.of());
  }

  /**
   * The meshes the table measures, for the test that pins it against the assets.
   *
   * @return their GLTF asset paths
   */
  static Set<String> meshPaths() {
    return CLUSTERS.keySet();
  }
}
