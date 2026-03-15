package com.smousseur.orbitlab.simulation.mission;

import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;

/**
 * Abstract base class for a single phase of a {@link Mission}. Each stage has a name, an optional
 * entry action, and a configuration step that sets up the propagator with force models, attitude
 * providers, and event detectors specific to that phase.
 *
 * <p>Stages are executed sequentially by the mission. The {@link #enter} method is called when the
 * stage becomes active, and {@link #configure} sets up the numerical propagator for the stage's
 * duration.
 */
public abstract class MissionStage {
  protected final String name;

  /**
   * Creates a new mission stage with the given name.
   *
   * @param name the human-readable name of this stage
   */
  public MissionStage(String name) {
    this.name = name;
  }

  /**
   * Called when this stage becomes the active stage of the mission. Subclasses may override this
   * to perform entry actions such as applying a pitch kick or jettisoning a stage. The default
   * implementation returns the previous state unchanged.
   *
   * @param previousState the spacecraft state at the end of the previous stage
   * @param mission the parent mission
   * @return the spacecraft state to use as the initial state for this stage
   */
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    return previousState;
  }

  /**
   * Configures the given numerical propagator with the force models, attitude providers, and event
   * detectors required by this stage. Called after {@link #enter} when the stage becomes active.
   *
   * @param propagator the numerical propagator to configure
   * @param mission the parent mission
   */
  public abstract void configure(NumericalPropagator propagator, Mission mission);

  /**
   * Propagates this stage in standalone mode (no side-effects on Mission's stage index). Used by
   * the optimizer to advance the mission state through non-optimizable stages.
   *
   * <p>Default implementation calls {@link #enter} then returns the updated state. Stages with a
   * duration (e.g. thrust stages) should override this to actually propagate.
   */
  public SpacecraftState propagateStandalone(SpacecraftState currentState, Mission mission) {
    return enter(currentState, mission);
  }

  /**
   * Returns the name of this stage.
   *
   * @return the stage name
   */
  public final String getName() {
    return name;
  }
}
