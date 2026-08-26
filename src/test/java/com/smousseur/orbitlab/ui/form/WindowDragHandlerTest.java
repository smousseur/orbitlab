package com.smousseur.orbitlab.ui.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.simsilica.lemur.core.GuiComponent;
import com.simsilica.lemur.core.GuiControl;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.UiLayers;
import org.junit.jupiter.api.Test;

/**
 * Bounds of {@link WindowDragHandler#clamp}. The method takes a {@link com.jme3.scene.Spatial} and
 * reads a {@link GuiControl}, so it needs no display, no asset manager and no {@code GuiGlobals} —
 * a bare {@link Node} carrying a control is enough.
 */
class WindowDragHandlerTest {

  /** The mission window's dimensions, the only window this clamp currently serves. */
  private static final float WINDOW_WIDTH = 720f;

  private static final float WINDOW_HEIGHT = 640f;

  /** {@code PanelHeader.HEIGHT}, the grab band. */
  private static final float HEADER = 88f;

  /**
   * The application's own resolution — {@code OrbitLabApplication} calls setResolution(1280, 720).
   */
  private static final int APP_WIDTH = 1280;

  private static final int APP_HEIGHT = 720;

  private static final float EPS = 1e-4f;

  /**
   * What the top bound reserves. Derived from the constant rather than written out, so moving it
   * moves the expectations with it.
   */
  private static final float TOP_RESERVE = AppStyles.HUD_MARGIN_PX;

  // ---------------------------------------------------------------------------
  // The four bounds
  // ---------------------------------------------------------------------------

  @Test
  void leftBoundStopsTheWindowAtTheScreenEdge() {
    Node window = laidOutWindow(-120f, 400f);

    WindowDragHandler.clamp(window, APP_WIDTH, APP_HEIGHT, HEADER);

    assertEquals(0f, window.getLocalTranslation().x, EPS);
  }

  @Test
  void rightBoundKeepsTheWholeWidthOnScreen() {
    Node window = laidOutWindow(2000f, 400f);

    WindowDragHandler.clamp(window, APP_WIDTH, APP_HEIGHT, HEADER);

    assertEquals(APP_WIDTH - WINDOW_WIDTH, window.getLocalTranslation().x, EPS);
  }

  @Test
  void bottomBoundKeepsTheWholeWindowOnScreen() {
    Node window = laidOutWindow(300f, -500f);

    WindowDragHandler.clamp(window, APP_WIDTH, APP_HEIGHT, HEADER);

    // y is the top edge and grows upwards, so a window whose top sits at its own height has its
    // bottom flush with the bottom of the screen.
    assertEquals(WINDOW_HEIGHT, window.getLocalTranslation().y, EPS);
  }

  @Test
  void topBoundReservesOnlyTheHudMargin() {
    Node window = laidOutWindow(300f, 5000f);

    WindowDragHandler.clamp(window, APP_WIDTH, APP_HEIGHT, HEADER);

    assertEquals(APP_HEIGHT - TOP_RESERVE, window.getLocalTranslation().y, EPS);
  }

  @Test
  void aPositionInsideTheBoundsIsLeftAlone() {
    Node window = laidOutWindow(200f, 660f);

    WindowDragHandler.clamp(window, APP_WIDTH, APP_HEIGHT, HEADER);

    Vector3f position = window.getLocalTranslation();
    assertEquals(200f, position.x, EPS);
    assertEquals(660f, position.y, EPS);
    assertEquals(UiLayers.WINDOW, position.z, EPS, "the clamp must not touch the layer");
  }

  // ---------------------------------------------------------------------------
  // The vertical guarantee: the whole window when it fits, its header when it does not
  // ---------------------------------------------------------------------------

  @Test
  void aWindowThatFitsKeepsItsWholeBodyOnScreen() {
    Node window = laidOutWindow(300f, -500f);

    WindowDragHandler.clamp(window, APP_WIDTH, APP_HEIGHT, HEADER);

    // y is the top edge and grows upwards, so the body spans [y - WINDOW_HEIGHT, y].
    float top = window.getLocalTranslation().y;
    assertEquals(0f, top - WINDOW_HEIGHT, EPS, "the bottom edge is flush with the screen");
    assertTrue(top <= APP_HEIGHT - TOP_RESERVE, "and the top edge is inside the top bound");
  }

  @Test
  void aWindowTallerThanTheRoomAvailableFallsBackToTheHeaderGuarantee() {
    // 900 px of window in 720 px of screen: keeping the body on screen is impossible, so the bound
    // gives up on it and keeps the grab band reachable instead of pinning the window by its height.
    Node window = sizedWindow(WINDOW_WIDTH, 900f, 300f, -500f);

    WindowDragHandler.clamp(window, APP_WIDTH, APP_HEIGHT, HEADER);

    float top = window.getLocalTranslation().y;
    assertEquals(APP_HEIGHT - TOP_RESERVE, top, EPS, "pinned to the top bound");
    assertTrue(top - HEADER > 0f, "the header band is on screen");
    assertTrue(top - 900f < 0f, "and the body runs off the bottom, which is the fallback");
  }

  // ---------------------------------------------------------------------------
  // Rendered size versus preferred size
  // ---------------------------------------------------------------------------

  @Test
  void theRenderedSizeIsUsedOnceTheLayoutHasRun() {
    // A control whose laid-out size differs from its preferred size: only the rendered one may
    // decide the right bound.
    Node window = new Node("window");
    GuiControl control = new GuiControl(new GuiComponent[0]);
    window.addControl(control);
    control.setPreferredSize(new Vector3f(WINDOW_WIDTH, WINDOW_HEIGHT, 0f));
    control.setSize(new Vector3f(400f, 300f, 0f));
    window.setLocalTranslation(2000f, 400f, UiLayers.WINDOW);

    WindowDragHandler.clamp(window, APP_WIDTH, APP_HEIGHT, HEADER);

    assertEquals(APP_WIDTH - 400f, window.getLocalTranslation().x, EPS);
  }

