package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.engine.NozzlePlumeConfig;
import com.smousseur.orbitlab.engine.scene.spacecraft.PlumeDrive;
import org.orekit.time.AbsoluteDate;

/**
 * How a burn drives its plume at a date. Everything is a function of the date, so the plume is
 * frozen when the clock is paused and replays the same way when it is scrubbed or rewound.
 */
final class NozzlePlumeSchedule {

  private NozzlePlumeSchedule() {}

  /**
   * The plume's drive at a date.
   *
   * @param burn the burn firing now ({@link BurnTimeline#burnAt}), or {@code null} between burns
   * @param now the current simulation date
   * @param altitudeM the object's altitude now, in metres
   * @param clockRate the simulated seconds per real second, negative when rewinding, zero when
   *     paused
   * @param coreThrottle the fraction of its thrust the core applies while the boosters burn
   * @param config the plume tuning
   * @return the drive, {@link PlumeDrive#OFF} between burns
   */
  static PlumeDrive driveAt(
      BurnTimeline.Burn burn,
      AbsoluteDate now,
      double altitudeM,
      double clockRate,
      float coreThrottle,
      NozzlePlumeConfig config) {
    if (burn == null) {
      return PlumeDrive.OFF;
    }
    return new PlumeDrive(
        burn.role(),
        rampAt(burn, now, config.rampSeconds()),
        (float) now.durationFrom(burn.start()),
        config.pressureFactor(altitudeM),
        clockRate,
        coreThrottle);
  }

  /**
   * How far a burn's plume has lit up, or gone out, at a date: a linear ramp from ignition and
   * toward cut-off, the smaller of the two, so a burn shorter than two ramps peaks below full.
   *
   * @param burn the burn
   * @param now the current simulation date
   * @param rampSeconds the simulated seconds a plume takes to light up and to go out
   * @return the ramp, in {@code [0, 1]}
   */
  static float rampAt(BurnTimeline.Burn burn, AbsoluteDate now, double rampSeconds) {
    double sinceIgnition = now.durationFrom(burn.start());
    double untilCutOff = burn.end().durationFrom(now);
    if (sinceIgnition < 0.0 || untilCutOff < 0.0) {
      return 0f;
    }
    if (rampSeconds <= 0.0) {
      return 1f;
    }
    return (float) Math.min(1.0, Math.min(sinceIgnition, untilCutOff) / rampSeconds);
  }
}
