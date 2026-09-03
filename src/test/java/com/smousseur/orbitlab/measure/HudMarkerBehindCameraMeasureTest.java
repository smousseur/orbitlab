package com.smousseur.orbitlab.measure;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import com.smousseur.orbitlab.simulation.OrekitService;
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
 * The solar system appearing <i>behind</i> Pluto, as icons without their orbits, while the camera
 * looks at Pluto's lit face — that is, while the whole system is behind the camera.
 *
 * <p>The orbits are scene geometry and are correctly frustum-culled; the icons are GUI overlays
 * placed by {@code BillboardIconView.updateScreenPosition}, which projects the anchor with {@code
 * Camera.getScreenCoordinates} and rejects the result on {@code screen.z < 0 || screen.z > 1}. A
 * point behind the camera projects mirrored, and lands on screen if — and only if — that guard
 * fails to see it.
 *
 * <p>This class asks the two questions the symptom raises: what {@code z} a behind-camera point
 * actually returns, and what changes with the zoom, since the view is reported correct until one
 * zooms in far enough.
 */
class HudMarkerBehindCameraMeasureTest {

  private static final Logger logger = LogManager.getLogger(HudMarkerBehindCameraMeasureTest.class);

  /** {@code FloatingOriginAppState.PLANET_MODE_FAR_MIN}. */
  private static final float PLANET_MODE_FAR_MIN = 50_000f;

