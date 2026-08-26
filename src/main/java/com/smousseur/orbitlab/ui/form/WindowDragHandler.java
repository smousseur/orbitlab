package com.smousseur.orbitlab.ui.form;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.core.GuiControl;
import com.simsilica.lemur.event.CursorMotionEvent;
import com.simsilica.lemur.event.DragHandler;
import com.smousseur.orbitlab.ui.AppStyles;
import java.util.Objects;

/**
 * Drags a window by its header and keeps at least its header inside the screen.
 *
 * <p>Lemur's {@link DragHandler} already does the moving, including the 1:1 mapping the GUI bucket
 * needs, and its draggable locator is what lets a grab on the header move the whole window. What it
 * does not do is bound the result: left alone it will happily drag a window off screen, out of
 * reach for good. This subclass clamps after every move.
 *
 * <p>The bounds keep the whole window on screen whenever it fits, and at least its header when it
 * does not. What they deliberately do <i>not</i> do is reserve room for the application menu's
 * title button: see {@link #clamp} for why paying for that across the full screen width left the
 * mission management window with two pixels of vertical travel.
 */
public final class WindowDragHandler extends DragHandler {

  private final Spatial window;
  private final float headerHeight;

  /**
   * @param window the spatial actually moved — the window root, not the header grabbed
   * @param headerHeight height of the grab band, kept fully on screen at all times
   * @throws NullPointerException if {@code window} is null
   * @throws IllegalArgumentException if {@code headerHeight} is negative, which would let the
   *     header itself leave the bottom of the screen
   */
  public WindowDragHandler(Spatial window, float headerHeight) {
    this.window = Objects.requireNonNull(window, "window");
    if (headerHeight < 0f) {
      throw new IllegalArgumentException("headerHeight cannot be negative: " + headerHeight);
    }
    this.headerHeight = headerHeight;
    // Set here rather than through super(...): a locator passed to the constructor cannot read a
    // field, which is what pushes the null check into the drag itself, deep inside Lemur's
    // dispatch.
    setDraggableLocator(grabbed -> this.window);
  }

  @Override
  public void cursorMoved(CursorMotionEvent event, Spatial target, Spatial capture) {
    super.cursorMoved(event, target, capture);
    if (!isDragging() || capture == null) {
      return;
    }
    clamp(
        findDraggable(capture),
        event.getViewPort().getCamera().getWidth(),
        event.getViewPort().getCamera().getHeight(),
        headerHeight);
  }

  /**
   * Pulls a window back until at least its header is inside the screen. Shared with the window
   * itself, which calls it when it is placed and when the render surface is resized under a window
   * already sitting near an edge.
   *
   * <p>The whole window is kept on screen on both axes as long as it fits. A window taller than the
   * room available falls back to a header-only guarantee vertically: its body runs off the bottom
   * rather than the window being pinned by its own height, so its grab area stays reachable.
   *
   * <p>Lemur's GUI convention puts the translation at the element's top-left corner, so {@code y}
   * is the top edge and grows upwards.
   *
   * @param window the window root
   * @param screenWidth width of the render surface in pixels
   * @param screenHeight height of the render surface in pixels
   * @param headerHeight height of the grab band that must stay on screen
   */
  public static void clamp(Spatial window, int screenWidth, int screenHeight, float headerHeight) {
    GuiControl control = window.getControl(GuiControl.class);
    // The rendered size, except on the very first frame of a window: Lemur lays out during the GUI
    // node's logical-state update, which runs after the app states, so a window placed on the frame
    // it is attached still reports a zero size. The preferred size is what that layout will resolve
    // to; falling back to it keeps the first placement bounded without hard-coding this window's
    // dimensions.
    Vector3f size = control.getSize();
    if (size.x <= 0f || size.y <= 0f) {
      size = control.getPreferredSize();
    }
    Vector3f position = window.getLocalTranslation();

    float maxX = Math.max(0f, screenWidth - size.x);

    // Top bound: the ordinary HUD margin, nothing more. An earlier version reserved the whole
    // anchoring chain — margin, menu height and stack gap — so the header could never slide under
    // the application menu's title button, which is thirty depth units in front. That protected a
    // 176-pixel button by taxing the full screen width, and it cost 78 px of a budget that is only
    // (screenHeight - windowHeight) wide: on the 1280x720 the application asks for, a 640-pixel
    // window was left with two pixels of vertical travel and read as horizontally draggable only.
    // The button can now cover the header's left corner; the other ~540 px of a 720-wide header
    // stay grabbable, so the window is never lost.
    float maxY = Math.max(headerHeight, screenHeight - AppStyles.HUD_MARGIN_PX);

    // Bottom bound: keep the whole window on screen, the same guarantee the horizontal bound
    // gives. Only a window too tall to fit falls back to the header-only guarantee, which keeps
    // its grab area reachable instead of pinning it by its own height.
    float minY = FastMath.clamp(size.y, headerHeight, maxY);

    window.setLocalTranslation(
        FastMath.clamp(position.x, 0f, maxX), FastMath.clamp(position.y, minY, maxY), position.z);
  }
}
