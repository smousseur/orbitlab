package com.smousseur.orbitlab.simulation.mission;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

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

  private AbsoluteDate lastAltLogDate = null;
  private static final double ALT_LOG_PERIOD_S = 5.0;

  public Mission(
      String name, Vehicle vehicle, List<MissionStage> stages, MissionObjective objective) {
    this.name = name;
    this.vehicle = vehicle;
    this.objective = objective;
    this.stages = stages;
  }

  public abstract SpacecraftState getInitialState(AbsoluteDate initialDate);

  public void start(AbsoluteDate initialDate) {
    if (currentStageIndex != -1) {
      throw new IllegalStateException("Mission already started");
    }
    this.initialDate = initialDate;
    this.currentState = getInitialState(initialDate);
    isStarted = true;
    transitionToNextStage(currentState);
  }

  public void update(AbsoluteDate currentTime) {
    if (isFinished()) {
      return;
    }
    MissionStage currentStage = stages.get(currentStageIndex);
    currentState = propagator.propagate(currentTime);
    if (shouldLogAltitude(currentState.getDate())) {
      double altitudeM = computeAltitudeMeters(currentState);
      logger.info(
          "t={} stage='{}' alt={} eccentricity={} speed={}",
          currentState.getDate(),
          currentStage.getName(),
          String.format("%.1f m", altitudeM),
          currentState.getOrbit().getE(),
          currentState.getVelocity().getNorm());
      lastAltLogDate = currentState.getDate();
    }
  }

  private boolean shouldLogAltitude(AbsoluteDate date) {
    return lastAltLogDate == null || date.durationFrom(lastAltLogDate) >= ALT_LOG_PERIOD_S;
  }

  private double computeAltitudeMeters(SpacecraftState state) {
    OneAxisEllipsoid earth = OrekitService.get().getEarthEllipsoid();
    Frame bodyFrame = earth.getBodyFrame(); // typically ITRF
    GeodeticPoint gp = earth.transform(state.getPosition(), state.getFrame(), state.getDate());
    return gp.getAltitude();
  }

  public void transitionToNextStage(SpacecraftState stateAtEvent) {
    currentStageIndex++;
    if (isFinished()) {
      this.currentState = stateAtEvent;
      return;
    }
    this.currentState = stateAtEvent;
    propagator = OrekitService.get().createDefaultPropagator();
    propagator.setInitialState(stateAtEvent);
    listeners.forEach(listener -> listener.onStageTransition(this, stateAtEvent));
    MissionStage newStage = stages.get(currentStageIndex);
    this.currentState = newStage.enter(currentState, this);
    newStage.configure(propagator, this);
  }

  public void updateMass() {
    this.currentState = this.currentState.withMass(vehicle.getMass());
  }

  public void addListener(MissionListener listener) {
    listeners.add(listener);
  }

  public boolean isFinished() {
    return currentStageIndex >= stages.size();
  }

  public boolean isStarted() {
    return isStarted;
  }

  public boolean isOnGoing() {
    return isStarted && !isFinished();
  }

  public String getName() {
    return name;
  }

  public Vehicle getVehicle() {
    return vehicle;
  }

  public MissionObjective getObjective() {
    return objective;
  }

  public SpacecraftState getCurrentState() {
    return currentState;
  }

  public NumericalPropagator getPropagator() {
    return propagator;
  }

  public AbsoluteDate getInitialDate() {
    return initialDate;
  }

  public void setCurrentState(SpacecraftState parkingState) {
    this.currentState = parkingState;
  }

  public List<MissionStage> getStages() {
    return stages;
  }
}
