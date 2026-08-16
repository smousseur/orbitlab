package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.stage.StageSeparationStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnBurnStage.AscentInstrumentation;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import java.util.List;

/**
 * Builds the explicit three-phase ascent that replaces the single {@code Gravity turn} stage (spec
 * {@code docs/mission-stages/01-separations-implicites.md} §5.1):
 *
 * <pre>
 *   Gravity turn (S1) → S1 separation → Gravity turn (S2)
 * </pre>
 *
 * <p><b>What the factory guarantees.</b> The three phases share one {@link AscentPlanRef}, so they
 * cannot disagree on the jettison and ignition dates; and the jettison is a phase rather than a
 * detector planted inside a burn, so a MECO scheduled too early can no longer end the propagation
 * before the first stage is dropped. That failure mode — 0.4 s of missing MECO stranding 3.3 t in
 * S1 and letting the next separation jettison the wrong stage — is closed structurally, not by a
 * penalty.
 *
 * <p>The separation phase is a plain {@link StageSeparationStage}: it already drops the mass on
 * entry, already carries a settling coast, and already refuses to jettison the wrong stage. Its
 * coast <em>is</em> the interstage coast, so the ascent needs no extra coasting stage.
 */
public final class AscentSequence {

  /** Name of the first powered phase, first stage burning to flame-out. */
  public static final String FIRST_BURN_NAME = "Gravity turn (S1)";

  /** Name of the jettison phase carrying the interstage coast. */
  public static final String SEPARATION_NAME = "S1 separation";

  /** Name of the second powered phase, upper stage burning to MECO. */
  public static final String SECOND_BURN_NAME = "Gravity turn (S2)";

  /**
   * Stack index of the stage {@link #SEPARATION_NAME} drops. The launcher's first stage is the
   * bottom of the stack, so declaring it makes the separation fail fast if the ascent ever leaves a
   * different stage active.
   */
  public static final int FIRST_STAGE_INDEX = 0;

  private AscentSequence() {}

  /**
   * Builds the three ascent phases a mission flies, sharing one plan reference.
   *
   * <p><b>The plane arguments are live as of MIS-7.</b> This overload existed before it and was
   * never called: both concrete missions went through the two-argument one, which passed {@code (0,
   * 0)}. Handing it a {@link LaunchPlane} that differs from the site's free plane now switches the
   * ascent to the commanded-plane attitude (spec {@code
   * docs/earth-orbit/01-mission-terre-parametrable.md} §4); handing it the free plane keeps the
   * historical trajectory bit-for-bit.
   *
   * @param profile the launcher's flight profile (pitch kick, interstage coast)
   * @param constraints the apogee, velocity and flight-path-angle targets of the turn
   * @param launchPlane the target orbital plane
   * @param launchLatitudeDeg the launch site latitude (<b>degrees</b>)
   * @return the three phases, in order
   */
  public static List<MissionStage> gravityTurn(
      AscentProfile profile,
      GravityTurnConstraints constraints,
      LaunchPlane launchPlane,
      double launchLatitudeDeg) {
    AscentPlanRef planRef = new AscentPlanRef();
    return List.of(
        new GravityTurnFirstBurnStage(
            FIRST_BURN_NAME,
            planRef,
            profile.pitchKickAngleDeg(),
            profile.interstageCoastDuration(),
            launchPlane,
            launchLatitudeDeg,
            constraints,
            AscentInstrumentation.REPLAY),
        separation(profile.interstageCoastDuration()),
        new GravityTurnSecondBurnStage(SECOND_BURN_NAME, planRef, AscentInstrumentation.REPLAY));
  }

  /**
   * Builds the three ascent phases for a launch into the site's free due-east plane — no plane
   * commanded, no inclination change, the shape every profile in the catalog flew before MIS-7.
   *
   * @param profile the launcher's flight profile
   * @param constraints the apogee, velocity and flight-path-angle targets of the turn
   * @param launchLatitudeDeg the launch site latitude (degrees)
   * @return the three phases, in order
   */
  public static List<MissionStage> gravityTurn(
      AscentProfile profile, GravityTurnConstraints constraints, double launchLatitudeDeg) {
    return gravityTurn(
        profile, constraints, LaunchPlane.dueEast(launchLatitudeDeg), launchLatitudeDeg);
  }

  /**
   * The jettison phase of an ascent, on the replay path: drops the first stage, carries the
   * interstage coast, and logs the jettison — once per mission, which is what makes that line worth
   * reading.
   *
   * @param interstageCoastDuration the settling coast after the jettison (s)
   * @return the separation phase
   */
  static StageSeparationStage separation(double interstageCoastDuration) {
    return new StageSeparationStage(SEPARATION_NAME, interstageCoastDuration, FIRST_STAGE_INDEX);
  }

  /**
   * The same jettison phase for an optimization chain, silent. It fires once per CMA-ES candidate,
   * on every exploration thread at once, where the jettison mass is exactly the quantity being
   * varied and the line carries no information — only appender contention. The stack-index check is
   * unaffected and still fails fast.
   *
   * @param interstageCoastDuration the settling coast after the jettison (s)
   * @return the separation phase, not logging its jettison
   */
  static StageSeparationStage silentSeparation(double interstageCoastDuration) {
    return new StageSeparationStage(
        SEPARATION_NAME, interstageCoastDuration, FIRST_STAGE_INDEX, false);
  }
}
