package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.mission.MissionHorizon;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable description of the mission the user configured in the wizard, independent of how it
 * will be flown. It captures the <em>what</em> — targets, vehicle configuration, launch site — and
 * deliberately omits the <em>how</em>: the stage decomposition, which is a function of the chosen
 * {@link OptimizationType optimization mode} and is resolved later by {@link MissionComposer}.
 *
 * <p>Splitting the spec from the built {@link com.smousseur.orbitlab.simulation.mission.Mission}
 * lets the optimization toggle recompose the stages (analytic ↔ CMA-ES) after the wizard closes,
 * instead of freezing one composition at creation time.
 */
public sealed interface MissionSpec permits MissionSpec.EarthOrbit, MissionSpec.Geo {

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
   * Returns the restitution horizon: how far past insertion the mission is sampled and displayed
   * (spec {@code docs/mission-horizon/01-horizon-explicite.md}). Never {@code null} — a spec built
   * without one falls back to {@link MissionHorizon#defaultFor(MissionType)}.
   *
   * <p>It lives on the spec rather than on the built mission because it is <em>user intent</em>: it
   * must survive the recompositions that {@code MissionEntry} performs on a mode toggle or a wizard
   * edit, both of which replace the {@link com.smousseur.orbitlab.simulation.mission.Mission}.
   *
   * @return the mission horizon
   */
  MissionHorizon horizon();

  /**
   * Returns the atmosphere this mission is flown against (spec {@code
   * docs/atmosphere/04-conception-L1.md} §3.2). Never {@code null} — a spec built without one falls
   * back to {@link AtmosphereModel#NONE}, which mounts no drag force at all.
   *
   * <p>It lives on the spec for the same reason {@link #horizon()} does: it is <em>user intent</em>,
   * and it must survive the recompositions {@code MissionEntry} performs on a mode toggle or a
   * wizard edit, both of which replace the {@link com.smousseur.orbitlab.simulation.mission.Mission}
   * and neither of which replaces the spec.
   *
   * @return the atmosphere model
   */
  AtmosphereModel atmosphere();

  /**
   * Returns a copy of this spec with the launcher's per-stage propellant loads replaced, keeping
   * the launcher model and the payload (including a GEO payload's fixed AKM load) unchanged. Used
   * by the propellant-sizing planner to rebuild the mission at each candidate load array.
   *
   * @param launcherLoads the per-stage launcher loads (kg), same order as the launcher stages
   * @return a spec identical to this one but flying the given launcher loads
   */
  MissionSpec withLauncherLoads(double[] launcherLoads);

  /**
   * Earth-orbit insertion spec: any orbit reached by an ascent followed by a transfer, whatever its
   * plane. A circular target has {@code perigeeAltitude == apogeeAltitude}; an elliptic target keeps
   * a distinct apogee. A polar or sun-synchronous target is this same record with another
   * inclination — no dedicated mission type, no dedicated objective (spec {@code
   * docs/earth-orbit/01-mission-terre-parametrable.md} §3.2 and §5).
   *
   * <p><b>The eccentricity stays implicit.</b> The (perigee, apogee) pair already carries it, and
   * {@link MissionComposer} reads that pair to choose between the circular and the elliptic
   * composition. A third, redundant parameter would be a source of inconsistency rather than a
   * generalisation.
   *
   * <p><b>The compact constructor validates</b> (spec §8): an inclination the site cannot reach, or
   * an apogee below the perigee, is refused here — at construction, where the caller still knows
   * what it asked for — instead of surfacing as a mission that propagates into something else.
   *
   * @param name the mission name
   * @param configuration the launch configuration
   * @param perigeeAltitude the target perigee altitude in meters
   * @param apogeeAltitude the target apogee altitude in meters
   * @param targetInclination the target orbit inclination in <b>radians</b>, in the frame {@link
   *     LaunchPlane#inclinationFrame()} declares
   * @param nodeBranch which of the two azimuths reaching that inclination is flown; {@code null} is
   *     normalised to {@link NodeBranch#ASCENDING}
   * @param targetRaan the right ascension of the target plane's ascending node in <b>degrees</b>,
   *     or {@code null} when the mission waits for no plane — read through {@link
   *     #hasTargetRaan()}
   * @param siteName the launch site display name, or {@code null} when unnamed
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   * @param horizon the restitution horizon, or {@code null} for the derived default
   * @param atmosphere the atmosphere to fly against, or {@code null} for {@link
   *     AtmosphereModel#NONE}
   */
  record EarthOrbit(
      String name,
      LaunchConfiguration configuration,
      double perigeeAltitude,
      double apogeeAltitude,
      double targetInclination,
      NodeBranch nodeBranch,
      Double targetRaan,
      String siteName,
      double latitude,
      double longitude,
      double altitude,
      MissionHorizon horizon,
      AtmosphereModel atmosphere)
      implements MissionSpec {
    public EarthOrbit {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(configuration, "configuration");
      // Normalised here rather than at every call site: a spec assembled by hand (tests, any
      // programmatic path) gets the derived default without having to know it exists.
      if (horizon == null) {
        horizon = MissionHorizon.defaultFor(MissionType.LEO);
      }
      if (atmosphere == null) {
        atmosphere = AtmosphereModel.NONE;
      }
      if (nodeBranch == null) {
        nodeBranch = NodeBranch.ASCENDING;
      }
      if (apogeeAltitude < perigeeAltitude) {
        throw new OrbitlabException(
            String.format(
                Locale.ROOT,
                "Target apogee %.0f m is below the target perigee %.0f m",
                apogeeAltitude,
                perigeeAltitude));
      }
      new LaunchPlane(targetInclination, nodeBranch).requireReachableFrom(latitude);
      if (targetRaan != null && !Double.isFinite(targetRaan)) {
        throw new OrbitlabException("Target RAAN is not a number: " + targetRaan);
      }
    }

    /**
     * The form every call site that predates MIS-2 means: no target plane to wait for, so the
     * mission launches when it was told to.
     *
     * @param name the mission name
     * @param configuration the launch configuration
     * @param perigeeAltitude the target perigee altitude in meters
     * @param apogeeAltitude the target apogee altitude in meters
     * @param targetInclination the target orbit inclination in radians
     * @param nodeBranch which of the two azimuths is flown
     * @param siteName the launch site display name, or {@code null} when unnamed
     * @param latitude the launch site latitude in degrees
     * @param longitude the launch site longitude in degrees
     * @param altitude the launch site altitude in meters
     * @param horizon the restitution horizon, or {@code null} for the derived default
     */
    public EarthOrbit(
        String name,
        LaunchConfiguration configuration,
        double perigeeAltitude,
        double apogeeAltitude,
        double targetInclination,
        NodeBranch nodeBranch,
        String siteName,
        double latitude,
        double longitude,
        double altitude,
        MissionHorizon horizon) {
      this(
          name,
          configuration,
          perigeeAltitude,
          apogeeAltitude,
          targetInclination,
          nodeBranch,
          null,
          siteName,
          latitude,
          longitude,
          altitude,
          horizon,
          null);
    }

    /**
     * The form every call site that predates PHY-1 means: no atmosphere, so the mission flies in
     * vacuum. Shaped exactly like the {@code targetRaan}-less form above — a parameter a caller has
     * no opinion on is one it does not have to name.
     *
     * @param name the mission name
     * @param configuration the launch configuration
     * @param perigeeAltitude the target perigee altitude in meters
     * @param apogeeAltitude the target apogee altitude in meters
     * @param targetInclination the target orbit inclination in radians
     * @param nodeBranch which of the two azimuths is flown
     * @param targetRaan the target ascending node in degrees, or {@code null}
     * @param siteName the launch site display name, or {@code null} when unnamed
     * @param latitude the launch site latitude in degrees
     * @param longitude the launch site longitude in degrees
     * @param altitude the launch site altitude in meters
     * @param horizon the restitution horizon, or {@code null} for the derived default
     */
    public EarthOrbit(
        String name,
        LaunchConfiguration configuration,
        double perigeeAltitude,
        double apogeeAltitude,
        double targetInclination,
        NodeBranch nodeBranch,
        Double targetRaan,
        String siteName,
        double latitude,
        double longitude,
        double altitude,
        MissionHorizon horizon) {
      this(
          name,
          configuration,
          perigeeAltitude,
          apogeeAltitude,
          targetInclination,
          nodeBranch,
          targetRaan,
          siteName,
          latitude,
          longitude,
          altitude,
          horizon,
          null);
    }

    /**
     * Whether this mission is aiming at a plane that already exists, and therefore has a launch
     * window to wait for.
     *
     * <p>The absence is the common case and it is not a defect: an inclination alone is reached at
     * every instant of the day, so a mission that only names one launches whenever it likes. A RAAN
     * is what makes the launch date a consequence rather than a choice (MIS-2).
     *
     * @return {@code true} when a target node was asked for
     */
    public boolean hasTargetRaan() {
      return targetRaan != null;
    }

    /**
     * The historical shape: the plane a due-east launch from the site reaches for free, inclination
     * equal to the latitude. Every call site that predates MIS-7 means this one.
     *
     * @param name the mission name
     * @param configuration the launch configuration
     * @param perigeeAltitude the target perigee altitude in meters
     * @param apogeeAltitude the target apogee altitude in meters
     * @param siteName the launch site display name, or {@code null} when unnamed
     * @param latitude the launch site latitude in degrees
     * @param longitude the launch site longitude in degrees
     * @param altitude the launch site altitude in meters
     * @param horizon the restitution horizon, or {@code null} for the derived default
     * @return the spec, targeting the site's free plane
     */
    public static EarthOrbit dueEast(
        String name,
        LaunchConfiguration configuration,
        double perigeeAltitude,
        double apogeeAltitude,
        String siteName,
        double latitude,
        double longitude,
        double altitude,
        MissionHorizon horizon) {
      LaunchPlane plane = LaunchPlane.dueEast(latitude);
      return new EarthOrbit(
          name,
          configuration,
          perigeeAltitude,
          apogeeAltitude,
          plane.targetInclination(),
          plane.nodeBranch(),
          siteName,
          latitude,
          longitude,
          altitude,
          horizon);
    }

    /**
     * @return the target plane, as {@code EarthOrbitMission} and the composer consume it
     */
    public LaunchPlane launchPlane() {
      return new LaunchPlane(targetInclination, nodeBranch);
    }

    @Override
    public MissionType type() {
      return MissionType.LEO;
    }

    @Override
    public MissionSpec withLauncherLoads(double[] launcherLoads) {
      return new EarthOrbit(
          name,
          new LaunchConfiguration(
              configuration.launcher(),
              launcherLoads,
              configuration.payload(),
              configuration.payloadId()),
          perigeeAltitude,
          apogeeAltitude,
          targetInclination,
          nodeBranch,
          targetRaan,
          siteName,
          latitude,
          longitude,
          altitude,
          horizon,
          atmosphere);
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
   * @param horizon the restitution horizon, or {@code null} for the derived default
   * @param atmosphere the atmosphere to fly against, or {@code null} for {@link
   *     AtmosphereModel#NONE}
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
      double altitude,
      MissionHorizon horizon,
      AtmosphereModel atmosphere)
      implements MissionSpec {
    public Geo {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(configuration, "configuration");
      // See Leo: normalised here so a hand-assembled spec need not know the default exists.
      if (horizon == null) {
        horizon = MissionHorizon.defaultFor(MissionType.GEO);
      }
      if (atmosphere == null) {
        atmosphere = AtmosphereModel.NONE;
      }
    }

    /**
     * The form every call site that predates PHY-1 means: no atmosphere.
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
     * @param horizon the restitution horizon, or {@code null} for the derived default
     */
    public Geo(
        String name,
        LaunchConfiguration configuration,
        double parkingAltitude,
        double targetAltitude,
        double finalInclination,
        String siteName,
        double latitude,
        double longitude,
        double altitude,
        MissionHorizon horizon) {
      this(
          name,
          configuration,
          parkingAltitude,
          targetAltitude,
          finalInclination,
          siteName,
          latitude,
          longitude,
          altitude,
          horizon,
          null);
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
          altitude,
          horizon,
          atmosphere);
    }
  }
}
