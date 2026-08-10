package com.smousseur.orbitlab.simulation.mission.ephemeris;

import java.util.Objects;

/**
 * One contiguous stretch of a {@link TrajectoryPolyline} produced by a single mission stage.
 *
 * <p><b>Why runs and not distinct stage names.</b> Stage names are free-form strings chosen per
 * mission ({@code "Coasting parking"}, {@code "GTO injection"}, {@code "Trim"}…), and nothing stops
 * two stages of one mission from sharing one. Keying on the name would merge two segments of flight
 * that are not the same segment. A run is defined by contiguity instead, which is also what a
 * transition marker needs: one marker per run boundary.
 *
 * <p>Two <em>adjacent</em> stages with the same name and the same propulsive flag do merge into a
 * single run. That is deliberate — nothing distinguishes them on screen, so nothing should.
 *
 * @param stageName the name of the stage that produced this stretch, for labelling
 * @param propulsive whether that stage burns propellant
 * @param firstVertex index of this run's first vertex <em>in the polyline</em>, after any
 *     decimation
 * @param vertexCount how many vertices this run spans, after any decimation. A run of fewer than
 *     {@link #MIN_DRAWABLE_VERTICES} draws no segment at all — {@code StageSeparationStage} is
 *     instantaneous and yields one — so consumers that shade the line by run must not let it take a
 *     place in their ordering. It still deserves its transition marker: a staging event is exactly
 *     what one wants to see.
 */
public record PhaseRun(String stageName, boolean propulsive, int firstVertex, int vertexCount) {

  /** Fewer vertices than this and the run has no segment to colour. */
  public static final int MIN_DRAWABLE_VERTICES = 2;

  public PhaseRun {
    Objects.requireNonNull(stageName, "stageName");
    if (firstVertex < 0) {
      throw new IllegalArgumentException("firstVertex must be >= 0, got " + firstVertex);
    }
    if (vertexCount < 1) {
      throw new IllegalArgumentException("vertexCount must be >= 1, got " + vertexCount);
    }
  }

  /**
   * Whether this run is long enough to carry a colour of its own.
   *
   * @return {@code true} when the run spans at least one drawable segment
   */
  public boolean isDrawable() {
    return vertexCount >= MIN_DRAWABLE_VERTICES;
  }
}
