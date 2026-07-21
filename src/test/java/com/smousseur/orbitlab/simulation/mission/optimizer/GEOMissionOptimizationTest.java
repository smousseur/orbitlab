package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.operation.GEOMission;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

public class GEOMissionOptimizationTest extends AbstractTrajectoryOptimizerTest {
  private static final Logger logger = LogManager.getLogger(GEOMissionOptimizationTest.class);
  public static final int GEO_ALTITUDE = 35_786_000;
  public static final int PARKING_ALTITUDE = 400_000;

  // Tolerances calibrated on the split GEO profile (spec 06 I5). The apogee circularization is a
  // single ~3 h AKM burn whose plane rotation smears over the ~40° arc, leaving a ~0.25° residual
  // it cannot correct off-node (at the burn point the plane only rotates about the radius vector).
  // The node-targeted plane trim (bilan 08 §3.5, AnalyticPlaneTrimAtNodeStage) nulls that residual
  // with a short out-of-plane burn at a node — on the reference mission the final inclination lands
  // at ~3e-5°. 0.05° here guards against a regression (e.g. back to the ~0.25° pre-trim floor)
  // while keeping generous headroom (was 0.30° before the trim).
  private static final double ALTITUDE_TOLERANCE_M = 50_000.0; // ±50 km
  private static final double INCLINATION_TOLERANCE_RAD = FastMath.toRadians(0.05);

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void testGEOMission() {
    Mission geoMission = new GEOMission("GTO mission", PARKING_ALTITUDE, GEO_ALTITUDE);

    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    SpacecraftState initialState = geoMission.getInitialState(epoch);
    geoMission.setCurrentState(initialState);
    MissionOptimizer optimizer = new MissionOptimizer(geoMission, 40_000, TEST_SEED);
    MissionComputeResult result = optimizer.optimize();
    MissionEphemeris ephemeris = result.ephemeris();

    MinMaxAltitudeResults coast = extractMinMaxAltitudes(ephemeris, "Coasting");
    Assertions.assertTrue(
        FastMath.abs(coast.maxAltitude - GEO_ALTITUDE) < ALTITUDE_TOLERANCE_M,
        () ->
            String.format(
                "Final coast max altitude %.0f m off target %.0f m by more than %.0f m",
                coast.maxAltitude, (double) GEO_ALTITUDE, ALTITUDE_TOLERANCE_M));
    Assertions.assertTrue(
        FastMath.abs(coast.minAltitude - GEO_ALTITUDE) < ALTITUDE_TOLERANCE_M,
        () ->
            String.format(
                "Final coast min altitude %.0f m off target %.0f m by more than %.0f m",
                coast.minAltitude, (double) GEO_ALTITUDE, ALTITUDE_TOLERANCE_M));

    MissionEphemerisPoint last = ephemeris.lastPoint();
    KeplerianOrbit finalOrbit =
        new KeplerianOrbit(
            new PVCoordinates(last.position(), last.velocity()),
            OrekitService.get().gcrf(),
            last.time(),
            Constants.WGS84_EARTH_MU);
    Assertions.assertTrue(
        finalOrbit.getI() < INCLINATION_TOLERANCE_RAD,
        () ->
            String.format(
                "Final inclination %.4f rad (%.3f°) exceeds tolerance %.4f rad (%.3f°)",
                finalOrbit.getI(),
                FastMath.toDegrees(finalOrbit.getI()),
                INCLINATION_TOLERANCE_RAD,
                FastMath.toDegrees(INCLINATION_TOLERANCE_RAD)));
    // Split GEO profile (spec 06 I5): the launcher separates after GTO injection, only the
    // payload (2 t dry + AKM residual, 4 t max) reaches GEO. A final mass above that means the
    // upper stage never separated.
    double payloadReferenceMass = 4_000.0;
    Assertions.assertTrue(
        last.mass() <= payloadReferenceMass + 1.0,
        () ->
            String.format(
                "Final mass %.0f kg exceeds the payload reference mass %.0f kg: "
                    + "the upper stage did not separate",
                last.mass(), payloadReferenceMass));

    logger.info("Inclinaison: {}°", FastMath.toDegrees(finalOrbit.getI()));
    logger.info("Min coast altitude: {} km", coast.minAltitude / 1000);
    logger.info("Max coast altitude: {} km", coast.maxAltitude / 1000);
    logger.info("Final mass: {} kg (AKM residual {} kg)", last.mass(), last.mass() - 2_000.0);
  }
}
