package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * PHY-4 / L6 §7.2 — the test of the lot, and the acceptance recipe of the whole item. It flies
 * {@link LunarTransferMission} through {@code MissionOptimizer} and then the ephemeris generator,
 * exactly the way the application's compute path does, and judges the trajectory the application
 * would draw.
 *
 * <p><b>Everything asserted here is read off the ephemeris rather than off a propagation of its
 * own</b>, and that is what gives the assertions their reach: one perilune reading proves at the
 * same time that the arcs carry the right central body, that {@code altitudeMeters} is measured
 * against the lunar reference shape (L3 §3.4, L4 §3.6), and that the sampling actually covers
 * closest approach. A separate propagation would have proved only the physics.
 *
 * <p><b>{@code isComplete()} is asserted but it has little to say</b>, and the design says so
 * rather than letting a reader over-trust it: the terminal coast is sized by the horizon, so {@code
 * StageRun.shortfallSeconds} returns zero for it whatever happened, and only a thrown propagation
 * would clear the flag (spec §1.9). The date of the last sample is the assertion that actually
 * catches a truncated flight.
 */
class LunarTransferFlightTest {
  private static final Logger logger = LogManager.getLogger(LunarTransferFlightTest.class);

  /**
   * No stage of this mission is optimizable, so CMA-ES never runs and this budget is never spent.
   */
  private static final int MAX_EVALUATIONS = 1;

  /**
   * The announced band on the flown perilune (m). An order of magnitude above the 0.9 km the 60 s
   * coast sampling can over-read closest approach by (spec §4.3), and above the 1 km the aim secant
   * converges to.
   */
  private static final double PERILUNE_BAND = 10_000.0;

  private static AbsoluteDate epoch;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 3, 31, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  @DisplayName("A translunar transfer flies two arcs and reaches the perilune it aimed for")
  void translunarTransferReachesItsPerilune() {
    LunarTransferMission mission = new LunarTransferMission("PHY-4 acceptance");
    // The application's own sequence: seed the launch state, then let the planner fly the chain
    // (MissionPlanOptimizer.fixedLoadPlanner, the branch a spec-less legacy entry takes).
    mission.setCurrentState(mission.getInitialState(epoch));

    long startedAt = System.nanoTime();
    MissionComputeResult result = new MissionOptimizer(mission, MAX_EVALUATIONS, 42L).optimize();
    double wallSeconds = (System.nanoTime() - startedAt) / 1.0e9;

    MissionEphemeris ephemeris = result.ephemeris();
    assertNotNull(ephemeris, "the flight produced no ephemeris");

    logger.info(
        "Lunar transfer: {} points in {} s of wall time",
        ephemeris.allPoints().size(),
        String.format(Locale.ROOT, "%.1f", wallSeconds));

    // ── the arcs ────────────────────────────────────────────────────────────
    List<SolarSystemBody> arcBodies = new ArrayList<>();
    for (MissionEphemerisPoint point : ephemeris.allPoints()) {
      if (arcBodies.isEmpty() || arcBodies.get(arcBodies.size() - 1) != point.arc().body()) {
        arcBodies.add(point.arc().body());
      }
    }
    logger.info("Arcs flown, in order: {}", arcBodies);
    assertEquals(
        List.of(SolarSystemBody.EARTH, SolarSystemBody.MOON),
        arcBodies,
        "the transfer must cross into the lunar sphere and stay there to the horizon");

    // ── the perilune, read on the lunar arc ─────────────────────────────────
    double perilune = Double.POSITIVE_INFINITY;
    for (MissionEphemerisPoint point : ephemeris.allPoints()) {
      if (point.arc().body() == SolarSystemBody.MOON) {
        perilune = FastMath.min(perilune, point.altitudeMeters());
      }
    }
    logger.info(
        "Flown perilune altitude: {} km", String.format(Locale.ROOT, "%.1f", perilune / 1000.0));
    assertEquals(
        LunarTransferMission.DEFAULT_PERILUNE_ALTITUDE,
        perilune,
        PERILUNE_BAND,
        "the flown perilune must land in the announced band");

    // A sanity floor that no tolerance can absorb: the trajectory must not graze or enter the Moon.
    assertTrue(perilune > 0.0, "the trajectory impacted the Moon, got " + perilune + " m");

    // ── the trajectory is whole ─────────────────────────────────────────────
    // The real check on truncation. isComplete() cannot see it on a horizon-sized terminal coast
    // (spec §1.9), so the date of the last sample is what does.
    double flownSeconds =
        ephemeris.allPoints().get(ephemeris.allPoints().size() - 1).time().durationFrom(epoch);
    assertEquals(
        LunarTransferMission.MISSION_DURATION_SECONDS,
        flownSeconds,
        60.0,
        "the flight must reach the mission horizon, not stop short of it");
    assertTrue(ephemeris.isComplete(), "a stage threw during the flight");

    // ── the boundary is one instant written twice ───────────────────────────
    int boundaries = 0;
    for (int i = 1; i < ephemeris.allPoints().size(); i++) {
      MissionEphemerisPoint previous = ephemeris.allPoints().get(i - 1);
      MissionEphemerisPoint current = ephemeris.allPoints().get(i);
      if (previous.arc().body() == current.arc().body()) {
        continue;
      }
      boundaries++;
      assertEquals(
          0.0,
          current.time().durationFrom(previous.time()),
          1.0e-6,
          "the samples either side of an arc change must be the same instant");

      Vector3D previousInGcrf = inGcrf(previous);
      Vector3D currentInGcrf = inGcrf(current);
      logger.info(
          "Arc change {} -> {} at {} h, discontinuity {} m",
          previous.arc().body(),
          current.arc().body(),
          FastMath.round(previous.time().durationFrom(epoch) / 3600.0),
          String.format(Locale.ROOT, "%.6f", previousInGcrf.subtract(currentInGcrf).getNorm()));
      assertEquals(
          0.0,
          previousInGcrf.subtract(currentInGcrf).getNorm(),
          1.0e-3,
          "and the same place, brought into a common frame");
    }
    assertEquals(1, boundaries, "exactly one crossing, inbound");

    // ── the trail is the flight, vertex for vertex ──────────────────────────
    // A property no other mission in the repository has: 4.5 days at the 60 s coast step stays
    // under
    // the 8 192 vertex budget, so nothing is decimated (spec §3.4).
    logger.info(
        "Trail: {} vertices for {} points",
        ephemeris.displayTrail().size(),
        ephemeris.allPoints().size());
    assertEquals(
        ephemeris.allPoints().size(),
        ephemeris.displayTrail().size(),
        "the trail must not decimate at this horizon");

    // ── logged, not asserted (the discipline of L2 §5.2) ────────────────────
    logger.info(
        "Achieved orbit at the end of the optimize pass (the translunar ellipse, not the lunar"
            + " arrival): {}",
        result.achievedOrbit().formatOsculating());
  }

