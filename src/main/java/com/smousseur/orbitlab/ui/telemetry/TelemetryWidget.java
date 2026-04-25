package com.smousseur.orbitlab.ui.telemetry;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.BorderLayout;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.IconComponent;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import java.util.Objects;
import org.orekit.time.AbsoluteDate;

/**
 * A Lemur-based HUD widget displaying basic mission telemetry. All values come from pre-computed
 * ephemeris — no Orekit calls at runtime.
 */
public class TelemetryWidget implements AutoCloseable {

  private static final float MARGIN_PX = AppStyles.HUD_MARGIN_PX;

  private static final float WIDTH = 268f;
  private static final float HEIGHT = 215f;
  private static final float PAD_X = 14f;
  private static final float PAD_Y = 12f;
  private static final float INNER_WIDTH = WIDTH - 2 * PAD_X;
  private static final float SECTION_GAP = 8f;
  private static final float LABEL_VALUE_GAP = 2f;

  /** step-dot-active.png is 32x32; scale to ~6.4 px so the cyan dot stays a clean small circle. */
  private static final float DOT_ICON_SCALE = 0.4f;

  private static final float DOT_LABEL_GAP = 5f;

  private final Container root;
  private final Label phaseLabel;
  private final Label metValue;
  private final Label altValue;
  private final Label altUnit;
  private final Label velValue;
  private final Label velUnit;
  private final Label massValue;
  private final Label massUnit;

