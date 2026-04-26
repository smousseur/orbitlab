package com.smousseur.orbitlab.ui.mission.panel;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.core.GuiComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.MissionContext;
import com.smousseur.orbitlab.simulation.mission.MissionEntry;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.form.ModalBackdrop;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Modal mission panel: orchestrates a {@link PanelHeader}, a {@link MissionListView} and a {@link
 * PanelFooter} attached to the shared modal layer through a {@link ModalBackdrop}. Per-row actions
 * (Edit / Compute / Visualize / Delete) are routed to {@link MissionContext} and the {@link
 * EventBus}.
 */
public class MissionPanelWidget implements AutoCloseable {
  private static final Logger logger = LogManager.getLogger(MissionPanelWidget.class);

  private static final float WINDOW_WIDTH = 720f;
  private static final float WINDOW_HEIGHT = 520f;
  private static final float HEADER_HEIGHT = 88f;
  private static final float FOOTER_HEIGHT = 78f;
  private static final float CONTENT_HEIGHT = WINDOW_HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT;

  private final MissionContext missionContext;
  private final EventBus eventBus;

  private final ModalBackdrop backdrop;
  private final Container root;
  private final PanelHeader header;
  private final MissionListView listView;
  private final PanelFooter footer;

  private String selectedMissionName;
  private List<String> lastSnapshot = List.of();
  private boolean visible = false;

  private Runnable onClose = () -> {};
  private Runnable onNewMission = () -> {};

  public MissionPanelWidget(ApplicationContext context) {
    Objects.requireNonNull(context, "context");
    this.missionContext = context.missionContext();
    this.eventBus = context.eventBus();
    this.selectedMissionName = missionContext.getSelectedMissionName();

    backdrop = new ModalBackdrop();
    backdrop.setOnClick(() -> onClose.run());

    root = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(WINDOW_WIDTH, WINDOW_HEIGHT, 0));
    root.setBackground(FormStyles.shellBg());
    root.getInsetsComponent().setInsets(new Insets3f(0, 0, 0, 0));
    root.setBorder(null);
    clearMargin(root.getBackground());

    header = new PanelHeader(WINDOW_WIDTH);
    header.setOnClose(() -> onClose.run());

    listView = new MissionListView(WINDOW_WIDTH, CONTENT_HEIGHT);
    listView.setOnNewMission(() -> onNewMission.run());
    listView.setRowListener(buildRowListener());

    footer = new PanelFooter(WINDOW_WIDTH);

    root.addChild(header.getNode());
    root.addChild(listView.getNode());
    root.addChild(footer.getNode());

    // Mouse on the modal shell must not leak through to the backdrop.
    MouseEventControl.addListenersToSpatial(
        root,
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            event.setConsumed();
          }
        });

    refresh();
  }

  public void attachTo(Node modalNode) {
    modalNode.attachChild(backdrop.getNode());
    modalNode.attachChild(root);
    visible = true;
  }

  @Override
  public void close() {
    backdrop.getNode().removeFromParent();
    root.removeFromParent();
    visible = false;
  }

  public boolean isVisible() {
    return visible;
  }

  public void update(float tpf, Camera cam) {
    if (!visible) return;
    backdrop.update(cam);
    centerOnScreen(cam.getWidth(), cam.getHeight());

    List<String> snapshot = buildSnapshot();
    if (selectedMissionName != null && missionContext.findMission(selectedMissionName).isEmpty()) {
      selectedMissionName = null;
      missionContext.setSelectedMissionName(null);
    }
    if (!snapshot.equals(lastSnapshot)) {
      lastSnapshot = snapshot;
      refresh();
    }
  }

  public void setOnClose(Runnable action) {
    this.onClose = action != null ? action : () -> {};
  }

  public void setOnNewMission(Runnable action) {
    this.onNewMission = action != null ? action : () -> {};
  }

  private MissionListView.RowListener buildRowListener() {
    return new MissionListView.RowListener() {
      @Override
      public void onSelect(String missionName) {
        selectMission(missionName);
      }

      @Override
      public void onEdit(String missionName) {
        logger.info("Edit not yet implemented for mission '{}'", missionName);
      }

      @Override
      public void onCompute(String missionName) {
        eventBus.publishMissionAction(missionName, EventBus.MissionAction.OPTIMIZE);
      }

      @Override
      public void onToggleVisible(String missionName) {
        eventBus.publishMissionAction(missionName, EventBus.MissionAction.TOGGLE_VISIBLE);
      }

      @Override
      public void onDelete(String missionName) {
        if (missionName.equals(selectedMissionName)) {
          selectedMissionName = null;
          missionContext.setSelectedMissionName(null);
        }
        eventBus.publishMissionAction(missionName, EventBus.MissionAction.DELETE);
      }
    };
  }

  private void selectMission(String name) {
    if (name.equals(selectedMissionName)) {
      selectedMissionName = null;
    } else {
      selectedMissionName = name;
    }
    missionContext.setSelectedMissionName(selectedMissionName);
    refresh();
  }

  private void refresh() {
    List<MissionEntry> entries = missionContext.getMissions();
    listView.refresh(entries, selectedMissionName);
    MissionEntry selected =
        selectedMissionName == null
            ? null
            : missionContext.findMission(selectedMissionName).orElse(null);
    footer.setSelectedMission(selected);
  }

  private List<String> buildSnapshot() {
    List<MissionEntry> entries = missionContext.getMissions();
    List<String> snapshot = new ArrayList<>(entries.size() + 1);
    snapshot.add("sel=" + (selectedMissionName == null ? "" : selectedMissionName));
    for (MissionEntry entry : entries) {
      snapshot.add(
          entry.mission().getName() + ":" + entry.mission().getStatus() + ":" + entry.isVisible());
    }
    return snapshot;
  }

  private void centerOnScreen(int screenWidth, int screenHeight) {
    float x = Math.round((screenWidth - WINDOW_WIDTH) / 2f);
    float y = Math.round((screenHeight + WINDOW_HEIGHT) / 2f);
    root.setLocalTranslation(x, y, 101f);
  }

  private static void clearMargin(GuiComponent bg) {
    if (bg instanceof TbtQuadBackgroundComponent quad) {
      quad.setMargin(0f, 0f);
    } else if (bg instanceof QuadBackgroundComponent quad) {
      quad.setMargin(0f, 0f);
    }
  }
}
