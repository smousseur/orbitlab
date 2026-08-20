package com.smousseur.orbitlab.simulation.mission.window.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.operation.NodeBranch;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import java.util.List;
import java.util.Optional;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

/**
 * MIS-2 — what the wizard's launch date becomes once a mission names a target node.
 *
 * <p>The properties asserted here are the ones a user would notice: a mission that waits for
 * nothing keeps its date, a mission that waits for a plane gets the next opening, and asking again
 * from inside a window does not push the launch to tomorrow. The remaining tests cover the newer
 * {@link EarthLaunchWindowRequest} adapter: that it reaches the same optimum as the spec it is read
 * from, and that its semi-major axis is genuinely derived from both altitudes rather than copying
 * one of them; and the wizard timeline's own two needs: that successive opportunities are spaced a
 * sidereal day apart, and that an optimum handed back as a floor resolves to itself.
 */
class EarthLaunchWindowPlannerTest {

  private static final double KOUROU_LATITUDE = 5.23;
  private static final double KOUROU_LONGITUDE = -52.77;
  private static final double SIDEREAL_DAY = 86_164.0905;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  private static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 3, 21, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  private static MissionSpec.EarthOrbit spec(Double raanDeg) {
    return spec(raanDeg, 400_000.0, 400_000.0);
  }

  private static MissionSpec.EarthOrbit spec(
      Double raanDeg, double perigeeAltitude, double apogeeAltitude) {
    return new MissionSpec.EarthOrbit(
        "MIS-2",
        LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY),
        perigeeAltitude,
        apogeeAltitude,
        FastMath.toRadians(51.6),
        NodeBranch.ASCENDING,
        raanDeg,
        "Kourou",
        KOUROU_LATITUDE,
        KOUROU_LONGITUDE,
        0.0,
        null);
  }

  @Test
  @DisplayName("A mission that names no plane has no window, and keeps the date it was given")
  void aMissionWithoutATargetNodeHasNoWindow() {
    assertTrue(EarthLaunchWindowPlanner.nextOpportunity(spec(null), epoch()).isEmpty());
  }

  @Test
  @DisplayName("A mission that names a plane launches at the next opening, within a sidereal day")
  void theOpportunityIsTheNextOneAfterTheRequestedDate() {
    AbsoluteDate requested = epoch();

    LaunchWindow window =
        EarthLaunchWindowPlanner.nextOpportunity(spec(120.0), requested).orElseThrow();

    double wait = window.date().durationFrom(requested);
    assertTrue(wait >= 0.0, "the requested date is a floor, not a suggestion, waited " + wait);
    assertTrue(
        wait <= SIDEREAL_DAY,
        "the pad meets the plane once a day, so nothing justifies waiting longer than " + wait);
    assertTrue(
        window.opening().compareTo(window.date()) <= 0
            && window.closing().compareTo(window.date()) >= 0,
        "the optimum sits inside its own slot");
  }

  @Test
  @DisplayName("Ten minutes past an opening, the answer is tomorrow's window")
  void pastTheWindowTheAnswerIsTheNextDay() {
    // Ten minutes is far outside a slot measured at 3 min 52 s, so the cost at the requested date
    // is some 260 m/s: the boundary of the search range is a minimum of the sampled grid, but it is
    // not within the margin of the real alignment, and the solver withdraws it.
    AbsoluteDate requested = epoch();
    LaunchWindow first =
        EarthLaunchWindowPlanner.nextOpportunity(spec(120.0), requested).orElseThrow();

    LaunchWindow next =
        EarthLaunchWindowPlanner.nextOpportunity(spec(120.0), first.date().shiftedBy(600.0))
            .orElseThrow();

    assertEquals(
        SIDEREAL_DAY,
        next.date().durationFrom(first.date()),
        120.0,
        "the next opening of the same plane is one sidereal day later");
  }

  @Test
  @DisplayName("A second past an opening, the answer is still that opening")
  void insideTheWindowTheAnswerIsImmediate() {
    // The other side of the same rule, and the one that matters operationally: a request landing
    // inside a slot must not push the launch a day back for a metre per second.
    AbsoluteDate requested = epoch();
    LaunchWindow first =
        EarthLaunchWindowPlanner.nextOpportunity(spec(120.0), requested).orElseThrow();
    AbsoluteDate justAfter = first.date().shiftedBy(1.0);

    Optional<LaunchWindow> still = EarthLaunchWindowPlanner.nextOpportunity(spec(120.0), justAfter);

    assertTrue(still.isPresent());
    assertTrue(
        still.get().date().durationFrom(justAfter) < 60.0,
        "still inside the slot, so the launch is now — waited "
            + still.get().date().durationFrom(justAfter)
            + " s");
  }

  @Test
  @DisplayName("The request built from a spec reaches the same optimum as the spec itself")
  void theRequestReachesTheSameOptimumAsTheSpec() {
    MissionSpec.EarthOrbit spec = spec(120.0);
    Optional<LaunchWindow> fromSpec = EarthLaunchWindowPlanner.nextOpportunity(spec, epoch());
    List<LaunchWindow> fromRequest =
        EarthLaunchWindowPlanner.nextOpportunities(
            EarthLaunchWindowRequest.from(spec), epoch(), 1);

    assertTrue(fromSpec.isPresent());
    assertEquals(1, fromRequest.size());
    assertEquals(
        0.0, fromRequest.getFirst().date().durationFrom(fromSpec.get().date()), 2.0);
  }

  @Test
  @DisplayName("The semi-major axis is the target ellipse's, not one of its altitudes")
  void theSemiMajorAxisIsDerivedFromBothAltitudes() {
    // Deliberately elliptical: on a circular target 0.5 * (perigee + apogee) and perigee are the
    // same number, so a dropped factor would pass unseen.
    MissionSpec.EarthOrbit elliptical = spec(120.0, 200_000.0, 600_000.0);

    assertEquals(
        Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 400_000.0,
        EarthLaunchWindowRequest.from(elliptical).semiMajorAxis(),
        1.0);
  }

  @Test
  @DisplayName("Three opportunities come back one sidereal day apart")
  void threeOpportunitiesAreOneSiderealDayApart() {
    List<LaunchWindow> windows =
        EarthLaunchWindowPlanner.nextOpportunities(
            EarthLaunchWindowRequest.from(spec(120.0)), epoch(), 3);

    assertEquals(3, windows.size());
    for (int i = 1; i < windows.size(); i++) {
      double gap = windows.get(i).date().durationFrom(windows.get(i - 1).date());
      assertTrue(gap > 0.0, "the opportunities must be in chronological order");
      assertEquals(SIDEREAL_DAY, gap, 60.0);
    }
  }

  @Test
  @DisplayName("Asking again from a window's optimum returns that same instant")
  void anOptimumTakenAsAFloorReturnsItself() {
    MissionSpec.EarthOrbit spec = spec(120.0);
    List<LaunchWindow> windows =
        EarthLaunchWindowPlanner.nextOpportunities(
            EarthLaunchWindowRequest.from(spec), epoch(), 3);
    AbsoluteDate chosen = windows.get(1).date();

    Optional<LaunchWindow> again = EarthLaunchWindowPlanner.nextOpportunity(spec, chosen);

    assertTrue(again.isPresent());
    assertEquals(0.0, again.get().date().durationFrom(chosen), 2.0);
  }
}
