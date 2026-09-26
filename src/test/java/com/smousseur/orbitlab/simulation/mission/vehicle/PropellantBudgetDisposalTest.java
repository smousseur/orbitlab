package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.disposal.DeorbitTail;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Test;
import org.orekit.utils.Constants;

/**
 * PHY-10 / L1 — the disposal reserve sizing. Every ΔV target is recomputed here from first
 * principles (vis-viva, Hohmann), independently of {@code PropellantBudget}'s own helpers, so the
 * round-trip proves the sized reserve delivers the physics and not just the same arithmetic.
 */
class PropellantBudgetDisposalTest {

  private static final double MU = Constants.WGS84_EARTH_MU;
  private static final double RE = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static final double G0 = Constants.G0_STANDARD_GRAVITY;
  private static final double MARGIN = PropellantBudget.SAFETY_MARGIN;

  /** The graveyard raise this lot sizes for (IADC usual figure); pins the design decision. */
  private static final double GRAVEYARD_RAISE = 300_000;

  // Fixture shared by the reserve-aware loadsForLeo tests below: Falcon Heavy carrying
  // EARTH_OBSERVATION_SAT at 10 t dry to a 400 km circular target from Kourou's latitude.
  private static final PayloadModel REFERENCE_PAYLOAD = Payloads.EARTH_OBSERVATION_SAT;
  private static final double REFERENCE_DRY_MASS = 10_000;
  private static final double REFERENCE_TARGET_ALTITUDE = 400_000;
  private static final double REFERENCE_LATITUDE = 5.23;
  private static final double REFERENCE_AZIMUTH = Physics.getLaunchAzimuth();

  @Test
  void reentryReserve_deliversTheRetrogradeDeltaV_belowTheBoundary() {
    PayloadModel sat = Payloads.EARTH_OBSERVATION_SAT; // hydrazine, Isp 220 s
    double dry = 10_000;
    double orbit = 400_000; // below the disposal boundary → reentry regime
    double reentryPerigee = 100_000;

    double reserve = PropellantBudget.disposalReserveFor(sat, dry, orbit, reentryPerigee);

    // Round-trip: strip the margin, fly the reserve back on the dry bus, reach the vis-viva ΔV.
    double delivered = deliveredDeltaV(sat, dry, reserve / (1 + MARGIN));
    assertEquals(reentryDeltaV(orbit, reentryPerigee), delivered, 1e-6);
    // Magnitude anchored on the L0 baseline table (454.1 kg for this exact case).
    assertEquals(454.1, reserve, 0.5);
  }

  @Test
  void graveyardReserve_deliversTheHohmannRaise_aboveTheBoundary() {
    PayloadModel geo = Payloads.GEO_SAT; // apogee kick motor, Isp 320 s
    double dry = 2_000;
    double orbit = 35_786_000; // GEO, above the boundary → graveyard regime

    double reserve = PropellantBudget.disposalReserveFor(geo, dry, orbit, 100_000);

    double delivered = deliveredDeltaV(geo, dry, reserve / (1 + MARGIN));
    assertEquals(graveyardRaiseDeltaV(orbit, GRAVEYARD_RAISE), delivered, 1e-6);
    // Magnitude anchored on the L0 graveyard table (7.6 kg for +300 km).
    assertEquals(7.6, reserve, 0.2);
  }

  @Test
  void graveyardReserve_ignoresTheReentryPerigee() {
    PayloadModel geo = Payloads.GEO_SAT;
    double deep = PropellantBudget.disposalReserveFor(geo, 2_000, 35_786_000, 100_000);
    double ground = PropellantBudget.disposalReserveFor(geo, 2_000, 35_786_000, 0);
    assertEquals(deep, ground, 1e-9, "above the boundary the reentry perigee is not part of it");
  }

  @Test
  void theBoundarySwitchesRegime_atTwoThousandKilometres() {
    PayloadModel sat = Payloads.EARTH_OBSERVATION_SAT;
    // Just below 2 000 km, the reserve depends on the reentry perigee — it is a reentry.
    double below100 = PropellantBudget.disposalReserveFor(sat, 5_000, 1_999_000, 100_000);
    double below0 = PropellantBudget.disposalReserveFor(sat, 5_000, 1_999_000, 0);
    assertNotEquals(below100, below0, "below the boundary the reentry perigee drives the reserve");
    // Just above, it does not — it is a graveyard re-orbit.
    double above100 = PropellantBudget.disposalReserveFor(sat, 5_000, 2_001_000, 100_000);
    double above0 = PropellantBudget.disposalReserveFor(sat, 5_000, 2_001_000, 0);
    assertEquals(above100, above0, 1e-9, "above the boundary the reentry perigee is ignored");
  }