  public TelemetryWidget(ApplicationContext context) {
    Objects.requireNonNull(context, "context");
    Node telemetryNode = context.guiGraph().getTelemetryNode();

    this.root = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    this.root.setBackground(FormStyles.shellBg());
    this.root.setPreferredSize(new Vector3f(WIDTH, HEIGHT, 0));
    this.root.setInsetsComponent(new InsetsComponent(new Insets3f(PAD_Y, PAD_X, PAD_Y, PAD_X)));
    telemetryNode.attachChild(root);

    // Header: dot + TELEMETRY (left)  /  dot + phase (right)
    Container header = root.addChild(new Container(new BorderLayout(), FormStyles.STYLE));
    header.setPreferredSize(new Vector3f(INNER_WIDTH, 14f, 0));
    header.addChild(buildDotLabel("TELEMETRY"), BorderLayout.Position.West);
    this.phaseLabel = buildDotLabel("—");
    header.addChild(phaseLabel, BorderLayout.Position.East);

    root.addChild(UiKit.vSpacer(SECTION_GAP));
    root.addChild(hDivider(INNER_WIDTH));
    root.addChild(UiKit.vSpacer(SECTION_GAP));

    // MET section
    root.addChild(smallLabel("MISSION ELAPSED TIME", INNER_WIDTH));
    root.addChild(UiKit.vSpacer(LABEL_VALUE_GAP));
    this.metValue = root.addChild(buildMetLabel("—"));
    this.metValue.setPreferredSize(new Vector3f(INNER_WIDTH, 18f, 0));

    root.addChild(UiKit.vSpacer(SECTION_GAP));
    root.addChild(hDivider(INNER_WIDTH));
    root.addChild(UiKit.vSpacer(SECTION_GAP));

    // ALT | VEL row
    float halfWidth = (INNER_WIDTH - 1f) / 2f;
    Container altVelRow =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE));
    altVelRow.setPreferredSize(new Vector3f(INNER_WIDTH, 36f, 0));

    Container altCell = altVelRow.addChild(buildValueCell("ALT", halfWidth));
    this.altValue = (Label) ((Container) altCell.getChild(2)).getChild(0);
    this.altUnit = (Label) ((Container) altCell.getChild(2)).getChild(2);

    altVelRow.addChild(vDivider(36f));

    Container velCell = altVelRow.addChild(buildValueCell("VEL", halfWidth));
    this.velValue = (Label) ((Container) velCell.getChild(2)).getChild(0);
    this.velUnit = (Label) ((Container) velCell.getChild(2)).getChild(2);

    root.addChild(UiKit.vSpacer(SECTION_GAP));
    root.addChild(hDivider(INNER_WIDTH));
    root.addChild(UiKit.vSpacer(SECTION_GAP));

    // MASS section
    root.addChild(smallLabel("MASS", INNER_WIDTH));
    root.addChild(UiKit.vSpacer(LABEL_VALUE_GAP));
    Container massRow = root.addChild(buildValueUnitRow(INNER_WIDTH));
    this.massValue = (Label) massRow.getChild(0);
    this.massUnit = (Label) massRow.getChild(2);
  }

  private Label buildDotLabel(String text) {
    Label label = new Label(text, FormStyles.STYLE);
    label.setFont(UiKit.mono(10));
    label.setColor(FormStyles.TEXT_PRIMARY);
    IconComponent dot = UiKit.wizardIconComponent("step-dot-active");
    dot.setIconScale(DOT_ICON_SCALE);
    dot.setMargin(DOT_LABEL_GAP, 0f);
    dot.setHAlignment(HAlignment.Left);
    dot.setVAlignment(VAlignment.Center);
    label.setIcon(dot);
    return label;
  }

  private Label smallLabel(String text, float width) {
    Label l = new Label(text, FormStyles.STYLE);
    l.setFont(UiKit.mono(10));
    l.setColor(FormStyles.TEXT_LO);
    l.setPreferredSize(new Vector3f(width, 10f, 0));
    l.setTextHAlignment(HAlignment.Left);
    return l;
  }

  private Label buildMetLabel(String text) {
    Label l = new Label(text, FormStyles.STYLE);
    l.setFont(UiKit.mono(14));
    l.setColor(FormStyles.CYAN);
    l.setTextHAlignment(HAlignment.Left);
    return l;
  }

  private Container buildValueCell(String labelText, float width) {
    Container cell = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    cell.setPreferredSize(new Vector3f(width, 36f, 0));
    cell.setInsetsComponent(new InsetsComponent(new Insets3f(0, 4, 0, 4)));
    cell.addChild(smallLabel(labelText, width - 8f));
    cell.addChild(UiKit.vSpacer(LABEL_VALUE_GAP));
    cell.addChild(buildValueUnitRow(width - 8f));
    return cell;
  }

  private Container buildValueUnitRow(float width) {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    row.setPreferredSize(new Vector3f(width, 18f, 0));
    Label value = row.addChild(new Label("—", FormStyles.STYLE));
    value.setFont(UiKit.mono(14));
    value.setColor(FormStyles.CYAN);
    value.setTextHAlignment(HAlignment.Left);
    row.addChild(UiKit.hSpacer(4f));
    Label unit = row.addChild(new Label("", FormStyles.STYLE));
    unit.setFont(UiKit.mono(10));
    unit.setColor(FormStyles.TEXT_LO);
    return row;
  }

  private Container hDivider(float width) {
    Container d = new Container(FormStyles.STYLE);
    d.setPreferredSize(new Vector3f(width, 1f, 0));
    d.setBackground(new QuadBackgroundComponent(FormStyles.BORDER));
    return d;
  }

  private Container vDivider(float height) {
    Container d = new Container(FormStyles.STYLE);
    d.setPreferredSize(new Vector3f(1f, height, 0));
    d.setBackground(new QuadBackgroundComponent(FormStyles.BORDER));
    return d;
  }

  /**
   * Updates telemetry display from ephemeris data. All values come from pre-computed ephemeris.
   *
   * @param eph the mission ephemeris
   * @param now the current simulation time
   * @param mission the mission (for initial date / MET computation)
   */
  public void updateFromEphemeris(MissionEphemeris eph, AbsoluteDate now, Mission mission) {
    if (now.compareTo(eph.startDate()) < 0) {
      metValue.setText("--:--:--");
      phaseLabel.setText("BEFORE LAUNCH");
      setValueUnit(altValue, altUnit, "—", "");
      setValueUnit(velValue, velUnit, "—", "");
      setValueUnit(massValue, massUnit, "—", "");
      return;
    }

    if (now.compareTo(eph.endDate()) > 0) {
      MissionEphemerisPoint last = eph.lastPoint();
      updateFields(last, mission);
      phaseLabel.setText("COMPLETE");
      return;
    }

    MissionEphemerisPoint pt = eph.interpolate(now);
    updateFields(pt, mission);
  }

  private void updateFields(MissionEphemerisPoint pt, Mission mission) {
    double elapsedS = pt.time().durationFrom(mission.getInitialDate());
    metValue.setText(formatMet(elapsedS));
    phaseLabel.setText(pt.stageName().toUpperCase());
    setValueUnit(altValue, altUnit, String.format("%.1f", pt.altitudeMeters() / 1000.0), "km");
    setValueUnit(velValue, velUnit, String.format("%.0f", pt.velocity().getNorm()), "m/s");
    setValueUnit(massValue, massUnit, String.format("%.0f", pt.mass()), "kg");
  }

  private static void setValueUnit(Label value, Label unit, String v, String u) {
    value.setText(v);
    unit.setText(u);
  }

  public void setVisible(boolean visible) {
    root.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
  }

  public void layoutTopRight(int screenWidth, int screenHeight) {
    var size = root.getPreferredSize();
    float x = screenWidth - size.x - MARGIN_PX;
    float y = screenHeight - MARGIN_PX;
    root.setLocalTranslation(x, y, 0f);
  }

  @Override
  public void close() {
    root.removeFromParent();
  }

  private static String formatMet(double totalSeconds) {
    long s = (long) Math.abs(totalSeconds);
    return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
  }
}
