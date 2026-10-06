package com.smousseur.orbitlab.simulation.mission.detector;

import org.hipparchus.util.FastMath;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.AbstractDetector;
import org.orekit.propagation.events.EventDetectionSettings;
import org.orekit.propagation.events.handlers.ContinueOnEvent;
import org.orekit.propagation.events.handlers.EventHandler;

/**
 * Detects a trajectory that has sunk below a given altitude floor. Mass sibling of {@link
 * MassDepletionDetector}: same shape, same statelessness, one scalar switching function, and the
 * decision of what to do about it left to the handler {@link ReentryGuard} attaches.
 *
 * <p>The switching function is the spherical altitude of {@link MinAltitudeTracker} offset by a
 * {@code floor}. It serves two regimes with the same shape:
 *
 * <ul>
 *   <li><b>drag-off</b>, {@code floor = }{@link ReentryGuard#SUBSURFACE_FLOOR} (−50 km): it goes
 *       negative once the trajectory is 50 km <em>under</em> the WGS84 equatorial reference sphere.
 *       See {@link ReentryGuard#SUBSURFACE_FLOOR} for why the floor sits that deep — the short
 *       version is that a floor at 0 m would already be breached on the launch pad.
 *   <li><b>drag-on</b>, {@code floor = }{@link ReentryGuard#DRAG_REENTRY_FLOOR} (0 km): the
 *       integrator cedes under drag <em>above</em> the deepest launch pad, so the deep floor never
 *       fires; a shallow one does, and the handler {@link ReentryGuard} attaches gates it on a
 *       descending radial velocity so a climbing ascent crosses it without stopping.
 * </ul>
 */
public class ReentryDetector extends AbstractDetector<ReentryDetector> {

  /** How often the switching function is checked by default (s); see the explicit constructor. */
  private static final double CHECK_INTERVAL = 10.0;

  /** Reference sphere radius the switching function is measured against (m). */
  private final double equatorialRadius;

  /** Spherical altitude (m) below which {@link #g} goes negative. */
  private final double floor;

  /**
   * Creates a re-entry detector at the default {@link ReentryGuard#SUBSURFACE_FLOOR} floor.
   *
   * @param equatorialRadius the reference sphere radius (m), from the stage's gravitational context
   */
  public ReentryDetector(double equatorialRadius) {
    this(equatorialRadius, ReentryGuard.SUBSURFACE_FLOOR);
  }

  /**
   * Creates a re-entry detector at an explicit floor.
   *
   * <p>Checked every 10 s like {@link MinAltitudeTracker}: a re-entering trajectory sinks through
   * the floor monotonically, so the interval only has to be short enough to catch the single sign
   * change, not to resolve a narrow feature. The 1 s date convergence is deliberately loose — the
   * caller acts on the <em>fact</em> of the crossing, never on its epoch, and every root-finding
   * iteration on a doomed candidate is wasted work.
   *
   * @param equatorialRadius the reference sphere radius (m), from the stage's gravitational context
   * @param floor the spherical altitude (m) below which the detector fires
   */
  public ReentryDetector(double equatorialRadius, double floor) {
    super(CHECK_INTERVAL, 1.0, DEFAULT_MAX_ITER, new ContinueOnEvent());
    this.equatorialRadius = equatorialRadius;
    this.floor = floor;
  }

  /**
   * The same detector, checked no more often than the floor can be reached: a trajectory moving at
   * most {@code maxSpeed} cannot close a gap of {@code g} metres in less than {@code g / maxSpeed}
   * seconds, so nothing can be missed by waiting that long. Near the floor the check falls back to
   * every 10 s.
   *
   * <p>For a propagation that spends days far from the floor — a translunar coast — this turns tens
   * of thousands of checks into a handful. A nominal flight sees no event either way, so its
   * trajectory is unchanged.
   *
   * @param maxSpeed an upper bound on the speed of the trajectory (m/s)
   * @return the detector with an adaptive check interval
   */
  public ReentryDetector withClosingSpeed(double maxSpeed) {
    return withMaxCheck((state, isForward) -> FastMath.max(CHECK_INTERVAL, g(state) / maxSpeed));
  }

  /**
   * Copy constructor used by {@link #create}.
   *
   * <p><b>The radius and floor must travel through here</b>, and that is the whole reason this
   * constructor takes them: {@link AbstractDetector} rebuilds the detector through {@link #create}
   * as soon as a handler is attached with {@code withHandler}. Either left out of the copy would be
   * silently lost on that first call and the switching function would fall back to a default — a
   * failure that shows up as a wrong trajectory, never as an error.
   */
  private ReentryDetector(
      EventDetectionSettings settings,
      EventHandler handler,
      double equatorialRadius,
      double floor) {
    super(settings, handler);
    this.equatorialRadius = equatorialRadius;
    this.floor = floor;
  }

  @Override
  protected ReentryDetector create(
      EventDetectionSettings detectionSettings, EventHandler newHandler) {
    return new ReentryDetector(detectionSettings, newHandler, equatorialRadius, floor);
  }

  /**
   * Switching function: positive while the trajectory is above the floor, negative once it has sunk
   * below it. The root (g=0) is the moment the trajectory crosses the floor.
   */
  @Override
  public double g(SpacecraftState state) {
    double sphericalAltitude = state.getPVCoordinates().getPosition().getNorm() - equatorialRadius;
    return sphericalAltitude - floor;
  }
}