  @Test
  void graveyard_isOrdersLighterThanReentryFromTheSameOrbitWouldBe() {
    // The L0 contrast: disposing a GEO to a graveyard re-orbit is ~175× lighter than deorbiting it.
    PayloadModel geo = Payloads.GEO_SAT;
    double graveyard = PropellantBudget.disposalReserveFor(geo, 2_000, 35_786_000, 100_000);
    double dv = reentryDeltaV(35_786_000, 100_000);
    double reentry =
        2_000 * (FastMath.exp(dv / (geo.propulsion().isp() * G0)) - 1.0) * (1 + MARGIN);
    assertTrue(
        reentry > 100 * graveyard,
        () -> "graveyard " + graveyard + " kg vs reentry " + reentry + " kg — expected ~175×");
  }

  @Test
  void inertPayloadCarriesNoReserve() {
    // No engine to spend a reserve with; L2's invariant guarantees no orbital mission flies one.
    assertEquals(
        0.0,
        PropellantBudget.disposalReserveFor(Payloads.LUNAR_PROBE, 2_000, 400_000, 100_000),
        1e-9);
  }

  @Test
  void isReentryRegime_trueAtAndBelowTheBoundary_falseJustAboveIt() {
    assertTrue(PropellantBudget.isReentryRegime(400_000));
    assertTrue(PropellantBudget.isReentryRegime(2_000_000));
    assertFalse(PropellantBudget.isReentryRegime(2_000_001));
  }

  /**
   * The overload sizes the top stage on the payload's own load plus the disposal reserve; a zero
   * reserve must therefore reproduce the six-argument form bit for bit.
   */
  @Test
  void loadsForLeo_withZeroReserve_matchesTheSixArgFormBitForBit() {
    PropellantBudget.SizedLoads withoutReserve =
        PropellantBudget.loadsForLeo(
            Launchers.FALCON_HEAVY,
            REFERENCE_PAYLOAD,
            REFERENCE_DRY_MASS,
            REFERENCE_TARGET_ALTITUDE,
            REFERENCE_LATITUDE,
            REFERENCE_AZIMUTH);
    PropellantBudget.SizedLoads withZeroReserve =
        PropellantBudget.loadsForLeo(
            Launchers.FALCON_HEAVY,
            REFERENCE_PAYLOAD,
            REFERENCE_DRY_MASS,
            REFERENCE_TARGET_ALTITUDE,
            REFERENCE_LATITUDE,
            REFERENCE_AZIMUTH,
            0.0);

    assertArrayEquals(withoutReserve.launcherLoads(), withZeroReserve.launcherLoads(), 0.0);
    assertEquals(withoutReserve.payloadLoad(), withZeroReserve.payloadLoad());
  }

  /**
   * A non-zero reserve leaves the payload's own load alone and moves only the top stage, matching
   * bit for bit what the existing Spacecraft-taking form computes for a payload already carrying
   * that reserve as mass.
   */
  @Test
  void loadsForLeo_withReserve_matchesTheSpacecraftTakingFormAndMovesOnlyTheTopStage() {
    PropellantBudget.SizedLoads withoutReserve =
        PropellantBudget.loadsForLeo(
            Launchers.FALCON_HEAVY,
            REFERENCE_PAYLOAD,
            REFERENCE_DRY_MASS,
            REFERENCE_TARGET_ALTITUDE,
            REFERENCE_LATITUDE,
            REFERENCE_AZIMUTH);
    double reserve =
        PropellantBudget.disposalReserveFor(
            REFERENCE_PAYLOAD,
            REFERENCE_DRY_MASS,
            REFERENCE_TARGET_ALTITUDE,
            DeorbitTail.REENTRY_PERIGEE_ALTITUDE_M);

    PropellantBudget.SizedLoads withReserve =
        PropellantBudget.loadsForLeo(
            Launchers.FALCON_HEAVY,
            REFERENCE_PAYLOAD,
            REFERENCE_DRY_MASS,
            REFERENCE_TARGET_ALTITUDE,
            REFERENCE_LATITUDE,
            REFERENCE_AZIMUTH,
            reserve);

    assertEquals(withoutReserve.payloadLoad(), withReserve.payloadLoad());

    double[] expected =
        PropellantBudget.loadsForLeo(
            Launchers.FALCON_HEAVY,
            REFERENCE_PAYLOAD.toSpacecraft(
                REFERENCE_DRY_MASS, withoutReserve.payloadLoad(), reserve),
            REFERENCE_TARGET_ALTITUDE,
            REFERENCE_LATITUDE,
            REFERENCE_AZIMUTH);
    assertArrayEquals(expected, withReserve.launcherLoads(), 0.0);

    int top = expected.length - 1;
    for (int i = 0; i < top; i++) {
      assertEquals(withoutReserve.launcherLoads()[i], withReserve.launcherLoads()[i], 0.0);
    }
    assertNotEquals(withoutReserve.launcherLoads()[top], withReserve.launcherLoads()[top]);
  }

