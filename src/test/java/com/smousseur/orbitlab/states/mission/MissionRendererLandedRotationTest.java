package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.ephemeris.BodySample;
import com.smousseur.orbitlab.simulation.ephemeris.service.EphemerisService;
import com.smousseur.orbitlab.simulation.ephemeris.service.EphemerisServiceRegistry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import java.util.List;
import java.util.Optional;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.PVCoordinates;

/**
 * A landed object rides the turning globe for as long as it lies there, not only while the sliding
 * ephemeris window still reaches back to its touchdown — which it stops doing 36 to 84 hours later
 * at playback speeds, or at once after a seek more than 48 hours past it.
 */
class MissionRendererLandedRotationTest {

  private static final double SPIN_RAD_PER_SECOND = 2.0 * Math.PI / 86_164.1;

  /** As far as the production window reaches either side of "now" after a seek. */
  private static final double WINDOW_HALF_SECONDS = 47.9 * 3_600.0;

  private static AbsoluteDate epoch;
  private static AbsoluteDate touchdown;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    touchdown = epoch.shiftedBy(600.0);
  }

  private static MissionEphemeris landing() {
    return new MissionEphemeris(
        List.of(
            point(epoch, new Vector3D(6_388_137.0, 0.0, 0.0), 10_000.0),
            point(touchdown, new Vector3D(6_378_137.0, 0.0, 0.0), 0.0)));
  }

  private static MissionEphemerisPoint point(
      AbsoluteDate time, Vector3D position, double altitudeMeters) {
    return new MissionEphemerisPoint(
        time,
        position,
        new Vector3D(0.0, 465.0, -50.0),
        "Reentry",
        false,
        1_000.0,
        altitudeMeters,
        TrajectoryArc.forBody(SolarSystemBody.EARTH));
  }

  /** Every body turning about ICRF Z at the Earth's sidereal rate, the Sun included. */
  private static BodySample spinning(AbsoluteDate date) {
    return new BodySample(
        date,
        PVCoordinates.ZERO,
        new Rotation(
            Vector3D.PLUS_K,
            SPIN_RAD_PER_SECOND * date.durationFrom(epoch),
            RotationConvention.VECTOR_OPERATOR));
  }

  /** A service whose window reaches every date. */
  private static EphemerisService unbounded() {
    return (body, t) -> Optional.of(spinning(t));
  }

  /**
   * A service whose window covers only the dates around {@code now}, yet answers any date on its
   * grid, as the production buffer does.
   */
  private static EphemerisService windowedAround(AbsoluteDate now) {
    return new EphemerisService() {
      @Override
      public Optional<BodySample> trySampleIcrf(SolarSystemBody body, AbsoluteDate t) {
        return Math.abs(t.durationFrom(now)) <= WINDOW_HALF_SECONDS
            ? Optional.of(spinning(t))
            : Optional.empty();
      }

      @Override
      public Optional<BodySample> trySampleIcrfOnGrid(SolarSystemBody body, AbsoluteDate t) {
        return Optional.of(spinning(t));
      }
    };
  }

  private static Vector3D renderedWith(EphemerisService service, AbsoluteDate now) {
    EphemerisServiceRegistry.publish(service);
    try {
      return MissionRenderer.renderedPointOf(landing(), now).position();
    } finally {
      EphemerisServiceRegistry.clear(service);
    }
  }

  @Test
  void aLandedObjectStaysOnItsGroundPointOnceTheWindowHasSlidPastItsTouchdown() {
    AbsoluteDate fiveDaysLater = touchdown.shiftedBy(5 * 86_400.0);
    EphemerisService windowed = windowedAround(fiveDaysLater);
    assertTrue(
        windowed.trySampleIcrf(SolarSystemBody.EARTH, touchdown).isEmpty(),
        "the window must have slid past the touchdown");

    Vector3D expected = renderedWith(unbounded(), fiveDaysLater);
    Vector3D actual = renderedWith(windowed, fiveDaysLater);

    assertEquals(0.0, actual.distance(expected), 1e-6, () -> "drawn at " + actual);
  }
}
