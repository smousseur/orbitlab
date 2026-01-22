package com.smousseur.orbitlab.simulation.ephemeris;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
import com.smousseur.orbitlab.simulation.source.EphemerisSource;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

import java.util.EnumMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.smousseur.orbitlab.core.SolarSystemBody.EARTH;
import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowEphemerisBufferTest {

  @Test
  void interpolatesInsideWindow() {
    EphemerisSource src =
        (bodyId, date) -> {
          double t = date.durationFrom(AbsoluteDate.J2000_EPOCH);
          Vector3D p = new Vector3D(t, 0, 0);
          Vector3D v = new Vector3D(1, 0, 0);
          return new BodySample(date, new PVCoordinates(p, v), Rotation.IDENTITY);
        };

    EnumMap<SolarSystemBody, Double> periods = new EnumMap<>(SolarSystemBody.class);
    periods.put(SolarSystemBody.EARTH, 1000.0);

    EphemerisConfig cfg = new EphemerisConfig(10.0, 5, 5, periods);

    SlidingWindowEphemerisBuffer buf = new SlidingWindowEphemerisBuffer(src, cfg, EARTH);

    AbsoluteDate center = AbsoluteDate.J2000_EPOCH.shiftedBy(100.0);
    buf.rebuildWindow(center);

    AbsoluteDate query = AbsoluteDate.J2000_EPOCH.shiftedBy(103.0);
    Optional<BodySample> s = buf.trySampleInterpolated(query);

    assertTrue(s.isPresent());
    assertEquals(103.0, s.get().pvIcrf().getPosition().getX(), 1e-9);
    assertEquals(1.0, s.get().pvIcrf().getVelocity().getX(), 1e-9);
    assertEquals(Rotation.IDENTITY.getQ0(), s.get().rotationIcrf().getQ0(), 1e-9);
    assertEquals(Rotation.IDENTITY.getQ1(), s.get().rotationIcrf().getQ1(), 1e-9);
    assertEquals(Rotation.IDENTITY.getQ2(), s.get().rotationIcrf().getQ2(), 1e-9);
    assertEquals(Rotation.IDENTITY.getQ3(), s.get().rotationIcrf().getQ3(), 1e-9);
  }

  @Test
  void returnsEmptyOutsideWindow() {
    EphemerisSource src =
        (bodyId, date) -> new BodySample(date, PVCoordinates.ZERO, Rotation.IDENTITY);

    EnumMap<SolarSystemBody, Double> periods = new EnumMap<>(SolarSystemBody.class);
    periods.put(SolarSystemBody.EARTH, 1000.0);

    EphemerisConfig cfg = new EphemerisConfig(10.0, 2, 2, periods);

    SlidingWindowEphemerisBuffer buf = new SlidingWindowEphemerisBuffer(src, cfg, EARTH);
    buf.rebuildWindow(AbsoluteDate.J2000_EPOCH);

    assertTrue(buf.trySampleInterpolated(AbsoluteDate.J2000_EPOCH.shiftedBy(10_000.0)).isEmpty());
  }

  @Test
  void sourceIsCalledExpectedNumberOfTimesForWindow() {
    AtomicInteger calls = new AtomicInteger();

    EphemerisSource src =
        (bodyId, date) -> {
          calls.incrementAndGet();
          return new BodySample(date, PVCoordinates.ZERO, Rotation.IDENTITY);
        };

    EnumMap<SolarSystemBody, Double> periods = new EnumMap<>(SolarSystemBody.class);
    periods.put(SolarSystemBody.EARTH, 1000.0);

    EphemerisConfig cfg = new EphemerisConfig(10.0, 3, 4, periods);

    SlidingWindowEphemerisBuffer buf = new SlidingWindowEphemerisBuffer(src, cfg, EARTH);
    buf.rebuildWindow(AbsoluteDate.J2000_EPOCH);

    assertEquals(cfg.windowTotalPoints(), calls.get());
  }
}
