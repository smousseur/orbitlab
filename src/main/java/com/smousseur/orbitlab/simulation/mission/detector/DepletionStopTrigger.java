package com.smousseur.orbitlab.simulation.mission.detector;

import java.util.Collections;
import java.util.List;
import org.hipparchus.CalculusFieldElement;
import org.hipparchus.Field;
import org.orekit.forces.maneuvers.trigger.StartStopEventsTrigger;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.AbstractDetector;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.events.EventDetectionSettings;
import org.orekit.propagation.events.FieldEventDetector;
import org.orekit.propagation.events.handlers.ContinueOnEvent;
import org.orekit.propagation.events.handlers.EventHandler;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.ParameterDriver;

/**
 * Maneuver trigger implementing flame-out semantics (spec 06 I4b): the engine ignites at a fixed
 * date and thrusts until the spacecraft mass reaches the stage's depletion floor. The burn window
 * no longer needs to match the loaded propellant, so the outer propellant-sizing loop can vary
 * loads without recomputing windows.
 */
public class DepletionStopTrigger
    extends StartStopEventsTrigger<DateDetector, DepletionStopTrigger.FlameOutDetector> {

  /**
   * Creates the trigger.
   *
   * @param ignitionDate the engine ignition date
   * @param depletionFloor the mass floor (kg) at which the stage's propellant is exhausted
   */
  public DepletionStopTrigger(AbsoluteDate ignitionDate, double depletionFloor) {
    super(new DateDetector(ignitionDate), new FlameOutDetector(depletionFloor));
  }

  @Override
  public List<ParameterDriver> getParametersDrivers() {
    return Collections.emptyList();
  }

  @Override
  protected <D extends FieldEventDetector<S>, S extends CalculusFieldElement<S>>
      D convertStartDetector(Field<S> field, DateDetector detector) {
    throw new UnsupportedOperationException("field propagation is not supported");
  }

  @Override
  protected <D extends FieldEventDetector<S>, S extends CalculusFieldElement<S>>
      D convertStopDetector(Field<S> field, FlameOutDetector detector) {
    throw new UnsupportedOperationException("field propagation is not supported");
  }

  /**
   * Flame-out as an increasing event: {@code g = floor − mass} rises through zero when the
   * propellant runs out. Maneuver triggers stop the firing on increasing g crossings, which is
   * why {@link MassDepletionDetector} (decreasing g at depletion) cannot be used directly here.
   */
  public static class FlameOutDetector extends AbstractDetector<FlameOutDetector> {

    private final double depletionFloor;

    /**
     * Creates a flame-out detector.
     *
     * @param depletionFloor floor mass in kg (dry stage + upper stages + payload)
     */
    public FlameOutDetector(double depletionFloor) {
      super(1.0, 1.0e-6, DEFAULT_MAX_ITER, new ContinueOnEvent());
      this.depletionFloor = depletionFloor;
    }

    private FlameOutDetector(
        EventDetectionSettings settings, EventHandler handler, double depletionFloor) {
      super(settings, handler);
      this.depletionFloor = depletionFloor;
    }

    @Override
    protected FlameOutDetector create(EventDetectionSettings settings, EventHandler newHandler) {
      return new FlameOutDetector(settings, newHandler, depletionFloor);
    }

    @Override
    public double g(SpacecraftState state) {
      return depletionFloor - state.getMass();
    }
  }
}
