package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.engine.ReentryFxConfig;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.mission.ephemeris.DebrisTrack;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

/**
 * A mission's heat profiles are built off the render thread, and a renderer only ever reads the
 * ones built for the very debris list and the very primary ephemeris it is drawing. A primary gets
 * one only when its trajectory ends on the ground.
 */
class MissionHeatProfilesTest {

  private static final Vector3D DESCENDING = new Vector3D(-2_000.0, 7_000.0, 0.0);
  private static final Vector3D ASCENDING = new Vector3D(2_000.0, 7_000.0, 0.0);
  private static final ReentryFxConfig CONFIG = ReentryFxConfig.defaults();

  private static AbsoluteDate epoch;

  /** A primary left in orbit, climbing through 300 km. */
  private static MissionEphemeris orbiting;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    orbiting = climbingTrack().ephemeris();
  }

  @Test
  void nothingIsServedBeforeTheComputationRuns() {
    MissionHeatProfiles profiles = new MissionHeatProfiles();
    List<DebrisTrack> debris = List.of(fallingTrack());
    QueuedExecutor executor = new QueuedExecutor();

    profiles.request(debris, orbiting, AtmosphereModel.HARRIS_PRIESTER, CONFIG, executor);

    assertTrue(profiles.profileFor(debris, 0).samples().isEmpty());
  }

  @Test
  void theProfilesOfARequestServeThatVeryList() {
    MissionHeatProfiles profiles = new MissionHeatProfiles();
    List<DebrisTrack> debris = List.of(fallingTrack());
    QueuedExecutor executor = new QueuedExecutor();

    profiles.request(debris, orbiting, AtmosphereModel.HARRIS_PRIESTER, CONFIG, executor);
    executor.runAll();

    assertEquals(3, profiles.profileFor(debris, 0).samples().size());
  }

  @Test
  void aDebrisThatNeverEntersTheHeatingDomainGetsNoProfile() {
    MissionHeatProfiles profiles = new MissionHeatProfiles();
    List<DebrisTrack> debris = List.of(fallingTrack(), climbingTrack());
    QueuedExecutor executor = new QueuedExecutor();

    profiles.request(debris, orbiting, AtmosphereModel.HARRIS_PRIESTER, CONFIG, executor);
    executor.runAll();

    assertEquals(3, profiles.profileFor(debris, 0).samples().size());
    assertTrue(profiles.profileFor(debris, 1).samples().isEmpty());
  }

  @Test
  void anEqualButDistinctListGetsNoProfile() {
    MissionHeatProfiles profiles = new MissionHeatProfiles();
    List<DebrisTrack> debris = List.of(fallingTrack());
    QueuedExecutor executor = new QueuedExecutor();

    profiles.request(debris, orbiting, AtmosphereModel.HARRIS_PRIESTER, CONFIG, executor);
    executor.runAll();

    assertTrue(profiles.profileFor(new ArrayList<>(debris), 0).samples().isEmpty());
  }

  @Test
  void aResultOvertakenByANewerRequestIsDropped() {
    MissionHeatProfiles profiles = new MissionHeatProfiles();
    List<DebrisTrack> older = List.of(fallingTrack());
    List<DebrisTrack> newer = List.of(fallingTrack());
    QueuedExecutor executor = new QueuedExecutor();

    profiles.request(older, orbiting, AtmosphereModel.HARRIS_PRIESTER, CONFIG, executor);
    profiles.request(newer, orbiting, AtmosphereModel.HARRIS_PRIESTER, CONFIG, executor);
    executor.runInReverse();

    assertEquals(3, profiles.profileFor(newer, 0).samples().size());
    assertTrue(profiles.profileFor(older, 0).samples().isEmpty());
  }

  @Test
  void aRefusedTaskLeavesTheDebrisWithoutProfile() {
    MissionHeatProfiles profiles = new MissionHeatProfiles();
    List<DebrisTrack> debris = List.of(fallingTrack());
    Executor refusing =
        task -> {
          throw new RejectedExecutionException("shut down");
        };

    assertDoesNotThrow(
        () ->
            profiles.request(debris, orbiting, AtmosphereModel.HARRIS_PRIESTER, CONFIG, refusing));
    assertTrue(profiles.profileFor(debris, 0).samples().isEmpty());
  }

  @Test
  void aPrimaryThatEndsOnTheGroundGetsItsProfile() {
    MissionHeatProfiles profiles = new MissionHeatProfiles();
    List<DebrisTrack> debris = List.of(fallingTrack());
    MissionEphemeris landing = landingPrimary();
    QueuedExecutor executor = new QueuedExecutor();

    profiles.request(debris, landing, AtmosphereModel.HARRIS_PRIESTER, CONFIG, executor);
    executor.runAll();

    assertEquals(4, profiles.primaryProfile(debris, landing).samples().size());
  }

  /** A primary left in orbit — every mission that does not deorbit — never gets one built. */
  @Test
  void aPrimaryLeftInOrbitGetsNoProfile() {
    MissionHeatProfiles profiles = new MissionHeatProfiles();
    List<DebrisTrack> debris = List.of(fallingTrack());
    QueuedExecutor executor = new QueuedExecutor();

    profiles.request(debris, orbiting, AtmosphereModel.HARRIS_PRIESTER, CONFIG, executor);
    executor.runAll();

    assertTrue(profiles.primaryProfile(debris, orbiting).samples().isEmpty());
    assertEquals(3, profiles.profileFor(debris, 0).samples().size());
  }

  @Test
  void thePrimaryProfileServesOnlyThatEphemerisAndThatList() {
    MissionHeatProfiles profiles = new MissionHeatProfiles();
    List<DebrisTrack> debris = List.of(fallingTrack());
    MissionEphemeris landing = landingPrimary();
    QueuedExecutor executor = new QueuedExecutor();

    profiles.request(debris, landing, AtmosphereModel.HARRIS_PRIESTER, CONFIG, executor);
    executor.runAll();

    assertTrue(profiles.primaryProfile(debris, landingPrimary()).samples().isEmpty());
    assertTrue(profiles.primaryProfile(new ArrayList<>(debris), landing).samples().isEmpty());
    assertEquals(4, profiles.primaryProfile(debris, landing).samples().size());
  }

  /** A mission that shed no debris still lights its falling payload. */
  @Test
  void aPrimaryWithoutDebrisStillGetsItsProfile() {
    MissionHeatProfiles profiles = new MissionHeatProfiles();
    List<DebrisTrack> none = List.of();
    MissionEphemeris landing = landingPrimary();
    QueuedExecutor executor = new QueuedExecutor();

    profiles.request(none, landing, AtmosphereModel.HARRIS_PRIESTER, CONFIG, executor);
    executor.runAll();

    assertEquals(4, profiles.primaryProfile(none, landing).samples().size());
  }

  /**
   * A payload falling through 115, 112 and 110 km, then lying on the ground 500 m up, still: its
   * trajectory ends on the ground.
   */
  private static MissionEphemeris landingPrimary() {
    return new MissionEphemeris(
        List.of(
            earthPoint(epoch, 115_000.0, DESCENDING),
            earthPoint(epoch.shiftedBy(2.0), 112_000.0, DESCENDING),
            earthPoint(epoch.shiftedBy(4.0), 110_000.0, DESCENDING),
            earthPoint(epoch.shiftedBy(6.0), 500.0, Vector3D.ZERO)));
  }

  /** A booster falling through 115, 112 and 110 km, inside the heating domain. */
  private static DebrisTrack fallingTrack() {
    return track(
        List.of(
            earthPoint(epoch, 115_000.0, DESCENDING),
            earthPoint(epoch.shiftedBy(2.0), 112_000.0, DESCENDING),
            earthPoint(epoch.shiftedBy(4.0), 110_000.0, DESCENDING)));
  }

  /** An upper stage climbing through 300 km, never inside the heating domain. */
  private static DebrisTrack climbingTrack() {
    return track(
        List.of(
            earthPoint(epoch, 300_000.0, ASCENDING),
            earthPoint(epoch.shiftedBy(2.0), 301_000.0, ASCENDING)));
  }

  private static DebrisTrack track(List<MissionEphemerisPoint> points) {
    return new DebrisTrack(new MissionEphemeris(points), StageRole.BOOSTER, 1);
  }

  private static MissionEphemerisPoint earthPoint(
      AbsoluteDate date, double altitude, Vector3D velocity) {
    Vector3D position = new Vector3D(Constants.WGS84_EARTH_EQUATORIAL_RADIUS + altitude, 0.0, 0.0);
    return new MissionEphemerisPoint(
        date, position, velocity, "test", false, 1_000.0, altitude, TrajectoryArc.earth());
  }

  /** An executor that holds its tasks until the test runs them, in either order. */
  private static final class QueuedExecutor implements Executor {

    private final List<Runnable> queued = new ArrayList<>();

    @Override
    public void execute(Runnable task) {
      queued.add(task);
    }

    void runAll() {
      queued.forEach(Runnable::run);
      queued.clear();
    }

    void runInReverse() {
      for (int i = queued.size() - 1; i >= 0; i--) {
        queued.get(i).run();
      }
      queued.clear();
    }
  }
}
