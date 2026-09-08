package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.stage.StageSeparationStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnBurnStage.AscentInstrumentation;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagingPlan;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the explicit three-phase ascent that replaces the single {@code Gravity turn} stage (spec
 * {@code docs/mission-stages/01-separations-implicites.md} §5.1):
 *
 * <pre>
 *   Gravity turn (S1) → S1 separation → Gravity turn (S2)
 * </pre>
 *
 * <p>and the five phases a parallel-burn launcher whose boosters run dry before its core flies
 * instead (spec {@code docs/etagement/03-conception-L1.md} §3.5):
 *
 * <pre>
 *   Gravity turn (S1) → Booster separation → Gravity turn (core) → S1 separation → Gravity turn (S2)
 * </pre>
 *
 * <p><b>One factory, two callers.</b> The replay chain and the per-evaluation optimization chain
 * differ by nothing but the silence of their logs, and they must agree on the number of phases;
 * building them in two places would let them drift the day that number stopped being three.
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

  /** Name of the jettison dropping the boosters while the core keeps firing. */
  public static final String BOOSTER_SEPARATION_NAME = "Booster separation";

  /** Name of the powered phase the core flies alone, after the boosters are dropped. */
  public static final String CORE_BURN_NAME = "Gravity turn (core)";

  /** Name of the second powered phase, upper stage burning to MECO. */
  public static final String SECOND_BURN_NAME = "Gravity turn (S2)";

  private AscentSequence() {}

  /**
   * Builds the ascent phases a mission flies, sharing one plan reference.
   *
   * <p><b>The plane arguments are live as of MIS-7.</b> This overload existed before it and was
   * never called: both concrete missions went through the two-argument one, which passed {@code (0,
   * 0)}. Handing it a {@link LaunchPlane} that differs from the site's free plane now switches the
   * ascent to the commanded-plane attitude (spec {@code
   * docs/earth-orbit/01-mission-terre-parametrable.md} §4); handing it the free plane keeps the
   * historical trajectory bit-for-bit.
   *
   * @param vehicle the stack that will fly it, read for its staging plan
   * @param profile the launcher's flight profile (pitch kick, interstage coast)
   * @param constraints the apogee, velocity and flight-path-angle targets of the turn
   * @param launchPlane the target orbital plane
   * @param launchLatitudeDeg the launch site latitude (<b>degrees</b>)
   * @return the ascent phases, in order
   */
  public static List<MissionStage> gravityTurn(
      Vehicle vehicle,
      AscentProfile profile,
      GravityTurnConstraints constraints,
      LaunchPlane launchPlane,
      double launchLatitudeDeg) {
    AscentPlanRef planRef = new AscentPlanRef();
    GravityTurnFirstBurnStage firstBurn =
        new GravityTurnFirstBurnStage(
            FIRST_BURN_NAME,
            planRef,
            profile.pitchKickAngleDeg(),
            profile.interstageCoastDuration(),
            launchPlane,
            launchLatitudeDeg,
            constraints,
            AscentInstrumentation.REPLAY);
    return chain(
        vehicle,
        firstBurn,
        planRef,
        profile.interstageCoastDuration(),
        AscentInstrumentation.REPLAY,
        true);
  }

  /**
   * Builds the ascent phases for a launch into the site's free due-east plane — no plane commanded,
   * no inclination change, the shape every profile in the catalog flew before MIS-7.
   *
   * @param vehicle the stack that will fly it, read for its staging plan
   * @param profile the launcher's flight profile
   * @param constraints the apogee, velocity and flight-path-angle targets of the turn
   * @param launchLatitudeDeg the launch site latitude (degrees)
   * @return the ascent phases, in order
   */
  public static List<MissionStage> gravityTurn(
      Vehicle vehicle,
      AscentProfile profile,
      GravityTurnConstraints constraints,
      double launchLatitudeDeg) {
    return gravityTurn(
        vehicle, profile, constraints, LaunchPlane.dueEast(launchLatitudeDeg), launchLatitudeDeg);
  }

  /**
   * Assembles the phases around an already-built first burn — the single place the ascent's shape
   * is decided, for the replay chain and for the per-evaluation optimization chain alike.
   *
   * @param vehicle the stack that will fly it, read for its staging plan
   * @param firstBurn the phase owning the turn, already configured
   * @param planRef the reference every phase reads its schedule from
   * @param interstageCoastDuration the coast before the upper stage ignites (s)
   * @param instrumentation the guards the powered phases arm
   * @param logJettison whether the jettisons log; {@code false} on an optimization chain
   * @return the ascent phases, in order
   */
  static List<MissionStage> chain(
      Vehicle vehicle,
      GravityTurnFirstBurnStage firstBurn,
      AscentPlanRef planRef,
      double interstageCoastDuration,
      AscentInstrumentation instrumentation,
      boolean logJettison) {
    StagingPlan staging = vehicle.stagingPlan();
    boolean parallel = staging.hasParallelBlock();
    boolean corePhase = parallel && !staging.parallelBlock().groupedJettison();
    // The first jettison drops whatever the first burn was flying: the boosters when the launcher
    // burns in parallel — grouped with the core or not — and the core itself otherwise.
    StageRole firstDropped = parallel ? StageRole.BOOSTER : StageRole.CORE;

    List<MissionStage> phases = new ArrayList<>(corePhase ? 5 : 3);
    phases.add(firstBurn);
    if (corePhase) {
      phases.add(
          new StageSeparationStage(
              BOOSTER_SEPARATION_NAME,
              AscentPlan.BOOSTER_SEPARATION_COAST,
              firstDropped,
              logJettison));
      phases.add(new GravityTurnCoreBurnStage(CORE_BURN_NAME, planRef, instrumentation));
      phases.add(
          new StageSeparationStage(
              SEPARATION_NAME, interstageCoastDuration, StageRole.CORE, logJettison));
    } else {
      phases.add(
          new StageSeparationStage(
              SEPARATION_NAME, interstageCoastDuration, firstDropped, logJettison));
    }
    phases.add(new GravityTurnSecondBurnStage(SECOND_BURN_NAME, planRef, instrumentation));
    return List.copyOf(phases);
  }
}
