package com.smousseur.orbitlab.simulation.mission.detector;

import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.AbstractDetector;
import org.orekit.propagation.events.EventDetectionSettings;
import org.orekit.propagation.events.handlers.ContinueOnEvent;
import org.orekit.propagation.events.handlers.EventHandler;

/**
 * Detects a trajectory that has sunk irrecoverably below the Earth's surface — a re-entry. Mass
 * sibling of {@link MassDepletionDetector}: same shape, same statelessness, one scalar switching
 * function, and the decision of what to do about it left to the handler {@link ReentryGuard}
 * attaches.
 *
 * <p>The switching function is the spherical altitude of {@link MinAltitudeTracker} offset by
 * {@link ReentryGuard#SUBSURFACE_FLOOR}, i.e. it goes negative once the trajectory is 50 km
 * <em>under</em> the WGS84 equatorial reference sphere. See {@link ReentryGuard#SUBSURFACE_FLOOR}
 * for why the floor sits that deep — the short version is that a floor at 0 m would already be
 * breached on the launch pad.
 */
public class ReentryDetector extends AbstractDetector<ReentryDetector> {

  /** Reference sphere radius the switching function is measured against (m). */
  private final double equatorialRadius;

  /**
   * Creates a re-entry detector referenced to the given equatorial radius.
   *
   * <p>Checked every 10 s like {@link MinAltitudeTracker}: a re-entering trajectory sinks through
   * the floor monotonically, so the interval only has to be short enough to catch the single sign
   * change, not to resolve a narrow feature. The 1 s date convergence is deliberately loose — the
   * caller acts on the <em>fact</em> of the crossing, never on its epoch, and every root-finding
   * iteration on a doomed candidate is wasted work.
   *
   * @param equatorialRadius the reference sphere radius (m), from the stage's gravitational context
   */
  public ReentryDetector(double equatorialRadius) {
    super(10.0, 1.0, DEFAULT_MAX_ITER, new ContinueOnEvent());
    this.equatorialRadius = equatorialRadius;
  }

  /**
   * Copy constructor used by {@link #create}.
   *
   * <p><b>The radius must travel through here</b>, and that is the whole reason this constructor
   * takes it: {@link AbstractDetector} rebuilds the detector through {@link #create} as soon as a
   * handler is attached with {@code withHandler}. A radius left out of the copy would be silently
   * lost on that first call and the switching function would fall back to a default — a failure
   * that shows up as a wrong trajectory, never as an error.
   */
  private ReentryDetector(
      EventDetectionSettings settings, EventHandler handler, double equatorialRadius) {
    super(settings, handler);
    this.equatorialRadius = equatorialRadius;
  }

  @Override
  protected ReentryDetector create(
      EventDetectionSettings detectionSettings, EventHandler newHandler) {
    return new ReentryDetector(detectionSettings, newHandler, equatorialRadius);
  }

  /**
   * Switching function: positive while the trajectory is above the sub-surface floor, negative once
   * it has sunk below it. The root (g=0) is the moment the trajectory is unambiguously inside the
   * Earth.
   */
  @Override
  public double g(SpacecraftState state) {
    double sphericalAltitude = state.getPVCoordinates().getPosition().getNorm() - equatorialRadius;
    return sphericalAltitude - ReentryGuard.SUBSURFACE_FLOOR;
  }
}
