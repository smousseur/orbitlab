package com.smousseur.orbitlab.measure;

import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.simulation.OrekitService;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * Where the two-phase transition should hand the rendered frame over from the source body to the
 * destination — derived, not chosen.
 *
 * <p>The near viewport holds exactly one globe, parked on the frame's centre ({@code REL-7}), so
 * the handover is also the moment the source's 3D model is dropped and the destination's becomes
 * possible. The question is therefore whether there is a stretch of the flight where <b>neither</b>
 * body is large enough to warrant a model — the {@code LodView} threshold being 10 px of projected
 * radius. Inside such a window the handover is visually free, and any rule that lands in it works.
 *
 * <p>The schedule assumed is the one the two-phase design implies: the camera's distance to the
 * destination, {@code r}, falls geometrically from {@code |src − dst| + srcFraming} to the
 * destination's own framing, over the same 2.5 s of {@code SMOOTHSTEP}. The camera stays on the
 * segment, so its distance to the source is {@code |src − dst| − r}.
 */
class TransitionHandoverMeasureTest {

  private static final Logger logger = LogManager.getLogger(TransitionHandoverMeasureTest.class);

  /** {@code LodView.updateScreen}: projected radius that promotes the 3D model. */
  private static final double SHOW_3D_THRESHOLD_PX = 10.0;

  private static final int FRAMES = 150;

