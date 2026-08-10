package com.smousseur.orbitlab.ui.mission.detail;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.smousseur.orbitlab.simulation.OrbitElements;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.runtime.AchievedOrbit;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionPerformanceReport;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.MissionResultText;
import com.smousseur.orbitlab.ui.mission.MissionTargetOrbit;
import java.util.Locale;
import java.util.Optional;

/**
 * The detail screen of one mission: what it was asked to reach, what it achieved in both
 * conventions, its mass and ΔV totals, and its stage chain. Occupies the panel's content area in
 * place of the mission list.
 *
 * <p>Rebuilt from scratch on every display — like the footer, it holds no state beyond its
 * containers, and the panel owns the refresh cadence.
 *
 * <p>ASCII only, same constraint as the rest of the mission panel: the bundled bitmap fonts carry
 * glyphs 32-127.
 */
public final class MissionDetailView {

  private static final float PAD_X = 32f;
  private static final float PAD_Y = 20f;
  private static final float BACK_BTN_W = 90f;
  private static final float BACK_BTN_H = 22f;
  private static final float ITEM_GAP = 12f;
  private static final float BLOCK_GAP = 14f;
  private static final float LINE_GAP = 4f;
  private static final float LABEL_COL = 110f;
  private static final float LINE_HEIGHT = 16f;
  private static final int ERROR_WRAP_CHARS = 78;

  private static final String NO_RESULT =
      "This mission has not produced a result yet - run Compute.";
  private static final String CONVENTION_CAPTION =
      "mean and osculating cannot both be circular - J2 short-period, not an insertion miss";
  private static final String UNAVAILABLE = "unavailable";

  private final Container root;

  private Runnable onBack = () -> {};

  /**
   * Builds the detail view of one mission.
   *
   * @param entry the mission to detail
   * @param width the panel width
   * @param height the content area height
   */
  public MissionDetailView(MissionEntry entry, float width, float height) {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);
    root.setPreferredSize(new Vector3f(width, height, 0));
    root.setInsetsComponent(new InsetsComponent(new Insets3f(PAD_Y, PAD_X, PAD_Y, PAD_X)));

    addHeader(entry);
    root.addChild(UiKit.vSpacer(BLOCK_GAP));

    if (entry.mission().getStatus() == MissionStatus.FAILED) {
      addError(entry);
      return;
    }

