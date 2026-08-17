package com.smousseur.orbitlab.simulation.mission.ephemeris;

import java.util.Objects;

/**
 * One contiguous stretch of a {@link TrajectoryPolyline} expressed in a single frame.
 *
 * <p><b>Why this is a second partition beside {@link PhaseRun} and not a refinement of it.</b> A
 * phase run is a display object: a stage name, a colour, a transition marker. An arc boundary is a
 * change of central body, and the two do not coincide — a sphere-of-influence crossing falls in the
 * middle of a coast, with the same stage name and the same propulsive flag on either side. Folding
 * the arc into the run criterion would cut that coast into two homonymous runs, drawing two phase
 * markers and reporting one phase too many in the mission timeline (PHY-4 / L3, spec {@code
 * docs/multi-corps/05-conception-L3.md} §4).
 *
 * <p>Until L4 produces a second arc, every polyline has exactly one of these, spanning the whole
 * line.
 *
 * @param arc the frame this stretch's vertices are expressed in
 * @param firstVertex index of this arc's first vertex <em>in the polyline</em>, after any decimation
 * @param vertexCount how many vertices this arc spans, after any decimation
 */
public record ArcRun(TrajectoryArc arc, int firstVertex, int vertexCount) {

  public ArcRun {
    Objects.requireNonNull(arc, "arc");
    if (firstVertex < 0) {
      throw new IllegalArgumentException("firstVertex must be >= 0, got " + firstVertex);
    }
    if (vertexCount < 1) {
      throw new IllegalArgumentException("vertexCount must be >= 1, got " + vertexCount);
    }
  }
}
