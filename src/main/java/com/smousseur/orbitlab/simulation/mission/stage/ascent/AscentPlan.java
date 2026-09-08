package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

/**
 * The fully dated schedule of one gravity-turn ascent: the decoded CMA-ES variables, the burn
 * durations derived from them, and every date the ascent hangs off.
 *
 * <p><b>Why it exists.</b> The ascent is one propagation today but becomes three phases (spec
 * {@code docs/mission-stages/01-separations-implicites.md} §4.2): {@code Gravity turn (S1) → S1
 * separation → Gravity turn (S2)}. Those phases only reproduce today's trajectory if they agree to
 * the millisecond on when the first stage stops thrusting, when it is dropped, and when the second
 * ignites — so the dates must be computed <b>once</b>, not re-derived per phase. This record is
 * that single computation; {@link
 * com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver#plan} is the only place
 * that produces one.
 *
 * <p><b>Date arithmetic is deliberately literal.</b> The accessors below reproduce the exact chain
 * of {@link AbsoluteDate#shiftedBy(double)} calls the single-propagator configuration used, epsilon
 * by epsilon. {@code AbsoluteDate} stores its offset in two parts, so {@code
 * a.shiftedBy(x).shiftedBy(y)} and {@code a.shiftedBy(x + y)} are not the same bits: collapsing
 * these chains would move the burn boundaries by femtoseconds and take the refactor off its
 * iso-trajectory guarantee for no gain.
 *
 * @param kickDate the pitch-kick date, anchor of the pitch law and origin of every date below
 * @param transitionTime total gravity turn duration up to MECO (s) — CMA-ES variable 0
 * @param exponent power-law exponent controlling the pitch-over profile — CMA-ES variable 1
 * @param burn1Duration first-stage burn duration to propellant exhaustion (s) — of the parallel
 *     block, when the launcher flies one, and it ends at the <em>boosters'</em> flame-out
 * @param coreBurnDuration core-only burn duration after the boosters are dropped (s), {@code 0}
 *     when the ascent has no core-only phase
 * @param interstageCoast unpowered coast between jettison and second-stage ignition (s)
 * @param burn2Duration second-stage burn duration after the coast, floored at 0 (s)
 * @param maxStepSeconds integrator max step keeping the late-ignition invariant for the ascent
 * @param firstStage the vehicle stage active at gravity-turn entry (the one jettisoned)
 * @param coreStage the core burning alone after the boosters are dropped, or {@code null} when the
 *     ascent has no such phase; callers test {@link #hasCorePhase()} rather than the value
 * @param secondStage the vehicle stage active after the last launcher jettison
 * @param commandedPlaneNormal unit normal of the plane the ascent steers into, or {@code null} when
 *     no plane is commanded and the turn follows whatever plane the kick left behind (spec {@code
 *     docs/earth-orbit/01-mission-terre-parametrable.md} §4); callers test {@link
 *     #hasCommandedPlane()} rather than the value
 */
