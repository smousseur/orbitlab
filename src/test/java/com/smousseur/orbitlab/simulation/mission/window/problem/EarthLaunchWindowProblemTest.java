package com.smousseur.orbitlab.simulation.mission.window.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.NodeBranch;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSearch;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSolver;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

/**
 * MIS-2 — the Earth launch window, the first criterion of this chantier that has a window at all.
 *
 * <p><b>Every number below is derived, none is recorded.</b> The target plane is taken to be the
 * one the pad itself reaches at a chosen instant, which makes that instant an alignment by
 * construction; the recurrence is then a sidereal day, the slot width is the margin divided by the
 * slope {@code v·sin(i)·ω⊕}, and the cost half a day later is {@code 2·v·sin(i)}. A test written on
 * recorded outputs could not tell any of those three apart from a plausible bug.
 */
class EarthLaunchWindowProblemTest {

  /** Kourou, the latitude every mission in the catalog launches from. */
  private static final double KOUROU_LATITUDE = 5.23;

  private static final double KOUROU_LONGITUDE = -52.77;

  /** A mid-latitude pad, to size the geodetic-versus-geocentric floor where it is largest. */
  private static final double MID_LATITUDE = 45.0;

  private static final double INCLINATION = FastMath.toRadians(51.6);

  private static final double SEMI_MAJOR_AXIS =
      Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 400_000.0;

  private static final double SIDEREAL_DAY = 86_164.0905;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  private static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 3, 21, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  /** The orbital speed the plane change is paid at. */
  private static double orbitalVelocity() {
    return FastMath.sqrt(Constants.WGS84_EARTH_MU / SEMI_MAJOR_AXIS);
  }

  /**
   * How fast the cost climbs on either side of an alignment, {@code v·sin(i)·ω⊕} — the linearised
   * form of the problem's exact geometry, and therefore an independent prediction of it.
   */
  private static double slopePerSecond() {
    return orbitalVelocity()
        * FastMath.sin(INCLINATION)
        * Constants.WGS84_EARTH_ANGULAR_VELOCITY;
  }

  /** The node of a plane normal: the inverse of {@code EarthLaunchWindowProblem.planeNormal}. */
  private static double raanOf(Vector3D normal) {
    return FastMath.atan2(normal.getX(), -normal.getY());
  }

  /**
   * A problem whose target plane is the one {@code pad} reaches at {@code alignment} — so that
   * instant is an alignment, and the rest of the day is measured against it.
   */
  private static EarthLaunchWindowProblem alignedAt(
      AbsoluteDate alignment, double latitude, double longitude, NodeBranch branch) {
    LaunchPlane plane = new LaunchPlane(INCLINATION, branch);
    EarthLaunchWindowProblem probe =
        new EarthLaunchWindowProblem(latitude, longitude, 0.0, plane, 0.0, SEMI_MAJOR_AXIS);
    double raan = raanOf(probe.reachablePlaneNormal(alignment));
    return new EarthLaunchWindowProblem(
        latitude, longitude, 0.0, plane, raan, SEMI_MAJOR_AXIS);
  }

  private static EarthLaunchWindowProblem alignedAt(AbsoluteDate alignment) {
    return alignedAt(alignment, KOUROU_LATITUDE, KOUROU_LONGITUDE, NodeBranch.ASCENDING);
  }

