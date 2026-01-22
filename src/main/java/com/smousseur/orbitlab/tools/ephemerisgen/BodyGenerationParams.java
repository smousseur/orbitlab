package com.smousseur.orbitlab.tools.ephemerisgen;

public record BodyGenerationParams(double dtPvSeconds, double dtRotSeconds, double chunkDurationSeconds) {
  public BodyGenerationParams {
    if (!Double.isFinite(dtPvSeconds) || dtPvSeconds <= 0) throw new IllegalArgumentException("dtPvSeconds");
    if (!Double.isFinite(dtRotSeconds) || dtRotSeconds <= 0) throw new IllegalArgumentException("dtRotSeconds");
    if (!Double.isFinite(chunkDurationSeconds) || chunkDurationSeconds <= 0) throw new IllegalArgumentException("chunkDurationSeconds");
  }
}
