package com.smousseur.orbitlab.simulation.mission.vehicle;

import java.util.Objects;

/**
 * Physical capabilities of a launcher stage. Formalizes what a stage CAN do in a flight profile
 * and is the input of the future profile derivation (objective + launcher → stages).
 *
 * @param ignition how the stage is ignited
 * @param restartCount number of relights after first ignition (0 = not restartable)
 * @param shutdown how burns terminate
 * @param propellant propellant nature
 * @param maxCoastDuration maximum time in seconds the stage can stay shut down between two burns;
 *     {@link Double#POSITIVE_INFINITY} if unlimited; meaningless (use 0) when restartCount == 0
 * @param role intended role in the flight profile
 */
public record StageCapabilities(
    IgnitionMode ignition,
    int restartCount,
    ShutdownMode shutdown,
    PropellantType propellant,
    double maxCoastDuration,
    StageRole role) {

  public StageCapabilities {
    Objects.requireNonNull(ignition, "ignition");
    Objects.requireNonNull(shutdown, "shutdown");
    Objects.requireNonNull(propellant, "propellant");
    Objects.requireNonNull(role, "role");
    if (restartCount < 0) {
      throw new IllegalArgumentException("restartCount cannot be negative");
    }
    if (Double.isNaN(maxCoastDuration) || maxCoastDuration < 0) {
      throw new IllegalArgumentException("maxCoastDuration must be a non-negative duration");
    }
    if (propellant == PropellantType.SOLID
        && (shutdown != ShutdownMode.BURN_TO_DEPLETION || restartCount != 0)) {
      throw new IllegalArgumentException("solid stages burn to depletion and cannot restart");
    }
    if (propellant == PropellantType.CRYOGENIC && Double.isInfinite(maxCoastDuration)) {
      throw new IllegalArgumentException("cryogenic stages must declare a finite max coast");
    }
  }

  /**
   * Returns whether the propellant load can be sized per mission. Solids fly full (load ==
   * capacity); liquid loads are mission-sizable.
   */
  public boolean variableLoad() {
    return propellant != PropellantType.SOLID;
  }

  /**
   * Returns whether the stage survives a shutdown of the given duration between two burns.
   *
   * @param duration the coast duration in seconds
   */
  public boolean canCoastFor(double duration) {
    return duration <= maxCoastDuration;
  }
}
