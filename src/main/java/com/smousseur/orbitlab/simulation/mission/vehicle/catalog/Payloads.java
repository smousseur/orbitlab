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
      // The only cylindrical bus of the catalog, hence π·(d/2)² and not d². B = 429 kg/m².
      new PayloadModel(
          "CARGO_MODULE",
          "Cargo module",
          15_000,
          0,
          null,
          new AerodynamicProperties(15.9, 2.2),
          PayloadDomain.ANY,
          4.5,
          0,
          true);

  /**
   * The satellite that keeps its own orbit, and the four numbers that say so (spec {@code
   * docs/etagement/01-decoupage.md} §3.7).
   *
   * <ul>
   *   <li><b>15 m/s</b> is two and a half times the worst LEO trim PHY-8 / L0 measured — 6.1 m/s,
   *       stable to 0.4 m/s across two launchers and three payload masses. A ΔV does not depend on
   *       the mass carrying it, so that figure survives the day PHY-6 hands the trim to the
   *       satellite alone. PHY-2 is where it gets raised, once drag makes orbit maintenance a real
   *       expense rather than a residual.
   *   <li><b>100 kg</b> of tank covers those 15 m/s up to 13 032 kg of dry mass, i.e. 30 % above
   *       the default the wizard pre-fills — the same kind of writing margin {@link #GEO_SAT}
   *       leaves, at +21.6 %.
   *   <li><b>220 s</b> is a hydrazine monopropellant, not the 320 s of an apogee engine. It is the
   *       shortest way to say this is not a kick motor, and {@code MissionComposer} now checks the
   *       ΔV rather than the presence of a tank, so the distinction is enforced and not just
   *       stated.
   *   <li><b>400 N</b> keeps the measured trim at 152 s, <b>2.75 % of a revolution</b> at 400 km —
   *       inside the 5 % {@link #LUNAR_ORBITER} sets as the limit of a near-impulsive burn — and it
   *       is a thrust the catalog already carries.
   * </ul>
   *
   * <p><b>Nothing burns it yet.</b> A LEO chain never drops its upper stage, so the trim is still
   * the S2's burn; what this load does today is ride along as mass the launcher must lift, which is
   * the lot's one real trajectory movement.
   */
  public static final PayloadModel EARTH_OBSERVATION_SAT =
      // Boxy bus. B = 509 kg/m² at the 10 077 kg departure mass (10 t dry + 77 kg of propellant).
      new PayloadModel(
          "EARTH_OBS_SAT",
          "Earth observation satellite",
          10_000,
          100,
          new PropulsionSystem(220, 400),
          new AerodynamicProperties(9.0, 2.2),
          PayloadDomain.EARTH,
          3.0,
          15.0,
          false);

  /** AKM sized for ~1 800 m/s of apogee ΔV at 2 t dry, ~30 % margin (spec 06 §4.2). */
  public static final PayloadModel GEO_SAT =
      // Boxy bus. B = 291 kg/m² at the 4 t departure mass (2 t dry + 2 t AKM).
      new PayloadModel(
          "GEO_SAT",
          "GEO communications satellite",
          2_000,
          2_000,
          new PropulsionSystem(320, 400),
          new AerodynamicProperties(6.25, 2.2),
          PayloadDomain.EARTH,
          2.5,
          0,
          false);

  /**
   * An inert lunar probe (MIS-4 / L5 §5.1) — the dry mass of LRO (1 846 kg) and Luna-25 (1 750 kg),
   * rounded. It carries no propulsion of its own: the translunar injection is the launcher's last
   * burn and nothing is handed over afterwards (découpage §6 pt 8).
   */
  public static final PayloadModel LUNAR_PROBE =
      // Boxy bus. B = 227 kg/m², which widens the bracket of the PHY-2 table downwards without
      // having been fitted to it, as the three above are.
      new PayloadModel(
          "LUNAR_PROBE",
          "Lunar probe",
          2_000,
          0,
          null,
          new AerodynamicProperties(4.0, 2.2),
          PayloadDomain.LUNAR,
          2.0,
          0,
          false);

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
   *       Chang'e-3's 1.98. GEO_SAT's 400 N kick motor would take 66.4 % of a revolution, which is
   *       not a near-impulsive burn by any reading. Real orbiters split their insertion in three to
   *       five burns for exactly that reason; this one does it once, and that is a catalog decision
   *       written as such (découpage §6 pt 4).
   * </ul>
   */
  public static final PayloadModel LUNAR_ORBITER =
      // Same bus as the probe. B = 302 kg/m² at the 2 658 kg departure mass, between GEO_SAT's
      // 291 and the probe's 227, without having been fitted to either.
      new PayloadModel(
          "LUNAR_ORBITER",
          "Lunar orbiter",
          2_000,
          800,
          new PropulsionSystem(320, 5_500),
          new AerodynamicProperties(4.0, 2.2),
          PayloadDomain.LUNAR,
          2.0,
          0,
          false);

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
   * MissionType#requiresPayloadPropulsion()}, which keeps only the propelled models — and where it
   * is meant to <b>fly</b> (MIS-4 / L5 §5.2). A third axis joined them at PHY-8 / L6: what a
   * payload is <b>for</b>, which takes the cargo module out of every list until MIS-6 gives it the
   * rendezvous it is meant for.
   *
   * <p>The second axis was missing until L5, and it showed: a lunar flyby requires no propulsion,
   * so it was offered the whole catalog, GEO communications satellite included.
   *
   * <p><b>The two axes cross, and a lunar flyby is offered the orbiter too</b> (MIS-5 / L3 §2.2). A
   * flyby requires no propulsion, so it excludes none: the orbiter flies it with an empty tank,
   * exactly as {@link MissionType#LEO} says a propelled payload does. Only {@code LUNAR_ORBIT}
   * needs both axes at once, and it is the one type the catalog answers with a single model — the
   * probe being lunar but inert, the GEO satellite propelled but terrestrial, and the cargo module
   * now filtered out of everything.
   *
   * @param type the selected mission type
   * @return the eligible models, possibly empty if the catalog offers no compatible model
   */
  public static List<PayloadModel> forMissionType(MissionType type) {
    PayloadDomain domain = domainOf(type);
    return CATALOG.stream()
        .filter(model -> !type.requiresPayloadPropulsion() || model.hasPropulsion())
        .filter(model -> model.domain() == PayloadDomain.ANY || model.domain() == domain)
        .filter(model -> !model.requiresRendezvous())
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
   *
   * <p><b>Public for one reader, and it is a test.</b> The wizard's {@code MissionDomain} states
   * the same taxonomy a second time, so that {@code MissionProfile} keeps out of the catalog and
   * its static initialisation; {@code MissionDomainTest} pins the two together against this method,
   * and a test cannot call what it cannot see. Production code outside this class must not read it
   * — the question it answers about a <em>card</em> is {@code MissionProfile.domain()} (MIS-5 / L6
   * §3).
   *
   * @param type the mission type to classify
   * @return where a mission of this type flies
   */
  public static PayloadDomain domainOf(MissionType type) {
    return switch (type) {
      case LEO, GEO -> PayloadDomain.EARTH;
      case LUNAR_FLYBY, LUNAR_ORBIT -> PayloadDomain.LUNAR;
    };
  }
}
