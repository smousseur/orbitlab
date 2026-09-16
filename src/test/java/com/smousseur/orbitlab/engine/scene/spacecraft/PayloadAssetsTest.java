package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PayloadAssetsTest {

  @Test
  void geoSatelliteMapsToGoesAtItsCatalogSize() {
    Optional<PayloadAssets.PayloadAsset> asset = PayloadAssets.forPayload(Payloads.GEO_SAT.id());
    assertTrue(asset.isPresent());
    assertEquals("models/payloads/goes/goes.gltf", asset.orElseThrow().meshPath());
    assertEquals(Payloads.GEO_SAT.dimensionMeters(), asset.orElseThrow().drawnSizeMeters(), 1e-9);
  }

  @Test
  void earthObservationMapsToLandsat() {
    Optional<PayloadAssets.PayloadAsset> asset =
        PayloadAssets.forPayload(Payloads.EARTH_OBSERVATION_SAT.id());
    assertTrue(asset.isPresent());
    assertEquals("models/payloads/landsat8/landsat8.gltf", asset.orElseThrow().meshPath());
  }

  @Test
  void bothLunarPayloadsShareTheLroMesh() {
    assertEquals(
        "models/payloads/lro/lro.gltf",
        PayloadAssets.forPayload(Payloads.LUNAR_PROBE.id()).orElseThrow().meshPath());
    assertEquals(
        "models/payloads/lro/lro.gltf",
        PayloadAssets.forPayload(Payloads.LUNAR_ORBITER.id()).orElseThrow().meshPath());
  }

  @Test
  void cargoModuleHasNoMesh() {
    assertTrue(PayloadAssets.forPayload(Payloads.CARGO_MODULE.id()).isEmpty());
  }

  @Test
  void nullOrUnknownPayloadHasNoMesh() {
    assertTrue(PayloadAssets.forPayload(null).isEmpty());
    assertTrue(PayloadAssets.forPayload("NOT_A_PAYLOAD").isEmpty());
  }
}
