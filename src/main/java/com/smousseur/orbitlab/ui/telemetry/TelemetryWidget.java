package com.smousseur.orbitlab.ui.telemetry;

import com.jme3.scene.Spatial;
import com.jme3.scene.Node;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.ui.AppStyles;
import java.util.Objects;
import org.orekit.propagation.SpacecraftState;

/**
 * A Lemur-based HUD widget displaying basic mission telemetry in real time.
 *
 * <p>Shows five parameters updated every frame from the active {@link Mission}:
 * mission elapsed time, current stage name, altitude, speed, and spacecraft mass.
 * The widget is positioned in the top-right corner of the screen and hidden when
 * no mission is ongoing.
 *
 * <p>Implements {@link AutoCloseable} to detach itself from the scene graph when no longer needed.
 */
public class TelemetryWidget implements AutoCloseable {

  private static final float MARGIN_PX = AppStyles.HUD_MARGIN_PX;

  private final Container root;
  private final Label metVal;
  private final Label phaseVal;
  private final Label altVal;
  private final Label velVal;
  private final Label massVal;

  /**
   * Creates and attaches the telemetry widget to the GUI scene graph.
   *
   * @param context the application context providing the GUI scene graph
   */
  public TelemetryWidget(ApplicationContext context) {
    Objects.requireNonNull(context, "context");
    Node telemetryNode = context.guiGraph().getTelemetryNode();

    this.root = new Container(new BoxLayout(Axis.Y, FillMode.None), TelemetryStyles.STYLE);
    telemetryNode.attachChild(root);

    root.addChild(new Label("— TELEMETRY —", TelemetryStyles.STYLE));

    this.metVal  = addRow("MET  ");
    this.phaseVal = addRow("Phase");
    this.altVal  = addRow("Alt  ");
    this.velVal  = addRow("Vit  ");
    this.massVal  = addRow("Masse");
  }

  private Label addRow(String key) {
    Container row = root.addChild(
        new Container(new BoxLayout(Axis.X, FillMode.None), TelemetryStyles.STYLE));
    row.addChild(new Label(key, TelemetryStyles.STYLE));
    Label val = row.addChild(new Label("—", TelemetryStyles.STYLE));
    return val;
  }

  /**
   * Refreshes all displayed values from the given mission's current state.
   *
   * @param mission the mission currently in progress
   */
  public void update(Mission mission) {
    SpacecraftState state = mission.getCurrentState();
    if (state == null) {
      clearValues();
      return;
    }

    double elapsedS = state.getDate().durationFrom(mission.getInitialDate());
    metVal.setText(formatMet(elapsedS));

    phaseVal.setText(mission.getCurrentStage().getName());

    double altKm = mission.computeAltitudeMeters(state) / 1000.0;
    altVal.setText(String.format("%.1f km", altKm));

    double velMs = state.getVelocity().getNorm();
    velVal.setText(String.format("%.0f m/s", velMs));

    massVal.setText(String.format("%.0f kg", state.getMass()));
  }

  /**
   * Shows or hides the widget.
   *
   * @param visible {@code true} to show, {@code false} to hide
   */
  public void setVisible(boolean visible) {
    root.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
  }

  /**
   * Positions the widget in the top-right corner of the screen.
   *
   * @param screenWidth  the screen width in pixels
   * @param screenHeight the screen height in pixels
   */
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

  private void clearValues() {
    metVal.setText("—");
    phaseVal.setText("—");
    altVal.setText("—");
    velVal.setText("—");
    massVal.setText("—");
  }

  private static String formatMet(double totalSeconds) {
    long s = (long) Math.abs(totalSeconds);
    return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
  }
}
