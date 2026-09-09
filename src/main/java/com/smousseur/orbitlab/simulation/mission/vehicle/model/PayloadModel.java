package com.smousseur.orbitlab.simulation.mission.vehicle.model;

import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import java.util.Objects;

/**
 * Static description of a payload family offered by the mission wizard. The dry mass entered by the
 * user at mission creation instantiates it via {@link #toSpacecraft(double, double)}; {@code
 * defaultDryMass} only pre-fills the wizard form.
 *
 * @param id the catalog key (e.g. "GEO_SAT")
 * @param displayName the human-readable name shown by the wizard
 * @param defaultDryMass the dry mass (kg) pre-filling the wizard mass field
 * @param propellantCapacity the payload's own tank size (kg); 0 for an inert payload
 * @param propulsion the payload's own propulsion; null for an inert payload. Named for what every
 *     entry has in common — the payload can fly a burn of its own — and not for one of the three
 *     engines it holds: a GEO apogee kick motor, a 5 500 N lunar insertion engine, and a
 *     station-keeping thruster. That common denominator is exactly what {@code
 *     MissionType#requiresPayloadPropulsion()} has always asked for (spec {@code
 *     docs/etagement/01-decoupage.md} §3.6).
 * @param aerodynamics the frontal area and drag coefficient of the payload, or {@code null} when
 *     the model declares none (spec {@code docs/atmosphere/04-conception-L1.md} §3.3)
 * @param domain where this payload is meant to fly; {@code null} reads as {@link PayloadDomain#ANY}
 * @param dimensionMeters the bus's characteristic dimension (m) — the diameter of a cylindrical
 *     bus, the edge of a boxy one — with solar arrays stowed, as {@link #aerodynamics} assumes; 0
 *     when the model declares none. It is the figure the declared cross-section is computed from,
 *     so the two cannot drift apart unnoticed ({@code PayloadsTest}). Nothing reads it yet: the
 *     scene draws launchers only, and turning it into a drawn size needs the metres-per-mesh-unit
 *     of a given asset, which is PHY-6's to write alongside that asset.
 * @param deltaVBudget the ΔV (m/s) the payload must carry for burns <b>the mission chain does not
 *     compute for it</b>; 0 when it carries none. A GEO or lunar payload declares none on purpose:
 *     its burn is the mission's, sized from the target by {@code PropellantBudget}, and freezing it
 *     as a constant here would freeze one target (spec §3.7). What is left is orbit maintenance,
 *     which nothing computes — and which is where PHY-2 will come to raise the number once drag is
 *     real.
 * @param requiresRendezvous whether this payload only makes sense on a rendezvous mission, which no
 *     {@code MissionType} is before MIS-6. True on the cargo module alone. It is a purpose and not
 *     a place, which is why it is not a {@link PayloadDomain} value: a cargo module flies perfectly
 *     well in Earth orbit, it just has no reason to be put there on its own (spec §3.5).
 */
public record PayloadModel(
    String id,
    String displayName,
    double defaultDryMass,
    double propellantCapacity,
    PropulsionSystem propulsion,
    AerodynamicProperties aerodynamics,
    PayloadDomain domain,
    double dimensionMeters,
    double deltaVBudget,
    boolean requiresRendezvous) {

  /** A payload model declaring no aerodynamics, as every hand-assembled fixture does. */
  public PayloadModel(
      String id,
      String displayName,
      double defaultDryMass,
      double propellantCapacity,
      PropulsionSystem propulsion) {
    this(id, displayName, defaultDryMass, propellantCapacity, propulsion, null, null, 0, 0, false);
  }

  /** A payload model stating no domain, which means {@link PayloadDomain#ANY}. */
  public PayloadModel(
      String id,
      String displayName,
      double defaultDryMass,
      double propellantCapacity,
      PropulsionSystem propulsion,
      AerodynamicProperties aerodynamics) {
    this(
        id,
        displayName,
        defaultDryMass,
        propellantCapacity,
        propulsion,
        aerodynamics,
        null,
        0,
        0,
        false);
  }

  public PayloadModel {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(displayName, "displayName");
    if (domain == null) {
      domain = PayloadDomain.ANY;
    }
    if (!(defaultDryMass > 0)) {
      throw new IllegalArgumentException("defaultDryMass must be positive");
    }
    if (Double.isNaN(propellantCapacity) || propellantCapacity < 0) {
      throw new IllegalArgumentException("propellantCapacity cannot be negative");
    }
    if (Double.isNaN(dimensionMeters) || dimensionMeters < 0) {
      throw new IllegalArgumentException("dimensionMeters cannot be negative");
    }
    if (Double.isNaN(deltaVBudget) || deltaVBudget < 0) {
      throw new IllegalArgumentException("deltaVBudget cannot be negative");
    }
    if ((propellantCapacity > 0) != (propulsion != null)) {
      throw new IllegalArgumentException(
          "a payload has a propulsion if and only if it has propellant capacity");
    }
    if (deltaVBudget > 0 && propulsion == null) {
      throw new IllegalArgumentException("a payload with a ΔV budget needs a propulsion to fly it");
    }
  }

  /**
   * Tells whether this payload carries propulsion of its own. The compact constructor guarantees
   * the equivalence with {@code propellantCapacity > 0}, so this is the single predicate callers
   * need to decide whether the payload can perform a burn of its own.
   *
   * @return true when the payload has a propulsion system
   */
  public boolean hasPropulsion() {
    return propulsion != null;
  }

  /**
   * Instantiates the payload with the dry mass entered at mission creation.
   *
   * @param dryMass the dry mass (kg) entered in the wizard
   * @param propellantLoad the propellant load (kg), within [0, propellantCapacity]
   * @return the spacecraft instance topping the vehicle stack
   */
  public Spacecraft toSpacecraft(double dryMass, double propellantLoad) {
    if (!(dryMass > 0)) {
      throw new IllegalArgumentException("dryMass must be positive");
    }
    if (!(propellantLoad >= 0 && propellantLoad <= propellantCapacity)) {
      throw new IllegalArgumentException(
          "propellantLoad must be within [0, " + propellantCapacity + "]: " + propellantLoad);
    }
    return new Spacecraft(dryMass, propellantCapacity, propellantLoad, propulsion, aerodynamics);
  }
}
