package com.smousseur.orbitlab.engine.scene.hover;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.Optional;

/**
 * Decides which planet the hover designates, from what is under the cursor.
 *
 * <p>Lemur arbitrates first, by depth: a panel, the open menu or a modal in front of the scene
 * takes the cursor, and neither a planet icon nor the scene is then under it. What is left is
 * resolved here, in this order:
 *
 * <ol>
 *   <li>frozen — the camera turning or flying — keeps the previous target, without detection: an
 *       icon sweeping under a still cursor must not light anything;
 *   <li>a planet's icon under the cursor designates that planet;
 *   <li>the scene under the cursor runs the orbit detection;
 *   <li>otherwise nothing is designated.
 * </ol>
 *
 * <p>The detection is the expensive step, and it is only run in the third case.
 */
public final class HoverResolver {

  /** The orbit detection, asked only when the scene is under the cursor. */
  @FunctionalInterface
  public interface OrbitPicker {

    /**
     * Picks the orbit the cursor designates.
     *
     * @param current the planet lit until now, whichever its handle, or {@code null} when none is —
     *     its orbit keeps the hand within the wider exit band
     * @return the planet whose orbit the cursor designates, or empty
     */
    Optional<SolarSystemBody> pick(SolarSystemBody current);
  }

  private HoverResolver() {}

  /**
   * Resolves the hover for this frame.
   *
   * @param frozen whether the camera is turning or flying
   * @param previous the target of the previous frame, or {@code null} when there was none
   * @param iconUnderCursor the planet whose icon is under the cursor, or {@code null}
   * @param sceneUnderCursor whether the scene sensor is under the cursor
   * @param picker the orbit detection
   * @return the designated planet and its handle, or empty
   */
  public static Optional<HoverTarget> resolve(
      boolean frozen,
      HoverTarget previous,
      SolarSystemBody iconUnderCursor,
      boolean sceneUnderCursor,
      OrbitPicker picker) {
    if (frozen) {
      return Optional.ofNullable(previous);
    }
    if (iconUnderCursor != null) {
      return Optional.of(new HoverTarget(iconUnderCursor, HoverTarget.Handle.ICON));
    }
    if (sceneUnderCursor) {
      return picker
          .pick(previous == null ? null : previous.body())
          .map(body -> new HoverTarget(body, HoverTarget.Handle.ORBIT));
    }
    return Optional.empty();
  }
}
