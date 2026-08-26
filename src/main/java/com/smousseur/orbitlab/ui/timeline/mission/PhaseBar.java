package com.smousseur.orbitlab.ui.timeline.mission;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture2D;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.smousseur.orbitlab.simulation.mission.ephemeris.PhaseRun;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.mission.MissionPhaseShading;
import com.smousseur.orbitlab.ui.timeline.TimelineStyles;
import java.util.ArrayList;
import java.util.List;
import org.orekit.time.AbsoluteDate;

/**
 * The band of coloured rectangles that says how long each phase of the mission lasted (spec {@code
 * docs/navigation/02-timeline-mission.md} §6.3).
 *
 * <p><b>No floor width, ever.</b> A run whose duration projects to less than a pixel is not drawn.
 * That is the whole point of an honest axis: on a GEO the entire ascent really is a fraction of a
 * percent of the flight, and giving those runs a minimum width would make the bar lie about the
 * only quantity it encodes. What stays reachable is the marker, which has no duration to lie about
 * — see {@link PhaseMarkerCluster}.
 *
 * <p>Colours come from {@link MissionPhaseShading#shade}, the same call {@code
 * MissionTrajectoryRenderer} makes for the 3D trajectory. There is deliberately no second table:
 * "this tint means this phase" has to be verifiable by looking at the screen, from one object to
 * the other. The shading was calibrated on 3D arc length, where burns are under 1%, while on a time
 * axis they are around 2%; the same hues therefore take a different share of the track, and that is
 * a property of the two views, not a miscalibration.
 */
final class PhaseBar {

  private static final float SEGMENT_HEIGHT = 10f;
  private static final float RAIL_HEIGHT = 4f;

  /** A run narrower than this is not drawn at all. */
  private static final float MIN_VISIBLE_WIDTH_PX = 1f;

  private final Node parent;
  private final float bandTop;
  private final float railTop;
  private final float segmentZ;
  private final float railZ;
  private final List<Spatial> segments = new ArrayList<>();

  private Panel rail;

  /**
   * @param parent the widget root every element is attached to
   * @param bandTop distance in pixels from the capsule's top edge to the segment band's top
   * @param railTop distance in pixels from the capsule's top edge to the background rail's top
   * @param railZ local z of the background rail
   * @param segmentZ local z of the phase segments
   */
  PhaseBar(Node parent, float bandTop, float railTop, float railZ, float segmentZ) {
    this.parent = parent;
    this.bandTop = bandTop;
    this.railTop = railTop;
    this.railZ = railZ;
    this.segmentZ = segmentZ;
  }

  /**
   * Rebuilds the whole band for a new trajectory. Called once per mission-or-ephemeris identity
   * change, never per frame.
   *
   * @param trail the mission's display polyline
   * @param axis the projection of the mission's window
   * @param missionColor the mission's palette entry
   */
  void rebuild(TrajectoryPolyline trail, TimeAxis axis, ColorRGBA missionColor) {
    clear();
    ensureRail(axis);

    List<PhaseRun> runs = trail.runs();
    ColorRGBA[] shades = MissionPhaseShading.shade(missionColor, runs);

    for (int i = 0; i < runs.size(); i++) {
      AbsoluteDate from = trail.timeAt(runs.get(i).firstVertex());
      AbsoluteDate to =
          i + 1 < runs.size()
              ? trail.timeAt(runs.get(i + 1).firstVertex())
              : trail.timeAt(trail.size() - 1);
      float xFrom = axis.timeToX(from);
      float width = axis.timeToX(to) - xFrom;
      if (width < MIN_VISIBLE_WIDTH_PX) {
        continue;
      }
      Panel segment = new Panel(width, SEGMENT_HEIGHT, TimelineStyles.STYLE);
      segment.setBackground(new QuadBackgroundComponent(shades[i]));
      segment.setSize(segment.getPreferredSize());
      segment.setLocalTranslation(xFrom, -bandTop, segmentZ);
      parent.attachChild(segment);
      segments.add(segment);
    }
  }

  /** Detaches every element this band owns. */
  void clear() {
    for (Spatial s : segments) {
      s.removeFromParent();
    }
    segments.clear();
  }

  /** Detaches the band and its rail; the object is not reusable afterwards. */
  void close() {
    clear();
    if (rail != null) {
      rail.removeFromParent();
      rail = null;
    }
  }

  /**
   * The empty track behind the segments, built once. It is what shows through wherever a run is too
   * short to draw, which is exactly where it should be visible.
   */
  private void ensureRail(TimeAxis axis) {
    if (rail != null) {
      return;
    }
    rail = new Panel(axis.width(), RAIL_HEIGHT, TimelineStyles.STYLE);
    Texture2D tex = TimelineStyles.tex("scrubber-track.png");
    if (tex != null) {
      rail.setBackground(TbtQuadBackgroundComponent.create(tex, 1f, 2, 2, 2, 2, 1f, false));
    } else {
      rail.setBackground(new QuadBackgroundComponent(withAlpha(AppStyles.TL_CYAN_SOFT, 0.20f)));
    }
    rail.setPreferredSize(new Vector3f(axis.width(), RAIL_HEIGHT, 0f));
    rail.setSize(rail.getPreferredSize());
    rail.setLocalTranslation(axis.x0(), -railTop, railZ);
    parent.attachChild(rail);
  }

  private static ColorRGBA withAlpha(ColorRGBA c, float alpha) {
    return new ColorRGBA(c.r, c.g, c.b, alpha);
  }
}
