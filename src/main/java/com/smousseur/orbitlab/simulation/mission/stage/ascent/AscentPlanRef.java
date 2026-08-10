package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.core.OrbitlabException;
import java.util.Objects;

/**
 * The one {@link AscentPlan} the three ascent phases share: {@code Gravity turn (S1)}, {@code S1
 * separation} and {@code Gravity turn (S2)} hold the same reference, so they fly the same jettison
 * and ignition dates by construction rather than by each re-deriving them (spec {@code
 * docs/mission-stages/01-separations-implicites.md} §5.1).
 *
 * <p><b>Who writes it.</b> Exactly one writer per pass, and never a worker thread:
 *
 * <ul>
 *   <li><b>replay</b> — {@link GravityTurnFirstBurnStage} publishes the plan from the injected
 *       optimization variables when it configures, on the calling thread;
 *   <li><b>optimize</b> — {@link AscentChainPropagation} builds a <em>fresh</em> ref (and a fresh
 *       chain) per evaluation, so the parallel CMA-ES explorations never share one.
 * </ul>
 *
 * <p>The field is {@code volatile} because a plan published on the optimizer thread is read by the
 * JME thread replaying the ephemeris.
 */
public final class AscentPlanRef {

  private volatile AscentPlan plan;

  /**
   * Publishes the plan the ascent phases will fly, replacing any previous one.
   *
   * @param plan the plan, never {@code null}
   */
  public void set(AscentPlan plan) {
    this.plan = Objects.requireNonNull(plan, "plan");
  }

  /**
   * Returns the published plan, or fails loudly — an ascent phase without a plan has no dates to
   * fly and must not silently guess them.
   *
   * @param stageName the phase asking, for the error message
   * @return the published plan
   */
  public AscentPlan require(String stageName) {
    AscentPlan current = plan;
    if (current == null) {
      throw new OrbitlabException(
          "Ascent phase '"
              + stageName
              + "' has no AscentPlan: the gravity turn must be optimized (or a plan published)"
              + " before the ascent phases can be flown");
    }
    return current;
  }

  /**
   * Returns the published plan, or {@code null} when none has been published yet.
   *
   * @return the plan, or {@code null}
   */
  public AscentPlan orNull() {
    return plan;
  }
}
