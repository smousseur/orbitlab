package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.gravity.SoiCrossingDetector;
import com.smousseur.orbitlab.simulation.gravity.SphereOfInfluence;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisGenerator;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.stage.ParkingCoastStage;
import com.smousseur.orbitlab.simulation.mission.stage.TLIBurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.TranslunarCoastStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * MIS-5 / L1 §6.3 — the flight of the lot. Three production stages, a real aim, a real sphere: a
 * parking coast to ignition, the translunar injection, and a bounded translunar coast that ends
 * where the trajectory enters the lunar sphere of influence.
 *
 * <p><b>It starts in parking orbit rather than on the pad</b>, and that is a decision (spec {@code
 * docs/lunar-orbit/03-conception-L1.md} §6.3). No mission declares the terminating predicate before
 * L5, and {@code LunarFlybyMission.buildStages} is private with its stages frozen by {@code
 * List.copyOf}, so a ground launch would mean a permanent copy of the ascent chain in a test — for
 * a chain L5 will not build that way. From parking the copy is three stages, the aim and the sphere
 * are real, and the price falls from a CMA-ES ascent to a handful of seconds.
 *
 * <p><b>The parking geometry is {@link TranslunarInjectionPlan#parkingState}</b>, production and
 * public, kept precisely as a reference (MIS-4 / L6 §1.5). It is 185 km / 30° and not the 400 km /
 * 28.562° MIS-5 will fly: reproducing the latter would mean copying {@code transferPlaneNormal}'s
 * trigonometry, which is package-private, into a test — the very duplication this file exists to
 * avoid. The consequence is that the crossing date belongs to MIS-4 / L0's 3.08–3.16 d band rather
 * than to MIS-5 / L0's 3.071–3.148 d, and both are logged against.
 *
 * <p><b>What is asserted is the boundary, not the date.</b> The band below is wide enough to be
 * independent of the parking configuration and narrow enough to exclude the two ways the stage
 * could stop for the wrong reason — the 7200 s safety net, and its own bound.
 *
 * <p><b>Contrainte de méthode</b> (découpage §3): this flight is the user's to run.
 */
@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
class TranslunarBoundaryFlightTest {
  private static final Logger logger = LogManager.getLogger(TranslunarBoundaryFlightTest.class);

  private static final double PERILUNE_ALTITUDE = 100_000.0;

  /**
   * Bound on the translunar coast (s). Well past the crossing and well past the 4-day time of
   * flight, so that stopping at it would be unmistakable — and so that a stop at the boundary is
   * unmistakably not it.
   */
  private static final double COAST_BOUND_SECONDS = 5.0 * 86_400.0;

  /** How far back along the parking orbit the flight starts, so the coast has something to fly. */
  private static final double PARKING_LEAD_SECONDS = 1_500.0;

  /**
   * The definition of {@code StageLegRunner.BOUNDARY_STOP_TOLERANCE}, which is package-private
   * there: twice the detector's own date convergence (PHY-4 / L6 §12).
   */
  private static final double BOUNDARY_STOP_TOLERANCE =
      2.0 * SoiCrossingDetector.DATE_CONVERGENCE_SECONDS;

  private static final String COAST_NAME = "Translunar coast";

  private static AbsoluteDate injectionDate;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
    injectionDate = new AbsoluteDate(2026, 3, 31, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  @DisplayName("A translunar coast ends at the lunar sphere, and both passes end there together")
  void bothPassesEndAtTheLunarSphere() {
    VehicleStack stack = stack();
    SpacecraftState parking =
        TranslunarInjectionPlan.parkingState(injectionDate, stack.getMass())
            .shiftedBy(-PARKING_LEAD_SECONDS);

    // ── the stage walk: what MissionOptimizer does with a non-optimizable chain ──
    Mission walk = missionWith(stack);
    walk.setCurrentState(parking);
    SpacecraftState walkCoastEntry = null;
    for (MissionStage stage : walk.getStages()) {
      SpacecraftState propagated = stage.propagateStandalone(walk.getCurrentState(), walk);
      if (!COAST_NAME.equals(stage.getName())) {
        walkCoastEntry = propagated;
      }
      walk.setCurrentState(propagated);
    }
    SpacecraftState walkExit = walk.getCurrentState();
    double coastSeconds = walkExit.getDate().durationFrom(walkCoastEntry.getDate());

    // ── the ephemeris pass: the same chain, sampled ──
    Mission chain = missionWith(stack);
    MissionEphemeris ephemeris = new MissionEphemerisGenerator().generate(chain, parking, 0.0);
    AbsoluteDate chainExit = ephemeris.lastPoint().time();

    logger.info(
        "Translunar coast flown {} d of a {} d bound — MIS-5/L0 measured 3.071–3.148 d from"
            + " 400 km/28.562°, MIS-4/L0 3.08–3.16 d from 185 km/30° (this fixture)",
        String.format(Locale.ROOT, "%.4f", coastSeconds / 86_400.0),
        (int) (COAST_BOUND_SECONDS / 86_400.0));
    logger.info(
        "Two passes: stage walk {} , ephemeris {} , gap {} s",
        walkExit.getDate(),
        chainExit,
        String.format(Locale.ROOT, "%.3e", chainExit.durationFrom(walkExit.getDate())));

    // ── it stopped at the sphere, and not at either wrong place ──
    assertTrue(
        coastSeconds > 3.0 * 86_400.0 && coastSeconds < 3.2 * 86_400.0,
        "the coast must end at the lunar sphere, some three days out; got "
            + coastSeconds / 86_400.0
            + " d — 0.083 d would be the 7200 s safety net, 5 d its own bound");

    Vector3D toMoon =
        OrekitService.get()
            .body(SolarSystemBody.MOON)
            .getPosition(walkExit.getDate(), walkExit.getFrame());
    assertEquals(
        SphereOfInfluence.of(SolarSystemBody.MOON).radiusAt(walkExit.getDate()),
        walkExit.getPosition().subtract(toMoon).getNorm(),
        1_000.0,
        "and it must end ON the sphere");

    // ── the two passes agree ──
    assertEquals(
        0.0,
        chainExit.durationFrom(walkExit.getDate()),
        BOUNDARY_STOP_TOLERANCE,
        "the stage walk and the ephemeris must read the same crossing");

    // ── the flight is whole, and never leaves the Earth arc ──
    assertTrue(
        ephemeris.isComplete(),
        "a coast that stopped where it declared it would must not read as truncated");
    for (MissionEphemerisPoint point : ephemeris.allPoints()) {
      assertEquals(
          SolarSystemBody.EARTH,
          point.arc().body(),
          "the chain ends at the boundary, so no point is ever flown around the Moon");
    }
    assertEquals(
        COAST_NAME,
        ephemeris.lastPoint().stageName(),
        "the last point must belong to the coast, not to a stage that never ran");
  }

  /** Upper stage plus an inert payload, loaded well clear of what the injection costs. */
  private static VehicleStack stack() {
    LaunchVehicle upperStage =
        new LaunchVehicle(4_000, 107_500, 30_000, new PropulsionSystem(348, 981_000));
    Spacecraft payload = new Spacecraft(2_000, 2_000, 0, new PropulsionSystem(320, 400));
    return new VehicleStack(List.of(upperStage, payload));
  }

  /**
   * The chain of the lot. Earth-centred with the Moon and the Sun, which is what {@code
   * ArcTransition} derives the selenocentric context from and what makes the crossing physical.
   */
  private static Mission missionWith(Vehicle vehicle) {
    List<MissionStage> stages =
        List.of(
            new ParkingCoastStage("Parking coast"),
            new TLIBurnStage("Translunar injection", PERILUNE_ALTITUDE),
            new TranslunarCoastStage(COAST_NAME, COAST_BOUND_SECONDS));
    return new Mission("translunar boundary flight", vehicle, stages, null) {
      @Override
      public SpacecraftState getInitialState(AbsoluteDate initialDate) {
        return null;
      }

      @Override
      public GravitationalContext gravitationalContext() {
        return GravitationalContext.earth()
            .withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN);
      }
    };
  }
}
