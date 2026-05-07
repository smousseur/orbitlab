package com.smousseur.orbitlab.simulation.mission.objective;

import com.smousseur.orbitlab.core.SolarSystemBody;

/**
 * Objective describing insertion into an orbit of a given altitude, eccentricity, and inclination
 * around a celestial body.
 *
 * @param body the central body
 * @param altitude target circular orbit altitude in meters
 * @param eccentricity target eccentricity
 * @param inclination target orbital plane inclination in radians
 */
public record OrbitInsertionObjective(
    SolarSystemBody body, double altitude, double eccentricity, double inclination)
    implements MissionObjective {}
