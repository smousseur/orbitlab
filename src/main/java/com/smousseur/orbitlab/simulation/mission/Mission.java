package com.smousseur.orbitlab.simulation.mission;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * Abstract base class representing a space mission composed of sequential stages.
 *
 * <p>A mission manages the lifecycle of a spacecraft through a series of {@link MissionStage}
 * instances, propagating the spacecraft state forward in time using Orekit numerical propagators.
 * Subclasses must provide the initial spacecraft state for a given launch date.
 *
 * <p>The mission transitions through stages sequentially, notifying registered {@link
 * MissionListener} instances at each stage transition. Each stage configures the propagator with
 * its own force models, attitude providers, and event detectors.
 */
public abstract class Mission {
  private static final Logger logger = LogManager.getLogger(Mission.class);

  private final String name;
  private final Vehicle vehicle;
  private final MissionObjective objective;
  private final List<MissionStage> stages;
  private int currentStageIndex = -1;
  private final List<MissionListener> listeners = new ArrayList<>();
  private boolean isStarted = false;
  private SpacecraftState currentState;
  private NumericalPropagator propagator;

  private AbsoluteDate initialDate;

  private MissionStatus status = MissionStatus.DRAFT;
  private AbsoluteDate lastAltLogDate = null;
  private static final double ALT_LOG_PERIOD_S = 10.0;

  /**
   * Creates a new mission with the specified name, vehicle, stages, and objective.
   *
   * @param name the human-readable name of the mission
   * @param vehicle the vehicle used for the mission
   * @param stages the ordered list of mission stages to execute
   * @param objective the mission objective used for evaluation
   */
  public Mission(
      String name, Vehicle vehicle, List<MissionStage> stages, MissionObjective objective) {
    this.name = name;
    this.vehicle = vehicle;
    this.objective = objective;
    this.stages = stages;
  }

  /**
   * Computes the initial spacecraft state for the given launch date. Subclasses define the launch
   * site, orbital parameters, and initial mass based on the vehicle configuration.
   *
   * @param initialDate the launch date and time
   * @return the initial spacecraft state at the launch site
   */
  public abstract SpacecraftState getInitialState(AbsoluteDate initialDate);

  /**
   * Starts the mission at the given date, computing the initial state and transitioning to the
   * first stage.
   *
   * @param initialDate the launch date and time
   * @throws IllegalStateException if the mission has already been started
   */
  public void start(AbsoluteDate initialDate) {
    if (currentStageIndex != -1) {
      throw new IllegalStateException("Mission already started");
    }
    this.initialDate = initialDate;
    this.currentState = getInitialState(initialDate);
    isStarted = true;
    status = MissionStatus.RUNNING;
    transitionToNextStage(currentState);
  }

  /**
   * Advances the mission simulation to the given time by propagating the current stage.
   * Periodically logs altitude and orbital information for diagnostics.
   *
   * @param currentTime the simulation time to propagate to
   */
  public void update(AbsoluteDate currentTime) {
    if (!isStarted || isFinished()) {
      return;
    }
    MissionStage currentStage = stages.get(currentStageIndex);
    currentState = propagator.propagate(currentTime);
    if (shouldLogAltitude(currentState.getDate())) {
      double altitudeM = computeAltitudeMeters(currentState);
      logger.info(
          "t={} stage='{}' alt={} orbit=[{}], mass={}, speed={}",
          currentState.getDate(),
          currentStage.getName(),
          String.format("%.1f m", altitudeM),
          new KeplerianOrbit(currentState.getOrbit()),
          currentState.getMass(),
          currentState.getVelocity().getNorm());
      lastAltLogDate = currentState.getDate();
    }
  }

  private boolean shouldLogAltitude(AbsoluteDate date) {
    return lastAltLogDate == null || date.durationFrom(lastAltLogDate) >= ALT_LOG_PERIOD_S;
  }

  /**
   * Computes the geodetic altitude of the spacecraft above the Earth's surface.
   *
   * @param state the spacecraft state to evaluate
   * @return the altitude in meters above the WGS84 reference ellipsoid
   */
  public double computeAltitudeMeters(SpacecraftState state) {
    OneAxisEllipsoid earth = OrekitService.get().getEarthEllipsoid();
    GeodeticPoint gp = earth.transform(state.getPosition(), state.getFrame(), state.getDate());
    return gp.getAltitude();
  }

