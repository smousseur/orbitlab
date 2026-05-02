package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransferResult;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransfertTwoManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.TrajectoryProblem;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.Constants;

/**
 * Optimization problem for a two-burn orbit transfer where only burn 1 is optimized.
 *
 * <p>Burn 1 (4 CMA-ES parameters) places the spacecraft on an elliptical transfer orbit. Burn 2 is
 * a deterministic prograde circularization at apoapsis, computed by the maneuver class.
 *
 * <p>The cost function evaluates the <b>final</b> orbit after both burns — this is what matters.
 * Because burn 2 is deterministic, CMA-ES only needs to find the burn 1 parameters that produce a
 * transfer orbit from which the circularization yields the best circular orbit.
 *
 * <p>Parameter vector (4 dimensions):
 *
 * <ul>
 *   <li>[0] t1 — offset of burn 1 start from epoch (s)
 *   <li>[1] dt1 — duration of burn 1 (s)
 *   <li>[2] alpha1 — in-plane thrust angle in TNW frame (rad)
 *   <li>[3] beta1 — out-of-plane thrust angle in TNW frame (rad)
 * </ul>
 */
public class TransferTwoManeuverProblem implements TrajectoryProblem {
  private static final Logger logger = LogManager.getLogger(TransferTwoManeuverProblem.class);
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  private final TransfertTwoManeuver maneuver;
  private final SpacecraftState initialState;
  private TransferResult lastResult;

  // ── Primary objective weights ──
  private static final double W_APO = 3.0;
  private static final double W_PERI = 10.0;
  private static final double W_E_BASE = 2.0;
  private static final double W_V = 1.0;

  // Reference altitude for adaptive eccentricity weighting (Niveau 2.4)
  private static final double W_E_REF_ALT = 400_000.0;

  // Absolute-error scale (Niveau 2.4): keeps small absolute deviations
  // meaningful at high altitudes where relative errors become tiny.
  private static final double ABS_ERR_SCALE = 50_000.0;
  private static final double W_APO_ABS = 0.05;
  private static final double W_PERI_ABS = 0.15;

  // ── Constraint barrier weight ──
  private static final double W_BARRIER = 0.1;
  private static final double W_ALT_MAX = 1.0;

  // ── Constraint thresholds ──
  private static final double ALT_MIN = 80_000;
  private static final double PERIAPSIS_FLOOR_MIN = 120_000;

  // Niveau 2.3 — burn-1 duration multiplier on the Hohmann estimate
  private static final double DT1_MAX_MULTIPLIER = 4.0;
  // Niveau 2.3 — t1 upper bound as a fraction of the post-GT orbital period
  private static final double T1_MAX_PERIOD_FRACTION = 0.5;

  private final double altMax;

  // Precomputed values
  private final double aTarget;
  private final double vCircTarget;
  private final double effectiveTargetAlt;

  // Hohmann-like guess values (precomputed)
  private final double guessT1;
  private final double guessDt1;

  // Physical upper bound on burn 1 duration (from available propellant)
  private final double dt1MaxPhysical;

  // Propulsion characteristics (kept for post-mortem Δv breakdown diagnostics)
  private final double thrust;
  private final double isp;

  // Niveau 2.1 — adaptive β1 bound derived from post-GT apoapsis defect
  private final double betaMax;

  // Niveau 2.3 — adaptive t1 upper bound (fraction of post-GT orbital period)
  private final double t1Max;

  // Niveau 2.3 — burn 1 duration upper bound (Hohmann × K, capped by propellant)
  private final double dt1Max;

  // Niveau 2.4 — adaptive periapsis floor and eccentricity weight
  private final double periapsisFloor;
  private final double weightE;

