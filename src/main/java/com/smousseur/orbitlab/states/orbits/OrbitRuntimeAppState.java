package com.smousseur.orbitlab.states.orbits;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.OrbitLineFactory;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
import com.smousseur.orbitlab.simulation.orbit.OrbitPolicy;
import com.smousseur.orbitlab.simulation.orbit.OrbitRuntimeSlot;
import com.smousseur.orbitlab.simulation.orbit.OrbitSnapshot;
import com.smousseur.orbitlab.simulation.orbit.config.OrbitWindowConfig;
import com.smousseur.orbitlab.simulation.source.EphemerisSource;
import com.smousseur.orbitlab.simulation.source.EphemerisSourceRegistry;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

/**
 * Application state that dynamically recomputes and updates orbital path geometries at runtime as
 * simulation time progresses.
 *
 * <p>For each tracked celestial body, maintains an {@link OrbitRuntimeSlot} that monitors whether
 * the current orbit window still covers the simulation time. When the window drifts outside a
 * comfort margin, a background job is submitted to recompute orbit positions using Keplerian
 * propagation. Completed snapshots are applied to the scene graph's orbit line geometries on the
 * render thread.
 */
public final class OrbitRuntimeAppState extends BaseAppState {

  private static final Logger logger = LogManager.getLogger(OrbitRuntimeAppState.class);
  private final ApplicationContext context;
  private final SceneGraph.OrbitLayer orbitLayer;
  private final Set<SolarSystemBody> bodies;

  private final OrbitWindowConfig windowConfig;
  private final EphemerisConfig ephemerisConfig;

  private final Map<SolarSystemBody, OrbitRuntimeSlot> slots = new EnumMap<>(SolarSystemBody.class);
  private final Map<SolarSystemBody, Long> lastAppliedVersion =
      new EnumMap<>(SolarSystemBody.class);

  /**
   * Bodies already reported as having no ribbon to write into. A snapshot that finds no geometry is
   * computed and dropped on every frame that follows, and the orbit is never drawn at all — the one
   * failure of this state that used to be entirely silent. Kept so the report is made once and not
   * sixty times a second.
   */
  private final Set<SolarSystemBody> missingGeometryReported =
      EnumSet.noneOf(SolarSystemBody.class);

  private ExecutorService orbitPool;

  // Anchor for snapping (V1). You can swap it to something stable like J2000 if you prefer.
  private final AbsoluteDate snapAnchor = AbsoluteDate.J2000_EPOCH;

  /**
   * Creates a new orbit runtime state.
   *
   * @param context the application context providing clock, scene graph, and orbit configuration
   */
  public OrbitRuntimeAppState(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
    this.orbitLayer = context.sceneGraph().orbits();
    this.bodies = context.config().orbitBodies();

    this.windowConfig = context.config().orbitWindowConfig();
    this.ephemerisConfig = context.config().ephemerisConfig();
  }

