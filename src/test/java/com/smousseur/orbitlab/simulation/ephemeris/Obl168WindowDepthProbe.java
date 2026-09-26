package com.smousseur.orbitlab.simulation.ephemeris;

import com.smousseur.orbitlab.core.OrbitlabPath;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
import com.smousseur.orbitlab.simulation.ephemeris.config.SlidingWindowConfig;
import com.smousseur.orbitlab.simulation.source.DatasetEphemerisSource;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * OBL-168 measurement: how far back the production sliding window reaches behind "now". NOT a gate:
 * asserts nothing. Run with {@code -Dorbitlab.probe=true --tests '*Obl168WindowDepthProbe*'}.
 *
 * <p>Drives the real buffer with the production plan exactly as {@link EphemerisWorker} does — a
 * forced full rebuild at start-up, then one {@code ensureWindow} per 200 ms tick — against an
 * instant source, so what is measured is the window policy alone.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class Obl168WindowDepthProbe {

  private static final Logger logger = LogManager.getLogger(Obl168WindowDepthProbe.class);
  private static final double TICK_REAL_SECONDS = 0.2;
  private static final double RUN_SIM_SECONDS = 30 * 86400.0;

  @Test
  void backDepthByClockSpeed() {
    SlidingWindowConfig cfg = SlidingWindowConfig.defaultSolarSystem();
    AbsoluteDate anchor = AbsoluteDate.J2000_EPOCH.shiftedBy(9_876_543.21);
    for (SolarSystemBody body : List.of(SolarSystemBody.EARTH, SolarSystemBody.SUN)) {
      for (double speed : new double[] {1, 60, 1_000, 10_000, 17_000, 18_000, 100_000, 1e6}) {
        SlidingWindowConfig.WindowPlan plan = cfg.plan(body, speed);
        SlidingWindowEphemerisBuffer buf =
            new SlidingWindowEphemerisBuffer(
                (b, d) -> new BodySample(d, PVCoordinates.ZERO, Rotation.IDENTITY),
                minimalConfig(),
                body);
        AbsoluteDate now = anchor;
        buf.ensureWindow(anchor, now, speed, plan, true);
        double afterRebuild = now.durationFrom(buf.windowInfo().orElseThrow().start());
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        AbsoluteDate firstStart = buf.windowInfo().orElseThrow().start();
        boolean repositioned = false;
        double dt = TICK_REAL_SECONDS * speed;
        for (double t = dt; t < RUN_SIM_SECONDS; t += dt) {
          now = anchor.shiftedBy(t);
          buf.ensureWindow(anchor, now, speed, plan, false);
          SlidingWindowEphemerisBuffer.WindowInfo info = buf.windowInfo().orElseThrow();
          if (!info.start().equals(firstStart)) {
            repositioned = true;
          }
          if (repositioned) {
            double depth = now.durationFrom(info.start());
            min = Math.min(min, depth);
            max = Math.max(max, depth);
          }
        }
        logger.info(
            String.format(
                Locale.ROOT,
                "OBL168 body=%s speed=%.0f step=%.1fs points=%d margin=%d"
                    + " depthAfterRebuild=%.2fh steadyMin=%.2fh steadyMax=%.2fh",
                body,
                speed,
                plan.stepSeconds(),
                plan.pointsBack(),
                plan.marginPoints(),
                afterRebuild / 3600.0,
                min / 3600.0,
                max / 3600.0));
      }
    }
  }

  /**
   * On the real dataset: plays the clock from a touchdown until well past the window's reach, and
   * reports (1) the jump of the touchdown rotation when {@code trySampleOnGrid} switches from the
   * window to the grid read, (2) the same after a seek three days past the touchdown, (3) how far
   * the old fallback — the frozen inertial point — would draw an equatorial ground point at the
   * instant the window lost the touchdown, and (4) the cost of the off-window read.
   */
  @Test
  void touchdownRotationOnTheRealDataset() {
    OrekitService.get().initialize();
    SlidingWindowConfig cfg = SlidingWindowConfig.defaultSolarSystem();
    double speed = 1_000.0;
    SlidingWindowConfig.WindowPlan plan = cfg.plan(SolarSystemBody.EARTH, speed);
    AbsoluteDate anchor = new AbsoluteDate(2026, 9, 26, 8, 17, 31.25, TimeScalesFactory.getUTC());
    AbsoluteDate touchdown = anchor.shiftedBy(1_234.5);
    Vector3D equatorPoint = new Vector3D(Constants.WGS84_EARTH_EQUATORIAL_RADIUS, 0, 0);
    try (DatasetEphemerisSource source =
        new DatasetEphemerisSource(OrbitlabPath.EPHEMERIS_PATH, 32)) {
      SlidingWindowEphemerisBuffer buf =
          new SlidingWindowEphemerisBuffer(source, minimalConfig(), SolarSystemBody.EARTH);
      buf.ensureWindow(anchor, anchor, speed, plan, true);
      Rotation lastInWindow = null;
      double maxWindowVsGrid = 0.0;
      double lostAtHours = Double.NaN;
      double jumpAtSwitch = Double.NaN;
      double frozenDriftMeters = Double.NaN;
      double maxGridDrift = 0.0;
      Rotation firstGrid = null;
      long firstOffWindowNanos = -1;
      long laterOffWindowNanos = -1;
      double dt = TICK_REAL_SECONDS * speed;
      for (double t = dt; t < 10 * 86400.0; t += dt) {
        AbsoluteDate now = anchor.shiftedBy(t);
        buf.ensureWindow(anchor, now, speed, plan, false);
        if (now.compareTo(touchdown) < 0) {
          continue;
        }
        Optional<BodySample> window = buf.trySampleInterpolated(touchdown);
        long t0 = System.nanoTime();
        Rotation grid = buf.trySampleOnGrid(touchdown).orElseThrow().rotationIcrf();
        long elapsed = System.nanoTime() - t0;
        if (firstGrid == null) {
          firstGrid = grid;
        }
        maxGridDrift = Math.max(maxGridDrift, Rotation.distance(firstGrid, grid));
        if (window.isPresent()) {
          lastInWindow = window.get().rotationIcrf();
          maxWindowVsGrid = Math.max(maxWindowVsGrid, Rotation.distance(lastInWindow, grid));
        } else if (Double.isNaN(lostAtHours)) {
          lostAtHours = now.durationFrom(touchdown) / 3600.0;
          jumpAtSwitch = Rotation.distance(lastInWindow, grid);
          firstOffWindowNanos = elapsed;
          Rotation nowRot = buf.trySampleInterpolated(now).orElseThrow().rotationIcrf();
          Vector3D bodyFixed = grid.applyInverseTo(equatorPoint);
          frozenDriftMeters = nowRot.applyTo(bodyFixed).distance(equatorPoint);
        } else if (laterOffWindowNanos < 0) {
          laterOffWindowNanos = elapsed;
        }
      }
      logger.info(
          String.format(
              Locale.ROOT,
              "OBL168 real: lostAt=%.2fh maxWindowVsGrid=%.3e rad jumpAtSwitch=%.3e rad (%.3e m)"
                  + " gridDriftOver10d=%.3e rad frozenDriftAtLoss=%.1f km"
                  + " firstOffWindowRead=%d us laterOffWindowRead=%d us",
              lostAtHours,
              maxWindowVsGrid,
              jumpAtSwitch,
              jumpAtSwitch * Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
              maxGridDrift,
              frozenDriftMeters / 1000.0,
              firstOffWindowNanos / 1000,
              laterOffWindowNanos / 1000));

      AbsoluteDate threeDaysLater = touchdown.shiftedBy(3 * 86400.0);
      SlidingWindowEphemerisBuffer seeked =
          new SlidingWindowEphemerisBuffer(source, minimalConfig(), SolarSystemBody.EARTH);
      seeked.ensureWindow(anchor, threeDaysLater, speed, plan, true);
      SlidingWindowEphemerisBuffer covering =
          new SlidingWindowEphemerisBuffer(source, minimalConfig(), SolarSystemBody.EARTH);
      covering.ensureWindow(anchor, touchdown, speed, plan, true);
      logger.info(
          String.format(
              Locale.ROOT,
              "OBL168 real seek+3d: windowHasTouchdown=%b gridVsCoveringWindow=%.3e rad",
              seeked.trySampleInterpolated(touchdown).isPresent(),
              Rotation.distance(
                  seeked.trySampleOnGrid(touchdown).orElseThrow().rotationIcrf(),
                  covering.trySampleInterpolated(touchdown).orElseThrow().rotationIcrf())));
    }
  }

  private static EphemerisConfig minimalConfig() {
    Map<SolarSystemBody, Double> periods = new EnumMap<>(SolarSystemBody.class);
    periods.put(SolarSystemBody.EARTH, 1000.0);
    return new EphemerisConfig(10.0, 2, 2, periods);
  }
}