    boolean anything = addOrbitBlock(entry);
    anything |= addPerformanceBlock(entry);
    if (!anything) {
      addLine(NO_RESULT, FormStyles.TEXT_LO);
    }
  }

  public Container getNode() {
    return root;
  }

  /**
   * Sets the action run when the user leaves the detail view.
   *
   * @param action the action
   */
  public void setOnBack(Runnable action) {
    this.onBack = action != null ? action : () -> {};
  }

  private void addHeader(MissionEntry entry) {
    Container row = root.addChild(newRow());

    Button back = new Button("< BACK", FormStyles.STYLE);
    back.setFont(UiKit.ibmPlexMono(11));
    back.setColor(FormStyles.ACCENT_BRIGHT);
    back.setBackground(null);
    back.setInsetsComponent(new InsetsComponent(new Insets3f(0, 0, 0, 0)));
    back.setTextVAlignment(VAlignment.Center);
    back.setPreferredSize(new Vector3f(BACK_BTN_W, BACK_BTN_H, 0));
    back.addClickCommands(src -> onBack.run());
    row.addChild(back);

    row.addChild(UiKit.hSpacer(ITEM_GAP));

    Label name = row.addChild(new Label(entry.mission().getName(), FormStyles.STYLE));
    name.setFont(UiKit.orbitron(13));
    name.setColor(FormStyles.TEXT_PRIMARY);
    centerOnButtonHeight(name);

    row.addChild(UiKit.hSpacer(ITEM_GAP));

    MissionStatus status = entry.mission().getStatus();
    Label statusLabel = row.addChild(new Label("[ " + status.name() + " ]", FormStyles.STYLE));
    statusLabel.setFont(UiKit.ibmPlexMono(11));
    statusLabel.setColor(statusColor(status));
    centerOnButtonHeight(statusLabel);
  }

  /**
   * Grows a header label's box to the back button's height and centres its text in it, so the two
   * sit on the same baseline. {@code BoxLayout} on the X axis aligns the top edges of its children,
   * and the button is taller than a line of text, so without this the caption is drawn a few pixels
   * low. Same idiom as {@code MissionRow}, which centres its cells over the row height.
   *
   * @param label the label to grow, already carrying its final font
   */
  private static void centerOnButtonHeight(Label label) {
    // Read after the font is set: the preferred width is measured from the glyphs.
    label.setPreferredSize(new Vector3f(label.getPreferredSize().x, BACK_BTN_H, 0));
    label.setTextVAlignment(VAlignment.Center);
  }

  /**
   * Target, osculating and mean, each with its own deviation against the same target figures.
   *
   * <p>Both conventions are shown because they do not say the same thing and the two mission types
   * do not aim in the same one: the GEO trim targets mean elements, the LEO ascent cost treats the
   * objective as osculating. Measured on a 400 km circular insertion, the two readings differ by
   * about 9.4 km — hence the caption, without which a perfect insertion reads as a failure.
   *
   * @return whether anything was drawn
   */
  private boolean addOrbitBlock(MissionEntry entry) {
    Optional<AchievedOrbit> achieved = entry.getAchievedOrbit();
    if (achieved.isEmpty()) {
      return false;
    }
    AchievedOrbit orbit = achieved.get();
    // AchievedOrbit.UNAVAILABLE carries neither convention. Drawing two "unavailable" lines under a
    // caption about J2 short-period terms would explain a discrepancy between two numbers that are
    // not there; let the caller fall through to its "no result" line instead.
    if (!orbit.hasOsculating() && !orbit.hasMean()) {
      return false;
    }
    MissionTargetOrbit target = MissionTargetOrbit.forEntry(entry).orElse(null);

    if (target != null) {
      addKeyedLine(
          "TARGET",
          String.format(
                  Locale.ROOT,
                  "%.0f x %.0f m   ",
                  target.perigeeAltitude(),
                  target.apogeeAltitude())
              + MissionResultText.formatInclination(target.inclination()),
          FormStyles.TEXT_SECONDARY);
    }

    addConventionLine("OSCULATING", orbit.osculating(), target);
    addConventionLine("MEAN", orbit.mean(), target);

    addLine(CONVENTION_CAPTION, FormStyles.TEXT_LO);
    return true;
  }

  /**
   * One convention's line. Both parameters are nullable rather than {@code Optional}: a helper that
   * has to handle absence takes the value type and null-checks it (project rule in {@code
   * CLAUDE.md}), which is also what the {@code elements} check below already does.
   *
   * @param key the convention label
   * @param elements the elements in that convention, or {@code null} when the reading failed
   * @param target the requested orbit, or {@code null} when the mission carries no displayable one
   */
  private void addConventionLine(String key, OrbitElements elements, MissionTargetOrbit target) {
    if (elements == null) {
      addKeyedLine(key, UNAVAILABLE, FormStyles.TEXT_LO);
      return;
    }
    String value =
        MissionResultText.formatAltitudes(elements)
            + "   "
            + MissionResultText.formatInclination(elements.inclination())
            + (target == null ? "" : "   " + MissionResultText.formatMiss(elements, target));
    addKeyedLine(key, value, FormStyles.TEXT_PRIMARY);
  }

  /**
   * Totals then the stage chain.
   *
   * @return whether anything was drawn
   */
  private boolean addPerformanceBlock(MissionEntry entry) {
    Optional<MissionPerformanceReport> report = entry.getPerformanceReport();
    if (report.isEmpty()) {
      return false;
    }
    MissionPerformanceReport r = report.get();

    root.addChild(UiKit.vSpacer(BLOCK_GAP));
    addLine(
        "TOTAL DV "
            + MissionResultText.formatDeltaV(r.totalDeltaV())
            + "    LOADED "
            + MissionResultText.formatPropellant(r.totalPropellantLoaded())
            + "    RESIDUAL "
            + MissionResultText.formatPropellant(r.totalPropellantResidual())
            + String.format(Locale.ROOT, " (%.2f%%)", 100.0 * r.residualRatio()),
        FormStyles.TEXT_SECONDARY);

    root.addChild(UiKit.vSpacer(BLOCK_GAP));
    root.addChild(new DetailStageTable(r.stages()).getNode());
    return true;
  }

  /**
   * The raw failure, wrapped rather than clipped: this is the only place it can be read in full.
   */
  private void addError(MissionEntry entry) {
    addLine("ERROR", FormStyles.DANGER);
    root.addChild(UiKit.vSpacer(LINE_GAP));
    String message = entry.getLastError().orElse("computation failed, no detail recorded");
    for (int i = 0; i < message.length(); i += ERROR_WRAP_CHARS) {
      addLine(
          message.substring(i, Math.min(message.length(), i + ERROR_WRAP_CHARS)),
          FormStyles.TEXT_SECONDARY);
    }
  }

  private void addKeyedLine(String key, String value, ColorRGBA valueColor) {
    Container row = root.addChild(newRow());

    Label keyLabel = row.addChild(new Label(key, FormStyles.STYLE));
    keyLabel.setFont(UiKit.ibmPlexMono(11));
    keyLabel.setColor(FormStyles.TEXT_LO);
    keyLabel.setPreferredSize(new Vector3f(LABEL_COL, LINE_HEIGHT, 0));

    Label valueLabel = row.addChild(new Label(value, FormStyles.STYLE));
    valueLabel.setFont(UiKit.ibmPlexMono(11));
    valueLabel.setColor(valueColor);

    root.addChild(UiKit.vSpacer(LINE_GAP));
  }

  private void addLine(String text, ColorRGBA color) {
    Label label = root.addChild(new Label(text, FormStyles.STYLE));
    label.setFont(UiKit.ibmPlexMono(11));
    label.setColor(color);
    root.addChild(UiKit.vSpacer(LINE_GAP));
  }

  private static Container newRow() {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    return row;
  }

  private static ColorRGBA statusColor(MissionStatus status) {
    return switch (status) {
      case DRAFT -> FormStyles.TEXT_SECONDARY;
      case COMPUTING -> FormStyles.WARNING;
      case READY -> FormStyles.SUCCESS;
      case FAILED -> FormStyles.DANGER;
    };
  }
}
