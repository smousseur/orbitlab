package com.smousseur.orbitlab.simulation.mission.planner;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.operation.MissionComposer;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.progress.MissionProgressListener;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionLoadEvaluator;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionPerformanceReport;
import com.smousseur.orbitlab.simulation.mission.runtime.PropellantLoadOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.StagePerformance;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagePropellant;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.orekit.time.AbsoluteDate;

/**
 * {@link MissionPlanner} that sizes the top stage by <b>measuring it in flight</b>: it flies the
 * mission at its budgeted loads, reads the ΔV the top stage actually delivered, resizes the stage
 * for exactly that, and flies again (PHY-2 / L4, spec {@code
 * docs/atmosphere/11-conception-L4-PHY-2.md}).
 *
 * <p><b>What it replaces.</b> {@code PropellantBudget.sizeTopStage} hands the top stage the ideal
 * chain's remainder plus a universal 1 300 m/s reserve, because off-flight it cannot know the
 * hand-over state. That reserve is the worst case and every budgeted mission paid it — +18 to +66 %
 * of upper-stage load, carried as dead propellant ({@code DT-19}). Here the number is measured on
 * the mission itself, so it is that mission's own.
 *
 * <p><b>Why a flight and not a better formula.</b> In a direct LEO the ideal ascent delivers
 * straight to the target, so the top stage's whole job is the correction of an imperfect hand-over
 * — the part that has no closed form. The ascent over-delivers, and by how much is a property of
 * the flown gravity turn, not of the catalog.
 *
 * <p><b>Third planner beside {@link FixedLoadPlanner} and {@link MinimizedLoadPlanner}</b>, and
 * deliberately much cheaper than the latter: {@code MinimizedLoadPlanner} wraps a coordinate-wise
 * CMA-ES sweep over loads, each evaluation a full mission optimization. This one converges in two
 * flights — the second being the plan it returns, so no flight is wasted — and spends more only
 * where it has to, on a profile whose loop runs the stage dry (see {@link #MAX_BRACKET_PASSES}).
 *
 * <p><b>Sizing always runs in {@link OptimizationType#FAST}.</b> The right load is a property of
 * the vehicle, not of the flight mode, and the optimized transfer does not spend more ΔV than the
 * analytic one for the same target. A {@code BALANCED} mission therefore sizes over FAST flights
 * and pays a single {@code BALANCED} flight at the end, rather than iterating on the expensive
 * path.
 */
public final class MeasuredLoadPlanner implements MissionPlanner {
  private static final Logger logger = LogManager.getLogger(MeasuredLoadPlanner.class);

  /**
   * Upper edge of the accepted residual band, as a fraction of the sized stage's own load.
   *
   * <p>A stage sized on its measured ΔV lands at roughly {@link PropellantBudget#SAFETY_MARGIN}
   * worth of residual — the margin is the only thing it carries beyond what it burnt. This edge
   * sits well above that so a correctly sized stage is accepted on the first look, and well below
   * what the 1 300 m/s reserve leaves, so an over-provisioned seed is always rejected.
   *
   * <p><b>Initial value, to be posed by measurement</b> like every other number of this chantier.
   * Unlike the floor below it, this one is a genuine knob: the floor is a flame-out detector with a
   * cliff under it, this edge decides how much dead propellant is tolerated before paying another
   * flight.
   */
  public static final double RESIDUAL_BAND_UPPER_RATIO = 0.25;

  /**
   * Flights the sizing may spend before giving up and keeping the last feasible one.
   *
   * <p>{@code DT-19} bets on two — size, fly, resize — and warns why it may not be exact: resizing
   * changes the mass, therefore the ascent, therefore the ΔV the top stage delivers. Three leaves
   * room for one correction of that coupling; beyond it the loop is not converging and another
   * flight is unlikely to help.
   *
   * <p><b>This budget is for the ΔV-driven passes alone.</b> A loop that runs its stage dry has
   * measured something the formula cannot use but a bracket can, and it may spend {@link
   * #MAX_BRACKET_PASSES} more — see there.
   */
  public static final int MAX_SIZING_PASSES = 3;

