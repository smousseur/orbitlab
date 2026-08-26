package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Test;

/**
 * <b>MIS-7 / P1, test T6</b> — every feasibility rule of spec {@code
 * docs/earth-orbit/01-mission-terre-parametrable.md} §8 refuses at spec construction, with a
 * message naming what is reachable.
 *
 * <p><b>Refusals, not clamps.</b> A parameter silently corrected produces a mission that flies
 * something other than what was asked for — precisely the defect MIS-7 exists to remove. The spec
 * is the last place the caller still knows what it wanted, so it is where the refusal belongs; by
 * the time the propagation notices, it is running on a background thread with no idea what was
 * intended.
 *
 * <p>{@link LaunchPlaneTest} covers the same rules one level down, on the plane itself. This
 * fixture checks that {@link MissionSpec.EarthOrbit} actually enforces them rather than merely
 * storing an inclination.
 */
class EarthOrbitValidationTest {

  private static final double KOUROU_LAT = 5.23;
  private static final double KOUROU_LON = -52.77;
  private static final OptimizationType FAST = OptimizationType.FAST;

  private static LaunchConfiguration falconHeavy() {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY);
  }

  private static MissionSpec.EarthOrbit spec(
      double perigee, double apogee, double inclinationDeg, double latitude) {
    return new MissionSpec.EarthOrbit(
        "T6",
        falconHeavy(),
        perigee,
        apogee,
        FastMath.toRadians(inclinationDeg),
        NodeBranch.ASCENDING,
        "Kourou",
        latitude,
        KOUROU_LON,
        0.0,
        null);
  }

  @Test
  void inclinationBelowTheSiteLatitude_isRefused() {
    OrbitlabException failure =
        assertThrows(OrbitlabException.class, () -> spec(400_000.0, 400_000.0, 0.0, KOUROU_LAT));

    assertTrue(
        failure.getMessage().contains("5.230"),
        () -> "the message must name the reachable minimum: " + failure.getMessage());
  }

  @Test
  void inclinationAboveTheRetrogradeBound_isRefused() {
    assertThrows(OrbitlabException.class, () -> spec(400_000.0, 400_000.0, 179.0, KOUROU_LAT));
  }

  @Test
  void inclinationOutsideZeroToOneEighty_isRefused() {
    assertThrows(OrbitlabException.class, () -> spec(400_000.0, 400_000.0, 200.0, KOUROU_LAT));
    assertThrows(OrbitlabException.class, () -> spec(400_000.0, 400_000.0, -5.0, KOUROU_LAT));
  }

  /** Implicit before MIS-7 — the pair was simply used in the order it came — now explicit. */
  @Test
  void apogeeBelowPerigee_isRefusedNamingBoth() {
    OrbitlabException failure =
        assertThrows(
            OrbitlabException.class, () -> spec(600_000.0, 400_000.0, KOUROU_LAT, KOUROU_LAT));

    assertTrue(
        failure.getMessage().contains("400000") && failure.getMessage().contains("600000"),
        () -> "the message must name both altitudes: " + failure.getMessage());
  }

  // ── What must keep passing ───────────────────────────────────────────────

  @Test
  void theSitesFreePlane_isAccepted() {
    assertDoesNotThrow(() -> spec(400_000.0, 400_000.0, KOUROU_LAT, KOUROU_LAT));
  }

  @Test
  void polarAndSunSynchronousTargets_areAccepted() {
    assertDoesNotThrow(() -> spec(400_000.0, 400_000.0, 90.0, KOUROU_LAT));
    assertDoesNotThrow(() -> spec(700_000.0, 700_000.0, 98.19, KOUROU_LAT));
  }

  @Test
  void anEllipticTarget_isAccepted() {
    assertDoesNotThrow(() -> spec(400_000.0, 600_000.0, KOUROU_LAT, KOUROU_LAT));
  }

  // ── The coast rule of §6 / §6.1 ──────────────────────────────────────────

  /**
   * A target beyond the ascent's reach needs a parking orbit and a long coast to apogee. Where
   * neither the upper stage nor a kick motor can hold it, the mission is refused at composition —
   * naming the stage and the duration it is short of, so the way out ("fly Ariane 62, or take a
   * payload with a kick motor") is in the failure itself.
   */
  @Test
  void aMeoOnFalconHeavyWithoutAKickMotor_isRefusedNamingTheStageAndTheCoast() {
    MissionSpec.EarthOrbit meo =
        new MissionSpec.EarthOrbit(
            "MEO",
            LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY),
            20_200_000.0,
            20_200_000.0,
            FastMath.toRadians(55.0),
            NodeBranch.ASCENDING,
            "Kourou",
            KOUROU_LAT,
            KOUROU_LON,
            0.0,
            null);

    OrbitlabException failure =
        assertThrows(
            OrbitlabException.class, () -> MissionComposer.compose(meo, OptimizationType.FAST));

    assertTrue(
        failure.getMessage().contains("S2 (Merlin Vacuum)"),
        () -> "the message must name the stage that is short: " + failure.getMessage());
    // 2.98 h is spec §6's 2 h 58 for 400 km → 20 200 km; 2.00 h is what the catalog declares for
    // this stage. Both must be in the message: the gap between them is the whole diagnosis.
    assertTrue(
        failure.getMessage().contains("2.98 h") && failure.getMessage().contains("2.00 h"),
        () ->
            "the message must give the coast needed and the coast declared: "
                + failure.getMessage());
    assertTrue(
        failure.getMessage().contains("kick motor"),
        () -> "the message must name the way out: " + failure.getMessage());
  }

  /**
   * The same target on Ariane 62 composes: its upper stage declares 6 h against the 2 h 58 needed.
   * This is the "reserved to Ariane 62" of §6 turned into a property of the catalog rather than a
   * rule someone has to remember.
   */
  @Test
  void theSameMeoOnAriane62_composes() {
    assertDoesNotThrow(() -> MissionComposer.compose(meoSpec(Launchers.ARIANE_62), FAST));
  }

  /**
   * And the other way out of §6: a Falcon Heavy carrying a payload with its own kick motor, which
   * is exactly how it reaches GEO on a coast it could never hold itself.
   */
  @Test
  void aMeoOnFalconHeavyWithAKickMotor_composes() {
    Spacecraft withAkm = Payloads.GEO_SAT.toSpacecraft(Payloads.GEO_SAT.defaultDryMass(), 1_500.0);
    MissionSpec.EarthOrbit meo =
        new MissionSpec.EarthOrbit(
            "MEO with AKM",
            new LaunchConfiguration(
                Launchers.FALCON_HEAVY, new double[] {1_233_000, 107_500}, withAkm),
            20_200_000.0,
            20_200_000.0,
            FastMath.toRadians(55.0),
            NodeBranch.ASCENDING,
            "Kourou",
            KOUROU_LAT,
            KOUROU_LON,
            0.0,
            null);

    assertDoesNotThrow(() -> MissionComposer.compose(meo, FAST));
  }

  /**
   * The elliptic shape the rule caught in {@code EarthOrbitNonRegressionTest}: a GTO-apogee ellipse
   * on a Falcon Heavy with no kick motor. Its trim has to coast to apogee, which the stage cannot.
   */
  @Test
  void aGtoApogeeEllipseOnFalconHeavy_isRefusedForTheSameReason() {
    MissionSpec.EarthOrbit ellipse =
        MissionSpec.EarthOrbit.dueEast(
            "GTO ellipse",
            falconHeavy(),
            300_000.0,
            35_786_000.0,
            "Kourou",
            KOUROU_LAT,
            KOUROU_LON,
            0.0,
            null);

    assertThrows(OrbitlabException.class, () -> MissionComposer.compose(ellipse, FAST));
  }

  /** A LEO target must never be routed through the parking chain. */
  @Test
  void everyLeoTarget_staysOnTheDirectChain() {
    for (double apogee : new double[] {200_000.0, 400_000.0, 1_000_000.0, 1_999_000.0}) {
      assertFalse(
          MissionComposer.needsParkingOrbit(apogee),
          () -> apogee + " m must stay on the direct chain");
    }
    assertTrue(MissionComposer.needsParkingOrbit(20_200_000.0), "a MEO needs the parking chain");
  }

  private static MissionSpec.EarthOrbit meoSpec(LauncherModel launcher) {
    return new MissionSpec.EarthOrbit(
        "MEO",
        LaunchConfiguration.fullyLoaded(launcher, Spacecraft.LEGACY),
        20_200_000.0,
        20_200_000.0,
        FastMath.toRadians(55.0),
        NodeBranch.ASCENDING,
        "Kourou",
        KOUROU_LAT,
        KOUROU_LON,
        0.0,
        null);
  }

  /** The historical factory must never trip its own validation, whatever the site. */
  @Test
  void theDueEastFactory_isAlwaysReachable() {
    for (double latitude : new double[] {0.0, 5.23, 28.5, -34.6, 67.9}) {
      double lat = latitude;
      assertDoesNotThrow(
          () ->
              MissionSpec.EarthOrbit.dueEast(
                  "T6", falconHeavy(), 400_000.0, 400_000.0, "site", lat, KOUROU_LON, 0.0, null),
          () -> "the free plane of latitude " + lat + "° must be reachable from it");
    }
  }

  // --- MIS-2: the target node, which is optional and must survive a reload ---

  @Test
  void noTargetNode_isTheDefault() {
    // Every spec that predates MIS-2 goes through the eleven-argument form, and a mission that
    // names no plane must keep launching at the date it was given.
    assertFalse(spec(400_000.0, 400_000.0, 51.6, KOUROU_LAT).hasTargetRaan());
  }

  @Test
  void targetNode_isCarriedAndReadThroughThePredicate() {
    MissionSpec.EarthOrbit spec = specWithRaan(120.0);

    assertTrue(spec.hasTargetRaan());
    assertEquals(120.0, spec.targetRaan(), 0.0);
  }

  @Test
  void aNonFiniteTargetNode_isRefused() {
    assertThrows(OrbitlabException.class, () -> specWithRaan(Double.NaN));
    assertThrows(OrbitlabException.class, () -> specWithRaan(Double.POSITIVE_INFINITY));
  }

  @Test
  void resizingThePropellantLoads_keepsTheTargetNode() {
    // The sizing planner rebuilds the spec at every candidate load array; a node lost there would
    // silently turn a mission waiting for a plane into one launching whenever.
    MissionSpec resized = specWithRaan(120.0).withLauncherLoads(new double[] {1_000.0, 500.0});

    assertTrue(((MissionSpec.EarthOrbit) resized).hasTargetRaan());
    assertEquals(120.0, ((MissionSpec.EarthOrbit) resized).targetRaan(), 0.0);
  }

  private static MissionSpec.EarthOrbit specWithRaan(Double raanDeg) {
    return new MissionSpec.EarthOrbit(
        "T6",
        falconHeavy(),
        400_000.0,
        400_000.0,
        FastMath.toRadians(51.6),
        NodeBranch.ASCENDING,
        raanDeg,
        "Kourou",
        KOUROU_LAT,
        KOUROU_LON,
        0.0,
        null);
  }
}
