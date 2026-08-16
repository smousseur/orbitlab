package com.smousseur.orbitlab.simulation.mission.operation;

/**
 * Which of the two azimuths reaching a given inclination a launch flies (spec {@code
 * docs/earth-orbit/01-mission-terre-parametrable.md} §3.1).
 *
 * <p>An inclination never determines an azimuth on its own: {@code A} and {@code 180° − A} reach the
 * same plane from the same site, one heading north of east, the other south of it. The two differ by
 * where the launch sits relative to the orbit's nodes, hence the name — and by which side of the
 * ground track the first revolution sweeps, which is what a real mission actually picks between.
 */
public enum NodeBranch {

  /**
   * The northbound branch: azimuth {@code A = asin(cos i / cos φ)}, in {@code [−90°, +90°]}. Due
   * east for an equatorial-plane target, due north for a polar one, west of north for a retrograde
   * one. This is the branch the historical due-east profile flies.
   */
  ASCENDING,

  /**
   * The southbound branch: azimuth {@code 180° − A}, the mirror of {@link #ASCENDING} about the
   * east–west axis. Same inclination, opposite crossing.
   */
  DESCENDING
}
