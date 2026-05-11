package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransferManeuver;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransferResult;
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
 * Optimization problem for a single-burn orbit transfer where burn 1 is optimized to reach a target
 * {@code (perigee, apogee)} altitude pair.
 *
 * <p>Burn 1 (4 CMA-ES parameters) places the spacecraft on the target orbit. The cost function
 * evaluates the orbit at the end of burn 1 against the target apsidal altitudes and the derived
 * target eccentricity. For the special case {@code perigee == apogee}, see {@link
 * TransferTwoManeuverProblem}, which adds a deterministic circularization burn at the next
 * apoapsis.
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
public class TransferProblem implements TrajectoryProblem {
  private static final Logger logger = LogManager.getLogger(TransferProblem.class);
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  protected final TransferManeuver maneuver;
  protected final SpacecraftState initialState;
  // Stored per-thread so multiple CMA-ES exploration runs can call propagate()/computeCost()
  // concurrently without overwriting each other's results.
  protected final ThreadLocal<TransferResult> lastResult = new ThreadLocal<>();

  // ── Primary objective weights ──
  private static final double W_APO = 3.0;
  private static final double W_PERI = 10.0;
  private static final double W_E_BASE = 2.0;
  private static final double W_V = 1.0;
  // Penalize inclination drift away from the target plane (rad²); discourages
  // CMA-ES from using out-of-plane thrust (β1) to compensate for in-plane errors.
  private static final double W_I = 50.0;

  // Reference altitude for adaptive eccentricity weighting (Niveau 2.4)
  private static final double W_E_REF_ALT = 400_000.0;

  // Absolute-error scale (Niveau 2.4): keeps small absolute deviations
  // meaningful at high altitudes where relative errors become tiny.
  private static final double ABS_ERR_SCALE = 50_000.0;
  private static final double W_APO_ABS = 0.05;
  private static final double W_PERI_ABS = 0.15;

  // ── Constraint barrier weight ──
  protected static final double W_BARRIER = 0.1;
  protected static final double W_ALT_MAX = 1.0;

  // ── Constraint thresholds ──
  private static final double ALT_MIN = 80_000;
  private static final double PERIAPSIS_FLOOR_MIN = 120_000;

  // Niveau 2.3 — burn-1 duration multiplier on the Hohmann estimate
  private static final double DT1_MAX_MULTIPLIER = 4.0;
  // Niveau 2.3 — t1 upper bound as a fraction of the post-GT orbital period
  private static final double T1_MAX_PERIOD_FRACTION = 0.5;

  protected final double altMax;

  // Precomputed values
  private final double aTarget;
  private final double vCircTarget;
  // Effective target altitudes (geodetic, m), J2-compensated. Equal for a circular target.
  private final double perigeeTarget;
  private final double apogeeTarget;
  // Target eccentricity derived from (perigeeTarget, apogeeTarget). Zero for a circular target.
  private final double eTarget;

  // Hohmann-like guess values (precomputed)
  private final double guessT1;
  private final double guessDt1;

  // Physical upper bound on burn 1 duration (from available propellant)
  private final double dt1MaxPhysical;

  // Propulsion characteristics (kept for post-mortem Δv breakdown diagnostics)
  protected final double thrust;
  protected final double isp;

  // Niveau 2.1 — adaptive β1 bound derived from post-GT apoapsis defect
  private final double betaMax;

  // Adaptive value before the π/8 floor is applied — used by the per-attempt bounds
  // overrides to decide whether the floor is the active constraint and whether a
  // saturation observed on a previous attempt warrants relaxing β1Max on retry.
  private final double betaMaxAdaptive;

  // Niveau 2.3 — adaptive t1 upper bound (fraction of post-GT orbital period)
  protected final double t1Max;

  // Niveau 2.3 — burn 1 duration upper bound (Hohmann × K, capped by propellant)
  protected final double dt1Max;

  // Niveau 2.4 — adaptive periapsis floor and eccentricity weight
  protected final double periapsisFloor;
  private final double weightE;

  // Target orbital plane inclination (rad), used by the inclination penalty term
  private final double targetInclination;

