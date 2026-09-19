package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.simulation.mission.ephemeris.DebrisTrack;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.orekit.time.AbsoluteDate;

/**
 * The silhouette the primary object shows over time: the full launcher until it starts shedding
 * pieces, a shorter stack after each launcher separation, and finally the payload itself once the
 * upper stage is gone.
 *
 * <p>Derived from the debris a computation produced — each carries the date and role of the
 * separation that made it — so no new data has to flow from the replay. Stateless once built:
 * {@link #phaseAt} is a pure function of the date, which is what makes the silhouette reverse by
 * itself when the clock is scrubbed back. The phase names <em>what</em> to draw; resolving it to a
 * mesh and a size is {@link MissionRenderer}'s job, since only it knows the launcher and payload.
 */
final class PrimarySilhouette {

  /** The silhouette the primary wears, from the full stack down to the bare payload. */
  enum SilhouettePhase {
    FULL,
    AFTER_BOOSTERS,
    AFTER_S1,
    PAYLOAD
  }

  private final NavigableMap<AbsoluteDate, SilhouettePhase> transitions;

  private PrimarySilhouette(NavigableMap<AbsoluteDate, SilhouettePhase> transitions) {
    this.transitions = transitions;
  }

  /**
   * Builds the silhouette timeline from a mission's debris. {@code UPPER} shrinks the primary to
   * its payload (L5); {@code KICK} is the payload's own motor firing, not a launcher separation, so
   * it leaves the silhouette alone. Several boosters shed at one instant collapse to a single
   * transition.
   *
   * @param debris the mission's debris tracks
   * @return the silhouette timeline
   */
  static PrimarySilhouette from(List<DebrisTrack> debris) {
    NavigableMap<AbsoluteDate, SilhouettePhase> transitions = new TreeMap<>();
    for (DebrisTrack track : debris) {
      SilhouettePhase phase = phaseAfter(track.role());
      if (phase != null) {
        transitions.put(track.ephemeris().startDate(), phase);
      }
    }
    return new PrimarySilhouette(transitions);
  }

  private static SilhouettePhase phaseAfter(StageRole role) {
    return switch (role) {
      case BOOSTER -> SilhouettePhase.AFTER_BOOSTERS;
      case CORE -> SilhouettePhase.AFTER_S1;
      case UPPER -> SilhouettePhase.PAYLOAD;
      case KICK -> null;
    };
  }

  /**
   * The silhouette phase at {@code now} — {@link SilhouettePhase#FULL} before any separation, then
   * the phase of the last separation whose date is at or before {@code now}.
   *
   * @param now the current simulation date
   * @return the phase the primary should be drawn in
   */
  SilhouettePhase phaseAt(AbsoluteDate now) {
    Map.Entry<AbsoluteDate, SilhouettePhase> entry = transitions.floorEntry(now);
    return entry == null ? SilhouettePhase.FULL : entry.getValue();
  }
}
