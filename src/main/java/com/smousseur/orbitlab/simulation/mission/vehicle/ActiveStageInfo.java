package com.smousseur.orbitlab.simulation.mission.vehicle;

/**
 * Snapshot of the active stage resolved from the current spacecraft mass. Provides all the
 * information needed by maneuvers and mission stages without explicit stage-index lookups.
 *
 * @param stageIndex zero-based index of the active stage in the vehicle stack
 * @param stage the reference Vehicle representing the active stage
 * @param massAbove total reference mass (dry + propellant) of all stages above the active one
 * @param dryMassAbove total dry mass of all stages above the active one
 */
public record ActiveStageInfo(
    int stageIndex, Vehicle stage, double massAbove, double dryMassAbove) {

  /** Returns the propulsion system of the active stage. */
  public PropulsionSystem propulsion() {
    return stage.propulsion();
  }

  /** Returns the dry mass of the active stage. */
  public double dryMass() {
    return stage.dryMass();
  }

  /** Returns the propellant capacity of the active stage. */
  public double propellantCapacity() {
    return stage.propellantCapacity();
  }

  /** Returns the total reference mass (dry + propellant) of the active stage. */
  public double stageMass() {
    return stage.getMass();
  }

  /**
   * Returns the mass the spacecraft should have immediately after jettisoning the active stage.
   * This equals the total reference mass of all stages above.
   */
  public double massAfterJettison() {
    return massAbove;
  }

  /**
   * Returns the remaining fuel in the active stage given the current spacecraft mass.
   *
   * @param currentMass the current total spacecraft mass from SpacecraftState
   * @return the remaining propellant mass in the active stage
   */
  public double remainingFuel(double currentMass) {
    return currentMass - stage.dryMass() - massAbove;
  }

  /**
   * Returns the total dry mass from this stage upward (active stage dry mass + dry mass of all
   * stages above). This is the minimum possible mass after all propellant in and above this stage
   * is exhausted.
   */
  public double remainingDryMass() {
    return stage.dryMass() + dryMassAbove;
  }
}
