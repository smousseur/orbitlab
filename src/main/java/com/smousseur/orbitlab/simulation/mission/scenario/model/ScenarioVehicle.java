package com.smousseur.orbitlab.simulation.mission.scenario.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The vehicle by catalog id, and nothing more.
 *
 * <p>The propellant loads are deliberately absent: they are <b>derived</b>, and {@code
 * PropellantBudget} resizes them from these three values on the way back in (spec {@code
 * docs/scenario/01-persistance-missions.md} §2). Freezing them would replay an old sizing after the
 * budget improved. The one exception — the loads a {@code PRECISE} sweep <em>searched</em> rather
 * than derived — is not a property of the vehicle and travels in {@link ScenarioSolution}.
 *
 * @param launcherId the launcher catalog id
 * @param payloadId the payload catalog id, or {@code null} for a payload assembled by hand
 * @param payloadDryMassKg the payload dry mass in kilograms, as typed in the wizard
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScenarioVehicle(String launcherId, String payloadId, double payloadDryMassKg) {}
