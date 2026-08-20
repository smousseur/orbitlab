package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.operation.LunarTransferMission;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowCandidate;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowProblem;
import java.time.Duration;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

/**
 * The Earth-Moon problem: what a translunar injection costs at a given epoch, screened on the
 * closed-form Lambert seed and confirmed by the mission's own injection stage.
 *
 * <p><b>The cost varies for one reason, and it is worth naming</b>: the lunar distance swings from
 * 363 300 to 405 500 km over an anomalistic month, which the vis-viva turns into a swing on the
 * injection — <b>measured at 14 m/s</b>, 3 182.8 to 3 196.9 m/s over January 2026 at the six-hour
 * step. That, plus the epochs {@link TranslunarInjectionPlan#parkingState} refuses outright, is the
 * whole shape of the merit function — monthly, smooth, with one minimum per revolution around
 * perigee of the lunar orbit.
 *
 * <p><b>Fourteen m/s on three thousand is a ranking and not a window</b>, and a caller must know
 * it: no acceptance budget a mission would write down carves a slot out of a criterion that flat,
 * so the interval this problem yields is the searched range itself. What it does deliver is the
 * cheapest epoch of the month, and the verdict on whether it can be flown at all. A real slot needs
 * a criterion with relief — the plane alignment of a fixed launch site, which is the second
 * implementation named below.
 *
 * <p><b>Why the confirmation is the mission's own stage and not a second call to {@link
 * TranslunarInjectionPlan#solve}.</b> What refuses an epoch here is not the seed, which always
 * converges, but the <em>flown</em> perilune floor — measured at 135 km at one epoch of a lunar
 * month against a 100 km target — and the depletion floor of the active stage. Both verdicts live
 * in {@code TranslunarInjectionStage.enter}, so calling it is what guarantees that an epoch this
 * problem offers is an epoch the mission can fly: a re-implementation here could drift from it, and
 * the drift would show up as a mission failing on the optimizer thread after being scheduled.
 *
 * <p><b>What this problem does <em>not</em> yet model</b>, and the honest limit of the first
 * increment: {@link LunarTransferMission} builds its parking plane <em>to fit</em> the Moon at
 * arrival, so it has no plane to wait for. A real MIS-4 launches from a site into a plane fixed by
 * the ascent, and the dominant criterion becomes the alignment of that fixed plane with the Moon's
 * direction at arrival — twice per sidereal month per site. That criterion is a second
 * implementation of this same interface, and the reason the interface exists.
 */
public class TranslunarInjectionPlanWindowProblem implements LaunchWindowProblem {
  private static final Logger logger =
      LogManager.getLogger(TranslunarInjectionPlanWindowProblem.class);

  /**
   * Sweep step. Six hours resolves a monthly criterion by a factor of a hundred and twenty, and
   * keeps a sixty-day search at 240 closed-form evaluations — milliseconds.
   */
  private static final Duration COARSE_STEP = Duration.ofHours(6);

  /**
   * Recurrence. The injection cost follows the Moon's direction, so it repeats with the sidereal
   * month — 27.321 661 d, the period the criterion's own geometry turns on.
   */
  private static final Duration RECURRENCE = Duration.ofSeconds(2_360_591);

  private final LunarTransferMission mission;

  /**
   * @param mission the mission whose injection is being scheduled; it supplies both the screening
   *     mass and the flown verdict of {@link #confirm}
   */
  public TranslunarInjectionPlanWindowProblem(LunarTransferMission mission) {
    this.mission = Objects.requireNonNull(mission, "mission");
  }

  @Override
  public String name() {
    return "Translunar injection";
  }

  @Override
  public Duration coarseStep() {
    return COARSE_STEP;
  }

  @Override
  public Duration recurrence() {
    return RECURRENCE;
  }

  @Override
  public LaunchWindowCandidate evaluate(AbsoluteDate epoch) {
    try {
      return LaunchWindowCandidate.of(
          epoch,
          TranslunarInjectionPlan.keplerianInjectionDeltaV(epoch, mission.getVehicle().getMass()));
    } catch (OrbitlabException refused) {
      // The declination guard, and any other closed-form refusal: data, not a failure.
      return LaunchWindowCandidate.refused(epoch, refused.getMessage());
    }
  }

  /**
   * Flies the mission's injection stage at the screened epoch: some thirty four-day propagations,
   * which is why it runs on the handful of refined candidates and not on the sweep.
   *
   * <p>The confirmed cost is measured as the velocity jump across the stage rather than read off
   * the plan, because {@code enter} returns a state and not a plan; the impulse is applied at fixed
   * date and position, so the two are the same number.
   *
   * <p>Confirming leaves the mission untouched — {@code enter} reads the vehicle and the
   * gravitational context and writes nothing back — which is what allows the very instance being
   * scheduled to be handed to this problem.
   */
  @Override
  public LaunchWindowCandidate confirm(LaunchWindowCandidate candidate) {
    AbsoluteDate epoch = candidate.epoch();
    try {
      SpacecraftState parking = mission.getInitialState(epoch);
      SpacecraftState injected = mission.getStages().getFirst().enter(parking, mission);
      double deltaV =
          injected
              .getPVCoordinates()
              .getVelocity()
              .subtract(parking.getPVCoordinates().getVelocity())
              .getNorm();
      logger.info(
          "[{}] {} confirmed at {} m/s (screened at {} m/s)",
          name(),
          epoch,
          FastMath.round(deltaV),
          FastMath.round(candidate.deltaV()));
      return LaunchWindowCandidate.of(epoch, deltaV);
    } catch (OrbitlabException refused) {
      logger.info("[{}] {} refused: {}", name(), epoch, refused.getMessage());
      return LaunchWindowCandidate.refused(epoch, refused.getMessage());
    } catch (RuntimeException failure) {
      // Anything the force model or Orekit throws on an extreme geometry. Withdrawing the epoch
      // rather than propagating keeps one bad candidate from aborting a whole search, but it is a
      // fault and not a refusal, so it is logged as one.
      logger.warn("[{}] {} could not be confirmed", name(), epoch, failure);
      return LaunchWindowCandidate.refused(epoch, failure.getMessage());
    }
  }
}