  /**
   * Numeric pin on the budget's own output for the reference mission — Falcon Heavy,
   * EARTH_OBSERVATION_SAT at 10 t dry, 400 km circular, latitude 5.23 — the same configuration an
   * earlier mission flew with these loads.
   */
  @Test
  void loadsForLeo_withReserve_pinsTheReferenceMissionMagnitudes() {
    PropellantBudget.SizedLoads withoutReserve =
        PropellantBudget.loadsForLeo(
            Launchers.FALCON_HEAVY,
            REFERENCE_PAYLOAD,
            REFERENCE_DRY_MASS,
            REFERENCE_TARGET_ALTITUDE,
            REFERENCE_LATITUDE,
            REFERENCE_AZIMUTH);
    double reserve =
        PropellantBudget.disposalReserveFor(
            REFERENCE_PAYLOAD,
            REFERENCE_DRY_MASS,
            REFERENCE_TARGET_ALTITUDE,
            DeorbitTail.REENTRY_PERIGEE_ALTITUDE_M);
    PropellantBudget.SizedLoads withReserve =
        PropellantBudget.loadsForLeo(
            Launchers.FALCON_HEAVY,
            REFERENCE_PAYLOAD,
            REFERENCE_DRY_MASS,
            REFERENCE_TARGET_ALTITUDE,
            REFERENCE_LATITUDE,
            REFERENCE_AZIMUTH,
            reserve);

    int top = withoutReserve.launcherLoads().length - 1;
    assertEquals(534.2, reserve, 0.1);
    assertEquals(9_488.9, withoutReserve.launcherLoads()[top], 0.1);
    assertEquals(10_032.6, withReserve.launcherLoads()[top], 0.1);
  }

  /**
   * ΔV a reserve of {@code propellant} delivers on a dry bus, by Tsiolkovsky on the payload engine.
   */
  private static double deliveredDeltaV(PayloadModel payload, double dryMass, double propellant) {
    return payload.propulsion().isp() * G0 * FastMath.log((dryMass + propellant) / dryMass);
  }

  /** Two-burn Hohmann raise of a circular orbit by {@code raise} metres (m/s). */
  private static double graveyardRaiseDeltaV(double circularAlt, double raise) {
    double r1 = RE + circularAlt;
    double r2 = RE + circularAlt + raise;
    double semiMajor = 0.5 * (r1 + r2);
    double dv1 = FastMath.sqrt(MU * (2.0 / r1 - 1.0 / semiMajor)) - FastMath.sqrt(MU / r1);
    double dv2 = FastMath.sqrt(MU / r2) - FastMath.sqrt(MU * (2.0 / r2 - 1.0 / semiMajor));
    return dv1 + dv2;
  }

  /** Single retrograde burn from a circular orbit to a reentry perigee (m/s). */
  private static double reentryDeltaV(double circularAlt, double perigeeAlt) {
    double rC = RE + circularAlt;
    double rP = RE + perigeeAlt;
    double semiMajor = 0.5 * (rC + rP);
    return FastMath.sqrt(MU / rC) - FastMath.sqrt(MU * (2.0 / rC - 1.0 / semiMajor));
  }
}
