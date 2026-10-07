package com.smousseur.orbitlab.simulation.mission.window.problem;

/**
 * Everything the wizard's lunar timeline needs, and nothing the parameters step cannot know.
 *
 * <p><b>Five numbers, all of them available at that step.</b> The pad comes from the site step, the
 * parking altitude is {@code LunarFlybyMission.DEFAULT_PARKING_ALTITUDE}, and the perilune is the
 * one field the lunar panel offers. The vehicle and the measured insertion — {@link
 * LunarLaunchWindowProblem}'s two other inputs — are absent because they belong to the creation's
 * confirming problem alone, and the launcher is chosen a step later.
 *
 * <p><b>Two problems, as the creation has two searches</b>: due east first, {@link #toProblem()},
 * and the free azimuth {@link LaunchWindowPlanner} falls back to when due east offers nothing
 * flyable, {@link #toFreeAzimuthProblem()}.
 *
 * <p><b>The timeline's dates are indicative.</b> The creation dates a mission on the parking orbit
 * its ascent really reaches, which takes a flight and a launcher; the timeline has neither, so its
 * problems keep the parking orbit posed at the pad at the lift-off instant. Measured from Canaveral
 * over five days, its dates precede the creation's by 80 to 121 s on the morning windows and by 9
 * to 11 minutes on the night ones — always earlier, and always inside the same slot, 25 to 68
 * minutes wide. The typed date being a floor, picking the timeline's date leads the creation to
 * that same slot.
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
