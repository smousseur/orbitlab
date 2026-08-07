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
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
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
  private static final float CONTENT_HEIGHT = WINDOW_HEIGHT - HEADER_HEIGHT - PanelFooter.HEIGHT;

  private final MissionContext missionContext;
  private final EventBus eventBus;

  private final ModalBackdrop backdrop;
  private final Container root;
  private final MissionListView listView;
  private final PanelFooter footer;

  private MissionId selectedMissionId;
  private List<String> lastSnapshot = List.of();
  private boolean visible = false;

  private Runnable onClose = () -> {};
  private Runnable onNewMission = () -> {};

  public MissionPanelWidget(ApplicationContext context) {
    Objects.requireNonNull(context, "context");
    this.missionContext = context.missionContext();
    this.eventBus = context.eventBus();
    this.selectedMissionId = missionContext.getSelectedMissionId();

    backdrop = new ModalBackdrop();
    backdrop.setOnClick(() -> onClose.run());

    root = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(WINDOW_WIDTH, WINDOW_HEIGHT, 0));
    root.setBackground(FormStyles.shellBg());
    root.getInsetsComponent().setInsets(new Insets3f(0, 0, 0, 0));
    root.setBorder(null);
    clearMargin(root.getBackground());

    PanelHeader header = new PanelHeader(WINDOW_WIDTH);
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
    if (selectedMissionId != null && missionContext.findMission(selectedMissionId).isEmpty()) {
      selectedMissionId = null;
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
      public void onSelect(MissionId missionId) {
        selectMission(missionId);
      }

      @Override
      public void onEdit(MissionId missionId) {
        logger.info("Edit not yet implemented for mission [{}]", missionId.shortForm());
      }

      @Override
      public void onCompute(MissionId missionId) {
        eventBus.publishMissionAction(missionId, EventBus.MissionAction.OPTIMIZE);
      }

      @Override
      public void onSetMode(MissionId missionId, OptimizationType type) {
        missionContext.findMission(missionId).ifPresent(e -> e.setOptimizationType(type));
        refresh();
      }

      @Override
      public void onDelete(MissionId missionId) {
        if (missionId.equals(selectedMissionId)) {
          selectedMissionId = null;
        }
        missionContext.removeMission(missionId);
        eventBus.publishMissionAction(missionId, EventBus.MissionAction.DELETE);
      }
    };
  }

  private void selectMission(MissionId missionId) {
    if (missionId.equals(selectedMissionId)) {
      selectedMissionId = null;
    } else {
      selectedMissionId = missionId;
    }
    refresh();
  }

  private void refresh() {
    List<MissionEntry> entries = missionContext.getMissions();
    listView.refresh(entries, selectedMissionId);
    MissionEntry selected = missionContext.findMission(selectedMissionId).orElse(null);
    footer.setSelectedMission(selected);
  }

  private List<String> buildSnapshot() {
    List<MissionEntry> entries = missionContext.getMissions();
    MissionId telemetered = missionContext.getTelemetryFocusMissionId();
    List<String> snapshot = new ArrayList<>(entries.size() + 2);
    snapshot.add("sel=" + (selectedMissionId == null ? "" : selectedMissionId));
    snapshot.add("tel=" + (telemetered == null ? "" : telemetered));
    for (MissionEntry entry : entries) {
      // Keyed on the id so two homonymous missions produce distinct snapshot lines. The
      // optimization
      // type is part of the snapshot because the footer's details line shows it: a mode set outside
      // this panel must still redraw the selection details.
      snapshot.add(
          entry.id()
              + ":"
              + entry.mission().getStatus()
              + ":"
              + entry.isVisible()
              + ":"
              + entry.getOptimizationType());
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
