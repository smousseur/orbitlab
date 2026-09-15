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
 * pieces, then a shorter stack after each launcher separation (PHY-5 / L3, spec {@code
 * docs/multi-objets/05-conception-L3.md} §3.2).
 *
 * <p>Derived from the debris a computation produced — each carries the date and role of the
 * separation that made it — so no new data has to flow from the replay. Stateless once built:
 * {@link #suffixAt} is a pure function of the date, which is what makes the silhouette reverse by
 * itself when the clock is scrubbed back.
 */
final class PrimarySilhouette {

  private final NavigableMap<AbsoluteDate, String> transitions;

  private PrimarySilhouette(NavigableMap<AbsoluteDate, String> transitions) {
    this.transitions = transitions;
  }

  /**
   * Builds the silhouette timeline from a mission's debris. The {@code UPPER} separation is left
   * out — the primary becomes the payload there, which is {@code L4}; {@code KICK} is not a
   * launcher separation. Several boosters shed at one instant collapse to a single transition.
   *
   * @param debris the mission's debris tracks
   * @return the silhouette timeline
   */
  static PrimarySilhouette from(List<DebrisTrack> debris) {
    NavigableMap<AbsoluteDate, String> transitions = new TreeMap<>();
    for (DebrisTrack track : debris) {
      String suffix = suffixAfter(track.role());
      if (suffix != null) {
        transitions.put(track.ephemeris().startDate(), suffix);
      }
    }
    return new PrimarySilhouette(transitions);
  }

  private static String suffixAfter(StageRole role) {
    return switch (role) {
      case BOOSTER -> "-after_boosters";
      case CORE -> "-after_s1";
      case UPPER, KICK -> null;
    };
  }

  /**
   * The mesh-path suffix of the silhouette at {@code now} — {@code ""} for the full launcher,
   * {@code "-after_boosters"} once the boosters are gone, {@code "-after_s1"} once the core is too.
   *
   * @param now the current simulation date
   * @return the suffix to glue onto the launcher mesh path, {@code ""} before any separation
   */
  String suffixAt(AbsoluteDate now) {
    Map.Entry<AbsoluteDate, String> entry = transitions.floorEntry(now);
    return entry == null ? "" : entry.getValue();
  }
}
