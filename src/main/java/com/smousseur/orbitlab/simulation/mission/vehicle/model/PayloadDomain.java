package com.smousseur.orbitlab.simulation.mission.vehicle.model;

/**
 * The second axis of payload eligibility, beside {@code MissionType.requiresPayloadPropulsion()}:
 * <b>where</b> a payload is meant to fly, as opposed to <b>what</b> it must be able to do (MIS-4 /
 * L5 §5.2).
 *
 * <p>It exists because {@code hasAkm()} cannot carry it. A lunar probe is inert, so propulsion says
 * nothing about it, and a catalog filtered on propulsion alone offers a GEO communications
 * satellite to a lunar flyby — and the lunar probe to a low Earth orbit, which is the same
 * incoherence mirrored.
 */
public enum PayloadDomain {
  /** Flies around the Earth: an observation satellite, a communications platform. */
  EARTH,

  /** Flies to the Moon. */
  LUNAR,

  /**
   * Flies anywhere, definitively. Not "no constraint recorded yet": a fourth {@code MissionType}
   * must not make this value a lie, which is why it is a domain of its own rather than the absence
   * of one. A set of eligible types would force {@code CARGO_MODULE} to be reopened to state
   * something that has not changed.
   */
  ANY
}
