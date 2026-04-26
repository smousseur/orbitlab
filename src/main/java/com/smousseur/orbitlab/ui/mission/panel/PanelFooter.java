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
import com.smousseur.orbitlab.simulation.mission.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;

public class PanelFooter {

  private static final float HEIGHT = 78f;
  private static final float PAD_X = 32f;
  private static final float PAD_Y = 16f;

  // Dummy value shown in the selection details footer until real metadata is plumbed.
  private static final String DUMMY_ALTITUDE = "380 km";

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

  public void setSelectedMission(MissionEntry entry) {
    summary.clearChildren();

    if (entry == null) {
      Label hint = summary.addChild(new Label("Select a mission to see details", FormStyles.STYLE));
      hint.setFont(UiKit.ibmPlexMono(11));
      hint.setColor(FormStyles.TEXT_LO);
      return;
    }

    Container row = summary.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    row.setBackground(null);

    Label name = row.addChild(new Label(entry.mission().getName(), FormStyles.STYLE));
    name.setFont(UiKit.orbitron(13));
    name.setColor(FormStyles.TEXT_PRIMARY);

    row.addChild(UiKit.hSpacer(12));

    Label status =
        row.addChild(new Label("[ " + entry.mission().getStatus().name() + " ]", FormStyles.STYLE));
    status.setFont(UiKit.ibmPlexMono(11));
    status.setColor(statusColor(entry.mission().getStatus()));

    summary.addChild(UiKit.vSpacer(6));

    String vehicleName =
        entry.mission().getVehicle() != null
            ? entry.mission().getVehicle().getClass().getSimpleName()
            : "—";
    String schedule = entry.getScheduledDate().map(Object::toString).orElse("unscheduled");

    addDetailLine(
        "type: "
            + MissionTypes.label(entry)
            + "   •   vehicle: "
            + vehicleName
            + "   •   alt: "
            + DUMMY_ALTITUDE
            + "   •   launch: "
            + schedule);
  }

  private void addDetailLine(String text) {
    Label line = summary.addChild(new Label(text, FormStyles.STYLE));
    line.setFont(UiKit.ibmPlexMono(11));
    line.setColor(FormStyles.TEXT_SECONDARY);
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