  /**
   * Creates a two-burn transfer optimization problem.
   *
   * <p>Precomputes Hohmann-like initial guesses for burn timing and duration, applies a J2
   * short-period altitude compensation to the target, and determines physical upper bounds on burn
   * duration from available propellant.
   *
   * @param maneuver the two-burn transfer maneuver that handles propagation
   * @param initialState the spacecraft state at the start of the transfer
   * @param targetAltitude the desired circular orbit altitude in meters above the Earth's surface
   * @param propulsionSystem the propulsion system used for the transfer burns
   * @param vehicleMinMass minimum allowable vehicle mass after burns (dry mass)
   */
  public TransferTwoManeuverProblem(
      TransfertTwoManeuver maneuver,
      SpacecraftState initialState,
      double targetAltitude,
      PropulsionSystem propulsionSystem,
      double vehicleMinMass) {

    this.initialState = initialState;
    this.maneuver = maneuver;
    KeplerianOrbit initialOrbit = new KeplerianOrbit(initialState.getOrbit());
    double mu = initialOrbit.getMu();

    // ── J2 short-period altitude compensation ──
    // The osculating radius oscillates around the mean with amplitude ~J2*Re²/a
    // To center the geodetic altitude excursions on targetAltitude,
    // we target a slightly higher mean altitude
    double rNominal = EARTH_RADIUS + targetAltitude;
    double j2 = 1.0826e-3; // J2 coefficient
    double sinI = FastMath.sin(initialOrbit.getI());
    double j2Amplitude = j2 * EARTH_RADIUS * EARTH_RADIUS / rNominal * (1.0 - 1.5 * sinI * sinI);
    double altitudeOffset = j2Amplitude / 2.0;

    double effectiveTargetAlt = targetAltitude + altitudeOffset;
    logger.info(
        "J2 altitude offset: {} m, effective target: {} m", altitudeOffset, effectiveTargetAlt);

    double rTarget = EARTH_RADIUS + effectiveTargetAlt;

    this.effectiveTargetAlt = effectiveTargetAlt;
    this.aTarget = rTarget;
    this.vCircTarget = FastMath.sqrt(mu / rTarget);
    this.altMax = targetAltitude * 1.05;

    double aInitial = initialOrbit.getA();
    double eInitial = initialOrbit.getE();

    double initialPeriod = 2.0 * FastMath.PI * FastMath.sqrt(aInitial * aInitial * aInitial / mu);

    // Time-to-apoapsis on the current orbit — used as the CMA-ES initial seed.
    double meanAnomaly = initialOrbit.getMeanAnomaly();
    double dMeanAnomaly = FastMath.PI - meanAnomaly;
    if (dMeanAnomaly < 0) dMeanAnomaly += 2.0 * FastMath.PI;
    double timeToApoapsis = dMeanAnomaly / (2.0 * FastMath.PI) * initialPeriod;

    // ── Hohmann estimate for burn 1 ──
    double rApoapsis = aInitial * (1.0 + eInitial);
    double aTransfer = (rApoapsis + rTarget) / 2.0;

    double vAtApoapsis = FastMath.sqrt(mu * (2.0 / rApoapsis - 1.0 / aInitial));
    double vTransferAtApoapsis = FastMath.sqrt(mu * (2.0 / rApoapsis - 1.0 / aTransfer));
    double dv1 = vTransferAtApoapsis - vAtApoapsis;

    // Initial CMA-ES seed for t1: physically meaningful natural-apoapsis time.
    // Niveau 2.3 widens the upper bound to a fraction of the orbital period
    // so CMA-ES can still escape if the natural apoapsis isn't optimal.
    this.guessT1 = timeToApoapsis;

    double initialMass = initialState.getMass();
    this.thrust = propulsionSystem.thrust();
    this.isp = propulsionSystem.isp();

    this.guessDt1 = Physics.computeBurnDuration(FastMath.abs(dv1), initialMass, isp, thrust);

    // Physical upper bound: 90% of the time to exhaust available propellant
    double massFlow = thrust / (isp * Constants.G0_STANDARD_GRAVITY);
    double availablePropellant = initialMass - vehicleMinMass;
    this.dt1MaxPhysical = (availablePropellant * 0.90) / massFlow;

    // Niveau 2.3 — feasibility check: total Hohmann Δv must fit available propellant.
    double vCircAtTarget = FastMath.sqrt(mu / rTarget);
    double dv2Hohmann = vCircAtTarget - vTransferAtApoapsis;
    double dvHohmannTotal = FastMath.abs(dv1) + FastMath.max(0.0, dv2Hohmann);
    double dvAvailable =
        isp * Constants.G0_STANDARD_GRAVITY * FastMath.log(initialMass / vehicleMinMass);
    if (dvHohmannTotal > dvAvailable) {
      throw new OrbitlabException(
          String.format(
              "Target altitude %.0f m infeasible with current vehicle stack: "
                  + "Hohmann Δv ≈ %.0f m/s exceeds available Δv ≈ %.0f m/s",
              targetAltitude, dvHohmannTotal, dvAvailable));
    }

    // Niveau 2.1 — adaptive β1 bound. apoDefect quantifies how much apogee
    // raising remains; out-of-plane authority should grow with that defect.
    double apoDefect = (rTarget - rApoapsis) / rTarget;
    this.betaMax = (FastMath.PI / 12.0) * (1.0 + FastMath.max(0.0, apoDefect));

    // Niveau 2.3 — bound t1 by a fraction of the current orbital period so
    // CMA-ES can explore the full pre-burn coast window without depending on
    // a (possibly meaningless) time-to-apoapsis guess. dt1 cap moves from
    // 2·guessDt1 to K·guessDt1, still clamped by propellant feasibility.
    this.t1Max = FastMath.max(120.0, T1_MAX_PERIOD_FRACTION * initialPeriod);
    this.dt1Max = FastMath.min(DT1_MAX_MULTIPLIER * guessDt1, dt1MaxPhysical);

    // Niveau 2.4 — adaptive periapsis floor and eccentricity weight.
    // Spec (02 §2.4) suggests max(120 km, target − 100 km); but the existing
    // soft-barrier ramps from threshold up to ≈1.5·threshold, which would
    // overlap the nominal solution at high-altitude targets. Cap the floor
    // at 0.5·target to keep the barrier inactive at the target altitude.
    this.periapsisFloor =
        FastMath.max(
            PERIAPSIS_FLOOR_MIN,
            FastMath.min(targetAltitude * 0.5, targetAltitude - 100_000.0));
    this.weightE = W_E_BASE * FastMath.max(1.0, W_E_REF_ALT / targetAltitude);

    logger.info(
        "Initial guess for burn 1: T1={}, dt1={}, dv1={}, dv2≈{}",
        guessT1,
        guessDt1,
        dv1,
        dv2Hohmann);
    logger.info(
        "Physical dt1 max: {}s (propellant available: {}kg, Δv available: {}m/s)",
        dt1MaxPhysical,
        availablePropellant,
        dvAvailable);
    logger.info(
        "Adaptive bounds — βMax: {} rad, t1Max: {}s, dt1Max: {}s, periapsisFloor: {}m, W_E: {}",
        betaMax,
        t1Max,
        dt1Max,
        periapsisFloor,
        weightE);
  }

