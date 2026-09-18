package com.smousseur.orbitlab.simulation.mission.ephemeris;

import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionHorizon;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.runtime.StageChainRunner;
import com.smousseur.orbitlab.simulation.mission.stage.StageSeparationStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagingPlan;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.propagation.SpacecraftState;

/**
 * Generates the complete mission ephemeris by replaying all stages with their optimized parameters
 * injected. Uses Orekit numerical propagation with a fixed-step handler to sample the trajectory.
 *
 * <p>The stage-by-stage traversal itself lives in {@link StageChainRunner}, shared with the
 * optimize pass so the two cannot drift apart; this class contributes only what is
 * specific to sampling a trajectory: the points, and the completeness verdict.
 */
public final class MissionEphemerisGenerator {
  private static final Logger logger = LogManager.getLogger(MissionEphemerisGenerator.class);

  /**
   * How far short of its scheduled cutoff a stage may stop before the trajectory counts as
   * truncated. Orekit brackets STOP events to well under a millisecond, so a stage reaching its own
   * cutoff lands on it; ending seconds early means a different STOP fired first — in practice the
   * {@code DepletionGuard} on a burn that ran its tank dry. One second sits far
   * above the bracketing noise and far below any real depletion shortfall (tens of seconds+).
   */
  private static final double STAGE_END_TOLERANCE_SECONDS = 1.0;

  /**
   * Re-propagates the mission from initialState through all stages, sampling the trajectory. The
   * trailing coast is resolved from the mission's own {@link MissionHorizon}, taking {@code
   * mission.getCurrentState()} as the insertion state — which is what it holds once the optimize
   * pass has walked the chain.
   *
   * @param mission the mission with optimization results injected into stages
   * @param initialState the spacecraft state at T_start
   * @return the complete mission ephemeris
   */
  public MissionEphemeris generate(Mission mission, SpacecraftState initialState) {
    SpacecraftState insertionState =
        mission.getCurrentState() != null ? mission.getCurrentState() : initialState;
    return generate(
        mission,
        initialState,
        mission.getHorizon().finalCoastSeconds(initialState.getDate(), insertionState));
  }

  /**
   * Re-propagates the mission from initialState through all stages, sampling the trajectory and
   * coasting for {@code finalCoastSeconds} past the last stage.
   *
   * <p>The caller passes a <b>resolved duration</b>, not a {@link MissionHorizon}: deciding how
   * long a mission should be recorded is an intent, and a generator has no business knowing the
   * intent. {@code MissionOptimizer}
   * resolves it, because that is where the achieved orbit is already in hand.
   *
   * <p>The sampling step is not a parameter either: each stage advertises its own through {@link
   * com.smousseur.orbitlab.simulation.mission.MissionStage#sampleStepSeconds}, so burns are
   * recorded at 1 s and coasts at 60 s.
   *
   * @param mission the mission with optimization results injected into stages
   * @param initialState the spacecraft state at T_start
   * @param finalCoastSeconds how long to coast past the last stage, in seconds
   * @return the complete mission ephemeris
   */
  public MissionEphemeris generate(
      Mission mission, SpacecraftState initialState, double finalCoastSeconds) {
    return generateWithJettisons(mission, initialState, finalCoastSeconds).ephemeris();
  }

  /**
   * Re-propagates the mission as {@link #generate(Mission, SpacecraftState, double)} does, and also
   * reports the separation events for {@link DebrisGenerator} to fly.
   *
   * <p>The ephemeris is identical to what {@code generate} returns — the jettison capture is a
   * separate accumulation that touches no sample — so the callers that only want the trajectory,
   * and the gate that pins it, are unaffected.
   *
   * @param mission the mission with optimization results injected into stages
   * @param initialState the spacecraft state at T_start
   * @param finalCoastSeconds how long to coast past the last stage, in seconds
   * @return the mission trajectory and its separation events
   */
  public GeneratedTrajectory generateWithJettisons(
      Mission mission, SpacecraftState initialState, double finalCoastSeconds) {
    Collector collector = new Collector(mission);
    StageChainRunner runner = StageChainRunner.sampling(collector, finalCoastSeconds, collector);

    runner.run(mission.getStages(), initialState, mission);

    logger.info(
        "Total ephemeris points: {} (complete={}, final coast {} s, jettisons {})",
        collector.points.size(),
        collector.complete,
        String.format(java.util.Locale.ROOT, "%.0f", finalCoastSeconds),
        collector.jettisons.size());
    return new GeneratedTrajectory(
        new MissionEphemeris(collector.points, collector.complete), collector.jettisons);
  }

