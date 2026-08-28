package com.smousseur.orbitlab.simulation;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * Unit oracle for the flown-band aim. Synthetic orbits only — no mission, no numerical propagation,
 * tens of milliseconds (spec orbit-reporting/02 section 6.2).
 */
class FlownBandAimTest {

  private static final double RE = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static final double MU = Constants.WGS84_EARTH_MU;
  private static final double INCLINATION = FastMath.toRadians(5.23);

  /** The Eckstein-Hechler modelling residual measured in spec 01 section 3.2.2. */
  private static final double MEAN_RESIDUAL_M = 1_000.0;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  /**
   * The contract: the aim it returns produces an orbit whose mean semi-major axis is the requested
   * band centre plus {@code a·f}, to within Eckstein-Hechler's own residual. Checked at three
   * altitudes so a single lucky profile cannot carry the test.
   */
  @Test
  void aimLandsTheMeanSemiMajorAxisOnTarget() {
    for (double altitude : new double[] {400_000.0, 600_000.0, 1_000_000.0}) {
      double centreRadius = RE + altitude;
      double aim =
          FlownBandAim.resolve(centreRadius, centreRadius, r -> aimedOrbit(r, centreRadius));

      double expected = centreRadius + FlownBandAim.closedFormOffset(centreRadius);
      OrbitElements mean =
          OrbitElements.mean(aimedOrbit(aim, centreRadius), RE)
              .orElseThrow(() -> new AssertionError("mean unavailable at " + centreRadius));
      double miss = mean.semiMajorAxis() - expected;
      Assertions.assertTrue(
          FastMath.abs(miss) < MEAN_RESIDUAL_M,
          () ->
              String.format(
                  "mean semi-major axis missed by %.0f m at %.0f km", miss, altitude / 1000.0));
    }
  }

  /**
   * The aim must lift the orbit by roughly TWICE the offset: it moves the semi-major axis at half
   * its own rate, so centring the band on {@code a·f} costs {@code 2·a·f} of shaping radius. This
   * is the property that distinguishes this criterion from the mean-perigee one it replaced, which
   * lifted by only {@code a·f} and left the request at the top of the band.
   */
  @Test
  void aimLiftsByTwiceTheOffset() {
    double centreRadius = RE + 400_000.0;
    double aim = FlownBandAim.resolve(centreRadius, centreRadius, r -> aimedOrbit(r, centreRadius));

    double lift = aim - centreRadius;
    double expected = 2.0 * FlownBandAim.closedFormOffset(centreRadius);
    Assertions.assertEquals(
        expected,
        lift,
        2_000.0,
        () -> String.format("lift %.0f m, expected about %.0f m", lift, expected));
  }

  /**
   * The fixed point must actually iterate, not silently coast on its closed-form seed. Nothing else
   * in this class separates the two: a seed that happened to be good enough would satisfy the
   * contract test while the Eckstein-Hechler refinement was dead code.
   */
  @Test
  void theFixedPointImprovesOnItsClosedFormSeed() {
    double centreRadius = RE + 400_000.0;
    double targetMeanA = centreRadius + FlownBandAim.closedFormOffset(centreRadius);
    double seed = 2.0 * targetMeanA - centreRadius;
    double resolved =
        FlownBandAim.resolve(centreRadius, centreRadius, r -> aimedOrbit(r, centreRadius));

    double seedMiss = meanSemiMajorAxisMiss(seed, centreRadius, targetMeanA);
    double resolvedMiss = meanSemiMajorAxisMiss(resolved, centreRadius, targetMeanA);
    Assertions.assertTrue(
        resolvedMiss < seedMiss,
        () ->
            String.format(
                "the refinement did not improve on its seed: seed misses %.0f m, resolved"
                    + " misses %.0f m",
                seedMiss, resolvedMiss));
  }

  /**
   * Forced fallback: an aimed orbit Eckstein-Hechler refuses (an eccentricity far beyond its
   * near-circular domain) must NOT fail the mission — it must fall back on the closed-form seed.
   * This is the property that keeps the targeting path total (spec 02 section 3.2).
   */
  @Test
  void fallsBackOnTheClosedFormWhenTheMeanIsUnavailable() {
    Assumptions.assumeTrue(
        OrbitElements.mean(veryEccentricOrbit(), RE).isEmpty(),
        "Eckstein-Hechler accepted the eccentric orbit; the fallback cannot be forced this way");

    double centreRadius = RE + 400_000.0;
    double targetMeanA = centreRadius + FlownBandAim.closedFormOffset(centreRadius);
    double aim = FlownBandAim.resolve(centreRadius, centreRadius, r -> veryEccentricOrbit());
    Assertions.assertEquals(2.0 * targetMeanA - centreRadius, aim, 1.0e-6);
  }

  /** The closed form against the values measured in spec 01 section 2 and spec 02 section 5 P3. */
  @Test
  void closedFormMatchesTheMeasuredTable() {
    Assertions.assertEquals(9_746.0, FlownBandAim.closedFormOffset(RE + 400_000.0), 10.0);
    Assertions.assertEquals(9_467.0, FlownBandAim.closedFormOffset(RE + 600_000.0), 10.0);
    Assertions.assertEquals(1_567.0, FlownBandAim.closedFormOffset(42_164_000.0), 10.0);
  }

  private static double meanSemiMajorAxisMiss(double aim, double apsisRadius, double targetMeanA) {
    OrbitElements mean =
        OrbitElements.mean(aimedOrbit(aim, apsisRadius), RE)
            .orElseThrow(() -> new AssertionError("mean unavailable for aim " + aim));
    return FastMath.abs(mean.semiMajorAxis() - targetMeanA);
  }

  /**
   * The orbit an aim of {@code shapingRadius} would produce for a burn at {@code apsisRadius}:
   * velocity perpendicular to the radius, magnitude from vis-viva on {@code a = (shaping +
   * apsis)/2}. Mirrors {@code AnalyticHohmannTransferStage#computeTargetVelocityAtApogee} for the
   * equatorial-normal case, without dragging the mission packages into a unit test.
   */
  private static Orbit aimedOrbit(double shapingRadius, double apsisRadius) {
    double a = 0.5 * (shapingRadius + apsisRadius);
    double v = FastMath.sqrt(MU * (2.0 / apsisRadius - 1.0 / a));
    Vector3D position = new Vector3D(apsisRadius, 0.0, 0.0);
    Vector3D velocity =
        new Vector3D(0.0, v * FastMath.cos(INCLINATION), v * FastMath.sin(INCLINATION));
    return new KeplerianOrbit(new PVCoordinates(position, velocity), frame(), date(), MU);
  }

  /** An orbit outside Eckstein-Hechler's domain, so {@code OrbitElements.mean()} returns empty. */
  private static Orbit veryEccentricOrbit() {
    return new KeplerianOrbit(
        RE + 4_000_000.0,
        0.6,
        INCLINATION,
        0.0,
        0.0,
        0.0,
        PositionAngleType.TRUE,
        frame(),
        date(),
        MU);
  }

  private static Frame frame() {
    return OrekitService.get().gcrf();
  }

  private static AbsoluteDate date() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }
}
