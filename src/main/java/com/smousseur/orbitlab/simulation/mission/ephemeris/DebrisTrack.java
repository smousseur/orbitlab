package com.smousseur.orbitlab.simulation.mission.ephemeris;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.Objects;

/**
 * One jettisoned object's display trajectory: a {@link MissionEphemeris} propagated on its own
 * after separation, for the sole purpose of drawing it. A debris is never seen by the optimizer.
 *
 * <p>The {@code role} and {@code exemplarIndex} are its identity: the renderer maps them to the
 * piece's own mesh ({@code booster{i}}, {@code core}, {@code S2}) and label. The colour is a render
 * decision, not carried here.
 *
 * @param ephemeris the pre-computed trajectory of the jettisoned object
 * @param role the role of the jettisoned piece (booster, core, upper stage)
 * @param exemplarIndex the 1-based index of this exemplar among a multi-exemplar jettison; 1 for a
 *     single piece
 */
public record DebrisTrack(MissionEphemeris ephemeris, StageRole role, int exemplarIndex) {
  public DebrisTrack {
    Objects.requireNonNull(ephemeris, "ephemeris");
    Objects.requireNonNull(role, "role");
  }
}
