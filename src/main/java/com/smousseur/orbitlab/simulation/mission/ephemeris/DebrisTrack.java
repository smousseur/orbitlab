package com.smousseur.orbitlab.simulation.mission.ephemeris;

import java.util.Objects;

/**
 * One jettisoned object's display trajectory: a {@link MissionEphemeris} propagated on its own
 * after separation, for the sole purpose of drawing it (PHY-5 / L1, spec {@code
 * docs/multi-objets/03-conception-L1.md} §2.1). A debris is never seen by the optimizer.
 *
 * <p>Deliberately thin in L1 — the extension point, not yet the identity. {@code L2} adds the piece
 * key that picks its mesh, its colour and its label; here the renderer draws every debris with the
 * launcher's first booster mesh and a default colour.
 *
 * @param ephemeris the pre-computed trajectory of the jettisoned object
 */
public record DebrisTrack(MissionEphemeris ephemeris) {
  public DebrisTrack {
    Objects.requireNonNull(ephemeris, "ephemeris");
  }
}
