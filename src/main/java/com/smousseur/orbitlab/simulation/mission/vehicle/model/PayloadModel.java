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
 */
public record PayloadModel(
    String id,
    String displayName,
    double defaultDryMass,
    double akmPropellantCapacity,
    PropulsionSystem akmPropulsion) {

  public PayloadModel {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(displayName, "displayName");
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
    return new Spacecraft(dryMass, akmPropellantCapacity, akmLoad, akmPropulsion);
  }
}