public record AscentPlan(
    AbsoluteDate kickDate,
    double transitionTime,
    double exponent,
    double burn1Duration,
    double coreBurnDuration,
    double interstageCoast,
    double burn2Duration,
    double maxStepSeconds,
    ActiveStageInfo firstStage,
    ActiveStageInfo coreStage,
    ActiveStageInfo secondStage,
    Vector3D commandedPlaneNormal) {

  /**
   * Settling offset placed before every ignition and after the jettison. Orekit brackets an event
   * at the exact boundary ambiguously, so each transition is nudged clear of the previous one.
   */
  private static final double SETTLING_EPSILON = 1.0e-3;

  /**
   * Settling coast of the booster jettison. A real launcher cuts nothing there — the core keeps
   * firing through the separation — but the model has to stop and restart the propagation to change
   * the force model, so the jettison carries the shortest coast the model can schedule. The
   * separation phase is built with this exact value rather than zero, so that no clamping happens
   * and the phase and this schedule agree by construction (spec {@code
   * docs/etagement/03-conception-L1.md} §3.6).
   */
  public static final double BOOSTER_SEPARATION_COAST = 1.0e-3;

  /** An ascent that stages sequentially: one jettison, two burns, no core-only phase. */
  public AscentPlan(
      AbsoluteDate kickDate,
      double transitionTime,
      double exponent,
      double burn1Duration,
      double interstageCoast,
      double burn2Duration,
      double maxStepSeconds,
      ActiveStageInfo firstStage,
      ActiveStageInfo secondStage,
      Vector3D commandedPlaneNormal) {
    this(
        kickDate,
        transitionTime,
        exponent,
        burn1Duration,
        0.0,
        interstageCoast,
        burn2Duration,
        maxStepSeconds,
        firstStage,
        null,
        secondStage,
        commandedPlaneNormal);
  }

  /** Ignition of the first-stage burn, a settling epsilon after the pitch kick. */
  public AbsoluteDate firstIgnitionDate() {
    return kickDate.shiftedBy(SETTLING_EPSILON);
  }

  /**
   * Jettison of the first stage: first-stage burnout plus a settling epsilon. On a launcher flying
   * a parallel block this is the <em>booster</em> jettison, the core carrying on alone.
   */
  public AbsoluteDate jettisonDate() {
    return firstIgnitionDate().shiftedBy(burn1Duration).shiftedBy(SETTLING_EPSILON);
  }

  /** Ignition of the core-only burn, one separation coast after the boosters are dropped. */
  public AbsoluteDate coreIgnitionDate() {
    return jettisonDate().shiftedBy(BOOSTER_SEPARATION_COAST).shiftedBy(SETTLING_EPSILON);
  }

  /** Jettison of the core: core burnout plus a settling epsilon. */
  public AbsoluteDate coreJettisonDate() {
    return coreIgnitionDate().shiftedBy(coreBurnDuration).shiftedBy(SETTLING_EPSILON);
  }

  /**
   * Ignition of the second-stage burn, one interstage coast after the last launcher jettison.
   *
   * <p>Without a core phase this is literally {@code jettisonDate().shiftedBy(interstageCoast)
   * .shiftedBy(ε)}, the pre-split expression, epsilon by epsilon — which is what keeps a launcher
   * declaring no boosters on its calibrated trajectory.
   */
  public AbsoluteDate secondIgnitionDate() {
    AbsoluteDate lastJettison = hasCorePhase() ? coreJettisonDate() : jettisonDate();
    return lastJettison.shiftedBy(interstageCoast).shiftedBy(SETTLING_EPSILON);
  }

  /** Whether the boosters run dry before the core, leaving a core-only phase to fly. */
  public boolean hasCorePhase() {
    return coreStage != null;
  }

  /** Main engine cutoff, ending the gravity turn. */
  public AbsoluteDate mecoDate() {
    return kickDate.shiftedBy(transitionTime);
  }

  /**
   * Earliest MECO that still completes first-stage staging: burn 1 run to depletion plus the
   * interstage settling coast. The second burn has zero duration exactly at this time.
   */
  public double stagingCompleteTime() {
    double corePhase = hasCorePhase() ? BOOSTER_SEPARATION_COAST + coreBurnDuration : 0.0;
    return burn1Duration + corePhase + interstageCoast;
  }

  /** Spacecraft mass immediately after the first stage is dropped (the stack above it). */
  public double massAfterJettison() {
    return firstStage.massAfterJettison();
  }

  /**
   * Mass floor guarding the whole ascent: the post-jettison stack floor. A single detector at this
   * floor covers both burns — during burn 1 the mass stays above the first stage's own floor, which
   * is above this one (spec 06 I4a).
   */
  public double depletionFloor() {
    return secondStage.depletionFloor();
  }

  /**
   * Whether this ascent steers into a commanded plane rather than following the one the pitch kick
   * left behind. Drives which {@code GravityTurnAttitudeProvider} the burn phases install, and is
   * the seam that keeps the calibrated due-east trajectories bit-for-bit (spec §4.2).
   *
   * @return {@code true} when a target plane normal is carried
   */
  public boolean hasCommandedPlane() {
    return commandedPlaneNormal != null;
  }
}
