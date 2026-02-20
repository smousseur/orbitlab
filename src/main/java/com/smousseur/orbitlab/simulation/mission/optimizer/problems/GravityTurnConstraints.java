package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

public record GravityTurnConstraints(
    double targetAltitude, double targetApogee, double maxApogee) {}