  private static AbsoluteDate t0;
  private static Frame icrf;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    icrf = OrekitService.get().icrf();
    t0 = new AbsoluteDate(2026, 9, 3, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  void isThereAWindowWhereNeitherBodyNeedsItsModel() {
    logger.info("=== HANDOVER — the window where no globe is warranted, per transition ===");
    logger.info(
        "transition            travel(km)  source under 10px   destination over 10px   window");
    for (SolarSystemBody[] pair :
        List.of(
            new SolarSystemBody[] {SolarSystemBody.EARTH, SolarSystemBody.MARS},
            new SolarSystemBody[] {SolarSystemBody.EARTH, SolarSystemBody.MOON},
            new SolarSystemBody[] {SolarSystemBody.EARTH, SolarSystemBody.JUPITER},
            new SolarSystemBody[] {SolarSystemBody.EARTH, SolarSystemBody.PLUTO},
            new SolarSystemBody[] {SolarSystemBody.JUPITER, SolarSystemBody.SATURN},
            new SolarSystemBody[] {SolarSystemBody.MOON, SolarSystemBody.EARTH})) {
      report(pair[0], pair[1]);
    }
  }

  private void report(SolarSystemBody src, SolarSystemBody dst) {
    double srcRadius = radiusUnits(src);
    double dstRadius = radiusUnits(dst);
    double travel = position(dst).subtract(position(src)).getNorm();
    double srcFraming = MeasureSupport.PLANET_FOCUS_RADII * srcRadius;
    double dstFraming = MeasureSupport.PLANET_FOCUS_RADII * dstRadius;

    double r0 = travel + srcFraming;
    double r1 = dstFraming;

    double srcDropsAt = Double.NaN;
    double dstRisesAt = Double.NaN;
    for (int f = 0; f <= FRAMES; f++) {
      double u = smoothstep(f / (double) FRAMES);
      double r = Math.exp(Math.log(r0) + u * (Math.log(r1) - Math.log(r0)));
      double toSource = Math.max(srcFraming, travel - r);

      if (Double.isNaN(srcDropsAt)
          && MeasureSupport.projectedRadiusPx(srcRadius, toSource) < SHOW_3D_THRESHOLD_PX) {
        srcDropsAt = u;
      }
      if (Double.isNaN(dstRisesAt)
          && MeasureSupport.projectedRadiusPx(dstRadius, r) >= SHOW_3D_THRESHOLD_PX) {
        dstRisesAt = u;
      }
    }

    String window;
    if (Double.isNaN(srcDropsAt)) {
      window = "none - the source never shrinks below the threshold";
    } else if (Double.isNaN(dstRisesAt)) {
      window = "open to the end";
    } else if (dstRisesAt >= srcDropsAt) {
      window = String.format(Locale.ROOT, "FREE, u in [%.3f, %.3f]", srcDropsAt, dstRisesAt);
    } else {
      window = String.format(Locale.ROOT, "OVERLAP of %.3f in u", srcDropsAt - dstRisesAt);
    }

    logger.info(
        String.format(
            Locale.ROOT,
            "%-8s -> %-9s %11.0f %18s %23s   %s",
            src.displayName(),
            dst.displayName(),
            travel * MeasureSupport.KM_PER_UNIT,
            Double.isNaN(srcDropsAt) ? "never" : String.format(Locale.ROOT, "u=%.3f", srcDropsAt),
            Double.isNaN(dstRisesAt) ? "never" : String.format(Locale.ROOT, "u=%.3f", dstRisesAt),
            window));
  }

  /**
   * What is left of the shake once the handover fires. Before it, the camera is still placed by
   * adding a pivot of solar magnitude to its offset, so the view direction carries an error of one
   * quantum of that magnitude against the distance to the destination; after it, the pivot is the
   * origin and the error is exactly zero. The worst the flight can show is therefore the value at
   * the crossover itself.
   */
  @Test
  void whatIsLeftOfTheShakeAtTheHandover() {
    logger.info("=== HANDOVER / B — residual view-direction error at the crossover ===");
    logger.info("transition            u at crossover   dist to dst(km)   quantum(km)   error(px)");
    for (SolarSystemBody[] pair :
        List.of(
            new SolarSystemBody[] {SolarSystemBody.EARTH, SolarSystemBody.MARS},
            new SolarSystemBody[] {SolarSystemBody.EARTH, SolarSystemBody.JUPITER},
            new SolarSystemBody[] {SolarSystemBody.EARTH, SolarSystemBody.PLUTO},
            new SolarSystemBody[] {SolarSystemBody.MOON, SolarSystemBody.EARTH})) {
      SolarSystemBody src = pair[0];
      SolarSystemBody dst = pair[1];
      double srcRadius = radiusUnits(src);
      double dstRadius = radiusUnits(dst);
      Vector3D separation = position(dst).subtract(position(src));
      double travel = separation.getNorm();
      double r0 = travel + MeasureSupport.PLANET_FOCUS_RADII * srcRadius;
      double r1 = MeasureSupport.PLANET_FOCUS_RADII * dstRadius;

      // The pivot the camera is still added to before the handover: the destination expressed in
      // the source-centred frame, so its magnitude is the separation itself.
      double pivotComponent =
          Math.max(
              Math.abs(separation.getX()),
              Math.max(Math.abs(separation.getY()), Math.abs(separation.getZ())));
      double quantumKm = Math.ulp((float) pivotComponent) * MeasureSupport.KM_PER_UNIT;

      double crossoverU = Double.NaN;
      double rAtCrossover = Double.NaN;
      for (int f = 0; f <= FRAMES; f++) {
        double u = smoothstep(f / (double) FRAMES);
        double r = Math.exp(Math.log(r0) + u * (Math.log(r1) - Math.log(r0)));
        double toSource = Math.max(MeasureSupport.PLANET_FOCUS_RADII * srcRadius, travel - r);
        if (dstRadius / r >= srcRadius / toSource) {
          crossoverU = u;
          rAtCrossover = r;
          break;
        }
      }

      double errorPx =
          quantumKm
              / (rAtCrossover * MeasureSupport.KM_PER_UNIT)
              * MeasureSupport.pixelsPerRadian(MeasureSupport.adaptiveFovRad((float) rAtCrossover));
      logger.info(
          String.format(
              Locale.ROOT,
              "%-8s -> %-9s %14.3f %17.0f %13.1f %11.3f",
              src.displayName(),
              dst.displayName(),
              crossoverU,
              rAtCrossover * MeasureSupport.KM_PER_UNIT,
              quantumKm,
              errorPx));
    }
  }

  private static double smoothstep(double t) {
    return t * t * (3.0 - 2.0 * t);
  }

  private static double radiusUnits(SolarSystemBody body) {
    return PlanetRadius.radiusFor(body) * RenderContext.solar().unitsPerMeter();
  }

  private static Vector3D position(SolarSystemBody body) {
    Vector3D p = OrekitService.get().body(body).getPVCoordinates(t0, icrf).getPosition();
    Vector3D sun =
        OrekitService.get().body(SolarSystemBody.SUN).getPVCoordinates(t0, icrf).getPosition();
    return RenderTransform.toRenderUnitsJmeAxes(p.subtract(sun), null, RenderContext.solar());
  }
}
