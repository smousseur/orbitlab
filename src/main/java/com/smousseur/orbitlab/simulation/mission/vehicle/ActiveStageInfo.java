package com.smousseur.orbitlab.simulation.mission.vehicle;

/**
 * Snapshot of the active vehicle resolved from the current spacecraft mass. Provides all the
 * information needed by maneuvers and mission stages without explicit vehicle-index lookups.
 *
 * @param stageIndex zero-based index of the active vehicle in the vehicle stack
 * @param vehicle the reference Vehicle representing the active vehicle
 * @param massAbove total reference mass (dry + propellant) of all stages above the active one
 * @param dryMassAbove total dry mass of all stages above the active one
 */
public record ActiveStageInfo(
    int stageIndex, Vehicle vehicle, double massAbove, double dryMassAbove) {

  /** Returns the propulsion system of the active vehicle. */
  public PropulsionSystem propulsion() {
    return vehicle.propulsion();
  }

  /** Returns the dry mass of the active vehicle. */
  public double dryMass() {
    return vehicle.dryMass();
  }

  /** Returns the propellant capacity of the active vehicle. */
  public double propellantCapacity() {
    return vehicle.propellantCapacity();
  }

  /** Returns the total reference mass (dry + propellant) of the active vehicle. */
  public double stageMass() {
    return vehicle.getMass();
  }

  /**
   * Returns the mass the spacecraft should have immediately after jettisoning the active vehicle.
   * This equals the total reference mass of all stages above.
   */
  public double massAfterJettison() {
    return massAbove;
  }

  /**
   * Returns the remaining fuel in the active vehicle given the current spacecraft mass.
   *
   * @param currentMass the current total spacecraft mass from SpacecraftState
   * @return the remaining propellant mass in the active vehicle
   */
  public double remainingFuel(double currentMass) {
    return currentMass - vehicle.dryMass() - massAbove;
  }

  /**
   * Returns the total dry mass from this vehicle upward (active vehicle dry mass + dry mass of all
   * stages above). This is the minimum possible mass after all propellant in and above this vehicle
   * is exhausted.
   */
  public double remainingDryMass() {
    return vehicle.dryMass() + dryMassAbove;
  }

  /**
   * Returns the mass floor at which the active vehicle's propellant is fully consumed (its dry
   * mass plus the reference mass of everything above). Below this mass, a burn is consuming
   * propellant that does not exist.
   */
  public double depletionFloor() {
    return vehicle.dryMass() + massAbove;
  }
}
