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
 * @param akmPropellantCapacity the apogee-kick-motor tank size (kg); 0 for an inert payload
 * @param akmPropulsion the AKM propulsion; null for an inert payload
 * @param aerodynamics the frontal area and drag coefficient of the payload, or {@code null} when
 *     the model declares none (spec {@code docs/atmosphere/04-conception-L1.md} §3.3)
 * @param domain where this payload is meant to fly; {@code null} reads as {@link PayloadDomain#ANY}
 */
public record PayloadModel(
    String id,
    String displayName,
    double defaultDryMass,
    double akmPropellantCapacity,
    PropulsionSystem akmPropulsion,
    AerodynamicProperties aerodynamics,
    PayloadDomain domain) {

  /** A payload model declaring no aerodynamics, as every hand-assembled fixture does. */
  public PayloadModel(
      String id,
      String displayName,
      double defaultDryMass,
      double akmPropellantCapacity,
      PropulsionSystem akmPropulsion) {
    this(id, displayName, defaultDryMass, akmPropellantCapacity, akmPropulsion, null, null);
  }

  /** A payload model stating no domain, which means {@link PayloadDomain#ANY}. */
  public PayloadModel(
      String id,
      String displayName,
      double defaultDryMass,
      double akmPropellantCapacity,
      PropulsionSystem akmPropulsion,
      AerodynamicProperties aerodynamics) {
    this(id, displayName, defaultDryMass, akmPropellantCapacity, akmPropulsion, aerodynamics, null);
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
    if (Double.isNaN(akmPropellantCapacity) || akmPropellantCapacity < 0) {
      throw new IllegalArgumentException("akmPropellantCapacity cannot be negative");
    }
    if ((akmPropellantCapacity > 0) != (akmPropulsion != null)) {
      throw new IllegalArgumentException(
          "a payload has an AKM propulsion if and only if it has AKM propellant capacity");
    }
  }

  /**
   * Tells whether this payload carries an apogee kick motor. The compact constructor guarantees the
   * equivalence with {@code akmPropellantCapacity > 0}, so this is the single predicate callers
   * need to decide whether the payload can perform a burn of its own.
   *
   * @return true when the payload has an AKM
   */
  public boolean hasAkm() {
    return akmPropulsion != null;
  }

  /**
   * Instantiates the payload with the dry mass entered at mission creation.
   *
   * @param dryMass the dry mass (kg) entered in the wizard
   * @param akmLoad the AKM propellant load (kg), within [0, akmPropellantCapacity]
   * @return the spacecraft instance topping the vehicle stack
   */
  public Spacecraft toSpacecraft(double dryMass, double akmLoad) {
    if (!(dryMass > 0)) {
      throw new IllegalArgumentException("dryMass must be positive");
    }
    if (!(akmLoad >= 0 && akmLoad <= akmPropellantCapacity)) {
      throw new IllegalArgumentException(
          "akmLoad must be within [0, " + akmPropellantCapacity + "]: " + akmLoad);
    }
    return new Spacecraft(dryMass, akmPropellantCapacity, akmLoad, akmPropulsion, aerodynamics);
  }
}
