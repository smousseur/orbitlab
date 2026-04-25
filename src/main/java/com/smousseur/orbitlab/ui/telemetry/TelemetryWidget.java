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
import com.simsilica.lemur.component.BorderLayout;
import com.simsilica.lemur.component.BoxLayout;
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

  private static final float WIDTH = 286f;
  private static final float PAD_X = 18f;
  private static final float PAD_Y = 16f;
  private static final float INNER_WIDTH = WIDTH - 2 * PAD_X;
  private static final float SECTION_GAP = 10f;
  private static final float LABEL_VALUE_GAP = 4f;
  private static final float DOT_ICON_SIZE = 8f;
  private static final float DOT_LABEL_GAP = 6f;

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

    this.root = new Container(new BoxLayout(Axis.Y, FillMode.None), TelemetryStyles.STYLE);
    this.root.setBackground(FormStyles.shellBg());
    this.root.setPreferredSize(new Vector3f(WIDTH, 0, 0));
    this.root.setInsetsComponent(new InsetsComponent(new Insets3f(PAD_Y, PAD_X, PAD_Y, PAD_X)));
    telemetryNode.attachChild(root);

    // Header: dot + TELEMETRY (left)  /  dot + phase (right)
    Container header = root.addChild(new Container(new BorderLayout(), TelemetryStyles.STYLE));
    header.setPreferredSize(new Vector3f(INNER_WIDTH, 16f, 0));
    header.addChild(buildDotLabel("TELEMETRY"), BorderLayout.Position.West);
    Container phaseGroup = buildDotLabel("—");
    this.phaseLabel = (Label) phaseGroup.getChild(2);
    header.addChild(phaseGroup, BorderLayout.Position.East);

    root.addChild(UiKit.vSpacer(SECTION_GAP));
    root.addChild(hDivider(INNER_WIDTH));
    root.addChild(UiKit.vSpacer(SECTION_GAP));

    // MET section
    root.addChild(smallLabel("MISSION ELAPSED TIME", INNER_WIDTH));
    root.addChild(UiKit.vSpacer(LABEL_VALUE_GAP));
    this.metValue = root.addChild(buildBigValueLabel("—"));
    this.metValue.setPreferredSize(new Vector3f(INNER_WIDTH, 26f, 0));

    root.addChild(UiKit.vSpacer(SECTION_GAP));
    root.addChild(hDivider(INNER_WIDTH));
    root.addChild(UiKit.vSpacer(SECTION_GAP));

    // ALT | VEL row
    float halfWidth = (INNER_WIDTH - 1f) / 2f;
    Container altVelRow = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None),
        TelemetryStyles.STYLE));
    altVelRow.setPreferredSize(new Vector3f(INNER_WIDTH, 44f, 0));

    Container altCell = altVelRow.addChild(buildValueCell("ALT", halfWidth));
    this.altValue = (Label) ((Container) altCell.getChild(2)).getChild(0);
    this.altUnit = (Label) ((Container) altCell.getChild(2)).getChild(2);

    altVelRow.addChild(vDivider(44f));

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

  private Container buildDotLabel(String text) {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None), TelemetryStyles.STYLE);
    row.addChild(UiKit.wizardIcon("step-dot-active", DOT_ICON_SIZE, DOT_ICON_SIZE));
    row.addChild(UiKit.hSpacer(DOT_LABEL_GAP));
    Label label = row.addChild(new Label(text, TelemetryStyles.STYLE));
    label.setFont(UiKit.ibmPlexMono(11));
    label.setColor(FormStyles.TEXT_PRIMARY);
    return row;
  }

  private Label smallLabel(String text, float width) {
    Label l = new Label(text, TelemetryStyles.STYLE);
    l.setFont(UiKit.ibmPlexMono(10));
    l.setColor(FormStyles.TEXT_LO);
    l.setPreferredSize(new Vector3f(width, 12f, 0));
    l.setTextHAlignment(HAlignment.Left);
    return l;
  }

  private Label buildBigValueLabel(String text) {
    Label l = new Label(text, TelemetryStyles.STYLE);
    l.setFont(UiKit.orbitron(20));
    l.setColor(FormStyles.CYAN);
    l.setTextHAlignment(HAlignment.Left);
    return l;
  }

  private Container buildValueCell(String labelText, float width) {
    Container cell = new Container(new BoxLayout(Axis.Y, FillMode.None), TelemetryStyles.STYLE);
    cell.setPreferredSize(new Vector3f(width, 44f, 0));
    cell.setInsetsComponent(new InsetsComponent(new Insets3f(0, 4, 0, 4)));
    cell.addChild(smallLabel(labelText, width - 8f));
    cell.addChild(UiKit.vSpacer(LABEL_VALUE_GAP));
    cell.addChild(buildValueUnitRow(width - 8f));
    return cell;
  }

  private Container buildValueUnitRow(float width) {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None), TelemetryStyles.STYLE);
    row.setPreferredSize(new Vector3f(width, 22f, 0));
    Label value = row.addChild(new Label("—", TelemetryStyles.STYLE));
    value.setFont(UiKit.orbitron(18));
    value.setColor(FormStyles.CYAN);
    value.setTextHAlignment(HAlignment.Left);
    row.addChild(UiKit.hSpacer(4f));
    Label unit = row.addChild(new Label("", TelemetryStyles.STYLE));
    unit.setFont(UiKit.ibmPlexMono(10));
    unit.setColor(FormStyles.TEXT_LO);
    return row;
  }

  private Container hDivider(float width) {
    Container d = new Container();
    d.setPreferredSize(new Vector3f(width, 1f, 0));
    d.setBackground(new QuadBackgroundComponent(FormStyles.BORDER));
    return d;
  }

  private Container vDivider(float height) {
    Container d = new Container();
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
    setValueUnit(altValue, altUnit,
        String.format("%.1f", pt.altitudeMeters() / 1000.0), "km");
    setValueUnit(velValue, velUnit,
        String.format("%.0f", pt.velocity().getNorm()), "m/s");
    setValueUnit(massValue, massUnit,
        String.format("%.0f", pt.mass()), "kg");
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
