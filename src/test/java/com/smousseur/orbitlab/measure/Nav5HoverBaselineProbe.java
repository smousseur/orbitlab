package com.smousseur.orbitlab.measure;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix4f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.OrbitlabPath;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetColors;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.engine.scene.RibbonMeshBuilder;
import com.smousseur.orbitlab.engine.scene.hover.OrbitHoverDetector;
import com.smousseur.orbitlab.engine.scene.hover.OrbitScreenProjector;
import com.smousseur.orbitlab.engine.scene.hover.ProjectedOrbit;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
import com.smousseur.orbitlab.simulation.orbit.OrbitPolicy;
import com.smousseur.orbitlab.simulation.orbit.config.OrbitWindowConfig;
import com.smousseur.orbitlab.simulation.source.DatasetEphemerisSource;
import com.smousseur.orbitlab.simulation.source.EphemerisSource;
import com.smousseur.orbitlab.states.camera.CameraOrientation;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * Baseline of the planet and orbit hover, measured before any of it reaches the screen: what
 * projecting and testing the drawn orbits costs per call, how far a subsampled polyline strays from
 * the drawn one, and what the current icon hover does to each colour. NOT a gate: asserts nothing.
 * Run with {@code -Dorbitlab.probe=true --tests '*Nav5HoverBaselineProbe*'}.
 *
 * <p><b>The orbits are the production geometry, without the application.</b> Each ring is sampled
 * the way {@code OrbitRuntimeAppState.computeOrbitSnapshot} samples it — the application's
 * ephemeris dataset relative to the parent body, falling back to Keplerian propagators outside the
 * dataset's span, over one period centred on a fixed date, at the configured point count — and
 * written by {@link RibbonMeshBuilder} exactly as {@code OrbitLineFactory} writes it. The scene
 * graph is rebuilt per view: the Moon's orbit node translated onto the Earth, the far root shifted
 * by minus the focused body's position in planet view, and the orbit of the body drawn in 3D
 * culled.
 *
 * <p><b>The verdict is fixed in advance</b>: every point if the worst view's p99 at stride 1 is at
 * most {@value #BUDGET_MS} ms, otherwise the smallest stride whose worst p99 is within that budget
 * with an error of at most {@value #ERROR_BUDGET_PX} px.
 *
 * <p><b>What it does not measure</b>: the cost inside a frame on the render thread. The projector
 * and the detector have no consumer yet; this is their CPU time in a warmed test JVM, and it
 * depends on the machine.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class Nav5HoverBaselineProbe {

  private static final Logger logger = LogManager.getLogger(Nav5HoverBaselineProbe.class);

  private static final float ENTER_PX = 8f;
  private static final float EXIT_PX = 12f;
  private static final int[] STRIDES = {1, 2, 4, 8, 16};
  private static final double BUDGET_MS = 0.5;
  private static final double ERROR_BUDGET_PX = 0.5;
  private static final int CURSORS_PER_VIEW = 1_000;
  private static final int WARM_UP_PASSES = 3;
  private static final long SEED = 42L;

  /** The factor {@code BillboardIconView.mouseEntered} passes to its {@code saturate}. */
  private static final float HOVER_SATURATION = 3f;

  /** {@code FloatingOriginAppState.PLANET_MODE_FAR_MIN}. */
  private static final float PLANET_MODE_FAR_MIN = 50_000f;

  private static final int WIDTH = (int) MeasureSupport.SCREEN_WIDTH_PX;
  private static final int HEIGHT = (int) MeasureSupport.SCREEN_HEIGHT_PX;

  private static AbsoluteDate t0;
  private static final Map<SolarSystemBody, Mesh> ribbons = new EnumMap<>(SolarSystemBody.class);
  private static final Map<SolarSystemBody, Vector3f> bodyPositions =
      new EnumMap<>(SolarSystemBody.class);
  private static final Map<SolarSystemBody, Integer> keplerianFallbacks =
      new EnumMap<>(SolarSystemBody.class);

  /** One camera framing, with the scene state the application would be in. */
  private record View(
      String name, Camera camera, Vector3f rootOffset, Set<SolarSystemBody> culled) {}

  /** Per-call timings and subsampling error of one view at one stride. */
  private record Sample(double medianMs, double p99Ms, double maxMs, double errorPx, int lost) {}

  @BeforeAll
  static void buildOrbits() {
    OrekitService.get().initialize();
    t0 = new AbsoluteDate(2026, 10, 8, 12, 0, 0.0, TimeScalesFactory.getUTC());
    OrbitWindowConfig windows = OrbitWindowConfig.defaultSolarSystem();
    EphemerisConfig ephemeris = EphemerisConfig.defaultSolarSystem();

    try (DatasetEphemerisSource source =
        new DatasetEphemerisSource(OrbitlabPath.EPHEMERIS_PATH, 32)) {
      for (SolarSystemBody body : SolarSystemBody.values()) {
        bodyPositions.put(body, heliocentricRenderPosition(source, body));
        if (body == SolarSystemBody.SUN) {
          continue;
        }
        int points = windows.bodyPoints(body);
        double period = ephemeris.orbitalPeriodSeconds(body);
        double step = OrbitPolicy.stepSeconds(period, points);
        float[] xyz = parentCentricRing(source, body, period, points, step);
        Mesh mesh = RibbonMeshBuilder.allocate(points, true, false);
        RibbonMeshBuilder.write(mesh, xyz, points, true);
        ribbons.put(body, mesh);
      }
    }
  }

  @Test
  void whatTheDetectionCosts() {
    List<View> views = views();
    OrbitHoverDetector detector = new OrbitHoverDetector(ENTER_PX, EXIT_PX);
    Random random = new Random(SEED);

    logger.info("=== HOVER / A - the scene each view hands the projector ===");
    logger.info(
        String.format(
            Locale.ROOT,
            "date %s, screen %dx%d, Keplerian fallbacks per body %s",
            t0,
            WIDTH,
            HEIGHT,
            keplerianFallbacks));
    logger.info(
        "view                   dist(units)   near        far  fov(deg)  orbits  segments"
            + "   excluded  beyond-far  on-screen");

    List<Map<SolarSystemBody, Geometry>> scenes = new ArrayList<>();
    List<float[]> cursors = new ArrayList<>();
    for (View view : views) {
      Map<SolarSystemBody, Geometry> scene = scene(view);
      scenes.add(scene);
      List<ProjectedOrbit> full = new OrbitScreenProjector().project(view.camera(), scene, 1);
      cursors.add(cursors(full, random));
      logContext(view, scene, full);
    }

    for (int pass = 0; pass < WARM_UP_PASSES; pass++) {
      for (int v = 0; v < views.size(); v++) {
        for (int stride : STRIDES) {
          time(views.get(v).camera(), scenes.get(v), stride, cursors.get(v), detector);
        }
      }
    }

    Sample[][] samples = new Sample[views.size()][STRIDES.length];
    for (int v = 0; v < views.size(); v++) {
      View view = views.get(v);
      List<ProjectedOrbit> full =
          new OrbitScreenProjector().project(view.camera(), scenes.get(v), 1);
      OrbitScreenProjector subsampler = new OrbitScreenProjector();
      for (int s = 0; s < STRIDES.length; s++) {
        double[] ms = time(view.camera(), scenes.get(v), STRIDES[s], cursors.get(v), detector);
        double[] error =
            subsamplingError(full, subsampler.project(view.camera(), scenes.get(v), STRIDES[s]));
        Arrays.sort(ms);
        samples[v][s] =
            new Sample(
                percentile(ms, 0.50),
                percentile(ms, 0.99),
                ms[ms.length - 1],
                error[0],
                (int) error[1]);
      }
    }

    logger.info("");
    logger.info(
        "=== HOVER / B - projection + detection per call (ms), and subsampling error (px) ===");
    logger.info("view                   k   median      p99      max   error(px)  lost");
    for (int v = 0; v < views.size(); v++) {
      for (int s = 0; s < STRIDES.length; s++) {
        Sample x = samples[v][s];
        logger.info(
            String.format(
                Locale.ROOT,
                "%-22s %2d %8.4f %8.4f %8.4f %11.4f %5d",
                views.get(v).name(),
                STRIDES[s],
                x.medianMs(),
                x.p99Ms(),
                x.maxMs(),
                x.errorPx(),
                x.lost()));
      }
    }

    logger.info("");
    logger.info("=== HOVER / C - verdict (worst view per stride) ===");
    logger.info("k   worst p99(ms)  worst error(px)  within budget");
    Integer chosen = null;
    for (int s = 0; s < STRIDES.length; s++) {
      double worstP99 = 0;
      double worstError = 0;
      for (int v = 0; v < views.size(); v++) {
        worstP99 = Math.max(worstP99, samples[v][s].p99Ms());
        worstError = Math.max(worstError, samples[v][s].errorPx());
      }
      boolean passes = worstP99 <= BUDGET_MS && (STRIDES[s] == 1 || worstError <= ERROR_BUDGET_PX);
      logger.info(
          String.format(
              Locale.ROOT,
              "%2d %13.4f %16.4f  %s",
              STRIDES[s],
              worstP99,
              worstError,
              passes ? "yes" : "no"));
      if (passes && chosen == null) {
        chosen = STRIDES[s];
      }
    }
    if (chosen == null) {
      logger.info("VERDICT: no stride meets the budget");
    } else if (chosen == 1) {
      logger.info("VERDICT: every point (k = 1)");
    } else {
      logger.info(String.format(Locale.ROOT, "VERDICT: one point in %d", chosen));
    }
  }

  @Test
  void whatTheCurrentHoverDoesToEachColour() {
    logger.info("=== HOVER / D - saturate(colour, 3) on the icon ring, body by body ===");
    logger.info(
        "body      at rest                 hovered                    max change  out of range");
    for (SolarSystemBody body : SolarSystemBody.values()) {
      ColorRGBA rest = PlanetColors.colorFor(body);
      ColorRGBA hovered = saturate(rest, HOVER_SATURATION);
      float change =
          Math.max(
              Math.abs(hovered.r - rest.r),
              Math.max(Math.abs(hovered.g - rest.g), Math.abs(hovered.b - rest.b)));
      List<String> outOfRange = new ArrayList<>();
      if (hovered.r < 0f || hovered.r > 1f) {
        outOfRange.add("r");
      }
      if (hovered.g < 0f || hovered.g > 1f) {
        outOfRange.add("g");
      }
      if (hovered.b < 0f || hovered.b > 1f) {
        outOfRange.add("b");
      }
      logger.info(
          String.format(
              Locale.ROOT,
              "%-9s (%.3f, %.3f, %.3f)   (%.3f, %.3f, %.3f)   %10.3f  %s",
              body.displayName(),
              rest.r,
              rest.g,
              rest.b,
              hovered.r,
              hovered.g,
              hovered.b,
              change,
              outOfRange.isEmpty() ? (change == 0f ? "- (inert)" : "-") : outOfRange));
    }
  }

  /** The four framings: whole system, inner planets, Earth view, Moon view. */
  private static List<View> views() {
    float systemDistance =
        FastMath.clamp(
            MeasureSupport.CAM.defaultDistance(),
            MeasureSupport.CAM.minDistance(),
            MeasureSupport.CAM.maxDistance());
    Set<SolarSystemBody> solarCulled = EnumSet.of(SolarSystemBody.MOON);
    return List.of(
        new View("V1 whole system", turntableCamera(systemDistance), Vector3f.ZERO, solarCulled),
        new View("V2 inner planets", turntableCamera(innerDistance()), Vector3f.ZERO, solarCulled),
        planetView("V3 Earth view", SolarSystemBody.EARTH),
        planetView("V4 Moon view", SolarSystemBody.MOON));
  }

  /**
   * The orbit camera in solar view: pivot on the Sun, default turntable orientation, no far floor.
   */
  private static Camera turntableCamera(float distance) {
    CameraOrientation orientation = CameraOrientation.defaults();
    Quaternion rotation =
        new Quaternion()
            .fromAngleAxis(orientation.yawRad(), Vector3f.UNIT_Y)
            .mult(new Quaternion().fromAngleAxis(orientation.pitchRad(), Vector3f.UNIT_X));
    Camera camera = camera(distance, 0f);
    camera.setLocation(rotation.mult(new Vector3f(0f, 0f, distance)));
    camera.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
    return camera;
  }

  /**
   * The distance at which the radius of Mars's ring subtends half the vertical field of view, found
   * by bisection since the adaptive field of view itself depends on the distance.
   */
  private static float innerDistance() {
    float radius = 0f;
    FloatBuffer positions = positions(ribbons.get(SolarSystemBody.MARS));
    for (int i = 0; i < positions.limit(); i += 3) {
      radius =
          Math.max(
              radius,
              new Vector3f(positions.get(i), positions.get(i + 1), positions.get(i + 2)).length());
    }
    float low = radius;
    float high = radius * 1_000f;
    for (int i = 0; i < 60; i++) {
      float mid = 0.5f * (low + high);
      double gap = radius / mid - Math.tan(MeasureSupport.adaptiveFovRad(mid) / 2.0);
      if (gap > 0) {
        low = mid;
      } else {
        high = mid;
      }
    }
    return 0.5f * (low + high);
  }

  /** A planet view: camera on the body's sunward side, five radii out, far floor raised. */
  private static View planetView(String name, SolarSystemBody body) {
    float distance =
        (float)
            (MeasureSupport.PLANET_FOCUS_RADII
                * PlanetRadius.radiusFor(body)
                * RenderContext.solar().unitsPerMeter());
    Vector3f focus = bodyPositions.get(body);
    Camera camera = camera(distance, PLANET_MODE_FAR_MIN);
    camera.setLocation(focus.negate().normalizeLocal().multLocal(distance));
    camera.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
    return new View(name, camera, focus.negate(), EnumSet.of(body));
  }

  private static Camera camera(float distance, float farFloor) {
    float[] nearFar = MeasureSupport.frustum(distance, farFloor);
    Camera camera = new Camera(WIDTH, HEIGHT);
    camera.setFrustumPerspective(
        (float) Math.toDegrees(MeasureSupport.adaptiveFovRad(distance)),
        MeasureSupport.SCREEN_WIDTH_PX / MeasureSupport.SCREEN_HEIGHT_PX,
        nearFar[0],
        nearFar[1]);
    return camera;
  }

  /** The far scene graph of one view, rebuilt around the shared ribbon meshes. */
  private static Map<SolarSystemBody, Geometry> scene(View view) {
    Node farRoot = new Node("farRoot");
    farRoot.setLocalTranslation(view.rootOffset());
    Map<SolarSystemBody, Geometry> orbits = new EnumMap<>(SolarSystemBody.class);
    for (Map.Entry<SolarSystemBody, Mesh> entry : ribbons.entrySet()) {
      SolarSystemBody body = entry.getKey();
      Node orbitNode = new Node("Orbit-" + body.name());
      if (body.isSatellite()) {
        orbitNode.setLocalTranslation(bodyPositions.get(body.parent()));
      }
      if (view.culled().contains(body)) {
        orbitNode.setCullHint(Spatial.CullHint.Always);
      }
      Geometry geometry = new Geometry("OrbitLine-" + body.name(), entry.getValue());
      orbitNode.attachChild(geometry);
      farRoot.attachChild(orbitNode);
      orbits.put(body, geometry);
    }
    farRoot.updateGeometricState();
    return orbits;
  }

  private static void logContext(
      View view, Map<SolarSystemBody, Geometry> scene, List<ProjectedOrbit> full) {
    Camera camera = view.camera();
    int segments = 0;
    int excluded = 0;
    int onScreen = 0;
    for (ProjectedOrbit orbit : full) {
      for (int i = 0; i < orbit.pointCount(); i++) {
        float x = orbit.x(i);
        float y = orbit.y(i);
        if (Float.isNaN(x)) {
          excluded++;
          continue;
        }
        if (i > 0 && !Float.isNaN(orbit.x(i - 1))) {
          segments++;
        }
        if (x >= 0 && x <= WIDTH && y >= 0 && y <= HEIGHT) {
          onScreen++;
        }
      }
    }
    int beyondFar = 0;
    Map<SolarSystemBody, Integer> beyondFarKeptOnScreen = new EnumMap<>(SolarSystemBody.class);
    Matrix4f world = new Matrix4f();
    Matrix4f modelView = new Matrix4f();
    int stridePerPoint = 3 * RibbonMeshBuilder.VERTICES_PER_POINT;
    for (ProjectedOrbit orbit : full) {
      Geometry geometry = scene.get(orbit.body());
      geometry.getWorldTransform().toTransformMatrix(world);
      camera.getViewMatrix().mult(world, modelView);
      FloatBuffer positions = positions(geometry.getMesh());
      for (int i = 0; i < positions.limit(); i += stridePerPoint) {
        float depth =
            -(modelView.m20 * positions.get(i)
                + modelView.m21 * positions.get(i + 1)
                + modelView.m22 * positions.get(i + 2)
                + modelView.m23);
        if (depth > camera.getFrustumFar()) {
          beyondFar++;
          float x = orbit.x(i / stridePerPoint);
          float y = orbit.y(i / stridePerPoint);
          if (x >= 0 && x <= WIDTH && y >= 0 && y <= HEIGHT) {
            beyondFarKeptOnScreen.merge(orbit.body(), 1, Integer::sum);
          }
        }
      }
    }
    logger.info(
        String.format(
            Locale.ROOT,
            "%-22s %11.4f %8.5f %10.1f %9.2f %7d %9d %10d %11d %10d   beyond-far kept on screen %s",
            view.name(),
            camera.getLocation().length(),
            camera.getFrustumNear(),
            camera.getFrustumFar(),
            Math.toDegrees(2 * Math.atan(camera.getFrustumTop() / camera.getFrustumNear())),
            full.size(),
            segments,
            excluded,
            beyondFar,
            onScreen,
            beyondFarKeptOnScreen));
  }

  /**
   * Cursor positions for one view: half drawn uniformly over the screen, half on a drawn point of
   * an orbit shaken by up to the exit band — the case where the bounding box rejects nothing.
   */
  private static float[] cursors(List<ProjectedOrbit> full, Random random) {
    List<float[]> drawn = new ArrayList<>();
    for (ProjectedOrbit orbit : full) {
      for (int i = 0; i < orbit.pointCount(); i++) {
        float x = orbit.x(i);
        float y = orbit.y(i);
        if (!Float.isNaN(x) && x >= 0 && x <= WIDTH && y >= 0 && y <= HEIGHT) {
          drawn.add(new float[] {x, y});
        }
      }
    }
    float[] cursors = new float[CURSORS_PER_VIEW * 2];
    for (int i = 0; i < CURSORS_PER_VIEW; i++) {
      if (i < CURSORS_PER_VIEW / 2 || drawn.isEmpty()) {
        cursors[i * 2] = random.nextFloat() * WIDTH;
        cursors[i * 2 + 1] = random.nextFloat() * HEIGHT;
      } else {
        float[] point = drawn.get(random.nextInt(drawn.size()));
        cursors[i * 2] = point[0] + (random.nextFloat() * 2f - 1f) * EXIT_PX;
        cursors[i * 2 + 1] = point[1] + (random.nextFloat() * 2f - 1f) * EXIT_PX;
      }
    }
    return cursors;
  }

  /**
   * One projection and one pick per cursor, timed together as a frame would run them, the target
   * carried from one call to the next as the hover state would carry it.
   */
  private static double[] time(
      Camera camera,
      Map<SolarSystemBody, Geometry> scene,
      int stride,
      float[] cursors,
      OrbitHoverDetector detector) {
    OrbitScreenProjector projector = new OrbitScreenProjector();
    double[] ms = new double[CURSORS_PER_VIEW];
    SolarSystemBody current = null;
    for (int i = 0; i < CURSORS_PER_VIEW; i++) {
      long start = System.nanoTime();
      List<ProjectedOrbit> projected = projector.project(camera, scene, stride);
      current = detector.pick(projected, cursors[i * 2], cursors[i * 2 + 1], current).orElse(null);
      ms[i] = (System.nanoTime() - start) / 1e6;
    }
    return ms;
  }

  /**
   * How far the drawn polyline strays from the subsampled one: for every drawn point on screen, its
   * distance to the subsampled polyline. Points whose every nearby subsampled segment is broken by
   * the near plane have no distance at all and are counted as lost.
   *
   * @return the largest distance in pixels, and the number of lost points
   */
  private static double[] subsamplingError(
      List<ProjectedOrbit> full, List<ProjectedOrbit> subsampled) {
    double worst = 0;
    int lost = 0;
    for (int o = 0; o < full.size(); o++) {
      ProjectedOrbit drawn = full.get(o);
      ProjectedOrbit coarse = subsampled.get(o);
      if (drawn.body() != coarse.body()) {
        throw new IllegalStateException("Orbit lists out of step at " + o);
      }
      for (int i = 0; i < drawn.pointCount(); i++) {
        float x = drawn.x(i);
        float y = drawn.y(i);
        if (Float.isNaN(x) || x < 0 || x > WIDTH || y < 0 || y > HEIGHT) {
          continue;
        }
        float distance = coarse.distanceTo(x, y);
        if (Float.isFinite(distance)) {
          worst = Math.max(worst, distance);
        } else {
          lost++;
        }
      }
    }
    return new double[] {worst, lost};
  }

  private static double percentile(double[] sorted, double p) {
    return sorted[(int) Math.min(sorted.length - 1, Math.floor(p * sorted.length))];
  }

  /**
   * One ring about the body's parent, in render units on JME axes, sampled as {@code
   * OrbitRuntimeAppState.computeOrbitSnapshot} samples it: the dataset, and outside its span a
   * Keplerian propagator for the body and one for its parent, both seeded at the window's centre.
   *
   * <p>The fallback is {@code EphemerisSource.sampleIcrfSafe} written out, so as to count how many
   * points of each ring it produced. Orekit's own JPL ephemerides are not an option here: over the
   * windows of the outer planets — 84 to 248 years — the loader re-reads its archive for every
   * cache slot, and building the rings took more than ten minutes before it was stopped.
   */
  private static float[] parentCentricRing(
      EphemerisSource source, SolarSystemBody body, double period, int points, double step) {
    OrekitService orekit = OrekitService.get();
    Frame icrf = orekit.icrf();
    SolarSystemBody parent = body.parent();
    double mu = orekit.body(parent).getGM();
    KeplerianPropagator parentPropagator =
        new KeplerianPropagator(
            new CartesianOrbit(source.sampleIcrf(parent, t0).pvIcrf(), icrf, t0, mu));
    KeplerianPropagator bodyPropagator =
        new KeplerianPropagator(
            new CartesianOrbit(source.sampleIcrf(body, t0).pvIcrf(), icrf, t0, mu));

    float[] xyz = new float[points * 3];
    AbsoluteDate t = t0.shiftedBy(-period / 2.0);
    for (int i = 0; i < points; i++) {
      Vector3D relative =
          sample(source, body, t, bodyPropagator)
              .subtract(sample(source, parent, t, parentPropagator));
      Vector3f jme =
          JmeVectorAdapter.toVector3f(
              RenderTransform.toRenderUnitsJmeAxes(relative, null, RenderContext.solar()));
      xyz[i * 3] = jme.x;
      xyz[i * 3 + 1] = jme.y;
      xyz[i * 3 + 2] = jme.z;
      t = t.shiftedBy(step);
    }
    return xyz;
  }

  private static Vector3D sample(
      EphemerisSource source,
      SolarSystemBody body,
      AbsoluteDate date,
      KeplerianPropagator fallback) {
    try {
      return source.sampleIcrf(body, date).pvIcrf().getPosition();
    } catch (OrbitlabException e) {
      keplerianFallbacks.merge(body, 1, Integer::sum);
      return fallback.propagate(date).getPVCoordinates().getPosition();
    }
  }

  private static Vector3f heliocentricRenderPosition(EphemerisSource source, SolarSystemBody body) {
    Vector3D p = source.sampleIcrf(body, t0).pvIcrf().getPosition();
    Vector3D sun = source.sampleIcrf(SolarSystemBody.SUN, t0).pvIcrf().getPosition();
    return JmeVectorAdapter.toVector3f(
        RenderTransform.toRenderUnitsJmeAxes(p.subtract(sun), null, RenderContext.solar()));
  }

  private static FloatBuffer positions(Mesh mesh) {
    return (FloatBuffer) mesh.getBuffer(VertexBuffer.Type.Position).getData();
  }

  /** Copy of {@code BillboardIconView.saturate}, which is private. */
  private static ColorRGBA saturate(ColorRGBA c, float s) {
    float gray = (c.r + c.g + c.b) / 3f;
    return new ColorRGBA(
        gray + (c.r - gray) * s, gray + (c.g - gray) * s, gray + (c.b - gray) * s, c.a);
  }
}
