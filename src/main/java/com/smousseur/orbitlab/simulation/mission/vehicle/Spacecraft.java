package com.smousseur.orbitlab.simulation.mission.vehicle;

/**
 * Represents a spacecraft payload with its own dry mass, propellant capacity, and propulsion
 * system. Typically used as the uppermost element in a {@link VehicleStack}.
 *
 * @param dryMass the structural mass of the spacecraft without propellant (kg)
 * @param propellantCapacity the maximum propellant mass the spacecraft can carry (kg)
 * @param propulsion the spacecraft's propulsion system
 */
public record Spacecraft(
    double dryMass, double propellantCapacity, double propellantLoad, PropulsionSystem propulsion)
    implements Vehicle {
  public Spacecraft {
    if (propellantLoad > propellantCapacity) {
      throw new IllegalArgumentException("propellantLoad cannot exceed propellantCapacity");
    }
  }

  public Spacecraft(double dryMass, double propellantCapacity, PropulsionSystem propulsion) {
    this(dryMass, propellantCapacity, propellantCapacity, propulsion);
  }

  /**
   * Whether this payload can fly a burn of its own — an apogee kick motor with propellant in it.
   *
   * <p>What it buys is <b>coast</b>, not thrust: a payload that carries its own circularization can
   * be dropped on a transfer orbit and left to reach apogee hours later, past anything the launcher's
   * upper stage is specified to survive shut down. It is why a Falcon Heavy reaches GEO at all, on a
   * 5 h 15 coast to apogee its 2 h stage could never hold, and it is the second row of the
   * composition rule in {@code MissionComposer} (spec {@code
   * docs/earth-orbit/01-mission-terre-parametrable.md} §6.1).
   *
   * @return {@code true} when the payload carries usable propellant
   */
  public boolean hasApogeeKickMotor() {
    return propellantLoad > 0.0;
  }

  /**
   * Historical default payload (150 kg, no usable propellant). Kept for the legacy mission path and
   * test fixtures; wizard payloads come from the {@code Payloads} catalog.
   */
  public static final Spacecraft LEGACY =
      new Spacecraft(150, 0, PropulsionSystem.getSpacecraftPropulsion());
}
