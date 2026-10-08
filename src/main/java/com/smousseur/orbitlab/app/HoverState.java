package com.smousseur.orbitlab.app;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.hover.HoverTarget;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * What the cursor hovers, shared between the listeners that observe it and the state that resolves
 * it. Render thread only.
 *
 * <p>It holds two kinds of facts. The raw ones are written by Lemur listeners as the cursor enters
 * and leaves things: the planet whose icon is under the cursor, and whether the scene sensor is.
 * The result is written once a frame by the hover state: the designated planet with its handle, and
 * whether the hover is frozen because the camera is turning or flying. The mission icons read the
 * freeze, and the scene sensor reads the target to answer a click on an orbit.
 */
public final class HoverState {

  private SolarSystemBody iconUnderCursor;
  private boolean sceneUnderCursor;
  private HoverTarget target;
  private boolean frozen;

  /**
   * Records that the cursor entered a planet's icon.
   *
   * @param body the planet
   */
  public void iconEntered(SolarSystemBody body) {
    iconUnderCursor = body;
  }

  /**
   * Records that the cursor left a planet's icon. An exit from another planet's icon than the one
   * recorded changes nothing, whichever order Lemur reports the two in.
   *
   * @param body the planet
   */
  public void iconExited(SolarSystemBody body) {
    if (iconUnderCursor == body) {
      iconUnderCursor = null;
    }
  }

  /**
   * @return the planet whose icon is under the cursor, or empty
   */
  public Optional<SolarSystemBody> iconUnderCursor() {
    return Optional.ofNullable(iconUnderCursor);
  }

  /**
   * Records whether the scene sensor is under the cursor — that is, whether nothing in front of the
   * scene took the cursor.
   *
   * @param underCursor whether the scene is under the cursor
   */
  public void setSceneUnderCursor(boolean underCursor) {
    sceneUnderCursor = underCursor;
  }

  /**
   * @return whether the scene is under the cursor
   */
  public boolean isSceneUnderCursor() {
    return sceneUnderCursor;
  }

  /**
   * Records the result of this frame's resolution.
   *
   * @param target the designated planet and its handle, or {@code null} when none is
   * @param frozen whether the hover is frozen
   */
  public void update(HoverTarget target, boolean frozen) {
    this.target = target;
    this.frozen = frozen;
  }

  /**
   * @return the designated planet and its handle, or empty
   */
  public Optional<HoverTarget> target() {
    return Optional.ofNullable(target);
  }

  /**
   * @return whether the hover is frozen, the camera turning or flying
   */
  public boolean isFrozen() {
    return frozen;
  }

  /**
   * Answers a left press on the scene: when the lit planet was designated by its orbit, and the
   * hover is not frozen, flies to that planet. A planet designated by its icon is the icon's to
   * answer, and the sensor does not even receive that press.
   *
   * @param flyTo the flight request, given the planet
   */
  public void routeScenePress(Consumer<SolarSystemBody> flyTo) {
    if (!frozen && target != null && target.handle() == HoverTarget.Handle.ORBIT) {
      flyTo.accept(target.body());
    }
  }
}
