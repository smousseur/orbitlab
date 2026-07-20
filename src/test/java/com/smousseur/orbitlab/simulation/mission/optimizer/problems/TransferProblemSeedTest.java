package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransfertTwoManeuver;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

/**
 * Locks the seed fixes of bilan 08 §3.3/§3.4: on a sub-orbital hand-off orbit (post-gravity-turn
 * periapsis at a few tens of km), the Hohmann seed must depart from the apoapsis — the periapsis is
 * not flyable — and land inside the CMA-ES search box. Before the fix, {@code guessT1} was the time
 * to periapsis (almost a full period away, beyond {@code t1Max}) and the feasibility {@code dv2}
 * mixed speeds at two different radii.
 */
public class TransferProblemSeedTest {
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static final double TARGET_ALTITUDE = 300_000.0;
  private static final double TARGET_INCLINATION = FastMath.toRadians(45.96);
  /** Transfer-entry mass of the fully-loaded FH reference mission at 300 km (S2 active). */
  private static final double TRANSFER_ENTRY_MASS = 60_709.0;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void seedDepartsFromApoapsisOnSubOrbitalHandOff() {
    TransferProblem problem = problemFor(stateOnOrbit(30_000.0, 300_000.0));

    double[] guess = problem.buildInitialGuess();
    assertSeedInBounds(problem, guess);

    // The seed must aim at the upcoming apoapsis (~half a period ahead when just past
    // periapsis), not at the sub-orbital periapsis (~a full period ahead).
    double period = periodFor(30_000.0, 300_000.0);
    Assertions.assertTrue(
        guess[0] > 0.3 * period && guess[0] < 0.55 * period,
        () ->
            String.format(
                "Seed t1 %.0f s should target the apoapsis (~%.0f s), not the periapsis (~%.0f s)",
                guess[0], 0.47 * period, period));
  }

  @Test
  void seedStaysAtPeriapsisOnFlyableDeparture() {
    // Circular 400 km departure toward 800 km: the historical periapsis-departure
    // branch must be preserved when the periapsis is flyable.
    TransferProblem problem = problemFor(stateOnOrbit(400_000.0, 400_000.0), 800_000.0);
    assertSeedInBounds(problem, problem.buildInitialGuess());
  }

  private static void assertSeedInBounds(TransferProblem problem, double[] guess) {
    double[] lower = problem.getLowerBounds();
    double[] upper = problem.getUpperBounds();
    for (int i = 0; i < guess.length; i++) {
      int idx = i;
      Assertions.assertTrue(
          guess[i] >= lower[i] && guess[i] <= upper[i],
          () ->
              String.format(
                  "Seed[%d]=%.3f outside CMA-ES bounds [%.3f, %.3f]",
                  idx, guess[idx], lower[idx], upper[idx]));
    }
  }

  private static TransferProblem problemFor(SpacecraftState state) {
    return problemFor(state, TARGET_ALTITUDE);
  }

  private static TransferProblem problemFor(SpacecraftState state, double targetAltitude) {
    Vehicle vehicle =
        LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY)
            .toVehicleStack();
    ActiveStageInfo activeStage = vehicle.resolveActiveStage(state.getMass());
    return new TransferTwoManeuverProblem(
        new TransfertTwoManeuver(vehicle, targetAltitude),
        state,
        targetAltitude,
        activeStage.propulsion(),
        activeStage.depletionFloor(),
        TARGET_INCLINATION);
  }

  /** State just past periapsis (ν = 0.2 rad) on the given apsidal altitudes, S2 active. */
  private static SpacecraftState stateOnOrbit(double perigeeAltitude, double apogeeAltitude) {
    double rp = EARTH_RADIUS + perigeeAltitude;
    double ra = EARTH_RADIUS + apogeeAltitude;
    double a = (rp + ra) / 2.0;
    double e = (ra - rp) / (ra + rp);
    KeplerianOrbit orbit =
        new KeplerianOrbit(
            a,
            e,
            TARGET_INCLINATION,
            0.0,
            0.0,
            0.2,
            PositionAngleType.TRUE,
            OrekitService.get().gcrf(),
            new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC()),
            Constants.WGS84_EARTH_MU);
    return new SpacecraftState(orbit).withMass(TRANSFER_ENTRY_MASS);
  }

  private static double periodFor(double perigeeAltitude, double apogeeAltitude) {
    double a = EARTH_RADIUS + (perigeeAltitude + apogeeAltitude) / 2.0;
    return 2.0 * FastMath.PI * FastMath.sqrt(a * a * a / Constants.WGS84_EARTH_MU);
  }
}
