package com.smousseur.orbitlab.simulation.mission.runtime;

/**
 * Mass and ΔV accounting of one executed mission stage.
 *
 * <p>{@code deltaV} is the Tsiolkovsky estimate at the stage's entry Isp; stages that span a
 * jettison (e.g. the gravity turn burning two physical stages) mix Isps, so their value is an
 * approximation. Jettisoned dry mass is excluded from {@code propellantConsumed}.
 *
 * @param stageName the mission stage name
 * @param massIn the spacecraft mass at stage entry (kg)
 * @param massOut the spacecraft mass at stage exit (kg)
 * @param propellantConsumed the propellant burnt during the stage (kg), jettisons excluded
 * @param deltaV the ΔV delivered by the stage (m/s)
 */
public record StagePerformance(
    String stageName, double massIn, double massOut, double propellantConsumed, double deltaV) {}
