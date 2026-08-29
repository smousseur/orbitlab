package com.smousseur.orbitlab.ui.mission.wizard;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.operation.NodeBranch;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * <b>MIS-7 / P2.a</b> — the mapping between a mission spec and the wizard card it came from (spec
 * {@code docs/earth-orbit/02-wizard-orbites-terrestres.md} §2.1).
 *
 * <p>The profile is <em>derived</em> from the spec rather than stored on it, so this fixture is
 * what stands between a polar mission and reopening as a LEO one. It is also the reason {@code
 * MissionProfile} carries no Lemur type: a wizard step could not be built headless, this can.
 */
class MissionProfileTest {

  private static final double KOUROU_LAT = 5.236;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  /** A spec at the given altitudes and inclination, on a vehicle none of these assertions read. */
  private static MissionSpec.EarthOrbit spec(
      double perigeeAlt, double apogeeAlt, double inclinationDeg) {
    Spacecraft payload = Payloads.byId("EARTH_OBS_SAT").toSpacecraft(8_000.0, 0.0);
    LaunchConfiguration configuration =
        new LaunchConfiguration(
            Launchers.byId("FALCON_HEAVY"),
            new double[] {400_000.0, 90_000.0},
            payload,
            "EARTH_OBS_SAT");
    return new MissionSpec.EarthOrbit(
        "fixture",
        configuration,
        perigeeAlt,
        apogeeAlt,
        FastMath.toRadians(inclinationDeg),
        NodeBranch.ASCENDING,
        "Kourou",
        KOUROU_LAT,
        -52.769,
        14.0,
        null);
  }

  @Test
  void dueEastLowOrbit_readsAsLeo() {
    assertEquals(MissionProfile.LEO, MissionProfile.of(spec(400_000, 550_000, KOUROU_LAT)));
  }

  @Test
  void inclinedLowOrbit_stillReadsAsLeo() {
    assertEquals(MissionProfile.LEO, MissionProfile.of(spec(400_000, 400_000, 51.6)));
  }

  @Test
  void ninetyDegrees_readsAsPolar() {
    assertEquals(MissionProfile.POLAR, MissionProfile.of(spec(550_000, 550_000, 90.0)));
  }

  /** The inclination the altitude derives, to the digit the wizard would have shown. */
  @Test
  void sunSynchronousInclinationAtItsOwnAltitude_readsAsSso() {
    double inclinationDeg =
        FastMath.toDegrees(LaunchPlane.sunSynchronous(700_000.0).targetInclination());
    assertEquals(MissionProfile.SSO, MissionProfile.of(spec(700_000, 700_000, inclinationDeg)));
  }

  /**
   * The same inclination flown at another altitude is no longer sun-synchronous, and must not be
   * labelled so — this is the one assertion that keeps the SSO test from being a test of
   * "retrograde enough".
   */
  @Test
  void sunSynchronousInclinationAtAnotherAltitude_readsAsLeo() {
    double inclinationDeg =
        FastMath.toDegrees(LaunchPlane.sunSynchronous(700_000.0).targetInclination());
    assertEquals(MissionProfile.LEO, MissionProfile.of(spec(300_000, 300_000, inclinationDeg)));
  }

  /** An ellipse has no single altitude for the formula to work on, so it can never be an SSO. */
  @Test
  void ellipticTargetIsNeverSso() {
    double inclinationDeg =
        FastMath.toDegrees(LaunchPlane.sunSynchronous(700_000.0).targetInclination());
    assertEquals(MissionProfile.LEO, MissionProfile.of(spec(600_000, 800_000, inclinationDeg)));
  }

  /**
   * Past the direct-chain ceiling the profile is MEO whatever the inclination — the ceiling is what
   * {@code MissionComposer} itself routes on, so reading it first is what keeps a 55° MEO from
   * being shown as an ordinary inclined low orbit.
   */
  @Test
  void mediumEarthOrbit_readsAsMeo() {
    assertEquals(MissionProfile.MEO, MissionProfile.of(spec(20_200_000, 20_200_000, 55.0)));
  }

  @Test
  void geoSpec_readsAsGeo() {
    Spacecraft payload = Payloads.byId("GEO_SAT").toSpacecraft(2_000.0, 1_500.0);
    MissionSpec.Geo geo =
        new MissionSpec.Geo(
            "fixture",
            new LaunchConfiguration(
                Launchers.byId("FALCON_HEAVY"),
                new double[] {400_000.0, 90_000.0},
                payload,
                "GEO_SAT"),
            300_000.0,
            35_786_000.0,
            0.0,
            "Kourou",
            KOUROU_LAT,
            -52.769,
            14.0,
            null);
    assertEquals(MissionProfile.GEO, MissionProfile.of(geo));
  }

  /**
   * Two cards carry a type of their own; the other four are one and the same spec record.
   *
   * <p><b>The count stays at four, and that is the point</b> (MIS-4 / L5 §2.5). {@code
   * earthOrbitProfiles()} used to filter by excluding GEO <em>by name</em>, so the sixth constant
   * would have fallen through it and been handed a perigee/apogee panel. Repairing the filter to
   * read the mission type leaves this number where it was, which is what says the repair is right.
   */
  @Test
  void onlyGeoAndLunarProfilesCarryTheirOwnType() {
    for (MissionProfile profile : MissionProfile.values()) {
      MissionType expected =
          switch (profile) {
            case GEO -> MissionType.GEO;
            case LUNAR -> MissionType.LUNAR_FLYBY;
            default -> MissionType.LEO;
          };
      assertEquals(expected, profile.missionType(), profile.name());
    }
    assertEquals(4, MissionProfile.earthOrbitProfiles().size());
    assertFalse(MissionProfile.earthOrbitProfiles().contains(MissionProfile.GEO));
    assertFalse(MissionProfile.earthOrbitProfiles().contains(MissionProfile.LUNAR));
  }

