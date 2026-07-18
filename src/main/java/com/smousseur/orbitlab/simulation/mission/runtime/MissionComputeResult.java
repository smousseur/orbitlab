package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import java.util.Objects;

/**
 * Groups the optimization results, the generated ephemeris and the performance report produced by
 * a full mission computation.
 */
public record MissionComputeResult(
    MissionOptimizerResult optimizerResult,
    MissionEphemeris ephemeris,
    MissionPerformanceReport performanceReport) {
  public MissionComputeResult {
    Objects.requireNonNull(optimizerResult, "optimizerResult");
    Objects.requireNonNull(ephemeris, "ephemeris");
    Objects.requireNonNull(performanceReport, "performanceReport");
  }
}
