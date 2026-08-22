package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransfertTwoManeuver;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
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
 * Locks the depletion contract of the transfer cost: a candidate that burns its stage to flame-out
 * must grade <em>decisively</em> worse than one reaching the same orbit with a real residual left.
 *
 * <p>Measured on the 550 km LEO run of 2026-08-22, where the contract did not hold: five λ
 * evaluations out of seven converged to a cost of {@code 5.000000xxx e-3} — the I7 term pinned at
 * its {@code W_PROPELLANT} cap plus an orbital part of ~7e-10 — i.e. exact flame-out with a
 * perfect osculating orbit, against 4.44e-3 for the sober solution. A 13 % edge is not enough for
 * CMA-ES to prefer the sober basin reliably, and the outer λ-bisection reads the flame-out as a
 * zero residual: λ=0.7375 came back feasible (residual 16.9 %) while λ=0.7156 came back infeasible
 * (residual 0 %), on a load where a sober trajectory exists.
 *
 * <p>The two candidates below fly the same arrival orbit, so the orbital part of the cost is a
 * shared constant and the comparison isolates the propellant grading.
 */
public class TransferProblemDepletionCostTest {
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static final double TARGET_ALTITUDE = 300_000.0;
  private static final double TARGET_INCLINATION = FastMath.toRadians(45.96);

  /** Transfer-entry mass of the fully-loaded FH reference mission at 300 km (S2 active). */
  private static final double TRANSFER_ENTRY_MASS = 60_709.0;

  /**
   * How much worse flame-out must grade than the sober candidate. An order of magnitude puts it
   * out of reach of the exploration noise that made the two basins interchangeable.
   */
  private static final double MIN_REJECTION_RATIO = 10.0;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void flameOutGradesAnOrderOfMagnitudeWorseThanASoberTransfer() {
    SpacecraftState entry = handOffState();
    TransferProblem problem = problemFor(entry);
    double depletionFloor = depletionFloorFor(entry);

    double flameOutCost = problem.computeCost(arrivalState(entry, depletionFloor));
    double soberCost =
        problem.computeCost(
            arrivalState(entry, depletionFloor + 0.5 * (TRANSFER_ENTRY_MASS - depletionFloor)));

    System.out.printf(
        "flame-out cost=%.6e, sober cost=%.6e, ratio=%.2f%n",
        flameOutCost, soberCost, flameOutCost / soberCost);

    Assertions.assertTrue(
        flameOutCost >= MIN_REJECTION_RATIO * soberCost,
        () ->
            String.format(
                "Flame-out graded %.6e against %.6e for the sober transfer (ratio %.2f): the "
                    + "propellant grading must reject depletion by at least %.0fx",
                flameOutCost, soberCost, flameOutCost / soberCost, MIN_REJECTION_RATIO));
  }

  private static TransferProblem problemFor(SpacecraftState state) {
    Vehicle vehicle = vehicle();
    ActiveStageInfo activeStage = vehicle.resolveActiveStage(state.getMass());
    return new TransferTwoManeuverProblem(
        new TransfertTwoManeuver(vehicle, TARGET_ALTITUDE, FlightContext.earth()),
        state,
        TARGET_ALTITUDE,
        activeStage.propulsion(),
        activeStage.depletionFloor(),
        TARGET_INCLINATION);
  }

  private static Vehicle vehicle() {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY)
        .toVehicleStack();
  }

  private static double depletionFloorFor(SpacecraftState state) {
    return vehicle().resolveActiveStage(state.getMass()).depletionFloor();
  }

  /** Post-gravity-turn hand-off: 30 x 300 km, just past periapsis, S2 active. */
  private static SpacecraftState handOffState() {
    return stateOnOrbit(30_000.0, 300_000.0, 0.2, TRANSFER_ENTRY_MASS, 0.0);
  }

  /** The arrival orbit both candidates reach, 100 s later, at the given final mass. */
  private static SpacecraftState arrivalState(SpacecraftState entry, double finalMass) {
    return stateOnOrbit(TARGET_ALTITUDE, TARGET_ALTITUDE, 0.0, finalMass, 100.0);
  }

  private static SpacecraftState stateOnOrbit(
      double perigeeAltitude,
      double apogeeAltitude,
      double trueAnomaly,
      double mass,
      double secondsFromEpoch) {
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
            trueAnomaly,
            PositionAngleType.TRUE,
            OrekitService.get().gcrf(),
            new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC())
                .shiftedBy(secondsFromEpoch),
            Constants.WGS84_EARTH_MU);
    return new SpacecraftState(orbit).withMass(mass);
  }
}
