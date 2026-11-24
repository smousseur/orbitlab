package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import java.util.ArrayList;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.bodies.BodyShape;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.models.earth.atmosphere.Atmosphere;
import org.orekit.models.earth.atmosphere.SimpleExponentialAtmosphere;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

/**
 * The Sutton-Graves heat flux a re-entry profile reads off a raw ephemeris, and the bounded
 * intensity it derives from it, on an analytic exponential atmosphere.
 */
class ReentryHeatProfileTest {

  private static final double SUTTON_GRAVES_K = 1.7415e-4;
  private static final double RHO0 = 1.225;
  private static final double SCALE_HEIGHT_M = 7_200.0;
  private static final double THRESHOLD = 1e5;
  private static final double CEILING = 1e7;

  private static final Vector3D DESCENDING = new Vector3D(-2_000.0, 7_000.0, 0.0);
  private static final Vector3D ASCENDING = new Vector3D(2_000.0, 7_000.0, 0.0);
  private static final Vector3D GRAZING = new Vector3D(0.0, 7_000.0, 0.0);
  private static final Vector3D STEEPER = new Vector3D(-3_000.0, 6_000.0, 0.0);

  private static AbsoluteDate epoch;
  private static OneAxisEllipsoid earth;
  private static Atmosphere air;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    earth = OrekitService.get().getEarthEllipsoid();
    air = new SimpleExponentialAtmosphere(earth, RHO0, 0.0, SCALE_HEIGHT_M);
  }

  @Test
  void fluxIsSuttonGravesOnTheAirRelativeSpeed() {
    MissionEphemerisPoint point = earthPoint(epoch, 60_000.0, DESCENDING);
    Frame gcrf = point.arc().frame();

    ReentryHeatProfile profile = new ReentryHeatProfile(List.of(point), air, THRESHOLD, CEILING);

    double geodeticAltitude = earth.transform(point.position(), gcrf, epoch).getAltitude();
    double density = RHO0 * Math.exp(-geodeticAltitude / SCALE_HEIGHT_M);
    double vRel =
        point.velocity().subtract(air.getVelocity(epoch, point.position(), gcrf)).getNorm();
    double expected = SUTTON_GRAVES_K * Math.sqrt(density) * vRel * vRel * vRel;
    assertEquals(expected, profile.samples().get(0).fluxWm2(), 1e-9 * expected);
  }

  @Test
  void outsideTheDomainTheFluxIsZeroAndTheAtmosphereIsNotCalled() {
    TrajectoryArc moonArc = new TrajectoryArc(SolarSystemBody.MOON, FramesFactory.getGCRF());
    List<MissionEphemerisPoint> points =
        List.of(
            earthPoint(epoch, 60_000.0, ASCENDING),
            earthPoint(epoch.shiftedBy(2.0), 130_000.0, DESCENDING),
            point(epoch.shiftedBy(4.0), 60_000.0, DESCENDING, moonArc),
            earthPoint(epoch.shiftedBy(6.0), 60_000.0, GRAZING));

    ReentryHeatProfile profile =
        new ReentryHeatProfile(
            points, new ThrowingAtmosphere(earth, Double.POSITIVE_INFINITY), THRESHOLD, CEILING);

    for (ReentryHeatProfile.HeatSample sample : profile.samples()) {
      assertEquals(0.0, sample.fluxWm2(), 0.0);
    }
    assertEquals(0, profile.atmosphereFailures());
  }

  @Test
  void noneAtmosphereGivesAnEmptyProfile() {
    MissionEphemeris ephemeris =
        new MissionEphemeris(
            List.of(
                earthPoint(epoch, 70_000.0, DESCENDING),
                earthPoint(epoch.shiftedBy(2.0), 60_000.0, DESCENDING)));

    ReentryHeatProfile profile =
        ReentryHeatProfile.forMission(ephemeris, AtmosphereModel.NONE, THRESHOLD, CEILING);

    assertTrue(profile.samples().isEmpty());
    assertEquals(0.0, profile.intensityAt(epoch), 0.0);
    assertEquals(0.0, profile.intensityAt(epoch.shiftedBy(1.0)), 0.0);
    assertEquals(0.0, profile.intensityAt(epoch.shiftedBy(2.0)), 0.0);
    assertEquals(0, profile.atmosphereFailures());
  }

  @Test
  void intensityIsZeroAtTheThresholdOneFromTheCeilingAndLogInBetween() {
    assertEquals(0.0, ReentryHeatProfile.intensityOf(1e5, 1e5, 1e7), 0.0);
    assertEquals(0.0, ReentryHeatProfile.intensityOf(5e4, 1e5, 1e7), 0.0);
    assertEquals(1.0, ReentryHeatProfile.intensityOf(1e7, 1e5, 1e7), 0.0);
    assertEquals(1.0, ReentryHeatProfile.intensityOf(3e7, 1e5, 1e7), 0.0);
    assertEquals(0.5, ReentryHeatProfile.intensityOf(1e6, 1e5, 1e7), 1e-12);
    assertEquals(
        Math.log(2.0) / Math.log(100.0), ReentryHeatProfile.intensityOf(2e5, 1e5, 1e7), 1e-12);
  }

  @Test
  void interpolationIsLinearAndZeroOutsideTheEphemeris() {
    ReentryHeatProfile profile = descentProfile();
    double i0 = profile.samples().get(0).intensity();
    double i1 = profile.samples().get(1).intensity();
    double i2 = profile.samples().get(2).intensity();
    for (double intensity : new double[] {i0, i1, i2}) {
      assertTrue(intensity > 0.0 && intensity < 1.0, "intensity " + intensity);
    }

    assertEquals(i0, profile.intensityAt(epoch), 0.0);
    assertEquals(i2, profile.intensityAt(epoch.shiftedBy(4.0)), 0.0);
    assertEquals(0.5 * (i0 + i1), profile.intensityAt(epoch.shiftedBy(1.0)), 1e-12);
    assertEquals(i0 + 0.25 * (i1 - i0), profile.intensityAt(epoch.shiftedBy(0.5)), 1e-12);
    assertEquals(0.0, profile.intensityAt(epoch.shiftedBy(-0.001)), 0.0);
    assertEquals(0.0, profile.intensityAt(epoch.shiftedBy(4.001)), 0.0);
  }

  /** Above the domain at 130 km the sample is cold; the first to glow is the one at 70 km. */
  @Test
  void theFirstHeatDateIsThatOfTheFirstGlowingSample() {
    List<MissionEphemerisPoint> points =
        List.of(
            earthPoint(epoch, 130_000.0, DESCENDING),
            earthPoint(epoch.shiftedBy(2.0), 70_000.0, DESCENDING),
            earthPoint(epoch.shiftedBy(4.0), 65_000.0, DESCENDING));

    ReentryHeatProfile profile = new ReentryHeatProfile(points, air, THRESHOLD, CEILING);

    assertEquals(0.0, profile.samples().get(0).intensity(), 0.0);
    assertTrue(profile.samples().get(1).intensity() > 0.0);
    assertEquals(epoch.shiftedBy(2.0), profile.firstHeatDate().orElseThrow());
  }

  @Test
  void aProfileThatNeverGlowsHasNoFirstHeatDate() {
    ReentryHeatProfile climbing =
        new ReentryHeatProfile(
            List.of(
                earthPoint(epoch, 60_000.0, ASCENDING),
                earthPoint(epoch.shiftedBy(2.0), 62_000.0, ASCENDING)),
            air,
            THRESHOLD,
            CEILING);
    ReentryHeatProfile empty = new ReentryHeatProfile(List.of(), air, THRESHOLD, CEILING);

    assertTrue(climbing.firstHeatDate().isEmpty());
    assertTrue(empty.firstHeatDate().isEmpty());
  }

  @Test
  void rewindingGivesTheSameValues() {
    ReentryHeatProfile profile = descentProfile();
    int count = 17;
    double[] forward = new double[count];
    for (int i = 0; i < count; i++) {
      forward[i] = profile.intensityAt(epoch.shiftedBy(-1.0 + 0.37 * i));
    }
    double[] backward = new double[count];
    for (int i = count - 1; i >= 0; i--) {
      backward[i] = profile.intensityAt(epoch.shiftedBy(-1.0 + 0.37 * i));
    }

    assertArrayEquals(forward, backward, 0.0);
  }

  @Test
  void atmosphereFailuresGiveZeroFluxAndAreCounted() {
    List<MissionEphemerisPoint> points =
        List.of(
            earthPoint(epoch, 70_000.0, DESCENDING),
            earthPoint(epoch.shiftedBy(2.0), 50_000.0, DESCENDING),
            earthPoint(epoch.shiftedBy(4.0), 40_000.0, DESCENDING));

    ReentryHeatProfile profile =
        new ReentryHeatProfile(points, new ThrowingAtmosphere(earth, 55_000.0), THRESHOLD, CEILING);

    assertTrue(profile.samples().get(0).fluxWm2() > 0.0);
    assertEquals(0.0, profile.samples().get(1).fluxWm2(), 0.0);
    assertEquals(0.0, profile.samples().get(2).fluxWm2(), 0.0);
    assertEquals(2, profile.atmosphereFailures());
  }

  @Test
  void invalidThresholdOrCeilingIsRejected() {
    List<MissionEphemerisPoint> none = List.of();
    assertThrows(IllegalArgumentException.class, () -> new ReentryHeatProfile(none, null, 0, 1e7));
    assertThrows(IllegalArgumentException.class, () -> new ReentryHeatProfile(none, null, -1, 1e7));
    assertThrows(
        IllegalArgumentException.class, () -> new ReentryHeatProfile(none, null, 1e5, 1e5));
    assertThrows(
        IllegalArgumentException.class, () -> new ReentryHeatProfile(none, null, 1e5, 5e4));
  }

  @Test
  void nonFiniteDensityIsCountedAsAFailure() {
    Atmosphere nanAir =
        new SimpleExponentialAtmosphere(earth, RHO0, 0.0, SCALE_HEIGHT_M) {
          @Override
          public double getDensity(AbsoluteDate date, Vector3D position, Frame frame) {
            return Double.NaN;
          }
        };

    ReentryHeatProfile profile =
        new ReentryHeatProfile(
            List.of(earthPoint(epoch, 60_000.0, DESCENDING)), nanAir, THRESHOLD, CEILING);

    assertEquals(0.0, profile.samples().get(0).fluxWm2(), 0.0);
    assertEquals(0.0, profile.samples().get(0).intensity(), 0.0);
    assertEquals(1, profile.atmosphereFailures());
  }

  @Test
  void equalDatesResolveToTheLowestIndex() {
    List<MissionEphemerisPoint> points =
        List.of(
            earthPoint(epoch, 65_000.0, DESCENDING),
            earthPoint(epoch, 60_000.0, DESCENDING),
            earthPoint(epoch.shiftedBy(2.0), 62_000.0, DESCENDING));

    ReentryHeatProfile profile = new ReentryHeatProfile(points, air, THRESHOLD, CEILING);
    double first = profile.samples().get(0).intensity();
    double second = profile.samples().get(1).intensity();
    double third = profile.samples().get(2).intensity();
    assertTrue(first != second, "the two samples at the same date must differ");

    assertEquals(first, profile.intensityAt(epoch), 0.0);
    assertEquals(0.5 * (second + third), profile.intensityAt(epoch.shiftedBy(1.0)), 1e-12);
  }

  @Test
  void emptyAndSinglePointProfilesAreTotal() {
    ReentryHeatProfile empty = new ReentryHeatProfile(List.of(), air, THRESHOLD, CEILING);
    assertTrue(empty.samples().isEmpty());
    assertEquals(0.0, empty.intensityAt(epoch), 0.0);
    assertEquals(0, empty.atmosphereFailures());

    ReentryHeatProfile single =
        new ReentryHeatProfile(
            List.of(earthPoint(epoch, 60_000.0, DESCENDING)), air, THRESHOLD, CEILING);
    double intensity = single.samples().get(0).intensity();
    assertTrue(intensity > 0.0);
    assertEquals(intensity, single.intensityAt(epoch), 0.0);
    assertEquals(0.0, single.intensityAt(epoch.shiftedBy(-1.0)), 0.0);
    assertEquals(0.0, single.intensityAt(epoch.shiftedBy(1.0)), 0.0);
  }

  @Test
  void axisOnAHeatingSampleIsTheAirRelativeVelocityDirection() {
    MissionEphemerisPoint point = earthPoint(epoch, 60_000.0, DESCENDING);

    ReentryHeatProfile profile = new ReentryHeatProfile(List.of(point), air, THRESHOLD, CEILING);

    Vector3D expected = airRelativeDirection(point);
    assertTrue(Vector3D.angle(expected, DESCENDING) > 1e-3, "the wind must tilt the axis");
    assertVectorEquals(expected, profile.axisAt(epoch));
  }

  @Test
  void axisOutsideTheHeatingDomainIsTheInertialVelocityDirection() {
    ReentryHeatProfile profile =
        new ReentryHeatProfile(
            List.of(earthPoint(epoch, 60_000.0, ASCENDING)), air, THRESHOLD, CEILING);

    assertVectorEquals(ASCENDING.normalize(), profile.axisAt(epoch));
  }

  @Test
  void axisWhereTheAtmosphereFailsIsTheInertialVelocityDirection() {
    ReentryHeatProfile profile =
        new ReentryHeatProfile(
            List.of(earthPoint(epoch, 50_000.0, DESCENDING)),
            new ThrowingAtmosphere(earth, 55_000.0),
            THRESHOLD,
            CEILING);

    assertEquals(1, profile.atmosphereFailures());
    assertVectorEquals(DESCENDING.normalize(), profile.axisAt(epoch));
  }

  @Test
  void aVanishingAirRelativeSpeedFallsBackToTheInertialAxis() {
    Atmosphere coMoving = windAtmosphere(DESCENDING);

    ReentryHeatProfile profile =
        new ReentryHeatProfile(
            List.of(earthPoint(epoch, 60_000.0, DESCENDING)), coMoving, THRESHOLD, CEILING);

    assertEquals(0, profile.atmosphereFailures());
    assertVectorEquals(DESCENDING.normalize(), profile.axisAt(epoch));
  }

  @Test
  void axisBetweenTwoSamplesIsTheNormalizedBlendOfTheirAxes() {
    MissionEphemerisPoint first = earthPoint(epoch, 62_000.0, DESCENDING);
    MissionEphemerisPoint second = earthPoint(epoch.shiftedBy(2.0), 60_000.0, STEEPER);

    ReentryHeatProfile profile =
        new ReentryHeatProfile(List.of(first, second), air, THRESHOLD, CEILING);

    Vector3D a0 = airRelativeDirection(first);
    Vector3D a1 = airRelativeDirection(second);
    assertVectorEquals(a0, profile.axisAt(epoch));
    assertVectorEquals(a1, profile.axisAt(epoch.shiftedBy(2.0)));
    assertVectorEquals(a0.add(a1).normalize(), profile.axisAt(epoch.shiftedBy(1.0)));
    assertVectorEquals(
        new Vector3D(0.75, a0, 0.25, a1).normalize(), profile.axisAt(epoch.shiftedBy(0.5)));
  }

  @Test
  void opposedAxesBlendToTheEarlierOne() {
    Atmosphere still = windAtmosphere(Vector3D.ZERO);
    List<MissionEphemerisPoint> points =
        List.of(
            earthPoint(epoch, 60_000.0, DESCENDING),
            earthPoint(epoch.shiftedBy(2.0), 60_000.0, DESCENDING.negate()));

    ReentryHeatProfile profile = new ReentryHeatProfile(points, still, THRESHOLD, CEILING);

    assertVectorEquals(DESCENDING.normalize(), profile.axisAt(epoch.shiftedBy(1.0)));
  }

  @Test
  void axisIsZeroOutsideTheEphemerisAndOnAnEmptyProfile() {
    ReentryHeatProfile profile = descentProfile();
    ReentryHeatProfile empty = new ReentryHeatProfile(List.of(), air, THRESHOLD, CEILING);

    assertEquals(Vector3D.ZERO, profile.axisAt(epoch.shiftedBy(-0.001)));
    assertEquals(Vector3D.ZERO, profile.axisAt(epoch.shiftedBy(4.001)));
    assertEquals(Vector3D.ZERO, empty.axisAt(epoch));
  }

  @Test
  void axisOnEqualDatesIsTheFirstSample() {
    MissionEphemerisPoint first = earthPoint(epoch, 65_000.0, DESCENDING);
    List<MissionEphemerisPoint> points =
        List.of(
            first,
            earthPoint(epoch, 60_000.0, STEEPER),
            earthPoint(epoch.shiftedBy(2.0), 62_000.0, DESCENDING));

    ReentryHeatProfile profile = new ReentryHeatProfile(points, air, THRESHOLD, CEILING);

    assertVectorEquals(airRelativeDirection(first), profile.axisAt(epoch));
  }

  @Test
  void rewindingGivesTheSameAxes() {
    List<MissionEphemerisPoint> points =
        List.of(
            earthPoint(epoch, 70_000.0, DESCENDING),
            earthPoint(epoch.shiftedBy(2.0), 65_000.0, STEEPER),
            earthPoint(epoch.shiftedBy(4.0), 60_000.0, DESCENDING));
    ReentryHeatProfile profile = new ReentryHeatProfile(points, air, THRESHOLD, CEILING);
    int count = 17;
    Vector3D[] forward = new Vector3D[count];
    for (int i = 0; i < count; i++) {
      forward[i] = profile.axisAt(epoch.shiftedBy(-1.0 + 0.37 * i));
    }
    Vector3D[] backward = new Vector3D[count];
    for (int i = count - 1; i >= 0; i--) {
      backward[i] = profile.axisAt(epoch.shiftedBy(-1.0 + 0.37 * i));
    }

    assertArrayEquals(forward, backward);
  }

  @Test
  void mayHeatNeedsOneSampleInTheHeatingDomain() {
    TrajectoryArc moonArc = new TrajectoryArc(SolarSystemBody.MOON, FramesFactory.getGCRF());
    List<MissionEphemerisPoint> outside =
        List.of(
            earthPoint(epoch, 60_000.0, ASCENDING),
            earthPoint(epoch.shiftedBy(2.0), 130_000.0, DESCENDING),
            point(epoch.shiftedBy(4.0), 60_000.0, DESCENDING, moonArc),
            earthPoint(epoch.shiftedBy(6.0), 60_000.0, GRAZING));
    List<MissionEphemerisPoint> withOneInside = new ArrayList<>(outside);
    withOneInside.add(earthPoint(epoch.shiftedBy(8.0), 60_000.0, DESCENDING));

    assertFalse(ReentryHeatProfile.mayHeat(new MissionEphemeris(outside)));
    assertTrue(ReentryHeatProfile.mayHeat(new MissionEphemeris(withOneInside)));
  }

  @Test
  void speedOnAHeatingSampleIsTheAirRelativeSpeed() {
    MissionEphemerisPoint point = earthPoint(epoch, 60_000.0, DESCENDING);

    ReentryHeatProfile profile = new ReentryHeatProfile(List.of(point), air, THRESHOLD, CEILING);

    double expected = airRelativeSpeed(point);
    assertTrue(Math.abs(expected - DESCENDING.getNorm()) > 1.0, "the wind must change the speed");
    assertEquals(expected, profile.speedAt(epoch), 1e-9);
  }

  @Test
  void speedOutsideTheHeatingDomainIsTheInertialSpeed() {
    ReentryHeatProfile profile =
        new ReentryHeatProfile(
            List.of(earthPoint(epoch, 60_000.0, ASCENDING)), air, THRESHOLD, CEILING);

    assertEquals(ASCENDING.getNorm(), profile.speedAt(epoch), 1e-9);
  }

  @Test
  void speedWhereTheAtmosphereFailsIsTheInertialSpeed() {
    ReentryHeatProfile profile =
        new ReentryHeatProfile(
            List.of(earthPoint(epoch, 50_000.0, DESCENDING)),
            new ThrowingAtmosphere(earth, 55_000.0),
            THRESHOLD,
            CEILING);

    assertEquals(1, profile.atmosphereFailures());
    assertEquals(DESCENDING.getNorm(), profile.speedAt(epoch), 1e-9);
  }

  @Test
  void aCoMovingAtmosphereGivesAZeroSpeed() {
    ReentryHeatProfile profile =
        new ReentryHeatProfile(
            List.of(earthPoint(epoch, 60_000.0, DESCENDING)),
            windAtmosphere(DESCENDING),
            THRESHOLD,
            CEILING);

    assertEquals(0.0, profile.speedAt(epoch), 0.0);
  }

  @Test
  void speedBetweenTwoSamplesIsLinear() {
    MissionEphemerisPoint first = earthPoint(epoch, 62_000.0, DESCENDING);
    MissionEphemerisPoint second = earthPoint(epoch.shiftedBy(2.0), 60_000.0, STEEPER);

    ReentryHeatProfile profile =
        new ReentryHeatProfile(List.of(first, second), air, THRESHOLD, CEILING);

    double s0 = airRelativeSpeed(first);
    double s1 = airRelativeSpeed(second);
    assertTrue(Math.abs(s1 - s0) > 1.0, "the two speeds must differ");
    assertEquals(s0, profile.speedAt(epoch), 1e-9);
    assertEquals(s1, profile.speedAt(epoch.shiftedBy(2.0)), 1e-9);
    assertEquals(0.5 * (s0 + s1), profile.speedAt(epoch.shiftedBy(1.0)), 1e-9);
    assertEquals(s0 + 0.25 * (s1 - s0), profile.speedAt(epoch.shiftedBy(0.5)), 1e-9);
  }

  @Test
  void speedIsZeroOutsideTheEphemerisAndOnAnEmptyProfile() {
    ReentryHeatProfile profile = descentProfile();
    ReentryHeatProfile empty = new ReentryHeatProfile(List.of(), air, THRESHOLD, CEILING);

    assertEquals(0.0, profile.speedAt(epoch.shiftedBy(-0.001)), 0.0);
    assertEquals(0.0, profile.speedAt(epoch.shiftedBy(4.001)), 0.0);
    assertEquals(0.0, empty.speedAt(epoch), 0.0);
  }

  @Test
  void speedOnEqualDatesIsTheFirstSample() {
    MissionEphemerisPoint first = earthPoint(epoch, 65_000.0, DESCENDING);
    List<MissionEphemerisPoint> points =
        List.of(
            first,
            earthPoint(epoch, 60_000.0, STEEPER),
            earthPoint(epoch.shiftedBy(2.0), 62_000.0, DESCENDING));

    ReentryHeatProfile profile = new ReentryHeatProfile(points, air, THRESHOLD, CEILING);

    assertEquals(airRelativeSpeed(first), profile.speedAt(epoch), 1e-9);
  }

  @Test
  void rewindingGivesTheSameSpeeds() {
    List<MissionEphemerisPoint> points =
        List.of(
            earthPoint(epoch, 70_000.0, DESCENDING),
            earthPoint(epoch.shiftedBy(2.0), 65_000.0, STEEPER),
            earthPoint(epoch.shiftedBy(4.0), 60_000.0, DESCENDING));
    ReentryHeatProfile profile = new ReentryHeatProfile(points, air, THRESHOLD, CEILING);
    int count = 17;
    double[] forward = new double[count];
    for (int i = 0; i < count; i++) {
      forward[i] = profile.speedAt(epoch.shiftedBy(-1.0 + 0.37 * i));
    }
    double[] backward = new double[count];
    for (int i = count - 1; i >= 0; i--) {
      backward[i] = profile.speedAt(epoch.shiftedBy(-1.0 + 0.37 * i));
    }

    assertArrayEquals(forward, backward, 0.0);
  }

  /** Out of the heating domain too: the plasma's shape reads it wherever the profile is read. */
  @Test
  void altitudeOnASampleIsTheAltitudeTheSampleCarries() {
    ReentryHeatProfile profile =
        new ReentryHeatProfile(
            List.of(earthPoint(epoch, 130_000.0, ASCENDING)), air, THRESHOLD, CEILING);

    assertEquals(130_000.0, profile.altitudeAt(epoch), 0.0);
  }

  @Test
  void altitudeBetweenTwoSamplesIsLinear() {
    ReentryHeatProfile profile =
        new ReentryHeatProfile(
            List.of(
                earthPoint(epoch, 62_000.0, DESCENDING),
                earthPoint(epoch.shiftedBy(2.0), 60_000.0, STEEPER)),
            air,
            THRESHOLD,
            CEILING);

    assertEquals(62_000.0, profile.altitudeAt(epoch), 1e-9);
    assertEquals(61_000.0, profile.altitudeAt(epoch.shiftedBy(1.0)), 1e-9);
    assertEquals(61_500.0, profile.altitudeAt(epoch.shiftedBy(0.5)), 1e-9);
    assertEquals(60_000.0, profile.altitudeAt(epoch.shiftedBy(2.0)), 1e-9);
  }

  @Test
  void altitudeIsZeroOutsideTheEphemerisAndOnAnEmptyProfile() {
    ReentryHeatProfile profile = descentProfile();
    ReentryHeatProfile empty = new ReentryHeatProfile(List.of(), air, THRESHOLD, CEILING);

    assertEquals(0.0, profile.altitudeAt(epoch.shiftedBy(-0.001)), 0.0);
    assertEquals(0.0, profile.altitudeAt(epoch.shiftedBy(4.001)), 0.0);
    assertEquals(0.0, empty.altitudeAt(epoch), 0.0);
  }

  @Test
  void altitudeOnEqualDatesIsTheFirstSample() {
    List<MissionEphemerisPoint> points =
        List.of(
            earthPoint(epoch, 65_000.0, DESCENDING),
            earthPoint(epoch, 60_000.0, STEEPER),
            earthPoint(epoch.shiftedBy(2.0), 62_000.0, DESCENDING));

    ReentryHeatProfile profile = new ReentryHeatProfile(points, air, THRESHOLD, CEILING);

    assertEquals(65_000.0, profile.altitudeAt(epoch), 0.0);
  }

  /** The air-relative speed of a sample, read off the test atmosphere directly. */
  private static double airRelativeSpeed(MissionEphemerisPoint point) {
    Vector3D wind = air.getVelocity(point.time(), point.position(), point.arc().frame());
    return point.velocity().subtract(wind).getNorm();
  }

  /** The unit air-relative velocity of a sample, read off the test atmosphere directly. */
  private static Vector3D airRelativeDirection(MissionEphemerisPoint point) {
    Vector3D wind = air.getVelocity(point.time(), point.position(), point.arc().frame());
    return point.velocity().subtract(wind).normalize();
  }

  private static void assertVectorEquals(Vector3D expected, Vector3D actual) {
    assertEquals(0.0, expected.distance(actual), 1e-12, () -> expected + " vs " + actual);
  }

  /** An exponential atmosphere whose wind is {@code wind} everywhere. */
  private static Atmosphere windAtmosphere(Vector3D wind) {
    return new SimpleExponentialAtmosphere(earth, RHO0, 0.0, SCALE_HEIGHT_M) {
      @Override
      public Vector3D getVelocity(AbsoluteDate date, Vector3D position, Frame frame) {
        return wind;
      }
    };
  }

  /** Three descending Earth samples two seconds apart, at 70, 65 and 60 km. */
  private static ReentryHeatProfile descentProfile() {
    List<MissionEphemerisPoint> points =
        List.of(
            earthPoint(epoch, 70_000.0, DESCENDING),
            earthPoint(epoch.shiftedBy(2.0), 65_000.0, DESCENDING),
            earthPoint(epoch.shiftedBy(4.0), 60_000.0, DESCENDING));
    return new ReentryHeatProfile(points, air, THRESHOLD, CEILING);
  }

  /**
   * An Earth sample on the GCRF x axis, {@code altitude} above the equatorial radius: the GCRF
   * equator lies within a fraction of a degree of the terrestrial one, so the geodetic altitude of
   * that position matches the altitude the sample carries to well under a metre.
   */
  private static MissionEphemerisPoint earthPoint(
      AbsoluteDate date, double altitude, Vector3D velocity) {
    return point(date, altitude, velocity, TrajectoryArc.earth());
  }

  private static MissionEphemerisPoint point(
      AbsoluteDate date, double altitude, Vector3D velocity, TrajectoryArc arc) {
    Vector3D position = new Vector3D(Constants.WGS84_EARTH_EQUATORIAL_RADIUS + altitude, 0.0, 0.0);
    return new MissionEphemerisPoint(
        date, position, velocity, "test", false, 1_000.0, altitude, arc);
  }

  /** An exponential atmosphere that throws below {@code ceilingMeters}. */
  private static final class ThrowingAtmosphere extends SimpleExponentialAtmosphere {

    private final BodyShape shape;
    private final double ceilingMeters;

    ThrowingAtmosphere(BodyShape shape, double ceilingMeters) {
      super(shape, RHO0, 0.0, SCALE_HEIGHT_M);
      this.shape = shape;
      this.ceilingMeters = ceilingMeters;
    }

    @Override
    public double getDensity(AbsoluteDate date, Vector3D position, Frame frame) {
      if (shape.transform(position, frame, date).getAltitude() < ceilingMeters) {
        throw new IllegalStateException("test atmosphere refuses this altitude");
      }
      return super.getDensity(date, position, frame);
    }
  }
}