  /**
   * Extra flights the sizing may spend <b>once a pass has run the sized stage dry</b>, narrowing
   * the bracket that flame-out opens.
   *
   * <p>A dry pass and a feasible one bound the answer between them, and that bracket is strictly
   * more informative than the ΔV formula, because it is made of outcomes actually flown rather than
   * of an extrapolation the coupling invalidates. Spending flights on it is therefore paid for only
   * by the profiles that need it: a loop that converges inside the band never opens a bracket and
   * this budget stays unspent, which is why the Falcon Heavy is untouched by construction.
   *
   * <p><b>What it buys, flown 2026-09-12</b> (spec {@code docs/atmosphere/13-cloture-PHY-2.md}
   * §5.3). On the Ariane 64 LEO-400 drag-on profile the loop ends dry with the answer bracketed in
   * {@code [246, 3 804] kg} and used to return the rich end. The three probes land on 967.4, 487.8
   * and <b>346.5 kg</b>, all feasible: {@code 3 804 -> 346 kg} for the same orbit to the decimal
   * (399.3 x 420.3 km), <b>3 438 kg</b> of dead propellant given back, the computation going from
   * 140 s to 265 s. An independent flight of the same profile at another core ISP had converged on
   * 215 kg, the order the bisection walks to.
   *
   * <p><b>Three is one short of this planner's own stopping rule, knowingly.</b> The probes took
   * the bracket ratio from 15.5 to 1.41, above {@link #BRACKET_TIGHT_RATIO}: the loop stopped on
   * the budget, not because there was nothing left to measure, and the retained pass sits at 27.0 %
   * residual, just past the band. A fourth probe would fly 292.0 kg and, on the consumption
   * measured at 346 kg, should land inside the band — for about 54 kg and one more flight. Raise
   * this to 4 to take it; the number here is the one that has been flown.
   */
  public static final int MAX_BRACKET_PASSES = 3;

  /**
   * Bracket ratio under which another bisection would measure nothing: the two ends are then closer
   * to each other than the width of the accepted residual band itself, so the flight that separated
   * them could not tell them apart.
   */
  private static final double BRACKET_TIGHT_RATIO = 1.25;

  /**
   * Residual, as a fraction of the sized stage's load, under which a pass is taken to have run that
   * stage dry and is therefore not eligible to be the plan returned.
   *
   * <p><b>Deliberately far below {@link MissionLoadEvaluator#DEFAULT_RESIDUAL_FLOOR_RATIO}</b>,
   * because the two answer different questions. That floor decides <em>should another flight be
   * paid to resize this stage</em>; this one decides <em>did the trim burn have anything left to
   * fire</em>. Reusing the first for the second was measured to be expensive: on the Ariane 64
   * LEO-400 profile flown in vacuum the third pass came back at 0.37 % residual (1 kg of 273) and
   * had inserted at e = 1.8e-5 — a clean circular orbit — yet the 1 % floor discarded it in favour
   * of a pass carrying 2 349 kg, for 2 068 kg of dead propellant and a marginally worse
   * eccentricity.
   *
   * <p><b>Placed inside the measured bracket</b> (2026-09-12): the two passes that had to be
   * separated sat at 0.0 % — stage emptied, e = 3.9e-4 — and 0.37 % — e = 1.8e-5. This threshold
   * sits between them, an order of magnitude under the one that must be kept. It is a proxy posed
   * by measurement, not a physical limit: a pass landing between it and the resize floor is the
   * signal to move it, or to judge the pass on the orbit it delivered instead.
   */
  public static final double DRY_STAGE_RESIDUAL_RATIO = 0.001;

  /** Relative load change below which another flight would measure the same thing. */
  private static final double NEGLIGIBLE_LOAD_CHANGE_RATIO = 1.0e-3;

  private final MissionSpec spec;
  private final OptimizationType mode;
  private final AbsoluteDate launchEpoch;
  private final int maxEvaluations;
  private final Long seed;

  /** Progress sink handed to each flight's mission optimizer, or {@code null}. */
  private final MissionProgressListener progress;

