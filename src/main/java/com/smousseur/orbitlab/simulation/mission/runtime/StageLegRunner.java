package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.ArcTransition;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.gravity.SoiCrossingDetector;
import com.smousseur.orbitlab.simulation.gravity.SphereOfInfluence;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * Flies one {@link MissionStage} as a sequence of <b>legs</b>: one propagation per gravitational
 * context the stage passes through, cut wherever it crosses a sphere-of-influence boundary.
 *
 * <p>Extracted from the body of {@link StageChainRunner}'s loop by PHY-4 / L4 (spec {@code
 * docs/multi-corps/06-conception-L4.md} §4). The chain runner keeps its stage loop, its {@link
 * StageChainRunner.StageRun}, its listener and its statelessness; what moves here is the part that
 * has to become a loop of its own once a stage may change central body halfway through.
 *
 * <p><b>The delegation is unconditional, and that is the point.</b> A stage declaring no transition
 * flies a single leg through exactly the sequence of calls the chain runner used to make, in the
 * same order — so the L1 gate's 62 boundaries stay at a zero tolerance by construction rather than
 * by measurement, the way L2's empty perturber set did.
 *
 * <p><b>Stateless by contract</b>, for the reason {@link StageChainRunner} gives: CMA-ES explores in
 * parallel, so everything mutable lives in the caller's sampler or in a local of {@link #fly}.
 */
final class StageLegRunner {

  private static final Logger logger = LogManager.getLogger(StageLegRunner.class);

  /**
   * Most legs one stage may be cut into before the run is bounded and logged. A net rather than a
   * limit: the dead band of {@link SoiCrossingDetector} is what actually prevents chatter, and eight
   * legs is far more than a patched-conic transfer can legitimately need. Loud when it fires, for
   * the reason {@link StageChainRunner#FALLBACK_DURATION_SECONDS} is.
   */
  static final int MAX_LEGS_PER_STAGE = 8;

  /**
   * One propagation of one stage in one gravitational context.
   *
   * @param context the environment this leg was flown in
   * @param entryState the state it started from, expressed in {@code context}'s frame
   * @param exitState the state it ended on
   * @param crossedBoundary the body whose sphere of influence ended this leg, or {@code null} when
   *     the leg ran to the stage's end date
   */
  record Leg(
      GravitationalContext context,
      SpacecraftState entryState,
      SpacecraftState exitState,
      SolarSystemBody crossedBoundary) {}

  /** What a {@link SoiCrossingDetector} handler recorded: which sphere, and when. */
  private record Crossing(SolarSystemBody body, AbsoluteDate date) {}

  /**
   * The date a stage's propagation is asked to reach, and whether that date is the stage's own
   * cutoff — only then does stopping early mean anything.
   *
   * @param date the date to propagate to
   * @param isStageCutoff whether it is the stage's own configured cutoff
   */
  record EndDate(AbsoluteDate date, boolean isStageCutoff) {}

  /**
   * Resolves how far a stage is propagated.
   *
   * <p><b>It is a callback and not a parameter because of an ordering constraint</b>: a stage
   * publishes {@link MissionStage#getConfiguredEndDate()} from inside {@code configure()}, so the
   * end date cannot be known before the first leg has been configured. The policy itself — last
   * stage, own cutoff, or safety net — stays in {@link StageChainRunner}, which is where the two
   * runners differ.
   */
  @FunctionalInterface
  interface EndDateResolver {
    EndDate resolve(MissionStage stage, SpacecraftState stageEntry);
  }

  /**
   * What flying one stage produced.
   *
   * @param legs the legs flown, in order; never empty
   * @param endDate the date propagation was asked to reach
   * @param endDateIsStageCutoff whether that date is the stage's own cutoff
   * @param failure the exception a propagation threw, or {@code null} — reported rather than acted
   *     on, since the two chain runners disagree about what to do with it
   */
  record StageFlight(
      List<Leg> legs, AbsoluteDate endDate, boolean endDateIsStageCutoff, Exception failure) {

    Leg lastLeg() {
      return legs.get(legs.size() - 1);
    }
  }

  private final StageChainRunner.StepSampler sampler;
  private final boolean quietReentryGuard;
  private final EndDateResolver endDateResolver;

  /**
   * @param sampler receives every fixed-step sample, or {@code null} to fly without sampling
   * @param quietReentryGuard whether the re-entry guard is armed without its log — true on the
   *     optimize pass, where an infeasible candidate re-entering is a normal outcome
   * @param endDateResolver how far to propagate, resolved after the stage has configured itself
   */
  StageLegRunner(
      StageChainRunner.StepSampler sampler,
      boolean quietReentryGuard,
      EndDateResolver endDateResolver) {
    this.sampler = sampler;
    this.quietReentryGuard = quietReentryGuard;
    this.endDateResolver = endDateResolver;
  }

