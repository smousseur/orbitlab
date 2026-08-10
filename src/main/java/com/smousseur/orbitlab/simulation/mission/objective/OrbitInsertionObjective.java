package com.smousseur.orbitlab.simulation.mission.objective;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.mission.Mission;

/**
 * Objective describing insertion into an orbit defined by perigee, apogee and inclination around a
 * celestial body.
 *
 * <p>Altitudes are geodetic, in meters above the body's surface. For a circular target, {@code
 * perigeeAltitude == apogeeAltitude} — use {@link #circular(SolarSystemBody, double, double)}.
 *
 * <p>Read as <b>mean</b> elements by the analytic trim that shapes the final orbit (spec
 * orbit-reporting/02), so the flown perigee excursion is centred on these altitudes rather than
 * perched at the top of the J2 short-period oscillation. The record itself is unchanged and carries
 * no convention: every other reader still treats these as osculating targets.
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

  public static OrbitInsertionObjective gto(
      SolarSystemBody body, Mission mission, double altitude, double inclination) {
    return new OrbitInsertionObjective(
        body, mission.computeAltitudeMeters(), altitude, inclination);
  }
}
