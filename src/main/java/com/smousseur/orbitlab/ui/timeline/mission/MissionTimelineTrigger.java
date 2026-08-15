package com.smousseur.orbitlab.ui.timeline.mission;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.style.ElementId;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.UiLayers;
import com.smousseur.orbitlab.ui.timeline.TimelineStyles;
import java.util.Objects;

/**
 * The compact button that opens and closes the mission track (spec §11).
 *
 * <p><b>It is present or absent, never greyed.</b> Its condition of existence is the telemetry
 * widget's own: whenever telemetry is on screen there is a followed mission with an ephemeris, and
 * therefore something to open. {@code UI-4} deleted {@code MissionPanelTrigger} because its
 * disabled state lied about what it meant; the fix taken from that is not to re-wire a greyed
 * state but not to have one. The HUD already changes shape at that instant — telemetry appears and
 * disappears on exactly the same conditions — so a button that follows it reads as coherent rather
 * than as popping up.
 *
 * <p>Its skin comes from the {@code timeline.trigger.button} selector, never from overrides at
 * construction. Only the on/off transition touches a skin attribute, the same shape
 * {@code AppMenu.ItemView} uses.
 *
 * <p>It is deliberately not placed inside the time capsule: that widget lays its components out at
 * offsets computed in a chain ({@code TimelineWidget.java:73-101}, dividers included) which an
 * insertion would force to be recomputed entirely, for no gain. Nor is it a row action of the
 * display panel: the track is unique and global, and one action per row would suggest N tracks.
 */
public final class MissionTimelineTrigger implements AutoCloseable {

  /** Width of the button, sized on {@code TIMELINE} in mono 10. */
  private static final float WIDTH = 78f;

  private static final float HEIGHT = 20f;

  private final Node parent;
  private final Button button;

  private Runnable onClick = () -> {};
  private boolean present;
  private boolean active;

  /**
   * @param parent the GUI node the button is attached to while present
   */
  public MissionTimelineTrigger(Node parent) {
    this.parent = Objects.requireNonNull(parent, "parent");
    button =
        new Button("TIMELINE", new ElementId(TimelineStyles.TRIGGER_ELEMENT), TimelineStyles.STYLE);
    button.setPreferredSize(new Vector3f(WIDTH, HEIGHT, 0f));
    button.setSize(button.getPreferredSize());
    button.addClickCommands(source -> onClick.run());
  }

  /**
   * Sets the action run when the button is clicked.
   *
   * @param action the toggle action
   */
  public void setOnClick(Runnable action) {
    this.onClick = action != null ? action : () -> {};
  }

  /**
   * Attaches or detaches the button.
   *
   * @param value whether there is a mission to open a track on
   */
  public void setPresent(boolean value) {
    if (value == present) {
      return;
    }
    present = value;
    if (present) {
      parent.attachChild(button);
    } else {
      button.removeFromParent();
    }
  }

  /**
   * Reflects whether the track is currently open.
   *
   * @param value {@code true} when the track is on screen
   */
  public void setActive(boolean value) {
    if (value == active) {
      return;
    }
    active = value;
    TbtQuadBackgroundComponent skin = TimelineStyles.triggerBackground(active);
    if (skin != null) {
      button.setBackground(skin);
    }
    button.setColor(active ? AppStyles.TL_CYAN : AppStyles.TL_TEXT_DIM);
  }

  /**
   * Anchors the button just left of the track's 600-pixel column, vertically centred on the band.
   *
   * <p><b>Anchored to the column, not to the screen edge.</b> The screen's left margin was the
   * first choice and it was wrong twice over: at the bottom-left it lands under JME's statistics
   * overlay, and on a wide display it sits some 600 px away from the capsule it commands. That
   * second fault is the very one §11 used to reject housing this toggle in the telemetry widget —
   * it puts the command far from its effect. Following the column instead keeps the button in the
   * bottom-centre cluster at any window width, and marks the edge the track unfolds from.
   *
   * @param screenWidth the render surface width in pixels
   * @param bandBottom the y of the track band's bottom edge, in screen space
   */
  public void layout(int screenWidth, float bandBottom) {
    float columnLeft = (screenWidth - MissionTimelineWidget.WIDTH) * 0.5f;
    // On a window too narrow to hold the column plus the button, the margin wins: a button pushed
    // off-screen is worse than one touching the capsule.
    float x = Math.max(AppStyles.HUD_MARGIN_PX, columnLeft - AppStyles.HUD_STACK_GAP_PX - WIDTH);
    float y = bandBottom + (MissionTimelineWidget.HEIGHT + HEIGHT) * 0.5f;
    button.setLocalTranslation(x, y, UiLayers.HUD);
  }

  @Override
  public void close() {
    button.removeFromParent();
    present = false;
  }
}
