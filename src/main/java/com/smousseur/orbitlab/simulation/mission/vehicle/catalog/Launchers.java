package com.smousseur.orbitlab.simulation.mission.vehicle.catalog;

import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
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
                  // Mean-trajectory ISP (sea level 282 s / vacuum 311 s): with no atmosphere
                  // modeled, 296 s is the proxy for real ascent losses (spec 06 §S1).
                  new PropulsionSystem(296, 22_800_000),
                  new StageCapabilities(
                      IgnitionMode.GROUND,
                      0,
                      ShutdownMode.COMMANDED,
                      PropellantType.CRYOGENIC,
                      0.0,
                      StageRole.CORE),
                  // Frontal area of the block as aggregated: three 3.66 m cores flying side by
                  // side, so 3 × π·1.83² (Falcon Heavy core diameter verified 2026-08-20). The
                  // fairing is not modeled — it does not exceed the aggregate section. Cd 0.4 is
                  // the middle of the usual 0.3–0.5 bracket for a slender launcher in continuum
                  // flow, referred to that same area; NO transonic peak is represented.
                  new AerodynamicProperties(31.6, 0.4)),
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
                      StageRole.UPPER),
                  // π·1.83² for the single 3.66 m core. Cd 2.2 is the standard satellite-drag
                  // value: an upper stage ignites above 70 km, where the flow is already
                  // free-molecular rather than continuous. Numerically it barely matters — the
                  // density there is four orders of magnitude below what S1 crosses (spec
                  // docs/atmosphere/04-conception-L1.md §4.1).
                  new AerodynamicProperties(10.5, 2.2))),
          new AscentProfile(7.0, 3.0, 2.0));

  /**
   * Ariane 62 (two P120C boosters), first stage aggregating the boosters and the Vulcain core.
   *
   * <p><b>Why the boosters and the core are one stage.</b> Ariane 6 stages in <em>parallel</em> —
   * the P120Cs and the Vulcain are lit together on the pad — and the model has no parallel-burn
   * representation: {@code VehicleStack} resolves exactly one active stage, changed only by an
   * explicit jettison. Aggregating them is the same convention {@link #FALCON_HEAVY} already
   * applies to its three cores, and is what the mission stage list can actually fly (spec {@code
   * docs/launchers/01-ariane-62.md} §2).
   *
   * <p><b>What the aggregation costs, stated because it is not visible in the figures.</b> At 9 960
   * kN and 300 s the block flames out around 128 s — measured at T+128.2 s. That is faithful to the
   * boosters (~130 s) and wrong for the core, which really burns ~8 min, so the ascent
   * <em>shape</em> is not this vehicle's. Neither knob can move it: stretching the burn to 300 s
   * would need the aggregate thrust below the lift-off weight.
   *
   * <p>It does not, however, prevent the mission from closing. Measured 2026-08-09 by {@code
   * Ariane62MissionTest}: LEO 400 km on budget loads with a 5 t payload inserts inside 1.2 km of
   * target, 21.7 % of the sized upper-stage load to spare. The reason is that {@code
   * PropellantBudget} sizes the ULPM to 6.7 t rather than flying its 31 t capacity, so the Vinci
   * takes over at a thrust-to-weight near 1.04 — a fully-loaded stack, which no mission flies,
   * would hand over near 0.44 instead.
   *
   * <p>Ariane 64 is deliberately absent: with four boosters the distortion grows until the model
   * separates A64 from A62 by 3 % where reality separates them by a factor 2.5 in GTO capacity (§3
   * of the spec).
   */
  public static final LauncherModel ARIANE_62 =
      new LauncherModel(
          "ARIANE_62",
          "Ariane 62",
          List.of(
              new StageModel(
                  "S1 (2 P120C + LLPM aggregated)",
                  36_000,
                  434_000,
                  // Mean-trajectory ISP, same rule as the Falcon Heavy S1: placed in the
                  // aggregate's [sea level 271 s, vacuum 331 s] bracket where 296 s sits in the
                  // Falcon Heavy one, propellant-mass weighted over the solid boosters and the
                  // cryogenic core.
                  new PropulsionSystem(300, 9_960_000),
                  new StageCapabilities(
                      IgnitionMode.GROUND,
                      0,
                      ShutdownMode.COMMANDED,
                      // A modelling convention, not a chemical claim: 65 % of this block's
                      // propellant is solid, but declaring SOLID would freeze the load (a stage
                      // that is 35 % liquid) out of both StageModel.toVehicle and the multi-lambda
                      // sweep of PropellantLoadOptimizer. FALCON_HEAVY declares its kerolox
                      // aggregate the same way: the field reads "liquid, finite coast".
                      PropellantType.CRYOGENIC,
                      0.0,
                      StageRole.CORE),
                  // Same aggregation rule as the Falcon Heavy S1: the section of the block as it
                  // flies, π·2.7² for the 5.4 m LLPM plus 2 × π·1.7² for the P120C boosters
                  // (ESA Ariane 6 overview, diameters verified 2026-08-20). Cd 0.4, continuum.
                  new AerodynamicProperties(41.1, 0.4)),
              new StageModel(
                  "S2 (ULPM, Vinci)",
                  6_000,
                  31_000,
                  new PropulsionSystem(457, 180_000),
                  new StageCapabilities(
                      IgnitionMode.AIRSTART,
                      4,
                      ShutdownMode.COMMANDED,
                      PropellantType.CRYOGENIC,
                      21_600.0,
                      StageRole.UPPER),
                  // π·2.7² for the 5.4 m ULPM, and the free-molecular Cd of the Falcon Heavy S2 —
                  // same regime, same reason.
                  new AerodynamicProperties(22.9, 2.2))),
          // Shorter vertical rise than Falcon Heavy's 7 s (lift-off T/W ~1.99 against ~1.65, the
          // pad is cleared sooner); same pitch kick, deliberately — nothing justifies an offset.
          // The 5 s interstage coast is the chill-down Vinci needs and the Merlin Vacuum does not.
          new AscentProfile(6.0, 3.0, 5.0));

  private static final List<LauncherModel> CATALOG = List.of(FALCON_HEAVY, ARIANE_62);

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