  private static AbsoluteDate t0;
  private static Frame icrf;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    icrf = OrekitService.get().icrf();
    t0 = new AbsoluteDate(2026, 9, 3, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  /**
   * The near plane as the camera closes in, which is the only thing the zoom changes once {@code d
   * × nearFactor} has sunk below {@code nearMin}.
   */
  @Test
  void whatTheZoomChanges() {
    logger.info("=== GHOST / A — the near plane against the camera distance, in planet view ===");
    logger.info("dist(km)      near        far        which clamp");
    for (double km :
        new double[] {
          100_000, 31_891, 20_000, 12_199, 11_000, 10_500, 10_000, 8_687, 5_942, 3_000
        }) {
      float d = (float) (km / MeasureSupport.KM_PER_UNIT);
      float[] nf = MeasureSupport.frustum(d, PLANET_MODE_FAR_MIN);
      String clamp;
      if (nf[0] == MeasureSupport.CAM.nearMin()) {
        clamp = "nearMin";
      } else if (nf[0] == 0.0001f) {
        clamp = "KEEP PIVOT VISIBLE (near = 1e-4)";
      } else {
        clamp = "d x nearFactor";
      }
      logger.info(String.format(Locale.ROOT, "%9.0f %11.6f %10.0f    %s", km, nf[0], nf[1], clamp));
    }
  }

  /**
   * What {@code getScreenCoordinates} returns for the Sun and the planets while the camera sits on
   * Pluto's sunward side, looking at it — so that every one of them is behind the camera.
   */
  @Test
  void whatTheGuardSees() {
    Vector3f plutoWorld = renderPosition(SolarSystemBody.PLUTO);
    double radiusUnits =
        PlanetRadius.radiusFor(SolarSystemBody.PLUTO) * RenderContext.solar().unitsPerMeter();

    for (double radii : new double[] {20.0, 10.0, MeasureSupport.PLANET_FOCUS_RADII, 2.0}) {
      float d = (float) (radii * radiusUnits);
      float[] nf = MeasureSupport.frustum(d, PLANET_MODE_FAR_MIN);
      Camera cam = planetViewCamera(plutoWorld, d, nf);

      logger.info(
          String.format(
              Locale.ROOT,
              "=== GHOST / B — camera at %.0f radii (%.0f km), near = %.6f, far = %.0f ===",
              radii,
              d * MeasureSupport.KM_PER_UNIT,
              nf[0],
              nf[1]));
      logger.info("body      dist(units)  behind?   screen.z            guard z>1 ?  icon drawn");
      for (SolarSystemBody body : SolarSystemBody.values()) {
        if (body == SolarSystemBody.PLUTO) {
          continue;
        }
        // Planet view centres the far root on the focused body: every other body sits at its own
        // heliocentric render position minus Pluto's.
        Vector3f world = renderPosition(body).subtract(plutoWorld);
        Vector3f screen = cam.getScreenCoordinates(world);
        boolean behind = cam.getDirection().dot(world.subtract(cam.getLocation())) < 0;
        boolean rejected = screen.z < 0f || screen.z > 1f;
        logger.info(
            String.format(
                Locale.ROOT,
                "%-9s %11.0f %8s   %-19s %-12s %s",
                body.displayName(),
                world.length(),
                behind ? "YES" : "no",
                Float.toString(screen.z),
                rejected ? "rejected" : "PASSES",
                rejected ? "-" : "*** ON SCREEN ***"));
      }
    }
  }

  /**
   * The threshold itself: the depth a behind-camera point returns is {@code 1 + 2·near/dist} to
   * first order, and {@code 1f} has an {@code ulp} of 1.19e-7. Below that the sum rounds back to
   * exactly 1 and the guard, which tests {@code > 1}, lets the point through.
   */
  @Test
  void whereTheGuardStopsSeparating() {
    logger.info("=== GHOST / C — the margin the guard has left, per near plane ===");
    logger.info("near        dist(units)   1 + 2*near/dist        as float        separable?");
    for (float near : new float[] {0.001f, 0.0001f}) {
      for (double dist : new double[] {100, 1_000, 5_324, 10_000}) {
        double exact = 1.0 + 2.0 * near / dist;
        float asFloat = (float) exact;
        logger.info(
            String.format(
                Locale.ROOT,
                "%-11.6f %11.0f   %-22.12f %-15s %s",
                near,
                dist,
                exact,
                Float.toString(asFloat),
                asFloat > 1f ? "yes" : "NO - rounds back to 1"));
      }
    }
    logger.info(String.format(Locale.ROOT, "ulp(1.0f) = %s", Float.toString(Math.ulp(1f))));
  }

  /**
   * Who leaks, focus by focus. Once the near plane sits on its 1e-4 floor, the guard separates a
   * behind-camera point only while {@code 2·near/dist} stays above half an {@code ulp} of 1 — so
   * the threshold is a <em>distance between bodies</em>, and what the zoom decides is merely
   * whether the near plane has reached that floor.
   */
  @Test
  void whoLeaksFocusByFocus() {
    double floorNear = 0.0001;
    double halfUlp = Math.ulp(1f) / 2.0;
    double thresholdUnits = 2.0 * floorNear / halfUlp;
    logger.info("=== GHOST / D — who leaks, focus by focus ===");
    logger.info(
        String.format(
            Locale.ROOT,
            "with near on its 1e-4 floor, a body leaks once it is beyond %.0f units (%.1f AU) from"
                + " the camera",
            thresholdUnits,
            thresholdUnits / 149.6));
    logger.info("focused   arrival(km)  near at arrival  bodies whose icon leaks");
    for (SolarSystemBody focus : SolarSystemBody.values()) {
      if (focus == SolarSystemBody.SUN) {
        continue;
      }
      double radiusUnits = PlanetRadius.radiusFor(focus) * RenderContext.solar().unitsPerMeter();
      float arrival = (float) (MeasureSupport.PLANET_FOCUS_RADII * radiusUnits);
      float near = MeasureSupport.frustum(arrival, PLANET_MODE_FAR_MIN)[0];
      Vector3f focusWorld = renderPosition(focus);

      StringBuilder leaking = new StringBuilder();
      int count = 0;
      for (SolarSystemBody other : SolarSystemBody.values()) {
        if (other == focus) {
          continue;
        }
        double dist = renderPosition(other).subtract(focusWorld).length();
        if (!((float) (1.0 + 2.0 * near / dist) > 1f)) {
          count++;
          if (leaking.length() > 0) {
            leaking.append(", ");
          }
          leaking.append(other.displayName());
        }
      }
      logger.info(
          String.format(
              Locale.ROOT,
              "%-9s %11.0f %16.6f  %d/10 %s",
              focus.displayName(),
              arrival * MeasureSupport.KM_PER_UNIT,
              near,
              count,
              count == 0 ? "(none)" : leaking.toString()));
    }
  }

  /** A camera on the body's sunward side, looking at it, with the given frustum. */
  private static Camera planetViewCamera(Vector3f bodyWorld, float distance, float[] nearFar) {
    Camera cam =
        new Camera((int) MeasureSupport.SCREEN_WIDTH_PX, (int) MeasureSupport.SCREEN_HEIGHT_PX);
    cam.setFrustumPerspective(
        (float) Math.toDegrees(MeasureSupport.adaptiveFovRad(distance)),
        MeasureSupport.SCREEN_WIDTH_PX / MeasureSupport.SCREEN_HEIGHT_PX,
        nearFar[0],
        nearFar[1]);
    // Sunward side: the Sun sits at -bodyWorld once the far root is centred on the body.
    Vector3f towardsSun = bodyWorld.negate().normalizeLocal();
    cam.setLocation(towardsSun.mult(distance));
    cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
    return cam;
  }

  private static Vector3f renderPosition(SolarSystemBody body) {
    Vector3D p = OrekitService.get().body(body).getPVCoordinates(t0, icrf).getPosition();
    Vector3D sun =
        OrekitService.get().body(SolarSystemBody.SUN).getPVCoordinates(t0, icrf).getPosition();
    return JmeVectorAdapter.toVector3f(
        RenderTransform.toRenderUnitsJmeAxes(p.subtract(sun), null, RenderContext.solar()));
  }
}
