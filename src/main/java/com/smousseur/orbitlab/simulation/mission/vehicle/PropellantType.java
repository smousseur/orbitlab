package com.smousseur.orbitlab.simulation.mission.vehicle;

/** Propellant nature; constrains load variability and coast endurance. */
public enum PropellantType {
  /** Fixed load (= capacity), burn to depletion, no restart. */
  SOLID,
  /** Liquid, storable on orbit (hypergolics, RP-1 alone). */
  STORABLE,
  /** Liquid with cryogenic components — bounded coast duration. */
  CRYOGENIC
}
