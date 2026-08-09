package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.operation.LEOMission;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The flight profile a mission flies comes from <b>its own launcher</b>, not from a default shared
 * by the catalog (spec {@code specs/launchers/01-ariane-62.md} §6.2).
 *
 * <p>{@code AscentProfile} has been a field of {@code LauncherModel} since the catalog existed, but
 * with a single launcher in it no test could tell a wired profile from a hardcoded one: every
 * mission legitimately flew Falcon Heavy's numbers. Two launchers with different profiles is what
 * makes the property observable, and that is the point of this file.
 *
 * <p>Deliberately white-box on {@code ConstantThrustStage.duration}: the field is {@code protected}
 * and this test sits in its package, which is cheaper than adding a production accessor that only
 * tests would call.
 */
class LauncherProfileWiringTest {

  private static final double TARGET_ALT = 400_000.0;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  void verticalAscent_carriesTheLaunchersOwnProfile() {
    assertEquals(
        Launchers.FALCON_HEAVY.ascentProfile().verticalAscentDuration(),
        verticalAscentDuration(Launchers.FALCON_HEAVY),
        1e-9,
        "Falcon Heavy mission must fly Falcon Heavy's vertical ascent");
    assertEquals(
        Launchers.ARIANE_62.ascentProfile().verticalAscentDuration(),
        verticalAscentDuration(Launchers.ARIANE_62),
        1e-9,
        "Ariane 62 mission must fly Ariane 62's vertical ascent");
  }

  /**
   * Guards the test above against becoming vacuous: it compares each mission to its own launcher,
   * so it would still pass if both launchers declared the same profile — and would then prove
   * nothing about the wiring. The catalog must keep the two profiles distinguishable.
   */
  @Test
  void theTwoLaunchersDeclareDistinguishableProfiles() {
    assertNotEquals(
        Launchers.FALCON_HEAVY.ascentProfile().verticalAscentDuration(),
        Launchers.ARIANE_62.ascentProfile().verticalAscentDuration(),
        "profiles must differ, otherwise the wiring test above is vacuous");
  }

  private static double verticalAscentDuration(LauncherModel launcher) {
    Mission mission =
        new LEOMission(
            "profile wiring",
            LaunchConfiguration.fullyLoaded(launcher, Spacecraft.LEGACY),
            TARGET_ALT);
    ConstantThrustStage verticalAscent = (ConstantThrustStage) mission.getStages().getFirst();
    return verticalAscent.duration;
  }
}
