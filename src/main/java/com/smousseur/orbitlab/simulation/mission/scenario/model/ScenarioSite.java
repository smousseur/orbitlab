package com.smousseur.orbitlab.simulation.mission.scenario.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The launch site as the wizard holds it: a display name and the three coordinates the model
 * actually reads.
 *
 * <p>The site is written by value rather than by catalog id on purpose — a scenario must keep
 * describing the pad it flew from even if the catalog moves under it, and the geometry is what the
 * ascent consumes.
 *
 * @param name the display name, or {@code null} for a site assembled by hand
 * @param latitudeDeg the latitude in degrees
 * @param longitudeDeg the longitude in degrees
 * @param altitudeM the altitude in metres — the one wizard value that is <b>not</b> in kilometres,
 *     because the form field is not either
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScenarioSite(String name, double latitudeDeg, double longitudeDeg, double altitudeM) {}