  @Override
  public double getAcceptableCost() {
    return 8e-4;
  }

  @Override
  public int getNumVariables() {
    return 4;
  }

  @Override
  public double[] buildInitialGuess() {
    return new double[] {guessT1, guessDt1, 0.0, 0.0};
  }

  @Override
  public double[] getLowerBounds() {
    return new double[] {
      0.0,
      guessDt1 * 0.5,
      -FastMath.PI / 2.0, // alpha1: prograde ± 90°
      -betaMax // beta1: adaptive out-of-plane (Niveau 2.1)
    };
  }

  @Override
  public double[] getUpperBounds() {
    return new double[] {
      t1Max, // Niveau 2.3 — fraction of post-GT orbital period
      dt1Max, // Niveau 2.3 — K·guessDt1, capped by propellant
      FastMath.PI / 2.0,
      betaMax
    };
  }

  @Override
  public double[] getInitialSigma() {
    // Niveau 2.2 — keep sigma proportional to the box width so CMA-ES
    // explores each parameter consistently regardless of the bound update.
    double[] lo = getLowerBounds();
    double[] hi = getUpperBounds();
    double[] sigma = new double[lo.length];
    for (int i = 0; i < sigma.length; i++) {
      sigma[i] = 0.3 * (hi[i] - lo[i]);
    }
    return sigma;
  }

  @Override
  public SpacecraftState propagate(double[] variables) {
    lastResult = maneuver.propagateForOptimization(initialState, variables);
    return lastResult.finalState();
  }

  /**
   * Returns the transfer result from the most recent propagation, containing the post-burn-1 orbit
   * and the resolved burn-2 parameters.
   *
   * @return the last transfer result, or {@code null} if no propagation has been performed yet
   */
  public TransferResult getLastTransferResult() {
    return lastResult;
  }

