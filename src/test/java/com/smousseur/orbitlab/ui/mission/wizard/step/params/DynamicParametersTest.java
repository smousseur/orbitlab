package com.smousseur.orbitlab.ui.mission.wizard.step.params;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.MissionHorizon;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.utils.Constants;

/**
 * The wizard's derived mission duration, once it stopped assuming the Earth (MIS-5 / L2, spec
 * {@code docs/lunar-orbit/04-conception-L2.md} §5 and §7.3).
 *
 * <p>{@code revolutionDays} is {@code static}, so nothing here builds a Lemur {@code Container} and
 * no JME context is needed. Orekit is initialised because {@code GravitationalContext} resolves
 * frames and an ellipsoid — the same {@code @BeforeAll} four other UI tests already carry.
 */
class DynamicParametersTest {

  /** The Moon's equatorial radius, as {@code GravitationalContext.moon()} carries it. */
  private static final double MOON_RADIUS = 1_737_400.0;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  /**
   * The terrestrial non-regression, <b>at tolerance zero</b>: {@code GravitationalContext.earth()}
   * carries the WGS84 radius and µ themselves, so the wizard's line reads the same doubles it read
   * before the lot — an identity of the constants, not an agreement between two computations.
   *
   * <p>Zero tolerance is safe here for the reason it is in {@code OrbitElementsTest}: this is
   * arithmetic on two constants, with no frame cache and no shared gravity model to make the result
   * depend on which other test classes ran first.
   */
  @Test
  void revolutionDays_onEarth_isBitIdenticalToThePreviousArithmetic() {
    double altitude = 550_000.0;
    double a = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + altitude;
    double period = 2.0 * Math.PI * Math.sqrt(a * a * a / Constants.WGS84_EARTH_MU);
    double before =
        MissionHorizon.DEFAULT_LEO_REVOLUTIONS * period / MissionHorizon.SECONDS_PER_DAY;

    double after =
        DynamicParameters.revolutionDays(
            MissionHorizon.DEFAULT_LEO_REVOLUTIONS, altitude, GravitationalContext.earth());

    assertEquals(before, after, 0.0);
  }

  /**
   * And the case the lot exists for: a panel that names the Moon gets lunar revolutions. Twelve
   * turns at 100 km is what {@code MIS-5 / L5} flies — 7 067 s a turn, 0.98 day in all — against
   * the 0.039 day the Earth constants would have shown.
   */
  @Test
  void revolutionDays_onTheMoon_countsLunarRevolutions() {
    double altitude = 100_000.0;
    double a = MOON_RADIUS + altitude;
    double lunarPeriod =
        2.0 * FastMath.PI * FastMath.sqrt(a * a * a / GravitationalContext.moon().mu());

    double days = DynamicParameters.revolutionDays(12, altitude, GravitationalContext.moon());

    assertEquals(12 * lunarPeriod / MissionHorizon.SECONDS_PER_DAY, days, 1.0e-9);
    assertEquals(0.982, days, 0.005);
  }
}
