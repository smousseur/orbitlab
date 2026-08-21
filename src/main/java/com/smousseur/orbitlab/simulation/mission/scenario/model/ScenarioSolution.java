package com.smousseur.orbitlab.simulation.mission.scenario.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * What the optimization cost to find, and the only reason a scenario replays in one propagation
 * instead of N (spec {@code docs/scenario/01-persistance-missions.md} §2 and §5).
 *
 * <p>The vectors are the sole component of an {@code OptimizationResult} that any stage reads back
 * — the two Orekit states it also carries are read by nobody on the replay path — which is what
 * makes the whole thing cheap to persist.
 *
 * <p><b>The loads are kilograms, never λ</b> (§2.3). A scale factor has two dated dependencies an
 * absolute mass does not: its base, which is whatever {@code PropellantBudget} produced on the day
 * of the save, and its mask, which decides per launcher which stages carry a λ at all. Replaying
 * {@code budgeted × λ} after either moved would fly a third load set — neither the one that flew,
 * nor the one that would be computed today. The multiplication is therefore done at save time,
 * where both factors are unambiguously in hand.
 *
 * @param vectors the solved variables per {@code OptimizableMissionStage.optimizationKey()}
 * @param launcherLoads the per-stage launcher loads actually flown, in kilograms; {@code null}
 *     outside {@code PRECISE}, where the loads are derived rather than searched
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScenarioSolution(Map<String, double[]> vectors, double[] launcherLoads) {}
