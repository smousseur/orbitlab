package com.smousseur.orbitlab.simulation.mission.objective;

import com.smousseur.orbitlab.core.SolarSystemBody;

/**
 * Objective describing insertion into an orbit defined by perigee, apogee and inclination around a
 * celestial body.
 *
 * <p>Altitudes are geodetic, in meters above the body's surface. For a circular target,
 * {@code perigeeAltitude == apogeeAltitude} — use {@link #circular(SolarSystemBody, double, double)}.
 *
 * @param body the central body
 * @param perigeeAltitude target perigee altitude in meters
 * @param apogeeAltitude target apogee altitude in meters
 * @param inclination target orbital plane inclination in radians
 */
public record OrbitInsertionObjective(
    SolarSystemBody body, double perigeeAltitude, double apogeeAltitude, double inclination)
    implements MissionObjective {

  /** Factory for a circular target orbit at a single altitude. */
  public static OrbitInsertionObjective circular(
      SolarSystemBody body, double altitude, double inclination) {
    return new OrbitInsertionObjective(body, altitude, altitude, inclination);
  }
}
