package com.smousseur.orbitlab.simulation.ephemeris.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.ui.timeline.components.SpeedStepper;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * The property {@code BUG-19} broke: a body's own rotation must never advance by half a turn or
 * more between two samples of the sliding window grid.
 *
 * <p>Past that, {@link
 * com.smousseur.orbitlab.simulation.ephemeris.EphemerisInterpolator#slerp(Rotation, Rotation,
 * double)} takes the shortest arc and the whole-turn count is lost with no error of any kind — the
 * body simply renders slower than it spins, or backwards. Neptune was measured at 4,1 % of its true
 * rate, Saturn and Uranus reversed, and the Earth reversed then frozen once {@link
 * SlidingWindowConfig#plan} doubled its step at a high clock speed.
 *
 * <p>The rate is read from Orekit rather than from the configured table, so that a wrong entry in
 * {@code rotationPeriodSecondsByBody} fails here instead of silently widening the step it is meant
 * to cap.
 */
class SlidingWindowRotationSamplingTest {

  /** The SLERP cliff: at exactly half a turn the branch it picks is a coin toss. */
  private static final double HALF_TURN_DEGREES = 180.0;

  /**
   * Baseline over which the rotation rate is measured. Short enough that no body reaches half a
   * turn — Jupiter, the fastest, turns 36,3° in an hour — so the measured angle needs no
   * unwrapping.
   */
  private static final double RATE_BASELINE_SECONDS = 3600.0;

  private static Frame icrf;
  private static AbsoluteDate epoch;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    icrf = OrekitService.get().icrf();
    epoch = new AbsoluteDate(2026, 1, 1, 0, 0, 0.0, TimeScalesFactory.getTAI());
  }

  @Test
  void rotationPeriodTable_agreesWithOrekit() {
    SlidingWindowConfig cfg = SlidingWindowConfig.defaultSolarSystem();
    for (SolarSystemBody body : SolarSystemBody.values()) {
      double measured = rotationPeriodSecondsFromOrekit(body);
      assertEquals(
          measured,
          cfg.rotationPeriodSeconds(body),
          measured * 1e-3,
          () -> "Configured rotation period disagrees with Orekit for " + body);
    }
  }

  @Test
  void noSelectableClockSpeed_aliasesAnyRotation() {
    SlidingWindowConfig cfg = SlidingWindowConfig.defaultSolarSystem();
    for (int index = SpeedStepper.MIN_INDEX; index <= SpeedStepper.MAX_INDEX; index++) {
      assertNoAliasingAt(cfg, SpeedStepper.mapIndexToSpeed(index));
    }
  }

  /**
   * The same invariant over the whole domain {@link SlidingWindowConfig#plan} accepts, not just the
   * speeds the stepper can produce today. A speed added to the stepper, or a caller setting one
   * directly on the clock, must not be able to re-open the defect.
   */
  @Test
  void noPlannableClockSpeed_aliasesAnyRotation() {
    SlidingWindowConfig cfg = SlidingWindowConfig.defaultSolarSystem();
    int samples = 512;
    double logMax = Math.log(cfg.speedMaxAbs());
    for (int i = 0; i <= samples; i++) {
      assertNoAliasingAt(cfg, Math.exp(logMax * i / samples));
    }
    assertNoAliasingAt(cfg, cfg.speedMaxAbs());
  }

  private static void assertNoAliasingAt(SlidingWindowConfig cfg, double speed) {
    for (SolarSystemBody body : SolarSystemBody.values()) {
      double step = cfg.plan(body, speed).stepSeconds();
      double degrees = degreesPerSecond(body) * step;
      assertTrue(
          degrees < HALF_TURN_DEGREES,
          () ->
              String.format(
                  "%s turns %.1f deg per %.0f s step at speed %.0f — the SLERP would render %.1f deg",
                  body, degrees, step, speed, degrees - 360.0 * Math.round(degrees / 360.0)));
    }
  }

  private static double rotationPeriodSecondsFromOrekit(SolarSystemBody body) {
    return 360.0 / degreesPerSecond(body);
  }

  /** Angular rate of the body's ICRF-to-body-fixed rotation, in degrees per second. */
  private static double degreesPerSecond(SolarSystemBody body) {
    Frame bodyFrame = OrekitService.get().body(body).getBodyOrientedFrame();
    Rotation start = icrf.getTransformTo(bodyFrame, epoch).getRotation();
    Rotation end =
        icrf.getTransformTo(bodyFrame, epoch.shiftedBy(RATE_BASELINE_SECONDS)).getRotation();
    return Math.toDegrees(end.applyTo(start.revert()).getAngle()) / RATE_BASELINE_SECONDS;
  }
}
