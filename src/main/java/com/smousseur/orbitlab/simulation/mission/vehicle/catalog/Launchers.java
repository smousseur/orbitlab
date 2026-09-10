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
   * Falcon Heavy (expendable): two strap-on cores burning in parallel with a third, identical
   * central one, then the Merlin Vacuum upper stage. The upper stage max coast (2 h) exceeds any
   * parking coast but not a GTO coast to apogee (~5 h 15), which delegates distant circularization
   * to the payload's kick motor.
   *
   * <p><b>Why the three cores are two entries and not one</b> (spec {@code
   * docs/etagement/04-conception-L2.md}). They are physically identical and lit together, so the
   * old single {@code S1} reproduced the flight exactly — but it could not express the side cores
   * being dropped while the centre one keeps firing, which is what the vehicle actually does. Split
   * at full thrust the two entries run dry together and are jettisoned together, so the trajectory
   * is unchanged to the bit; {@code L3} is where the centre core is throttled and starts outliving
   * them.
   *
   * <p>Figures are <b>per exemplar</b> — one core is a third of the block: 22 t dry, 411 t of
   * kerolox, 7.6 MN, 10.5 m². The aggregate reads 66 t / 1 233 t / 22.8 MN, which is what the
   * catalog declared before the split.
   *
   * <p><b>70 m is checked against the mesh, not just asserted.</b> The fairing spans 0.1878 of the
   * normalized stack, which at this height is 13.1 m — the fairing's quoted length to the decimetre
   * — and the side booster's diameter comes out within 1 % of the centre core's, as three identical
   * cores require. {@code LauncherMeshProportionTest} holds that second check.
   */
  public static final LauncherModel FALCON_HEAVY =
      new LauncherModel(
          "FALCON_HEAVY",
          "Falcon Heavy",
          List.of(
              new StageModel(
                  "Boosters (2 side cores)",
                  22_000,
                  411_000,
                  // Mean-trajectory ISP (sea level 282 s / vacuum 311 s): with no atmosphere
                  // modeled, 296 s is the proxy for real ascent losses (spec 06 §S1).
                  new PropulsionSystem(296, 7_600_000),
                  new StageCapabilities(
                      IgnitionMode.GROUND,
                      0,
                      ShutdownMode.COMMANDED,
                      // Kerolox, not solid: the load stays mission-sizable, as it was on the
                      // aggregate.
                      PropellantType.CRYOGENIC,
                      0.0,
                      StageRole.BOOSTER),
                  // π·1.83² for one 3.66 m core (diameter verified 2026-08-20). Declared per
                  // exemplar, the three cores now total 31.5 m² where the aggregate rounded to
                  // 31.6; the exact value is 31.56, so this is the closer of the two. The fairing
                  // is not modeled — it does not exceed the section. Cd 0.4 is the middle of the
                  // usual 0.3–0.5 bracket for a slender launcher in continuum flow, referred to
                  // that same area; NO transonic peak is represented.
                  new AerodynamicProperties(10.5, 0.4),
                  2),
              new StageModel(
                  "Core",
                  22_000,
                  411_000,
                  new PropulsionSystem(296, 7_600_000),
                  new StageCapabilities(
                      IgnitionMode.GROUND,
                      0,
                      ShutdownMode.COMMANDED,
                      PropellantType.CRYOGENIC,
                      0.0,
                      StageRole.CORE),
                  new AerodynamicProperties(10.5, 0.4)),
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
          // Core throttle 0.81 during the shared phase. The three cores being identical, thrust
          // and propellant are in the same ratio and both blocks would flame out at the same
          // instant; throttling the centre one is what gives it the 29.8 s solo phase the real
          // vehicle flies. Anchored on the maiden flight's timeline — booster separation T+2:33,
          // centre core MECO T+3:04, so 31 s alone — which does not pin the fraction closer than
          // [0.78, 0.83] (spec docs/etagement/05-conception-L3.md §2.1).
          new AscentProfile(7.0, 3.0, 2.0, 0.81),
          70.0);

  /**
   * Ariane 64: four P120C strap-on boosters, a Vulcain 2.1 core, a Vinci upper stage.
   *
   * <p><b>It replaces the Ariane 62 rather than joining it</b> (spec {@code
   * docs/etagement/06-conception-L4.md} §3.5). The catalog holds one Ariane, and the 3D scene has
   * been drawing a four-booster Ariane 64 since AST-1 — the entry is what was lagging.
   *
   * <p><b>Every mass comes from the Ariane 62 entry it replaces.</b> That entry's 36 t of dry mass
   * decomposed exactly as {@code 2 x 11 + 14} and its 434 t of propellant as {@code 2 x 141 + 152}
   * ("65 % of this block's propellant is solid"), so four boosters give 58 t and 716 t. Its 41.1 m2
   * was likewise {@code pi*2.7^2 + 2*pi*1.7^2}, hence 22.9 m2 for the core and 9.08 m2 per booster
   * (spec §2.2). No external source is involved.
   *
   * <p><b>Thrusts follow the burn durations, not the other way round.</b> The two figures the
   * decoupage gives as controls -- boosters ~130 s, Vulcain ~8 min -- fix the mass flows, and the
   * thrust is whatever the chosen ISP makes of them. Reversing the derivation is what fails: the
   * P120C's quoted 4 500 kN is a peak, and holding it would burn the boosters out at 86 s.
   *
   * <p><b>63 m is the long-fairing configuration, and the mesh agrees.</b> The fairing spans 0.3264
   * of the normalized stack, which at this height is 20.6 m against the 20 m of Ariane 6's long
   * fairing; the core's 0.0925 gives 5.83 m against the 5.40 m its own 22.9 m2 declares, the excess
   * being the bounding box of what hangs off it. Its boosters do <em>not</em> agree, and by how
   * much is DT-18.
   *
   * <p><b>The ISPs are calibrated on the ratio between launchers, and that is where the drag debt
   * now sits.</b> The absolute capacity of this catalog is not trustworthy -- the Ariane 62 entry
   * placed 20 t in LEO 400 against ~10.3 t in reality -- so the anchor is the ratio, which reality
   * puts at 2.10. At 278.5 s / 360 s the model gives 2.09. And 278.5 s <em>is</em> the P120C's
   * vacuum ISP: the boosters carry no debt at all, while the Vulcain gives up 71 s of its [320,
   * 431] bracket and carries all of it. That is what PHY-2 has to pick up, and it is now localised
   * instead of diluted in an aggregate (spec §2.4, and decoupage §3.4 which predicted it).
   *
   * <p><b>What splitting buys, stated because the aggregate's javadoc stated the opposite.</b> The
   * Ariane 62 entry flamed its whole first stage out around 128 s -- faithful to the boosters,
   * wrong for a core that really burns ~8 min -- and said so. Here the boosters run dry at 130 s
   * and the core flies on alone for 350 s more. The ascent shape is this vehicle's at last.
   */
  public static final LauncherModel ARIANE_64 =
      new LauncherModel(
          "ARIANE_64",
          "Ariane 64",
          List.of(
              new StageModel(
                  "EAP (4 P120C)",
                  11_000,
                  141_000,
                  // Vacuum ISP, unproxied: a solid's sea-level-to-vacuum spread is tens of
                  // seconds, so the booster proxy would be nearly honest anyway. The thrust is
                  // 141 t over 130 s at that ISP, i.e. the P120C's average and not its 4 500 kN
                  // peak.
                  new PropulsionSystem(278.5, 2_962_000),
                  new StageCapabilities(
                      IgnitionMode.GROUND,
                      0,
                      // A solid at last: variableLoad() finally returns false on a stage that is
                      // one, which is all the lambda sweep needed (spec 06 §3.2).
                      ShutdownMode.BURN_TO_DEPLETION,
                      PropellantType.SOLID,
                      0.0,
                      StageRole.BOOSTER),
                  // pi*1.7^2 for one 3.4 m booster; four of them make 36.3 m2 of the 59.2 m2 the
                  // vehicle presents at lift-off. Cd 0.4, continuum, same rule as every other
                  // atmospheric stage of this catalog.
                  new AerodynamicProperties(9.08, 0.4),
                  4),
              new StageModel(
                  "LLPM (Vulcain 2.1)",
                  14_000,
                  152_000,
                  // Mean-trajectory ISP, 36 % into the [320, 431] bracket: this is the stage that
                  // carries the whole drag debt of the launcher (spec 06 §3.1).
                  new PropulsionSystem(360, 1_118_000),
                  new StageCapabilities(
                      IgnitionMode.GROUND,
                      0,
                      ShutdownMode.COMMANDED,
                      PropellantType.CRYOGENIC,
                      0.0,
                      StageRole.CORE),
                  new AerodynamicProperties(22.9, 0.4)),
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
                  // pi*2.7^2 for the 5.4 m ULPM, and the free-molecular Cd of the Falcon Heavy S2
                  // -- same regime, same reason.
                  new AerodynamicProperties(22.9, 2.2))),
          // Unchanged from the Ariane 62 entry. The core is not throttled: with a flow ratio of
          // 13.7 the boosters run dry long before it, so the block splits on its own.
          new AscentProfile(6.0, 3.0, 5.0),
          63.0);

  private static final List<LauncherModel> CATALOG = List.of(FALCON_HEAVY, ARIANE_64);

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
