package com.smousseur.orbitlab.states.orbits;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

public final class OrbitRuntimeAppState extends BaseAppState {

  private static final Logger logger = LogManager.getLogger(OrbitRuntimeAppState.class);
  private final ApplicationContext context;
  private final SceneGraph.OrbitLayer orbitLayer;
  private final EnumSet<SolarSystemBody> bodies;

  private final OrbitWindowConfig windowConfig;
  private final EphemerisConfig ephemerisConfig;

  private final Map<SolarSystemBody, OrbitRuntimeSlot> slots = new EnumMap<>(SolarSystemBody.class);
  private final Map<SolarSystemBody, Long> lastAppliedVersion =
      new EnumMap<>(SolarSystemBody.class);

  private ExecutorService orbitPool;

  // Anchor for snapping (V1). You can swap it to something stable like J2000 if you prefer.
  private final AbsoluteDate snapAnchor = AbsoluteDate.J2000_EPOCH;

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
          Geometry geom = findOrbitGeometry(body);
          if (geom != null) {
            OrbitLineFactory.updateGeometryPositionsHelioMeters(geom, s.positions());
            lastAppliedVersion.put(body, s.version());
          }
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

    // Build a Kepler propagator from a PV close to center (fallback usage inside sampleIcrfSafe)
    double muSun = orekit.body(SolarSystemBody.SUN).getGM();
    PVCoordinates pvSun0 = source.sampleIcrf(SolarSystemBody.SUN, centerTc).pvIcrf();
    CartesianOrbit orbitSun = new CartesianOrbit(pvSun0, icrf, centerTc, muSun);
    KeplerianPropagator sunPropagator = new KeplerianPropagator(orbitSun);

    PVCoordinates pvBodyCenter = source.sampleIcrf(body, centerTc).pvIcrf();
    Orbit orbit0 = new CartesianOrbit(pvBodyCenter, icrf, centerTc, muSun);
    KeplerianPropagator propagator = new KeplerianPropagator(orbit0);

    Vector3D[] positions = new Vector3D[nPoints];

    AbsoluteDate t = start;
    for (int i = 0; i < nPoints; i++) {
      Vector3D pBody = source.sampleIcrfSafe(body, t, propagator);
      Vector3D pSun = source.sampleIcrfSafe(SolarSystemBody.SUN, t, sunPropagator);
      positions[i] = pBody.subtract(pSun); // heliocentric meters
      t = t.shiftedBy(stepSeconds);
    }

    return new OrbitSnapshot(body, centerTc, periodSeconds, stepSeconds, positions, version);
  }

  private Geometry findOrbitGeometry(SolarSystemBody body) {
    Node n = orbitLayer.orbitNode(body);
    if (n == null) return null;
    // OrbitInitAppState builds Geometry name "OrbitLine-" + body.name()
    com.jme3.scene.Spatial child = n.getChild("OrbitLine-" + body.name());
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
