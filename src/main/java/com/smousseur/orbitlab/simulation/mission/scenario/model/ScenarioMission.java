package com.smousseur.orbitlab.simulation.mission.scenario.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.smousseur.orbitlab.simulation.mission.MissionType;

/**
 * One saved mission, sealed on <b>the same hierarchy as {@code MissionSpec}</b> — the same
 * branches, discriminated by the {@code type} the spec already reports (spec {@code
 * docs/scenario/01-persistance-missions.md} §3).
 *
 * <p>The mirror is on the hierarchy, not on the components, which differ deliberately: wizard units
 * rather than spec units (kilometres, degrees, days), a three-field {@link ScenarioVehicle} rather
 * than a resolved {@code LaunchConfiguration}, and five values that come from {@code MissionEntry}
 * and have no spec component at all. What it buys is elsewhere: {@code ScenarioMapper} switches
 * exhaustively with no default branch, so the day a new {@code MissionSpec} branch appears, the
 * compilation fails until the matching record exists here. A new mission type cannot silently
 * become unpersistable — which is what happened to the lunar branch, flagged by MIS-4 / L4 §10 pt 5
 * and filled by L5.
 *
 * <p><b>Absence is meaningful</b> and stays so (§3.1, rule 1): {@link #horizonDays()}, and the
 * inclination and node of an {@link EarthOrbit}, are {@code null} — hence omitted from the JSON —
 * when they were never commanded, never written at their derived value. Publishing a derived
 * inclination would move the azimuth by thousandths of a degree, hence the signed launch assist,
 * hence every propellant load: a trajectory drift no assertion on the inclination would catch.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ScenarioMission.EarthOrbit.class, name = "LEO"),
  @JsonSubTypes.Type(value = ScenarioMission.Geo.class, name = "GEO"),
  @JsonSubTypes.Type(value = ScenarioMission.Lunar.class, name = "LUNAR_FLYBY")
})
public sealed interface ScenarioMission
    permits ScenarioMission.EarthOrbit, ScenarioMission.Geo, ScenarioMission.Lunar {

  /**
   * @return the mission type, which is also the JSON discriminator
   */
  MissionType type();

  /**
   * @return the mission name
   */
  String name();

  /**
   * @return the launch date in ISO UTC, or {@code null} for a mission that was never scheduled
   */
  String launchDate();

  /**
   * @return the launch site geometry and its display name
   */
  ScenarioSite site();

  /**
   * @return the launcher and payload, by catalog id
   */
  ScenarioVehicle vehicle();

  /**
   * @return the forced restitution horizon in days, or {@code null} when the mission is on "auto"
   */
  Double horizonDays();

  /**
   * @return the {@code AtmosphereModel} name the mission was flown against
   */
  String atmosphere();

  /**
   * @return the {@code OptimizationType} name; it drives the stage composition, hence the
   *     trajectory
   */
  String optimizationMode();

  /**
   * @return the trajectory colour as {@code #RRGGBB}
   */
  String color();

  /**
   * @return whether the mission was shown in the 3D scene
   */
  boolean visible();

  /**
   * @return what the optimization cost to find, or {@code null} for a mission that never flew
   */
  ScenarioSolution solution();

  /**
   * An Earth-orbit mission, whatever its plane.
   *
   * <p><b>Reading trap</b>: the branch is named {@code EarthOrbit} and its discriminator reads
   * {@code "LEO"}, because {@code MissionSpec.EarthOrbit.type()} returns {@link MissionType#LEO}.
   * That is a naming legacy of the model, not an inconsistency of the format.
   *
   * @param type always {@link MissionType#LEO}
   * @param name the mission name
   * @param launchDate the launch date in ISO UTC, or {@code null}
   * @param site the launch site
   * @param vehicle the launcher and payload
   * @param horizonDays the forced horizon in days, or {@code null} for "auto"
   * @param atmosphere the atmosphere model name
   * @param optimizationMode the optimization mode name
   * @param color the trajectory colour as {@code #RRGGBB}
   * @param visible whether the mission was displayed
   * @param solution the optimization outcome, or {@code null}
   * @param perigeeKm the target perigee altitude in kilometres
   * @param apogeeKm the target apogee altitude in kilometres
   * @param inclinationDeg the commanded inclination in degrees, or {@code null} when the mission
   *     flies the plane its site gives for free
   * @param raanDeg the commanded ascending node in degrees, or {@code null} when the mission waits
   *     for no plane
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record EarthOrbit(
      MissionType type,
      String name,
      String launchDate,
      ScenarioSite site,
      ScenarioVehicle vehicle,
      Double horizonDays,
      String atmosphere,
      String optimizationMode,
      String color,
      boolean visible,
      ScenarioSolution solution,
      double perigeeKm,
      double apogeeKm,
      Double inclinationDeg,
      Double raanDeg)
      implements ScenarioMission {}

  /**
   * A GEO mission: parking orbit, GTO, then circularization by the payload's kick motor.
   *
   * @param type always {@link MissionType#GEO}
   * @param name the mission name
   * @param launchDate the launch date in ISO UTC, or {@code null}
   * @param site the launch site
   * @param vehicle the launcher and payload
   * @param horizonDays the forced horizon in days, or {@code null} for "auto"
   * @param atmosphere the atmosphere model name
   * @param optimizationMode the optimization mode name
   * @param color the trajectory colour as {@code #RRGGBB}
   * @param visible whether the mission was displayed
   * @param solution the optimization outcome, or {@code null}
   * @param parkingKm the parking orbit altitude in kilometres
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Geo(
      MissionType type,
      String name,
      String launchDate,
      ScenarioSite site,
      ScenarioVehicle vehicle,
      Double horizonDays,
      String atmosphere,
      String optimizationMode,
      String color,
      boolean visible,
      ScenarioSolution solution,
      double parkingKm)
      implements ScenarioMission {}

  /**
   * A lunar flyby: ascent, parking orbit, translunar injection, and a pass at the Moon.
   *
   * <p><b>The parking altitude is not persisted</b>, deliberately: it is {@code
   * LunarFlybyMission.DEFAULT_PARKING_ALTITUDE}, no wizard field carries it, and writing it down
   * would create a second truth about the same number the day the constant moves (MIS-4 / L5 §6.2).
   *
   * @param type always {@link MissionType#LUNAR_FLYBY}
   * @param name the mission name
   * @param launchDate the launch date in ISO UTC, or {@code null}
   * @param site the launch site
   * @param vehicle the launcher and payload
   * @param horizonDays the forced horizon in days, or {@code null} for "auto"
   * @param atmosphere the atmosphere model name
   * @param optimizationMode the optimization mode name
   * @param color the trajectory colour as {@code #RRGGBB}
   * @param visible whether the mission was displayed
   * @param solution the optimization outcome, or {@code null}
   * @param periluneKm the perilune altitude aimed for, in kilometres
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Lunar(
      MissionType type,
      String name,
      String launchDate,
      ScenarioSite site,
      ScenarioVehicle vehicle,
      Double horizonDays,
      String atmosphere,
      String optimizationMode,
      String color,
      boolean visible,
      ScenarioSolution solution,
      double periluneKm)
      implements ScenarioMission {}
}
