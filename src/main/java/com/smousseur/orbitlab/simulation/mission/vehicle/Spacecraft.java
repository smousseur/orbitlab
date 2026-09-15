package com.smousseur.orbitlab.simulation.mission.vehicle;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;

/**
 * Represents a spacecraft payload with its own dry mass, propellant capacity, and propulsion
 * system. Typically used as the uppermost element in a {@link VehicleStack}.
 *
 * @param dryMass the structural mass of the spacecraft without propellant (kg)
 * @param propellantCapacity the maximum propellant mass the spacecraft can carry (kg)
 * @param propellantLoad the propellant mass actually loaded (kg)
 * @param propulsion the spacecraft's propulsion system
 * @param aerodynamics the frontal area and drag coefficient of the payload, or {@code null} when it
 *     declares none — a payload that declares none does not drag (spec {@code
 *     docs/atmosphere/04-conception-L1.md} §3.1)
 */
public record Spacecraft(
    double dryMass,
    double propellantCapacity,
    double propellantLoad,
    PropulsionSystem propulsion,
    AerodynamicProperties aerodynamics)
    implements Vehicle {
  public Spacecraft {
    if (propellantLoad > propellantCapacity) {
      throw new IllegalArgumentException("propellantLoad cannot exceed propellantCapacity");
    }
  }

  /** A payload flying without declared aerodynamics — the historical shape, and the fixtures'. */
  public Spacecraft(
      double dryMass,
      double propellantCapacity,
      double propellantLoad,
      PropulsionSystem propulsion) {
    this(dryMass, propellantCapacity, propellantLoad, propulsion, null);
  }

  public Spacecraft(double dryMass, double propellantCapacity, PropulsionSystem propulsion) {
    this(dryMass, propellantCapacity, propellantCapacity, propulsion, null);
  }

  /**
   * Whether this payload can fly a burn of its own — an engine and propellant loaded to feed it.
   *
   * <p>This is the switch PHY-5 / L4 turns on: a LEO whose payload answers {@code true} drops its
   * upper stage after the transfer and lets the payload fly its own final trim, while an inert one
   * (the {@link #LEGACY} fixture, or a catalog payload loaded to zero) keeps flying the upper stage
   * to the end exactly as before (spec {@code docs/multi-objets/06-conception-L4.md} §3.1). Both
   * conditions are needed: {@code LEGACY} carries a propulsion system but no propellant, so the
   * load is what tells it apart.
   *
   * @return {@code true} when the payload has a propulsion system and propellant loaded in it
   */
  public boolean hasUsablePropellant() {
    return propulsion != null && propellantLoad > 0;
  }

  /**
   * Historical default payload (150 kg, no usable propellant). Kept for the legacy mission path and
   * test fixtures; wizard payloads come from the {@code Payloads} catalog.
   */
  public static final Spacecraft LEGACY =
      new Spacecraft(150, 0, PropulsionSystem.getSpacecraftPropulsion());
}
