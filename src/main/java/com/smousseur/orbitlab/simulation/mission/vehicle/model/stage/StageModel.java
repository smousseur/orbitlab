package com.smousseur.orbitlab.simulation.mission.vehicle.model.stage;

import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import java.util.Objects;

/**
 * Static description of one stage of a launcher model (design data, not a flying instance). The
 * tank size ({@code propellantCapacity}) is fixed by design; the propellant load is chosen per
 * mission when the stage is instantiated via {@link #toVehicle(double)}.
 *
 * <p><b>Components are per exemplar, accessors are aggregated</b> (spec {@code
 * docs/etagement/03-conception-L1.md} §3.4). A stage flown in {@code multiplicity} identical copies
 * — four P120C boosters, two Falcon Heavy side cores — declares what one of them is, which is what
 * the sources give and what {@code PHY-5} needs to propagate a single jettisoned booster.
 * Everything downstream keeps reading {@link #dryMass()}, {@link #propellantCapacity()}, {@link
 * #propulsion()} and {@link #aerodynamics()}, which now return the block as it flies. No accessor
 * with a total's name returns a per-exemplar value: the factor is impossible to pick up by mistake,
 * which is the same defect-prevention rule {@link AerodynamicProperties} states for the order of
 * its own two components.
 *
 * @param name the stage name, for diagnostics and logs (e.g. "S1 (3 cores aggregated)")
 * @param unitDryMass the structural mass without propellant of <b>one</b> exemplar (kg)
 * @param unitPropellantCapacity the maximum propellant mass <b>one</b> tank can hold (kg)
 * @param unitPropulsion the propulsion system of <b>one</b> exemplar
 * @param capabilities the physical capabilities of the stage
 * @param unitAerodynamics the frontal area and drag coefficient of <b>one</b> exemplar, or {@code
 *     null} when the model declares none — the stage then flies its phase without drag (spec {@code
 *     docs/atmosphere/04-conception-L1.md} §3.3)
 * @param multiplicity how many identical exemplars fly as this one stack entry (at least 1)
 */
public record StageModel(
    String name,
    double unitDryMass,
    double unitPropellantCapacity,
    PropulsionSystem unitPropulsion,
    StageCapabilities capabilities,
    AerodynamicProperties unitAerodynamics,
    int multiplicity) {

  /** A single-exemplar stage model, as every catalog entry but a booster block declares one. */
  public StageModel(
      String name,
      double unitDryMass,
      double unitPropellantCapacity,
      PropulsionSystem unitPropulsion,
      StageCapabilities capabilities,
      AerodynamicProperties unitAerodynamics) {
    this(
        name,
        unitDryMass,
        unitPropellantCapacity,
        unitPropulsion,
        capabilities,
        unitAerodynamics,
        1);
  }

  /** A stage model that declares no aerodynamics, as every fixture assembling one by hand does. */
  public StageModel(
      String name,
      double unitDryMass,
      double unitPropellantCapacity,
      PropulsionSystem unitPropulsion,
      StageCapabilities capabilities) {
    this(name, unitDryMass, unitPropellantCapacity, unitPropulsion, capabilities, null, 1);
  }

  public StageModel {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(unitPropulsion, "unitPropulsion");
    Objects.requireNonNull(capabilities, "capabilities");
    if (unitDryMass <= 0) {
      throw new IllegalArgumentException("dryMass must be positive");
    }
    if (Double.isNaN(unitPropellantCapacity) || unitPropellantCapacity < 0) {
      throw new IllegalArgumentException("propellantCapacity cannot be negative");
    }
    if (multiplicity < 1) {
      throw new IllegalArgumentException("multiplicity must be at least 1: " + multiplicity);
    }
  }

  /** The structural mass of the whole stack entry, all exemplars together (kg). */
  public double dryMass() {
    return multiplicity * unitDryMass;
  }

  /** The tank size of the whole stack entry, all exemplars together (kg). */
  public double propellantCapacity() {
    return multiplicity * unitPropellantCapacity;
  }

  /**
   * The propulsion of the whole stack entry: thrust summed, specific impulse untouched. Identical
   * exemplars share one exhaust velocity, so the aggregate of {@code N} of them is exactly {@code
   * (N·F, Isp)} — no averaging, no loss.
   */
  public PropulsionSystem propulsion() {
    return multiplicity == 1
        ? unitPropulsion
        : new PropulsionSystem(unitPropulsion.isp(), multiplicity * unitPropulsion.thrust());
  }

  /**
   * The aerodynamics of the whole stack entry: cross sections summed, drag coefficient untouched —
   * it is referred to that same area and is intensive.
   */
  public AerodynamicProperties aerodynamics() {
    if (unitAerodynamics == null || multiplicity == 1) {
      return unitAerodynamics;
    }
    return new AerodynamicProperties(
        multiplicity * unitAerodynamics.crossSection(), unitAerodynamics.dragCoefficient());
  }

  /**
   * Instantiates this stage with a mission-specific propellant load.
   *
   * @param propellantLoad the propellant mass to load into the whole entry (kg), within [0,
   *     capacity]; solid stages must be loaded at full capacity
   * @return the flying instance of this stage
   */
  public LaunchVehicle toVehicle(double propellantLoad) {
    double capacity = propellantCapacity();
    if (!(propellantLoad >= 0 && propellantLoad <= capacity)) {
      throw new IllegalArgumentException(
          "propellantLoad must be within [0, " + capacity + "]: " + propellantLoad);
    }
    if (!capabilities.variableLoad() && propellantLoad != capacity) {
      throw new IllegalArgumentException("solid stage flies full: load must equal capacity");
    }
    return new LaunchVehicle(dryMass(), capacity, propellantLoad, propulsion(), aerodynamics());
  }

  /** Instantiates this stage loaded at full capacity. */
  public LaunchVehicle toVehicleFullyLoaded() {
    return toVehicle(propellantCapacity());
  }
}
