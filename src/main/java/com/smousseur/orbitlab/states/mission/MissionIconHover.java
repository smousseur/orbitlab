package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.engine.HoverConfig;
import com.smousseur.orbitlab.engine.scene.hover.HoverFade;

/**
 * The hover fade of one mission icon.
 *
 * <p>The icon's own listener says whether the cursor is on it, and the fade follows that — except
 * while the planets' hover is frozen, the camera turning or flying: the target is then kept, so an
 * icon sweeping under a still cursor lights up no more than a planet would. The fade itself keeps
 * running toward the kept target.
 */
final class MissionIconHover {

  private final HoverFade fade;
  private boolean hovered;
  private boolean lit;

  MissionIconHover(HoverConfig config) {
    this.fade = HoverFade.of(config);
  }

  /**
   * @param hovered whether the cursor is on the icon
   */
  void setHovered(boolean hovered) {
    this.hovered = hovered;
  }

  /**
   * Advances the fade one frame.
   *
   * @param frozen whether the hover is frozen
   * @param tpf the frame time, in seconds
   * @return the intensity to show
   */
  float advance(boolean frozen, float tpf) {
    if (!frozen) {
      lit = hovered;
    }
    return fade.advance(lit, tpf);
  }
}
