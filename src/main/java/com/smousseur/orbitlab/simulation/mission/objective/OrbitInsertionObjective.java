package com.smousseur.orbitlab.simulation.mission.objective;

import com.smousseur.orbitlab.core.SolarSystemBody;

/**
 * Objective describing insertion into an orbit of a given altitude and eccentricity around a
 * celestial body.
 */
public record OrbitInsertionObjective(SolarSystemBody body, double altitude, double eccentricity)
    implements MissionObjective {}
