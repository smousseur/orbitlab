package com.smousseur.orbitlab.simulation.ephemeris;

import static com.smousseur.orbitlab.core.SolarSystemBody.EARTH;
import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
import com.smousseur.orbitlab.simulation.ephemeris.config.SlidingWindowConfig;
import com.smousseur.orbitlab.simulation.source.EphemerisSource;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

class SlidingWindowEphemerisBufferEnsureWindowTest {

  private static EphemerisConfig minimalConfig() {
    EnumMap<SolarSystemBody, Double> periods = new EnumMap<>(SolarSystemBody.class);
    periods.put(EARTH, 1000.0);
    return new EphemerisConfig(10.0, 2, 2, periods);
  }

  private static EphemerisSource countingSource(AtomicInteger counter) {
    return (body, date) -> {
      counter.incrementAndGet();
      return new BodySample(date, PVCoordinates.ZERO, Rotation.IDENTITY);
    };
  }

  private static SlidingWindowConfig.WindowPlan plan(int back, int fwd, int margin) {
    return new SlidingWindowConfig.WindowPlan(10.0, back, fwd, margin);
  }

  // --- forceFullRebuild ---

  @Test
  void forceFullRebuild_true_alwaysRebuilds() {
    AtomicInteger calls = new AtomicInteger();
    SlidingWindowEphemerisBuffer buf =
        new SlidingWindowEphemerisBuffer(countingSource(calls), minimalConfig(), EARTH);
    SlidingWindowConfig.WindowPlan p = plan(10, 10, 4);
    AbsoluteDate anchor = AbsoluteDate.J2000_EPOCH;

    buf.ensureWindow(anchor, anchor.shiftedBy(100), 1.0, p, true);
    int firstBuild = calls.get();
    assertEquals(21, firstBuild); // 10+1+10

    buf.ensureWindow(anchor, anchor.shiftedBy(100), 1.0, p, true);
    assertEquals(firstBuild * 2, calls.get(), "forceFullRebuild should re-fetch all points");
  }

  // --- comfort zone ---

  @Test
  void nowInComfortZone_noRebuild() {
    AtomicInteger calls = new AtomicInteger();
    SlidingWindowEphemerisBuffer buf =
        new SlidingWindowEphemerisBuffer(countingSource(calls), minimalConfig(), EARTH);
    SlidingWindowConfig.WindowPlan p = plan(10, 10, 4);
    AbsoluteDate anchor = AbsoluteDate.J2000_EPOCH;
    AbsoluteDate now = anchor.shiftedBy(100);

    // First build (s == null → full rebuild)
    buf.ensureWindow(anchor, now, 1.0, p, false);
    int afterFirstBuild = calls.get();

    // Second call with same 'now' (still in comfort zone [40,160] with center=100)
    buf.ensureWindow(anchor, now, 1.0, p, false);
    assertEquals(afterFirstBuild, calls.get(), "In comfort zone: no source call expected");
  }

  @Test
  void nowInComfortZone_windowUnchanged() {
    SlidingWindowEphemerisBuffer buf =
        new SlidingWindowEphemerisBuffer(
            (body, date) -> new BodySample(date, PVCoordinates.ZERO, Rotation.IDENTITY),
            minimalConfig(),
            EARTH);
    SlidingWindowConfig.WindowPlan p = plan(10, 10, 4);
    AbsoluteDate anchor = AbsoluteDate.J2000_EPOCH;
    AbsoluteDate now = anchor.shiftedBy(100);

    buf.ensureWindow(anchor, now, 1.0, p, false);
    SlidingWindowEphemerisBuffer.WindowInfo before = buf.windowInfo().orElseThrow();

    buf.ensureWindow(anchor, now, 1.0, p, false);
    SlidingWindowEphemerisBuffer.WindowInfo after = buf.windowInfo().orElseThrow();

    assertEquals(before.start(), after.start());
    assertEquals(before.end(), after.end());
  }

  // --- comfort zone exit → rebuild ---

  @Test
  void nowOutsideComfortZone_triggersRebuild() {
    AtomicInteger calls = new AtomicInteger();
    SlidingWindowEphemerisBuffer buf =
        new SlidingWindowEphemerisBuffer(countingSource(calls), minimalConfig(), EARTH);
    SlidingWindowConfig.WindowPlan p = plan(10, 10, 4);
    AbsoluteDate anchor = AbsoluteDate.J2000_EPOCH;

    // Build at center=100 → comfort zone [40, 160]
    buf.ensureWindow(anchor, anchor.shiftedBy(100), 1.0, p, false);
    calls.set(0);

    // Move to t=200, well outside comfort zone → should trigger a rebuild
    buf.ensureWindow(anchor, anchor.shiftedBy(200), 1.0, p, false);
    assertTrue(calls.get() > 0, "Moving outside comfort zone should trigger source calls");
  }

