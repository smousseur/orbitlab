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
 * flights — the second being the plan it returns, so no flight is wasted.
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
   */
  public static final int MAX_SIZING_PASSES = 3;

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

    for (int pass = 1; pass <= MAX_SIZING_PASSES; pass++) {
      sizingPlan = fly(loads, OptimizationType.FAST);
      flown = loads;
      passes = pass;
      if (sizedStage < 0) {
        break;
      }
      Optional<double[]> next = resized(sizingPlan, flown, sizedStage);
      if (next.isEmpty()) {
        break;
      }
      loads = next.get();
    }

    logSizing(budgeted, flown, sizedStage, passes);

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
   * The loads to fly next, or empty when the flown ones are already right — the sized stage's
   * residual sitting inside the band, no per-stage split to read, or a measurement that carries no
   * information.
   */
  private Optional<double[]> resized(MissionPlan plan, double[] flown, int sizedStage) {
    MissionPerformanceReport report = plan.computation().performanceReport();
    StagePropellant sized = report.residualForStage(sizedStage).orElse(null);
    if (sized == null || !(sized.loaded() > 0)) {
      return Optional.empty();
    }
    double ratio = sized.residualRatio();
    if (ratio >= MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO
        && ratio <= RESIDUAL_BAND_UPPER_RATIO) {
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

  private static void logSizing(double[] budgeted, double[] flown, int sizedStage, int passes) {
    if (sizedStage < 0) {
      logger.info(
          "Measured sizing: launcher has no variable-load top stage, loads left as budgeted");
      return;
    }
    logger.info(
        "Measured sizing over {} pass(es): sized stage [{}] {} kg budgeted -> {} kg flown ({}%)",
        passes,
        sizedStage,
        FastMath.round(budgeted[sizedStage]),
        FastMath.round(flown[sizedStage]),
        String.format(
            Locale.ROOT,
            "%+.1f",
            budgeted[sizedStage] > 0
                ? 100.0 * (flown[sizedStage] / budgeted[sizedStage] - 1.0)
                : 0.0));
  }
}
