package com.smousseur.orbitlab.simulation.mission.vehicle;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import java.util.List;

/** Catalog of named payload models, resolvable by id for the mission wizard. */
public final class Payloads {
  private Payloads() {}

  public static final PayloadModel CARGO_MODULE =
      new PayloadModel("CARGO_MODULE", "Cargo module", 15_000, 0, null);

  public static final PayloadModel EARTH_OBSERVATION_SAT =
      new PayloadModel("EARTH_OBS_SAT", "Earth observation satellite", 10_000, 0, null);

  /** AKM sized for ~1 800 m/s of apogee ΔV at 2 t dry, ~30 % margin (spec 06 §4.2). */
  public static final PayloadModel GEO_SAT =
      new PayloadModel(
          "GEO_SAT", "GEO communications satellite", 2_000, 2_000, new PropulsionSystem(320, 400));

  private static final List<PayloadModel> CATALOG =
      List.of(CARGO_MODULE, EARTH_OBSERVATION_SAT, GEO_SAT);

  /**
   * Resolves a payload model by its catalog id.
   *
   * @param id the catalog key (e.g. "GEO_SAT")
   * @return the payload model
   * @throws IllegalArgumentException if no model has this id
   */
  public static PayloadModel byId(String id) {
    return CATALOG.stream()
        .filter(model -> model.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown payload id: " + id));
  }

  /** Returns every payload model of the catalog. */
  public static List<PayloadModel> all() {
    return CATALOG;
  }
}
