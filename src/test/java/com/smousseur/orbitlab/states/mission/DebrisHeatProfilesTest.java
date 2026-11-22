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
 * The debris' heat profiles are built off the render thread, and a renderer only ever reads the
 * ones built for the very debris list it is drawing.
 */
class DebrisHeatProfilesTest {

  private static final Vector3D DESCENDING = new Vector3D(-2_000.0, 7_000.0, 0.0);
  private static final Vector3D ASCENDING = new Vector3D(2_000.0, 7_000.0, 0.0);
  private static final ReentryFxConfig CONFIG = ReentryFxConfig.defaults();

  private static AbsoluteDate epoch;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  void nothingIsServedBeforeTheComputationRuns() {
    DebrisHeatProfiles profiles = new DebrisHeatProfiles();
    List<DebrisTrack> debris = List.of(fallingTrack());
    QueuedExecutor executor = new QueuedExecutor();

    profiles.request(debris, AtmosphereModel.HARRIS_PRIESTER, CONFIG, executor);

    assertTrue(profiles.profileFor(debris, 0).samples().isEmpty());
  }

  @Test
  void theProfilesOfARequestServeThatVeryList() {
    DebrisHeatProfiles profiles = new DebrisHeatProfiles();
    List<DebrisTrack> debris = List.of(fallingTrack());
    QueuedExecutor executor = new QueuedExecutor();

    profiles.request(debris, AtmosphereModel.HARRIS_PRIESTER, CONFIG, executor);
    executor.runAll();

    assertEquals(3, profiles.profileFor(debris, 0).samples().size());
  }

  @Test
  void aDebrisThatNeverEntersTheHeatingDomainGetsNoProfile() {
    DebrisHeatProfiles profiles = new DebrisHeatProfiles();
    List<DebrisTrack> debris = List.of(fallingTrack(), climbingTrack());
    QueuedExecutor executor = new QueuedExecutor();

    profiles.request(debris, AtmosphereModel.HARRIS_PRIESTER, CONFIG, executor);
    executor.runAll();

    assertEquals(3, profiles.profileFor(debris, 0).samples().size());
    assertTrue(profiles.profileFor(debris, 1).samples().isEmpty());
  }

  @Test
  void anEqualButDistinctListGetsNoProfile() {
    DebrisHeatProfiles profiles = new DebrisHeatProfiles();
    List<DebrisTrack> debris = List.of(fallingTrack());
    QueuedExecutor executor = new QueuedExecutor();

    profiles.request(debris, AtmosphereModel.HARRIS_PRIESTER, CONFIG, executor);
    executor.runAll();

    assertTrue(profiles.profileFor(new ArrayList<>(debris), 0).samples().isEmpty());
  }

  @Test
  void aResultOvertakenByANewerRequestIsDropped() {
    DebrisHeatProfiles profiles = new DebrisHeatProfiles();
    List<DebrisTrack> older = List.of(fallingTrack());
    List<DebrisTrack> newer = List.of(fallingTrack());
    QueuedExecutor executor = new QueuedExecutor();

    profiles.request(older, AtmosphereModel.HARRIS_PRIESTER, CONFIG, executor);
    profiles.request(newer, AtmosphereModel.HARRIS_PRIESTER, CONFIG, executor);
    executor.runInReverse();

    assertEquals(3, profiles.profileFor(newer, 0).samples().size());
    assertTrue(profiles.profileFor(older, 0).samples().isEmpty());
  }

  @Test
  void aRefusedTaskLeavesTheDebrisWithoutProfile() {
    DebrisHeatProfiles profiles = new DebrisHeatProfiles();
    List<DebrisTrack> debris = List.of(fallingTrack());
    Executor refusing =
        task -> {
          throw new RejectedExecutionException("shut down");
        };

    assertDoesNotThrow(
        () -> profiles.request(debris, AtmosphereModel.HARRIS_PRIESTER, CONFIG, refusing));
    assertTrue(profiles.profileFor(debris, 0).samples().isEmpty());
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
