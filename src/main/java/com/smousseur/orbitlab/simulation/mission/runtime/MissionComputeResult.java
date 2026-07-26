package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import java.util.Objects;

/**
 * Groups the optimization results, the generated ephemeris and the performance report produced by
 * a full mission computation, together with the {@link Mission} actually flown.
 *
 * <p>The {@code mission} is the object the optimizer mutated (stage solutions applied, launch date
 * and final status set). For a fixed-load computation it is the same mission handed in; for a
 * propellant-sizing sweep it is the internal winning mission flown at the resolved loads, which the
 * caller adopts so the mission-level view (vehicle loads, solved stages) matches the ephemeris.
 */
public record MissionComputeResult(
    MissionOptimizerResult optimizerResult,
    MissionEphemeris ephemeris,
    MissionPerformanceReport performanceReport,
    Mission mission) {
  public MissionComputeResult {
    Objects.requireNonNull(optimizerResult, "optimizerResult");
    Objects.requireNonNull(ephemeris, "ephemeris");
    Objects.requireNonNull(performanceReport, "performanceReport");
    Objects.requireNonNull(mission, "mission");
  }
}
