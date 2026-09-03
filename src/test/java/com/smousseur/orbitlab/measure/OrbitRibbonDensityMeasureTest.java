package com.smousseur.orbitlab.measure;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
import com.smousseur.orbitlab.simulation.orbit.OrbitPolicy;
import com.smousseur.orbitlab.simulation.orbit.config.OrbitWindowConfig;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

/**
 * How many vertices each planetary orbit ribbon actually carries, and how densely they land on
 * screen.
 *
 * <p>{@code OrbitWindowConfig} asks for 4096 points per body, but {@code OrbitPathCache} clamps the
 * step that implies to at most 7 days and then <b>recomputes the count from the clamped step</b> —
 * so a body whose period needs a coarser step than that gets more points than were asked for, not
 * fewer. The ribbon is alpha-blended with depth writes off and each quad carries a one-pixel fade
 * margin on either side, so overlapping quads accumulate: density is what a reader sees as
 * thickness, at a width the vertex shader holds constant in pixels by construction.
 */
class OrbitRibbonDensityMeasureTest {

  private static final Logger logger = LogManager.getLogger(OrbitRibbonDensityMeasureTest.class);

  /** {@code OrbitWindowConfig.DEFAULT_MAX_STEP}, the clamp that reshapes the count. */
  private static final double MAX_STEP_SECONDS = 7 * 86400.0;

  private static final double DAY = 86400.0;

  @Test
  void howManyVerticesEachOrbitCarries() {
    OrbitWindowConfig windows = OrbitWindowConfig.defaultSolarSystem();
    EphemerisConfig ephemeris = EphemerisConfig.defaultSolarSystem();

    logger.info("=== RIBBON — vertices per orbit, after the 7-day step clamp ===");
    logger.info("body       period(d)   asked step(d)   applied step(d)   points   vs asked");
    for (SolarSystemBody body : SolarSystemBody.values()) {
      if (body == SolarSystemBody.SUN) {
        continue;
      }
      double period = ephemeris.orbitalPeriodSeconds(body);
      int asked = windows.bodyPoints(body);
      double rawStep = OrbitPolicy.stepSeconds(period, asked);
      double step = windows.clampStepSeconds(rawStep);
      int points = (int) Math.ceil(period / step) + 1;

      logger.info(
          String.format(
              Locale.ROOT,
              "%-9s %10.1f %15.3f %17.3f %8d %10s",
              body.displayName(),
              period / DAY,
              rawStep / DAY,
              step / DAY,
              points,
              rawStep > MAX_STEP_SECONDS
                  ? String.format(Locale.ROOT, "x%.2f", points / (double) asked)
                  : "as asked"));
    }
  }
}
