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

  static final float WINDOW_WIDTH = 420f;
  static final float WINDOW_HEIGHT = 240f;
  static final float BODY_HEIGHT = 120f;
  private static final float MARGIN_PX = 5f;
  private static final float TRIGGER_HEIGHT = 28f;
  private static final float TRIGGER_GAP = 8f;
  private static final int PAGE_SIZE = 3;

  private final MissionContext missionContext;
  private final Container root;
  private final Container body;
  private final DisplayPanelFooter footer;
  private Container listContainer;
  private int pageIndex = 0;

  private boolean attached = false;
  private boolean visible = true;
  private List<RowSnapshot> lastSnapshot = List.of();

  private Runnable onManageClicked = () -> {};
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
    root.setPreferredSize(new Vector3f(WINDOW_WIDTH, WINDOW_HEIGHT, 0));
    root.setBackground(FormStyles.shellBg());
    root.setInsetsComponent(new InsetsComponent(new Insets3f(5, 0, 5, 0)));

    DisplayPanelHeader header = new DisplayPanelHeader(WINDOW_WIDTH, () -> onManageClicked.run());
    root.addChild(header.getNode());
    root.addChild(UiKit.vSpacer(4));

    body = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    body.setBackground(null);
    body.setPreferredSize(new Vector3f(WINDOW_WIDTH, BODY_HEIGHT, 0));
    root.addChild(body);

    listContainer = newListContainer();

    footer = new DisplayPanelFooter(WINDOW_WIDTH);
    footer.setOnHideAll(() -> onHideAll.run());
    footer.setOnPrev(
        () -> {
          if (pageIndex > 0) {
            pageIndex--;
            rebuildBody(lastSnapshot);
          }
        });
    footer.setOnNext(
        () -> {
          pageIndex++;
          rebuildBody(lastSnapshot);
        });
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
   * Reattach this widget to its parent node if it was hidden. Combined helper for callers that keep
   * the parent reference.
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
    listContainer = newListContainer();

    int total = snapshot.size();
    int pageCount = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    pageIndex = Math.min(Math.max(0, pageIndex), pageCount - 1);
    int from = pageIndex * PAGE_SIZE;
    int to = Math.min(from + PAGE_SIZE, total);

    int visibleCount = 0;
    for (RowSnapshot s : snapshot) {
      if (s.visible()) visibleCount++;
    }
    for (int i = from; i < to; i++) {
      DisplayRow row = new DisplayRow(snapshot.get(i), WINDOW_WIDTH, rowListener);
      listContainer.addChild(row.getNode());
    }
    body.addChild(listContainer);

    if (footer.getNode().getParent() == null) {
      root.addChild(footer.getNode());
    }
    footer.refresh(visibleCount, total, pageIndex, pageCount);
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
