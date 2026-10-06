package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowCandidate;
import com.smousseur.orbitlab.simulation.mission.window.problem.LunarLaunchWindowProblem;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/** Probe: the lunar window criterion swept hourly over a lunation, per site. */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class LunarSiteReliefProbe {
  private static final Logger logger = LogManager.getLogger(LunarSiteReliefProbe.class);

  @Test
  void relief() {
    OrekitService.get().initialize();
    sweep("Canaveral", 28.562, -80.577, 3.0);
    sweep("Kourou", 5.236, -52.775, 0.0);
  }

  private void sweep(String name, double lat, double lon, double alt) {
    LunarLaunchWindowProblem problem =
        LunarLaunchWindowProblem.screening(lat, lon, alt, 400_000.0, 100_000.0);
    AbsoluteDate start = new AbsoluteDate(2026, 10, 6, 0, 0, 0.0, TimeScalesFactory.getUTC());
    int hours = 28 * 24;
    int under3400 = 0;
    int refused = 0;
    double minDv = Double.POSITIVE_INFINITY;
    double maxDv = 0.0;
    double sumDv = 0.0;
    AbsoluteDate firstUnder = null;
    StringBuilder daily = new StringBuilder();
    double dayMin = Double.POSITIVE_INFINITY;
    double dayMinBeta = Double.NaN;
    for (int h = 0; h < hours; h++) {
      AbsoluteDate epoch = start.shiftedBy(h * 3_600.0);
      LaunchWindowCandidate candidate = problem.evaluate(epoch);
      if (!candidate.feasible()) {
        refused++;
        continue;
      }
      double dv = candidate.deltaV();
      double beta = FastMath.toDegrees(problem.injectionAt(epoch).planeMisalignment());
      minDv = FastMath.min(minDv, dv);
      maxDv = FastMath.max(maxDv, dv);
      sumDv += dv;
      if (dv <= 3_400.0) {
        under3400++;
        if (firstUnder == null) {
          firstUnder = epoch;
        }
      }
      if (dv < dayMin) {
        dayMin = dv;
        dayMinBeta = beta;
      }
      if (h % 24 == 23) {
        daily.append(
            String.format(Locale.ROOT, " d%02d:%4.0f(β%+.1f)", h / 24, dayMin, dayMinBeta));
        dayMin = Double.POSITIVE_INFINITY;
      }
    }
    logger.info(
        String.format(
            Locale.ROOT,
            "RELIEF %-9s over %d h: <=3400 m/s in %d h (%.1f %%), refused %d, dv min %.0f /"
                + " mean %.0f / max %.0f, first under 3400 at %s",
            name,
            hours,
            under3400,
            100.0 * under3400 / hours,
            refused,
            minDv,
            sumDv / (hours - refused),
            maxDv,
            firstUnder));
    logger.info("RELIEF {} daily best:{}", name, daily);
  }
}
