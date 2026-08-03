package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import org.orekit.time.AbsoluteDate;

/**
 * The fully dated schedule of one gravity-turn ascent: the decoded CMA-ES variables, the burn
 * durations derived from them, and every date the ascent hangs off.
 *
 * <p><b>Why it exists.</b> The ascent is one propagation today but becomes three phases (spec
 * {@code specs/mission-stages/01-separations-implicites.md} §4.2): {@code Gravity turn (S1) → S1
 * separation → Gravity turn (S2)}. Those phases only reproduce today's trajectory if they agree
 * to the millisecond on when the first stage stops thrusting, when it is dropped, and when the
 * second ignites — so the dates must be computed <b>once</b>, not re-derived per phase. This
 * record is that single computation; {@link
 * com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver#plan} is the only place
 * that produces one.
 *
 * <p><b>Date arithmetic is deliberately literal.</b> The accessors below reproduce the exact
 * chain of {@link AbsoluteDate#shiftedBy(double)} calls the single-propagator configuration used,
 * epsilon by epsilon. {@code AbsoluteDate} stores its offset in two parts, so {@code
 * a.shiftedBy(x).shiftedBy(y)} and {@code a.shiftedBy(x + y)} are not the same bits: collapsing
 * these chains would move the burn boundaries by femtoseconds and take the refactor off its
 * iso-trajectory guarantee for no gain.
 *
 * @param kickDate the pitch-kick date, anchor of the pitch law and origin of every date below
 * @param transitionTime total gravity turn duration up to MECO (s) — CMA-ES variable 0
 * @param exponent power-law exponent controlling the pitch-over profile — CMA-ES variable 1
 * @param burn1Duration first-stage burn duration to propellant exhaustion (s)
 * @param interstageCoast unpowered coast between jettison and second-stage ignition (s)
 * @param burn2Duration second-stage burn duration after the coast, floored at 0 (s)
 * @param maxStepSeconds integrator max step keeping the late-ignition invariant for the ascent
 * @param firstStage the vehicle stage active at gravity-turn entry (the one jettisoned)
 * @param secondStage the vehicle stage active after jettison
 */
public record AscentPlan(
    AbsoluteDate kickDate,
    double transitionTime,
    double exponent,
    double burn1Duration,
    double interstageCoast,
    double burn2Duration,
    double maxStepSeconds,
    ActiveStageInfo firstStage,
    ActiveStageInfo secondStage) {

  /**
   * Settling offset placed before every ignition and after the jettison. Orekit brackets an event
   * at the exact boundary ambiguously, so each transition is nudged clear of the previous one.
   */
  private static final double SETTLING_EPSILON = 1.0e-3;

  /** Ignition of the first-stage burn, a settling epsilon after the pitch kick. */
  public AbsoluteDate firstIgnitionDate() {
    return kickDate.shiftedBy(SETTLING_EPSILON);
  }

  /** Jettison of the first stage: first-stage burnout plus a settling epsilon. */
  public AbsoluteDate jettisonDate() {
    return firstIgnitionDate().shiftedBy(burn1Duration).shiftedBy(SETTLING_EPSILON);
  }

  /** Ignition of the second-stage burn, one interstage coast after the jettison. */
  public AbsoluteDate secondIgnitionDate() {
    return jettisonDate().shiftedBy(interstageCoast).shiftedBy(SETTLING_EPSILON);
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
    return burn1Duration + interstageCoast;
  }

  /** Spacecraft mass immediately after the first stage is dropped (the stack above it). */
  public double massAfterJettison() {
    return firstStage.massAfterJettison();
  }

  /**
   * Mass floor guarding the whole ascent: the post-jettison stack floor. A single detector at this
   * floor covers both burns — during burn 1 the mass stays above the first stage's own floor,
   * which is above this one (spec 06 I4a).
   */
  public double depletionFloor() {
    return secondStage.depletionFloor();
  }
}
