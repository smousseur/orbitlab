package com.smousseur.orbitlab.simulation.ephemeris;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
import com.smousseur.orbitlab.simulation.ephemeris.config.SlidingWindowConfig;
import com.smousseur.orbitlab.simulation.source.EphemerisSource;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EphemerisWorkerTest {

  @Test
  void refreshesSlidingWindowWhenNowLeavesComfortZone_withoutSleeping() {
    AtomicInteger sourceCalls = new AtomicInteger();

    EphemerisSource src =
        (bodyId, date) -> {
          sourceCalls.incrementAndGet();
          double t = date.durationFrom(AbsoluteDate.J2000_EPOCH);
          return new BodySample(
              date, new PVCoordinates(new Vector3D(t, 0, 0), Vector3D.PLUS_I), Rotation.IDENTITY);
        };

    // Config only needed because SlidingWindowEphemerisBuffer currently requires it in ctor.
    EnumMap<SolarSystemBody, Double> dummyPeriods = new EnumMap<>(SolarSystemBody.class);
    dummyPeriods.put(SolarSystemBody.EARTH, 100.0);

    EphemerisConfig dummy = new EphemerisConfig(10.0, 2, 2, dummyPeriods);

    SlidingWindowEphemerisBuffer buffer =
        new SlidingWindowEphemerisBuffer(src, dummy, SolarSystemBody.EARTH);

    EnumMap<SolarSystemBody, Double> steps = new EnumMap<>(SolarSystemBody.class);
    steps.put(SolarSystemBody.EARTH, 1_000.0);

    SlidingWindowConfig windowCfg =
        new SlidingWindowConfig(
            10_000.0, // speedMaxAbs
            5.0, // lookaheadRealSeconds
            8, // minPointsEachSide
            20, // maxPointsEachSide (small to force short windows in test)
            0.25, // marginRatio
            steps);

    AtomicReference<AbsoluteDate> nowRef = new AtomicReference<>(AbsoluteDate.J2000_EPOCH);
    double speed = 10_000.0;

    EphemerisWorker worker =
        new EphemerisWorker(
            windowCfg,
            Map.of(SolarSystemBody.EARTH, buffer),
            nowRef::get,
            () -> speed,
            AbsoluteDate.J2000_EPOCH);

    // Tick #1: no snapshot => rebuild
    worker.tickOnceForTests();
    int callsAfterFirstRebuild = sourceCalls.get();
    assertTrue(callsAfterFirstRebuild > 0);

    Optional<SlidingWindowEphemerisBuffer.WindowInfo> w1 = buffer.windowInfo();
    assertTrue(w1.isPresent());

    // Jump "now" enough to exit comfort zone and force another rebuild
    // With maxPoints=20 and step=1000s => window end is roughly now+20000s.
    // marginPoints ~ floor(20*0.25)=5 => comfortEnd ~ end - 5000s => now+15000s.
    // So jumping by +16000s from the center should leave comfort zone.
    nowRef.set(nowRef.get().shiftedBy(16_000.0));
    worker.tickOnceForTests();

    int callsAfterSecondTick = sourceCalls.get();
    assertTrue(callsAfterSecondTick > callsAfterFirstRebuild);

    Optional<SlidingWindowEphemerisBuffer.WindowInfo> w2 = buffer.windowInfo();
    assertTrue(w2.isPresent());
    assertNotEquals(w1.get().start(), w2.get().start(), "Window start should change after rebuild");

    worker.close();
  }

  @Test
  void seekRebuildsAllBodiesImmediately() {
    AtomicInteger sourceCalls = new AtomicInteger();

    EphemerisSource src =
        (bodyId, date) -> {
          sourceCalls.incrementAndGet();
          return new BodySample(
              date, new PVCoordinates(Vector3D.ZERO, Vector3D.PLUS_I), Rotation.IDENTITY);
        };

    EnumMap<SolarSystemBody, Double> dummyPeriods = new EnumMap<>(SolarSystemBody.class);
    dummyPeriods.put(SolarSystemBody.EARTH, 100.0);
    dummyPeriods.put(SolarSystemBody.MARS, 100.0);

    EphemerisConfig dummy = new EphemerisConfig(10.0, 2, 2, dummyPeriods);

    SlidingWindowEphemerisBuffer earth =
        new SlidingWindowEphemerisBuffer(src, dummy, SolarSystemBody.EARTH);
    SlidingWindowEphemerisBuffer mars =
        new SlidingWindowEphemerisBuffer(src, dummy, SolarSystemBody.MARS);

    EnumMap<SolarSystemBody, Double> steps = new EnumMap<>(SolarSystemBody.class);
    steps.put(SolarSystemBody.EARTH, 1_000.0);
    steps.put(SolarSystemBody.MARS, 2_000.0);

    SlidingWindowConfig windowCfg = new SlidingWindowConfig(10_000.0, 5.0, 8, 20, 0.25, steps);

    AtomicReference<AbsoluteDate> nowRef = new AtomicReference<>(AbsoluteDate.J2000_EPOCH);
    EphemerisWorker worker =
        new EphemerisWorker(
            windowCfg,
            Map.of(SolarSystemBody.EARTH, earth, SolarSystemBody.MARS, mars),
            nowRef::get,
            () -> 1.0,
            AbsoluteDate.J2000_EPOCH);

    AbsoluteDate target = AbsoluteDate.J2000_EPOCH.shiftedBy(123_456.0);
    worker.onSeek(target);
    worker.tickOnceForTests();

    assertTrue(earth.windowInfo().isPresent());
    assertTrue(mars.windowInfo().isPresent());

    // Ensure windows are centered around the seek target (i.e., they include it)
    assertTrue(earth.trySampleInterpolated(target).isPresent());
    assertTrue(mars.trySampleInterpolated(target).isPresent());

    assertTrue(sourceCalls.get() > 0);
    worker.close();
  }
}
