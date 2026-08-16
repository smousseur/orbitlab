package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.stage.StageSeparationStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnBurnStage.AscentInstrumentation;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Structure of the explicit three-phase ascent (spec {@code
 * docs/mission-stages/01-separations-implicites.md} §4.2, §10). These fixtures need no propagation
 * — they hold the two properties the split exists for:
 *
 * <ul>
 *   <li>the jettison is a <b>phase</b>, sitting between the two burns and declaring which stage it
 *       drops, so it can no longer be skipped by a propagation ending early;
 *   <li>a burn phase without a plan refuses to fly rather than inventing a schedule.
 * </ul>
 */
class AscentSequenceTest {

  private static final AscentProfile PROFILE = new AscentProfile(7.0, 3.0, 2.0);

  /** Kourou, the site every profile in the catalog flies from. */
  private static final double LAT_DEG = 5.23;

  private static final GravityTurnConstraints CONSTRAINTS =
      GravityTurnConstraints.forTarget(400_000.0);

  @Test
  void ascent_isThreePhasesWithTheJettisonInTheMiddle() {
    List<MissionStage> ascent = AscentSequence.gravityTurn(PROFILE, CONSTRAINTS, LAT_DEG);

    assertEquals(3, ascent.size(), "the ascent is burn 1, separation, burn 2");
    assertEquals(AscentSequence.FIRST_BURN_NAME, ascent.get(0).getName());
    assertEquals(AscentSequence.SEPARATION_NAME, ascent.get(1).getName());
    assertEquals(AscentSequence.SECOND_BURN_NAME, ascent.get(2).getName());

    assertInstanceOf(GravityTurnFirstBurnStage.class, ascent.get(0));
    assertInstanceOf(GravityTurnSecondBurnStage.class, ascent.get(2));
  }

  @Test
  void separationPhase_isNonPropulsiveAndDeclaresTheStageItDrops() {
    MissionStage separation = AscentSequence.gravityTurn(PROFILE, CONSTRAINTS, LAT_DEG).get(1);

    StageSeparationStage jettison = assertInstanceOf(StageSeparationStage.class, separation);
    assertFalse(jettison.isPropulsive(), "a jettison burns nothing: no propellant, no ΔV reported");
    // The declared index is what makes the separation fail fast instead of dropping whichever
    // stage the mass accounting happens to point at (bilan 10 §6 follow-up).
    assertEquals(0, AscentSequence.FIRST_STAGE_INDEX, "the ascent drops the bottom stack stage");
  }

  @Test
  void firstBurn_ownsTheOptimizationAndKeepsTheHistoricalKey() {
    OptimizableMissionStage<?> firstBurn =
        assertInstanceOf(
            GravityTurnFirstBurnStage.class,
            AscentSequence.gravityTurn(PROFILE, CONSTRAINTS, LAT_DEG).getFirst());

    // The result map is keyed by string: keeping "Gravity turn" leaves results stored before the
    // split (including in a running session) valid.
    assertEquals("Gravity turn", firstBurn.optimizationKey());
    assertTrue(
        firstBurn.advancesByReplay(),
        "the ascent problem flies the whole chain: the mission loop must not replay it from"
            + " problem.propagate()");
  }

  @Test
  void secondBurn_refusesToConfigureWithoutAPlan() {
    GravityTurnSecondBurnStage secondBurn =
        new GravityTurnSecondBurnStage(
            AscentSequence.SECOND_BURN_NAME, new AscentPlanRef(), AscentInstrumentation.REPLAY);

    OrbitlabException failure =
        assertThrows(
            OrbitlabException.class,
            () -> secondBurn.maxStepSeconds(null, null),
            "a burn phase with no plan has no schedule to fly");
    assertTrue(
        failure.getMessage().contains(AscentSequence.SECOND_BURN_NAME),
        () -> "the failure must name the phase; was: " + failure.getMessage());
  }
}
