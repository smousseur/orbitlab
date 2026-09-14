package com.smousseur.orbitlab.simulation.mission.detector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.detector.AtmosphericInterfaceDetector.InterfaceCrossing;
import java.util.List;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Proves the PHY-3 brick records the Kármán-line crossings a flight actually makes, with the
 * direction {@code MIS-10} will select the atmospheric entry by. The detector is armed nowhere in
 * production yet, so this is its only exercise.
 */
class AtmosphericInterfaceDetectorTest {

  private static final double RE = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static final double MU = Constants.WGS84_EARTH_MU;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static KeplerianPropagator propagatorFor(double perigeeAlt, double apogeeAlt) {
    double rp = RE + perigeeAlt;
    double ra = RE + apogeeAlt;
    double a = 0.5 * (rp + ra);
    double e = (ra - rp) / (ra + rp);
    KeplerianOrbit orbit =
        new KeplerianOrbit(
            a,
            e,
            0.5,
            0.0,
            0.0,
            0.0,
            PositionAngleType.TRUE,
            OrekitService.get().gcrf(),
            AbsoluteDate.J2000_EPOCH,
            MU);
    return new KeplerianPropagator(orbit);
  }

  private static double period(KeplerianPropagator propagator) {
    double a = propagator.getInitialState().getOrbit().getA();
    return 2.0 * FastMath.PI * FastMath.sqrt(a * a * a / MU);
  }

  /**
   * An orbit whose perigee sits below the Kármán line and whose apogee sits above it crosses 100 km
   * twice per revolution — climbing out after perigee, falling back before the next. Flown from
   * perigee for one full period, the detector must record exactly those two, ascending then
   * descending, and offer the descending one as the entry mark.
   */
  @Test
  void records_bothCrossings_withDirection() {
    KeplerianPropagator propagator = propagatorFor(50_000.0, 300_000.0);
    AtmosphericInterfaceDetector detector = new AtmosphericInterfaceDetector(RE);
    propagator.addEventDetector(detector);

    AbsoluteDate start = propagator.getInitialState().getDate();
    propagator.propagate(start.shiftedBy(period(propagator)));

    List<InterfaceCrossing> crossings = detector.crossings();
    assertEquals(2, crossings.size(), () -> "expected two crossings, got " + crossings);
    assertFalse(crossings.get(0).descending(), "the first crossing is the climb out — ascending");
    assertTrue(crossings.get(1).descending(), "the second crossing is the fall back — descending");
    assertTrue(detector.firstDescendingCrossing().isPresent(), "the entry mark must be present");
    assertEquals(
        crossings.get(1).date(),
        detector.firstDescendingCrossing().orElseThrow(),
        "the entry mark is the descending crossing");
  }

  /**
   * A circular orbit that stays above 100 km never crosses the line: a detector whose {@code g}
   * never changes sign records nothing, and offers no entry.
   */
  @Test
  void records_nothing_whenOrbitStaysAboveTheLine() {
    KeplerianPropagator propagator = propagatorFor(400_000.0, 400_000.0);
    AtmosphericInterfaceDetector detector = new AtmosphericInterfaceDetector(RE);
    propagator.addEventDetector(detector);

    AbsoluteDate start = propagator.getInitialState().getDate();
    propagator.propagate(start.shiftedBy(period(propagator)));

    assertTrue(detector.crossings().isEmpty(), "a 400 km circular orbit never reaches the line");
    assertTrue(detector.firstDescendingCrossing().isEmpty());
  }
}
