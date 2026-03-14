package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;

/**
 * Immutable result of a two-burn transfer propagation.
 *
 * <p>Fields are nullable when the propagation ended in a penalty case (invalid orbit, failed
 * propagation, etc.). Callers must null-check before accessing optional fields.
 */
public record TransferResult(
    SpacecraftState finalState,
    KeplerianOrbit orbitPostBurn1,
    TransfertTwoManeuver.ResolvedBurn2 resolvedBurn2,
    MinAltitudeTracker altitudeTracker) {}
