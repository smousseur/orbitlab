package com.smousseur.orbitlab.simulation.orbit;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
import com.smousseur.orbitlab.simulation.orbit.config.OrbitWindowConfig;
import com.smousseur.orbitlab.simulation.source.EphemerisSource;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

public final class OrbitPathCache {

  private final EphemerisSource pvSource;
  private final EphemerisConfig ephemerisConfig;
  private final OrbitWindowConfig orbitWindowConfig;
  private final Executor executor;

  private final ConcurrentHashMap<SolarSystemBody, CompletableFuture<OrbitPath>> cache =
      new ConcurrentHashMap<>();

  public OrbitPathCache(
      EphemerisSource pvSource,
      EphemerisConfig ephemerisConfig,
      OrbitWindowConfig orbitWindowConfig,
      Executor executor) {
    this.pvSource = Objects.requireNonNull(pvSource, "pvSource");
    this.ephemerisConfig = Objects.requireNonNull(ephemerisConfig, "ephemerisConfig");
    this.orbitWindowConfig = Objects.requireNonNull(orbitWindowConfig, "orbitWindowConfig");
    this.executor = Objects.requireNonNull(executor, "executor");
  }

  /**
   * Returns a cached "1 orbital period" path. Computed at most once per bodyId.
   *
   * @param referenceStart fixed reference date used as the path start
   */
  public CompletableFuture<OrbitPath> getOrComputeOnePeriod(
      SolarSystemBody body, AbsoluteDate referenceStart) {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(referenceStart, "referenceStart");

    return cache.computeIfAbsent(
        body,
        id -> CompletableFuture.supplyAsync(() -> computeOnePeriod(id, referenceStart), executor));
  }

  private OrbitPath computeOnePeriod(SolarSystemBody body, AbsoluteDate start) {
    if (body == SolarSystemBody.SUN) {
      throw new IllegalArgumentException("OrbitPathCache does not support SUN orbit path");
    }

    double period = ephemerisConfig.orbitalPeriodSeconds(body);
    AbsoluteDate end = start.shiftedBy(period);

    int targetPoints = orbitWindowConfig.bodyPoints(body);
    double rawStep = period / targetPoints;
    double step = orbitWindowConfig.clampStepSeconds(rawStep);

    int n = (int) Math.ceil(period / step) + 1;
    ArrayList<Vector3D> positionsHelio = new ArrayList<>(n + 1);

    OrekitService orekit = OrekitService.get();
    orekit.initialize();
    Frame icrf = orekit.icrf();

    // Initial heliocentric relative PV (planet - sun) at t0
    PVCoordinates pvBody0 = pvSource.sampleIcrf(body, start).pvIcrf();

    PVCoordinates pvSun0 = pvSource.sampleIcrf(SolarSystemBody.SUN, start).pvIcrf();

    // Two-body Kepler propagation around the Sun as fallback computation
    double muSun = orekit.body(SolarSystemBody.SUN).getGM();
    CartesianOrbit orbitSun = new CartesianOrbit(pvSun0, icrf, start, muSun);
    Orbit relOrbit0 = new CartesianOrbit(pvBody0, icrf, start, muSun);
    KeplerianPropagator propagator = new KeplerianPropagator(relOrbit0);
    KeplerianPropagator sunPropagator = new KeplerianPropagator(orbitSun);

    // TODO only propagate from the last ephemeris point and not from start date
    AbsoluteDate t = start.shiftedBy(-step * n / 2);
    for (int i = 0; i <= n; i++) {
      Vector3D relPos = pvSource.sampleIcrfSafe(body, t, propagator);
      Vector3D pSun = pvSource.sampleIcrfSafe(SolarSystemBody.SUN, t, sunPropagator);
      positionsHelio.add(relPos.subtract(pSun));
      t = t.shiftedBy(step);
    }

    return new OrbitPath(body, start, end, step, positionsHelio);
  }
}
