package com.smousseur.orbitlab.simulation.mission.vehicle;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;

import java.util.Objects;

/**
 * Mission-ready assembly of a launcher model, per-stage propellant loads and a payload. Missions
 * consume this instead of a raw {@link Vehicle} so the launcher's flight-profile parameters ({@link
 * AscentProfile}) travel with the stack they belong to.
 *
 * @param launcher the launcher model from the catalog
 * @param propellantLoads the propellant load per stage (kg), same order as the launcher stages
 * @param payload the payload placed on top of the stack
 * @param payloadId the {@code Payloads} catalog id the payload was instantiated from, or {@code
 *     null} when it was assembled by hand
 */
public record LaunchConfiguration(
    LauncherModel launcher, double[] propellantLoads, Spacecraft payload, String payloadId) {

  public LaunchConfiguration {
    Objects.requireNonNull(launcher, "launcher");
    Objects.requireNonNull(propellantLoads, "propellantLoads");
    Objects.requireNonNull(payload, "payload");
    if (propellantLoads.length != launcher.stages().size()) {
      throw new IllegalArgumentException(
          "expected "
              + launcher.stages().size()
              + " propellant loads, got "
              + propellantLoads.length);
    }
    propellantLoads = propellantLoads.clone();
  }

  /**
   * Configuration for a payload with no catalog origin — tests and any programmatic path assembling
   * a {@link Spacecraft} directly. The launcher keeps its identity through {@link
   * LauncherModel#id()}; the payload simply has none to give.
   *
   * @param launcher the launcher model from the catalog
   * @param propellantLoads the propellant load per stage (kg), same order as the launcher stages
   * @param payload the payload placed on top of the stack
   */
  public LaunchConfiguration(LauncherModel launcher, double[] propellantLoads, Spacecraft payload) {
    this(launcher, propellantLoads, payload, null);
  }

  @Override
  public double[] propellantLoads() {
    return propellantLoads.clone();
  }

  /**
   * Reports whether this configuration remembers which catalog model its payload came from. Only
   * the wizard path sets it, and it is what lets the wizard reopen prefilled on the very payload
   * the user picked: the {@link Spacecraft} alone does not identify its model — two inert models
   * differ only by a default dry mass the user overrides.
   *
   * @return {@code true} when {@link #payloadId()} is usable
   */
  public boolean hasPayloadId() {
    return payloadId != null && !payloadId.isBlank();
  }

  /** Configuration with every stage loaded at full capacity (historical behaviour). */
  public static LaunchConfiguration fullyLoaded(LauncherModel launcher, Spacecraft payload) {
    double[] loads =
        launcher.stages().stream().mapToDouble(StageModel::propellantCapacity).toArray();
    return new LaunchConfiguration(launcher, loads, payload);
  }

  /** Assembles the vehicle stack for this configuration. */
  public VehicleStack toVehicleStack() {
    return launcher.instantiate(propellantLoads, payload);
  }

  /** Returns the flight-profile parameters imposed by the launcher. */
  public AscentProfile ascentProfile() {
    return launcher.ascentProfile();
  }
}