  /**
   * The aim converges at every epoch, not only at the one the flight test flies.
   *
   * <p><b>This test exists because the application found what the flight test could not.</b> Loaded
   * with {@code mission.lunarDemo} at the simulation clock's own date, the first version of the aim
   * returned a perilune of <b>−53 km</b> — an impact, computed and flown as though it were a plan.
   * The geometry test upstream proves the transfer <em>plane</em> resolves at any epoch; nothing
   * proved the aim did, and a secant seeded on a unit slope does not (spec §12). Bisection on a
   * bracket does, and this is where that is checked.
   *
   * <p>Five epochs spread across a lunar month, which is the period the encounter geometry varies
   * over.
   */
  @Test
  @DisplayName("Every epoch either reaches the aimed perilune or refuses — none flies an impact")
  void theAimConvergesOrRefusesAcrossALunarMonth() {
    int converged = 0;
    int refused = 0;
    for (int day = 0; day < 25; day += 6) {
      AbsoluteDate date = epoch.shiftedBy(day * 86_400.0);
      LunarTransferMission mission = new LunarTransferMission("epoch probe " + day);
      SpacecraftState parking = mission.getInitialState(date);
      mission.setCurrentState(parking);

      try {
        SpacecraftState injected = mission.getStages().get(0).enter(parking, mission);
        converged++;
        assertTrue(
            injected.getMass() < parking.getMass(),
            "day " + day + ": the impulse must have consumed propellant");
        assertTrue(
            injected.getPVCoordinates().getVelocity().getNorm()
                > parking.getPVCoordinates().getVelocity().getNorm(),
            "day " + day + ": the impulse must have raised the speed");
      } catch (OrbitlabException refusal) {
        // A refusal is a legitimate outcome and it is the point of this test. Not every encounter
        // geometry can deliver a given perilune: with the aim point on the lunar surface the flown
        // closest approach still has a floor, measured at 135 km at one epoch of this month against
        // a
        // 100 km target. What must never happen is the alternative — a plan below the surface,
        // flown
        // as though it were a trajectory, which is what the first version of the aim produced (spec
        // §12).
        refused++;
        assertTrue(
            refusal.getMessage().contains("did not reach"),
            "day "
                + day
                + ": a refusal must say what it could not reach, got: "
                + refusal.getMessage());
      }
    }
    logger.info(
        "Aim across a lunar month: {} epochs converged, {} refused cleanly", converged, refused);
    assertTrue(converged > 0, "no epoch of the month could be flown at all");
  }

  /** An ephemeris point's position brought into GCRF, whatever arc it was recorded in. */
  private static Vector3D inGcrf(MissionEphemerisPoint point) {
    return point
        .arc()
        .frame()
        .getTransformTo(OrekitService.get().gcrf(), point.time())
        .transformPosition(point.position());
  }
}
