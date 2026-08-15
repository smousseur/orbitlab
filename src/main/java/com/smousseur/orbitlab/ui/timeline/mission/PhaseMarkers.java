package com.smousseur.orbitlab.ui.timeline.mission;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.IconComponent;
import com.smousseur.orbitlab.simulation.mission.ephemeris.PhaseRun;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.mission.MissionPhaseShading;
import com.smousseur.orbitlab.ui.timeline.TimelineStyles;
import java.util.ArrayList;
import java.util.List;

/**
 * The transition markers of the track: one per phase-run boundary, grouped where the axis has
 * squeezed them together (spec {@code docs/navigation/02-timeline-mission.md} §8).
 *
 * <p><b>Non-drawable runs keep their marker.</b> {@code StageSeparationStage} is instantaneous and
 * yields a run of one vertex, so it colours no segment — but a staging event is exactly what one
 * wants to see. That is the rule {@link PhaseRun}'s own Javadoc states for the 3D markers, applied
 * here unchanged.
 *
 * <p>The first run carries no marker: the start of the mission is the window's bound, not a
 * transition.
 *
 * <p>A group is drawn in the chrome tint rather than in a phase colour, because it stands for
 * several phases at once and no single one of them owns it.
 */
final class PhaseMarkers {

  private static final float MARKER_WIDTH = 8f;
  private static final float MARKER_HEIGHT = 6f;
  private static final float COUNT_LABEL_WIDTH = 20f;

  /**
   * Height of the {@code ×N} count label, matched to the font size below. Not a free pixel value:
   * {@code share-tech-mono} only ships bundled at 10, 11, 12 and 14 px ({@code
   * src/main/resources/fonts/}), so any other size passed to {@link TimelineStyles#mono(int)}
   * silently falls back to Lemur's default typeface instead of failing loudly. Keep this at 10 in
   * lockstep with the {@code setFont}/{@code setFontSize} calls in {@link #attachCount} — do not
   * "tidy" it back down to a smaller value without adding that size to the font pack first.
   */
  private static final float COUNT_LABEL_HEIGHT = 10f;

  private static final float COUNT_LABEL_GAP = 1f;

  /** How far from a marker's centre a cursor still counts as being on it. */
  static final float HIT_RADIUS_PX = 6f;

  private final Node parent;
  private final float bandTop;
  private final float z;
  private final List<Spatial> elements = new ArrayList<>();
  private List<PhaseMarkerCluster> clusters = List.of();

  /**
   * @param parent the widget root every element is attached to
   * @param bandTop distance in pixels from the capsule's top edge to the marker band's top
   * @param z local z of the markers
   */
  PhaseMarkers(Node parent, float bandTop, float z) {
    this.parent = parent;
    this.bandTop = bandTop;
    this.z = z;
  }

  /**
   * Rebuilds every marker for a new trajectory.
   *
   * @param trail the mission's display polyline
   * @param axis the projection of the mission's window
   * @param missionColor the mission's palette entry, shaded per run for lone markers
   */
  void rebuild(TrajectoryPolyline trail, TimeAxis axis, ColorRGBA missionColor) {
    clear();

    List<PhaseRun> runs = trail.runs();
    List<PhaseMarkerCluster.Anchor> anchors = new ArrayList<>(Math.max(0, runs.size() - 1));
    for (int i = 1; i < runs.size(); i++) {
      anchors.add(
          new PhaseMarkerCluster.Anchor(i, axis.timeToX(trail.timeAt(runs.get(i).firstVertex()))));
    }

    float half = PhaseMarkerCluster.MARKER_WIDTH_PX / 2f;
    clusters =
        PhaseMarkerCluster.cluster(
            anchors,
            PhaseMarkerCluster.MIN_SPACING_PX,
            axis.x0() + half,
            axis.x0() + axis.width() - half);

    ColorRGBA[] shades = MissionPhaseShading.shade(missionColor, runs);
    for (PhaseMarkerCluster cluster : clusters) {
      ColorRGBA tint = cluster.isGroup() ? AppStyles.TL_CYAN : shades[cluster.firstRunIndex()];
      attachGlyph(cluster.x(), tint);
      if (cluster.isGroup()) {
        attachCount(cluster.x(), cluster.size());
      }
    }
  }

  /**
   * The cluster whose glyph is under the given track x, if any. Used by the widget to give a
   * marker's content priority over the bar's on hover, and to seek to a group's first transition
   * on click (§9.1).
   *
   * @param trackX an x in the widget's local space
   * @return the cluster hit, or {@code null}
   */
  PhaseMarkerCluster clusterAt(float trackX) {
    PhaseMarkerCluster best = null;
    float bestDistance = HIT_RADIUS_PX;
    for (PhaseMarkerCluster cluster : clusters) {
      float distance = Math.abs(cluster.x() - trackX);
      if (distance <= bestDistance) {
        bestDistance = distance;
        best = cluster;
      }
    }
    return best;
  }

  /** Detaches every marker. */
  void clear() {
    for (Spatial s : elements) {
      s.removeFromParent();
    }
    elements.clear();
    clusters = List.of();
  }

  private void attachGlyph(float centreX, ColorRGBA tint) {
    // Tinting a texture goes through IconComponent.setColor, not through a Panel background —
    // the mechanism LiveIndicator already relies on. It is what lets a lone marker take its own
    // phase colour instead of being limited to the three tints shipped in the texture pack.
    Label holder = new Label("", TimelineStyles.STYLE);
    if (TimelineStyles.tex("event-marker-cyan.png") != null) {
      IconComponent icon = new IconComponent("interface/timeline/event-marker-cyan.png");
      icon.setIconSize(new Vector2f(MARKER_WIDTH, MARKER_HEIGHT));
      icon.setHAlignment(HAlignment.Center);
      icon.setVAlignment(VAlignment.Center);
      icon.setColor(tint);
      holder.setIcon(icon);
    }
    holder.setBackground(null);
    holder.setPreferredSize(new Vector3f(MARKER_WIDTH, MARKER_HEIGHT, 0f));
    holder.setSize(holder.getPreferredSize());
    holder.setLocalTranslation(centreX - MARKER_WIDTH / 2f, -bandTop, z);
    parent.attachChild(holder);
    elements.add(holder);
  }

  private void attachCount(float centreX, int count) {
    Label label = new Label("×" + count, TimelineStyles.STYLE);
    label.setFont(TimelineStyles.mono(10));
    label.setFontSize(10f);
    label.setColor(AppStyles.TL_CYAN);
    label.setBackground(null);
    label.setTextHAlignment(HAlignment.Left);
    label.setTextVAlignment(VAlignment.Center);
    label.setPreferredSize(new Vector3f(COUNT_LABEL_WIDTH, COUNT_LABEL_HEIGHT, 0f));
    label.setSize(label.getPreferredSize());
    label.setLocalTranslation(
        centreX + MARKER_WIDTH / 2f + COUNT_LABEL_GAP, -bandTop, z);
    parent.attachChild(label);
    elements.add(label);
  }
}