  @Test
  @DisplayName("The alignment recurs once per sidereal day, not once per solar day")
  void theAlignmentRecursOncePerSiderealDay() {
    AbsoluteDate alignment = epoch();
    EarthLaunchWindowProblem problem = alignedAt(alignment);
    LaunchWindowSearch search =
        new LaunchWindowSearch(
            alignment.shiftedBy(-6.0 * 3_600.0),
            Duration.ofSeconds(Math.round(3.0 * SIDEREAL_DAY)),
            problem.coarseStep(),
            problem.refinementPrecision(),
            1.0e9,
            50.0,
            5);

    List<LaunchWindow> windows = new LaunchWindowSolver(problem).solve(search);

    assertEquals(3, windows.size(), "three sidereal days, three alignments");
    List<AbsoluteDate> dates =
        windows.stream().map(LaunchWindow::date).sorted(Comparator.naturalOrder()).toList();
    assertEquals(
        SIDEREAL_DAY,
        dates.get(1).durationFrom(dates.get(0)),
        60.0,
        "consecutive windows are one sidereal day apart, not one solar day");
    assertEquals(SIDEREAL_DAY, dates.get(2).durationFrom(dates.get(1)), 60.0);
    // A solar day would be 86 400 s: the 236 s difference is what this assertion is about, and it
    // is thirty times the tolerance.
    assertEquals(
        0.0,
        dates.get(0).durationFrom(alignment),
        60.0,
        "the first window sits on the instant the target plane was taken from");
  }

  @Test
  @DisplayName("The slot is as wide as the margin divided by the rate the cost climbs at")
  void theSlotWidthIsTheMarginOverTheSlope() {
    // This is the assertion that makes the criterion usable: 50 m/s of margin buys ±114 s, so a
    // launch window here is minutes wide where the translunar one was the whole search range.
    AbsoluteDate alignment = epoch();
    EarthLaunchWindowProblem problem = alignedAt(alignment);
    double margin = 50.0;
    LaunchWindowSearch search =
        new LaunchWindowSearch(
            alignment.shiftedBy(-6.0 * 3_600.0),
            Duration.ofSeconds(Math.round(SIDEREAL_DAY)),
            problem.coarseStep(),
            problem.refinementPrecision(),
            1.0e9,
            margin,
            1);

    LaunchWindow window = new LaunchWindowSolver(problem).solve(search).getFirst();

    double expected = 2.0 * margin / slopePerSecond();
    assertEquals(
        expected,
        window.duration().toSeconds(),
        0.05 * expected,
        "the slot must be 2·margin/(v·sin i·ω), got " + window.duration());
    assertTrue(
        window.duration().toSeconds() > 180 && window.duration().toSeconds() < 300,
        "and that is minutes, not hours: " + window.duration());
  }

  @Test
  @DisplayName("Half a sidereal day from the alignment the plane change costs 2·v·sin(i)")
  void theFarSideOfTheDayCostsAFullPlaneChange() {
    AbsoluteDate alignment = epoch();
    EarthLaunchWindowProblem problem = alignedAt(alignment);

    double cost = problem.evaluate(alignment.shiftedBy(0.5 * SIDEREAL_DAY)).deltaV();

    double expected = 2.0 * orbitalVelocity() * FastMath.sin(INCLINATION);
    assertEquals(
        expected,
        cost,
        0.01 * expected,
        "the node is 180° away, so the planes are 2i apart and the cost is the full rotation");
    assertTrue(cost > 11_000.0, "a criterion with this much relief is what makes a window sharp");
  }

  @Test
  @DisplayName("The floor is the geodetic-geocentric inclination gap, and it grows with latitude")
  void theCostFloorComesFromTheLatitudeConvention() {
    // The azimuth is derived from the geodetic latitude on a spherical Earth; the plane is raised
    // on the pad's true inertial position, whose geocentric latitude is lower. The gap is nil at
    // the equator and largest near 45°, and it is a constant offset in time — which is why the
    // acceptance threshold has to be relative.
    AbsoluteDate alignment = epoch();
    double atKourou = alignedAt(alignment).evaluate(alignment).deltaV();
    double atMidLatitude =
        alignedAt(alignment, MID_LATITUDE, 0.0, NodeBranch.ASCENDING).evaluate(alignment).deltaV();

    // Measured at 1.14 m/s from Kourou and 35.9 m/s from 45°, on a 400 km orbit at 51.6°.
    assertTrue(
        atKourou < 2.0,
        "near the equator the two latitudes almost coincide, floor is " + atKourou + " m/s");
    assertTrue(
        atMidLatitude > 5.0 * atKourou,
        "at 45° the gap is an order of magnitude larger, floor is " + atMidLatitude + " m/s");
    assertTrue(
        atMidLatitude < 50.0,
        "but it stays tens of m/s, not hundreds: " + atMidLatitude + " m/s");
  }

