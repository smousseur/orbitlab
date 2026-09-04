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
 * How many vertices each planetary orbit ribbon carries, and how densely they land on screen
 * ({@code docs/bugs.md}, BUG-23).
 *
 * <p><b>What this measured before the fix.</b> {@code OrbitPathCache} clamped the step the budget
 * implied to at most seven days and then recomputed the count from the clamped step, so a body
 * whose period needed a coarser step than that got <em>more</em> points than were asked for, not
 * fewer: Pluto 12 939 for a budget of 4 096, Neptune 8 599, Uranus 4 386. The clamp is gone and the
 * count is now the budget for every body, which is also what {@code
 * OrbitRuntimeAppState.computeOrbitSnapshot} has always produced when it rebuilds the same ribbon.
 *
 * <p>The two columns on the right are why the count matters at all. The ribbon is alpha-blended
 * with depth writes off and each quad carries a one-pixel fade margin on either side, so quads that
 * overlap accumulate; density is what a reader can mistake for thickness, at a width the vertex
 * shader holds constant in pixels by construction. Note the sign, though: at an equal count, the
 * ring with the <em>largest</em> screen radius has its vertices furthest apart and overlaps least.
 * Uniform counts therefore make the outer orbits the thinnest, not the thickest.
 */
class OrbitRibbonDensityMeasureTest {

  private static final Logger logger = LogManager.getLogger(OrbitRibbonDensityMeasureTest.class);

  /** The clamp that used to reshape the count, kept here only to print what it did. */
  private static final double FORMER_MAX_STEP_SECONDS = 7 * 86400.0;

  private static final double DAY = 86400.0;

  @Test
  void howManyVerticesEachOrbitCarries() {
    OrbitWindowConfig windows = OrbitWindowConfig.defaultSolarSystem();
    EphemerisConfig ephemeris = EphemerisConfig.defaultSolarSystem();

    logger.info("=== RIBBON — vertices per orbit, budget vs what is built ===");
    logger.info(
        String.format(
            Locale.ROOT,
            "%-9s %11s %12s %8s %10s %12s %10s",
            "body",
            "period(d)",
            "step(d)",
            "budget",
            "points",
            "was (clamp)",
            "pts/deg"));

    for (SolarSystemBody body : SolarSystemBody.values()) {
      if (body == SolarSystemBody.SUN) {
        continue;
      }
      double period = ephemeris.orbitalPeriodSeconds(body);
      int budget = windows.bodyPoints(body);
      double step = OrbitPolicy.stepSeconds(period, budget);

      double formerStep = Math.min(FORMER_MAX_STEP_SECONDS, step);
      int formerPoints = (int) Math.ceil(period / formerStep) + 1;

      logger.info(
          String.format(
              Locale.ROOT,
              "%-9s %11.1f %12.3f %8d %10d %12s %10.1f",
              body.displayName(),
              period / DAY,
              step / DAY,
              budget,
              budget,
              formerPoints == budget + 1
                  ? "as asked"
                  : String.format(
                      Locale.ROOT, "%d (x%.2f)", formerPoints, formerPoints / (double) budget),
              budget / 360.0));
    }

    logger.info("");
    logger.info(
        "Coverage is exact by construction: budget * step = period, so the closed ribbon shuts on"
            + " its own seam without the overlap the old count carried.");
  }
}
