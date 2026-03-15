package com.smousseur.orbitlab.tools.ephemerisgen;

/**
 * Sampling and chunking parameters for generating ephemeris data of a single celestial body.
 *
 * <p>All time intervals must be positive and finite.
 *
 * @param dtPvSeconds time step in seconds between consecutive position/velocity samples
 * @param dtRotSeconds time step in seconds between consecutive rotation (attitude) samples
 * @param chunkDurationSeconds duration in seconds of each data chunk in the output file
 */
public record BodyGenerationParams(double dtPvSeconds, double dtRotSeconds, double chunkDurationSeconds) {
  public BodyGenerationParams {
    if (!Double.isFinite(dtPvSeconds) || dtPvSeconds <= 0) throw new IllegalArgumentException("dtPvSeconds");
    if (!Double.isFinite(dtRotSeconds) || dtRotSeconds <= 0) throw new IllegalArgumentException("dtRotSeconds");
    if (!Double.isFinite(chunkDurationSeconds) || chunkDurationSeconds <= 0) throw new IllegalArgumentException("chunkDurationSeconds");
  }
}
