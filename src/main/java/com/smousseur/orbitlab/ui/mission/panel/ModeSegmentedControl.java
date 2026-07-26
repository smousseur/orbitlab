package com.smousseur.orbitlab.ui.mission.panel;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.ui.UiKit;
import java.util.function.Consumer;

/**
 * A 3-state segmented control (Fast / Balanced / Precise) driving a mission's {@link
 * OptimizationType}. Exactly one segment is active (radio semantics). The active segment reflects
 * {@link MissionEntry#getOptimizationType()} at build time; clicking an inactive segment fires the
 * {@code onSelect} callback, whose implementer updates the entry and rebuilds the row — so the
 * control does not track selection state itself.
 *
 * <p>Textures live in the roster asset dir ({@code mode-<name>-default|hover|active|disabled}).
 */
final class ModeSegmentedControl {

  private static final float SEGMENT_W = 30f;
  private static final float SEGMENT_H = 22f;
  private static final float ICON_SIZE = 15f;
  private static final float BORDER = 1f;

  private static final ColorRGBA BORDER_COLOR = new ColorRGBA(0.071f, 0.161f, 0.267f, 0.3f);

  private ModeSegmentedControl() {}

  /**
   * Builds the segmented control for the given entry.
   *
   * @param entry the mission entry whose {@link OptimizationType} is displayed
   * @param enabled when {@code false} the segments are inert (e.g. while COMPUTING)
   * @param onSelect invoked with the chosen type when an inactive segment is clicked
   * @return the control container
   */
  static Container build(MissionEntry entry, boolean enabled, Consumer<OptimizationType> onSelect) {
    OptimizationType current = entry.getOptimizationType();

    // Outer group: rounded-border frame (the app's standard "input" 9-slice: rounded corners +
    // subtle border), with a small inset so the frame is visible around the segments.
    TbtQuadBackgroundComponent groupBg = UiKit.wizardBg9("input", 8);
    groupBg.setMargin(0f, 0f);
    Container group = new Container(new BoxLayout(Axis.X, FillMode.None));
    group.setBackground(groupBg);
    group.setInsets(new Insets3f(2, 4, 2, 4));

    OptimizationType[] order = {
      OptimizationType.FAST, OptimizationType.BALANCED, OptimizationType.PRECISE
    };
    for (int i = 0; i < order.length; i++) {
      OptimizationType type = order[i];
      group.addChild(segment(type, type == current, enabled, onSelect));
      if (i < order.length - 1) {
        Container divider = new Container();
        divider.setPreferredSize(new Vector3f(BORDER, SEGMENT_H, 0));
        divider.setBackground(new QuadBackgroundComponent(BORDER_COLOR));
        group.addChild(divider);
      }
    }
    return group;
  }

  private static Container segment(
      OptimizationType type, boolean active, boolean enabled, Consumer<OptimizationType> onSelect) {
    final String base = "mode-" + type.name().toLowerCase();
    final String defaultTex = base + "-default";
    final String activeTex = base + "-active";
    final String hoverTex = base + "-hover";
    final String disabledTex = base + "-disabled";

    final String baseTex = active ? activeTex : defaultTex;

    final Container seg = new Container(new BoxLayout(Axis.Y, FillMode.None));
    seg.setPreferredSize(new Vector3f(SEGMENT_W, SEGMENT_H, 0));
    seg.setBackground(null);

    final Container icon = new Container();
    icon.setPreferredSize(new Vector3f(ICON_SIZE, ICON_SIZE, 0));
    icon.setBackground(UiKit.rosterFlat(baseTex));

    float vPad = Math.max(0f, (SEGMENT_H - ICON_SIZE) * 0.5f);
    float hPad = Math.max(0f, (SEGMENT_W - ICON_SIZE) * 0.5f);
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    row.addChild(UiKit.hSpacer(hPad));
    row.addChild(icon);
    seg.addChild(UiKit.vSpacer(vPad));
    seg.addChild(row);
    seg.addChild(UiKit.vSpacer(vPad));

    if (!enabled) {
      icon.setBackground(UiKit.rosterFlat(disabledTex));
      return seg;
    }

    MouseEventControl.addListenersToSpatial(
        seg,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent evt, Spatial target, Spatial capture) {
            icon.setBackground(UiKit.rosterFlat(hoverTex));
          }

          @Override
          public void mouseExited(MouseMotionEvent evt, Spatial target, Spatial capture) {
            icon.setBackground(UiKit.rosterFlat(baseTex));
          }

          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            event.setConsumed(); // never fall through to row selection
            if (!active) {
              onSelect.accept(type);
            }
          }
        });
    return seg;
  }
}