  /**
   * Flies the stage from {@code stageEntry}, returning one leg per context traversed — at least one,
   * always.
   *
   * @param stage the stage to fly
   * @param stageEntry the state the stage starts from
   * @param mission the parent mission
   * @return the flight
   */
  StageFlight fly(MissionStage stage, SpacecraftState stageEntry, Mission mission) {

    GravitationalContext context = stage.gravitationalContext(mission);
    Set<SolarSystemBody> transitions = stage.soiTransitions(mission);

    if (!transitions.isEmpty() && stage.isPropulsive()) {
      throw new IllegalStateException(
          "stage '"
              + stage.getName()
              + "' is propulsive and declares SOI transitions "
              + transitions
              + "; a burn cannot straddle a boundary (spec docs/multi-corps/06-conception-L4.md"
              + " §3.3)");
    }

    // The seam L3 §1 observed nobody was checking: a stage declares a context, the state it is
    // handed is expressed in some frame, and until now nothing tied the two. Aligning here makes it
    // true by contract. The comparison inside convert() is REFERENCE equality, so a state already in
    // the declared frame is returned untouched and no existing trajectory crosses an identity
    // transform — which is what keeps the L1 gate bit-identical (spec L4 §3.5).
    SpacecraftState legEntry = ArcTransition.convert(stageEntry, context);

    // Sized once, from the stage entry, exactly as the chain runner sized it. A switch only happens
    // on a non-propulsive stage (spec L4 §3.3), where this is COAST_MAX_STEP whatever the state.
    double maxStep = stage.maxStepSeconds(stageEntry, mission);
    double sampleStep = stage.sampleStepSeconds(stageEntry, mission);

    List<Leg> legs = new ArrayList<>(1);
    EndDate endDate = null;

    while (true) {
      NumericalPropagator propagator =
          OrekitService.get().createOptimizationPropagator(context, maxStep);
      propagator.setInitialState(legEntry);

      // Re-entry guard on every leg of every stage, armed with the leg's own context: the floor is
      // measured against the body actually being flown around. Loud only on the sampling pass — see
      // ReentryGuard for why.
      if (quietReentryGuard) {
        ReentryGuard.armQuiet(propagator, context);
      } else {
        ReentryGuard.arm(propagator, stage.getName(), context);
      }

      stage.configure(propagator, mission);

      if (sampler != null && sampleStep > 0.0) {
        GravitationalContext legContext = context;
        propagator
            .getMultiplexer()
            .add(sampleStep, state -> sampler.sample(stage, legContext, state));
      }

      AtomicReference<Crossing> crossed = new AtomicReference<>();
      armBoundaries(propagator, context, transitions, crossed);

      // Resolved once, on the first leg: a stage publishes its cutoff from configure(), and every
      // later leg propagates to that same absolute date.
      if (endDate == null) {
        endDate = endDateResolver.resolve(stage, stageEntry);
      }

      SpacecraftState exit;
      try {
        exit = propagator.propagate(endDate.date());
      } catch (Exception e) {
        legs.add(new Leg(context, legEntry, legEntry, null));
        return new StageFlight(legs, endDate.date(), endDate.isStageCutoff(), e);
      }

      Crossing crossing = crossed.get();
      boolean stoppedOnBoundary =
          crossing != null && Math.abs(exit.getDate().durationFrom(crossing.date())) < 1.0e-6;

      if (!stoppedOnBoundary) {
        legs.add(new Leg(context, legEntry, exit, null));
        return new StageFlight(legs, endDate.date(), endDate.isStageCutoff(), null);
      }

      legs.add(new Leg(context, legEntry, exit, crossing.body()));

      // The outgoing sample, in the frame that is being left. Without it the last recorded point of
      // this arc is the previous sampling step — a whole coast step short of the boundary, hundreds
      // of kilometres at transfer speed. The incoming sample is produced by the next leg's own
      // multiplexer at its first step, so the boundary is one instant written twice, once per frame
      // (spec L4 §5).
      if (sampler != null) {
        sampler.sample(stage, context, exit);
      }

      if (legs.size() >= MAX_LEGS_PER_STAGE) {
        logger.warn(
            "Stage '{}' was cut into {} legs by SOI crossings; bounding it there. A grazing"
                + " trajectory chattering across the boundary is the expected cause.",
            stage.getName(),
            legs.size());
        return new StageFlight(legs, endDate.date(), endDate.isStageCutoff(), null);
      }

      context = ArcTransition.across(context, crossing.body());
      legEntry = ArcTransition.convert(exit, context);
    }
  }

  /**
   * Arms one {@link SoiCrossingDetector} per declared boundary, in {@code STOP}.
   *
   * <p><b>The direction decides the threshold.</b> Entering a sphere is decided at its radius;
   * leaving is decided at the radius plus the dead band, because a leg that has just switched starts
   * <em>on</em> the sphere and a detector re-armed on the same radius would see a sign decided by
   * rounding (spec L4 §4.4).
   */
  private static void armBoundaries(
      NumericalPropagator propagator,
      GravitationalContext context,
      Set<SolarSystemBody> transitions,
      AtomicReference<Crossing> crossed) {

    for (SolarSystemBody boundaryBody : transitions) {
      SphereOfInfluence soi = SphereOfInfluence.of(boundaryBody);
      double scale =
          context.body() == boundaryBody ? 1.0 + SoiCrossingDetector.EXIT_DEAD_BAND : 1.0;
      propagator.addEventDetector(
          new SoiCrossingDetector(soi, scale)
              .withHandler(
                  (state, detector, increasing) -> {
                    crossed.set(new Crossing(boundaryBody, state.getDate()));
                    return Action.STOP;
                  }));
    }
  }
}
