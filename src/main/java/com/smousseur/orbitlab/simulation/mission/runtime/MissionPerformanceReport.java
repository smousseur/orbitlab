package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.vehicle.StagePropellant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregated mass and ΔV accounting of a computed mission. Instrument for calibrating the
 * analytic propellant budget (residual propellant, measured ascent losses) — spec 06 §4.7.
 *
 * <p>Residual propellant is reported twice, at two different granularities:
 *
 * <ul>
 *   <li>{@link #totalPropellantResidual()} — everything still aboard at mission end, whole stack.
 *       Dominated by whatever sits above the final active stage (a payload kick motor, say), so it
 *       is <em>not</em> a usable margin for any single stage.
 *   <li>{@link #stagePropellants()} — the true per-stage split (bilan 10 §6). Use this to judge the
 *       margin of a specific stage, in particular the propellant-sized one, which is not
 *       necessarily the final active stage.
 * </ul>
 *
 * @param stages the per-mission-stage accounting, in execution order
 * @param totalDeltaV the summed ΔV of all propulsed stages (m/s)
 * @param totalPropellantLoaded the propellant loaded at lift-off, whole stack (kg)
 * @param totalPropellantResidual the propellant still aboard at mission end, whole stack (kg)
 * @param stagePropellants the per-vehicle-stage propellant split, in stack order; empty when the
 *     report was built without vehicle context
 */
public record MissionPerformanceReport(
    List<StagePerformance> stages,
    double totalDeltaV,
    double totalPropellantLoaded,
    double totalPropellantResidual,
    List<StagePropellant> stagePropellants) {

  public MissionPerformanceReport {
    Objects.requireNonNull(stages, "stages");
    Objects.requireNonNull(stagePropellants, "stagePropellants");
    stages = List.copyOf(stages);
    stagePropellants = List.copyOf(stagePropellants);
  }

  /**
   * Creates a report without the per-vehicle-stage split. Only for callers that have no vehicle
   * context; production reports carry the split.
   */
  public MissionPerformanceReport(
      List<StagePerformance> stages,
      double totalDeltaV,
      double totalPropellantLoaded,
      double totalPropellantResidual) {
    this(stages, totalDeltaV, totalPropellantLoaded, totalPropellantResidual, List.of());
  }

  /** Residual propellant as a fraction of the loaded propellant (0 when nothing was loaded). */
  public double residualRatio() {
    return totalPropellantLoaded > 0 ? totalPropellantResidual / totalPropellantLoaded : 0.0;
  }

  /**
   * The propellant accounting of one physical stage.
   *
   * @param stageIndex the stack index (0 = bottom stage), matching the launcher stage order
   * @return the stage's accounting, or empty when the report carries no per-stage split or the
   *     index is out of range
   */
  public Optional<StagePropellant> residualForStage(int stageIndex) {
    return stagePropellants.stream().filter(sp -> sp.stageIndex() == stageIndex).findFirst();
  }
}