  /**
   * Transitions the mission to the next stage. Creates a new propagator, notifies listeners, and
   * configures the new stage. If the stage is an {@link OptimizableMissionStage} with a saved entry
   * state, that state is used to ensure reproducibility with the optimization results.
   *
   * @param stateAtEvent the spacecraft state at the moment of transition
   */
  public void transitionToNextStage(SpacecraftState stateAtEvent) {
    currentStageIndex++;
    if (isFinished()) {
      this.currentState = stateAtEvent;
      this.status = MissionStatus.COMPLETED;
      return;
    }
    this.currentState = stateAtEvent;
    MissionStage newStage = stages.get(currentStageIndex);
    propagator = OrekitService.get().createOptimizationPropagator();
    listeners.forEach(listener -> listener.onStageTransition(this, stateAtEvent));
    this.currentState = newStage.enter(currentState, this);

    // If this is an optimizable stage with a saved entry state, use it
    // to ensure perfect reproducibility with the optimization
    if (newStage instanceof OptimizableMissionStage<?> opt && opt.getEntryState() != null) {
      this.currentState = opt.getEntryState();
    }

    propagator.setInitialState(this.currentState);
    newStage.configure(propagator, this);
  }

  /**
   * Registers a listener to be notified of mission events such as stage transitions.
   *
   * @param listener the listener to register
   */
  public void addListener(MissionListener listener) {
    listeners.add(listener);
  }

  /**
   * Returns whether all stages have been completed.
   *
   * @return {@code true} if the mission has finished all stages
   */
  public boolean isFinished() {
    return currentStageIndex >= stages.size();
  }

  /**
   * Returns whether the mission has been started.
   *
   * @return {@code true} if {@link #start(AbsoluteDate)} has been called
   */
  public boolean isStarted() {
    return isStarted;
  }

  /**
   * Returns whether the mission is currently in progress (started but not yet finished).
   *
   * @return {@code true} if the mission is actively executing stages
   */
  public boolean isOnGoing() {
    return isStarted && !isFinished();
  }

  /**
   * Returns the name of this mission.
   *
   * @return the mission name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the vehicle assigned to this mission.
   *
   * @return the mission vehicle
   */
  public Vehicle getVehicle() {
    return vehicle;
  }

  /**
   * Returns the objective that this mission is trying to achieve.
   *
   * @return the mission objective
   */
  public MissionObjective getObjective() {
    return objective;
  }

  /**
   * Returns the current spacecraft state in the simulation.
   *
   * @return the most recent propagated spacecraft state
   */
  public SpacecraftState getCurrentState() {
    return currentState;
  }

  /**
   * Returns the numerical propagator currently being used by the active stage.
   *
   * @return the active numerical propagator
   */
  public NumericalPropagator getPropagator() {
    return propagator;
  }

  /**
   * Returns the date at which the mission was started.
   *
   * @return the mission start date
   */
  public AbsoluteDate getInitialDate() {
    return initialDate;
  }

  /**
   * Replaces the current spacecraft state. Used to inject externally computed states such as
   * parking orbit states.
   *
   * @param parkingState the new spacecraft state to set
   */
  public void setCurrentState(SpacecraftState parkingState) {
    this.currentState = parkingState;
  }

  /**
   * Returns the ordered list of all stages in this mission.
   *
   * @return the list of mission stages
   */
  public List<MissionStage> getStages() {
    return stages;
  }

  /**
   * Returns the stage currently being executed.
   *
   * @return the current mission stage
   * @throws com.smousseur.orbitlab.core.OrbitlabException if the mission has not been started or is
   *     already finished
   */
  public MissionStage getCurrentStage() {
    if (currentStageIndex < 0) {
      throw new OrbitlabException("Mission has not been started yet");
    }
    if (isFinished()) {
      throw new OrbitlabException("Mission is already finished, no current stage");
    }
    return stages.get(currentStageIndex);
  }

  /**
   * Gets status.
   *
   * @return the status
   */
  public MissionStatus getStatus() {
    return status;
  }

  /**
   * Sets status.
   *
   * @param status the status
   */
  public void setStatus(MissionStatus status) {
    this.status = status;
  }
}
