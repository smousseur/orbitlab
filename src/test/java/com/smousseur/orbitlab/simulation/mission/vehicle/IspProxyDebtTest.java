package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.IgnitionMode;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;
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
 * so in their own comments: Falcon Heavy S1 flies 298 s (296 s before PHY-2/L3) inside a [282 s at
 * sea level, 311 s in vacuum] bracket, Ariane 64 S1 flies 300 s inside [271 s, 331 s]. With no
 * atmosphere modelled, that deficit is what stands in for the losses of a real ascent.
 *
 * <p><b>The debt is the Δv that convention is quietly absorbing</b>: {@code g₀·ΔIsp·ln R} over the
 * first stage's own mass ratio. PHY-2 owes it back the day it models the drag and restores a vacuum
 * ISP — and what makes the figure worth recording is the comparison with the 100–300 m/s of drag
 * losses the impact study attributes to a heavy launcher. It says whether the proxy pays roughly
 * what it claims to pay, or nothing like it.
 *
 * <p><b>The mass ratio is read off the stack {@code PropellantBudget} actually sizes</b> rather
 * than off tank capacities. For the first stage the two turn out to coincide — the budget sizes
 * top-down from the payload and leaves the booster full, so S1 flies its 1 233 t (434 t on Ariane
 * 62) — but the figure is computed from the sized stack all the same, so it follows the catalog if
 * a later lot changes how the boosters are loaded.
 *
 * <p><b>Measured 2026-08-21: 408 m/s on Falcon Heavy S1, 671 m/s on Ariane 64 S1</b> — since
 * revised to 396 and 64 by PHY-8, see the assertions. Both sat <em>above</em> the 100–300 m/s of
 * ascent drag the impact study attributes to a heavy launcher, and Ariane 64 more than doubles its
 * upper bound — the wider its sea-level-to-vacuum bracket, the more the mean-trajectory convention
 * absorbs. PHY-2 therefore does not simply hand back what the drag will cost: on these two entries
 * the proxy is paying for more than drag alone.
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
    double falconHeavy = debtOf(Launchers.FALCON_HEAVY, 10_000.0, new double[] {311.0, 311.0});
    double ariane64 = debtOf(Launchers.ARIANE_64, 20_000.0, new double[] {278.5, 431.0});

    logger.info("L2 ISP proxy debt — the losses the catalog compensates by a mean-trajectory ISP:");
    logger.info("  Falcon Heavy S1 (298 s against 311 s in vacuum) = {} m/s", round(falconHeavy));
    logger.info(
        "  Ariane 64 block (P120C honest, Vulcain 360 against 431) = {} m/s", round(ariane64));
    logger.info("  for comparison, the impact study puts ascent drag losses at 100-300 m/s");

    assertTrue(
        falconHeavy > 0 && ariane64 > 0,
        "a mean-trajectory ISP is below the vacuum one, so the debt is a positive number");
    // Pinned loosely, because the figure is quoted outside this test - the DT-13 roadmap entry, the
    // PHY-8 decoupage §3.4 and its L0 §3 all carry it. Splitting the Falcon Heavy's first stage in
    // two halves the mass ratio and reports 144 m/s here, silently, if the debt keeps being read
    // off the bottom entry alone (spec docs/etagement/04-conception-L2.md §2.3).
    // 396 since PHY-8 reserved 1 300 m/s of insertion ΔV on the top stage: the debt goes as
    // ln(m0/mf) over the first stage, and a fuller S2 rides above it in both masses. The 12 m/s
    // lost is arithmetic on a heavier stack, not a change in what the proxy hides.
    // 343 since PHY-2/L3: raising the proxy from 296 to 298 s handed back the ~51 m/s of ascent
    // drag it was standing in for (spec docs/atmosphere/10-conception-L3-PHY-2.md §3.3). The first
    // stage flies full and the S2 keeps its 348 s, so the mass ratio is unchanged and the debt
    // scales exactly by the ISP ratio, 396 × 13/15. What is left is the deficit L2 kept in the ISP
    // deliberately — not drag, and not double-counted (DT-13).
    assertEquals(
        343,
        falconHeavy,
        5,
        "the Falcon Heavy S1 residual proxy debt after L3 handed back the drag");
    // PHY-8 / L4 collapsed this one, and that is the finding rather than the number. The Ariane 62
    // aggregate blended a solid with a cryogenic core into a single 300 s proxy inside a [271, 331]
    // bracket, and 671 m/s of the debt was that blend rather than any real loss. Split, the four
    // P120C fly their true vacuum ISP and carry nothing, the Vulcain gives up 71 s over the 21 % of
    // the flow it owns, and what is left is the debt PHY-2 actually has to hand back (spec
    // docs/etagement/06-conception-L4.md §3.1).
    assertEquals(71, ariane64, 10, "the Ariane 64 block proxy debt recorded for PHY-2");
  }

  /**
   * {@code g₀·(IspVacuum − IspProxy)·ln(m0/mf)} over the first stage, on the stack the propellant
   * budget sizes for a LEO mission with this payload.
   *
   * <p><b>The first stage may be several stack entries.</b> A parallel block burns its tanks
   * together and is jettisoned as one, so the mass ratio is the block's — the entries taken apart
   * have none of their own, a ratio presupposing a serial burn (spec {@code
   * docs/etagement/04-conception-L2.md} §3.3).
   */
  private static double debtOf(
      LauncherModel launcher, double payloadDryMass, double[] vacuumIspPerGroundLitStage) {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(payloadDryMass, 0.0);
    double[] loads =
        PropellantBudget.loadsForLeo(launcher, payload, TARGET_ALTITUDE, LAUNCH_LATITUDE_DEG);
    VehicleStack stack = new LaunchConfiguration(launcher, loads, payload).toVehicleStack();

    double firstStageLoad = 0.0;
    double thrust = 0.0;
    double proxyFlow = 0.0;
    double vacuumFlow = 0.0;
    int groundLit = 0;
    for (int i = 0; i < launcher.stages().size(); i++) {
      StageModel stage = launcher.stages().get(i);
      if (stage.capabilities().ignition() != IgnitionMode.GROUND) {
        continue;
      }
      firstStageLoad += loads[i];
      double stageThrust = stage.propulsion().thrust();
      thrust += stageThrust;
      proxyFlow += stageThrust / stage.propulsion().isp();
      vacuumFlow += stageThrust / vacuumIspPerGroundLitStage[groundLit++];
    }
    // Effective ISPs of the block, ΣF/Σ(F/Isp), because that is what the block actually flies at.
    // Passing one aggregate pair instead would be the Ariane 62's own mistake: its 300 s in
    // [271, 331] was a blend of a solid and a cryogenic core, and most of the "debt" it reported
    // was the blend.
    double proxyIsp = thrust / proxyFlow;
    double vacuumIsp = thrust / vacuumFlow;
    double liftOffMass = stack.getMass();
    double burnOutMass = liftOffMass - firstStageLoad;
    double massRatio = liftOffMass / burnOutMass;

    logger.info(
        "  {}: lift-off {} kg, first-stage load {} kg, mass ratio {}",
        launcher.displayName(),
        round(liftOffMass),
        round(firstStageLoad),
        String.format(Locale.ROOT, "%.3f", massRatio));

    return G0 * (vacuumIsp - proxyIsp) * FastMath.log(massRatio);
  }

  private static long round(double value) {
    return Math.round(value);
  }
}
