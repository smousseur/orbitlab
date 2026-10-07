package com.smousseur.orbitlab.simulation.mission.window.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowCandidate;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSearch;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSolver;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.PVCoordinates;

/**
 * The lunar window on the parking orbit the ascent really reaches — geometry only, on an insertion
 * <b>recorded</b> rather than flown.
 *
 * <p><b>Recorded, unlike the problem's closed tests next door</b>, because what is tested here is
 * what the window does with a measured insertion, and measuring one is a fifty-second CMA-ES ascent
 * that belongs to {@code ParkingInsertionTest}. The figures are that test's own: the ascent flown
 * due east from Canaveral at 2026-10-06T00:00Z — Falcon Heavy, a 2 000 kg orbiter, a 400 km parking
 * orbit, the production budget and seed.
 *
 * <p><b>Nothing here confirms</b>: a confirmation flies the aim, seven to ten seconds a candidate,
 * and the dates of a window are decided by the criterion it refines, not by the confirmation.
 */
class LunarLaunchWindowInsertionTest {
  private static final Logger logger = LogManager.getLogger(LunarLaunchWindowInsertionTest.class);

  private static final double CANAVERAL_LATITUDE = 28.562;
  private static final double CANAVERAL_LONGITUDE = -80.577;
  private static final double CANAVERAL_ALTITUDE = 3.0;

  /** Lift-off step of the scan for a short first coast (s), a couple of seconds of coast each. */
  private static final double SCAN_STEP_SECONDS = 30.0;

  /** A day of lift-offs. */
  private static final int SCAN_STEPS = 2_880;

  /** The next passage is a revolution later, plus the 12 s the injection point drifts meanwhile. */
  private static final double NEXT_PASSAGE_TOLERANCE_SECONDS = 30.0;

  /** The ascent flown at 2026-10-06T00:00Z, as {@code ParkingInsertion.fly} measured it. */
  private static final ParkingInsertion MIDNIGHT_INSERTION =
      new ParkingInsertion(
          new PVCoordinates(
              new Vector3D(-4414973.845382083, 4621240.759864438, -2218153.317407967),
              new Vector3D(-5471.558369536221, -3952.2272354175307, 2650.4812200157967)),
          183.4322638935742,
          3654.3209102200944,
          3.9436361376142717,
          20142.46838546861);

