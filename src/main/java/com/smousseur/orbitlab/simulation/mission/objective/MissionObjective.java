package com.smousseur.orbitlab.simulation.mission.objective;

/**
 * Declarative description of a mission goal. New objective kinds (rendezvous, specific orbit,
 * etc.) are added by declaring a new record that implements this sealed interface.
 */
public sealed interface MissionObjective permits OrbitInsertionObjective {}