  /**
   * Turns the flown chain into ephemeris points, and judges whether the trajectory is whole. Both
   * roles read the same stream of stages, so they share one object.
   */
  private static final class Collector
      implements StageChainRunner.StepSampler, StageChainRunner.StageListener {

    private final Mission mission;
    private final List<MissionEphemerisPoint> points = new ArrayList<>();

    /** The separation events, in flight order — for {@link DebrisGenerator} (PHY-5 / L1). */
    private final List<JettisonEvent> jettisons = new ArrayList<>();

    /**
     * The final state of the stage that ran just before the current one — the pre-jettison state a
     * separation drops from, since a {@code StageSeparationStage} only changes the mass.
     */
    private SpacecraftState previousStageFinalState;

    /** Cleared the moment any stage fails to reach its scheduled end. */
    private boolean complete = true;

    private Collector(Mission mission) {
      this.mission = mission;
    }

    @Override
    public void sample(MissionStage stage, FlightContext context, SpacecraftState state) {
      points.add(pointOf(stage, context.gravity(), state));
    }

    @Override
    public void onStageStart(MissionStage stage) {
      logger.info("Generating ephemeris for stage '{}'", stage.getName());
    }

    @Override
    public void onStageEnd(StageChainRunner.StageRun run) {
      if (run.propagationFailed()) {
        complete = false;
      }

      // A stage that stops materially before its own scheduled cutoff was truncated by an earlier
      // STOP event — in practice the DepletionGuard firing on a burn that ran its tank dry. The
      // flown 8×8 trajectory (the one rendered and read for feasibility) is then
      // broken past this point, so the whole ephemeris is flagged incomplete.
      double shortfall = run.shortfallSeconds();
      if (shortfall > STAGE_END_TOLERANCE_SECONDS) {
        logger.warn(
            "Stage '{}' stopped {} s before its scheduled cutoff — flown trajectory truncated"
                + " (propellant depleted mid-burn); marking ephemeris incomplete",
            run.stage().getName(),
            String.format(java.util.Locale.ROOT, "%.1f", shortfall));
        complete = false;
      }

      // Add the final state of this stage as a sample point, in the context it ENDED in — which is
      // the one it declared unless it crossed a sphere of influence on the way.
      points.add(pointOf(run.stage(), run.exitContext().gravity(), run.finalState()));

      captureJettison(run);
      previousStageFinalState = run.finalState();

      logger.info(
          "Stage '{}': {} points, ended at {}",
          run.stage().getName(),
          points.size(),
          run.finalState().getDate());
    }

    /**
     * Emits a {@link JettisonEvent} when this stage is a separation. The debris
     * start from the pre-jettison state ({@link #previousStageFinalState} — position and velocity
     * unchanged by the mass drop) with the total mass shed, the jettisoned block's own aggregate
     * section, and its multiplicity: the {@link DebrisGenerator} splits the block into that many
     * drawn objects. The active stage at the pre-jettison mass <em>is</em> the one being dropped.
     */
    private void captureJettison(StageChainRunner.StageRun run) {
      if (previousStageFinalState == null || !(run.stage() instanceof StageSeparationStage)) {
        return;
      }
      SpacecraftState pre = previousStageFinalState;
      double jettisonedMass = pre.getMass() - run.entryState().getMass();
      if (jettisonedMass <= 0.0) {
        return;
      }
      ActiveStageInfo info = mission.getVehicle().resolveActiveStage(pre.getMass());
      AerodynamicProperties aero = info.aerodynamics();
      StageRole role = info.role();
      if (aero == null || role == null) {
        return;
      }
      jettisons.add(new JettisonEvent(pre, aero, jettisonedMass, multiplicityOf(role), role));
    }

    /**
     * How many exemplars a role drops at once: the booster count for a parallel block, else one.
     */
    private int multiplicityOf(StageRole role) {
      if (role != StageRole.BOOSTER) {
        return 1;
      }
      StagingPlan staging = mission.getVehicle().stagingPlan();
      return staging.hasParallelBlock() ? staging.parallelBlock().boosterCount() : 1;
    }

    /**
     * Turns one flown state into a sample.
     *
     * <p>The gravitational context serves twice: it names the arc the sample belongs to, and it
     * provides the reference shape the altitude is measured against. Reading it twice would let the
     * two disagree
     * about which body the point is describing.
     *
     * <p>It is <b>handed in by the runner</b> rather than read off the stage since PHY-4 / L4: a
     * stage that crosses a sphere of influence declares one context and flies two, and asking the
     * stage would tag the second arc's points with the first arc's body.
     */
    private MissionEphemerisPoint pointOf(
        MissionStage stage, GravitationalContext context, SpacecraftState state) {
      Vector3D pos = state.getPosition();
      Vector3D vel = state.getPVCoordinates().getVelocity();
      double alt = mission.computeAltitudeMeters(state, context);
      return new MissionEphemerisPoint(
          state.getDate(),
          pos,
          vel,
          stage.getName(),
          stage.isPropulsive(),
          state.getMass(),
          alt,
          TrajectoryArc.of(context));
    }
  }
}
