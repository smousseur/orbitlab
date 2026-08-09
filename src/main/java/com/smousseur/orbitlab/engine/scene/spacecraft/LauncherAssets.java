package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Pairs a launcher of the {@link Launchers} catalog with the GLTF mesh that represents it in the 3D
 * scene.
 *
 * <p>This lives in the rendering layer rather than on {@link
 * com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel} on purpose: the catalog
 * describes what a launcher <em>is</em> — stages, propulsion, ascent profile — and nothing in the
 * propagation depends on which mesh is drawn. A launcher with no mesh is a rendering gap, not an
 * incomplete catalog entry.
 *
 * <p>The table is explicit rather than a naming convention like the planets' ({@code
 * models/planets/&lt;name&gt;/&lt;name&gt;.gltf}), because the asset folders do not follow the
 * catalog ids: {@code FALCON_HEAVY} is stored under {@code heavy_falcon/heavy_falcon.gltf} and
 * {@code ARIANE_62} under {@code ariane/scene.gltf}.
 *
 * <p><b>Known limitation.</b> The mesh paired with {@code ARIANE_62} is an <em>Ariane 5</em>: no
 * Ariane 6 model was available. Only the silhouette is wrong — masses, propulsion and ascent
 * profile all remain the catalog's Ariane 62 — but the screen does not show the launcher being
 * flown. Replacing it is one row here plus the asset.
 *
 * <p><b>What a mesh listed here must satisfy.</b> Two conventions are assumed by the code that
 * draws it, and both hold for the two current assets: after the GLTF root transform the nose points
 * along {@code +Y} — the correction {@link SpacecraftPresenter} applies is a single one for every
 * spacecraft — and the vehicle is normalized to roughly one unit tall, since {@code Model3dView}
 * scales it from the spacecraft radius alone. A mesh breaking either would fly sideways or at the
 * wrong size, so check them before adding a row.
 */
public final class LauncherAssets {
  private static final Logger logger = LogManager.getLogger(LauncherAssets.class);

  /**
   * Mesh used when the launcher is unknown — a catalog entry with no row here — or when the mission
   * carries no launcher at all. The latter is the legacy {@code MissionEntry(Mission)} path, whose
   * missions are built by the historical constructors: those fly a fully loaded Falcon Heavy, so
   * that is the honest fallback rather than an arbitrary one.
   */
  public static final String DEFAULT_MODEL_PATH = "models/vehicles/heavy_falcon/heavy_falcon.gltf";

  private static final Map<String, String> MODEL_PATHS =
      Map.of(
          Launchers.FALCON_HEAVY.id(),
          DEFAULT_MODEL_PATH,
          Launchers.ARIANE_62.id(),
          "models/vehicles/ariane/scene.gltf");

  private LauncherAssets() {}

  /**
   * Returns the GLTF asset path representing the given launcher.
   *
   * @param launcherId the catalog key (e.g. {@code "ARIANE_62"}), or {@code null} for a mission
   *     carrying no launcher
   * @return the asset path, or {@link #DEFAULT_MODEL_PATH} when the launcher has no mesh of its own
   */
  public static String modelPath(String launcherId) {
    // Tested before the lookup, and not merged into the miss branch below: MODEL_PATHS is a
    // Map.of(), which throws on a null key rather than reporting it absent. "No launcher" is also
    // not a gap to warn about — it is the legacy path, and the default is exactly what it flies.
    if (launcherId == null) {
      return DEFAULT_MODEL_PATH;
    }
    String path = MODEL_PATHS.get(launcherId);
    if (path == null) {
      logger.warn(
          "No 3D model for launcher '{}', falling back to {}", launcherId, DEFAULT_MODEL_PATH);
      return DEFAULT_MODEL_PATH;
    }
    return path;
  }
}
