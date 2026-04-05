package com.smousseur.orbitlab.ui.telemetry;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.ui.AppStyles;
import java.util.Objects;
import org.orekit.time.AbsoluteDate;

/**
 * A Lemur-based HUD widget displaying basic mission telemetry. All values come from pre-computed
 * ephemeris — no Orekit calls at runtime.
 */
public class TelemetryWidget implements AutoCloseable {

  private static final float MARGIN_PX = AppStyles.HUD_MARGIN_PX;

  private final Container root;
  private final Label metVal;
  private final Label phaseVal;
  private final Label altVal;
  private final Label velVal;
  private final Label massVal;

  public TelemetryWidget(ApplicationContext context) {
    Objects.requireNonNull(context, "context");
    Node telemetryNode = context.guiGraph().getTelemetryNode();

    this.root = new Container(new BoxLayout(Axis.Y, FillMode.None), TelemetryStyles.STYLE);
    telemetryNode.attachChild(root);

    root.addChild(new Label("— TELEMETRY —", TelemetryStyles.STYLE));

    this.metVal = addRow("MET  ");
    this.phaseVal = addRow("Phase");
    this.altVal = addRow("Alt  ");
    this.velVal = addRow("Vit  ");
    this.massVal = addRow("Masse");
  }

  private Label addRow(String key) {
    Container row =
        root.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.None), TelemetryStyles.STYLE));
    row.addChild(new Label(key, TelemetryStyles.STYLE));
    Label val = row.addChild(new Label("—", TelemetryStyles.STYLE));
    return val;
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
      metVal.setText("Before launch");
      phaseVal.setText("—");
      altVal.setText("—");
      velVal.setText("—");
      massVal.setText("—");
      return;
    }

    if (now.compareTo(eph.endDate()) > 0) {
      MissionEphemerisPoint last = eph.lastPoint();
      updateFields(last, mission);
      phaseVal.setText("Mission complete");
      return;
    }

    MissionEphemerisPoint pt = eph.interpolate(now);
    updateFields(pt, mission);
  }

  private void updateFields(MissionEphemerisPoint pt, Mission mission) {
    double elapsedS = pt.time().durationFrom(mission.getInitialDate());
    metVal.setText(formatMet(elapsedS));
    phaseVal.setText(pt.stageName());
    altVal.setText(String.format("%.1f km", pt.altitudeMeters() / 1000.0));
    velVal.setText(String.format("%.0f m/s", pt.velocity().getNorm()));
    massVal.setText(String.format("%.0f kg", pt.mass()));
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
