package com.smousseur.orbitlab.simulation.mission.window.problem;

/**
 * Everything the wizard's lunar timeline needs, and nothing the parameters step cannot know.
 *
 * <p><b>Five numbers, all of them available at that step.</b> The pad comes from the site step, the
 * parking altitude is {@code LunarFlybyMission.DEFAULT_PARKING_ALTITUDE}, and the perilune is the
 * one field the lunar panel offers. The vehicle and the mass at injection — {@link
 * LunarLaunchWindowProblem}'s two other inputs — are absent because they belong to its {@code
 * confirm()} alone, and the launcher is chosen a step later.
 *
 * <p><b>Two problems, as the creation has two searches</b>: due east first, {@link #toProblem()},
 * and the free azimuth {@link LaunchWindowPlanner} falls back to when due east offers nothing
 * flyable, {@link #toFreeAzimuthProblem()}.
 *
 * @param latitude the launch site latitude in degrees
 * @param longitude the launch site longitude in degrees
 * @param altitude the launch site altitude in meters
 * @param parkingAltitude the circular parking altitude the injection leaves from (m)
 * @param periluneAltitude the perilune altitude aimed for (m)
 */
public record LunarLaunchWindowRequest(
    double latitude,
    double longitude,
    double altitude,
    double parkingAltitude,
    double periluneAltitude)
    implements LaunchWindowRequest {

  @Override
  public LunarLaunchWindowProblem toProblem() {
    return LunarLaunchWindowProblem.screening(
        latitude, longitude, altitude, parkingAltitude, periluneAltitude);
  }

  /**
   * @return the same screening problem with its azimuth freed
   */
  public LunarLaunchWindowProblem toFreeAzimuthProblem() {
    return LunarLaunchWindowProblem.screening(
        latitude,
        longitude,
        altitude,
        parkingAltitude,
        periluneAltitude,
        LunarLaunchWindowProblem.PlaneChoice.FREE_AZIMUTH);
  }
}
