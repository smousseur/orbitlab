package com.smousseur.orbitlab.simulation.mission.vehicle;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import java.util.List;

/**
 * Represents a vehicle in the mission simulation. A vehicle has a dry mass, propellant capacity,
 * and a propulsion system. Vehicles can be single-stage or multi-stage (via {@link VehicleStack}).
 *
 * <p>The active stage is resolved dynamically via {@link #resolveActiveStage(double)} based on the
 * current spacecraft mass, eliminating the need for explicit stage-index lookups.
 */
public interface Vehicle {
  /**
   * Returns the dry mass of this vehicle (structural mass without propellant).
   *
   * @return the dry mass in kilograms
   */
  double dryMass();

  /**
   * Returns the maximum propellant capacity of this vehicle.
   *
   * @return the propellant capacity in kilograms
   */
  double propellantCapacity();

  /**
   * Returns the propellant actually loaded into the vehicle.
   *
   * @return the propellant loaded in kilograms
   */
  double propellantLoad();

  /**
   * Returns the propulsion system of this vehicle or its active vehicle.
   *
   * @return the propulsion system
   */
  PropulsionSystem propulsion();

  /**
   * The frontal area and drag coefficient of this vehicle, or {@code null} when it declares none.
   *
   * <p><b>Declaring nothing means not dragging</b>, and that is the whole contract (spec {@code
   * docs/atmosphere/04-conception-L1.md} §3.1). It makes a partially populated catalog predictable
   * rather than dangerous: a stage with no aerodynamic data flies its phase without drag, instead
   * of borrowing a neighbour's section or inventing one. No exception, no default value, no {@code
   * NaN}.
   *
   * <p>The aerodynamics is resolved through {@link #resolveActiveStage(double)}, never off the
   * stack as a whole — see {@code VehicleStack}, which deliberately does not override this.
   *
   * @return the aerodynamic properties of this vehicle, or {@code null}
   */
  default AerodynamicProperties aerodynamics() {
    return null;
  }

  /**
   * Returns the total mass of this vehicle (dry mass plus propellant loaded).
   *
   * @return the total mass in kilograms
   */
  default double getMass() {
    return dryMass() + propellantLoad();
  }

  /**
   * Resolves the active vehicle based on the current spacecraft mass. For single-vehicle vehicles,
   * always returns itself. For multi-vehicle vehicles ({@link VehicleStack}), determines which
   * vehicle is active by comparing the current mass against cumulative vehicle mass thresholds.
   *
   * @param currentMass the current spacecraft mass from SpacecraftState
   * @return the active vehicle information
   */
  default ActiveStageInfo resolveActiveStage(double currentMass) {
    return new ActiveStageInfo(0, this, 0, 0);
  }

  /**
   * Resolves the per-stage propellant accounting implied by the current spacecraft mass. For a
   * single vehicle this is one entry holding whatever mass sits above the dry mass.
   *
   * <p>The mass model makes a stage's transition to the next one happen exactly when the mass
   * reaches its depletion floor, so a stage below the active one has burnt out (residual 0). A
   * stage jettisoned <em>early</em>, with propellant still aboard, is invisible here — the caller
   * observing the jettison must capture that residual (see {@code MissionOptimizer}).
   *
   * @param currentMass the current spacecraft mass from SpacecraftState
   * @return the per-stage propellant accounting, in stack order (index 0 = bottom stage)
   */
  default List<StagePropellant> resolveStagePropellant(double currentMass) {
    double residual = Math.max(0.0, Math.min(propellantLoad(), currentMass - dryMass()));
    return List.of(new StagePropellant(0, propellantLoad(), residual));
  }
}
