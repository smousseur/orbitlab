package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>PHY-1 / L2 — how much the proxy ISPs are paying for</b> (spec {@code
 * docs/atmosphere/05-conception-L2.md} §4.3). Recorded for PHY-2, not asserted.
 *
 * <p>The catalog's two first stages carry a "mean-trajectory" ISP instead of a vacuum one, and say
 * so in their own comments: Falcon Heavy S1 flies 296 s inside a [282 s at sea level, 311 s in
 * vacuum] bracket, Ariane 62 S1 flies 300 s inside [271 s, 331 s]. With no atmosphere modelled, that
 * deficit is what stands in for the losses of a real ascent.
 *
 * <p><b>The debt is the Δv that convention is quietly absorbing</b>: {@code g₀·ΔIsp·ln R} over the
 * first stage's own mass ratio. PHY-2 owes it back the day it models the drag and restores a vacuum
 * ISP — and what makes the figure worth recording is the comparison with the 100–300 m/s of drag
 * losses the impact study attributes to a heavy launcher. It says whether the proxy pays roughly
 * what it claims to pay, or nothing like it.
 *
 * <p><b>The mass ratio is read off the stack {@code PropellantBudget} actually sizes</b> rather than
 * off tank capacities. For the first stage the two turn out to coincide — the budget sizes top-down
 * from the payload and leaves the booster full, so S1 flies its 1 233 t (434 t on Ariane 62) — but
 * the figure is computed from the sized stack all the same, so it follows the catalog if a later lot
 * changes how the boosters are loaded.
 *
 * <p><b>Measured 2026-08-21: 408 m/s on Falcon Heavy S1, 671 m/s on Ariane 62 S1.</b> Both sit
 * <em>above</em> the 100–300 m/s of ascent drag the impact study attributes to a heavy launcher, and
 * Ariane 62 more than doubles its upper bound — the wider its sea-level-to-vacuum bracket, the more
 * the mean-trajectory convention absorbs. PHY-2 therefore does not simply hand back what the drag
 * will cost: on these two entries the proxy is paying for more than drag alone.
 *
 * <p>No propagation, no Orekit data — this is arithmetic on the catalog.
 */
class IspProxyDebtTest {
  private static final Logger logger = LogManager.getLogger(IspProxyDebtTest.class);

  private static final double G0 = 9.80665;

  /** Kourou, the latitude both profiles are sized and flown at. */
  private static final double LAUNCH_LATITUDE_DEG = 5.23;

  private static final double TARGET_ALTITUDE = 400_000.0;

  @Test
  @DisplayName("The Δv the mean-trajectory ISPs absorb, recorded for PHY-2")
  void ispProxyDebt_isRecorded() {
    // The vacuum ISPs are the upper bound of the brackets quoted in the catalog comments beside
    // each PropulsionSystem; they exist nowhere else in the code, which is why they are repeated
    // here rather than read.
    double falconHeavy = debtOf(Launchers.FALCON_HEAVY, 10_000.0, 296.0, 311.0);
    double ariane62 = debtOf(Launchers.ARIANE_62, 5_000.0, 300.0, 331.0);

    logger.info("L2 ISP proxy debt — the losses the catalog compensates by a mean-trajectory ISP:");
    logger.info("  Falcon Heavy S1 (296 s against 311 s in vacuum) = {} m/s", round(falconHeavy));
    logger.info("  Ariane 62 S1    (300 s against 331 s in vacuum) = {} m/s", round(ariane62));
    logger.info("  for comparison, the impact study puts ascent drag losses at 100-300 m/s");

    assertTrue(
        falconHeavy > 0 && ariane62 > 0,
        "a mean-trajectory ISP is below the vacuum one, so the debt is a positive number");
  }

  /**
   * {@code g₀·(IspVacuum − IspProxy)·ln(m0/mf)} over the first stage, on the stack the propellant
   * budget sizes for a LEO mission with this payload.
   */
  private static double debtOf(
      LauncherModel launcher, double payloadDryMass, double proxyIsp, double vacuumIsp) {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(payloadDryMass, 0.0);
    double[] loads =
        PropellantBudget.loadsForLeo(launcher, payload, TARGET_ALTITUDE, LAUNCH_LATITUDE_DEG);
    VehicleStack stack = new LaunchConfiguration(launcher, loads, payload).toVehicleStack();

    double liftOffMass = stack.getMass();
    double burnOutMass = liftOffMass - loads[0];
    double massRatio = liftOffMass / burnOutMass;

    logger.info(
        "  {}: lift-off {} kg, S1 load {} kg, mass ratio {}",
        launcher.displayName(),
        round(liftOffMass),
        round(loads[0]),
        String.format(Locale.ROOT, "%.3f", massRatio));

    return G0 * (vacuumIsp - proxyIsp) * FastMath.log(massRatio);
  }

  private static long round(double value) {
    return Math.round(value);
  }
}
