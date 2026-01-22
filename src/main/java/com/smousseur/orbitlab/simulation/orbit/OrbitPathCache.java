package com.smousseur.orbitlab.simulation.orbit;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
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
  private final OrbitPathConfig orbitPathConfig;
  private final Executor executor;

  private final ConcurrentHashMap<SolarSystemBody, CompletableFuture<OrbitPath>> cache =
      new ConcurrentHashMap<>();

  public OrbitPathCache(
      EphemerisSource pvSource,
      EphemerisConfig ephemerisConfig,
      OrbitPathConfig orbitPathConfig,
      Executor executor) {
    this.pvSource = Objects.requireNonNull(pvSource, "pvSource");
    this.ephemerisConfig = Objects.requireNonNull(ephemerisConfig, "ephemerisConfig");
    this.orbitPathConfig = Objects.requireNonNull(orbitPathConfig, "orbitPathConfig");
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

    int targetPoints = orbitPathConfig.targetPoints(body);
    double rawStep = period / targetPoints;
    double step = orbitPathConfig.clampStepSeconds(rawStep);

    int n = (int) Math.ceil(period / step) + 1;
    ArrayList<Vector3D> positionsHelio = new ArrayList<>(n + 1);

    OrekitService orekit = OrekitService.get();
    orekit.initialize();
    Frame icrf = orekit.icrf();

    // Initial heliocentric relative PV (planet - sun) at t0
    PVCoordinates pvBody0 = pvSource.sampleIcrf(body, start).pvIcrf();
    PVCoordinates pvSun0 = pvSource.sampleIcrf(SolarSystemBody.SUN, start).pvIcrf();

    PVCoordinates pvRel0 =
        new PVCoordinates(
            pvBody0.getPosition().subtract(pvSun0.getPosition()),
            pvBody0.getVelocity().subtract(pvSun0.getVelocity()));

    // Two-body Kepler propagation around the Sun
    double muSun = orekit.body(SolarSystemBody.SUN).getGM();
    Orbit relOrbit0 = new CartesianOrbit(pvRel0, icrf, start, muSun);
    KeplerianPropagator propagator = new KeplerianPropagator(relOrbit0);

    AbsoluteDate t = start;
    for (int i = 0; i < n; i++) {
      Vector3D relPos = pvSource.sampleIcrfSafe(body, t, propagator);
      positionsHelio.add(relPos);

      t = t.shiftedBy(step);
      if (t.compareTo(end) > 0) {
        break;
      }
    }

    Vector3D relEnd = pvSource.sampleIcrfSafe(body, end, propagator);
    positionsHelio.add(relEnd);

    return new OrbitPath(body, start, end, step, positionsHelio);
  }
}
