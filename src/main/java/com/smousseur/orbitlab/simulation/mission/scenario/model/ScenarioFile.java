package com.smousseur.orbitlab.simulation.mission.scenario.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * A saved session: the missions that were open and the clock they were being watched with (spec
 * {@code docs/scenario/01-persistance-missions.md} §2).
 *
 * <p>The unit is the <b>session</b>, not the mission. That is what the end of phase asks for — "a
 * mission survives the application closing" — and it is the only shape in which the file describes
 * what was on screen. The clock is part of it for the same reason: the orchestrator hides any
 * mission whose ephemeris starts after the current instant, so a scenario launching in six months,
 * reopened on "now", would restore a list of missions and a black screen (§2.2).
 *
 * @param formatVersion the schema this file was written with; a file claiming more than {@link
 *     #CURRENT_FORMAT_VERSION} is refused whole, since nothing here knows what it is reading
 * @param savedAt when the file was written, ISO UTC — displayed by the browser window, read by
 *     nothing else
 * @param clockDate the simulation clock at save time, ISO UTC
 * @param missions the saved missions, in roster order
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScenarioFile(
    int formatVersion, String savedAt, String clockDate, List<ScenarioMission> missions) {

  /** The version this build writes, and the highest one it accepts to read. */
  public static final int CURRENT_FORMAT_VERSION = 1;
}
