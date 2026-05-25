package com.smousseur.orbitlab.simulation.mission;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.vehicle.LauncherType;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import java.util.ArrayList;
import java.util.List;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

/**
 * Abstract base class representing a space mission composed of sequential stages.
 *
 * <p>A mission holds the configuration (name, vehicle, stages, objective) and provides the initial
 * spacecraft state. The actual trajectory computation is handled by {@link
 * com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer} and {@link
 * com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisGenerator}.
 */
public abstract class Mission {
  private final String name;
  private final Vehicle vehicle;
  private final MissionObjective objective;
  private final List<MissionStage> stages;
  private SpacecraftState currentState;
  private AbsoluteDate initialDate;
  private MissionStatus status = MissionStatus.DRAFT;

  /**
   * Creates a new mission with the specified name, vehicle, stages and objective.
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
   * Returns the default vehicle stack used when no explicit configuration is provided: the
   * {@link LauncherType#FALCON_HEAVY} stages topped with the default payload.
   *
   * @return the default vehicle stack
   */
  public static VehicleStack getDefaultVehicle() {
    return buildVehicle(LauncherType.FALCON_HEAVY.toVehicleStack(), Spacecraft.defaultPayload());
  }

  /**
   * Assembles a {@link VehicleStack} from a launcher stack and a payload by flattening the
   * launcher's stages and appending the payload as the topmost element. The resulting stack
   * contains a flat list of vehicles (no nested {@link VehicleStack}), which is required by
   * {@link VehicleStack#resolveActiveStage(double)}.
   *
   * @param launcher the launcher stack (its stages will be flattened into the result)
   * @param payload the payload to place on top of the launcher
   * @return the assembled vehicle stack
   */
  public static VehicleStack buildVehicle(VehicleStack launcher, Vehicle payload) {
    List<Vehicle> stacked = new ArrayList<>(launcher.vehicles());
    stacked.add(payload);
    return new VehicleStack(stacked);
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

  public double computeAltitudeMeters() {
    return computeAltitudeMeters(currentState);
  }

  /**
   * Called by stage event handlers during ephemeris generation to update the current state. This
   * simplified version only sets the current state — no stage tracking or listener notification.
   *
   * @param stateAtEvent the spacecraft state at the moment of transition
   */
  public void transitionToNextStage(SpacecraftState stateAtEvent) {
    this.currentState = stateAtEvent;
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

  public void setCurrentState(SpacecraftState state) {
    this.currentState = state;
  }

  public List<MissionStage> getStages() {
    return stages;
  }

  public AbsoluteDate getInitialDate() {
    return initialDate;
  }

  public void setInitialDate(AbsoluteDate initialDate) {
    this.initialDate = initialDate;
  }

  public MissionStatus getStatus() {
    return status;
  }

  public void setStatus(MissionStatus status) {
    this.status = status;
  }
}
