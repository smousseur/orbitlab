package com.smousseur.orbitlab.simulation.mission.ephemeris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * The arc's two contracts (PHY-4 / L3, spec {@code docs/multi-corps/05-conception-L3.md} §2).
 *
 * <p>{@link TrajectoryArc#earth()} states the Earth pairing a <em>second</em> time — {@link
 * GravitationalContext#earth()} already does — because four of the five test classes that build
 * ephemeris points never initialise {@code OrekitService}, and the gravitational context resolves
 * ITRF and the WGS84 ellipsoid, both of which need the data archive. GCRF does not. That duplication
 * is the price, and the first test here is what stops the two from drifting apart.
 *
 * <p>The second contract is that the record's <b>equality is arc identity</b>: it is what {@link
 * TrajectoryPolyline} partitions on, and it is why the record is this narrow rather than being the
 * gravitational context itself.
 */
class TrajectoryArcTest {

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  void theEarthArcAgreesWithTheEarthGravitationalContext() {
    assertEquals(TrajectoryArc.of(GravitationalContext.earth()), TrajectoryArc.earth());
  }

  /**
   * The failure mode the narrow record exists to prevent. In L6 one lunar arc will legitimately run
   * a coast without perturbers into a burn with them; had the arc been the gravitational context,
   * partitioning by {@code equals} would have manufactured a boundary there. Derived through {@link
   * TrajectoryArc#of}, the two contexts collapse onto one arc, which is the truth.
   */
  @Test
  void perturbersDoNotChangeTheArc() {
    GravitationalContext coast = GravitationalContext.earth();
    GravitationalContext burn =
        GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN);

    assertNotEquals(coast, burn, "the two contexts really are different");
    assertEquals(
        TrajectoryArc.of(coast), TrajectoryArc.of(burn), "but they are the same stretch of flight");
  }

  @Test
  void anArcIsIdentifiedByItsBodyAndItsFrame() {
    TrajectoryArc earth = TrajectoryArc.earth();

    assertEquals(new TrajectoryArc(SolarSystemBody.EARTH, earth.frame()), earth);
    assertNotEquals(new TrajectoryArc(SolarSystemBody.MOON, earth.frame()), earth);
  }

  /**
   * The conversion PHY-4 / L5 rests on, checked against a geometry known independently of it: the
   * selenocentric origin <em>is</em> the Moon, so converting it into the Earth arc must return the
   * Moon's geocentric position at that date. Asserting the formula against Orekit's own ephemeris
   * rather than against a recomputation of itself.
   */
  @Test
  void theSelenocentricOriginConvertsToWhereTheMoonIs() {
    AbsoluteDate date = new AbsoluteDate(2026, 3, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
    TrajectoryArc moon = TrajectoryArc.forBody(SolarSystemBody.MOON);

    Vector3D converted = moon.convertPosition(Vector3D.ZERO, date, TrajectoryArc.earth());
    Vector3D expected =
        OrekitService.get()
            .body(SolarSystemBody.MOON)
            .getPosition(date, OrekitService.get().gcrf());

    assertEquals(0.0, converted.subtract(expected).getNorm(), 1.0, "within a metre of the Moon");
    assertEquals(
        384_400_000.0,
        converted.getNorm(),
        30_000_000.0,
        "and at a plausible lunar distance, so a sign error could not pass");
  }

  /**
   * Both frames are body-centred with ICRF axes, so the conversion is a pure translation and has
   * nothing to round on the position — L4 §7.3 measured the same round trip at exactly 0 m. The
   * tolerance here is a nanometre only because the two directions are composed rather than compared
   * to a reference.
   */
  @Test
  void convertingThereAndBackIsExact() {
    AbsoluteDate date = new AbsoluteDate(2026, 3, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
    TrajectoryArc moon = TrajectoryArc.forBody(SolarSystemBody.MOON);
    Vector3D perilune = new Vector3D(1_837_000.0, -420_000.0, 96_000.0);

    Vector3D geocentric = moon.convertPosition(perilune, date, TrajectoryArc.earth());
    Vector3D back = TrajectoryArc.earth().convertPosition(geocentric, date, moon);

    assertEquals(0.0, back.subtract(perilune).getNorm(), 1e-9, "a translation is reversible");
  }

  /**
   * The short circuit that keeps every trajectory flown before L6 bit-for-bit unconverted: same
   * frame instance, argument returned untouched, no {@code Transform} built at all.
   */
  @Test
  void aSameBodyConversionReturnsTheArgumentItself() {
    Vector3D leo = new Vector3D(4_512_758.3, -3_981_204.7, 2_874_611.9);
    AbsoluteDate date = new AbsoluteDate(2026, 3, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());

    assertSame(
        leo,
        TrajectoryArc.earth().convertPosition(leo, date, TrajectoryArc.forBody(SolarSystemBody.EARTH)),
        "identity by reference, not by value");
  }

  @Test
  void neitherComponentMayBeNull() {
    assertThrows(
        NullPointerException.class,
        () -> new TrajectoryArc(null, TrajectoryArc.earth().frame()));
    assertThrows(
        NullPointerException.class, () -> new TrajectoryArc(SolarSystemBody.EARTH, null));
  }
}