  @Test
  void nowOutsideComfortZone_windowMovesToCoverNow() {
    SlidingWindowEphemerisBuffer buf =
        new SlidingWindowEphemerisBuffer(
            (body, date) -> new BodySample(date, PVCoordinates.ZERO, Rotation.IDENTITY),
            minimalConfig(),
            EARTH);
    SlidingWindowConfig.WindowPlan p = plan(10, 10, 4);
    AbsoluteDate anchor = AbsoluteDate.J2000_EPOCH;
    AbsoluteDate far = anchor.shiftedBy(5000);

    buf.ensureWindow(anchor, anchor.shiftedBy(100), 1.0, p, false);
    buf.ensureWindow(anchor, far, 1.0, p, false);

    SlidingWindowEphemerisBuffer.WindowInfo info = buf.windowInfo().orElseThrow();
    assertTrue(
        far.compareTo(info.start()) >= 0 && far.compareTo(info.end()) <= 0,
        "New window should contain 'now'");
  }

  // --- plan change → full rebuild ---

  @Test
  void planChange_stepDiffers_triggersFullRebuild() {
    AtomicInteger calls = new AtomicInteger();
    SlidingWindowEphemerisBuffer buf =
        new SlidingWindowEphemerisBuffer(countingSource(calls), minimalConfig(), EARTH);
    AbsoluteDate anchor = AbsoluteDate.J2000_EPOCH;
    AbsoluteDate now = anchor.shiftedBy(100);

    SlidingWindowConfig.WindowPlan p1 = new SlidingWindowConfig.WindowPlan(10.0, 10, 10, 4);
    buf.ensureWindow(anchor, now, 1.0, p1, false);
    calls.set(0);

    // Different step → plan incompatible → full rebuild
    SlidingWindowConfig.WindowPlan p2 = new SlidingWindowConfig.WindowPlan(20.0, 10, 10, 4);
    buf.ensureWindow(anchor, now, 1.0, p2, false);
    assertEquals(21, calls.get(), "Step change should trigger full rebuild of 21 points");
  }

  // --- sliding (partial rebuild) ---

  @Test
  void smallShiftBackward_slidesPartially_fewerSourceCalls() {
    AtomicInteger calls = new AtomicInteger();
    EphemerisSource src =
        (body, date) -> {
          calls.incrementAndGet();
          double t = date.durationFrom(AbsoluteDate.J2000_EPOCH);
          return new BodySample(
              date, new PVCoordinates(new Vector3D(t, 0, 0), Vector3D.ZERO), Rotation.IDENTITY);
        };

    SlidingWindowEphemerisBuffer buf =
        new SlidingWindowEphemerisBuffer(src, minimalConfig(), EARTH);
    // plan: back=10, fwd=10, margin=4 → window=[0,200] when center=100, comfort=[40,160]
    SlidingWindowConfig.WindowPlan p = plan(10, 10, 4);
    AbsoluteDate anchor = AbsoluteDate.J2000_EPOCH;

    // Initial build: center=100 → start=0, 21 points fetched
    buf.ensureWindow(anchor, anchor.shiftedBy(100), 1.0, p, false);
    assertEquals(21, calls.get());

    // Move 'now' to t=20 (left of comfort zone start at 40)
    // targetIndex = 10 - 4 = 6, startCandidate = 20 - 60 = -40 → snapped to -40
    // shiftSteps = -4 → |4| <= margin(4) → SLIDE, only 4 new points fetched
    calls.set(0);
    buf.ensureWindow(anchor, anchor.shiftedBy(20), 1.0, p, false);
    assertEquals(4, calls.get(), "Small backward shift should only fetch new edge points");
  }

  @Test
  void sliding_preservesInterpolatedValues_inOverlapRegion() {
    // Build a source with distinct, time-varying positions
    EphemerisSource src =
        (body, date) -> {
          double t = date.durationFrom(AbsoluteDate.J2000_EPOCH);
          return new BodySample(
              date,
              new PVCoordinates(new Vector3D(t * 10, t * 20, 0), new Vector3D(10, 20, 0)),
              Rotation.IDENTITY);
        };

    SlidingWindowEphemerisBuffer buf =
        new SlidingWindowEphemerisBuffer(src, minimalConfig(), EARTH);
    SlidingWindowConfig.WindowPlan p = plan(10, 10, 4);
    AbsoluteDate anchor = AbsoluteDate.J2000_EPOCH;

    // Build at center=100: window=[0,200]
    buf.ensureWindow(anchor, anchor.shiftedBy(100), 1.0, p, false);

    // Query a point in the overlap region before the slide (t=80)
    AbsoluteDate queryT = anchor.shiftedBy(80);
    double xBefore = buf.trySampleInterpolated(queryT).orElseThrow().pvIcrf().getPosition().getX();

    // Slide backward (now=20)
    buf.ensureWindow(anchor, anchor.shiftedBy(20), 1.0, p, false);

    // After slide, new window: [-40, 160]. t=80 is still in window
    double xAfter = buf.trySampleInterpolated(queryT).orElseThrow().pvIcrf().getPosition().getX();

    assertEquals(xBefore, xAfter, 1e-6, "Overlap region values should be preserved after slide");
  }
}
