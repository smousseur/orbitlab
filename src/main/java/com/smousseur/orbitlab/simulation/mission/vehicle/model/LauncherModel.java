package com.smousseur.orbitlab.simulation.mission.vehicle.model;

import com.smousseur.orbitlab.simulation.mission.vehicle.*;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Named launcher model: capability-typed stages plus flight-profile parameters. Single source of
 * truth for the launcher catalog ({@link Launchers}).
 *
 * @param id the catalog key (e.g. "FALCON_HEAVY"), used by the mission wizard
 * @param displayName the human-readable name (e.g. "Falcon Heavy")
 * @param stages the stage models, bottom to top
 * @param ascentProfile the flight-profile parameters imposed by this launcher
 */
public record LauncherModel(
        String id, String displayName, List<StageModel> stages, AscentProfile ascentProfile) {

  public LauncherModel {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(stages, "stages");
    Objects.requireNonNull(ascentProfile, "ascentProfile");
    if (stages.isEmpty()) {
      throw new IllegalArgumentException("a launcher model needs at least one stage");
    }
    stages = List.copyOf(stages);
  }

  /**
   * Instantiates the vehicle stack with mission-specific propellant loads.
   *
   * @param propellantLoads the propellant load per stage (kg), same order as {@link #stages()}
   * @param payload the payload placed on top of the stack
   * @return the assembled vehicle stack, bottom stage first, payload last
   */
  public VehicleStack instantiate(double[] propellantLoads, Spacecraft payload) {
    Objects.requireNonNull(propellantLoads, "propellantLoads");
    Objects.requireNonNull(payload, "payload");
    if (propellantLoads.length != stages.size()) {
      throw new IllegalArgumentException(
          "expected " + stages.size() + " propellant loads, got " + propellantLoads.length);
    }
    List<Vehicle> vehicles = new ArrayList<>(stages.size() + 1);
    for (int i = 0; i < stages.size(); i++) {
      vehicles.add(stages.get(i).toVehicle(propellantLoads[i]));
    }
    vehicles.add(payload);
    return new VehicleStack(List.copyOf(vehicles));
  }

  /**
   * Instantiates the vehicle stack with every stage loaded at full capacity (historical
   * behaviour).
   *
   * @param payload the payload placed on top of the stack
   * @return the assembled vehicle stack, bottom stage first, payload last
   */
  public VehicleStack instantiateFullyLoaded(Spacecraft payload) {
    double[] loads = stages.stream().mapToDouble(StageModel::propellantCapacity).toArray();
    return instantiate(loads, payload);
  }
}