  /**
   * @param spec the mission description the loads are sized for
   * @param mode the optimization mode the returned plan must be flown in
   * @param launchEpoch the launch date each flight's initial state is built at
   * @param maxEvaluations the per-stage CMA-ES evaluation budget of each flight
   * @param seed the CMA-ES master seed, or {@code null} for non-deterministic
   * @param progress the sink, or {@code null}
   */
  public MeasuredLoadPlanner(
      MissionSpec spec,
      OptimizationType mode,
      AbsoluteDate launchEpoch,
      int maxEvaluations,
      Long seed,
      MissionProgressListener progress) {
    this.spec = Objects.requireNonNull(spec, "spec");
    this.mode = Objects.requireNonNull(mode, "mode");
    this.launchEpoch = Objects.requireNonNull(launchEpoch, "launchEpoch");
    this.maxEvaluations = maxEvaluations;
    this.seed = seed;
    this.progress = progress;
  }

  @Override
  public MissionPlan plan() {
    int sizedStage = sizedStageIndex();
    double[] budgeted = spec.configuration().propellantLoads();

    double[] loads = budgeted;
    double[] flown = loads;
    MissionPlan sizingPlan = null;
    int passes = 0;

    // The cheapest pass whose sized stage did NOT run dry, and the richest load that did. Kept so
    // the loop cannot end on a plan it has itself judged infeasible (the fallback below), and
    // because together they bracket the answer (the bisection below).
    MissionPlan cheapestFeasible = null;
    double[] cheapestFeasibleLoads = null;
    double richestDryLoad = 0.0;

    int budget = MAX_SIZING_PASSES;
    for (int pass = 1; pass <= budget; pass++) {
      sizingPlan = fly(loads, OptimizationType.FAST);
      flown = loads;
      passes = pass;
      if (sizedStage < 0) {
        break;
      }
      if (hadPropellantLeft(sizingPlan, sizedStage)) {
        if (cheapestFeasibleLoads == null
            || flown[sizedStage] < cheapestFeasibleLoads[sizedStage]) {
          cheapestFeasible = sizingPlan;
          cheapestFeasibleLoads = flown;
        }
      } else {
        richestDryLoad = FastMath.max(richestDryLoad, flown[sizedStage]);
        budget = MAX_SIZING_PASSES + MAX_BRACKET_PASSES;
      }
      if (residualWithinBand(sizingPlan, sizedStage)) {
        break;
      }
      // Once both ends are in hand the bracket governs, and the ΔV formula is set aside: the
      // formula extrapolates from a trajectory the next load will change, the bracket only states
      // what was flown. This can only improve the answer — the plan finally returned is still the
      // cheapest feasible pass seen, and bisection adds feasible passes below it, never above.
      boolean bracketed = cheapestFeasibleLoads != null && richestDryLoad > 0;
      Optional<double[]> next =
          bracketed
              ? bisected(cheapestFeasibleLoads, richestDryLoad, sizedStage)
              : resized(sizingPlan, flown, sizedStage);
      if (next.isEmpty()) {
        break;
      }
      loads = next.get();
    }

    // ── The pass budget must not hand back a starved stage ──────────────────
    //
    // The loop above exits either because the residual landed in the band — then the last pass is
    // the right one and nothing here fires — or because the pass budget ran out. In that second
    // case it used to return the last flight whatever its residual, including one below the floor,
    // i.e. a stage that ran dry and left the trim burn nothing to fire. Measured 2026-09-12 on the
    // Ariane 64 LEO-400 profile, where the loop diverges instead of converging:
    //
    //   pass 1: 18 048 kg loaded, residual 66.5 %  ->  mean orbit 409 666 x 409 923 m, e = 1.9e-5
    //   pass 2:  3 804 kg loaded, residual 92.8 %  ->  mean orbit 409 672 x 409 919 m, e = 1.8e-5
    //   pass 3:    246 kg loaded, residual  0.0 %  ->  mean orbit 404 333 x 409 681 m, e = 3.9e-4
    //
    // The 15-fold drop into pass 3 is the coupling MAX_SIZING_PASSES already warns about: the
    // 272 kg pass 2 measured were burnt on a trajectory the previous resizing had itself changed —
    // at those loads the gravity turn stops relighting the upper stage (burn 2 falls to 0 s, MECO
    // from 620 s to 457 s), so the measurement no longer describes what the stage has to do. A
    // Falcon Heavy never reaches here: it converges inside the band on pass 2.
    //
    // The preference is deliberately asymmetric. An over-provisioned stage costs dead mass; a
    // starved one costs the orbit, which is the deliverable. So on exhaustion the plan returned is
    // the cheapest pass that still had propellant left, and the sizing says so rather than
    // reporting a convergence it did not reach. Since 2026-09-12 the flights of MAX_BRACKET_PASSES
    // make that cheapest pass a bisected one rather than whatever the formula last produced, so
    // this fallback keeps its role and costs far less dead mass than it used to.
    //
    // "Still had propellant left" is DRY_STAGE_RESIDUAL_RATIO and not the resize floor, for a
    // reason this fallback was measured to need: on the same profile flown in vacuum the third pass
    // came back at 0.37 % residual having inserted cleanly, and rejecting it on the 1 % floor cost
    // 2 068 kg of dead propellant for nothing.
    if (sizedStage >= 0 && cheapestFeasible != null && !hadPropellantLeft(sizingPlan, sizedStage)) {
      logger.warn(
          "Sizing did not converge in {} passes: the last one left stage {} dry ({} kg loaded). "
              + "Keeping the cheapest pass that did not, at {} kg — the answer is bracketed in "
              + "[{}, {}] kg and another flight would narrow it.",
          passes,
          sizedStage,
          String.format(Locale.ROOT, "%.0f", flown[sizedStage]),
          String.format(Locale.ROOT, "%.0f", cheapestFeasibleLoads[sizedStage]),
          String.format(Locale.ROOT, "%.0f", richestDryLoad),
          String.format(Locale.ROOT, "%.0f", cheapestFeasibleLoads[sizedStage]));
      sizingPlan = cheapestFeasible;
      flown = cheapestFeasibleLoads;
    }

    logSizing(budgeted, flown, sizedStage, passes, sizingPlan);

    // The sizing flights run in FAST; only a different requested mode owes one more flight, at the
    // loads the sizing settled on.
    MissionPlan result = mode == OptimizationType.FAST ? sizingPlan : fly(flown, mode);
    return new MissionPlan(result.computation(), sizing(budgeted, flown, passes));
  }

