package com.smousseur.orbitlab.simulation.mission.vehicle;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.*;

import java.util.List;

/** Catalog of named launcher models, resolvable by id for the mission wizard. */
public final class Launchers {
  private Launchers() {}

  /**
   * Falcon Heavy (expendable), first stage aggregating the three kerolox cores. The upper stage max
   * coast (2 h) exceeds any parking coast but not a GTO coast to apogee (~5 h 15), which delegates
   * distant circularization to the payload's kick motor.
   */
  public static final LauncherModel FALCON_HEAVY =
      new LauncherModel(
          "FALCON_HEAVY",
          "Falcon Heavy",
          List.of(
              new StageModel(
                  "S1 (3 cores aggregated)",
                  66_000,
                  1_233_000,
                  new PropulsionSystem(311, 22_800_000),
                  new StageCapabilities(
                      IgnitionMode.GROUND,
                      0,
                      ShutdownMode.COMMANDED,
                      PropellantType.CRYOGENIC,
                      0.0,
                      StageRole.CORE)),
              new StageModel(
                  "S2 (Merlin Vacuum)",
                  4_000,
                  107_500,
                  new PropulsionSystem(348, 981_000),
                  new StageCapabilities(
                      IgnitionMode.AIRSTART,
                      2,
                      ShutdownMode.COMMANDED,
                      PropellantType.CRYOGENIC,
                      7_200.0,
                      StageRole.UPPER))),
          new AscentProfile(7.0, 3.0, 2.0));

  private static final List<LauncherModel> CATALOG = List.of(FALCON_HEAVY);

  /**
   * Resolves a launcher model by its catalog id.
   *
   * @param id the catalog key (e.g. "FALCON_HEAVY")
   * @return the launcher model
   * @throws IllegalArgumentException if no model has this id
   */
  public static LauncherModel byId(String id) {
    return CATALOG.stream()
        .filter(model -> model.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown launcher id: " + id));
  }

  /** Returns every launcher model of the catalog. */
  public static List<LauncherModel> all() {
    return CATALOG;
  }
}
