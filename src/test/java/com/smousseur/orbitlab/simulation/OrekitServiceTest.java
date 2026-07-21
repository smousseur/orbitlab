package com.smousseur.orbitlab.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService.BurnSpec;
import org.junit.jupiter.api.Test;
import org.orekit.utils.Constants;

/**
 * Unit tests for the late-ignition max-step sizing ({@link OrekitService#burnLimitedMaxStep}). Pure
 * arithmetic — no {@code orekit-data.zip} required.
 */
class OrekitServiceTest {

  private static double burnToZeroSeconds(double thrust, double isp, double mass) {
    double massFlow = thrust / (isp * Constants.G0_STANDARD_GRAVITY);
    return mass / massFlow;
  }

  @Test
  void burnLimitedMaxStep_noBurns_returnsCoastCap() {
    assertEquals(OrekitService.COAST_MAX_STEP, OrekitService.burnLimitedMaxStep(), 0.0);
  }

  @Test
  void burnLimitedMaxStep_falconHeavyUpperStage_keepsCalibratedCap() {
    // FH upper stage: 981 kN, isp 348 s, ~16.24 t → ~56 s to depletion, comfortably above the cap,
    // so the proven SAFE_MAX_STEP is preserved: the calibrated FH stepping does not change.
    BurnSpec s2 = new BurnSpec(981_000, 348, 16_238.0);
    assertEquals(OrekitService.SAFE_MAX_STEP, OrekitService.burnLimitedMaxStep(s2), 0.0);
  }

  @Test
  void burnLimitedMaxStep_lightUpperStage_tightensBelowCap() {
    // A lighter I7 load on the same engine depletes faster (~21 s), so the max step must drop
    // below the cap and stay under the mass-depletion time (the invariant the crash violates).
    double lightMass = 6_000.0;
    BurnSpec light = new BurnSpec(981_000, 348, lightMass);
    double maxStep = OrekitService.burnLimitedMaxStep(light);
    double burnToZero = burnToZeroSeconds(981_000, 348, lightMass);

    assertTrue(maxStep < OrekitService.SAFE_MAX_STEP, "a fast-depleting burn must tighten the step");
    assertTrue(maxStep < burnToZero, "max step must stay below the mass-depletion time (invariant)");
    assertEquals(burnToZero / 1.5, maxStep, 1e-6, "tightened step keeps a 1/1.5 mass margin");
  }

  @Test
  void burnLimitedMaxStep_multipleBurns_takesTightest() {
    BurnSpec heavy = new BurnSpec(981_000, 348, 16_238.0); // stays at the cap
    BurnSpec light = new BurnSpec(981_000, 348, 6_000.0); // tightens below the cap

    assertEquals(
        OrekitService.burnLimitedMaxStep(light),
        OrekitService.burnLimitedMaxStep(heavy, light),
        1e-9,
        "the tightest (most violent) burn must set the max step");
  }
}
