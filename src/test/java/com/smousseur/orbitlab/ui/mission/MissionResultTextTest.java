package com.smousseur.orbitlab.ui.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrbitElements;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Test;

/** ASCII formatting rules of the mission detail view. */
class MissionResultTextTest {

  private static OrbitElements elements(double perigee, double apogee, double inclinationDeg) {
    return new OrbitElements(
        6_778_137.0, 1.5e-4, FastMath.toRadians(inclinationDeg), perigee, apogee);
  }

  @Test
  void altitudesUseAsciiOnly() {
    String line = MissionResultText.formatAltitudes(elements(400_000.0, 400_114.0, 51.6));
    assertEquals("400000 x 400114 m", line);
    assertTrue(line.chars().allMatch(c -> c >= 32 && c < 127), "bitmap fonts only carry 32-127");
  }

  @Test
  void inclinationIsSpelledDegNotDegreeSign() {
    assertEquals("i=51.6012 deg", MissionResultText.formatInclination(FastMath.toRadians(51.6012)));
  }

  @Test
  void missIsSignedOnBothAltitudesAndInclination() {
    MissionTargetOrbit target =
        new MissionTargetOrbit(400_000.0, 400_000.0, FastMath.toRadians(51.6));
    String miss = MissionResultText.formatMiss(elements(400_000.0, 400_114.0, 51.6012), target);
    assertEquals("miss +0 / +114 m  +0.0012 deg", miss);
  }

  @Test
  void meanConventionMissIsLargeAndNegativeOnPerigee() {
    // The measured 2026-08-05 case: an insertion aimed circular at 400 km reads 390612 x 409712 m
    // in mean elements. Roughly 9.4 km, and not an insertion miss — hence the caption in the view.
    MissionTargetOrbit target =
        new MissionTargetOrbit(400_000.0, 400_000.0, FastMath.toRadians(51.6));
    String miss = MissionResultText.formatMiss(elements(390_612.0, 409_712.0, 51.5994), target);
    assertTrue(miss.startsWith("miss -9388 / +9712 m"), () -> "got " + miss);
  }

  @Test
  void durationsBelowAnHourAreMinutesAndSeconds() {
    assertEquals("2:31", MissionResultText.formatDuration(151.0));
    assertEquals("0:07", MissionResultText.formatDuration(7.0));
    assertEquals("0:00", MissionResultText.formatDuration(0.0));
  }

  @Test
  void durationsPastAnHourGainAnHourField() {
    assertEquals("1:02:04", MissionResultText.formatDuration(3724.0));
  }

  @Test
  void nonFiniteDurationIsADash() {
    assertEquals("-", MissionResultText.formatDuration(Double.NaN));
    assertEquals("-", MissionResultText.formatDuration(-1.0));
  }

  @Test
  void nonPropulsiveStagesShowADashRatherThanZero() {
    assertEquals("-", MissionResultText.formatDeltaV(0.0));
    assertEquals("-", MissionResultText.formatPropellant(0.0));
  }

  @Test
  void deltaVIsRoundedToTheMetrePerSecond() {
    assertEquals("5980 m/s", MissionResultText.formatDeltaV(5979.6));
  }

  @Test
  void massesSwitchToTonnesAboveOneTonne() {
    assertEquals("402.4 t", MissionResultText.formatPropellant(402_351.0));
    assertEquals("284 kg", MissionResultText.formatPropellant(284.0));
  }

  /**
   * MIS-5 / L7 §5 — the miss line, whose Earth form must not move by a character.
   *
   * <p>This is the one change of the lot that touches a screen already in use: {@code formatMiss}
   * is shared with LEO, GEO and the four presets, and they all command a plane.
   */
  @Test
  void aTargetCommandingAPlaneKeepsItsDegreeField() {
    String line =
        MissionResultText.formatMiss(
            elements(400_000.0, 400_114.0, 51.6012),
            new MissionTargetOrbit(400_000.0, 400_000.0, FastMath.toRadians(51.6)));
    assertEquals("miss +0 / +114 m  +0.0012 deg", line);
  }

  /** A lunar orbit undergoes its plane, so the degree field is dropped rather than printed NaN. */
  @Test
  void aTargetWithNoPlaneDropsTheDegreeField() {
    String line =
        MissionResultText.formatMiss(
            elements(100_000.0, 100_114.0, 87.3),
            new MissionTargetOrbit(100_000.0, 100_000.0, Double.NaN));
    assertEquals("miss +0 / +114 m", line);
    assertFalse(line.contains("NaN"), line);
    assertTrue(line.chars().allMatch(c -> c >= 32 && c < 127), "bitmap fonts only carry 32-127");
  }

  @Test
  void truncationMarksWhatItCutAndNeverGrowsTheText() {
    assertEquals("short", MissionResultText.truncate("short", 10));
    assertEquals("0123456...", MissionResultText.truncate("0123456789abc", 10));
    assertEquals(10, MissionResultText.truncate("0123456789abc", 10).length());
  }
}
