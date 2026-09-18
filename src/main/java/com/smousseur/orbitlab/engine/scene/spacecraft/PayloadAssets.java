package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import java.util.Map;
import java.util.Optional;

/**
 * Pairs a payload of the {@link Payloads} catalog with the GLTF mesh that draws it, and the size it
 * is drawn at. It is the
 * render layer's own asset mapping, the payload counterpart of {@link LauncherAssets}: nothing in
 * the propagation depends on which mesh a satellite wears.
 *
 * <p><b>Three meshes, five payloads.</b> {@code goes}, {@code landsat8} and {@code lro} cover the
 * geostationary, earth-observation and lunar families; the two lunar payloads share {@code lro}.
 * The cargo module has no mesh — it is filtered out of every mission type until MIS-6 — and a
 * payload with no catalog id (a hand-assembled fixture) has none either. Both cases return {@link
 * Optional#empty()}, and the primary then keeps its {@code -after_s1} launcher silhouette rather
 * than shrinking to a satellite.
 *
 * <p><b>The drawn size is the catalog's {@code dimensionMeters}, not a mesh property.</b> The
 * payload meshes are third-party assets with no shared normalization (their intrinsic scales differ
 * by orders of magnitude), so the mesh is normalized by its own bounding box at load time and
 * scaled to this true size — see {@code AssetFactory.loadModelNormalized}.
 */
public final class PayloadAssets {

  /**
   * A payload's drawn asset: the mesh to load and the size to draw it at.
   *
   * @param meshPath the GLTF asset path
   * @param drawnSizeMeters the payload's characteristic dimension (m), from the catalog — the size
   *     the mesh's largest extent is normalized to
   */
  public record PayloadAsset(String meshPath, double drawnSizeMeters) {}

  private static final Map<String, String> MESH_PATHS =
      Map.of(
          Payloads.GEO_SAT.id(), "models/payloads/goes/goes.gltf",
          Payloads.EARTH_OBSERVATION_SAT.id(), "models/payloads/landsat8/landsat8.gltf",
          Payloads.LUNAR_PROBE.id(), "models/payloads/lro/lro.gltf",
          Payloads.LUNAR_ORBITER.id(), "models/payloads/lro/lro.gltf");

  private PayloadAssets() {}

  /**
   * Resolves the drawn asset of a payload by its catalog id.
   *
   * @param payloadId the {@code Payloads} catalog key, or {@code null} for a payload with no
   *     catalog origin
   * @return the mesh and drawn size, or {@link Optional#empty()} when the payload has no mesh
   *     (cargo, an unknown id, {@code null}, or a model that declares no dimension)
   */
  public static Optional<PayloadAsset> forPayload(String payloadId) {
    if (payloadId == null) {
      return Optional.empty();
    }
    String meshPath = MESH_PATHS.get(payloadId);
    if (meshPath == null) {
      return Optional.empty();
    }
    double dimensionMeters = Payloads.byId(payloadId).dimensionMeters();
    if (!(dimensionMeters > 0)) {
      return Optional.empty();
    }
    return Optional.of(new PayloadAsset(meshPath, dimensionMeters));
  }
}