  /** Flies the mission once at the given loads, in the given mode. */
  private MissionPlan fly(double[] loads, OptimizationType flightMode) {
    Mission mission = MissionComposer.compose(spec.withLauncherLoads(loads), flightMode);
    // MissionOptimizer reads mission.getCurrentState() as the launch epoch, exactly as the
    // fixed-load path seeds it.
    mission.setCurrentState(mission.getInitialState(launchEpoch));
    return new FixedLoadPlanner(mission, maxEvaluations, seed, progress).plan();
  }

  /**
   * Whether this flight's sized stage came back inside the accepted residual band, which is the
   * loop's one real convergence: the load is right and no further flight is owed.
   *
   * <p>Hoisted out of {@link #resized} when the bisection arrived, because it is now the exit of
   * <em>both</em> ways of producing the next load and not of one of them.
   *
   * @param plan the flight to read
   * @param sizedStage the stack index of the stage being sized
   * @return {@code true} when the residual is inside the band; {@code false} when it is outside, or
   *     when there is no per-stage split to read — absence of a measurement is not convergence
   */
  private static boolean residualWithinBand(MissionPlan plan, int sizedStage) {
    StagePropellant sized =
        plan.computation().performanceReport().residualForStage(sizedStage).orElse(null);
    if (sized == null || !(sized.loaded() > 0)) {
      return false;
    }
    double ratio = sized.residualRatio();
    return ratio >= MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO
        && ratio <= RESIDUAL_BAND_UPPER_RATIO;
  }

