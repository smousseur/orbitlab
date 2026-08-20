package com.smousseur.orbitlab.simulation.mission.vehicle.catalog;

import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import java.util.List;

/** Catalog of named payload models, resolvable by id for the mission wizard. */
public final class Payloads {
  private Payloads() {}

  /**
   * <b>The three payload sections are conventions, not measurements.</b> These entries are generic
   * families with no hardware behind them, so no published geometry exists to derive a section
   * from: each one states the bus shape it assumes and stops there. The three ballistic
   * coefficients that result bracket the 455 kg/m² of the L0 measurement table without having been
   * fitted to it — fitting them would make PHY-2's decay measurement self-fulfilling (spec {@code
   * docs/atmosphere/04-conception-L1.md} §4.3).
   *
   * <p>All three use Cd 2.2, the standard free-molecular value, and none models <b>deployed solar
   * arrays</b>: at the altitude where drag matters a satellite has them stowed. That approximation
   * stops holding for an end-of-life re-entry, which is outside PHY-1 and PHY-2 alike.
   */
  public static final PayloadModel CARGO_MODULE =
      // Assumed 4.5 m diameter bus: π·2.25². B = 429 kg/m².
      new PayloadModel(
          "CARGO_MODULE", "Cargo module", 15_000, 0, null, new AerodynamicProperties(15.9, 2.2));

  public static final PayloadModel EARTH_OBSERVATION_SAT =
      // Assumed 3.0 × 3.0 m bus. B = 505 kg/m².
      new PayloadModel(
          "EARTH_OBS_SAT",
          "Earth observation satellite",
          10_000,
          0,
          null,
          new AerodynamicProperties(9.0, 2.2));

  /** AKM sized for ~1 800 m/s of apogee ΔV at 2 t dry, ~30 % margin (spec 06 §4.2). */
  public static final PayloadModel GEO_SAT =
      // Assumed 2.5 × 2.5 m bus. B = 291 kg/m² at the 4 t departure mass (2 t dry + 2 t AKM).
      new PayloadModel(
          "GEO_SAT",
          "GEO communications satellite",
          2_000,
          2_000,
          new PropulsionSystem(320, 400),
          new AerodynamicProperties(6.25, 2.2));

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

  /**
   * Returns the payload models a mission of the given type can actually fly. A type requiring
   * payload propulsion (see {@link MissionType#requiresPayloadPropulsion()}) keeps only the models
   * carrying an AKM; every other type accepts the whole catalog.
   *
   * @param type the selected mission type
   * @return the eligible models, possibly empty if the catalog offers no compatible model
   */
  public static List<PayloadModel> forMissionType(MissionType type) {
    if (!type.requiresPayloadPropulsion()) {
      return CATALOG;
    }
    return CATALOG.stream().filter(PayloadModel::hasAkm).toList();
  }
}