  private static MissionSpec.LunarOrbit spec;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "insertion");
    values.put("LAUNCH_SITE_LAT", CANAVERAL_LATITUDE);
    values.put("LAUNCH_SITE_LONG", CANAVERAL_LONGITUDE);
    values.put("LAUNCH_SITE_ALT", CANAVERAL_ALTITUDE);
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "LUNAR_ORBITER");
    values.put("PAYLOAD_MASS", 2_000.0);
    values.put("LUNAR_ORBIT_ALT", 100.0);
    spec =
        (MissionSpec.LunarOrbit)
            MissionFactory.specFromWizardValues(values, MissionType.LUNAR_ORBIT);
  }

  private static AbsoluteDate utc(String iso) {
    return new AbsoluteDate(iso, TimeScalesFactory.getUTC());
  }

  /** The due-east problem on the recorded insertion, its confirmation switched off. */
  private static LunarLaunchWindowProblem onTheRecordedInsertion() {
    return new LunarLaunchWindowProblem(
        CANAVERAL_LATITUDE,
        CANAVERAL_LONGITUDE,
        CANAVERAL_ALTITUDE,
        spec.parkingAltitude(),
        spec.orbitAltitude(),
        spec.configuration().toVehicleStack(),
        MIDNIGHT_INSERTION) {
      @Override
      public LaunchWindowCandidate confirm(LaunchWindowCandidate candidate) {
        return candidate;
      }
    };
  }

  /**
   * At 2026-10-08T02:52:04 the parking orbit posed at the pad passes its injection point 918 s
   * after lift-off — before the vehicle is even in parking. The window injects on the passage that
   * follows the insertion, as the chain does.
   */
  @Test
  void theInjectionIsThePassageThatFollowsTheInsertion() {
    AbsoluteDate liftOff = utc("2026-10-08T02:52:04.000Z");
    LunarLaunchWindowProblem.Injection injection = onTheRecordedInsertion().injectionAt(liftOff);

    double afterLiftOff = injection.state().getDate().durationFrom(liftOff);
    double period = MIDNIGHT_INSERTION.carriedTo(liftOff).getOrbit().getKeplerianPeriod();
    logger.info(
        "Injection {} s after lift-off, {} s after the insertion",
        afterLiftOff,
        afterLiftOff - MIDNIGHT_INSERTION.insertionDelay());
    assertTrue(
        afterLiftOff > MIDNIGHT_INSERTION.insertionDelay(),
        () -> "the window injects " + afterLiftOff + " s after lift-off, before the insertion");
    assertTrue(
        afterLiftOff < MIDNIGHT_INSERTION.insertionDelay() + period,
        () -> "the window injects " + afterLiftOff + " s after lift-off, past the first passage");
  }

  /**
   * A lift-off whose first passage comes less than half a burn after the insertion: the chain would
   * have to ignite before its coast began, so it waits for the next passage, and the window injects
   * there too.
   */
  @Test
  void aPassageInsideTheHalfBurnIsSkipped() {
    VehicleStack vehicle = spec.configuration().toVehicleStack();
    ActiveStageInfo active = vehicle.resolveActiveStage(MIDNIGHT_INSERTION.mass());
    AbsoluteDate floor = utc("2026-10-06T00:00:00.000Z");
    SpacecraftState first = MIDNIGHT_INSERTION.carriedTo(floor);
    double lead =
        TranslunarInjectionPlan.ignitionLead(
            first, TranslunarInjectionPlan.departureFrom(first), active);

    AbsoluteDate liftOff = null;
    double firstCoast = Double.NaN;
    for (int step = 0; step < SCAN_STEPS && liftOff == null; step++) {
      AbsoluteDate candidate = floor.shiftedBy(step * SCAN_STEP_SECONDS);
      double coast =
          TranslunarInjectionPlan.departureFrom(MIDNIGHT_INSERTION.carriedTo(candidate))
              .coastDuration();
      if (coast > 0.25 * lead && coast < 0.75 * lead) {
        liftOff = candidate;
        firstCoast = coast;
      }
    }
    assertNotNull(liftOff, "no lift-off within a day puts the first passage inside the half burn");

    LunarLaunchWindowProblem.Injection injection = onTheRecordedInsertion().injectionAt(liftOff);

    double afterInsertion =
        injection
            .state()
            .getDate()
            .durationFrom(liftOff.shiftedBy(MIDNIGHT_INSERTION.insertionDelay()));
    double period = MIDNIGHT_INSERTION.carriedTo(liftOff).getOrbit().getKeplerianPeriod();
    logger.info(
        "Lift-off {}: first passage {} s after the insertion for a {} s half burn, injection {} s"
            + " after it",
        liftOff,
        firstCoast,
        lead,
        afterInsertion);
    assertEquals(
        firstCoast + period,
        afterInsertion,
        NEXT_PASSAGE_TOLERANCE_SECONDS,
        "the window must inject on the passage after the one inside the half burn");
  }

  /**
   * Over five days from Canaveral, the windows move to the dates the flown insertion gives: the
   * morning ones by some eighty seconds, the night ones by some ten minutes.
   */
  @Test
  void theWindowsAreDatedOnTheFlownInsertion() {
    LunarLaunchWindowProblem problem = onTheRecordedInsertion();
    List<LaunchWindow> windows =
        new LaunchWindowSolver(problem)
            .solve(
                new LaunchWindowSearch(
                    utc("2026-10-06T00:00:00.000Z"),
                    Duration.ofDays(5),
                    problem.coarseStep(),
                    problem.refinementPrecision(),
                    LunarLaunchWindowPlanner.MAX_DELTA_V,
                    50.0,
                    20));
    List<AbsoluteDate> dates = windows.stream().map(LaunchWindow::date).sorted().toList();
    logger.info("Windows on the recorded insertion: {}", dates);

    assertTrue(dates.contains(utc("2026-10-06T10:03:36.000Z")), () -> "dated " + dates);
    assertTrue(dates.contains(utc("2026-10-08T03:02:50.000Z")), () -> "dated " + dates);
  }
}