  /**
   * The next load to try inside the bracket a flame-out opened, or empty when the two ends are too
   * close for a flight to tell them apart.
   *
   * <p><b>Geometric and not arithmetic.</b> A propellant load is a scale, and the bracket a failing
   * loop opens spans an order of magnitude — {@code [246, 3 804] kg} measured on the Ariane 64
   * LEO-400 drag-on profile. The arithmetic midpoint would spend every flight in the rich half and
   * approach the answer linearly; the geometric one takes the square root of the bracket's
   * <em>ratio</em> each time, which is the quantity that actually has to shrink.
   *
   * <p>Only the sized stage moves: every load array this planner flies carries the lower stages at
   * capacity, so the candidate is the feasible one with a single component replaced.
   *
   * @param feasibleLoads the cheapest loads flown whose sized stage still had propellant left
   * @param dryLoad the richest load flown that ran the sized stage dry
   * @param sizedStage the stack index of the stage being sized
   * @return the loads to fly next, or empty when the bracket is already tight
   */
  // Package-private rather than private so the arithmetic can be pinned without a flight, as
  // PropellantBudget.ascentDeltaV already is.
  static Optional<double[]> bisected(double[] feasibleLoads, double dryLoad, int sizedStage) {
    double feasible = feasibleLoads[sizedStage];
    if (!(feasible > dryLoad * BRACKET_TIGHT_RATIO)) {
      return Optional.empty();
    }
    double[] candidate = feasibleLoads.clone();
    candidate[sizedStage] = FastMath.sqrt(dryLoad * feasible);
    return Optional.of(candidate);
  }

  /**
   * The loads to fly next from the ΔV the top stage delivered, or empty when no useful load can be
   * derived — no per-stage split to read, or a measurement that carries no information.
   */
  private Optional<double[]> resized(MissionPlan plan, double[] flown, int sizedStage) {
    MissionPerformanceReport report = plan.computation().performanceReport();
    StagePropellant sized = report.residualForStage(sizedStage).orElse(null);
    if (sized == null || !(sized.loaded() > 0)) {
      return Optional.empty();
    }

    double deltaV = topStageDeltaV(report, plan.computation().mission().getVehicle(), sizedStage);
    if (!(deltaV > 0)) {
      return Optional.empty();
    }
    // Below the floor the stage ran dry, so the ΔV just measured is short of what it needed rather
    // than equal to it. Sizing on it still moves the load the right way: the margin is applied to a
    // ΔV the stage delivered on all of its propellant, which lands the new load above the old one.
    // That upward step is the margin, and it is why a flame-out recovers instead of sizing itself
    // down to nothing.
    double[] candidate =
        PropellantBudget.loadsForMeasuredTopStage(
            spec.configuration().launcher(), payloadMass(), deltaV);
    if (negligibleChange(flown[sizedStage], candidate[sizedStage])) {
      return Optional.empty();
    }
    return Optional.of(candidate);
  }

  /**
   * Whether the sized stage came back from this flight with propellant still aboard.
   *
   * <p>Read against {@link #DRY_STAGE_RESIDUAL_RATIO} and not against the resize floor: a stage
   * outside the band is merely mis-sized, one that is dry could not finish its trim burn, and only
   * the second is a reason to discard the flight. A plan whose per-stage split cannot be read
   * answers {@code true}: absence of a measurement is not evidence of a flame-out, and treating it
   * as one would discard a plan on no information.
   *
   * @param plan the flight to read
   * @param sizedStage the stack index of the stage being sized
   * @return {@code true} when the residual clears the dry threshold, or is unreadable
   */
  private static boolean hadPropellantLeft(MissionPlan plan, int sizedStage) {
    StagePropellant sized =
        plan.computation().performanceReport().residualForStage(sizedStage).orElse(null);
    if (sized == null || !(sized.loaded() > 0)) {
      return true;
    }
    return sized.residualRatio() >= DRY_STAGE_RESIDUAL_RATIO;
  }

