package com.smousseur.orbitlab.simulation.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * PHY-4 / L4 §7.1 — the Laplace sphere of the Moon.
 *
 * <p>The découpage quotes a single 66 200 km. This class pins that it is the value at the mean
 * Earth-Moon distance and that the real radius sweeps 61 427 → 70 000 km over a year — the reason
 * the detector's threshold is recomputed rather than written down (spec {@code
 * docs/multi-corps/06-conception-L4.md} §3.2).
 */
class SphereOfInfluenceTest {
  private static final Logger logger = LogManager.getLogger(SphereOfInfluenceTest.class);

  /** The distance the découpage's 66 200 km is quoted at. */
  private static final double MEAN_EARTH_MOON_DISTANCE = 384_400_000.0;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
  }

  @Test
  @DisplayName("The Laplace factor is computed from Orekit's GMs, not written down")
  void laplaceFactor() {
    SphereOfInfluence soi = SphereOfInfluence.of(SolarSystemBody.MOON);

    double gmMoon = OrekitService.get().body(SolarSystemBody.MOON).getGM();
    double gmEarth = OrekitService.get().body(SolarSystemBody.EARTH).getGM();
    assertEquals(Math.pow(gmMoon / gmEarth, 0.4), soi.laplaceFactor());

    // Measured 2026-08-17 on Orekit 13.1.1 with the repository's DE-440 archive.
    assertEquals(0.17217202, soi.laplaceFactor(), 1.0e-8);
  }

  @Test
  @DisplayName("At the mean distance the radius is the decoupage's number")
  void radiusAtMeanDistanceMatchesTheDecoupage() {
    SphereOfInfluence soi = SphereOfInfluence.of(SolarSystemBody.MOON);
    double atMeanDistance = MEAN_EARTH_MOON_DISTANCE * soi.laplaceFactor();

    // 66 183 km against the 66 200 km of docs/multi-corps/01-decoupage.md §4 (L4).
    assertEquals(66_183_000.0, atMeanDistance, 1_000.0);
  }

  @Test
  @DisplayName("The radius breathes by 14% over a year, which is why it is not a constant")
  void radiusSweepsOverALunarYear() {
    SphereOfInfluence soi = SphereOfInfluence.of(SolarSystemBody.MOON);
    AbsoluteDate start = new AbsoluteDate(2026, 3, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());

    double min = Double.MAX_VALUE;
    double max = 0.0;
    for (int day = 0; day <= 400; day++) {
      double radius = soi.radiusAt(start.shiftedBy(day * 86_400.0));
      min = Math.min(min, radius);
      max = Math.max(max, radius);
    }

    logger.info(
        "Lunar SOI over 400 days: {} km to {} km (decoupage quotes a single 66 200 km)",
        Math.round(min / 1000.0),
        Math.round(max / 1000.0));

    // Measured 61 427 / 70 000 km; the bounds are loose enough to survive another epoch and tight
    // enough that a constant radius could not satisfy both.
    assertEquals(61_427_000.0, min, 1_000_000.0);
    assertEquals(70_000_000.0, max, 1_000_000.0);
    assertTrue((max - min) / min > 0.10, "the spread is what forbids a constant");
  }

  @Test
  @DisplayName("The radius is the separation times the factor, at the date asked")
  void radiusFollowsTheInstantaneousSeparation() {
    SphereOfInfluence soi = SphereOfInfluence.of(SolarSystemBody.MOON);
    AbsoluteDate date = new AbsoluteDate(2026, 3, 4, 0, 0, 0.0, TimeScalesFactory.getUTC());

    assertEquals(soi.separationAt(date) * soi.laplaceFactor(), soi.radiusAt(date));
  }

  @Test
  @DisplayName("The primary is read from the enum, and a body without one has no sphere")
  void primaryComesFromTheEnum() {
    assertEquals(SolarSystemBody.EARTH, SphereOfInfluence.of(SolarSystemBody.MOON).primary());
    assertEquals(SolarSystemBody.SUN, SphereOfInfluence.of(SolarSystemBody.EARTH).primary());

    assertThrows(
        IllegalArgumentException.class, () -> SphereOfInfluence.of(SolarSystemBody.SUN));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SphereOfInfluence(SolarSystemBody.MOON, SolarSystemBody.MOON));
  }
}
