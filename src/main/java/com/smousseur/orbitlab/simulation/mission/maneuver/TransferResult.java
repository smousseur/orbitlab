package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;

/**
 * Immutable result of a two-burn transfer propagation.
 *
 * <p>Fields are nullable when the propagation ended in a penalty case (invalid orbit, failed
 * propagation, etc.). Callers must null-check before accessing optional fields.
 *
 * @param finalState the spacecraft state at the end of the transfer, or the initial state in
 *     penalty cases
 * @param orbitPostBurn1 the Keplerian orbit after burn 1 completes, or {@code null} if burn 1
 *     failed
 * @param resolvedBurn2 the deterministically resolved burn 2 parameters, or {@code null} if burn
 *     2 could not be resolved
 * @param altitudeTracker the altitude tracker that recorded min/max altitudes during the transfer,
 *     or {@code null} in early penalty cases
 */
public record TransferResult(
    SpacecraftState finalState,
    KeplerianOrbit orbitPostBurn1,
    TransfertTwoManeuver.ResolvedBurn2 resolvedBurn2,
    MinAltitudeTracker altitudeTracker) {}
