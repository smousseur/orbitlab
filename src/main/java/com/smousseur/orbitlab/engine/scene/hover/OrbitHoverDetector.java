package com.smousseur.orbitlab.engine.scene.hover;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.List;
import java.util.Optional;

/**
 * Which orbit the cursor designates, among the orbits as they land on the screen.
 *
 * <p>An orbit is hovered within a band in screen space: {@code enterPx} to light it, {@code exitPx}
 * to put it out again. The gap between the two is a hysteresis, and it is not decoration: the lit
 * orbit is drawn wider and fades in, so a single threshold would make it flicker each time the
 * cursor rests on its edge.
 *
 * <p><b>The rule.</b> The orbit already lit keeps the hand while the cursor stays within {@code
 * exitPx} of it, unless another orbit is within its own {@code enterPx} <em>and strictly
 * closer</em>. Otherwise the closest orbit within {@code enterPx} is lit, or none. A tie keeps the
 * current orbit; between two others, the first in the list wins, which is the {@link
 * SolarSystemBody} order when the list comes from {@link OrbitScreenProjector}.
 *
 * <p>This class answers that one question and nothing else. Whether the cursor is over the scene at
 * all — not over a panel, not over a planet icon, which both take priority — is Lemur's decision,
 * made before this one is asked.
 */
public final class OrbitHoverDetector {

  private final float enterPx;
  private final float exitPx;

  /**
   * @param enterPx the distance in pixels within which an orbit is lit
   * @param exitPx the distance in pixels beyond which the lit orbit goes out; at least {@code
   *     enterPx}
   */
  public OrbitHoverDetector(float enterPx, float exitPx) {
    if (!(enterPx > 0f) || !(exitPx >= enterPx)) {
      throw new IllegalArgumentException(
          "Expected 0 < enterPx <= exitPx, got enterPx=" + enterPx + ", exitPx=" + exitPx);
    }
    this.enterPx = enterPx;
    this.exitPx = exitPx;
  }

  /**
   * Picks the orbit the cursor designates.
   *
   * @param orbits the orbits on screen; an orbit that is not drawn is simply absent
   * @param cursorX horizontal cursor position, in pixels
   * @param cursorY vertical cursor position, in pixels, from the bottom of the screen
   * @param current the orbit lit until now, or {@code null} when none is
   * @return the orbit to light, or empty when the cursor designates none
   */
  public Optional<SolarSystemBody> pick(
      List<ProjectedOrbit> orbits, float cursorX, float cursorY, SolarSystemBody current) {
    float currentDistance = Float.POSITIVE_INFINITY;
    SolarSystemBody closest = null;
    float closestDistance = Float.POSITIVE_INFINITY;
    for (ProjectedOrbit orbit : orbits) {
      if (!orbit.isNear(cursorX, cursorY, exitPx)) {
        continue;
      }
      float distance = orbit.distanceTo(cursorX, cursorY);
      if (orbit.body() == current) {
        currentDistance = distance;
      } else if (distance <= enterPx && distance < closestDistance) {
        closest = orbit.body();
        closestDistance = distance;
      }
    }
    if (currentDistance <= exitPx && !(closestDistance < currentDistance)) {
      return Optional.of(current);
    }
    return Optional.ofNullable(closest);
  }
}