  /**
   * Creates a single-burn transfer optimization problem.
   *
   * <p>Precomputes Hohmann-like initial guesses for burn timing and duration, applies a J2
   * short-period altitude compensation to the target, and determines physical upper bounds on burn
   * duration from available propellant.
   *
   * <p>The target is described by a (perigee, apogee) altitude pair. For a circular target, both
   * altitudes are equal. The single optimized burn shapes the orbit to match these apsides.
   *
   * @param maneuver the transfer maneuver that handles propagation
   * @param initialState the spacecraft state at the start of the transfer
   * @param perigeeAltitude target perigee altitude in meters above the Earth's surface
   * @param apogeeAltitude target apogee altitude in meters above the Earth's surface
   * @param propulsionSystem the propulsion system used for the transfer burn
   * @param vehicleMinMass minimum allowable vehicle mass after burns (dry mass)
   * @param targetInclination target orbital plane inclination in radians
   */
  public TransferProblem(
      TransferManeuver maneuver,
      SpacecraftState initialState,
      double perigeeAltitude,
      double apogeeAltitude,
      PropulsionSystem propulsionSystem,
      double vehicleMinMass,
      double targetInclination) {

    this.initialState = initialState;
    this.maneuver = maneuver;
    this.targetInclination = targetInclination;
    KeplerianOrbit initialOrbit = new KeplerianOrbit(initialState.getOrbit());
    double mu = initialOrbit.getMu();

    // ── J2 short-period altitude compensation ──
    // The osculating radius oscillates around the mean with amplitude ~J2*Re²/a
    // To center the geodetic altitude excursions on the target altitudes, we target
    // slightly higher mean altitudes. Compute the offset from the apogee (where the
    // circularization burn lands) and apply it uniformly to both apsides — preserves
    // the historical LEO behavior (rp == ra) and remains a small constant offset for
    // elliptic targets.
    double rNominal = EARTH_RADIUS + apogeeAltitude;
    double j2 = 1.0826e-3; // J2 coefficient
    double sinI = FastMath.sin(initialOrbit.getI());
    double j2Amplitude = j2 * EARTH_RADIUS * EARTH_RADIUS / rNominal * (1.0 - 1.5 * sinI * sinI);
    double altitudeOffset = j2Amplitude / 2.0;

    double effectivePerigeeAlt = perigeeAltitude + altitudeOffset;
    double effectiveApogeeAlt = apogeeAltitude + altitudeOffset;
    logger.info(
        "J2 altitude offset: {} m, effective perigee: {} m, effective apogee: {} m",
        altitudeOffset,
        effectivePerigeeAlt,
        effectiveApogeeAlt);

    this.perigeeTarget = effectivePerigeeAlt;
    this.apogeeTarget = effectiveApogeeAlt;

    double rPerigeeTarget = EARTH_RADIUS + effectivePerigeeAlt;
    double rApogeeTarget = EARTH_RADIUS + effectiveApogeeAlt;
    this.eTarget = (rApogeeTarget - rPerigeeTarget) / (rApogeeTarget + rPerigeeTarget);

    // Anchor the adaptive scaling on the apogee — the apside the transfer is built to reach.
    double rTarget = rApogeeTarget;
    this.aTarget = rTarget;
    this.vCircTarget = FastMath.sqrt(mu / rTarget);
    // At low altitudes (≤400 km) the previous 5 % cap leaves <20 km of margin,
    // which the J2 oscillation alone can violate. Use a floor of 20 km so the
    // bound is meaningful below LEO; at higher altitudes the relative term
    // dominates and behaves as before.
    this.altMax = apogeeAltitude + FastMath.max(500_000.0, 0.30 * apogeeAltitude);

    double aInitial = initialOrbit.getA();
    double eInitial = initialOrbit.getE();

    double initialPeriod = 2.0 * FastMath.PI * FastMath.sqrt(aInitial * aInitial * aInitial / mu);

    // Time-to-apoapsis on the current orbit — used as the CMA-ES initial seed.
    double meanAnomaly = initialOrbit.getMeanAnomaly();
    double dMeanAnomaly = FastMath.PI - meanAnomaly;
    if (dMeanAnomaly < 0) dMeanAnomaly += 2.0 * FastMath.PI;
    double timeToApoapsis = dMeanAnomaly / (2.0 * FastMath.PI) * initialPeriod;

    // ── Hohmann estimate for burn 1 ──
    // Pick the apside that minimizes the burn: periapsis when raising the orbit,
    // apoapsis when lowering it. This makes the guess valid for both circular
    // departures (LEO → GTO) and elliptic departures (post-gravity-turn → LEO).
    double rPeriapsis = aInitial * (1.0 - eInitial);
    double rApoapsis = aInitial * (1.0 + eInitial);
    boolean raising = rTarget >= aInitial;
    double rDeparture = raising ? rPeriapsis : rApoapsis;
    double aTransfer = (rDeparture + rTarget) / 2.0;

    double vAtDeparture = FastMath.sqrt(mu * (2.0 / rDeparture - 1.0 / aInitial));
    double vTransferAtDeparture = FastMath.sqrt(mu * (2.0 / rDeparture - 1.0 / aTransfer));
    double dv1 = vTransferAtDeparture - vAtDeparture;

    // Initial CMA-ES seed for t1: time-to-departure-apside on the current orbit.
    // For a circular initial orbit, both apsides are at the same radius, so
    // timeToApoapsis is a safe default (any t1 works geometrically); the
    // optimizer is free to relocate the burn within [0, t1Max].
    this.guessT1 = raising ? timeToPeriapsis(initialOrbit, initialPeriod) : timeToApoapsis;

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
    double dv2Hohmann = vCircAtTarget - vTransferAtDeparture;
    double dvHohmannTotal = FastMath.abs(dv1) + FastMath.max(0.0, dv2Hohmann);
    double dvAvailable =
        isp * Constants.G0_STANDARD_GRAVITY * FastMath.log(initialMass / vehicleMinMass);
    if (dvHohmannTotal > dvAvailable) {
      throw new OrbitlabException(
          String.format(
              "Target orbit (perigee %.0f m, apogee %.0f m) infeasible with current vehicle stack: "
                  + "Hohmann Δv ≈ %.0f m/s exceeds available Δv ≈ %.0f m/s",
              perigeeAltitude, apogeeAltitude, dvHohmannTotal, dvAvailable));
    }

    // Niveau 2.1 — adaptive β1 bound. apoDefect quantifies how much apogee
    // raising remains; out-of-plane authority should grow with that defect.
    // Floor at π/8 (~22.5°) so CMA-ES retains usable out-of-plane authority
    // even when the GT lands close to the target apogee (apoDefect ≈ 0): in
    // practice, the transfer often needs β to absorb the GT's residual
    // velocity orientation regardless of the apogee gap.
    double apoDefect = (rTarget - rApoapsis) / rTarget;
    this.betaMaxAdaptive = (FastMath.PI / 12.0) * (1.0 + FastMath.max(0.0, apoDefect));
    this.betaMax = FastMath.max(FastMath.PI / 8.0, betaMaxAdaptive);

    // Niveau 2.3 — bound t1 by a fraction of the current orbital period so
    // CMA-ES can explore the full pre-burn coast window without depending on
    // a (possibly meaningless) time-to-apoapsis guess. dt1 cap moves from
    // 2·guessDt1 to K·guessDt1, still clamped by propellant feasibility.
    this.t1Max = FastMath.max(120.0, T1_MAX_PERIOD_FRACTION * initialPeriod);
    this.dt1Max = FastMath.max(DT1_MAX_MULTIPLIER * guessDt1, dt1MaxPhysical);

    // Niveau 2.4 — adaptive periapsis floor and eccentricity weight.
    // Spec (02 §2.4) suggests max(120 km, target − 100 km); but the existing
    // soft-barrier ramps from threshold up to ≈1.5·threshold, which would
    // overlap the nominal solution at high-altitude targets. Cap the floor
    // at 0.5·target to keep the barrier inactive at the target altitude.
    // Additional cap at target/1.6 prevents the barrier from biting the target
    // at low altitudes (e.g. at 185 km, PERIAPSIS_FLOOR_MIN=120 km would
    // saturate up to ~180 km — almost the target itself).
    // Anchored on the perigee target — the most constraining apside.
    double floorCandidate =
        FastMath.max(
            PERIAPSIS_FLOOR_MIN, FastMath.min(perigeeAltitude * 0.5, perigeeAltitude - 100_000.0));
    this.periapsisFloor = FastMath.min(floorCandidate, perigeeAltitude / 1.6);
    // Quadratic ramp on the W_E_REF_ALT/perigeeAltitude ratio so the eccentricity
    // term dominates more aggressively at very low altitudes (e.g. 185 km),
    // where the test margin (±7%) leaves little room for residual ellipticity.
    double altRatio = FastMath.max(1.0, W_E_REF_ALT / perigeeAltitude);
    this.weightE = W_E_BASE * altRatio * altRatio;

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
    boolean betaFloorActive = betaMaxAdaptive < FastMath.PI / 8.0;
    logger.info(
        "Adaptive bounds — βMax: {} rad (adaptive={}, π/8 floor active={}), "
            + "t1Max: {}s, dt1Max: {}s, periapsisFloor: {}m, W_E: {}",
        betaMax,
        betaMaxAdaptive,
        betaFloorActive,
        t1Max,
        dt1Max,
        periapsisFloor,
        weightE);
    logger.info(
        "Inclination target: {} rad ({}°), W_I={}",
        targetInclination,
        FastMath.toDegrees(targetInclination),
        W_I);
  }