  @Override
  protected void initialize(Application app) {
    int threads = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors(), 4));
    orbitPool = Executors.newFixedThreadPool(threads);

    for (SolarSystemBody body : bodies) {
      if (body == SolarSystemBody.SUN) {
        continue;
      }
      slots.put(body, new OrbitRuntimeSlot(body));
      lastAppliedVersion.put(body, -1L);
    }
  }

  @Override
  public void update(float tpf) {
    super.update(tpf);

    AbsoluteDate now = context.clock().now();

    for (SolarSystemBody body : slots.keySet()) {
      OrbitRuntimeSlot slot = slots.get(body);

      int n = windowConfig.bodyPoints(body);
      double period = ephemerisConfig.orbitalPeriodSeconds(body);
      double step = OrbitPolicy.stepSeconds(period, n);
      double snapStep = Math.max(1.0, windowConfig.snapPoints() * step);

      double m =
          OrbitPolicy.comfortMarginSeconds(
              period,
              step,
              windowConfig.marginPoints(),
              windowConfig.minSizeSeconds(),
              windowConfig.maxFraction());

      slot.requestRebuildIfNeeded(
          now,
          s -> OrbitPolicy.isInComfort(s, now, m),
          (b, t) -> submitJob(slot, b, t, period, n, step, snapStep));

      OrbitSnapshot s = slot.snapshot();
      if (s != null) {
        long last = lastAppliedVersion.getOrDefault(body, -1L);
        if (s.version() != last) {
          applySnapshot(body, s);
        }
      }

      // For satellites, position the orbit node at the parent body's heliocentric location
      if (body.isSatellite()) {
        Spatial parentSpatial = context.getBodySpatial(body.parent());
        Node orbitNode = orbitLayer.orbitNode(body);
        if (parentSpatial != null) {
          orbitNode.setLocalTranslation(parentSpatial.getLocalTranslation());
        }
      }
    }
  }

  private void submitJob(
      OrbitRuntimeSlot slot,
      SolarSystemBody body,
      AbsoluteDate now,
      double periodSeconds,
      int nPoints,
      double stepSeconds,
      double snapStepSeconds) {

    AbsoluteDate center = OrbitPolicy.snapCenter(now, snapAnchor, snapStepSeconds);
    long version = slot.newVersion();

    orbitPool.submit(
        () -> {
          try {
            OrbitSnapshot snap =
                computeOrbitSnapshot(body, center, periodSeconds, nPoints, stepSeconds, version);
            slot.publish(snap);
          } catch (Exception e) {
            // V1: keep previous snapshot, just log.
            logger.error("Orbit runtime job failed for {}: {}", body, e.getMessage(), e);
          } finally {
            slot.endJob();
          }
        });
  }

  private OrbitSnapshot computeOrbitSnapshot(
      SolarSystemBody body,
      AbsoluteDate centerTc,
      double periodSeconds,
      int nPoints,
      double stepSeconds,
      long version) {

    EphemerisSource source =
        EphemerisSourceRegistry.get()
            .orElseThrow(() -> new OrbitlabException("Cannot get Ephemeris source"));

    OrekitService orekit = OrekitService.get();
    Frame icrf = orekit.icrf();

    // Window [Tc - P/2, Tc + P/2]
    AbsoluteDate start = centerTc.shiftedBy(-periodSeconds / 2.0);

    // Use the body's parent as orbit center (SUN for planets, parent planet for satellites)
    SolarSystemBody center = body.parent();
    double muCenter = orekit.body(center).getGM();

    PVCoordinates pvCenter0 = source.sampleIcrf(center, centerTc).pvIcrf();
    CartesianOrbit orbitCenter = new CartesianOrbit(pvCenter0, icrf, centerTc, muCenter);
    KeplerianPropagator centerPropagator = new KeplerianPropagator(orbitCenter);

    PVCoordinates pvBodyCenter = source.sampleIcrf(body, centerTc).pvIcrf();
    Orbit orbit0 = new CartesianOrbit(pvBodyCenter, icrf, centerTc, muCenter);
    KeplerianPropagator propagator = new KeplerianPropagator(orbit0);

    Vector3D[] positions = new Vector3D[nPoints];

    AbsoluteDate t = start;
    for (int i = 0; i < nPoints; i++) {
      Vector3D pBody = source.sampleIcrfSafe(body, t, propagator);
      Vector3D pCenter = source.sampleIcrfSafe(center, t, centerPropagator);
      positions[i] = pBody.subtract(pCenter); // parent-centric meters
      t = t.shiftedBy(stepSeconds);
    }

    return new OrbitSnapshot(body, centerTc, periodSeconds, stepSeconds, positions, version);
  }

  /**
   * Writes a freshly computed window into the body's ribbon, and says so.
   *
   * <p><b>The log is the evidence that the runtime window reaches the screen at all</b> ({@code
   * docs/bugs.md}, BUG-23). What {@code OrbitInitAppState} draws at startup comes from the dataset
   * file, whose point count is the generator's; this state then recomputes the same orbit at
   * exactly {@code bodyPoints} and rewrites the mesh. Reading the two vertex counts on either side
   * of that write is what distinguishes "the rebuild landed" from "the ribbon still carries what
   * the generator wrote", and the two used to be indistinguishable from outside.
   *
   * <p>It fires once per body, on the single rebuild each one gets at startup: past that the
   * comfort margin runs to decades of simulated time.
   */
  private void applySnapshot(SolarSystemBody body, OrbitSnapshot snapshot) {
    Geometry geom = findOrbitGeometry(body);
    if (geom == null) {
      if (missingGeometryReported.add(body)) {
        logger.warn(
            "No ribbon geometry named {} to receive {}'s orbit window: it will never be drawn."
                + " OrbitInitAppState skips a body whose dataset file is missing.",
            "OrbitLine-" + body.name(),
            body.displayName());
      }
      return;
    }
    int verticesBefore = geom.getMesh().getVertexCount();
    OrbitLineFactory.updateGeometryPositionsHelioMeters(geom, snapshot.positions());
    lastAppliedVersion.put(body, snapshot.version());
    logger.info(
        "Orbit ribbon for {} rebuilt from the runtime window: {} points at {} d, mesh {} -> {}"
            + " vertices (version {})",
        body.displayName(),
        snapshot.positions().length,
        String.format(java.util.Locale.ROOT, "%.3f", snapshot.stepSeconds() / 86400.0),
        verticesBefore,
        geom.getMesh().getVertexCount(),
        snapshot.version());
  }

  private Geometry findOrbitGeometry(SolarSystemBody body) {
    Node n = orbitLayer.orbitNode(body);
    if (n == null) return null;
    // OrbitInitAppState builds Geometry name "OrbitLine-" + body.name()
    Spatial child = n.getChild("OrbitLine-" + body.name());
    return (child instanceof Geometry g) ? g : null;
  }

  @Override
  protected void cleanup(Application app) {
    if (orbitPool != null) {
      orbitPool.shutdown();
      try {
        orbitPool.awaitTermination(2, TimeUnit.SECONDS);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      } finally {
        orbitPool.shutdownNow();
      }
      orbitPool = null;
    }
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
