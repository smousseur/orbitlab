package com.smousseur.orbitlab.simulation.mission;

import com.smousseur.orbitlab.simulation.OrekitService;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

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
  protected AbsoluteDate configuredEndDate;

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
   * Returns whether this stage burns propellant. Non-propulsive stages (coasts, separations) drop
   * mass only by jettison: the performance report must not account their mass delta as consumed
   * propellant nor derive a ΔV from it.
   */
  public boolean isPropulsive() {
    return true;
  }

  /**
   * Returns the integrator max step to use when propagating this stage, sized to keep the
   * late-ignition invariant (spec 06 I6, bilan 08 §3.1). The default steps at {@link
   * OrekitService#COAST_MAX_STEP} for a non-propulsive (burn-free) stage and at the conservative
   * {@link OrekitService#SAFE_MAX_STEP} for a propulsive one. Stages whose upper-stage burn can grow
   * light under a varying I7 load override this to size the step from their actual burns, so the
   * proven Falcon Heavy stepping is preserved while a lighter load auto-tightens.
   *
   * @param entryState the spacecraft state at the start of this stage
   * @param mission the parent mission
   * @return the integrator max step in seconds
   */
  public double maxStepSeconds(SpacecraftState entryState, Mission mission) {
    return isPropulsive() ? OrekitService.SAFE_MAX_STEP : OrekitService.COAST_MAX_STEP;
  }

  /**
   * Returns the name of this stage.
   *
   * @return the stage name
   */
  public final String getName() {
    return name;
  }

  /**
   * Returns the expected end date for this stage, set during {@link #configure}. Used by the
   * ephemeris generator to propagate to the exact end time rather than an arbitrary safety limit.
   *
   * @return the end date, or {@code null} if not set
   */
  public AbsoluteDate getConfiguredEndDate() {
    return configuredEndDate;
  }
}
