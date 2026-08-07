package com.smousseur.orbitlab.ui.mission.panel;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.smousseur.orbitlab.app.OrekitTime;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import java.util.Locale;

/**
 * Details strip at the bottom of the mission panel, showing the selected mission on two lines:
 * identity (name, status, current optimization mode) then attributes (type, scheduled launch date,
 * launch site). Rebuilt from scratch on every selection or roster change — the panel owns the
 * refresh cadence, this view holds no state beyond its containers.
 *
 * <p>All text here is plain ASCII on purpose: the bundled bitmap fonts only carry glyphs 32-127, so
 * typographic separators ({@code bullet}, {@code em dash}) and the degree sign would render as
 * missing glyphs.
 */
public class PanelFooter {

  static final float HEIGHT = 78f;

  private static final float PAD_X = 32f;
  private static final float PAD_Y = 16f;

  /** Vertical gap between the identity line and the attribute line. */
  private static final float LINE_GAP = 8f;

  /** Horizontal gap between the identity line's items. */
  private static final float ITEM_GAP = 12f;

  /** Horizontal gap on each side of an attribute separator. */
  private static final float FIELD_GAP = 10f;

  /** Horizontal gap between an attribute key and its value. */
  private static final float KEY_VALUE_GAP = 6f;

  private static final String SEPARATOR = "|";
  private static final String UNKNOWN = "-";
  private static final String UNSCHEDULED = "unscheduled";

  private final Container root;
  private final Container summary;

  public PanelFooter(float width) {
    float innerWidth = width - 2 * PAD_X;

    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(FormStyles.footerBg());
    root.setPreferredSize(new Vector3f(width, HEIGHT, 0));
    root.setInsetsComponent(new InsetsComponent(new Insets3f(PAD_Y, PAD_X, PAD_Y, PAD_X)));

    summary = root.addChild(new Container(new BoxLayout(Axis.Y, FillMode.None)));
    summary.setBackground(null);
    summary.setPreferredSize(new Vector3f(innerWidth, HEIGHT - 2 * PAD_Y, 0));
  }

  public Container getNode() {
    return root;
  }

  /**
   * Renders the details for the selected mission, or the idle hint when nothing is selected.
   *
   * @param entry the selected mission entry, or {@code null} when the selection was cleared
   */
  public void setSelectedMission(MissionEntry entry) {
    summary.clearChildren();

    if (entry == null) {
      Label hint = summary.addChild(new Label("Select a mission to see details", FormStyles.STYLE));
      hint.setFont(UiKit.ibmPlexMono(11));
      hint.setColor(FormStyles.TEXT_LO);
      return;
    }

    addIdentityLine(entry);
    summary.addChild(UiKit.vSpacer(LINE_GAP));
    addAttributeLine(entry);
  }

  /** Name, status and the optimization mode the mission is currently composed for. */
  private void addIdentityLine(MissionEntry entry) {
    Container row = summary.addChild(newRow());

    Label name = row.addChild(new Label(entry.mission().getName(), FormStyles.STYLE));
    name.setFont(UiKit.orbitron(13));
    name.setColor(FormStyles.TEXT_PRIMARY);

    row.addChild(UiKit.hSpacer(ITEM_GAP));

    MissionStatus status = entry.mission().getStatus();
    Label statusLabel = row.addChild(new Label("[ " + status.name() + " ]", FormStyles.STYLE));
    statusLabel.setInsetsComponent(new InsetsComponent(new Insets3f(2, 8, 2, 8)));
    statusLabel.setFont(UiKit.ibmPlexMono(11));
    statusLabel.setColor(statusColor(status));

    row.addChild(UiKit.hSpacer(ITEM_GAP));
    row.addChild(modeChip(entry.getOptimizationType()));
  }

  /** Mission type, planned launch date and launch site, as dim-key / bright-value pairs. */
  private void addAttributeLine(MissionEntry entry) {
    Container row = summary.addChild(newRow());

    addField(row, "TYPE", MissionTypes.label(entry), true);
    addField(row, "LAUNCH", scheduleLabel(entry), false);
    addField(row, "SITE", siteLabel(entry), false);
  }

  private void addField(Container row, String key, String value, boolean first) {
    if (!first) {
      row.addChild(UiKit.hSpacer(FIELD_GAP));
      Label separator = row.addChild(new Label(SEPARATOR, FormStyles.STYLE));
      separator.setFont(UiKit.ibmPlexMono(11));
      separator.setColor(FormStyles.BORDER);
      row.addChild(UiKit.hSpacer(FIELD_GAP));
    }

    Label keyLabel = row.addChild(new Label(key, FormStyles.STYLE));
    keyLabel.setFont(UiKit.ibmPlexMono(11));
    keyLabel.setColor(FormStyles.TEXT_LO);

    row.addChild(UiKit.hSpacer(KEY_VALUE_GAP));

    Label valueLabel = row.addChild(new Label(value, FormStyles.STYLE));
    valueLabel.setFont(UiKit.ibmPlexMono(11));
    valueLabel.setColor(FormStyles.TEXT_SECONDARY);
  }

  /**
   * Small pill echoing the row's segmented control in text form, so the mode the mission would be
   * recomputed in is readable without decoding the three icons.
   */
  private static Container modeChip(OptimizationType type) {
    Container chip = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    chip.setInsetsComponent(new InsetsComponent(new Insets3f(2, 8, 2, 8)));

    Label label = chip.addChild(new Label(type.name(), FormStyles.STYLE));
    label.setFont(UiKit.ibmPlexMono(11));
    label.setColor(FormStyles.ACCENT_BRIGHT);
    return chip;
  }

  private static String scheduleLabel(MissionEntry entry) {
    return entry.getScheduledDate().map(OrekitTime::formatDate).orElse(UNSCHEDULED);
  }

  /**
   * The cosmodrome name picked in the wizard, falling back to the site's coordinates for a spec
   * built outside it. Legacy entries wrapping a pre-built mission carry no {@link MissionSpec} at
   * all and have nothing to show.
   */
  private static String siteLabel(MissionEntry entry) {
    return entry
        .spec()
        .map(
            spec ->
                spec.hasSiteName()
                    ? spec.siteName()
                    : formatCoordinates(spec.latitude(), spec.longitude()))
        .orElse(UNKNOWN);
  }

  private static String formatCoordinates(double latitude, double longitude) {
    return String.format(
        Locale.ROOT,
        "%.2f%s %.2f%s",
        Math.abs(latitude),
        latitude >= 0 ? "N" : "S",
        Math.abs(longitude),
        longitude >= 0 ? "E" : "W");
  }

  private static Container newRow() {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    return row;
  }

  static ColorRGBA statusColor(MissionStatus status) {
    return switch (status) {
      case DRAFT -> FormStyles.TEXT_SECONDARY;
      case COMPUTING -> FormStyles.WARNING;
      case READY -> FormStyles.SUCCESS;
      case FAILED -> FormStyles.DANGER;
    };
  }
}