  @Test
  @DisplayName("The two node branches align at different instants of the same day")
  void theTwoBranchesAlignAtDifferentInstants() {
    // The site crosses the target plane twice a day; the two crossings are served by the two
    // azimuths, so a mission that fixes its branch gets one window per sidereal day and not two.
    AbsoluteDate alignment = epoch();
    EarthLaunchWindowProblem ascending = alignedAt(alignment);
    LaunchPlane descendingPlane = new LaunchPlane(INCLINATION, NodeBranch.DESCENDING);
    double raan = raanOf(ascending.reachablePlaneNormal(alignment));
    EarthLaunchWindowProblem descending =
        new EarthLaunchWindowProblem(
            KOUROU_LATITUDE, KOUROU_LONGITUDE, 0.0, descendingPlane, raan, SEMI_MAJOR_AXIS);

    LaunchWindowSearch search =
        LaunchWindowSearch.over(
                alignment.shiftedBy(-6.0 * 3_600.0),
                Duration.ofSeconds(Math.round(SIDEREAL_DAY)),
                descending,
                1.0e9)
            .withMargin(50.0);
    LaunchWindow other = new LaunchWindowSolver(descending).solve(search).getFirst();

    double separation = other.date().durationFrom(alignment);
    assertTrue(
        FastMath.abs(separation) > 600.0,
        "the descending branch cannot align at the same instant, separation is " + separation + " s");
    assertTrue(
        FastMath.abs(separation) < SIDEREAL_DAY,
        "but it does align within the same sidereal day");
  }

  @Test
  @DisplayName("An inclination the pad cannot reach is refused at construction")
  void anUnreachableInclinationIsRefusedAtConstruction() {
    // Not a per-epoch refusal: no instant of any day makes a 3° orbit reachable from 5.23°, so the
    // caller is told when it asks, not once per sample.
    LaunchPlane tooFlat = new LaunchPlane(FastMath.toRadians(3.0), NodeBranch.ASCENDING);

    assertThrows(
        OrbitlabException.class,
        () ->
            new EarthLaunchWindowProblem(
                KOUROU_LATITUDE, KOUROU_LONGITUDE, 0.0, tooFlat, 0.0, SEMI_MAJOR_AXIS));
  }

  @Test
  @DisplayName("The recurrence is one sidereal day, derived from the rotation rate")
  void theRecurrenceIsOneSiderealDay() {
    EarthLaunchWindowProblem problem =
        new EarthLaunchWindowProblem(
            5.23,
            -52.77,
            0.0,
            new LaunchPlane(FastMath.toRadians(51.6), NodeBranch.ASCENDING),
            FastMath.toRadians(120.0),
            Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 400_000.0);

    // Orekit's WGS84_EARTH_ANGULAR_VELOCITY is rounded to 7.292115e-5 (7 significant figures),
    // so 2·pi/omega lands 0.0101 s from the textbook 86 164.0905 s; the tolerance absorbs that
    // upstream rounding rather than the true sidereal day.
    assertEquals(86_164.0905, problem.recurrence().toMillis() / 1000.0, 0.02);
  }

  @Test
  @DisplayName("The search adopts both of the problem's scales, an hour and a second")
  void theSearchAdoptsBothScales() {
    // The sweep only has to bracket a minimum that recurs daily; the refinement has to resolve a
    // slot minutes wide. A tenth of the sweep step would have asked for six minutes.
    EarthLaunchWindowProblem problem = alignedAt(epoch());

    LaunchWindowSearch search =
        LaunchWindowSearch.over(epoch(), Duration.ofDays(2), problem, 1.0e9);

    assertEquals(Duration.ofHours(1), search.step());
    assertEquals(Duration.ofSeconds(1), search.precision());
  }
}
