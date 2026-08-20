package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.OrbitElements;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisGenerator;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver;
import com.smousseur.orbitlab.simulation.mission.operation.EarthOrbitMission;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnProblem;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentSequence;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagePropellant;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

/**
 * Read-only diagnostic probe of the two gravity-turn frontiers under investigation. It changes no
 * behaviour: it flies existing missions at <b>forced</b> gravity-turn variables and prints what the
 * CMA-ES logs cannot show — the shape of the cost landscape around the retained solution, and where
 * a perigee deficit is created along the stage chain.
 *
 * <p><b>Why forced variables.</b> Both questions are about whether the retained solution is the
 * right point of a landscape, which no single optimizer run can answer: the run reports one point.
 * Forcing the variables removes CMA-ES entirely, so every number below is deterministic and the
 * probe costs mission propagations rather than tens of thousands of evaluations.
 *
 * <p>Opt-in, like the other integration loops:
 *
 * <pre>{@code
 * gradlew test --tests "*GravityTurnFloorProbeTest" -Dorbitlab.probe=true
 * }</pre>
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class GravityTurnFloorProbeTest {
  private static final Logger logger = LogManager.getLogger(GravityTurnFloorProbeTest.class);

  private static final double RE = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  /** Seconds above the staging floor sampled by the defect-A scan. */
  private static final double[] TRANSITION_OFFSETS = {0.0, 1.0, 2.0, 5.0, 10.0, 20.0, 40.0, 60.0};

  /** Pitch exponents sampled at each offset, bracketing the retained 0.354465. */
  private static final double[] EXPONENTS = {0.25, 0.354465, 0.5, 0.8};

  /** The solution the failing elliptic run retained (recorded 2026-08-04/05). */
  private static final double[] ELLIPTIC_RETAINED = {316.52191504680076, 0.17933503027454614};

  /** The solution the Falcon Heavy budget-loads run retained (recorded 2026-08-04/05). */
  private static final double[] FH_400_RETAINED = {151.97984025658428, 0.35446500155493776};

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  // ── Missions under probe ─────────────────────────────────────────────────

  /** Exactly the mission of {@code LEOMissionOptimizationTest#testFalconHeavyBudgetLoads}. */
  private static EarthOrbitMission falconHeavyBudgetLoads() {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 0.0);
    double[] loads = PropellantBudget.loadsForLeo(Launchers.FALCON_HEAVY, payload, 400_000, 45.96);
    return new EarthOrbitMission(
        "FH budget loads (probe)",
        new LaunchConfiguration(Launchers.FALCON_HEAVY, loads, payload),
        400_000);
  }

  /** Exactly the failing row of {@code LEOMissionOptimizationTest#testEllipticMissions}. */
  private static EarthOrbitMission elliptic200x1000() {
    return new EarthOrbitMission("LEO 200/1000 (probe)", 200_000, 1_000_000);
  }

  // ── Flight record ────────────────────────────────────────────────────────

  /**
   * Everything one forced flight yields. Altitudes in metres, masses in kg, angles in degrees.
   *
   * @param stagingComplete the earliest MECO completing staging (s) — the floor under scrutiny
   */
  private record Flight(
      double transitionTime,
      double exponent,
      double stagingComplete,
      double gtCost,
      double burn2Propellant,
      double mecoAltitude,
      double mecoApogee,
      double mecoPerigee,
      double mecoFpaDeg,
      double handOffVRad,
      double finalMass,
      double finalPerigee,
      double finalApogee,
      double s2Loaded,
      double s2Residual,
      Map<String, double[]> perStagePerigeeApogee,
      SpacecraftState insertionState) {

    double s2ResidualRatio() {
      return s2Loaded > 0 ? s2Residual / s2Loaded : 0.0;
    }
  }

  /**
   * Earliest MECO that completes staging, rebuilt the way the ascent phases do: fly the mission's
   * vertical ascent, then read the maneuver the gravity turn would build from that entry mass.
   */
  private static double stagingCompleteTime(EarthOrbitMission mission, AscentProfile profile) {
    AbsoluteDate epoch = epoch();
    SpacecraftState initial = mission.getInitialState(epoch);
    mission.setCurrentState(initial);
    SpacecraftState postVa = mission.getStages().getFirst().propagateStandalone(initial, mission);
    GravityTurnManeuver maneuver =
        new GravityTurnManeuver(
            mission.getVehicle(),
            postVa.getMass(),
            FastMath.toRadians(profile.pitchKickAngleDeg()),
            Physics.getLaunchAzimuth(),
            profile.interstageCoastDuration(),
            FlightContext.earth());
    return maneuver.getStagingCompleteTime();
  }

  private static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  /**
   * Flies the whole mission with the gravity turn pinned to {@code vars}, mirroring the {@code
   * MissionOptimizer} loop with the CMA-ES call replaced by the forced result. The final coast is
   * skipped: the post-trim orbit already carries the perigee and apogee it would reveal.
   */
  private static Flight fly(EarthOrbitMission mission, double[] vars, double stagingComplete) {
    SpacecraftState initial = mission.getInitialState(epoch());
    mission.setCurrentState(initial);

    double gtCost = Double.NaN;
    double burn2Propellant = Double.NaN;
    SpacecraftState meco = null;
    SpacecraftState last = initial;
    Map<String, double[]> perStage = new LinkedHashMap<>();

    for (MissionStage stage : mission.getStages()) {
      if ("Coasting".equals(stage.getName())) {
        break;
      }
      double massIn = mission.getCurrentState().getMass();

      if (stage instanceof OptimizableMissionStage<?> optimizable) {
        SpacecraftState entry = mission.getCurrentState();
        GravityTurnProblem problem = (GravityTurnProblem) optimizable.buildProblem(mission);
        // propagate() records the staging shortfall computeCost() reads, on this thread.
        SpacecraftState scored = problem.propagate(vars);
        gtCost = problem.computeCost(scored);
        mission.setCurrentState(entry);
        optimizable.applyOptimization(new OptimizationResult(vars, gtCost, scored, 0, entry));
        last = stage.propagateStandalone(entry, mission);
      } else {
        last = stage.propagateStandalone(mission.getCurrentState(), mission);
      }
      mission.setCurrentState(last);

      if (AscentSequence.SECOND_BURN_NAME.equals(stage.getName())) {
        burn2Propellant = massIn - last.getMass();
        meco = last;
      }
      perStage.put(stage.getName(), perigeeApogee(last));
    }

    double[] finalPa = perigeeApogee(last);
    double[] mecoPa = meco != null ? perigeeApogee(meco) : new double[] {Double.NaN, Double.NaN};
    List<StagePropellant> split = mission.getVehicle().resolveStagePropellant(last.getMass());
    StagePropellant s2 = split.size() > 1 ? split.get(1) : split.getLast();

    return new Flight(
        vars[0],
        vars[1],
        stagingComplete,
        gtCost,
        burn2Propellant,
        meco != null ? meco.getPosition().getNorm() - RE : Double.NaN,
        mecoPa[1],
        mecoPa[0],
        meco != null ? fpaDeg(meco) : Double.NaN,
        meco != null ? radialSpeed(meco) : Double.NaN,
        last.getMass(),
        finalPa[0],
        finalPa[1],
        s2.loaded(),
        s2.residual(),
        perStage,
        last);
  }

  /** Perigee and apogee altitudes (m) of the osculating orbit of {@code state}. */
  private static double[] perigeeApogee(SpacecraftState state) {
    KeplerianOrbit orbit =
        new KeplerianOrbit(
            state.getPVCoordinates(), state.getFrame(), state.getDate(), Constants.WGS84_EARTH_MU);
    double a = orbit.getA();
    double e = orbit.getE();
    return new double[] {a * (1.0 - e) - RE, a * (1.0 + e) - RE};
  }

  /** Radial component of the velocity (m/s) — what the transfer's first burn has to cancel. */
  private static double radialSpeed(SpacecraftState state) {
    return Vector3D.dotProduct(
        state.getPVCoordinates().getVelocity(), state.getPosition().normalize());
  }

  private static double fpaDeg(SpacecraftState state) {
    Vector3D pos = state.getPosition();
    Vector3D vel = state.getPVCoordinates().getVelocity();
    Vector3D zenith = pos.normalize();
    double vRad = Vector3D.dotProduct(vel, zenith);
    double vNorm = vel.getNorm();
    double vTan = FastMath.sqrt(FastMath.max(0.0, vNorm * vNorm - vRad * vRad));
    return FastMath.toDegrees(FastMath.atan2(vRad, vTan));
  }

  // ── Defect A: is the staging floor an artefact, a search trap, or the envelope? ──

  /**
   * Scans MECO above the staging floor on the Falcon Heavy budget-loads profile, at several pitch
   * exponents, and prints the two curves the diagnosis needs:
   *
   * <ul>
   *   <li><b>gravity-turn cost vs MECO</b> — whether CMA-ES found the minimum of its own objective,
   *       or stopped against the cliff. Monotone increasing from the floor means the pin is real; a
   *       lower point above the floor means the cliff censors the population and traps the search;
   *   <li><b>final mass vs MECO</b> — whether that objective is the right one. The vehicle, the
   *       loads and the jettison sequence are identical at every point of the scan, so final mass
   *       is an exact inverse proxy for total propellant burnt.
   * </ul>
   */
  /**
   * One evaluated gravity-turn candidate: exactly what CMA-ES scores, and nothing more.
   *
   * @param failed the propagation returned the entry state, so the cost is the penalty branch
   */
  private record HandOff(
      double transitionTime,
      double exponent,
      double gtCost,
      double altitude,
      double perigee,
      double apogee,
      double fpaDeg,
      double mass,
      boolean failed) {}

  /**
   * Evaluates the gravity turn alone over the {@code offset × exponent} grid — the ascent chain and
   * the cost function, which is the whole of what CMA-ES sees. Nothing downstream is flown: the
   * first scan wrote here flew each candidate's full mission, and the apside detectors of the
   * transfer and trim propagate up to a full orbit apiece, which turned 64 points into 36 minutes
   * for measurements that do not depend on them.
   */
  private static List<HandOff> gravityTurnCurve(EarthOrbitMission mission, double stagingComplete) {
    SpacecraftState initial = mission.getInitialState(epoch());
    mission.setCurrentState(initial);
    SpacecraftState postVa = mission.getStages().getFirst().propagateStandalone(initial, mission);
    OptimizableMissionStage<?> firstBurn = (OptimizableMissionStage<?>) mission.getStages().get(1);

    List<HandOff> out = new ArrayList<>();
    for (double offset : TRANSITION_OFFSETS) {
      for (double exponent : EXPONENTS) {
        double[] vars = {stagingComplete + offset, exponent};
        // Rebuilt per candidate, as the optimizer does: buildProblem reads the entry state.
        mission.setCurrentState(postVa);
        GravityTurnProblem problem = (GravityTurnProblem) firstBurn.buildProblem(mission);
        SpacecraftState handOff = problem.propagate(vars);
        double cost = problem.computeCost(handOff);
        boolean failed = handOff.getDate().durationFrom(postVa.getDate()) < 1.0;
        double[] pa = perigeeApogee(handOff);
        out.add(
            new HandOff(
                vars[0],
                exponent,
                cost,
                handOff.getPosition().getNorm() - RE,
                pa[0],
                pa[1],
                fpaDeg(handOff),
                handOff.getMass(),
                failed));
      }
    }
    return out;
  }

  /**
   * Prediction A1 and A2, on both profiles: does the cost rise monotonically from the staging floor
   * (the pin is a real optimum), or is there a cheaper candidate above it that the penalty cliff
   * hides from CMA-ES (the pin is a search trap)? And is the apogee ceiling already saturated at
   * the floor, which would explain why no second burn can help?
   */
  @Test
  void defautA_gravityTurnCostCurve() {
    for (String tag : new String[] {"FH-400", "LEO-200x1000"}) {
      EarthOrbitMission mission = "FH-400".equals(tag) ? falconHeavyBudgetLoads() : elliptic200x1000();
      EarthOrbitMission twin = "FH-400".equals(tag) ? falconHeavyBudgetLoads() : elliptic200x1000();
      double stagingComplete = stagingCompleteTime(twin, Launchers.FALCON_HEAVY.ascentProfile());
      logger.info("[A/{}] stagingCompleteTime = {} s", tag, fmt(stagingComplete, 5));

      List<HandOff> curve = gravityTurnCurve(mission, stagingComplete);
      for (HandOff h : curve) {
        logger.info(
            "[A/{}] t=floor+{} s exp={} | cost={} | alt={} m | {}×{} m | fpa={}° | mass={} kg{}",
            tag,
            fmt(h.transitionTime() - stagingComplete, 1),
            fmt(h.exponent(), 6),
            fmt(h.gtCost(), 6),
            fmt(h.altitude(), 0),
            fmt(h.perigee(), 0),
            fmt(h.apogee(), 0),
            fmt(h.fpaDeg(), 3),
            fmt(h.mass(), 1),
            h.failed() ? " [PROPAGATION FAILED — penalty branch]" : "");
      }
      HandOff best =
          curve.stream().min((a, b) -> Double.compare(a.gtCost(), b.gtCost())).orElseThrow();
      logger.info(
          "[A/{}] A1 — cheapest candidate: cost={} at t=floor+{} s, exp={}, apogee={} m",
          tag,
          fmt(best.gtCost(), 6),
          fmt(best.transitionTime() - stagingComplete, 1),
          fmt(best.exponent(), 6),
          fmt(best.apogee(), 0));
    }
  }

  /**
   * Prediction A3, on the few points that matter: is the mission cheaper in propellant with a
   * non-zero second burn? Restricted to the Falcon Heavy budget-loads profile and to candidates
   * whose hand-off is sane — a degenerate hand-off sends the analytic transfer propagating toward
   * an apogee that may be hours away, which is what made the first scan unusable.
   */
  @Test
  void defautA_missionPropellantAroundTheFloor() {
    AscentProfile profile = Launchers.FALCON_HEAVY.ascentProfile();
    double stagingComplete = stagingCompleteTime(falconHeavyBudgetLoads(), profile);
    double exponent = FH_400_RETAINED[1];
    logger.info(
        "[A3] FH-400 — stagingComplete={} s, exponent held at the retained {}",
        fmt(stagingComplete, 5),
        fmt(exponent, 6));

    List<Flight> flights = new ArrayList<>();
    for (double offset : new double[] {0.0, 2.0, 5.0, 10.0, 20.0}) {
      double[] vars = {stagingComplete + offset, exponent};
      try {
        Flight flight = fly(falconHeavyBudgetLoads(), vars, stagingComplete);
        flights.add(flight);
        logger.info("[A3] {}", row(flight, offset));
      } catch (RuntimeException e) {
        // Past a few seconds of second burn the hand-off is so far outside the apogee window that
        // the analytic transfer has no apside to aim at ("No apogee found within one transfer
        // half-period"). That is the measurement, not an error: the point is infeasible.
        logger.info("[A3] t=floor+{} s: mission infeasible — {}", fmt(offset, 1), e.getMessage());
      }
    }
    summarize("A3/FH-400", flights, stagingComplete);
  }

  /**
   * The sizing latitude the budget-loads case passes to {@link PropellantBudget} is 45.96°, while
   * the mission it then builds launches from {@code EarthMission.DEFAULT_LATITUDE} = 5.23°. The
   * Earth-rotation assist credited to the budget is therefore {@code 465·cos(45.96°)} = 323 m/s
   * instead of the 463 m/s the flight actually gets — 140 m/s of ΔV budgeted for nothing, which
   * inverse Tsiolkovsky turns into propellant loaded into the sized top stage and carried dead.
   *
   * <p>Flies both sizings at the gravity turn's own optimum (the staging floor, established by the
   * cost curve above) and reports the residual each leaves.
   */
  @Test
  void defautA_sizingLatitudeMismatch() {
    for (double lat : new double[] {45.96, 5.23}) {
      Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 0.0);
      double[] loads = PropellantBudget.loadsForLeo(Launchers.FALCON_HEAVY, payload, 400_000, lat);
      EarthOrbitMission mission =
          new EarthOrbitMission(
              "FH sized at " + lat + "°",
              new LaunchConfiguration(Launchers.FALCON_HEAVY, loads, payload),
              400_000);
      EarthOrbitMission twin =
          new EarthOrbitMission(
              "twin",
              new LaunchConfiguration(
                  Launchers.FALCON_HEAVY,
                  loads,
                  Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 0.0)),
              400_000);
      double stagingComplete = stagingCompleteTime(twin, Launchers.FALCON_HEAVY.ascentProfile());

      double[] vars = {stagingComplete, FH_400_RETAINED[1]};
      Flight flight = fly(mission, vars, stagingComplete);
      logger.info(
          "[A-lat] sizing latitude {}° | S2 load={} kg | {}",
          fmt(lat, 2),
          fmt(loads[loads.length - 1], 1),
          row(flight, 0.0));
    }
  }

  /**
   * What the 2026-08-05 run of {@code testFalconHeavyBudgetLoads} exposed: with the budget sized at
   * the flown latitude, the mission's success depends on which exponent the gravity turn retains,
   * not on the load. Flies the <b>same</b> loads at the two exponents and reports both.
   *
   * <p>0.354465 is what the optimizer retained against the over-sized (45.96°) budget; 0.386137 is
   * what it retains against the correctly sized one, at a <em>lower</em> gravity-turn cost (0.1309
   * against 0.2254). If the mission succeeds at the first and fails at the second, the gravity
   * turn's objective is mis-ranking hand-offs: it prefers a state the transfer then has to pay for.
   */
  @Test
  void defautA_exponentDecidesTheMissionAtTheTightLoad() {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 0.0);
    double[] loads = PropellantBudget.loadsForLeo(Launchers.FALCON_HEAVY, payload, 400_000, 5.23);
    logger.info("[A-exp] loads sized at 5.23 deg: S2={} kg", fmt(loads[loads.length - 1], 1));

    for (double exponent : new double[] {0.35446500155493776, 0.38613687144408326}) {
      EarthOrbitMission mission =
          new EarthOrbitMission(
              "FH tight loads, exponent " + exponent,
              new LaunchConfiguration(
                  Launchers.FALCON_HEAVY,
                  loads,
                  Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 0.0)),
              400_000);
      EarthOrbitMission twin =
          new EarthOrbitMission(
              "twin",
              new LaunchConfiguration(
                  Launchers.FALCON_HEAVY,
                  loads,
                  Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 0.0)),
              400_000);
      double stagingComplete = stagingCompleteTime(twin, Launchers.FALCON_HEAVY.ascentProfile());
      Flight flight = fly(mission, new double[] {stagingComplete, exponent}, stagingComplete);
      logger.info(
          "[A-exp] exponent={} | handoff vRad={} m/s | {}",
          fmt(exponent, 8),
          fmt(radialVelocity(flight), 1),
          row(flight, 0.0));
    }
  }

  /**
   * Radial velocity implied by the recorded hand-off (m/s), the quantity the transfer must cancel.
   */
  private static double radialVelocity(Flight flight) {
    return flight.handOffVRad();
  }

  // ── Defect B: where are the 17 km of perigee lost? ───────────────────────

  /**
   * Replays the elliptic 200/1000 mission at the variables the failing run retained, and prints the
   * perigee and apogee <b>after every stage</b>. That is the measurement separating the three
   * candidate causes: a hand-off apogee the gravity turn was asked to place below the target
   * perigee (constraint spec), a fixed absolute loss in the perigee-raising burn (targeting), or
   * the 30 km guard rail.
   */
  @Test
  void defautB_perStageOrbitOfTheFailingEllipticRun() {
    AscentProfile profile = Launchers.FALCON_HEAVY.ascentProfile();
    double stagingComplete = stagingCompleteTime(elliptic200x1000(), profile);
    Flight flight = fly(elliptic200x1000(), ELLIPTIC_RETAINED, stagingComplete);

    logger.info(
        "[B] retained vars t={} s exponent={} | stagingComplete={} s | gtCost={}",
        fmt(flight.transitionTime(), 5),
        fmt(flight.exponent(), 6),
        fmt(stagingComplete, 3),
        fmt(flight.gtCost(), 3));
    logger.info(
        "[B] GT hand-off: alt={} m | FPA={}° | perigee={} m | apogee={} m",
        fmt(flight.mecoAltitude(), 0),
        fmt(flight.mecoFpaDeg(), 3),
        fmt(flight.mecoPerigee(), 0),
        fmt(flight.mecoApogee(), 0));
    logger.info("[B] target: perigee 200000 m, apogee 1000000 m; tolerance ±14000 m on perigee");
    flight
        .perStagePerigeeApogee()
        .forEach(
            (name, pa) ->
                logger.info(
                    "[B] after '{}': perigee={} m, apogee={} m",
                    name,
                    fmt(pa[0], 0),
                    fmt(pa[1], 0)));
  }

  /**
   * The follow-up the per-stage orbit forces: the post-trim osculating orbit is on target, so the
   * 17 km the assertion sees are created <b>after</b> the last burn. This flies the coast through
   * {@link MissionEphemerisGenerator} — the very path the failing assertion reads — and compares,
   * sample by sample, the geodetic altitude against the two-body perigee of the same state.
   *
   * <p>Two outcomes, and they call for opposite fixes. If the osculating perigee itself walks from
   * 200 km down to 183 km, the analytic stages target a two-body perigee the 8×8 flight does not
   * keep, and the miss is a targeting bias. If the osculating perigee holds at 200 km while the
   * geodetic altitude dips to 183 km, the two quantities are simply not the same thing and the
   * assertion is comparing a geodetic minimum against a Keplerian target.
   */
  @Test
  void defautB_coastMinimumVersusOsculatingPerigee() {
    coastProbe("200x1000", elliptic200x1000(), elliptic200x1000(), ELLIPTIC_RETAINED, 200_000);
    // The same measurement on a profile that PASSES. A systematic offset of the same magnitude
    // here would mean the deficit is not specific to the failing case — only hidden by a relative
    // tolerance three times wider in absolute terms (±28 km at 400 km against ±14 km at 200 km).
    coastProbe(
        "FH-400", falconHeavyBudgetLoads(), falconHeavyBudgetLoads(), FH_400_RETAINED, 400_000);
  }

  /**
   * Step 0 of the mean-elements work (spec orbit-reporting/01 section 5.1). Read-only: measures, at
   * insertion, the gap between the osculating orbit — the one the analytic stages aim at and hit to
   * the metre — and the mean orbit, the one the user asked for.
   *
   * <p>The prediction under test: insertion falling at the top of the J2 oscillation, the mean
   * orbit should read around {@code target − a*f}, i.e. ~9.7 km below target at 400 km. If the sign
   * flips, or differs between profiles, insertion does not land at the same point of the
   * oscillation from one profile to the next and section 2 of the spec has to be rewritten.
   *
   * <p>Measured 2026-08-05: −9 388 m on FH-400 (96% of a*f) and −7 363 m on the elliptic profile
   * (78%), negative on both. The prediction holds.
   */
  @Test
  void meanVersusOsculatingAtInsertion() {
    meanOrbitProbe(
        "FH-400", falconHeavyBudgetLoads(), falconHeavyBudgetLoads(), FH_400_RETAINED, 400_000);
    meanOrbitProbe(
        "LEO-200x1000", elliptic200x1000(), elliptic200x1000(), ELLIPTIC_RETAINED, 200_000);
  }

  private static void meanOrbitProbe(
      String tag, EarthOrbitMission mission, EarthOrbitMission twin, double[] vars, double targetPerigee) {
    double stagingComplete = stagingCompleteTime(twin, Launchers.FALCON_HEAVY.ascentProfile());
    Flight flight = fly(mission, vars, stagingComplete);

    KeplerianOrbit insertion =
        new KeplerianOrbit(
            flight.insertionState().getPVCoordinates(),
            flight.insertionState().getFrame(),
            flight.insertionState().getDate(),
            Constants.WGS84_EARTH_MU);

    OrbitElements osculating = OrbitElements.osculating(insertion);
    Optional<OrbitElements> mean = OrbitElements.mean(insertion);

    double a = osculating.semiMajorAxis();
    double j2 = -Constants.WGS84_EARTH_C20;
    double af = a * 1.5 * j2 * (RE / a) * (RE / a);

    logger.info("[M/{}] osculating at insertion: {}", tag, osculating.format());
    if (mean.isEmpty()) {
      logger.warn("[M/{}] mean orbit UNAVAILABLE — Eckstein-Hechler did not converge", tag);
      return;
    }
    logger.info("[M/{}] mean at insertion:       {}", tag, mean.get().format());
    logger.info(
        "[M/{}] target perigee={} m | osculating−target={} m | mean−target={} m"
            + " | mean−osculating={} m | closed-form a·f={} m",
        tag,
        fmt(targetPerigee, 0),
        fmt(osculating.perigeeAltitude() - targetPerigee, 0),
        fmt(mean.get().perigeeAltitude() - targetPerigee, 0),
        fmt(mean.get().perigeeAltitude() - osculating.perigeeAltitude(), 0),
        fmt(af, 0));
  }

  /**
   * Flown-band centring and its propellant cost — the baseline of spec orbit-reporting/02, and the
   * measurement that falsifies it after the retargeting (spec 02 sections 5 P1 and 5 P2).
   *
   * <p>Prints, per profile: the min and max geodetic altitude of the final coast, their centre, the
   * offset the centring would need ({@code target − centre}), the worst-case deviation, and the
   * sized stage's residual.
   *
   * <p>Deliberately a NEW probe rather than an extension of {@link
   * #meanVersusOsculatingAtInsertion} or {@code coastProbe}: their outputs are the baseline
   * recorded in spec 01, and changing them would make yesterday's measurements incomparable to
   * tomorrow's.
   *
   * <p>After the change both centres must land within 1 500 m of the target. If either misses, the
   * "mean perigee = band centre" identity spec 02 rests on is wrong and the work stops there.
   */
  @Test
  void flownBandCentringAndCost() {
    bandProbe(
        "FH-400", falconHeavyBudgetLoads(), falconHeavyBudgetLoads(), FH_400_RETAINED, 400_000);
    bandProbe("LEO-200x1000", elliptic200x1000(), elliptic200x1000(), ELLIPTIC_RETAINED, 200_000);
  }

  private static void bandProbe(
      String tag, EarthOrbitMission mission, EarthOrbitMission twin, double[] vars, double targetPerigee) {
    double stagingComplete = stagingCompleteTime(twin, Launchers.FALCON_HEAVY.ascentProfile());
    Flight flight = fly(mission, vars, stagingComplete);

    // Re-run the chain through the sampling generator, exactly as MissionOptimizer closes a run.
    SpacecraftState initial = mission.getInitialState(epoch());
    MissionEphemeris ephemeris = new MissionEphemerisGenerator().generate(mission, initial);

    // The centring quantity is the excursion of the OSCULATING PERIGEE along the coast, not of
    // the flown altitude. On an elliptic target the altitude spans the whole ellipse — measured
    // 2026-08-05 on the 200x1000 profile: 183 094 -> 1 000 573 m, whose "centre" at 591 833 m is
    // the middle of the ellipse and says nothing about the J2 oscillation this probe is after.
    // The perigee excursion is the same quantity on both profiles.
    double perigeeMin = Double.POSITIVE_INFINITY;
    double perigeeMax = Double.NEGATIVE_INFINITY;
    double altitudeMin = Double.POSITIVE_INFINITY;
    double altitudeMax = Double.NEGATIVE_INFINITY;
    for (MissionEphemerisPoint pt : ephemeris.allPoints()) {
      if (!"Coasting".equals(pt.stageName())) {
        continue;
      }
      KeplerianOrbit orbit =
          new KeplerianOrbit(
              new org.orekit.utils.PVCoordinates(pt.position(), pt.velocity()),
              initial.getFrame(),
              pt.time(),
              Constants.WGS84_EARTH_MU);
      double perigee = orbit.getA() * (1.0 - orbit.getE()) - RE;
      perigeeMin = FastMath.min(perigeeMin, perigee);
      perigeeMax = FastMath.max(perigeeMax, perigee);
      altitudeMin = FastMath.min(altitudeMin, pt.altitudeMeters());
      altitudeMax = FastMath.max(altitudeMax, pt.altitudeMeters());
    }
    if (!Double.isFinite(perigeeMin) || !Double.isFinite(perigeeMax)) {
      logger.warn("[C/{}] no Coasting samples in the ephemeris", tag);
      return;
    }

    double centre = 0.5 * (perigeeMin + perigeeMax);
    logger.info(
        "[C/{}] osculating perigee excursion {} -> {} m | centre {} m | target {} m"
            + " | required offset {} m | worst-case deviation {} m",
        tag,
        fmt(perigeeMin, 0),
        fmt(perigeeMax, 0),
        fmt(centre, 0),
        fmt(targetPerigee, 0),
        fmt(targetPerigee - centre, 0),
        fmt(FastMath.max(targetPerigee - perigeeMin, perigeeMax - targetPerigee), 0));
    // THE quantity the chantier is judged on: the flown altitude band and where its centre sits.
    // Removing it in favour of the perigee excursion alone was the mistake that let "the band is
    // centred" go unmeasured through a whole implementation (spec 02 section 5.5.2). On an
    // elliptic target this band spans the whole ellipse, which is exactly what its centre means
    // there — the mid-point of the requested apsides is the reference.
    logger.info(
        "[C/{}] flown altitude band {} -> {} m | centre {} m | min is {} m under target",
        tag,
        fmt(altitudeMin, 0),
        fmt(altitudeMax, 0),
        fmt(0.5 * (altitudeMin + altitudeMax), 0),
        fmt(targetPerigee - altitudeMin, 0));
    logger.info(
        "[C/{}] sized stage residual {} kg of {} kg loaded (ratio {}), final mass {} kg",
        tag,
        fmt(flight.s2Residual(), 0),
        fmt(flight.s2Loaded(), 0),
        fmt(flight.s2ResidualRatio(), 4),
        fmt(flight.finalMass(), 0));
  }

  private static void coastProbe(
      String tag, EarthOrbitMission mission, EarthOrbitMission twin, double[] vars, double targetPerigee) {
    AscentProfile profile = Launchers.FALCON_HEAVY.ascentProfile();
    double stagingComplete = stagingCompleteTime(twin, profile);
    fly(mission, vars, stagingComplete);

    // Re-run the chain through the sampling generator, exactly as MissionOptimizer closes a run.
    SpacecraftState initial = mission.getInitialState(epoch());
    MissionEphemeris ephemeris = new MissionEphemerisGenerator().generate(mission, initial);

    MissionEphemerisPoint lowest = null;
    MissionEphemerisPoint firstCoast = null;
    int coastPoints = 0;
    for (MissionEphemerisPoint pt : ephemeris.allPoints()) {
      if (!"Coasting".equals(pt.stageName())) {
        continue;
      }
      coastPoints++;
      if (firstCoast == null) {
        firstCoast = pt;
      }
      if (lowest == null || pt.altitudeMeters() < lowest.altitudeMeters()) {
        lowest = pt;
      }
    }
    if (lowest == null) {
      logger.warn("[B2/{}] no Coasting samples in the ephemeris", tag);
      return;
    }

    logger.info("[B2/{}] coast samples: {}", tag, coastPoints);
    logCoastPoint(tag, "first coast sample", firstCoast, initial);
    logCoastPoint(tag, "lowest coast sample", lowest, initial);
    logger.info(
        "[B2/{}] geodetic min {} m vs target {} m → deficit {} m (tolerance ±{} m)",
        tag,
        fmt(lowest.altitudeMeters(), 0),
        fmt(targetPerigee, 0),
        fmt(targetPerigee - lowest.altitudeMeters(), 0),
        fmt(0.07 * targetPerigee, 0));
  }

  /** Prints geodetic altitude, spherical altitude and two-body perigee of one ephemeris sample. */
  private static void logCoastPoint(
      String tag, String label, MissionEphemerisPoint pt, SpacecraftState reference) {
    KeplerianOrbit orbit =
        new KeplerianOrbit(
            new org.orekit.utils.PVCoordinates(pt.position(), pt.velocity()),
            reference.getFrame(),
            pt.time(),
            Constants.WGS84_EARTH_MU);
    double perigee = orbit.getA() * (1.0 - orbit.getE()) - RE;
    double apogee = orbit.getA() * (1.0 + orbit.getE()) - RE;
    logger.info(
        "[B2/{}] {}: t={} | geodetic alt={} m | spherical alt={} m | osculating {}×{} m | ecc={}",
        tag,
        label,
        pt.time(),
        fmt(pt.altitudeMeters(), 0),
        fmt(pt.position().getNorm() - RE, 0),
        fmt(perigee, 0),
        fmt(apogee, 0),
        fmt(orbit.getE(), 6));
  }

  // ── Reporting ────────────────────────────────────────────────────────────

  private static String row(Flight f, double offset) {
    return String.format(
        Locale.ROOT,
        "t=floor+%5.1f s (%9.4f) exp=%.6f | gtCost=%10.6f | burn2=%7.1f kg | "
            + "handoff alt=%8.0f m apo=%9.0f m fpa=%6.3f° | final %8.0f×%8.0f m | "
            + "mass=%9.1f kg | S2 residual=%6.0f kg (%.1f%%)",
        offset,
        f.transitionTime(),
        f.exponent(),
        f.gtCost(),
        f.burn2Propellant(),
        f.mecoAltitude(),
        f.mecoApogee(),
        f.mecoFpaDeg(),
        f.finalPerigee(),
        f.finalApogee(),
        f.finalMass(),
        f.s2Residual(),
        100.0 * f.s2ResidualRatio());
  }

  /** Prints the two verdicts the scan exists to deliver, per prediction A1 and A3. */
  private static void summarize(String tag, List<Flight> flights, double stagingComplete) {
    Flight bestCost =
        flights.stream().min((a, b) -> Double.compare(a.gtCost(), b.gtCost())).orElseThrow();
    Flight lightest =
        flights.stream().max((a, b) -> Double.compare(a.finalMass(), b.finalMass())).orElseThrow();

    logger.info(
        "[{}] A1 — cheapest GT cost {} at t=floor+{} s (exp={}), burn2={} kg",
        tag,
        fmt(bestCost.gtCost(), 6),
        fmt(bestCost.transitionTime() - stagingComplete, 1),
        fmt(bestCost.exponent(), 6),
        fmt(bestCost.burn2Propellant(), 1));
    logger.info(
        "[{}] A3 — highest final mass {} kg at t=floor+{} s (exp={}), final orbit {}×{} m",
        tag,
        fmt(lightest.finalMass(), 1),
        fmt(lightest.transitionTime() - stagingComplete, 1),
        fmt(lightest.exponent(), 6),
        fmt(lightest.finalPerigee(), 0),
        fmt(lightest.finalApogee(), 0));
  }

  private static String fmt(double value, int decimals) {
    return String.format(Locale.ROOT, "%." + decimals + "f", value);
  }
}
