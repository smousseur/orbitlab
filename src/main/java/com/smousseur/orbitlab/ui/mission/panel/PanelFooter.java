package com.smousseur.orbitlab.ui.mission.panel;

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
import com.smousseur.orbitlab.app.OrekitTime;
import com.smousseur.orbitlab.simulation.OrbitElements;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.runtime.AchievedOrbit;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.MissionResultText;
import com.smousseur.orbitlab.ui.mission.MissionTargetOrbit;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

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

  static final float HEIGHT = 100f;

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

  /**
   * Characters the result line may occupy before the {@code DETAILS} button. IBM Plex Mono at 11 px
   * measures ~6.6 px per glyph over the 656 px of inner width, less the button's own box.
   */
  private static final int RESULT_MAX_CHARS = 72;

  private static final float DETAILS_BTN_W = 110f;
  private static final float DETAILS_BTN_H = 20f;

  private static final String SEPARATOR = "|";
  private static final String UNKNOWN = "-";
  private static final String UNSCHEDULED = "unscheduled";
  private static final String NO_RESULT = "No result yet - run Compute";
  private static final String COMPUTING_TEXT = "Computing...";
  private static final String DETAILS_LABEL = "DETAILS >";

  private final Container root;
  private final Container summary;
  private Consumer<MissionEntry> onShowDetails = entry -> {};

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
   * Sets the action run when the user opens the detail view from this footer.
   *
   * @param action the action, receiving the selected entry
   */
  public void setOnShowDetails(Consumer<MissionEntry> action) {
    this.onShowDetails = action != null ? action : entry -> {};
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
    summary.addChild(UiKit.vSpacer(LINE_GAP));
    addResultLine(entry);
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

  /**
   * The one line of result the footer can hold: what the mission achieved, or why it failed. The
   * osculating convention is the one shown here - it is the launcher accuracy convention, the one
   * that demonstrates targeting quality. The mean orbit differs from it by kilometres through J2
   * short-period terms alone, so a single line showing the mean orbit would make a perfect
   * insertion look like a miss; both conventions live side by side in the detail view instead.
   */
  private void addResultLine(MissionEntry entry) {
    Container row = summary.addChild(newRow());

    Label text = row.addChild(new Label(resultText(entry), FormStyles.STYLE));
    text.setFont(UiKit.ibmPlexMono(11));
    text.setColor(
        entry.mission().getStatus() == MissionStatus.FAILED
            ? FormStyles.DANGER
            : FormStyles.TEXT_SECONDARY);
    centerOnButtonHeight(text, DETAILS_BTN_H);

    if (hasDetailsToShow(entry)) {
      row.addChild(UiKit.hSpacer(FIELD_GAP + 50));
      row.addChild(detailsButton(entry));
    }
  }

  /**
   * Grows a label's box to a button's height and centres its text in it, so the two sit on the same
   * baseline. {@code BoxLayout} on the X axis aligns the top edges of its children, and a button is
   * taller than a line of text, so without this its caption is drawn a few pixels low. Same idiom
   * as {@code MissionRow}, which centres its cells over the row height.
   *
   * @param label the label to grow, already carrying its final font
   * @param buttonHeight the height of the button it sits beside
   */
  private static void centerOnButtonHeight(Label label, float buttonHeight) {
    // Read after the font is set: the preferred width is measured from the glyphs.
    label.setPreferredSize(new Vector3f(label.getPreferredSize().x, buttonHeight, 0));
    label.setTextVAlignment(VAlignment.Center);
  }

  private String resultText(MissionEntry entry) {
    if (entry.mission().getStatus() == MissionStatus.FAILED) {
      return MissionResultText.truncate(
          "ERROR  " + entry.getLastError().orElse("computation failed"), RESULT_MAX_CHARS);
    }
    if (entry.mission().getStatus() == MissionStatus.COMPUTING) {
      return COMPUTING_TEXT;
    }
    return achievedSummary(entry).orElse(NO_RESULT);
  }

  /** {@code "ORBIT 400000 x 400114 m i=51.60 deg MISS +0 / +114 m +0.0012 deg"}. */
  private static Optional<String> achievedSummary(MissionEntry entry) {
    Optional<AchievedOrbit> achieved = entry.getAchievedOrbit();
    if (achieved.isEmpty() || !achieved.get().hasOsculating()) {
      return Optional.empty();
    }
    OrbitElements osculating = achieved.get().osculating();
    StringBuilder line =
        new StringBuilder("ORBIT ")
            .append(MissionResultText.formatAltitudes(osculating))
            .append(' ')
            .append(MissionResultText.formatInclination(osculating.inclination()));
    MissionTargetOrbit.forEntry(entry)
        .ifPresent(
            target -> line.append("   ").append(MissionResultText.formatMiss(osculating, target)));
    return Optional.of(MissionResultText.truncate(line.toString(), RESULT_MAX_CHARS));
  }

  /** Whether the detail view would have anything to show beyond what this line already says. */
  private static boolean hasDetailsToShow(MissionEntry entry) {
    return entry.getAchievedOrbit().isPresent()
        || entry.getPerformanceReport().isPresent()
        || entry.getLastError().isPresent();
  }

  /**
   * Text button rather than an icon: row actions are backed by {@code icon-action-*.png} textures
   * and none exists for this gesture. Swap it for an icon the day one is produced.
   */
  private Button detailsButton(MissionEntry entry) {
    Button btn = new Button(DETAILS_LABEL, FormStyles.STYLE);
    btn.setFont(UiKit.ibmPlexMono(11));
    btn.setColor(FormStyles.ACCENT_BRIGHT);
    btn.setBackground(null);
    btn.setInsetsComponent(new InsetsComponent(new Insets3f(0, 0, 0, 0)));
    btn.setTextVAlignment(VAlignment.Center);
    btn.setPreferredSize(new Vector3f(DETAILS_BTN_W, DETAILS_BTN_H, 0));
    btn.addClickCommands(src -> onShowDetails.accept(entry));
    return btn;
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
