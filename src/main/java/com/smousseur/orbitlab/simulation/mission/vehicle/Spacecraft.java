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
 *     declares none — a payload that declares none does not drag
 * @param disposalReserve the end-of-life disposal propellant reserve (kg), carried on top of {@link
 *     #propellantLoad} and sized per-mission by {@code PropellantBudget.disposalReserveFor}. It is
 *     mass the launcher must lift, so it counts in {@link #getMass()}, but it is <b>not</b> usable
 *     propellant: it does not enter {@link #propellantLoad}, {@link #propellantCapacity} or {@link
 *     #hasUsablePropellant()}, so it never triggers the ascent trim. {@code MissionFactory} sets it
 *     when the wizard asks to deorbit the payload at end of mission, 0 otherwise. Spent by the
 *     payload's own engine, flown by {@code DeorbitTail}.
 */
public record Spacecraft(
    double dryMass,
    double propellantCapacity,
    double propellantLoad,
    PropulsionSystem propulsion,
    AerodynamicProperties aerodynamics,
    double disposalReserve)
    implements Vehicle {
  public Spacecraft {
    if (propellantLoad > propellantCapacity) {
      throw new IllegalArgumentException("propellantLoad cannot exceed propellantCapacity");
    }
    if (Double.isNaN(disposalReserve) || disposalReserve < 0) {
      throw new IllegalArgumentException("disposalReserve cannot be negative");
    }
  }

  /** A payload carrying no disposal reserve — the shape every caller but the disposal path uses. */
  public Spacecraft(
      double dryMass,
      double propellantCapacity,
      double propellantLoad,
      PropulsionSystem propulsion,
      AerodynamicProperties aerodynamics) {
    this(dryMass, propellantCapacity, propellantLoad, propulsion, aerodynamics, 0);
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
   * Total mass the launcher lifts: dry structure, usable propellant, and the disposal reserve. The
   * reserve is dead mass through ascent and orbit — only the end-of-life burn spends it — but it is
   * mass all the same, so it belongs here and nowhere in the propellant accounting.
   *
   * @return the total mass in kilograms
   */
  @Override
  public double getMass() {
    return dryMass + propellantLoad + disposalReserve;
  }

  /**
   * Whether this payload can fly a burn of its own — an engine and propellant loaded to feed it.
   *
   * <p>This is the switch PHY-5 / L4 turns on: a LEO whose payload answers {@code true} drops its
   * upper stage after the transfer and lets the payload fly its own final trim, while an inert one
   * (the {@link #LEGACY} fixture, or a catalog payload loaded to zero) keeps flying the upper stage
   * to the end exactly as before. Both conditions are needed: {@code LEGACY} carries a propulsion
   * system but no propellant, so the load is what tells it apart.
   *
   * @return {@code true} when the payload has a propulsion system and propellant loaded in it
   */
  public boolean hasUsablePropellant() {
    return propulsion != null && propellantLoad > 0;
  }

  /**
   * Whether this payload carries an end-of-life disposal reserve. 0 means the mission asks for no
   * disposal.
   *
   * @return {@code true} when {@link #disposalReserve} is positive
   */
  public boolean hasDisposalReserve() {
    return disposalReserve > 0;
  }

  /**
   * Historical default payload (150 kg, no usable propellant). Kept for the legacy mission path and
   * test fixtures; wizard payloads come from the {@code Payloads} catalog.
   */
  public static final Spacecraft LEGACY =
      new Spacecraft(150, 0, PropulsionSystem.getSpacecraftPropulsion());
}
