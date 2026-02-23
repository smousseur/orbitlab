package com.smousseur.orbitlab.simulation.mission.maneuver;

import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.AbstractDetector;
import org.orekit.propagation.events.EventDetectionSettings;
import org.orekit.propagation.events.handlers.ContinueOnEvent;
import org.orekit.propagation.events.handlers.EventHandler;
import org.orekit.propagation.events.handlers.StopOnEvent;

/**
 * Stops propagation when spacecraft mass drops to the minimum allowable mass (i.e. all available
 * propellant for the current stage has been consumed).
 */
public class MassDepletionDetector extends AbstractDetector<MassDepletionDetector> {

  private final double minAllowableMass;

  /**
   * @param minAllowableMass floor mass in kg (dry stage + upper stages + payload)
   */
  public MassDepletionDetector(double minAllowableMass) {
    super(1.0, 1e-6, DEFAULT_MAX_ITER, new ContinueOnEvent());
    this.minAllowableMass = minAllowableMass;
  }

  private MassDepletionDetector(
      EventDetectionSettings settings, EventHandler handler, double minAllowableMass) {
    super(settings, handler);
    this.minAllowableMass = minAllowableMass;
  }

  @Override
  protected MassDepletionDetector create(
      EventDetectionSettings detectionSettings, EventHandler newHandler) {
    return new MassDepletionDetector(detectionSettings, newHandler, minAllowableMass);
  }

  /**
   * Switching function: positive when mass is above floor, negative when below. The root (g=0) is
   * the exact moment the propellant runs out.
   */
  @Override
  public double g(SpacecraftState state) {
    return state.getMass() - minAllowableMass;
  }
}
