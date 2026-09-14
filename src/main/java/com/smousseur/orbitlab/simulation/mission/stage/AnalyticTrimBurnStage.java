package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.FlownBandAim;
import com.smousseur.orbitlab.simulation.OrbitElements;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryGuard;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.attitudes.FrameAlignedProvider;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.frames.Frame;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.ApsideDetector;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.events.handlers.RecordAndContinue;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * Deterministic trim stage at the next apside(s), after a transfer, to land the orbit's shape and
 * inclination the analytic Hohmann or CMA-ES transfer left approximate — driven mainly by
 * un-modelled J3+ zonal harmonics and finite-burn losses.
 *
 * <p><b>Two shapes, one seam.</b> This stage is the last orbit-shaping phase of every Earth-orbit
 * profile (analytic and optimized transfer alike), which is why the mean-element correction lives
 * here rather than in each transfer.
 *
 * <ul>
 *   <li><b>Elliptic target</b> (and GEO, through the three-argument constructor): a single burn at
 *       the next apogee. The target shape is the ellipse (aimed shaping radius, achieved apogee),
 *       where the shaping radius is resolved by {@link FlownBandAim} so the <b>flown</b> altitude
 *       band is centred on the requested orbit (spec orbit-reporting/02). The resulting orbit
 *       carries the requested perigee <b>in mean elements</b>.
 *   <li><b>Circular target</b> (through the four-argument constructor): a two-burn Hohmann in
 *       <b>mean</b> elements — see below. A single apside burn cannot circularize the mean orbit
 *       off the equator, so this path replaces it there.
 * </ul>
 *
 * <p><b>Why the circular target needs two burns.</b> The transfer hands over an orbit that is
 * <em>osculating</em>-circular but <em>mean</em>-eccentric (an instantaneously circular orbit has a
 * mean eccentricity of order {@code f = (3/2)·J2·(RE/a)²}; the two cannot be circular at once, spec
 * orbit-reporting/01 §2.2). The single-burn trim aims the mean <em>semi-major axis</em> with one
 * degree of freedom, firing at the osculating apogee. At the equator that also nulls the mean
 * eccentricity; off the equator the osculating apogee is offset from the mean apogee by the J2
 * short-period term (∝ {@code sin²i}), so the one burn leaves a mean eccentricity no magnitude can
 * remove — measured at 28.5° a 407×413 km mean ellipse for a 400 km request, against a clean circle
 * at 5°. Circularizing the mean therefore needs two apsidal burns: {@code d₁} at the next mean
 * perigee raises the mean apogee to the target, {@code d₂} at the next mean apogee circularizes.
 *
 * <p><b>How the two magnitudes are found.</b> A simulate-and-correct, the same shape as {@link
 * AnalyticApogeeCircularizationStage}: seed the two ΔV from an impulsive mean Hohmann, then a 2×2
 * Newton on {@code (d₁, d₂)} against the residual {@code (mean perigee − target, mean apogee −
 * target)} read back off {@link OrbitElements#mean(Orbit, double)} of the simulated end state. The
 * burns are placed at the <b>mean</b> apsides (timed off {@link OrbitElements#meanOrbit(Orbit)} of
 * the hand-off), not the osculating ones, which for a near-circular hand-off are J2 wiggle. The
 * burns are prograde, so the plane is untouched: the target inclination of a commanded plane is
 * cleaned by {@link AnalyticPlaneTrimAtNodeStage}, not here.
 *
 * <p><b>Total by construction.</b> Any failure of the mean path — the conversion not converging, a
 * singular Jacobian, an undeliverable burn — falls back on the single-burn trim, which flies the
 * mission at the pre-fix quality rather than failing it (spec orbit-reporting/01 §3.4).
 */
public class AnalyticTrimBurnStage extends MissionStage {
  private static final Logger logger = LogManager.getLogger(AnalyticTrimBurnStage.class);
  private static final double SKIP_DV_THRESHOLD = 1.0; // m/s
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  /** Apo − peri below which a target is flown as circular, matching {@code MissionComposer}. */
  private static final double CIRCULAR_TOLERANCE_M = 1_000.0;

  /**
   * Convergence threshold on each mean apside (m). The floor is Eckstein-Hechler's own ~625 m
   * modelling residual at 400 km ({@link OrbitElements#mean}); iterating finer chases noise. 1 km
   * brings the 28.5° mean band from ~6 km to ~1-2 km.
   */
  private static final double MEAN_CIRC_TOLERANCE_M = 1_000.0;

  private static final int MEAN_CIRC_MAX_ITERATIONS = 5;

  /**
   * Ceiling above which the mean solve is judged not to have circularized and the single-burn trim
   * is flown instead. Sized under the ~3 km mean-apside miss the single burn leaves off the
   * equator, so the two-burn path is adopted only when it actually beats what it replaces; at low
   * inclination it converges far below this (burn 1 ≈ 0, the plan degenerating to the single apogee
   * burn).
   */
  private static final double MEAN_CIRC_SANITY_M = 2_000.0;

  /** Finite-difference step for the Jacobian columns (m/s). */
  private static final double JACOBIAN_STEP = 0.5;

  /** Below this the Jacobian is treated as singular and the mean path is abandoned. */
  private static final double JACOBIAN_MIN_DET = 1.0e-3;

  /** Fraction of a burn's required duration that may be lost to propellant capping. */
  private static final double BURN_CAPACITY_TOLERANCE = 1.0e-3;

  private final double targetPerigeeAltitude;
  private final double targetApogeeAltitude;
  private final double targetInclination;

  /**
   * Whether this trim may circularize in mean elements. False for the three-argument constructor
   * (GEO), which keeps the single-burn trim it has always flown; true for the Earth-orbit
   * factories, which then use the two-burn path only when the target is actually circular.
   */
  private final boolean meanCircularization;

  /**
   * The historical constructor: a single-burn trim, never mean-circularizing. Used by GEO, whose
   * trim entry is a different regime (a perigee deficit left by the apogee circularization) and
   * whose equatorial plane has no mean-eccentricity defect to fix.
   *
   * @param name human-readable stage name
   * @param targetPerigeeAltitude target perigee altitude (m, above the Earth surface); equals the
   *     target apogee altitude for a circular orbit
   * @param targetInclination target orbital plane inclination (rad); 0 for an equatorial GEO
   */
  public AnalyticTrimBurnStage(
      String name, double targetPerigeeAltitude, double targetInclination) {
    this(name, targetPerigeeAltitude, targetPerigeeAltitude, targetInclination, false);
  }

  /**
   * The Earth-orbit constructor: mean-circularizes when {@code targetApogeeAltitude} equals {@code
   * targetPerigeeAltitude} (within {@link #CIRCULAR_TOLERANCE_M}), single-burn otherwise.
   *
   * @param name human-readable stage name
   * @param targetPerigeeAltitude target perigee altitude (m, above the Earth surface)
   * @param targetApogeeAltitude target apogee altitude (m, above the Earth surface)
   * @param targetInclination target orbital plane inclination (rad)
   */
  public AnalyticTrimBurnStage(
      String name,
      double targetPerigeeAltitude,
      double targetApogeeAltitude,
      double targetInclination) {
    this(name, targetPerigeeAltitude, targetApogeeAltitude, targetInclination, true);
  }

  private AnalyticTrimBurnStage(
      String name,
      double targetPerigeeAltitude,
      double targetApogeeAltitude,
      double targetInclination,
      boolean meanCircularization) {
    super(name);
    this.targetPerigeeAltitude = targetPerigeeAltitude;
    this.targetApogeeAltitude = targetApogeeAltitude;
    this.targetInclination = targetInclination;
    this.meanCircularization = meanCircularization;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState state = mission.getCurrentState();
    List<Burn> burns = computeBurns(state, mission.getVehicle(), flightContext(state, mission));

    if (burns.isEmpty()) {
      this.configuredEndDate = state.getDate();
      propagator.addEventDetector(
          new DateDetector(state.getDate().shiftedBy(1.0e-3))
              .withHandler(
                  (s, detector, increasing) -> {
                    mission.transitionToNextStage(s);
                    return Action.STOP;
                  }));
      return;
    }

    addBurns(propagator, state, burns, mission.getVehicle());

    Burn last = burns.getLast();
    AbsoluteDate endDate = last.start().shiftedBy(last.dt());
    this.configuredEndDate = endDate;
    propagator.addEventDetector(
        new DateDetector(endDate)
            .withHandler(
                (s, detector, increasing) -> {
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                }));
  }

  @Override
  public double maxStepSeconds(SpacecraftState entryState, Mission mission) {
    return burnLimitedMaxStep(entryState, mission.getVehicle());
  }

  @Override
  public SpacecraftState propagateStandalone(SpacecraftState currentState, Mission mission) {
    FlightContext context = flightContext(currentState, mission);
    List<Burn> burns = computeBurns(currentState, mission.getVehicle(), context);
    if (burns.isEmpty()) {
      return currentState;
    }

    // 8×8 gravity, matching the ephemeris generator (bilan 11 §3.9): this standalone flight
    // advances
    // the state the next stage plans from, so a Newtonian point-mass field here would diverge from
    // the flown 8×8 trajectory that the whole GEO plane strategy is measured against.
    NumericalPropagator propagator =
        OrekitService.get()
            .createOptimizationPropagator(
                context, burnLimitedMaxStep(currentState, mission.getVehicle()));
    propagator.setInitialState(currentState);
    ReentryGuard.armQuiet(propagator, context.gravity());
    addBurns(propagator, currentState, burns, mission.getVehicle());

    Burn last = burns.getLast();
    return propagator.propagate(last.start().shiftedBy(last.dt()));
  }

  /** One scheduled burn: an inertial-fixed direction over a window. {@code dv} is for logging. */
  private record Burn(AbsoluteDate start, double dt, Vector3D directionInertial, double dv) {}

  /**
   * The burns this trim flies: two for a mean-circularization that succeeds, one for the
   * single-burn trim, none when the residual is already below the skip threshold.
   */
  private List<Burn> computeBurns(SpacecraftState state, Vehicle vehicle, FlightContext context) {
    if (meanCircularization && isCircularTarget()) {
      Optional<List<Burn>> circular = computeCircularTrim(state, vehicle, context);
      if (circular.isPresent()) {
        return circular.get();
      }
      logger.info("Circular trim: mean Hohmann unavailable, falling back on the single-burn trim.");
    }
    return computeSingleBurn(state, vehicle, context);
  }

  private boolean isCircularTarget() {
    return FastMath.abs(targetApogeeAltitude - targetPerigeeAltitude) < CIRCULAR_TOLERANCE_M;
  }

  // ── Circular target: two-burn Hohmann in mean elements ────────────────────

  /** Everything the simulate-and-correct reads, computed once from the hand-off state. */
  private record CircularContext(
      SpacecraftState entry,
      FlightContext flight,
      AbsoluteDate tPeri,
      AbsoluteDate tApo,
      Vector3D dir1,
      Vector3D dir2,
      double aStarAltitude,
      ActiveStageInfo stage,
      double maxStep) {}

  /**
   * Plans the two-burn mean circularization, or returns empty to signal a fallback to the
   * single-burn trim (mean conversion unavailable, singular Jacobian, or an undeliverable burn). A
   * present-but-empty list means the hand-off is already mean-circular at the target and no burn is
   * worth flying.
   */
  private Optional<List<Burn>> computeCircularTrim(
      SpacecraftState entry, Vehicle vehicle, FlightContext context) {
    Optional<Orbit> meanOpt = OrbitElements.meanOrbit(entry.getOrbit());
    if (meanOpt.isEmpty()) {
      return Optional.empty();
    }

    double rTargetPeri = EARTH_RADIUS + targetPerigeeAltitude;
    double rStar = rTargetPeri + FlownBandAim.closedFormOffset(rTargetPeri);
    double mu = entry.getOrbit().getMu();

    double[] seed;
    CircularContext ctx;
    try {
      KeplerianOrbit meanOrbit = new KeplerianOrbit(meanOpt.get());
      double period = meanOrbit.getKeplerianPeriod();
      double n = meanOrbit.getKeplerianMeanMotion();
      double twoPi = 2.0 * FastMath.PI;
      // Time to the next mean perigee (mean anomaly 0), then half a mean period to the mean apogee.
      double dtPerigee = (((-meanOrbit.getMeanAnomaly()) % twoPi + twoPi) % twoPi) / n;
      AbsoluteDate tPeri = entry.getDate().shiftedBy(dtPerigee);
      AbsoluteDate tApo = tPeri.shiftedBy(period / 2.0);

      // Prograde directions, read off a burn-free coast of the hand-off: the apogee heading barely
      // moves once burn 1 raises the apogee (it stays horizontal at an apside), so it is
      // precomputed
      // rather than re-read after burn 1.
      NumericalPropagator coast =
          OrekitService.get().createOptimizationPropagator(context, OrekitService.COAST_MAX_STEP);
      coast.setInitialState(entry);
      ReentryGuard.armQuiet(coast, context.gravity());
      Vector3D dir1 = coast.propagate(tPeri).getPVCoordinates().getVelocity().normalize();
      Vector3D dir2 = coast.propagate(tApo).getPVCoordinates().getVelocity().normalize();

      double a0 = meanOrbit.getA();
      double e0 = meanOrbit.getE();
      seed = seedHohmann(a0 * (1.0 - e0), a0 * (1.0 + e0), rStar, mu);

      ActiveStageInfo stage = vehicle.resolveActiveStage(entry.getMass());
      ctx =
          new CircularContext(
              entry,
              context,
              tPeri,
              tApo,
              dir1,
              dir2,
              rStar - EARTH_RADIUS,
              stage,
              burnLimitedMaxStep(entry, vehicle));
    } catch (RuntimeException e) {
      logger.debug(
          "Circular trim setup failed ({}): {}", e.getClass().getSimpleName(), e.getMessage());
      return Optional.empty();
    }

    if (FastMath.abs(seed[0]) < SKIP_DV_THRESHOLD && FastMath.abs(seed[1]) < SKIP_DV_THRESHOLD) {
      logger.info("Circular trim: hand-off already mean-circular at target, skipping.");
      return Optional.of(List.of());
    }

    Optional<double[]> solved = solveMeanCircular(ctx, seed);
    if (solved.isEmpty()) {
      return Optional.empty();
    }
    double[] best = solved.get();

    List<Burn> burns = circularBurns(ctx, best[0], best[1]);
    if (!deliverable(ctx, burns)) {
      logger.info(
          "Circular trim: a burn is propellant-capped, falling back on the single-burn trim.");
      return Optional.empty();
    }
    logger.info(
        "Circular trim (mean Hohmann): dv1={} m/s at mean perigee, dv2={} m/s at mean apogee,"
            + " target mean circle {} km",
        (float) best[0],
        (float) best[1],
        (float) (ctx.aStarAltitude() / 1000.0));
    return Optional.of(burns);
  }

  /**
   * 2×2 Newton on {@code (d₁, d₂)} against {@code (mean perigee − target, mean apogee − target)},
   * with a chord Jacobian estimated once at the seed. Returns the best magnitudes seen, or empty if
   * a simulation could not be read, the Jacobian is singular, or the solve did not circularize.
   */
  private Optional<double[]> solveMeanCircular(CircularContext ctx, double[] seed) {
    double[] x = {seed[0], seed[1]};
    Optional<double[]> f = meanResidual(ctx, x[0], x[1]);
    Optional<double[]> fp1 = meanResidual(ctx, x[0] + JACOBIAN_STEP, x[1]);
    Optional<double[]> fp2 = meanResidual(ctx, x[0], x[1] + JACOBIAN_STEP);
    if (f.isEmpty() || fp1.isEmpty() || fp2.isEmpty()) {
      return Optional.empty();
    }
    double[] r = f.get();
    double[] best = {x[0], x[1]};
    double bestNorm = normInf(r);

    double j00 = (fp1.get()[0] - r[0]) / JACOBIAN_STEP;
    double j10 = (fp1.get()[1] - r[1]) / JACOBIAN_STEP;
    double j01 = (fp2.get()[0] - r[0]) / JACOBIAN_STEP;
    double j11 = (fp2.get()[1] - r[1]) / JACOBIAN_STEP;
    double det = j00 * j11 - j01 * j10;
    if (FastMath.abs(det) < JACOBIAN_MIN_DET) {
      return Optional.empty();
    }

    for (int iter = 0;
        iter < MEAN_CIRC_MAX_ITERATIONS && bestNorm >= MEAN_CIRC_TOLERANCE_M;
        iter++) {
      x[0] -= (j11 * r[0] - j01 * r[1]) / det;
      x[1] -= (-j10 * r[0] + j00 * r[1]) / det;
      Optional<double[]> next = meanResidual(ctx, x[0], x[1]);
      if (next.isEmpty()) {
        return Optional.empty();
      }
      r = next.get();
      double norm = normInf(r);
      if (norm < bestNorm) {
        bestNorm = norm;
        best[0] = x[0];
        best[1] = x[1];
      }
    }
    // Adopt the two-burn plan only if it actually circularized; a diverged solve falls back on the
    // single-burn trim, which is no worse than an unconverged mean Hohmann.
    return bestNorm < MEAN_CIRC_SANITY_M ? Optional.of(best) : Optional.empty();
  }

  /** The mean apside residual of the two burns {@code (d₁, d₂)}, or empty if unreadable. */
  private Optional<double[]> meanResidual(CircularContext ctx, double dv1, double dv2) {
    SpacecraftState end;
    try {
      end = simulateCircular(ctx, circularBurns(ctx, dv1, dv2));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
    return OrbitElements.mean(end.getOrbit(), EARTH_RADIUS)
        .map(
            m ->
                new double[] {
                  m.perigeeAltitude() - ctx.aStarAltitude(),
                  m.apogeeAltitude() - ctx.aStarAltitude()
                });
  }

  /** The two burns for magnitudes {@code (d₁, d₂)}: prograde when positive, retrograde when not. */
  private List<Burn> circularBurns(CircularContext ctx, double dv1, double dv2) {
    PropulsionSystem propulsion = ctx.stage().propulsion();
    double g0Ve = propulsion.isp() * Constants.G0_STANDARD_GRAVITY;
    double mass1 = ctx.entry().getMass();
    double dt1 =
        Physics.computeBurnDurationCapped(
            FastMath.abs(dv1),
            mass1,
            propulsion.isp(),
            propulsion.thrust(),
            ctx.stage().remainingFuel(mass1));
    double mass2 = mass1 * FastMath.exp(-FastMath.abs(dv1) / g0Ve);
    double dt2 =
        Physics.computeBurnDurationCapped(
            FastMath.abs(dv2),
            mass2,
            propulsion.isp(),
            propulsion.thrust(),
            ctx.stage().remainingFuel(mass2));

    AbsoluteDate s1 = clampStart(ctx.tPeri().shiftedBy(-dt1 / 2.0), ctx.entry().getDate());
    AbsoluteDate s2 = clampStart(ctx.tApo().shiftedBy(-dt2 / 2.0), s1.shiftedBy(dt1));
    return List.of(
        new Burn(s1, dt1, signedDirection(ctx.dir1(), dv1), dv1),
        new Burn(s2, dt2, signedDirection(ctx.dir2(), dv2), dv2));
  }

  /** Flies the two burns on a throwaway propagator and returns the state at the last burn's end. */
  private SpacecraftState simulateCircular(CircularContext ctx, List<Burn> burns) {
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(ctx.flight(), ctx.maxStep());
    propagator.setInitialState(ctx.entry());
    ReentryGuard.armQuiet(propagator, ctx.flight().gravity());
    DepletionGuard.armCappedBurn(propagator, ctx.stage().depletionFloor(), getName() + " (sim)");
    Frame frame = ctx.entry().getFrame();
    for (Burn burn : burns) {
      addManeuver(
          propagator,
          burn.start(),
          burn.dt(),
          burn.directionInertial(),
          frame,
          ctx.stage().propulsion());
    }
    Burn last = burns.getLast();
    return propagator.propagate(last.start().shiftedBy(last.dt()));
  }

  private boolean deliverable(CircularContext ctx, List<Burn> burns) {
    PropulsionSystem propulsion = ctx.stage().propulsion();
    double g0Ve = propulsion.isp() * Constants.G0_STANDARD_GRAVITY;
    double mass = ctx.entry().getMass();
    for (Burn burn : burns) {
      double required =
          Physics.computeBurnDuration(
              FastMath.abs(burn.dv()), mass, propulsion.isp(), propulsion.thrust());
      if (burn.dt() < required * (1.0 - BURN_CAPACITY_TOLERANCE)) {
        return false;
      }
      mass *= FastMath.exp(-FastMath.abs(burn.dv()) / g0Ve);
    }
    return true;
  }

  /**
   * Impulsive mean-Hohmann seed from the hand-off's mean apsides to a circle at {@code rStar}: burn
   * 1 at the mean perigee raises the mean apogee to {@code rStar}, burn 2 circularizes there.
   */
  private static double[] seedHohmann(double rp0, double ra0, double rStar, double mu) {
    double a0 = 0.5 * (rp0 + ra0);
    double aTransfer = 0.5 * (rp0 + rStar);
    double dv1 =
        FastMath.sqrt(mu * (2.0 / rp0 - 1.0 / aTransfer))
            - FastMath.sqrt(mu * (2.0 / rp0 - 1.0 / a0));
    double dv2 = FastMath.sqrt(mu / rStar) - FastMath.sqrt(mu * (2.0 / rStar - 1.0 / aTransfer));
    return new double[] {dv1, dv2};
  }

  private static Vector3D signedDirection(Vector3D prograde, double dv) {
    return dv >= 0 ? prograde : prograde.negate();
  }

  private static AbsoluteDate clampStart(AbsoluteDate start, AbsoluteDate floor) {
    return start.isBefore(floor) ? floor.shiftedBy(1.0e-3) : start;
  }

  private static double normInf(double[] v) {
    return FastMath.max(FastMath.abs(v[0]), FastMath.abs(v[1]));
  }

  // ── Elliptic target (and GEO): the historical single-burn trim ────────────

  private List<Burn> computeSingleBurn(
      SpacecraftState state, Vehicle vehicle, FlightContext context) {
    SpacecraftState stateAtApogee = detectStateAtApogee(state, context);
    if (stateAtApogee == null) {
      logger.info("Trim burn: no apogee detected within one period, skipping.");
      return List.of();
    }

    double mu = stateAtApogee.getOrbit().getMu();
    Vector3D rApo = stateAtApogee.getPVCoordinates().getPosition();
    Vector3D vCurrentApo = stateAtApogee.getPVCoordinates().getVelocity();
    double r2 = rApo.getNorm();
    // Centre the FLOWN altitude band on the requested orbit (spec orbit-reporting/02). Aiming at an
    // osculating perigee perches the mission at the TOP of the J2 short-period oscillation, so the
    // flown perigee can only fall, by the whole ~19 km amplitude. The amplitude itself is not a
    // choice — no orbit is flat under J2 — only the centring is, and it is worth a factor of two on
    // the worst-case deviation for ~5 m/s.
    double targetBandCentreRadius = 0.5 * ((EARTH_RADIUS + targetPerigeeAltitude) + r2);
    double rPerigeeTarget =
        FlownBandAim.resolve(
            targetBandCentreRadius,
            r2,
            aim ->
                new KeplerianOrbit(
                    new PVCoordinates(
                        rApo,
                        AnalyticHohmannTransferStage.computeTargetVelocityAtApogee(
                            rApo, vCurrentApo, mu, aim, r2, targetInclination)),
                    stateAtApogee.getFrame(),
                    stateAtApogee.getDate(),
                    mu));

    // Same function as the aim closure above: no drift is possible between what is aimed at and
    // what
    // is flown.
    Vector3D vTarget =
        AnalyticHohmannTransferStage.computeTargetVelocityAtApogee(
            rApo, vCurrentApo, mu, rPerigeeTarget, r2, targetInclination);
    Vector3D deltaV = vTarget.subtract(vCurrentApo);
    double dv = deltaV.getNorm();

    if (dv < SKIP_DV_THRESHOLD) {
      logger.info("Trim burn: residual ΔV={} m/s below threshold, skipping.", dv);
      return List.of();
    }

    ActiveStageInfo stageInfo = vehicle.resolveActiveStage(stateAtApogee.getMass());
    PropulsionSystem propulsion = stageInfo.propulsion();
    double dt =
        Physics.computeBurnDurationCapped(
            dv,
            stateAtApogee.getMass(),
            propulsion.isp(),
            propulsion.thrust(),
            stageInfo.remainingFuel(stateAtApogee.getMass()));

    AbsoluteDate burnStart = stateAtApogee.getDate().shiftedBy(-dt / 2.0);
    if (burnStart.isBefore(state.getDate())) {
      burnStart = state.getDate().shiftedBy(1.0e-3);
    }

    logger.info(
        "Trim burn plan: dv={} m/s, dt={}s, apogee altitude {} km, flown-band aim lift {} m",
        dv,
        dt,
        (r2 - EARTH_RADIUS) / 1000.0,
        rPerigeeTarget - (EARTH_RADIUS + targetPerigeeAltitude));

    return List.of(new Burn(burnStart, dt, deltaV.normalize(), dv));
  }

  /**
   * Detects the next apogee after {@code state} using a propagator with J2+ (same gravity model as
   * the optimization path). Returns the spacecraft state recorded at the apogee event, or null on
   * failure. Pattern matches {@code CircularizationBurnResolver#detectTimeToApoapsis}.
   *
   * <p>Package-private so {@link AnalyticApogeeCircularizationStage} reuses the same detection.
   */
  static SpacecraftState detectStateAtApogee(SpacecraftState state, FlightContext context) {
    // Burn-free coast: nothing ignites, so step at the large coast cap (the apogee found is set by
    // the detector's root-finder + dense output, not by the integration step). See bilan 08 §3.1.
    NumericalPropagator coastPropagator =
        OrekitService.get().createOptimizationPropagator(context, OrekitService.COAST_MAX_STEP);
    coastPropagator.setInitialState(state);
    // On a re-entering orbit the coast stops early, no apogee is recorded and this returns null —
    // which both callers already turn into an explicit failure (spec 03-garde-rentree §4.1).
    ReentryGuard.armQuiet(coastPropagator, context.gravity());

    RecordAndContinue recorder = new RecordAndContinue();
    ApsideDetector apsideDetector = new ApsideDetector(state.getOrbit()).withHandler(recorder);
    coastPropagator.addEventDetector(apsideDetector);

    double period = state.getOrbit().getKeplerianPeriod();
    coastPropagator.propagate(state.getDate().shiftedBy(period * 1.1));

    double minDt = 1.0; // skip the immediate apsis if we're already at one
    for (RecordAndContinue.Event event : recorder.getEvents()) {
      if (!event.isIncreasing()) {
        double dt = event.getState().getDate().durationFrom(state.getDate());
        if (dt > minDt) {
          return event.getState();
        }
      }
    }
    return null;
  }

  private void addBurns(
      NumericalPropagator propagator, SpacecraftState state, List<Burn> burns, Vehicle vehicle) {
    ActiveStageInfo stageInfo = vehicle.resolveActiveStage(state.getMass());
    DepletionGuard.armCappedBurn(propagator, stageInfo.depletionFloor(), getName());
    Frame frame = state.getFrame();
    for (Burn burn : burns) {
      addManeuver(
          propagator,
          burn.start(),
          burn.dt(),
          burn.directionInertial(),
          frame,
          stageInfo.propulsion());
    }
  }

  private static void addManeuver(
      NumericalPropagator propagator,
      AbsoluteDate start,
      double dt,
      Vector3D directionInertial,
      Frame frame,
      PropulsionSystem propulsion) {
    // FrameAlignedProvider maps inertial → body. We want body PLUS_I to point along the inertial
    // burn direction, i.e. r(directionInertial) = PLUS_I.
    Rotation inertialToBody = new Rotation(directionInertial, Vector3D.PLUS_I);
    FrameAlignedProvider attitude = new FrameAlignedProvider(inertialToBody, frame);
    propagator.addForceModel(
        new ConstantThrustManeuver(
            start, dt, propulsion.thrust(), propulsion.isp(), attitude, Vector3D.PLUS_I));
  }
}
