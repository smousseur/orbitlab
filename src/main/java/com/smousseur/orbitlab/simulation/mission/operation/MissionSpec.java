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
   * LEO insertion spec. A circular target has {@code perigeeAltitude == apogeeAltitude}; an elliptic
   * target keeps a distinct apogee.
   *
   * @param name the mission name
   * @param configuration the launch configuration
   * @param perigeeAltitude the target perigee altitude in meters
   * @param apogeeAltitude the target apogee altitude in meters
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   */
  record Leo(
      String name,
      LaunchConfiguration configuration,
      double perigeeAltitude,
      double apogeeAltitude,
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
  }

  /**
   * GEO insertion spec (parking orbit → GTO → geostationary).
   *
   * @param name the mission name
   * @param configuration the launch configuration
   * @param parkingAltitude the parking orbit altitude in meters
   * @param targetAltitude the geostationary target altitude in meters
   * @param finalInclination the target final inclination in degrees
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
  }
}