  /**
   * The ΔV the sized stage delivered over the whole flight (m/s) — the sum over the mission stages
   * it flew, not just the insertion ones.
   *
   * <p><b>The whole flight and not only the insertion</b>, because the load has to cover everything
   * that stage burns: on a Falcon Heavy the upper stage lights during the ascent as well (burn 2),
   * and a number that left that out would size it short. Summing what it delivered gives its ascent
   * share, its insertion and its trim in one measurement, which is exactly what {@code
   * PropellantBudget.loadsForMeasuredTopStage} converts back into kilograms.
   *
   * <p>Each mission stage is attributed to the physical stage that flew it, resolved from the mass
   * at its entry. That is exact because no mission stage spans a jettison any more (spec {@code
   * docs/mission-stages/01-separations-implicites.md}), so one phase burns one stage's propellant
   * at one Isp. Non-propulsive phases report zero ΔV and drop out, which is what keeps separations
   * — whose entry mass still resolves to the stage being dropped — from being counted.
   */
  private static double topStageDeltaV(
      MissionPerformanceReport report, Vehicle vehicle, int sizedStage) {
    double total = 0.0;
    for (StagePerformance stage : report.stages()) {
      if (stage.deltaV() > 0
          && vehicle.resolveActiveStage(stage.massIn()).stageIndex() == sizedStage) {
        total += stage.deltaV();
      }
    }
    return total;
  }

  /**
   * The sizing metadata the plan carries, as scale factors against the budgeted loads.
   *
   * <p>Expressed as λ rather than as absolute kilograms so the existing plumbing applies: the
   * orchestrator multiplies them back the moment the plan lands, while both factors are
   * unambiguously in hand, and persists the product (spec {@code
   * docs/scenario/01-persistance-missions.md} §2.3). A stage the budget left empty keeps λ = 1
   * rather than dividing by zero.
   */
  private static PropellantSizing sizing(double[] budgeted, double[] flown, int passes) {
    double[] lambdas = new double[flown.length];
    for (int stage = 0; stage < lambdas.length; stage++) {
      lambdas[stage] = budgeted[stage] > 0 ? flown[stage] / budgeted[stage] : 1.0;
    }
    return new PropellantSizing(lambdas, passes, passes);
  }

  /** The stack index of the sized (last variable-load) stage, or -1 when no stage is sizeable. */
  private int sizedStageIndex() {
    boolean[] mask = PropellantLoadOptimizer.lambdaScaledMask(spec.configuration().launcher());
    for (int stage = mask.length - 1; stage >= 0; stage--) {
      if (mask[stage]) {
        return stage;
      }
    }
    return -1;
  }

  private double payloadMass() {
    return spec.configuration().payload().getMass();
  }

  private static boolean negligibleChange(double flown, double candidate) {
    double reference = FastMath.max(flown, candidate);
    return reference <= 0
        || FastMath.abs(candidate - flown) / reference < NEGLIGIBLE_LOAD_CHANGE_RATIO;
  }

  /**
   * Reports what the sizing settled on, <b>and whether that is a convergence</b>.
   *
   * <p>The second half is not decoration. A loop that ends inside the residual band has found the
   * load; one that ends outside it has merely run out of flights, and the two used to print the
   * same line. Measured 2026-09-12: the Ariane 64 LEO-400 drag-on profile ends at 27.0 % residual,
   * just past the band's 25 % edge, which is readable only if the line says so.
   */
  private static void logSizing(
      double[] budgeted, double[] flown, int sizedStage, int passes, MissionPlan plan) {
    if (sizedStage < 0) {
      logger.info(
          "Measured sizing: launcher has no variable-load top stage, loads left as budgeted");
      return;
    }
    StagePropellant sized =
        plan.computation().performanceReport().residualForStage(sizedStage).orElse(null);
    String outcome;
    if (sized == null || !(sized.loaded() > 0)) {
      outcome = "no residual to read";
    } else {
      outcome =
          String.format(
              Locale.ROOT,
              "residual %.1f %%, %s",
              100.0 * sized.residualRatio(),
              residualWithinBand(plan, sizedStage)
                  ? "inside the band"
                  : "outside the band — the pass budget ran out first");
    }
    logger.info(
        "Measured sizing over {} pass(es): sized stage [{}] {} kg budgeted -> {} kg flown ({}%),"
            + " {}",
        passes,
        sizedStage,
        FastMath.round(budgeted[sizedStage]),
        FastMath.round(flown[sizedStage]),
        String.format(
            Locale.ROOT,
            "%+.1f",
            budgeted[sizedStage] > 0
                ? 100.0 * (flown[sizedStage] / budgeted[sizedStage] - 1.0)
                : 0.0),
        outcome);
  }
}
