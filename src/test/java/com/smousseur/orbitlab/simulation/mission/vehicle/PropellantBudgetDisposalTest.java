package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
