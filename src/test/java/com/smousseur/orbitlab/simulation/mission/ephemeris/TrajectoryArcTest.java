package com.smousseur.orbitlab.simulation.mission.ephemeris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

  @Test
  void neitherComponentMayBeNull() {
    assertThrows(
        NullPointerException.class,
        () -> new TrajectoryArc(null, TrajectoryArc.earth().frame()));
    assertThrows(
        NullPointerException.class, () -> new TrajectoryArc(SolarSystemBody.EARTH, null));
  }
}
