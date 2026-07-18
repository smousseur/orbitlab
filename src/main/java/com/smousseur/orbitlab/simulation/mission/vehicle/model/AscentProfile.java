package com.smousseur.orbitlab.simulation.mission.vehicle.model;

/**
 * Flight-profile parameters imposed by the launcher (launcher-driven profile). These are
 * operational constraints of the vehicle (tower clearance, staging sequence), not optimization
 * variables.
 *
 * @param verticalAscentDuration the duration of the purely vertical ascent after lift-off (s)
 * @param pitchKickAngleDeg the initial pitch kick angle starting the gravity turn (degrees)
 * @param interstageCoastDuration the unpowered coast between MECO and next-stage ignition (s)
 */
public record AscentProfile(
    double verticalAscentDuration, double pitchKickAngleDeg, double interstageCoastDuration) {

  public AscentProfile {
    if (!(verticalAscentDuration > 0)) {
      throw new IllegalArgumentException("verticalAscentDuration must be positive");
    }
    if (!(pitchKickAngleDeg > 0 && pitchKickAngleDeg < 90)) {
      throw new IllegalArgumentException("pitchKickAngleDeg must be within (0, 90)");
    }
    if (!(interstageCoastDuration >= 0)) {
      throw new IllegalArgumentException("interstageCoastDuration cannot be negative");
    }
  }

  /** Flight profile of the historical default launcher (legacy vehicle path). */
  public static final AscentProfile LEGACY = new AscentProfile(10.0, 3.0, 0.0);
}
