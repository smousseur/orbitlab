package com.smousseur.orbitlab.simulation.mission.detector;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.AbstractDetector;
import org.orekit.propagation.events.EventDetectionSettings;
import org.orekit.propagation.events.handlers.ContinueOnEvent;
import org.orekit.propagation.events.handlers.EventHandler;

/**
 * Detects a trajectory reaching the lunar surface. Shape sibling of {@link ReentryDetector}: one
 * scalar switching function, no state of its own, and what to do about the crossing left to the
 * handler the caller attaches.
 *
 * <p>The switching function is the distance to the Moon's centre minus the lunar radius, the Moon
 * being evaluated in the state's own frame — so it reads the same on a geocentric arc, which is
 * where the translunar aim flies its candidates. A point-mass Moon has no surface: without this, a
 * candidate aimed at the centre flies straight through the body and reports a perilune hundreds of
 * kilometres inside it.
 *
 * <p><b>Spherical, and checked every 10 s near the surface.</b> A grazing passage shorter than one
 * check interval can slip between two evaluations; the trajectory then flies on and the caller
 * reads its closest approach, which is still under the surface. That is acceptable for the caller
 * this detector exists for, which acts on the fact of the impact and not on where it happened.
 */
public class LunarImpactDetector extends AbstractDetector<LunarImpactDetector> {

  /** How often the switching function is checked by default (s); see the constructor. */
  private static final double CHECK_INTERVAL = 10.0;

  /** Radius of the lunar reference sphere (m). */
  private final double lunarRadius;

  /**
   * Creates a lunar-impact detector.
   *
   * <p>Checked every 10 s with a 1 s date convergence, the settings of {@link ReentryDetector} and
   * for the same reason: the caller acts on the fact of the crossing, never on its epoch.
   *
   * @param lunarRadius the radius of the lunar reference sphere (m)
   */
  public LunarImpactDetector(double lunarRadius) {
    super(CHECK_INTERVAL, 1.0, DEFAULT_MAX_ITER, new ContinueOnEvent());
    this.lunarRadius = lunarRadius;
  }

  /**
   * The same detector, checked no more often than the surface can be reached — the cadence of
   * {@link ReentryDetector#withClosingSpeed}, for the same reason: a translunar coast spends days
   * hundreds of thousands of kilometres from the Moon, where a check every 10 s is pure cost.
   *
   * @param maxSpeed an upper bound on the speed of the trajectory relative to the Moon (m/s)
   * @return the detector with an adaptive check interval
   */
  public LunarImpactDetector withClosingSpeed(double maxSpeed) {
    return withMaxCheck((state, isForward) -> FastMath.max(CHECK_INTERVAL, g(state) / maxSpeed));
  }

  /**
   * Copy constructor used by {@link #create}. The radius travels through here for the reason {@link
   * ReentryDetector}'s copy constructor states: attaching a handler rebuilds the detector.
   */
  private LunarImpactDetector(
      EventDetectionSettings settings, EventHandler handler, double lunarRadius) {
    super(settings, handler);
    this.lunarRadius = lunarRadius;
  }

  @Override
  protected LunarImpactDetector create(
      EventDetectionSettings detectionSettings, EventHandler newHandler) {
    return new LunarImpactDetector(detectionSettings, newHandler, lunarRadius);
  }

  /**
   * Switching function: positive above the lunar surface, negative under it. It decreases through
   * zero when the trajectory hits the Moon.
   */
  @Override
  public double g(SpacecraftState state) {
    return state
            .getPosition()
            .subtract(
                OrekitService.get()
                    .body(SolarSystemBody.MOON)
                    .getPosition(state.getDate(), state.getFrame()))
            .getNorm()
        - lunarRadius;
  }
}
