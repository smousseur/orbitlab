package com.smousseur.orbitlab.ui.mission;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.simulation.mission.MissionContext;
import com.smousseur.orbitlab.simulation.mission.MissionEntry;
import com.smousseur.orbitlab.ui.AppStyles;
import java.util.List;
import java.util.Objects;

/**
 * A Lemur-based GUI widget that provides a button to toggle the display of all missions
 * registered in the {@link MissionContext}.
 *
 * <p>The widget consists of a "Missions" button positioned at the top-left of the screen.
 * Clicking it reveals or hides a panel listing each mission's name and status.
 *
 * <p>Implements {@link AutoCloseable} to detach itself from the scene graph when no longer needed.
 */
public class MissionPanelWidget implements AutoCloseable {

  private static final float MARGIN_PX = AppStyles.HUD_MARGIN_PX;

  private final MissionContext missionContext;

  private final Container root;
  private final Button toggleButton;
  private final Container listPanel;
  private boolean panelVisible = false;

  /**
   * Creates and attaches the mission panel widget to the GUI scene graph.
   *
   * @param context the application context providing the mission context and GUI scene graph
   */
  public MissionPanelWidget(ApplicationContext context) {
    Objects.requireNonNull(context, "context");
    this.missionContext = context.missionContext();

    Node missionPanelNode = context.guiGraph().getMissionPanelNode();

    this.root = new Container(new BoxLayout(Axis.Y, FillMode.None), MissionPanelStyles.STYLE);
    missionPanelNode.attachChild(root);

    this.toggleButton = root.addChild(new Button("Missions"));
    toggleButton.addClickCommands(source -> togglePanel());

    this.listPanel = root.addChild(
        new Container(new BoxLayout(Axis.Y, FillMode.None), MissionPanelStyles.STYLE));
    listPanel.setCullHint(Spatial.CullHint.Always);
  }

  /**
   * Updates the mission list content if the panel is visible.
   * Called once per frame from the managing AppState.
   *
   * @param tpf time per frame in seconds
   */
  public void update(float tpf) {
    if (panelVisible) {
      refreshMissionList();
    }
  }

  /**
   * Positions the widget at the top-left corner of the screen.
   *
   * @param screenWidth  the screen width in pixels
   * @param screenHeight the screen height in pixels
   */
  public void layoutTopLeft(int screenWidth, int screenHeight) {
    float x = MARGIN_PX;
    float y = screenHeight - MARGIN_PX;
    root.setLocalTranslation(x, y, 0f);
  }

  @Override
  public void close() {
    root.removeFromParent();
  }

  private void togglePanel() {
    panelVisible = !panelVisible;
    if (panelVisible) {
      listPanel.setCullHint(Spatial.CullHint.Inherit);
      refreshMissionList();
    } else {
      listPanel.setCullHint(Spatial.CullHint.Always);
    }
  }

  private void refreshMissionList() {
    listPanel.clearChildren();
    List<MissionEntry> missions = missionContext.getMissions();
    if (missions.isEmpty()) {
      listPanel.addChild(new Label("No missions", MissionPanelStyles.STYLE));
    } else {
      for (MissionEntry entry : missions) {
        Container row = listPanel.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.None), MissionPanelStyles.STYLE));
        row.addChild(new Label(entry.mission().getName(), MissionPanelStyles.STYLE));
        row.addChild(
            new Label("  [" + entry.mission().getStatus() + "]", MissionPanelStyles.STYLE));
      }
    }
  }
}
