package com.smousseur.orbitlab.simulation.mission;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.Objects;

/**
 * Which object of a mission the view follows: the mission's primary vehicle, or one of its
 * jettisoned debris. This is the addressing SEL-1 introduces so the camera — and, from L2, the
 * telemetry — can point at an object <em>below</em> the mission (spec {@code
 * docs/selection-objets/03-conception-L1.md}).
 *
 * <p>Every variant carries the {@link MissionId} it belongs to; a {@link Debris} additionally
 * carries the piece's own identity — its {@link StageRole role} and 1-based exemplar index. That is
 * the identity {@code DebrisTrack} already declares, and it is stable across the recomputations
 * that rebuild a mission's debris list; an index into that list would not be.
 */
public sealed interface FollowedObject permits FollowedObject.Primary, FollowedObject.Debris {

  /**
   * The mission this object belongs to.
   *
   * @return the mission id
   */
  MissionId mission();

  /**
   * The display label of a jettisoned piece — {@code "Booster 1"}, {@code "Core"}, {@code "Upper
   * stage"}, {@code "Kick stage"} — shared by the renderer's icon and the telemetry identity line
   * so a piece reads the same wherever it is named (SEL-1 / L2).
   *
   * @param role the piece's role
   * @param exemplar its 1-based exemplar index, shown only for a booster
   * @return the piece label
   */
  static String debrisLabel(StageRole role, int exemplar) {
    return switch (role) {
      case BOOSTER -> "Booster " + exemplar;
      case CORE -> "Core";
      case UPPER -> "Upper stage";
      case KICK -> "Kick stage";
    };
  }

  /**
   * The mission's primary vehicle — the object followed before any object-level selection, and the
   * one a selection returns to.
   *
   * @param mission the mission followed
   */
  record Primary(MissionId mission) implements FollowedObject {
    public Primary {
      Objects.requireNonNull(mission, "mission");
    }
  }

  /**
   * One jettisoned piece of a mission, addressed exactly as {@code DebrisTrack} identifies it.
   *
   * @param mission the mission the piece was jettisoned from
   * @param role the role of the jettisoned piece (booster, core, upper stage, kick)
   * @param exemplar the 1-based exemplar index among a multi-exemplar jettison; 1 for a single
   *     piece
   */
  record Debris(MissionId mission, StageRole role, int exemplar) implements FollowedObject {
    public Debris {
      Objects.requireNonNull(mission, "mission");
      Objects.requireNonNull(role, "role");
    }
  }
}
