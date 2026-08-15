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
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.UiLayers;
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
        public void onToggleTelemetry(MissionId missionId, boolean currentlyTelemetered) {}

        @Override
        public void onToggleVisibility(MissionId missionId) {}
      };

  /** Listener for row-level actions exposed by the widget. */
  public interface RowListener {
    void onToggleTelemetry(MissionId missionId, boolean currentlyTelemetered);

    void onToggleVisibility(MissionId missionId);
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

  /**
   * Positions the panel top-left, directly under the application menu it is opened from. The three
   * numbers are shared with the menu rather than guessed here: the panel used to sit 11 px to the
   * left of the button above it, on a trigger height that matched nothing on screen.
   *
   * @param screenHeight height of the render surface in pixels
   * @param topOffset pixels between the top of the screen and the top of the menu's title button
   */
  public void layoutTopLeft(int screenHeight, float topOffset) {
    float y =
        screenHeight - topOffset - AppStyles.HUD_MENU_HEIGHT_PX - AppStyles.HUD_STACK_GAP_PX;
    root.setLocalTranslation(AppStyles.HUD_MARGIN_PX, y, UiLayers.PANEL);
  }

  /** Called every frame; rebuilds the body only when the snapshot key changes. */
  public void update() {
    if (!attached) return;
    List<RowSnapshot> snapshot = buildSnapshot();
    if (!snapshot.equals(lastSnapshot)) {
      lastSnapshot = snapshot;
      rebuildBody(snapshot);
    }
  }

  private List<RowSnapshot> buildSnapshot() {
    List<RowSnapshot> snapshot = new ArrayList<>();
    MissionId telemeteredId = missionContext.getTelemetryFocusMissionId();
    for (MissionEntry entry : missionContext.getMissions()) {
      if (entry.mission().getStatus() != MissionStatus.READY) continue;
      ColorRGBA c = entry.getColor() != null ? entry.getColor() : ColorRGBA.Cyan;
      snapshot.add(
          new RowSnapshot(
              entry.id(),
              entry.mission().getName(),
              entry.mission().getStatus(),
              c,
              entry.isVisible(),
              entry.id().equals(telemeteredId),
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
    footer.refresh(visibleCount, pageIndex, pageCount);
  }

  /**
   * Row snapshot key — equality drives whether the body needs a rebuild. Carries both the id (what
   * the row's actions target) and the name (what the row displays), since names may be duplicated.
   */
  public record RowSnapshot(
      MissionId missionId,
      String name,
      MissionStatus status,
      ColorRGBA color,
      boolean visible,
      boolean telemetered,
      String subtitle) {}
}
