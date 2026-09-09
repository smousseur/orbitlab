package com.smousseur.orbitlab.simulation.mission.vehicle.model;

import com.smousseur.orbitlab.simulation.mission.vehicle.*;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagingPlan;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.IgnitionMode;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
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
 * @param heightMeters the height of the assembled vehicle on the pad, fairing included (m). A
 *     physical figure of the launcher and not a drawing parameter — which is why it lives here
 *     rather than beside the mesh in {@code LauncherAssets}: it stays true whether or not a mesh
 *     exists. The rendering layer converts it (spec {@code docs/etagement/01-decoupage.md} §3.8),
 *     the propagation ignores it.
 */
public record LauncherModel(
    String id,
    String displayName,
    List<StageModel> stages,
    AscentProfile ascentProfile,
    double heightMeters) {

  /**
   * Height given to a launcher that declares none, which is the Falcon Heavy's.
   *
   * <p>Not the 100 m every launcher used to be drawn at. The only path reading this default at
   * runtime is the legacy {@code MissionEntry(Mission)} one, whose missions fly a fully loaded
   * Falcon Heavy and are drawn with {@code LauncherAssets.DEFAULT_MODEL_PATH} — the Falcon Heavy's
   * mesh. Drawing that mesh at any other height would be a knowingly wrong number.
   */
  public static final double DEFAULT_HEIGHT_METERS = 70.0;

  /** A launcher stating no height, which is every hand-assembled fixture: none is ever drawn. */
  public LauncherModel(
      String id, String displayName, List<StageModel> stages, AscentProfile ascentProfile) {
    this(id, displayName, stages, ascentProfile, DEFAULT_HEIGHT_METERS);
  }

  public LauncherModel {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(stages, "stages");
    Objects.requireNonNull(ascentProfile, "ascentProfile");
    if (stages.isEmpty()) {
      throw new IllegalArgumentException("a launcher model needs at least one stage");
    }
    if (!(heightMeters > 0)) {
      throw new IllegalArgumentException("heightMeters must be positive: " + heightMeters);
    }
    stages = List.copyOf(stages);
    StagingPlan.checkStructure(stages, ascentProfile);
  }

  /**
   * The thrust this launcher leaves the pad with: the sum over every ground-lit stage.
   *
   * <p>Reading the bottom stage alone was the same number until {@code PHY-8 / L2} split the Falcon
   * Heavy's three cores into two entries; it would now report the two strap-ons, 15.2 MN instead of
   * 22.8 (spec {@code docs/etagement/04-conception-L2.md} §3.4). Summing the ground-lit stages is
   * the grandeur a reader actually compares between launchers, and it stays right whatever the
   * catalog does to its staging afterwards.
   *
   * <p><b>A throttled core leaves the pad throttled.</b> The sum is taken at the thrust each stage
   * actually applies, so a launcher holding its core at {@code f} during the shared phase reports
   * what it produces rather than what it has installed — the field is named after an instant of
   * flight, and it is the one figure of the wizard card a reader can check against the trajectory
   * (spec {@code docs/etagement/05-conception-L3.md} §3.4). The factor is 1 on every launcher that
   * does not throttle.
   *
   * @return the lift-off thrust in newtons
   */
  public double liftOffThrust() {
    return stages.stream()
        .filter(stage -> stage.capabilities().ignition() == IgnitionMode.GROUND)
        .mapToDouble(stage -> stage.propulsion().thrust() * throttleOf(stage))
        .sum();
  }

  /**
   * The fraction of its thrust a ground-lit stage applies at lift-off. Only the core is throttled,
   * and a profile declaring a throttle without a booster stage to share the phase with is refused
   * by the constructor — so the factor is 1 on a sequential launcher without testing for one.
   */
  private double throttleOf(StageModel stage) {
    return stage.capabilities().role() == StageRole.CORE ? ascentProfile.coreThrottle() : 1.0;
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
    return new VehicleStack(
        List.copyOf(vehicles), StagingPlan.forLauncher(stages, propellantLoads, ascentProfile));
  }

  /**
   * Instantiates the vehicle stack with every stage loaded at full capacity (historical behaviour).
   *
   * @param payload the payload placed on top of the stack
   * @return the assembled vehicle stack, bottom stage first, payload last
   */
  public VehicleStack instantiateFullyLoaded(Spacecraft payload) {
    double[] loads = stages.stream().mapToDouble(StageModel::propellantCapacity).toArray();
    return instantiate(loads, payload);
  }
}
