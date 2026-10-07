package com.smousseur.orbitlab.simulation.mission.window.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * The ascent a lunar launch window measures its parking orbit on, flown alone from Canaveral on the
 * production configuration — Falcon Heavy, a 2 000 kg orbiter, a 400 km parking orbit.
 *
 * <p><b>Behind {@code orbitlab.slowTests}</b>: each ascent is a full CMA-ES gravity turn, some
 * fifty seconds, and this class flies two.
 */
@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
class ParkingInsertionTest {
  private static final Logger logger = LogManager.getLogger(ParkingInsertionTest.class);

  private static AbsoluteDate morningWindow;
  private static ParkingInsertion atMidnight;
  private static ParkingInsertion atMorningWindow;

  @BeforeAll
  static void flyBothAscents() {
    OrekitService.get().initialize();
    morningWindow = utc("2026-10-06T10:02:16.000Z");
    atMidnight = fly(utc("2026-10-06T00:00:00.000Z"));
    atMorningWindow = fly(morningWindow);
  }

  private static AbsoluteDate utc(String iso) {
    return new AbsoluteDate(iso, TimeScalesFactory.getUTC());
  }

  private static ParkingInsertion fly(AbsoluteDate liftOff) {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "ascent");
    values.put("LAUNCH_SITE_LAT", 28.562);
    values.put("LAUNCH_SITE_LONG", -80.577);
    values.put("LAUNCH_SITE_ALT", 3.0);
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "LUNAR_ORBITER");
    values.put("PAYLOAD_MASS", 2_000.0);
    values.put("LUNAR_ORBIT_ALT", 100.0);
    MissionSpec.LunarOrbit spec =
        (MissionSpec.LunarOrbit)
            MissionFactory.specFromWizardValues(values, MissionType.LUNAR_ORBIT);
    ParkingInsertion insertion =
        ParkingInsertion.fly(
            spec.configuration(),
            spec.atmosphere(),
            spec.latitude(),
            spec.longitude(),
            spec.altitude(),
            spec.parkingAltitude(),
            liftOff);
    logger.info(
        "Ascent flown at {}: MECO +{} s, insertion +{} s, downrange {} rad, {} kg, earth-fixed {}",
        liftOff,
        insertion.mecoDelay(),
        insertion.insertionDelay(),
        insertion.downrange(),
        insertion.mass(),
        insertion.earthFixed());
    return insertion;
  }

  /**
   * Flown alone at the date of the production compute's first Canaveral window, the ascent lands on
   * the insertion that compute flew there, to the tenth of a second and of a kilogram.
   */
  @Test
  void theAscentLandsOnTheInsertionTheChainFlies() {
    assertEquals(3_656.4, atMorningWindow.insertionDelay(), 0.05);
    assertEquals(20_150.3, atMorningWindow.mass(), 0.05);
  }

  /**
   * The insertion flown at midnight, carried through the Earth-fixed frame to the morning window,
   * is the one an ascent flown at the morning window reaches: the same plane to a hundredth of a
   * degree, and the same injection to the second.
   */
  @Test
  void anInsertionCarriedToAnotherLiftOffIsTheOneFlownThere() {
    SpacecraftState carried = atMidnight.carriedTo(morningWindow);
    SpacecraftState flown = atMorningWindow.carriedTo(morningWindow);

    double planeGap =
        FastMath.toDegrees(
            Vector3D.angle(
                carried.getPVCoordinates().getMomentum(), flown.getPVCoordinates().getMomentum()));
    double injectionGap =
        TranslunarInjectionPlan.departureFrom(carried)
            .injectionDate()
            .durationFrom(TranslunarInjectionPlan.departureFrom(flown).injectionDate());
    logger.info(
        String.format(
            Locale.ROOT,
            "Midnight insertion carried to %s: plane %.4f° from the flown one, injection %+.2f s",
            morningWindow,
            planeGap,
            injectionGap));

    assertTrue(planeGap < 0.01, () -> "the carried plane is " + planeGap + "° off the flown one");
    assertEquals(0.0, injectionGap, 1.0, "the carried insertion injects on the flown passage");
  }
}
