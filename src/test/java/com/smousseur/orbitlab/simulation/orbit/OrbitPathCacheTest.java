package com.smousseur.orbitlab.simulation.orbit;

import static com.smousseur.orbitlab.core.SolarSystemBody.EARTH;
import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.ephemeris.BodySample;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
import com.smousseur.orbitlab.simulation.orbit.config.OrbitWindowConfig;
import com.smousseur.orbitlab.simulation.source.EphemerisSource;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

class OrbitPathCacheTest {

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void computesOncePerBody() throws Exception {
    AtomicInteger calls = new AtomicInteger();

    EphemerisSource src =
        (body, date) -> {
          calls.incrementAndGet();
          double t = date.durationFrom(AbsoluteDate.J2000_EPOCH);

          if (body == SolarSystemBody.SUN) {
            return new BodySample(
                AbsoluteDate.J2000_EPOCH,
                new PVCoordinates(new Vector3D(t, 0, 0), Vector3D.PLUS_I),
                Rotation.IDENTITY);
          } else {
            return new BodySample(
                AbsoluteDate.J2000_EPOCH,
                new PVCoordinates(new Vector3D(t, 1.0e7, 0), Vector3D.PLUS_J),
                Rotation.IDENTITY);
            //            return new PVCoordinates(new Vector3D(t, 1.0e7, 0), Vector3D.PLUS_J);
          }
        };

    EnumMap<SolarSystemBody, Double> periods = new EnumMap<>(SolarSystemBody.class);
    periods.put(SolarSystemBody.EARTH, 100.0);

    EphemerisConfig cfg = new EphemerisConfig(10.0, 2, 2, periods);
    OrbitWindowConfig orbitCfg =
        new OrbitWindowConfig(
            new EnumMap<>(Map.of(SolarSystemBody.EARTH, 200)), 512, 64, 2 * 86400, 0.25);

    ExecutorService exec = Executors.newSingleThreadExecutor();
    try {
      OrbitPathCache cache = new OrbitPathCache(src, cfg, orbitCfg, exec);

      CompletableFuture<OrbitPath> f1 =
          cache.getOrComputeOnePeriod(EARTH, AbsoluteDate.J2000_EPOCH);
      CompletableFuture<OrbitPath> f2 =
          cache.getOrComputeOnePeriod(EARTH, AbsoluteDate.J2000_EPOCH);

      OrbitPath p1 = f1.get(10, TimeUnit.SECONDS);
      OrbitPath p2 = f2.get(10, TimeUnit.SECONDS);

      assertSame(p1, p2);
      assertEquals(EARTH, p1.body());
      assertTrue(p1.positionsHelioMeters().size() >= 2);

      assertTrue(calls.get() < 1000);
    } finally {
      exec.shutdownNow();
      exec.awaitTermination(2, TimeUnit.SECONDS);
    }
  }
}
