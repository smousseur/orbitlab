package com.smousseur.orbitlab.simulation.ephemeris.service;

import static com.smousseur.orbitlab.core.SolarSystemBody.EARTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.BodySample;
import com.smousseur.orbitlab.simulation.ephemeris.SlidingWindowEphemerisBuffer;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
import com.smousseur.orbitlab.simulation.ephemeris.config.SlidingWindowConfig;
import java.util.EnumMap;
import java.util.Map;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

/** The production service answers from each body's buffer, window and grid alike. */
class BufferedEphemerisServiceTest {

  private static final AbsoluteDate ANCHOR = AbsoluteDate.J2000_EPOCH;
  private static final AbsoluteDate TOUCHDOWN = ANCHOR.shiftedBy(7.3 * 3_600.0);
  private static final AbsoluteDate FIVE_DAYS_LATER = TOUCHDOWN.shiftedBy(5 * 86_400.0);

  private static final SlidingWindowConfig.WindowPlan PLAN =
      new SlidingWindowConfig.WindowPlan(86_164.1 / 4.0, 8, 8, 2);

  private static SlidingWindowEphemerisBuffer earthWindowAt(AbsoluteDate now) {
    Map<SolarSystemBody, Double> periods = new EnumMap<>(SolarSystemBody.class);
    periods.put(EARTH, 1000.0);
    SlidingWindowEphemerisBuffer buffer =
        new SlidingWindowEphemerisBuffer(
            (body, date) ->
                new BodySample(
                    date,
                    PVCoordinates.ZERO,
                    new Rotation(
                        Vector3D.PLUS_K,
                        1e-4 * date.durationFrom(ANCHOR),
                        RotationConvention.VECTOR_OPERATOR)),
            new EphemerisConfig(10.0, 2, 2, periods),
            EARTH);
    buffer.ensureWindow(ANCHOR, now, 1.0, PLAN, true);
    return buffer;
  }

  @Test
  void aDateTheWindowNoLongerCoversIsAnsweredFromTheBodysGrid() {
    SlidingWindowEphemerisBuffer earth = earthWindowAt(FIVE_DAYS_LATER);
    Map<SolarSystemBody, SlidingWindowEphemerisBuffer> buffers =
        new EnumMap<>(SolarSystemBody.class);
    buffers.put(EARTH, earth);
    EphemerisService service = new BufferedEphemerisService(buffers);

    assertTrue(service.trySampleIcrf(EARTH, TOUCHDOWN).isEmpty());
    assertEquals(
        0.0,
        Rotation.distance(
            earth.trySampleOnGrid(TOUCHDOWN).orElseThrow().rotationIcrf(),
            service.trySampleIcrfOnGrid(EARTH, TOUCHDOWN).orElseThrow().rotationIcrf()));
  }

  @Test
  void aBodyWithoutABufferIsEmpty() {
    EphemerisService service = new BufferedEphemerisService(new EnumMap<>(SolarSystemBody.class));

    assertTrue(service.trySampleIcrf(EARTH, TOUCHDOWN).isEmpty());
    assertTrue(service.trySampleIcrfOnGrid(EARTH, TOUCHDOWN).isEmpty());
  }
}
