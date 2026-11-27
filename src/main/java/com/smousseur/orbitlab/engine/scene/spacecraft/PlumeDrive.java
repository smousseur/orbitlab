package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;

/**
 * What drives a launcher's plume this frame, all of it a function of the date: which stage fires,
 * how far into its ignition or cut-off ramp it is, how long it has been firing, and how thin the
 * air is around it.
 *
 * @param role the role of the stage flying the burn now, or {@code null} between burns
 * @param ramp the ignition and cut-off ramp, in {@code [0, 1]}
 * @param flowSeconds the simulated seconds since the burn ignited, which the flame's turbulence
 *     drifts with; small, so a float holds them to well under a frame
 * @param pressureFactor how thin the air is, 0 at sea level and 1 in vacuum
 * @param clockRate the simulated seconds per real second, negative when rewinding, zero when paused
 * @param coreThrottle the fraction of its thrust the core applies while the boosters burn
 */
public record PlumeDrive(
    StageRole role,
    float ramp,
    float flowSeconds,
    float pressureFactor,
    double clockRate,
    float coreThrottle) {

  /** Nothing firing. */
  public static final PlumeDrive OFF = new PlumeDrive(null, 0f, 0f, 0f, 0.0, 1f);
}
