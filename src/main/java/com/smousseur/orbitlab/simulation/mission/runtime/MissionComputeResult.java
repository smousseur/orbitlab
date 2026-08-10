package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import java.util.Objects;

/**
 * Groups the optimization results, the generated ephemeris and the performance report produced by a
 * full mission computation, together with the {@link Mission} actually flown.
 *
 * <p>The {@code mission} is the object the optimizer mutated (stage solutions applied, launch date
 * and final status set). For a fixed-load computation it is the same mission handed in; for a
 * propellant-sizing sweep it is the internal winning mission flown at the resolved loads, which the
 * caller adopts so the mission-level view (vehicle loads, solved stages) matches the ephemeris.
 *
 * <p>{@code achievedOrbit} is the orbit at the end of the flown mission, in both the osculating and
 * mean conventions. Reporting only — nothing reads it back into the computation. It is carried on
 * the result rather than merely logged so the UI can display it without recomputing.
 */
public record MissionComputeResult(
    MissionOptimizerResult optimizerResult,
    MissionEphemeris ephemeris,
    MissionPerformanceReport performanceReport,
    Mission mission,
    AchievedOrbit achievedOrbit) {
  public MissionComputeResult {
    Objects.requireNonNull(optimizerResult, "optimizerResult");
    Objects.requireNonNull(ephemeris, "ephemeris");
    Objects.requireNonNull(performanceReport, "performanceReport");
    Objects.requireNonNull(mission, "mission");
    Objects.requireNonNull(achievedOrbit, "achievedOrbit");
  }
}
