package com.smousseur.orbitlab.simulation.ephemeris;

import static com.smousseur.orbitlab.core.SolarSystemBody.EARTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
import com.smousseur.orbitlab.simulation.ephemeris.config.SlidingWindowConfig;
import com.smousseur.orbitlab.simulation.source.EphemerisSource;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

/**
 * A reader that needs a body at a fixed date — a landed object needs the Earth's rotation at its
 * touchdown for as long as it lies there — gets the very sample the window would give, even once
 * the window has slid past that date.
 */
class SlidingWindowEphemerisBufferOnGridTest {

  private static final AbsoluteDate ANCHOR = AbsoluteDate.J2000_EPOCH;

  /**
   * The production Earth plan at any speed below about 17 000x: a quarter sidereal day, 8 each
   * side.
   */
  private static final SlidingWindowConfig.WindowPlan PLAN =
      new SlidingWindowConfig.WindowPlan(86_164.1 / 4.0, 8, 8, 2);

  private static final double SPIN_RAD_PER_SECOND = 2.0 * Math.PI / 86_164.1;

  /** A touchdown off the grid nodes, so the read has to interpolate. */
  private static final AbsoluteDate TOUCHDOWN = ANCHOR.shiftedBy(7.3 * 3_600.0);

  /** Five days on: well past the 83.8 h the window can reach back at most. */
  private static final AbsoluteDate FIVE_DAYS_LATER = TOUCHDOWN.shiftedBy(5 * 86_400.0);

  /** An Earth spinning about ICRF Z and drifting along X, so both interpolations have work. */
  private static final EphemerisSource SPINNING =
      (body, date) -> {
        double t = date.durationFrom(ANCHOR);
        return new BodySample(
            date,
            new PVCoordinates(new Vector3D(30_000.0 * t, 0, 0), new Vector3D(30_000.0, 0, 0)),
            new Rotation(
                Vector3D.PLUS_K, SPIN_RAD_PER_SECOND * t, RotationConvention.VECTOR_OPERATOR));
      };

  private static SlidingWindowEphemerisBuffer windowAt(EphemerisSource source, AbsoluteDate now) {
    SlidingWindowEphemerisBuffer buffer = new SlidingWindowEphemerisBuffer(source, config(), EARTH);
    buffer.ensureWindow(ANCHOR, now, 1.0, PLAN, true);
    return buffer;
  }

  private static EphemerisConfig config() {
    Map<SolarSystemBody, Double> periods = new EnumMap<>(SolarSystemBody.class);
    periods.put(EARTH, 1000.0);
    return new EphemerisConfig(10.0, 2, 2, periods);
  }

  @Test
  void outsideTheWindowItGivesWhatAWindowCoveringTheDateWould() {
    SlidingWindowEphemerisBuffer slidPast = windowAt(SPINNING, FIVE_DAYS_LATER);
    SlidingWindowEphemerisBuffer covering = windowAt(SPINNING, TOUCHDOWN);
    assertTrue(
        slidPast.trySampleInterpolated(TOUCHDOWN).isEmpty(), "the window must have slid past");

    BodySample expected = covering.trySampleInterpolated(TOUCHDOWN).orElseThrow();
    BodySample actual = slidPast.trySampleOnGrid(TOUCHDOWN).orElseThrow();

    assertEquals(0.0, Rotation.distance(expected.rotationIcrf(), actual.rotationIcrf()), 1e-12);
    assertEquals(
        0.0, expected.pvIcrf().getPosition().distance(actual.pvIcrf().getPosition()), 1e-6);
  }

  @Test
  void insideTheWindowItIsTheWindow() {
    SlidingWindowEphemerisBuffer buffer = windowAt(SPINNING, TOUCHDOWN);

    BodySample window = buffer.trySampleInterpolated(TOUCHDOWN).orElseThrow();
    BodySample onGrid = buffer.trySampleOnGrid(TOUCHDOWN).orElseThrow();

    assertEquals(0.0, Rotation.distance(window.rotationIcrf(), onGrid.rotationIcrf()));
    assertEquals(window.pvIcrf().getPosition(), onGrid.pvIcrf().getPosition());
  }

  @Test
  void aDateOutsideTheWindowIsReadFromTheSourceOnce() {
    AtomicInteger calls = new AtomicInteger();
    SlidingWindowEphemerisBuffer buffer =
        windowAt(
            (body, date) -> {
              calls.incrementAndGet();
              return SPINNING.sampleIcrf(body, date);
            },
            FIVE_DAYS_LATER);
    calls.set(0);

    for (int frame = 0; frame < 3; frame++) {
      buffer.trySampleOnGrid(TOUCHDOWN).orElseThrow();
    }

    assertEquals(2, calls.get(), "the two grid nodes around the date, once");
  }

  @Test
  void aDateTheSourceCannotSampleIsEmpty() {
    AbsoluteDate coverageStart = ANCHOR.shiftedBy(-86_400.0);
    SlidingWindowEphemerisBuffer buffer =
        windowAt(
            (body, date) -> {
              if (date.compareTo(coverageStart) < 0) {
                throw new OrbitlabException("Date outside ephemeris dataset range");
              }
              return SPINNING.sampleIcrf(body, date);
            },
            FIVE_DAYS_LATER);

    assertTrue(buffer.trySampleOnGrid(coverageStart.shiftedBy(-86_400.0)).isEmpty());
  }

  @Test
  void beforeAnyWindowIsBuiltItIsEmpty() {
    SlidingWindowEphemerisBuffer buffer =
        new SlidingWindowEphemerisBuffer(SPINNING, config(), EARTH);

    assertTrue(buffer.trySampleOnGrid(TOUCHDOWN).isEmpty());
  }
}