  @Override
  public double getAcceptableCost() {
    return 3e-3;
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
  public double[] buildAnalyticalSeed() {
    // Niveau 3.2 — pure Hohmann analytical seed: burn immediately (t1=0) for the
    // Hohmann burn duration, prograde coplanar (alpha1=0, beta1=0). Forces at least
    // one CMA-ES exploration run to start in the closed-form Hohmann basin.
    return new double[] {0.0, guessDt1, 0.0, 0.0};
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

  // Index of β1 in the parameter vector (t1=0, dt1=1, α1=2, β1=3).
  private static final int BETA1_INDEX = 3;

  // Saturation threshold for triggering relaxation: previous |β1| ≥ 0.95 × βMax.
  private static final double BETA_SATURATION_FRACTION = 0.95;

  // Per-attempt relaxation policy: minimum floor and multiplicative coefficient on
  // the previous saturated |β1|. Index 0 is unused (attempt 0 never relaxes).
  private static final double[] BETA_RELAX_MIN_FLOOR = {
    Double.NaN, FastMath.PI / 6.0, FastMath.PI / 4.0
  };
  private static final double[] BETA_RELAX_COEFF = {Double.NaN, 1.5, 2.0};

  /**
   * Computes the relaxed β1Max for a retry attempt, given the previous attempt's best vector. The
   * relaxation only fires when the previous solution saturated the current β1 bound (within {@link
   * #BETA_SATURATION_FRACTION}); otherwise the current βMax is returned unchanged.
   *
   * <p>For attempt N ≥ 1: {@code relaxedβMax = max(BETA_RELAX_MIN_FLOOR[N], BETA_RELAX_COEFF[N] ·
   * |β1_observed|)}.
   */
  private double computeRelaxedBetaMax(int attempt, double[] previousBestVars) {
    if (attempt <= 0 || previousBestVars == null) return betaMax;
    double prevBetaAbs = FastMath.abs(previousBestVars[BETA1_INDEX]);
    if (prevBetaAbs < BETA_SATURATION_FRACTION * betaMax) return betaMax;
    int idx = FastMath.min(attempt, BETA_RELAX_MIN_FLOOR.length - 1);
    return FastMath.max(BETA_RELAX_MIN_FLOOR[idx], BETA_RELAX_COEFF[idx] * prevBetaAbs);
  }

  @Override
  public double[] getLowerBoundsForAttempt(int attempt, double[] previousBestVars) {
    double[] lb = getLowerBounds();
    double relaxed = computeRelaxedBetaMax(attempt, previousBestVars);
    if (relaxed > betaMax) {
      lb[BETA1_INDEX] = -relaxed;
      logger.info(
          "Attempt {}: relaxing β1 lower bound from {} to {} (previous |β1|={})",
          attempt,
          -betaMax,
          -relaxed,
          FastMath.abs(previousBestVars[BETA1_INDEX]));
    }
    return lb;
  }

  @Override
  public double[] getUpperBoundsForAttempt(int attempt, double[] previousBestVars) {
    double[] ub = getUpperBounds();
    double relaxed = computeRelaxedBetaMax(attempt, previousBestVars);
    if (relaxed > betaMax) {
      ub[BETA1_INDEX] = relaxed;
    }
    return ub;
  }

  @Override
  public double[] getInitialSigmaForAttempt(int attempt, double[] previousBestVars) {
    double[] lo = getLowerBoundsForAttempt(attempt, previousBestVars);
    double[] hi = getUpperBoundsForAttempt(attempt, previousBestVars);
    double[] sigma = new double[lo.length];
    for (int i = 0; i < sigma.length; i++) {
      sigma[i] = 0.3 * (hi[i] - lo[i]);
    }
    return sigma;
  }

  @Override
  public SpacecraftState propagate(double[] variables) {
    TransferResult result = maneuver.propagateForOptimization(initialState, variables);
    lastResult.set(result);
    return result.finalState();
  }

  /**
   * Returns the transfer result from the most recent propagation on the calling thread, containing
   * the post-burn-1 orbit and (when applicable) the resolved circularization burn parameters.
   *
   * @return the last transfer result for this thread, or {@code null} if no propagation has been
   *     performed on this thread yet
   */
  public TransferResult getLastTransferResult() {
    return lastResult.get();
  }

  @Override
  public double computeCost(SpacecraftState state) {
    // Detect penalty states: if propagation failed, the returned state is the initial state
    // (no time advancement). Use a graded penalty so CMA-ES gets a usable gradient instead
    // of a flat 1e6 wall. The 1e3 base still dominates any nominal cost (typically ≪ 100).
    // Three failure modes, in order of preference:
    //   1. Tracker captured an in-flight altitude excursion → grade by depth underground.
    //   2. orbitPostBurn1 is non-null but extreme (e>0.95, a out of range) → grade by
    //      orbital distance from a viable target orbit.
    //   3. Nothing usable → fall back to the flat 1e6 wall.
    double elapsed = state.getDate().durationFrom(initialState.getDate());
    if (elapsed < 1.0) {
      TransferResult tr = lastResult.get();
      if (tr != null) {
        MinAltitudeTracker failureTracker = tr.altitudeTracker();
        if (failureTracker != null && failureTracker.getMinAltitude() != Double.MAX_VALUE) {
          double underground = FastMath.max(0.0, -failureTracker.getMinAltitude());
          return 1e3 + underground / 1000.0;
        }
        KeplerianOrbit postBurn1 = tr.orbitPostBurn1();
        if (postBurn1 != null) {
          double aErr = FastMath.abs(postBurn1.getA() - aTarget) / aTarget;
          double eErr = postBurn1.getE();
          return 1e3 + 50.0 * aErr + 50.0 * eErr;
        }
      }
      return 1e6;
    }

    KeplerianOrbit finalOrbit = (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(state.getOrbit());

    OneAxisEllipsoid earth = OrekitService.get().getEarthEllipsoid();
    double apoAlt = computeGeodeticAltitude(finalOrbit, FastMath.PI, earth); // ν = π
    double periAlt = computeGeodeticAltitude(finalOrbit, 0.0, earth); // ν = 0

    // Niveau 2.4 — relative errors keep the cost dimensionless across altitudes.
    double errApo = (apoAlt - apogeeTarget) / apogeeTarget;
    double errPeri = (periAlt - perigeeTarget) / perigeeTarget;
    // Niveau 2.4 — small absolute terms preserve sensitivity at high altitudes
    // where relative errors get arbitrarily small for non-trivial deviations.
    double errApoAbs = (apoAlt - apogeeTarget) / ABS_ERR_SCALE;
    double errPeriAbs = (periAlt - perigeeTarget) / ABS_ERR_SCALE;
    // Penalize deviation from the target eccentricity (0 for circular targets).
    // Forces the two apsides to converge to the target shape, not just to their
    // individual altitudes.
    double errE = finalOrbit.getE() - eTarget;
    double errV = Physics.computeRadialVelocity(state) / vCircTarget;
    double errI = finalOrbit.getI() - targetInclination;

    double objective =
        W_APO * errApo * errApo
            + W_PERI * errPeri * errPeri
            + W_APO_ABS * errApoAbs * errApoAbs
            + W_PERI_ABS * errPeriAbs * errPeriAbs
            + weightE * errE * errE
            + W_V * errV * errV
            + W_I * errI * errI;

    double barrier = 0.0;
    barrier += barrierBelow(periAlt, periapsisFloor); // périapsis géodésique

    double altMaxPenalty = 0.0;
    TransferResult tr = lastResult.get();
    MinAltitudeTracker tracker = tr != null ? tr.altitudeTracker() : null;
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

  protected static double barrierBelow(double value, double threshold) {
    double normalized = (value - threshold) / FastMath.abs(threshold);
    double k = 10.0;
    if (normalized > 5.0 / k) return 0.0;
    return FastMath.log1p(FastMath.exp(-k * normalized));
  }

  /**
   * Time from the current state to the next periapsis passage on the given orbit. Returns a
   * strictly positive value within ]0, period].
   */
  private static double timeToPeriapsis(KeplerianOrbit orbit, double period) {
    double meanAnomaly = orbit.getMeanAnomaly();
    double dMeanAnomaly = -meanAnomaly; // periapsis is M = 0
    while (dMeanAnomaly <= 0) dMeanAnomaly += 2.0 * FastMath.PI;
    return dMeanAnomaly / (2.0 * FastMath.PI) * period;
  }

  /**
   * Returns the underlying transfer maneuver.
   *
   * @return the transfer maneuver instance
   */
  public TransferManeuver getManeuver() {
    return maneuver;
  }
}
