package com.smousseur.orbitlab.ui.mission.detail;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.simulation.mission.runtime.StagePerformance;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.MissionResultText;
import java.util.List;

/**
 * The per-stage table of the detail view: name, duration, ΔV and propellant burnt, in execution
 * order.
 *
 * <p>No pagination: the panel is sized so that the longest chain in the catalog fits whole. That is
 * the GEO profile at <b>12</b> stages — vertical ascent, the three gravity-turn phases, then
 * parking, coast, GTO injection, S2 separation, circularization, trim, plane trim and the final
 * coast. {@link #MAX_ROWS} sits above that so it stays a guard against a future profile rather than
 * a live limit, and says so on screen instead of silently dropping stages.
 *
 * <p>Adding stages to a mission profile is therefore a layout change too: past {@link #MAX_ROWS}
 * rows the chain is cut, and past what {@code MissionPanelWidget.WINDOW_HEIGHT} budgets the surplus
 * rows draw over the footer rather than scrolling.
 */
final class DetailStageTable {

  static final int MAX_ROWS = 16;

  private static final float ROW_HEIGHT = 18f;
  private static final float COL_NAME = 190f;
  private static final float COL_DURATION = 70f;
  private static final float COL_DELTA_V = 90f;
  private static final float COL_PROPELLANT = 90f;
  private static final float HEADER_ALPHA = 0.6f;
  private static final int NAME_MAX_CHARS = 26;

  private final Container root;

  DetailStageTable(List<StagePerformance> stages) {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);

    root.addChild(headerRow());

    int shown = Math.min(stages.size(), MAX_ROWS);
    for (int i = 0; i < shown; i++) {
      root.addChild(stageRow(stages.get(i)));
    }
    if (stages.size() > MAX_ROWS) {
      Label more =
          root.addChild(
              new Label("... " + (stages.size() - MAX_ROWS) + " more stages", FormStyles.STYLE));
      more.setFont(UiKit.ibmPlexMono(11));
      more.setColor(FormStyles.TEXT_LO);
    }
  }

  Container getNode() {
    return root;
  }

  private static Container headerRow() {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    ColorRGBA color = FormStyles.TEXT_LO.clone();
    color.a = HEADER_ALPHA;
    row.addChild(cell("STAGE", COL_NAME, color));
    row.addChild(cell("DUR", COL_DURATION, color));
    row.addChild(cell("DV", COL_DELTA_V, color));
    row.addChild(cell("PROP", COL_PROPELLANT, color));
    return row;
  }

  private static Container stageRow(StagePerformance stage) {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    row.addChild(
        cell(
            MissionResultText.truncate(stage.stageName(), NAME_MAX_CHARS),
            COL_NAME,
            FormStyles.TEXT_PRIMARY));
    row.addChild(
        cell(
            MissionResultText.formatDuration(stage.durationSeconds()),
            COL_DURATION,
            FormStyles.TEXT_SECONDARY));
    row.addChild(
        cell(
            MissionResultText.formatDeltaV(stage.deltaV()),
            COL_DELTA_V,
            FormStyles.TEXT_SECONDARY));
    row.addChild(
        cell(
            MissionResultText.formatPropellant(stage.propellantConsumed()),
            COL_PROPELLANT,
            FormStyles.TEXT_SECONDARY));
    return row;
  }

  private static Label cell(String text, float width, ColorRGBA color) {
    Label label = new Label(text, FormStyles.STYLE);
    label.setFont(UiKit.ibmPlexMono(11));
    label.setColor(color);
    label.setTextHAlignment(HAlignment.Left);
    label.setPreferredSize(new Vector3f(width, ROW_HEIGHT, 0));
    return label;
  }
}