  @Test
  void thePreferredSizeIsUsedBeforeTheFirstLayout() {
    // What a window looks like on the frame it is attached: Lemur lays out during the GUI node's
    // logical-state update, which runs after the app states, so the rendered size is still zero.
    Node window = unlaidOutWindow(2000f, 400f);
    assertEquals(
        0f,
        window.getControl(GuiControl.class).getSize().x,
        EPS,
        "precondition: the layout has not run yet");

    WindowDragHandler.clamp(window, APP_WIDTH, APP_HEIGHT, HEADER);

    assertEquals(APP_WIDTH - WINDOW_WIDTH, window.getLocalTranslation().x, EPS);
  }

  // ---------------------------------------------------------------------------
  // Regression: the mission window must actually be draggable on both axes
  // ---------------------------------------------------------------------------

  @Test
  void theMissionWindowHasRealVerticalTravelAtTheApplicationResolution() {
    // Why this test exists: the first version of the bounds reserved the whole HUD anchoring chain
    // (margin + menu height + stack gap = 78 px) at the top and guaranteed only the header at the
    // bottom. On the 1280x720 the application asks for, that left a 640 px window
    // 720 - 78 - 640 = 2 px of travel where its body stayed on screen, and it read as horizontally
    // draggable only. The bound has to leave room worth dragging through.
    float minY = WINDOW_HEIGHT;
    float maxY = APP_HEIGHT - TOP_RESERVE;

    assertTrue(
        maxY - minY >= 32f,
        "vertical travel is "
            + (maxY - minY)
            + " px, too little to read as draggable — the bounds are over-constrained");

    // Both extremes are reachable, and each keeps the whole window on screen.
    Node atBottom = laidOutWindow(300f, -5000f);
    WindowDragHandler.clamp(atBottom, APP_WIDTH, APP_HEIGHT, HEADER);
    assertEquals(minY, atBottom.getLocalTranslation().y, EPS);

    Node atTop = laidOutWindow(300f, 5000f);
    WindowDragHandler.clamp(atTop, APP_WIDTH, APP_HEIGHT, HEADER);
    assertEquals(maxY, atTop.getLocalTranslation().y, EPS);
  }

  @Test
  void theCentredFirstPlacementIsLegalAndSitsInsideTheTravel() {
    // MissionPanelWidget.place() centres with these two expressions, then clamps.
    float centredX = Math.round((APP_WIDTH - WINDOW_WIDTH) / 2f);
    float centredY = Math.round((APP_HEIGHT + WINDOW_HEIGHT) / 2f);

    Node window = laidOutWindow(centredX, centredY);
    WindowDragHandler.clamp(window, APP_WIDTH, APP_HEIGHT, HEADER);

    Vector3f placed = window.getLocalTranslation();
    assertEquals(centredX, placed.x, EPS, "centring is untouched horizontally");
    assertEquals(centredY, placed.y, EPS, "and vertically: the centred position is now legal");
    assertEquals(UiLayers.WINDOW, placed.z, EPS);

    // It is a fixed point, so the first pixel of the first drag cannot snap the window.
    WindowDragHandler.clamp(window, APP_WIDTH, APP_HEIGHT, HEADER);
    assertEquals(placed.x, window.getLocalTranslation().x, EPS);
    assertEquals(placed.y, window.getLocalTranslation().y, EPS);

    // And it opens with room on both sides of it.
    assertTrue(placed.y > WINDOW_HEIGHT, "room to drag down");
    assertTrue(placed.y < APP_HEIGHT - TOP_RESERVE, "room to drag up");
  }

  // ---------------------------------------------------------------------------
  // Constructor validation
  // ---------------------------------------------------------------------------

  @Test
  void aNullWindowIsRejectedAtConstruction() {
    assertThrows(NullPointerException.class, () -> new WindowDragHandler(null, HEADER));
  }

  @Test
  void aNegativeHeaderHeightIsRejectedAtConstruction() {
    Node window = laidOutWindow(0f, 0f);
    assertThrows(IllegalArgumentException.class, () -> new WindowDragHandler(window, -1f));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** A window whose layout has already run, so {@code GuiControl.getSize()} is the real size. */
  private static Node laidOutWindow(float x, float y) {
    return sizedWindow(WINDOW_WIDTH, WINDOW_HEIGHT, x, y);
  }

  /** The same, at an arbitrary size. */
  private static Node sizedWindow(float width, float height, float x, float y) {
    Node window = new Node("window");
    GuiControl control = new GuiControl(new GuiComponent[0]);
    // Setting the preferred size before attaching lets the control's own attach-time revalidation
    // publish it as the rendered size.
    control.setPreferredSize(new Vector3f(width, height, 0f));
    window.addControl(control);
    window.setLocalTranslation(x, y, UiLayers.WINDOW);
    return window;
  }

  /** A window on the frame it is attached: preferred size known, rendered size still zero. */
  private static Node unlaidOutWindow(float x, float y) {
    Node window = new Node("window");
    GuiControl control = new GuiControl(new GuiComponent[0]);
    // Attaching first makes the revalidation run against no preferred size; the one set afterwards
    // only marks the control invalid, and nothing revalidates it until a logical-state update.
    window.addControl(control);
    control.setPreferredSize(new Vector3f(WINDOW_WIDTH, WINDOW_HEIGHT, 0f));
    window.setLocalTranslation(x, y, UiLayers.WINDOW);
    return window;
  }
}
