package com.smousseur.orbitlab.simulation.mission.vehicle.model;

/**
 * Flight-profile parameters imposed by the launcher (launcher-driven profile). These are
 * operational constraints of the vehicle (tower clearance, staging sequence), not optimization
 * variables.
 *
 * @param verticalAscentDuration the duration of the purely vertical ascent after lift-off (s)
 * @param pitchKickAngleDeg the initial pitch kick angle starting the gravity turn (degrees)
 * @param interstageCoastDuration the unpowered coast between MECO and next-stage ignition (s)
 * @param coreThrottle the fraction of its thrust the core stage applies while the boosters burn
 *     alongside it, within (0, 1]; 1 means the core is not throttled. It lives here rather than on
 *     the stage because a throttle held during a shared phase is a flight program, not an engine
 *     property (spec {@code docs/etagement/01-decoupage.md} §3.3), and it is read by nothing but
 *     the parallel block: at booster jettison the core recovers full thrust without anything
 *     writing it.
 */
public record AscentProfile(
    double verticalAscentDuration,
    double pitchKickAngleDeg,
    double interstageCoastDuration,
    double coreThrottle) {

  /** A flight profile whose core is not throttled — every launcher of the catalog so far. */
  public AscentProfile(
      double verticalAscentDuration, double pitchKickAngleDeg, double interstageCoastDuration) {
    this(verticalAscentDuration, pitchKickAngleDeg, interstageCoastDuration, 1.0);
  }

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
    if (!(coreThrottle > 0 && coreThrottle <= 1)) {
      throw new IllegalArgumentException("coreThrottle must be within (0, 1]: " + coreThrottle);
    }
  }

  /**
   * Whether this profile throttles its core during the shared phase. A launcher declaring one
   * without a booster stage to share that phase with is refused by {@code LauncherModel}: the value
   * would be read by nothing and silently ignored.
   *
   * @return {@code true} when the core flies below full thrust while the boosters burn
   */
  public boolean throttlesCore() {
    return coreThrottle < 1.0;
  }

  /** Flight profile of the historical default launcher (legacy vehicle path). */
  public static final AscentProfile LEGACY = new AscentProfile(10.0, 3.0, 0.0);
}
