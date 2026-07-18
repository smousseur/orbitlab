package com.smousseur.orbitlab.simulation.mission.runtime;

import java.util.List;
import java.util.Objects;

/**
 * Aggregated mass and ΔV accounting of a computed mission. Instrument for calibrating the
 * analytic propellant budget (residual propellant, measured ascent losses) — spec 06 §4.7.
 *
 * @param stages the per-stage accounting, in execution order
 * @param totalDeltaV the summed ΔV of all propulsed stages (m/s)
 * @param totalPropellantLoaded the propellant loaded at lift-off, whole stack (kg)
 * @param totalPropellantResidual the propellant still aboard at mission end (kg)
 */
public record MissionPerformanceReport(
    List<StagePerformance> stages,
    double totalDeltaV,
    double totalPropellantLoaded,
    double totalPropellantResidual) {

  public MissionPerformanceReport {
    Objects.requireNonNull(stages, "stages");
    stages = List.copyOf(stages);
  }

  /** Residual propellant as a fraction of the loaded propellant (0 when nothing was loaded). */
  public double residualRatio() {
    return totalPropellantLoaded > 0 ? totalPropellantResidual / totalPropellantLoaded : 0.0;
  }
}
