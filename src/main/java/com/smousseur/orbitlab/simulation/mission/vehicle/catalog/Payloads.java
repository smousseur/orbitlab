package com.smousseur.orbitlab.simulation.mission.vehicle.catalog;

import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadDomain;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import java.util.List;

/** Catalog of named payload models, resolvable by id for the mission wizard. */
public final class Payloads {
  private Payloads() {}

  /**
   * <b>The payload sections are conventions, not measurements.</b> These entries are generic
   * families with no hardware behind them, so no published geometry exists to derive a section
   * from: each one states the bus shape it assumes and stops there. The ballistic coefficients that
   * result bracket the 455 kg/m² of the L0 measurement table without having been fitted to it —
   * fitting them would make PHY-2's decay measurement self-fulfilling (spec {@code
   * docs/atmosphere/04-conception-L1.md} §4.3).
   *
   * <p>All of them use Cd 2.2, the standard free-molecular value, and none models <b>deployed solar
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
          new AerodynamicProperties(9.0, 2.2),
          PayloadDomain.EARTH);

  /** AKM sized for ~1 800 m/s of apogee ΔV at 2 t dry, ~30 % margin (spec 06 §4.2). */
  public static final PayloadModel GEO_SAT =
      // Assumed 2.5 × 2.5 m bus. B = 291 kg/m² at the 4 t departure mass (2 t dry + 2 t AKM).
      new PayloadModel(
          "GEO_SAT",
          "GEO communications satellite",
          2_000,
          2_000,
          new PropulsionSystem(320, 400),
          new AerodynamicProperties(6.25, 2.2),
          PayloadDomain.EARTH);

  /**
   * An inert lunar probe (MIS-4 / L5 §5.1) — the dry mass of LRO (1 846 kg) and Luna-25 (1 750 kg),
   * rounded. It carries no propulsion of its own: the translunar injection is the launcher's last
   * burn and nothing is handed over afterwards (découpage §6 pt 8).
   */
  public static final PayloadModel LUNAR_PROBE =
      // Assumed 2.0 × 2.0 m bus. B = 227 kg/m², which widens the bracket of the PHY-2 table
      // downwards without having been fitted to it, as the three above are.
      new PayloadModel(
          "LUNAR_PROBE",
          "Lunar probe",
          2_000,
          0,
          null,
          new AerodynamicProperties(4.0, 2.2),
          PayloadDomain.LUNAR);

  /**
   * A propelled lunar orbiter (MIS-5 / L3, spec {@code docs/lunar-orbit/05-conception-L3.md} §2) —
   * the payload that flies its own lunar-orbit insertion, which {@link #LUNAR_PROBE} cannot.
   *
   * <p><b>Every number comes from L0's measured arrival</b> (spec {@code
   * docs/lunar-orbit/02-baseline-L0.md} §3), and the engine is the one that is not a real
   * orbiter's:
   *
   * <ul>
   *   <li>2 000 kg dry at Isp 320 is the configuration L0 recomputed its table on;
   *   <li>800 kg of capacity covers the 664 kg the insertion costs at the floor of the altitude
   *       band, and leaves 2 433 kg of dry mass writable in the wizard before the budget refuses
   *       (+21.6 %, against GEO_SAT's +17.4 %);
   *   <li>5 500 N is what keeps the burn under 5 % of a lunar revolution — 4.83 % at 100 km, 5.08 %
   *       at 50 km — for an initial acceleration of 2.06 m/s², between Apollo's 2.03 and
   *       Chang'e-3's 1.98. The catalog's 400 N AKM would take 66.4 % of a revolution, which is not
   *       a near-impulsive burn by any reading. Real orbiters split their insertion in three to
   *       five burns for exactly that reason; this one does it once, and that is a catalog decision
   *       written as such (découpage §6 pt 4).
   * </ul>
   */
  public static final PayloadModel LUNAR_ORBITER =
      // Same assumed 2.0 × 2.0 m bus as the probe. B = 302 kg/m² at the 2 658 kg departure mass,
      // between GEO_SAT's 291 and the probe's 227, without having been fitted to either.
      new PayloadModel(
          "LUNAR_ORBITER",
          "Lunar orbiter",
          2_000,
          800,
          new PropulsionSystem(320, 5_500),
          new AerodynamicProperties(4.0, 2.2),
          PayloadDomain.LUNAR);

  private static final List<PayloadModel> CATALOG =
      List.of(CARGO_MODULE, EARTH_OBSERVATION_SAT, GEO_SAT, LUNAR_PROBE, LUNAR_ORBITER);

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
   * Returns the payload models a mission of the given type can actually fly, on the two axes the
   * question has: what the payload must be able to <b>do</b> — {@link
   * MissionType#requiresPayloadPropulsion()}, which keeps only the models carrying an AKM — and
   * where it is meant to <b>fly</b> (MIS-4 / L5 §5.2).
   *
   * <p>The second axis was missing until L5, and it showed: a lunar flyby requires no propulsion,
   * so it was offered the whole catalog, GEO communications satellite included.
   *
   * <p><b>The two axes cross, and a lunar flyby is offered the orbiter too</b> (MIS-5 / L3 §2.2). A
   * flyby requires no propulsion, so it excludes none: the orbiter flies it with an empty tank,
   * exactly as {@link MissionType#LEO} says an AKM-equipped payload does. Only {@code LUNAR_ORBIT}
   * needs both axes at once, and it is the one type the catalog answers with a single model — the
   * cargo module being universal but inert, the probe lunar but inert, the GEO satellite propelled
   * but terrestrial.
   *
   * @param type the selected mission type
   * @return the eligible models, possibly empty if the catalog offers no compatible model
   */
  public static List<PayloadModel> forMissionType(MissionType type) {
    PayloadDomain domain = domainOf(type);
    return CATALOG.stream()
        .filter(model -> !type.requiresPayloadPropulsion() || model.hasAkm())
        .filter(model -> model.domain() == PayloadDomain.ANY || model.domain() == domain)
        .toList();
  }

  /**
   * Where a mission of this type flies.
   *
   * <p><b>Here rather than on {@link MissionType}.</b> This class already imports {@code
   * MissionType}; an accessor the other way would make {@code simulation.mission} depend on {@code
   * simulation.mission.vehicle.model}, which is a cycle. The switch being exhaustive over the
   * enumeration, the compiler points at this site the day another type appears — which is how MIS-5
   * / L3 found it.
   */
  private static PayloadDomain domainOf(MissionType type) {
    return switch (type) {
      case LEO, GEO -> PayloadDomain.EARTH;
      case LUNAR_FLYBY, LUNAR_ORBIT -> PayloadDomain.LUNAR;
    };
  }
}
