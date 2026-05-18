package com.smousseur.orbitlab.ui.mission.display;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Non-modal HUD widget anchored top-left that lists READY missions and lets the user toggle each
 * mission's visibility and telemetry focus. The widget is purely presentation; routing of UI
 * actions to the rest of the app is done via callbacks set by {@code MissionDisplayPanelAppState}.
 */
public final class MissionDisplayPanelWidget implements AutoCloseable {

  static final float WINDOW_WIDTH = 320f;
  private static final float MARGIN_PX = AppStyles.HUD_MARGIN_PX;
  private static final float TRIGGER_HEIGHT = 28f;
  private static final float TRIGGER_GAP = 8f;

  private final MissionContext missionContext;
  private final Container root;
  private final Container body;
  private final DisplayPanelHeader header;
  private final DisplayPanelFooter footer;
  private final DisplayPanelEmptyState emptyState;
  private Container listContainer;

  private boolean attached = false;
  private boolean visible = true;
  private List<RowSnapshot> lastSnapshot = List.of();

  private Runnable onManageClicked = () -> {};
  private Runnable onCreateClicked = () -> {};
  private Runnable onHideAll = () -> {};
  private RowListener rowListener =
      new RowListener() {
        @Override
        public void onToggleTelemetry(String missionName, boolean currentlyTelemetered) {}

        @Override
        public void onToggleVisibility(String missionName) {}
      };

  /** Listener for row-level actions exposed by the widget. */
  public interface RowListener {
    void onToggleTelemetry(String missionName, boolean currentlyTelemetered);

    void onToggleVisibility(String missionName);
  }

  public MissionDisplayPanelWidget(ApplicationContext context) {
    Objects.requireNonNull(context, "context");
    this.missionContext = context.missionContext();

    root = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(WINDOW_WIDTH, 0f, 0));
    root.setBackground(FormStyles.shellBg());
    root.setInsetsComponent(new InsetsComponent(new Insets3f(8, 0, 8, 0)));

    header = new DisplayPanelHeader(WINDOW_WIDTH, () -> onManageClicked.run());
    root.addChild(header.getNode());
    root.addChild(UiKit.vSpacer(4));

    body = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    body.setBackground(null);
    body.setPreferredSize(new Vector3f(WINDOW_WIDTH, 0f, 0));
    root.addChild(body);

    emptyState = new DisplayPanelEmptyState(WINDOW_WIDTH);
    emptyState.setOnCreate(() -> onCreateClicked.run());

    listContainer = newListContainer();

    footer = new DisplayPanelFooter(WINDOW_WIDTH);
    footer.setOnHideAll(() -> onHideAll.run());
    root.addChild(UiKit.vSpacer(4));
    root.addChild(footer.getNode());
  }

  private Container newListContainer() {
    Container c = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    c.setBackground(null);
    return c;
  }

  /** Attach to the given GUI node and make visible. */
  public void attachTo(Node parent) {
    if (!attached) {
      parent.attachChild(root);
      attached = true;
    }
    visible = true;
  }

  @Override
  public void close() {
    if (attached) {
      root.removeFromParent();
      attached = false;
    }
    visible = false;
  }

  public boolean isVisible() {
    return visible;
  }

  public void setVisible(boolean v) {
    if (v == visible) return;
    visible = v;
    if (v) {
      if (!attached) return; // attachTo() must be called first
      // ensure node is attached visually; attachTo handles attachment, here we just toggle
      // by reattaching root to its previous parent if needed.
      // attached==true means root is in the scene graph; nothing to do.
    } else {
      if (attached) {
        // detach but keep "attached" semantic for re-show? We'll just remove from parent.
        root.removeFromParent();
        attached = false;
      }
    }
  }

  /**
   * Reattach this widget to its parent node if it was hidden. Combined helper for callers that
   * keep the parent reference.
   */
  public void show(Node parent) {
    if (!attached) {
      parent.attachChild(root);
      attached = true;
    }
    visible = true;
  }

  public void setOnManageClicked(Runnable r) {
    this.onManageClicked = r != null ? r : () -> {};
  }

  public void setOnCreateClicked(Runnable r) {
    this.onCreateClicked = r != null ? r : () -> {};
  }

  public void setOnHideAll(Runnable r) {
    this.onHideAll = r != null ? r : () -> {};
  }

  public void setRowListener(RowListener listener) {
    this.rowListener = Objects.requireNonNull(listener, "listener");
  }

  /** Position the panel top-left, sitting just below the trigger button. */
  public void layoutTopLeft(int screenWidth, int screenHeight) {
    float y = screenHeight - MARGIN_PX - TRIGGER_HEIGHT - TRIGGER_GAP;
    root.setLocalTranslation(MARGIN_PX, y, 0f);
  }

  /** Called every frame; rebuilds the body only when the snapshot key changes. */
  public void update(float tpf, Camera cam) {
    if (!attached) return;
    List<RowSnapshot> snapshot = buildSnapshot();
    if (!snapshot.equals(lastSnapshot)) {
      lastSnapshot = snapshot;
      rebuildBody(snapshot);
    }
  }

  private List<RowSnapshot> buildSnapshot() {
    List<RowSnapshot> snapshot = new ArrayList<>();
    String telemeteredName = missionContext.getTelemetryFocusMissionName();
    for (MissionEntry entry : missionContext.getMissions()) {
      if (entry.mission().getStatus() != MissionStatus.READY) continue;
      String name = entry.mission().getName();
      ColorRGBA c = entry.getColor() != null ? entry.getColor() : ColorRGBA.Cyan;
      snapshot.add(
          new RowSnapshot(
              name,
              entry.mission().getStatus(),
              c,
              entry.isVisible(),
              name.equals(telemeteredName),
              subtitleFor(entry)));
    }
    return snapshot;
  }

  private static String subtitleFor(MissionEntry entry) {
    return entry.mission().getObjective().body().name();
  }

  private void rebuildBody(List<RowSnapshot> snapshot) {
    body.clearChildren();

    if (snapshot.isEmpty()) {
      body.addChild(emptyState.getNode());
      footer.getNode().removeFromParent();
      return;
    }

    listContainer = newListContainer();
    int visibleCount = 0;
    for (RowSnapshot s : snapshot) {
      DisplayRow row = new DisplayRow(s, WINDOW_WIDTH, rowListener);
      listContainer.addChild(row.getNode());
      if (s.visible()) visibleCount++;
    }
    body.addChild(listContainer);

    if (footer.getNode().getParent() == null) {
      root.addChild(footer.getNode());
    }
    footer.refresh(visibleCount, snapshot.size());
  }

  /** Row snapshot key — equality drives whether the body needs a rebuild. */
  public record RowSnapshot(
      String name,
      MissionStatus status,
      ColorRGBA color,
      boolean visible,
      boolean telemetered,
      String subtitle) {}
}
