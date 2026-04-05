package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import java.util.Objects;

/**
 * Groups the optimization results and the generated ephemeris produced by a full mission
 * computation.
 */
public record MissionComputeResult(
    MissionOptimizerResult optimizerResult, MissionEphemeris ephemeris) {
  public MissionComputeResult {
    Objects.requireNonNull(optimizerResult, "optimizerResult");
    Objects.requireNonNull(ephemeris, "ephemeris");
  }
}
