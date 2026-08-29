package com.smousseur.orbitlab.ui.mission.wizard.step.params;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.mission.MissionHorizon;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MIS-5 / L7 §6 — the one decision the lunar orbit panel makes, taken out of the panel so it can be
 * exercised without an initialised {@code AssetManager}. The same split {@code RefusedPage} takes
 * out of its own step.
 *
 * <p>What it guards is not a display detail. The wizard's duration field writes a {@code
 * MissionHorizon.FixedDuration}, whose contract is a <em>total</em> measured from lift-off; a panel
 * publishing the twelve revolutions alone would show one day for a mission that runs five, and a
 * user confirming that number would create a mission ending before it reaches the Moon.
 */
class LunarOrbitDynamicParametersTest {

  private static final double TRANSFER_DAYS =
      TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS / MissionHorizon.SECONDS_PER_DAY;

  @Test
  @DisplayName("The derived duration is the transfer plus the twelve revolutions, not the coast")
  void theDurationCoversTheTransfer() {
    double atHundred = LunarOrbitDynamicParameters.horizonDays(100_000.0);

    // The transfer is four days; twelve revolutions of a 7 067 s lunar orbit are just under one.
    assertEquals(4.0, TRANSFER_DAYS, 1e-9, "the transfer is not four days any more");
    assertEquals(4.98, atHundred, 0.02);

    double revolutionsOnly = atHundred - TRANSFER_DAYS;
    assertEquals(0.98, revolutionsOnly, 0.02);
    assertTrue(
        atHundred > 4.0 * revolutionsOnly,
        "publishing the revolutions alone would truncate the mission before the Moon");
  }

  @Test
  @DisplayName("The duration follows the slider, the lunar period growing with the altitude")
  void theDurationFollowsTheAltitude() {
    double low = LunarOrbitDynamicParameters.horizonDays(50_000.0);
    double high = LunarOrbitDynamicParameters.horizonDays(500_000.0);

    assertTrue(low < high, "a higher orbit has a longer period");
    // 6 781 s at 50 km against 9 497 s at 500 km: 40 % on the coast, and the transfer does not
    // move.
    assertEquals(0.94, low - TRANSFER_DAYS, 0.02);
    assertEquals(1.32, high - TRANSFER_DAYS, 0.02);
  }
}
