package com.smousseur.orbitlab.engine.scene.body.lod;

import com.jme3.scene.Spatial;
import com.simsilica.lemur.event.MouseEventControl;
import java.util.function.Consumer;

/**
 * Keeps a hidden icon out of the hover and the click, and reports the icon's hover.
 *
 * <p><b>Lemur still picks a hidden icon.</b> JME's {@code collideWith} ignores the cull hint, and
 * Lemur's pick does not filter on visibility either, so an icon culled at its last position stays
 * hoverable and clickable there — on the globe of the centred planet, whose 3D model hid its icon,
 * or where a body now behind the camera used to be. Lemur's hit search walks up to the first
 * <em>enabled</em> listener control, so switching the icon's control off makes it transparent.
 *
 * <p>The exit is reported here, on the frame the icon is hidden, rather than left to Lemur: Lemur
 * only notices that the hit target changed at its next pick, and sends its own exit then — to the
 * switched-off control too, which forwards it regardless. Reports are edge-triggered, so that later
 * exit changes nothing.
 */
final class IconHoverGuard {

  private final Spatial icon;
  private final Consumer<Boolean> onHover;
  private boolean shown = true;
  private boolean hovered;

  /**
   * @param icon the spatial carrying the icon's {@link MouseEventControl}
   * @param onHover told {@code true} when the cursor enters the icon and {@code false} when it
   *     leaves it, or {@code null}
   */
  IconHoverGuard(Spatial icon, Consumer<Boolean> onHover) {
    this.icon = icon;
    this.onHover = onHover;
  }

  /** The cursor entered the icon. Ignored while the icon is hidden. */
  void entered() {
    if (shown && !hovered) {
      hovered = true;
      report(true);
    }
  }

  /** The cursor left the icon. */
  void exited() {
    if (hovered) {
      hovered = false;
      report(false);
    }
  }

  /**
   * Shows or hides the icon to the pointer. Hiding a hovered icon reports its exit.
   *
   * @param shown whether the icon is drawn
   */
  void setShown(boolean shown) {
    if (shown == this.shown) {
      return;
    }
    this.shown = shown;
    MouseEventControl control = icon.getControl(MouseEventControl.class);
    if (control != null) {
      control.setEnabled(shown);
    }
    if (!shown) {
      exited();
    }
  }

  private void report(boolean entered) {
    if (onHover != null) {
      onHover.accept(entered);
    }
  }
}
