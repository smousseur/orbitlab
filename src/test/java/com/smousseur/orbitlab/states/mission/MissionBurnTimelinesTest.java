package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagingPlan;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/** The primary's burn timeline, built off the render thread and served by ephemeris identity. */
class MissionBurnTimelinesTest {

  private static final double G0 = 9.80665;

  /** A core burning 10 kg/s under an upper stage: one burn per run of falling mass. */
  private static final Vehicle STACK =
      new VehicleStack(
          List.of(
              new LaunchVehicle(1_000.0, 4_000.0, new PropulsionSystem(300.0, 10.0 * 300.0 * G0)),
              new LaunchVehicle(200.0, 800.0, new PropulsionSystem(400.0, 2.0 * 400.0 * G0))),
          new StagingPlan(List.of(StageRole.CORE, StageRole.UPPER), null));

  private static AbsoluteDate epoch;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  void nothingIsServedBeforeTheTimelineIsBuilt() {
    MissionBurnTimelines timelines = new MissionBurnTimelines();
    MissionEphemeris ephemeris = burning();

    timelines.request(ephemeris, STACK, new QueuedExecutor());

    assertTrue(timelines.timelineFor(ephemeris).isEmpty());
  }

  @Test
  void theTimelineOfARequestServesThatVeryEphemeris() {
    MissionBurnTimelines timelines = new MissionBurnTimelines();
    MissionEphemeris ephemeris = burning();
    QueuedExecutor executor = new QueuedExecutor();

    timelines.request(ephemeris, STACK, executor);
    executor.runAll();

    assertEquals(1, timelines.timelineFor(ephemeris).orElseThrow().burns().size());
  }

  /** A recomputation publishes a new ephemeris: the old one's burns must not be drawn on it. */
  @Test
  void anotherEphemerisGetsNoTimeline() {
    MissionBurnTimelines timelines = new MissionBurnTimelines();
    QueuedExecutor executor = new QueuedExecutor();

    timelines.request(burning(), STACK, executor);
    executor.runAll();

    assertTrue(timelines.timelineFor(burning()).isEmpty());
  }

  @Test
  void aResultOvertakenByANewerRequestIsDropped() {
    MissionBurnTimelines timelines = new MissionBurnTimelines();
    MissionEphemeris older = burning();
    MissionEphemeris newer = burning();
    QueuedExecutor executor = new QueuedExecutor();

    timelines.request(older, STACK, executor);
    timelines.request(newer, STACK, executor);
    executor.runInReverse();

    assertTrue(timelines.timelineFor(newer).isPresent());
    assertTrue(timelines.timelineFor(older).isEmpty());
  }

  private static MissionEphemeris burning() {
    return new MissionEphemeris(
        List.of(at(0.0, 6_000.0), at(1.0, 5_990.0), at(2.0, 5_980.0), at(3.0, 5_980.0)));
  }

  private static MissionEphemerisPoint at(double seconds, double mass) {
    return new MissionEphemerisPoint(
        epoch.shiftedBy(seconds),
        new Vector3D(7_000_000.0, 0.0, 0.0),
        new Vector3D(0.0, 7_500.0, 0.0),
        "Gravity turn",
        true,
        mass,
        400_000.0,
        TrajectoryArc.earth());
  }

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