  @Override
  public double computeCost(SpacecraftState state) {
    // Detect penalty states: if propagation failed, the returned state is the initial state
    // (no time advancement). Assign a very high cost so CMA-ES avoids these solutions.
    double elapsed = state.getDate().durationFrom(initialState.getDate());
    if (elapsed < 1.0) {
      return 1e6;
    }

    KeplerianOrbit finalOrbit = (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(state.getOrbit());

    OneAxisEllipsoid earth = OrekitService.get().getEarthEllipsoid();
    double apoAlt = computeGeodeticAltitude(finalOrbit, FastMath.PI, earth); // ν = π
    double periAlt = computeGeodeticAltitude(finalOrbit, 0.0, earth); // ν = 0
    double targetAlt = effectiveTargetAlt;

    // Niveau 2.4 — relative errors keep the cost dimensionless across altitudes.
    double errApo = (apoAlt - targetAlt) / targetAlt;
    double errPeri = (periAlt - targetAlt) / targetAlt;
    // Niveau 2.4 — small absolute terms preserve sensitivity at high altitudes
    // where relative errors get arbitrarily small for non-trivial deviations.
    double errApoAbs = (apoAlt - targetAlt) / ABS_ERR_SCALE;
    double errPeriAbs = (periAlt - targetAlt) / ABS_ERR_SCALE;
    double errE = finalOrbit.getE();
    double errV = Physics.computeRadialVelocity(state) / vCircTarget;

    double objective =
        W_APO * errApo * errApo
            + W_PERI * errPeri * errPeri
            + W_APO_ABS * errApoAbs * errApoAbs
            + W_PERI_ABS * errPeriAbs * errPeriAbs
            + weightE * errE * errE
            + W_V * errV * errV;

    double barrier = 0.0;
    barrier += barrierBelow(periAlt, periapsisFloor); // périapsis géodésique

    double altMaxPenalty = 0.0;
    MinAltitudeTracker tracker = lastResult != null ? lastResult.altitudeTracker() : null;
    if (tracker != null) {
      barrier += barrierBelow(tracker.getMinAltitude(), ALT_MIN);
      if (tracker.getMaxAltitude() > altMax) {
        double excess = (tracker.getMaxAltitude() - altMax) / altMax;
        altMaxPenalty = excess * excess;
      }
    }

    return objective + W_BARRIER * barrier + W_ALT_MAX * altMaxPenalty;
  }

  private static double computeGeodeticAltitude(
      KeplerianOrbit src, double trueAnomaly, OneAxisEllipsoid earth) {
    KeplerianOrbit at =
        new KeplerianOrbit(
            src.getA(),
            src.getE(),
            src.getI(),
            src.getPerigeeArgument(),
            src.getRightAscensionOfAscendingNode(),
            trueAnomaly,
            PositionAngleType.TRUE,
            src.getFrame(),
            src.getDate(),
            src.getMu());
    Vector3D pos = at.getPVCoordinates().getPosition();
    return earth.transform(pos, src.getFrame(), src.getDate()).getAltitude();
  }

  private static double barrierBelow(double value, double threshold) {
    double normalized = (value - threshold) / FastMath.abs(threshold);
    double k = 10.0;
    if (normalized > 5.0 / k) return 0.0;
    return FastMath.log1p(FastMath.exp(-k * normalized));
  }

  /**
   * Returns the underlying two-burn transfer maneuver.
   *
   * @return the transfer maneuver instance
   */
  public TransfertTwoManeuver getManeuver() {
    return maneuver;
  }

  // ════════════════════════════════════════════════════════════════════════
  // Post-mortem diagnostics — see specs/optimizer/03-robustness-roadmap.md §0.1
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Δv decomposition for the two-burn transfer.
   *
   * @param dvBurn1Total the total Δv delivered by burn 1 (Tsiolkovsky), m/s
   * @param dvBurn1Useful the in-plane prograde projection (cos α · cos β), m/s
   * @param dvBurn1Wasted the residual ({@code total − useful}), m/s
   * @param dvBurn2 the deterministic burn-2 circularization Δv, m/s, or {@code NaN} if burn 2 could
   *     not be resolved from the optimal parameters
   */
  public record DvBreakdown(
      double dvBurn1Total, double dvBurn1Useful, double dvBurn1Wasted, double dvBurn2) {}

  /**
   * Computes the Δv breakdown for the optimal solution.
   *
   * <p>Burn 1 total uses the rocket equation with the propulsion characteristics of the stage
   * active at transfer entry. The useful projection is {@code cos α · cos β} of that scalar Δv.
   * Burn 2 is resolved deterministically via {@link TransfertTwoManeuver#resolveBurn2FromInitial}.
   *
   * @param bestVariables the optimized 4-element parameter vector
   * @return the Δv breakdown
   */
  public DvBreakdown computeDvBreakdown(double[] bestVariables) {
    TransfertTwoManeuver.Burn1Params params = maneuver.decode(bestVariables);

    double m0 = initialState.getMass();
    double massFlow = thrust / (isp * Constants.G0_STANDARD_GRAVITY);
    double mAfter = FastMath.max(m0 - massFlow * params.dt1(), 1.0);
    double dv1Total = isp * Constants.G0_STANDARD_GRAVITY * FastMath.log(m0 / mAfter);
    double useful = dv1Total * FastMath.cos(params.alpha1()) * FastMath.cos(params.beta1());
    double wasted = dv1Total - useful;

    TransfertTwoManeuver.ResolvedBurn2 burn2 =
        maneuver.resolveBurn2FromInitial(initialState, params);
    double dv2 = burn2 != null ? burn2.dvNeeded() : Double.NaN;

    return new DvBreakdown(dv1Total, useful, wasted, dv2);
  }

  /**
   * Per-barrier diagnostic for the optimal solution.
   *
   * @param periapsisFloor true if the post-burn-2 periapsis is at or below the adaptive
   *     periapsis floor
   * @param altMin true if the in-flight altitude tracker recorded a value at or below {@code
   *     ALT_MIN}
   * @param altMax true if the in-flight altitude tracker exceeded {@code 1.05 · target}
   * @param periapsisContribution the {@code W_BARRIER · barrierBelow(periapsis, periapsisFloor)}
   *     term
   * @param altMinContribution the {@code W_BARRIER · barrierBelow(minAlt, ALT_MIN)} term
   * @param altMaxContribution the {@code W_ALT_MAX · ((excess)/altMax)²} term
   */
  public record BarrierReport(
      boolean periapsisFloor,
      boolean altMin,
      boolean altMax,
      double periapsisContribution,
      double altMinContribution,
      double altMaxContribution) {}

  /**
   * Re-runs propagation for the optimal parameters and isolates each barrier contribution to the
   * cost. Pure, no side effects beyond updating {@code lastResult}.
   *
   * @param bestVariables the optimized parameter vector
   * @return the per-barrier report
   */
  public BarrierReport diagnoseBarriers(double[] bestVariables) {
    SpacecraftState state = propagate(bestVariables);
    double elapsed = state.getDate().durationFrom(initialState.getDate());
    if (elapsed < 1.0) {
      return new BarrierReport(false, false, false, 0.0, 0.0, 0.0);
    }
    KeplerianOrbit finalOrbit = (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(state.getOrbit());
    double periapsisAlt = finalOrbit.getA() * (1.0 - finalOrbit.getE()) - EARTH_RADIUS;

    double periBarrier = barrierBelow(periapsisAlt, periapsisFloor);
    boolean periHit = periapsisAlt <= periapsisFloor;

    double altMinBarrier = 0.0;
    double altMaxPenalty = 0.0;
    boolean altMinHit = false;
    boolean altMaxHit = false;
    MinAltitudeTracker tracker = lastResult != null ? lastResult.altitudeTracker() : null;
    if (tracker != null) {
      altMinBarrier = barrierBelow(tracker.getMinAltitude(), ALT_MIN);
      altMinHit = tracker.getMinAltitude() <= ALT_MIN;
      if (tracker.getMaxAltitude() > altMax) {
        double excess = (tracker.getMaxAltitude() - altMax) / altMax;
        altMaxPenalty = excess * excess;
        altMaxHit = true;
      }
    }
    return new BarrierReport(
        periHit,
        altMinHit,
        altMaxHit,
        W_BARRIER * periBarrier,
        W_BARRIER * altMinBarrier,
        W_ALT_MAX * altMaxPenalty);
  }
}
