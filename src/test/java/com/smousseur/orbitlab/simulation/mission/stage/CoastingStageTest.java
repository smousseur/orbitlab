package com.smousseur.orbitlab.simulation.mission.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.LunarApproachFixture;
import com.smousseur.orbitlab.simulation.mission.Mission;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MIS-5 / L5 §3.1 — the arc a coast declares.
 *
 * <p>Two cases, and the first is the one that protects every other mission of the repository: a
 * coast built through either historical constructor declares no arc body and must return the
 * inherited expression itself. The second is what the lunar terminal coast needs, and it is the
 * defect that raises nothing when it is wrong — a selenocentric state handed to a stage declaring
 * the mission's terrestrial context is really transformed back to GCRF, and the mission ends up
 * measured against the Earth from 380 000 km away.
 */
class CoastingStageTest {

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        LunarApproachFixture.orekitDataAvailable(), "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  @DisplayName("A coast declaring no arc body flies the mission's own context")
  void noArcBody_keepsTheMissionContext() {
    Mission mission = LunarApproachFixture.missionWith(List.of());
    GravitationalContext missionContext = mission.gravitationalContext();

    for (CoastingStage stage :
        List.of(
            new CoastingStage("bounded", 120.0),
            new CoastingStage("open", (Double) null),
            new CoastingStage("at node", true))) {
      GravitationalContext declared = stage.gravitationalContext(mission);
      assertEquals(missionContext, declared, stage.getName());
      // The frame is what ArcTransition.convert compares, by reference: the same instance is what
      // makes the conversion an identity rather than a transform that happens to be close.
      assertSame(missionContext.inertialFrame(), declared.inertialFrame(), stage.getName());
    }
  }

  @Test
  @DisplayName("A coast declaring the Moon flies the arc the crossing rule derives")
  void lunarArcBody_derivesTheSelenocentricContext() {
    Mission mission = LunarApproachFixture.missionWith(List.of());
    GravitationalContext declared =
        new CoastingStage("Coasting", null, SolarSystemBody.MOON).gravitationalContext(mission);

    // Written as the crossing rule renders it, not as a literal: ArcTransition.across is the one
    // place saying that the Earth becomes a perturber of a lunar arc (L4 §1.3 measured them equal).
    assertEquals(
        GravitationalContext.moon().withPerturbers(SolarSystemBody.EARTH, SolarSystemBody.SUN),
        declared);
    assertSame(LunarApproachFixture.lunarContext().inertialFrame(), declared.inertialFrame());
  }
}
