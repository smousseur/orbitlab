package com.smousseur.orbitlab.ui.timeline.mission;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A group of phase-transition markers drawn as one glyph, and the algorithm that forms the groups
 * (spec {@code docs/navigation/02-timeline-mission.md} §8).
 *
 * <p><b>Why grouping rather than a curved axis.</b> {@link TimeAxis} is linear, so on a GEO the
 * whole ascent — vertical climb, staging, parking insertion, GTO injection — falls inside the
 * first few pixels of the track. Segments that short are invisible by construction and that is
 * correct, because their duration really is negligible at the mission's scale. A marker has no
 * duration, though: letting it inherit that compression would make it unreachable exactly where a
 * user wants to look. Grouping is how the linear axis is paid for.
 *
 * <p><b>The group sits on its first transition, not on its barycentre.</b> What one is aiming at
 * when clicking a cluster is the start of the sequence.
 *
 * <p>Free of any JME dependency: run indices and pixel coordinates only.
 */
public record PhaseMarkerCluster(float x, List<Integer> runIndices) {

  /** Default centre-to-centre distance under which two markers are merged. */
  public static final float MIN_SPACING_PX = 8f;

  /** Width of the marker glyph, {@code event-marker-*.png} being 8×6. */
  public static final float MARKER_WIDTH_PX = 8f;

  public PhaseMarkerCluster {
    Objects.requireNonNull(runIndices, "runIndices");
    if (runIndices.isEmpty()) {
      throw new IllegalArgumentException("a cluster holds at least one marker");
    }
    runIndices = List.copyOf(runIndices);
  }

  /**
   * One transition awaiting grouping.
   *
   * @param runIndex index into {@code TrajectoryPolyline.runs()} of the run this transition opens
   * @param x the transition's projected x, before any edge clamping
   */
  public record Anchor(int runIndex, float x) {}

  /**
   * How many transitions this cluster stands for.
   *
   * @return the member count, at least 1
   */
  public int size() {
    return runIndices.size();
  }

  /**
   * The run opened by this cluster's earliest transition — the seek target of a click.
   *
   * @return the first member's run index
   */
  public int firstRunIndex() {
    return runIndices.get(0);
  }

  /**
   * Whether this cluster stands for more than one transition, in which case it is drawn in the
   * chrome tint with a {@code ×N} count rather than in a phase colour.
   *
   * @return {@code true} when it holds two or more transitions
   */
  public boolean isGroup() {
    return runIndices.size() > 1;
  }

  /**
   * Groups projected transitions and holds the result off both track edges.
   *
   * <p>Spacing is measured against the <em>anchor</em> of the group being filled, not against the
   * previous member: measuring against the previous member would let a chain of markers each 7 px
   * apart merge into one group spanning the whole track.
   *
   * <p>The edge clamp of §8 is applied to every cluster, lone markers included: a marker landing
   * 0.14 px from the rail's start would be cropped in half by the shell's 9-slice border whether
   * or not it stands for several transitions, and on a GEO that is the most interesting marker of
   * the flight.
   *
   * @param anchors the transitions, in chronological order
   * @param minSpacingPx the merge distance in pixels
   * @param minX the smallest centre a marker may take
   * @param maxX the largest centre a marker may take
   * @return the clusters, in chronological order; every anchor appears in exactly one
   */
  public static List<PhaseMarkerCluster> cluster(
      List<Anchor> anchors, float minSpacingPx, float minX, float maxX) {
    Objects.requireNonNull(anchors, "anchors");
    List<PhaseMarkerCluster> clusters = new ArrayList<>();
    List<Integer> members = null;
    float anchorX = 0f;
    for (Anchor a : anchors) {
      if (members == null || a.x() - anchorX > minSpacingPx) {
        if (members != null) {
          clusters.add(new PhaseMarkerCluster(clamp(anchorX, minX, maxX), members));
        }
        members = new ArrayList<>();
        anchorX = a.x();
      }
      members.add(a.runIndex());
    }
    if (members != null) {
      clusters.add(new PhaseMarkerCluster(clamp(anchorX, minX, maxX), members));
    }
    return List.copyOf(clusters);
  }

  private static float clamp(float v, float lo, float hi) {
    return Math.max(lo, Math.min(hi, v));
  }
}
