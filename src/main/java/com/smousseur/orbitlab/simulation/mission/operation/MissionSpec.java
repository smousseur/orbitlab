package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import java.util.Objects;

/**
 * Immutable description of the mission the user configured in the wizard, independent of how it will
 * be flown. It captures the <em>what</em> — targets, vehicle configuration, launch site — and
 * deliberately omits the <em>how</em>: the stage decomposition, which is a function of the chosen
 * {@link OptimizationType optimization mode} and is resolved later by {@link MissionComposer}.
 *
 * <p>Splitting the spec from the built {@link com.smousseur.orbitlab.simulation.mission.Mission}
 * lets the optimization toggle recompose the stages (analytic ↔ CMA-ES) after the wizard closes,
 * instead of freezing one composition at creation time.
 */
public sealed interface MissionSpec permits MissionSpec.Leo, MissionSpec.Geo {

  /**
   * @return the human-readable mission name
   */
  String name();

  /**
   * @return the launcher model, propellant loads and payload
   */
  LaunchConfiguration configuration();

  /**
   * Returns the launch site's display name as picked in the wizard, e.g. {@code "Kourou - French
   * Guiana"}. Purely descriptive: nothing in the composition or the propagation reads it, the site
   * geometry is carried by {@link #latitude()}, {@link #longitude()} and {@link #altitude()}.
   *
   * <p>Nullable, because a spec built outside the wizard — tests, or any programmatic path
   * assembling raw coordinates — has no name to give. Callers check {@link #hasSiteName()} rather
   * than the value.
   *
   * @return the launch site name, or {@code null} when the site is unnamed
   */
  String siteName();

  /**
   * @return {@code true} when this spec carries a usable launch site name
   */
  default boolean hasSiteName() {
    return siteName() != null && !siteName().isBlank();
  }

  /**
   * @return the launch site latitude in degrees
   */
  double latitude();

  /**
   * @return the launch site longitude in degrees
   */
  double longitude();

  /**
   * @return the launch site altitude in meters
   */
  double altitude();

  /**
   * @return the mission type this spec describes
   */
  MissionType type();

  /**
   * Returns a copy of this spec with the launcher's per-stage propellant loads replaced, keeping the
   * launcher model and the payload (including a GEO payload's fixed AKM load) unchanged. Used by the
   * propellant-sizing planner to rebuild the mission at each candidate load array.
   *
   * @param launcherLoads the per-stage launcher loads (kg), same order as the launcher stages
   * @return a spec identical to this one but flying the given launcher loads
   */
  MissionSpec withLauncherLoads(double[] launcherLoads);

  /**
   * LEO insertion spec. A circular target has {@code perigeeAltitude == apogeeAltitude}; an elliptic
   * target keeps a distinct apogee.
   *
   * @param name the mission name
   * @param configuration the launch configuration
   * @param perigeeAltitude the target perigee altitude in meters
   * @param apogeeAltitude the target apogee altitude in meters
   * @param siteName the launch site display name, or {@code null} when unnamed
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   */
  record Leo(
      String name,
      LaunchConfiguration configuration,
      double perigeeAltitude,
      double apogeeAltitude,
      String siteName,
      double latitude,
      double longitude,
      double altitude)
      implements MissionSpec {
    public Leo {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    public MissionType type() {
      return MissionType.LEO;
    }

    @Override
    public MissionSpec withLauncherLoads(double[] launcherLoads) {
      return new Leo(
          name,
          new LaunchConfiguration(
              configuration.launcher(),
              launcherLoads,
              configuration.payload(),
              configuration.payloadId()),
          perigeeAltitude,
          apogeeAltitude,
          siteName,
          latitude,
          longitude,
          altitude);
    }
  }

  /**
   * GEO insertion spec (parking orbit → GTO → geostationary).
   *
   * @param name the mission name
   * @param configuration the launch configuration
   * @param parkingAltitude the parking orbit altitude in meters
   * @param targetAltitude the geostationary target altitude in meters
   * @param finalInclination the target final inclination in degrees
   * @param siteName the launch site display name, or {@code null} when unnamed
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   */
  record Geo(
      String name,
      LaunchConfiguration configuration,
      double parkingAltitude,
      double targetAltitude,
      double finalInclination,
      String siteName,
      double latitude,
      double longitude,
      double altitude)
      implements MissionSpec {
    public Geo {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    public MissionType type() {
      return MissionType.GEO;
    }

    @Override
    public MissionSpec withLauncherLoads(double[] launcherLoads) {
      return new Geo(
          name,
          new LaunchConfiguration(
              configuration.launcher(),
              launcherLoads,
              configuration.payload(),
              configuration.payloadId()),
          parkingAltitude,
          targetAltitude,
          finalInclination,
          siteName,
          latitude,
          longitude,
          altitude);
    }
  }
}