  /** MIS-4 / L5 §2.1 — the sixth card, and the one aimed at another body. */
  @Test
  void lunarProfileIsFlownAsALunarFlyby() {
    assertEquals(MissionType.LUNAR_FLYBY, MissionProfile.LUNAR.missionType());
    assertEquals(MissionProfile.Availability.WINDOWED, MissionProfile.LUNAR.availability());
    assertEquals(MissionProfile.InclinationMode.NONE, MissionProfile.LUNAR.inclinationMode());
    // i = phi: the chain flies the plane the pad reaches, and NONE means the panel offers no field.
    assertEquals(28.562, MissionProfile.LUNAR.initialInclinationDeg(28.562, 400_000.0), 1e-9);
  }

  /**
   * MIS-4 / L5 §1.3 — the defect the sixth constant makes visible. {@code of} answered GEO for a
   * lunar spec, and the verdict of L4 §1.2 only held because {@code WizardPrefill} threw before
   * reaching it. Now that the prefill fills, the wrong card would light in silence.
   */
  @Test
  void lunarSpecLightsTheLunarCard() {
    MissionSpec.Lunar lunar =
        new MissionSpec.Lunar(
            "fixture",
            LaunchConfiguration.fullyLoaded(
                Launchers.FALCON_HEAVY, Payloads.LUNAR_PROBE.toSpacecraft(2_000.0, 0.0)),
            400_000.0,
            100_000.0,
            "Cape Canaveral",
            28.562,
            -80.577,
            3.0,
            null,
            null);
    assertEquals(MissionProfile.LUNAR, MissionProfile.of(lunar));
  }

  /** A profile's default altitude has to be one its own sliders can show. */
  @Test
  void everyProfileDefaultSitsInsideItsBand() {
    for (MissionProfile profile : MissionProfile.values()) {
      MissionProfile.AltitudeRange band = profile.altitudes();
      assertTrue(band.minKm() < band.maxKm(), profile + " band");
      assertTrue(
          band.defaultKm() >= band.minKm() && band.defaultKm() <= band.maxKm(),
          profile + " default altitude outside its own band");
    }
  }

  /** AUTO means "the site's free plane", which is the whole of the non-regression rule of §2.0. */
  @Test
  void autoProfileStartsOnTheSitesFreePlane() {
    assertEquals(
        MissionProfile.InclinationMode.AUTO, MissionProfile.LEO.inclinationMode(), "LEO mode");
    assertEquals(KOUROU_LAT, MissionProfile.LEO.initialInclinationDeg(KOUROU_LAT, 550_000.0), 1e-9);
    // Southern sites reach the same planes as their northern mirrors: an inclination is positive.
    assertEquals(34.632, MissionProfile.LEO.initialInclinationDeg(-34.632, 550_000.0), 1e-9);
  }

  @Test
  void explicitProfilesStartOnTheirOwnDefault() {
    assertEquals(90.0, MissionProfile.POLAR.initialInclinationDeg(KOUROU_LAT, 550_000.0), 1e-9);
    assertEquals(55.0, MissionProfile.MEO.initialInclinationDeg(KOUROU_LAT, 20_200_000.0), 1e-9);
  }

  /** The derived one ignores the site entirely: only the altitude decides (spec {@code 01} §5). */
  @Test
  void ssoInclinationFollowsTheAltitude() {
    double at600 = MissionProfile.SSO.initialInclinationDeg(KOUROU_LAT, 600_000.0);
    double at800 = MissionProfile.SSO.initialInclinationDeg(KOUROU_LAT, 800_000.0);
    assertEquals(97.79, at600, 0.02, "600 km");
    assertEquals(98.61, at800, 0.02, "800 km");
    assertEquals(at600, MissionProfile.SSO.initialInclinationDeg(45.965, 600_000.0), 1e-12);
  }

  /**
   * MIS-5 / L5 §5.1 — a lunar orbit spec has no card yet, and {@code of} says so rather than
   * inventing one.
   *
   * <p>Answering {@code LUNAR} would light the flyby card for an orbit insertion — the exact
   * fallback whose removal the switch documents — and adding a seventh constant would overflow the
   * fixed wizard window until L6 turns the grid into tabs. The refusal names the lot that fills it,
   * as MIS-5 / L3 wrote two others.
   */
  @Test
  void lunarOrbitSpecHasNoCardYetAndSaysSo() {
    MissionSpec.LunarOrbit lunarOrbit =
        new MissionSpec.LunarOrbit(
            "fixture",
            LaunchConfiguration.fullyLoaded(
                Launchers.FALCON_HEAVY, Payloads.LUNAR_ORBITER.toSpacecraft(2_000.0, 657.7)),
            400_000.0,
            100_000.0,
            "Cape Canaveral",
            28.562,
            -80.577,
            3.0,
            null,
            null);

    UnsupportedOperationException refused =
        assertThrows(UnsupportedOperationException.class, () -> MissionProfile.of(lunarOrbit));
    assertTrue(refused.getMessage().contains("L7"), refused.getMessage());
  }
}
