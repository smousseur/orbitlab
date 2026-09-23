package com.smousseur.orbitlab.simulation.mission.bench;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.flight.DragContext;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.ArcTransition;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.detector.AtmosphericInterfaceDetector;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryDetector;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryGuard;
import com.smousseur.orbitlab.simulation.mission.disposal.DeorbitSequence;
import com.smousseur.orbitlab.simulation.mission.disposal.DeorbitTail;
import com.smousseur.orbitlab.simulation.mission.ephemeris.DebrisTrack;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisGenerator;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.MissionComposer;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlan;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.stage.DeorbitBurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.StageNames;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.FrameAlignedProvider;
import org.orekit.attitudes.LofOffset;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.frames.LOFType;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.AltitudeDetector;
import org.orekit.propagation.events.ApsideDetector;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.events.FunctionalDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * MIS-10 / L0 — the measured baseline of the deorbit chantier. NOT a gate: it changes no {@code
 * src/main} and asserts nothing. Run with {@code -Dorbitlab.probe=true --tests
 * '*Mis10DeorbitBaselineProbe*'}.
 *
 * <p>{@link #reentryTailRegime} answers the compute question from representative states: the
 * propelled Earth-observation payload on a 400 km circular orbit burns its whole disposal reserve
 * (sized by the production {@code PropellantBudget.disposalReserveFor}) retrograde on its own
 * engine, then falls under drag. The fall is flown under the two recipes production already has —
 * the debris one ({@link #DEBRIS_MAX_STEP} cap, geodetic-0 floor) and the stage-chain one ({@code
 * COAST_MAX_STEP}, {@code ReentryGuard.arm}) — with the Kármán-line detector armed on both.
 *
 * <p>{@link #horizonOfProductionMissions} flies a LEO and a GEO reference through the real planner,
 * reads what the payload does over the trailing coast, and re-flies that coast with the propagator
 * {@code StageLegRunner} would build to count its steps and list the detectors armed on it.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class Mis10DeorbitBaselineProbe {

  private static final PayloadModel LEO_PAYLOAD = Payloads.EARTH_OBSERVATION_SAT;

  private static final double ORBIT_ALTITUDE = 400_000.0;

  private static final double[] REENTRY_PERIGEES = {100_000.0, 50_000.0, 25_000.0, 0.0};

  /**
   * Kourou due east, the ISS plane, and a sun-synchronous plane: the latitudes a fall can end at.
   */
  private static final double[] INCLINATIONS_DEG = {5.2, 51.6, 97.5};

  /** Cap on a fall that never reaches a floor: the order of the default LEO restitution horizon. */
  private static final double TAIL_HORIZON_SECONDS = 3.0 * 86_400.0;

  /** Mirror of the private {@code DebrisGenerator.REENTRY_MAX_STEP_SECONDS}. */
  private static final double DEBRIS_MAX_STEP = 15.0;

  /** Mirror of the private {@code MissionRenderer.LANDED_ALTITUDE_METERS}. */
  private static final double LANDED_ALTITUDE_METERS = 1000.0;

  /** Safety cap: abort a run past this many steps rather than let an unbounded decay hang. */
  private static final long STEP_CAP = 1_000_000L;

  /** The burn ignites one second into its propagation, so the trigger never sits on t0. */
  private static final double IGNITION_OFFSET_SECONDS = 1.0;

  /** Set once Orekit data is loaded: the UTC scale cannot be built before. */
  private static AbsoluteDate epoch;

  private enum Recipe {
    DEBRIS,
    STAGE_CHAIN
  }

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  void reentryTailRegime() {
    GravitationalContext earth = GravitationalContext.earth();
    OneAxisEllipsoid ellipsoid = OrekitService.get().getEarthEllipsoid();
    FlightContext context =
        new FlightContext(
            earth, new DragContext(LEO_PAYLOAD.aerodynamics(), AtmosphereModel.NRLMSISE));
    PropulsionSystem engine = LEO_PAYLOAD.propulsion();
    double dry = LEO_PAYLOAD.defaultDryMass();
    double exhaustVelocity = engine.isp() * Constants.G0_STANDARD_GRAVITY;
    double massFlow = engine.thrust() / exhaustVelocity;
    double semiMajor = earth.equatorialRadius() + ORBIT_ALTITUDE;
    double period = 2.0 * FastMath.PI * FastMath.sqrt(FastMath.pow(semiMajor, 3) / earth.mu());

    System.out.printf(
        Locale.ROOT,
        "%nMIS-10 / L0 (a) — %s, dry %.0f kg, %.0f N / Isp %.0f s, %.2f m² Cd %.1f, orbit %.0f km"
            + " circ., period %.0f s%n",
        LEO_PAYLOAD.id(),
        dry,
        engine.thrust(),
        engine.isp(),
        LEO_PAYLOAD.aerodynamics().crossSection(),
        LEO_PAYLOAD.aerodynamics().dragCoefficient(),
        ORBIT_ALTITUDE / 1000.0,
        period);

    for (double reentryPerigee : REENTRY_PERIGEES) {
      double reserve =
          PropellantBudget.disposalReserveFor(LEO_PAYLOAD, dry, ORBIT_ALTITUDE, reentryPerigee);
      double burnSeconds = reserve / massFlow;
      double deliveredDeltaV = exhaustVelocity * FastMath.log((dry + reserve) / dry);
      System.out.printf(
          Locale.ROOT,
          "%n-- target perigee %.0f km: reserve %.1f kg, ΔV of the whole reserve %.1f m/s, burn %.0f"
              + " s = %.1f %% of a revolution%n",
          reentryPerigee / 1000.0,
          reserve,
          deliveredDeltaV,
          burnSeconds,
          100.0 * burnSeconds / period);
      System.out.printf(
          Locale.ROOT,
          "%-6s %-9s %-9s %-9s | %-12s %-7s %-8s %-9s %-9s %-10s %-7s %s%n",
          "i(°)",
          "hp_imp",
          "hp_fin",
          "alt_end",
          "recipe",
          "steps",
          "wall_ms",
          "t_karman",
          "t_stop",
          "geod_alt_m",
          "lat°",
          "outcome");
      for (double inclinationDeg : INCLINATIONS_DEG) {
        SpacecraftState circular =
            circularState(earth, semiMajor, FastMath.toRadians(inclinationDeg), dry + reserve);
        double impulsivePerigee = impulsivePerigee(circular, deliveredDeltaV, earth);
        SpacecraftState burnEnd = flyRetrogradeBurn(circular, context, engine, burnSeconds);
        double finitePerigee = perigeeAltitude(burnEnd, earth);
        double burnEndAltitude = geodetic(burnEnd, ellipsoid).getAltitude();
        for (Recipe recipe : Recipe.values()) {
          String fall = fall(recipe, burnEnd, context, ellipsoid);
          System.out.printf(
              Locale.ROOT,
              "%-6.1f %-9.1f %-9.1f %-9.1f | %s%n",
              inclinationDeg,
              impulsivePerigee / 1000.0,
              finitePerigee / 1000.0,
              burnEndAltitude / 1000.0,
              fall);
        }
      }
    }
  }

  @Test
  void horizonOfProductionMissions() {
    flyAndReport(
        "LEO 400 km — Falcon Heavy, Kourou, EARTH_OBS_SAT", MissionType.LEO, leoWizardValues());
    flyAndReport("GEO — Falcon Heavy, Kourou, GEO_SAT", MissionType.GEO, geoWizardValues());
  }

  /**
   * MIS-10 / L1 input: how the burn geometry changes the perigee the whole reserve actually
   * reaches, for both LEO-eligible payloads, across the direct-chain altitude range. Every geometry
   * spends the same propellant; only where and how it is burnt differs.
   */
  @Test
  void burnGeometrySweep() {
    GravitationalContext earth = GravitationalContext.earth();
    OneAxisEllipsoid ellipsoid = OrekitService.get().getEarthEllipsoid();
    for (PayloadModel payload : List.of(LEO_PAYLOAD, Payloads.GEO_SAT)) {
      FlightContext context =
          new FlightContext(
              earth, new DragContext(payload.aerodynamics(), AtmosphereModel.NRLMSISE));
      PropulsionSystem engine = payload.propulsion();
      double dry = payload.defaultDryMass();
      double massFlow = engine.thrust() / (engine.isp() * Constants.G0_STANDARD_GRAVITY);
      for (double orbitAltitude : GEOMETRY_ALTITUDES) {
        double semiMajor = earth.equatorialRadius() + orbitAltitude;
        double period = 2.0 * FastMath.PI * FastMath.sqrt(FastMath.pow(semiMajor, 3) / earth.mu());
        for (double target : GEOMETRY_TARGETS) {
          double reserve = PropellantBudget.disposalReserveFor(payload, dry, orbitAltitude, target);
          double burnSeconds = reserve / massFlow;
          System.out.printf(
              Locale.ROOT,
              "%nMIS-10 / L1 geometry — %s, %.0f km circ. (period %.0f s), sized for %.0f km:"
                  + " reserve %.1f kg, burn %.0f s = %.1f %% of a revolution%n",
              payload.id(),
              orbitAltitude / 1000.0,
              period,
              target / 1000.0,
              reserve,
              burnSeconds,
              100.0 * burnSeconds / period);
          SpacecraftState circular =
              circularState(earth, semiMajor, FastMath.toRadians(51.6), dry + reserve);
          for (Geometry geometry : Geometry.values()) {
            SpacecraftState burnEnd;
            try {
              burnEnd = flyGeometry(geometry, circular, context, engine, burnSeconds);
            } catch (IllegalStateException e) {
              System.out.printf(
                  Locale.ROOT,
                  "  %-9s re-entered before its last burn (%s)%n",
                  geometry,
                  e.getMessage());
              continue;
            }
            System.out.printf(
                Locale.ROOT,
                "  %-9s burns end +%6.0f s, mass left %.1f kg, hp %7.1f km, ha %7.1f km | %s%n",
                geometry,
                burnEnd.getDate().durationFrom(circular.getDate()),
                burnEnd.getMass() - dry,
                perigeeAltitude(burnEnd, earth) / 1000.0,
                apogeeAltitude(burnEnd, earth) / 1000.0,
                fall(Recipe.DEBRIS, burnEnd, context, ellipsoid));
          }
        }
      }
    }
  }

  /**
   * MIS-10 / L1 input for the closed-loop burn: tracking retrograde burns, each capped at a
   * fraction of the current revolution and centred on the apogee after the first, repeated until
   * the osculating perigee crosses the sizing target or the reserve is spent.
   */
  @Test
  void arcCapSweep() {
    GravitationalContext earth = GravitationalContext.earth();
    OneAxisEllipsoid ellipsoid = OrekitService.get().getEarthEllipsoid();
    for (PayloadModel payload : List.of(LEO_PAYLOAD, Payloads.GEO_SAT)) {
      FlightContext context =
          new FlightContext(
              earth, new DragContext(payload.aerodynamics(), AtmosphereModel.NRLMSISE));
      PropulsionSystem engine = payload.propulsion();
      double dry = payload.defaultDryMass();
      for (double orbitAltitude : GEOMETRY_ALTITUDES) {
        double semiMajor = earth.equatorialRadius() + orbitAltitude;
        for (double target : GEOMETRY_TARGETS) {
          double reserve = PropellantBudget.disposalReserveFor(payload, dry, orbitAltitude, target);
          System.out.printf(
              Locale.ROOT,
              "%nMIS-10 / L1 arc cap — %s, %.0f km circ., target %.0f km, reserve %.1f kg%n",
              payload.id(),
              orbitAltitude / 1000.0,
              target / 1000.0,
              reserve);
          SpacecraftState circular =
              circularState(earth, semiMajor, FastMath.toRadians(51.6), dry + reserve);
          for (double cap : ARC_CAPS) {
            ClosedLoopOutcome outcome =
                flyClosedLoop(circular, context, engine, dry, cap, target, earth, false);
            System.out.printf(
                Locale.ROOT,
                "  cap %4.0f %% : %3d burn(s) %-21s, burns end +%6.0f s, reserve left %6.1f kg, hp"
                    + " %7.1f km | %s%n",
                100.0 * cap,
                outcome.burns(),
                outcome.reason(),
                outcome.end().getDate().durationFrom(circular.getDate()),
                outcome.end().getMass() - dry,
                perigeeAltitude(outcome.end(), earth) / 1000.0,
                fall(Recipe.DEBRIS, outcome.end(), context, ellipsoid));
          }
        }
      }
    }
  }

  /**
   * MIS-10 / L1 input for the elliptical convention: the reserve the production sizing gives when
   * fed the apogee altitude as if the orbit were circular, against the reserve an exact apogee-burn
   * sizing (same margin, same engine) would carry.
   */
  @Test
  void ellipticSizing() {
    GravitationalContext earth = GravitationalContext.earth();
    double mu = earth.mu();
    double re = earth.equatorialRadius();
    double target = 50_000.0;
    double[][] orbits = {
      {400_000.0, 400_000.0},
      {300_000.0, 800_000.0},
      {200_000.0, 1_000_000.0},
      {200_000.0, 2_000_000.0},
      {1_000_000.0, 2_000_000.0}
    };
    for (PayloadModel payload : List.of(LEO_PAYLOAD, Payloads.GEO_SAT)) {
      double dry = payload.defaultDryMass();
      double ve = payload.propulsion().isp() * Constants.G0_STANDARD_GRAVITY;
      System.out.printf(Locale.ROOT, "%nMIS-10 / L1 elliptic sizing — %s%n", payload.id());
      for (double[] orbit : orbits) {
        double rp = re + orbit[0];
        double ra = re + orbit[1];
        double vApogee = FastMath.sqrt(mu * (2.0 / ra - 2.0 / (rp + ra)));
        double vApogeeTarget = FastMath.sqrt(mu * (2.0 / ra - 2.0 / (re + target + ra)));
        double exactDeltaV = vApogee - vApogeeTarget;
        double exactReserve =
            dry * (FastMath.exp(exactDeltaV / ve) - 1.0) * (1.0 + PropellantBudget.SAFETY_MARGIN);
        double circularAtApogee =
            PropellantBudget.disposalReserveFor(payload, dry, orbit[1], target);
        double circularAtPerigee =
            PropellantBudget.disposalReserveFor(payload, dry, orbit[0], target);
        System.out.printf(
            Locale.ROOT,
            "  %4.0f x %4.0f km: exact apogee burn %6.1f m/s -> %7.1f kg | circular at apogee"
                + " %7.1f kg | circular at perigee %7.1f kg%n",
            orbit[0] / 1000.0,
            orbit[1] / 1000.0,
            exactDeltaV,
            exactReserve,
            circularAtApogee,
            circularAtPerigee);
      }
    }
  }

  /**
   * MIS-10 / L1 check of the elliptical convention: the reserve sized as if circular at the perigee
   * altitude, flown as the closed loop at a 25 % arc cap with every burn — the first included —
   * centred on the next apogee.
   */
  @Test
  void ellipticClosedLoop() {
    GravitationalContext earth = GravitationalContext.earth();
    OneAxisEllipsoid ellipsoid = OrekitService.get().getEarthEllipsoid();
    double target = 50_000.0;
    double[][] orbits = {
      {400_000.0, 400_000.0},
      {200_000.0, 1_000_000.0},
      {200_000.0, 2_000_000.0},
      {1_000_000.0, 2_000_000.0}
    };
    for (PayloadModel payload : List.of(LEO_PAYLOAD, Payloads.GEO_SAT)) {
      FlightContext context =
          new FlightContext(
              earth, new DragContext(payload.aerodynamics(), AtmosphereModel.NRLMSISE));
      double dry = payload.defaultDryMass();
      System.out.printf(Locale.ROOT, "%nMIS-10 / L1 elliptic closed loop — %s%n", payload.id());
      for (double[] orbit : orbits) {
        double rp = earth.equatorialRadius() + orbit[0];
        double ra = earth.equatorialRadius() + orbit[1];
        double reserve = PropellantBudget.disposalReserveFor(payload, dry, orbit[0], target);
        SpacecraftState start =
            new SpacecraftState(
                new KeplerianOrbit(
                    0.5 * (rp + ra),
                    (ra - rp) / (ra + rp),
                    FastMath.toRadians(51.6),
                    0.0,
                    0.0,
                    0.0,
                    PositionAngleType.TRUE,
                    OrekitService.get().gcrf(),
                    epoch,
                    earth.mu()),
                dry + reserve);
        ClosedLoopOutcome outcome =
            flyClosedLoop(start, context, payload.propulsion(), dry, 0.25, target, earth, true);
        System.out.printf(
            Locale.ROOT,
            "  %4.0f x %4.0f km, reserve %6.1f kg: %2d burn(s) %-21s, burns end +%6.0f s, reserve"
                + " left %6.1f kg, hp %6.1f km | %s%n",
            orbit[0] / 1000.0,
            orbit[1] / 1000.0,
            reserve,
            outcome.burns(),
            outcome.reason(),
            outcome.end().getDate().durationFrom(start.getDate()),
            outcome.end().getMass() - dry,
            perigeeAltitude(outcome.end(), earth) / 1000.0,
            fall(Recipe.DEBRIS, outcome.end(), context, ellipsoid));
      }
    }
  }

  /**
   * MIS-10 / L1 closure: the disposal tail on the production path. The same LEO 400 km Falcon Heavy
   * mission is computed by {@code MissionPlanOptimizer.compute()} through the fixed-load planner,
   * first as production composes it today, then with its payload carrying the disposal reserve
   * sized for the tail's target — the launcher re-sized for that heavier payload by the production
   * budget. The tail is then re-planned and re-flown from the horizon the computation left, to time
   * it on its own, count its integrator steps and check it reproduces to the bit.
   */
  @Test
  void closureFlight() {
    MissionSpec.EarthOrbit nominal =
        (MissionSpec.EarthOrbit)
            MissionFactory.specFromWizardValues(leoWizardValues(), MissionType.LEO);
    Spacecraft payload = nominal.configuration().payload();
    LauncherModel launcher = nominal.configuration().launcher();
    double reserve =
        PropellantBudget.disposalReserveFor(
            LEO_PAYLOAD, payload.dryMass(), ORBIT_ALTITUDE, DeorbitTail.REENTRY_PERIGEE_ALTITUDE_M);
    Spacecraft disposable =
        LEO_PAYLOAD.toSpacecraft(payload.dryMass(), payload.propellantLoad(), reserve);
    double[] resized =
        PropellantBudget.loadsForLeo(launcher, disposable, ORBIT_ALTITUDE, nominal.latitude());
    MissionSpec.EarthOrbit disposed =
        withConfiguration(
            nominal,
            new LaunchConfiguration(
                launcher, resized, disposable, nominal.configuration().payloadId()));

    System.out.printf(
        Locale.ROOT,
        "%nMIS-10 / L1 closure — %s, %s, dry %.0f kg, nominal load %.1f kg, reserve %.1f kg"
            + " (sized for %.0f km), atmosphere %s, horizon %s%n",
        launcher.id(),
        LEO_PAYLOAD.id(),
        payload.dryMass(),
        payload.propellantLoad(),
        reserve,
        DeorbitTail.REENTRY_PERIGEE_ALTITUDE_M / 1000.0,
        nominal.atmosphere(),
        nominal.horizon().describe());
    System.out.printf(
        Locale.ROOT,
        "  launcher loads: production %s | re-sized for the reserve %s | budget of the nominal"
            + " payload re-derived here %s%n",
        Arrays.toString(nominal.configuration().propellantLoads()),
        Arrays.toString(resized),
        Arrays.toString(
            PropellantBudget.loadsForLeo(launcher, payload, ORBIT_ALTITUDE, nominal.latitude())));

    MissionPlan before = closureRun("before — no reserve", nominal);
    MissionPlan after = closureRun("after  — with reserve", disposed);
    reportClosureTail(before, after, payload.dryMass());
  }

  private MissionPlan closureRun(String label, MissionSpec spec) {
    Mission mission = MissionComposer.compose(spec, OptimizationType.FAST);
    MissionEntry entry = new MissionEntry(mission);
    long t0 = System.nanoTime();
    MissionPlan plan = new MissionPlanOptimizer(entry, epoch).compute();
    double seconds = (System.nanoTime() - t0) / 1e9;

    MissionComputeResult result = plan.computation();
    List<MissionEphemerisPoint> points = result.ephemeris().allPoints();
    MissionEphemerisPoint horizon = lastPointOf(points, StageNames.TERMINAL_COAST);
    GravitationalContext earth = GravitationalContext.earth();
    System.out.printf(
        Locale.ROOT,
        "%n  [%s] compute() %.1f s, tail on the mission: %s; ephemeris %d points, complete=%s,"
            + " last '%s' at %s%n",
        label,
        seconds,
        result.mission().hasDisposalTail(),
        points.size(),
        result.ephemeris().isComplete(),
        points.getLast().stageName(),
        points.getLast().time());
    System.out.printf(
        Locale.ROOT,
        "    horizon %s (+%.0f s after launch): mass %.1f kg, hp %.1f km, ha %.1f km (spherical);"
            + " achieved orbit %s%n",
        horizon.time(),
        horizon.time().durationFrom(points.getFirst().time()),
        horizon.mass(),
        perigeeAltitude(stateOf(horizon, earth), earth) / 1000.0,
        apogeeAltitude(stateOf(horizon, earth), earth) / 1000.0,
        result.achievedOrbit().formatOsculating());
    return plan;
  }

  private static void reportClosureTail(MissionPlan before, MissionPlan after, double dryMass) {
    GravitationalContext earth = GravitationalContext.earth();
    MissionComputeResult result = after.computation();
    Mission mission = result.mission();
    List<MissionEphemerisPoint> points = result.ephemeris().allPoints();
    int tailPoints = points.size() - indexAfterLast(points, StageNames.TERMINAL_COAST);
    MissionEphemerisPoint last = points.getLast();

    SpacecraftState horizon = mission.getCurrentState();
    long t0 = System.nanoTime();
    DeorbitSequence sequence = mission.getDisposalTail().plan(horizon, mission);
    long planned = System.nanoTime();
    MissionEphemeris tail =
        new MissionEphemerisGenerator().generateChain(mission, sequence.stages(), horizon);
    long flown = System.nanoTime();
    mission.setCurrentState(horizon);

    System.out.printf(
        Locale.ROOT,
        "%n  tail: %d burn(s), ended %s; %d points appended (re-flown: %d); tail ephemeris"
            + " complete=%s; the plan's own points before it: %d vs %d without reserve%n",
        sequence.burns().size(),
        sequence.end(),
        tailPoints,
        tail.size(),
        tail.isComplete(),
        points.size() - tailPoints,
        before.computation().ephemeris().size());
    System.out.printf(
        Locale.ROOT,
        "    cost on its own: planning %.2f s, flight %.2f s%n",
        (planned - t0) / 1e9,
        (flown - planned) / 1e9);
    for (int index = 0; index < sequence.burns().size(); index++) {
      DeorbitSequence.Burn burn = sequence.burns().get(index);
      DeorbitBurnStage stage = (DeorbitBurnStage) sequence.stages().get(2 * index + 1);
      System.out.printf(
          Locale.ROOT,
          "    burn %d: apogee +%.0f s after the horizon, planned %.1f s, flown %.1f s%n",
          index + 1,
          burn.apogee().durationFrom(horizon.getDate()),
          burn.plannedSeconds(),
          stage.durationSeconds());
    }
    SpacecraftState end = stateOf(last, earth);
    GeodeticPoint ground = geodetic(end, OrekitService.get().getEarthEllipsoid());
    System.out.printf(
        Locale.ROOT,
        "    end %s, +%.0f s after the horizon: hp %.2f km (spherical), ha %.1f km, altitude"
            + " %.1f km (geodetic); propellant burnt %.1f kg, left above dry %.1f kg%n",
        last.time(),
        last.time().durationFrom(horizon.getDate()),
        perigeeAltitude(end, earth) / 1000.0,
        apogeeAltitude(end, earth) / 1000.0,
        ground.getAltitude() / 1000.0,
        horizon.getMass() - last.mass(),
        last.mass() - dryMass);
    System.out.printf(
        Locale.ROOT,
        "    re-flown tail vs appended tail: same final point to the bit = %s%n",
        tail.lastPoint().position().equals(last.position())
            && tail.lastPoint().velocity().equals(last.velocity())
            && tail.lastPoint().time().equals(last.time())
            && tail.lastPoint().mass() == last.mass());

    reflyTailCountingSteps(sequence, horizon, mission, last);

    FlightContext context =
        new FlightContext(
            earth, new DragContext(LEO_PAYLOAD.aerodynamics(), AtmosphereModel.NRLMSISE));
    System.out.printf(
        Locale.ROOT,
        "    fall after the tail (debris recipe, not flown by L1): %-12s %-7s %-8s %-9s %-9s"
            + " %-10s %-7s %s%n      %s%n",
        "recipe",
        "steps",
        "wall_ms",
        "t_karman",
        "t_stop",
        "geod_alt_m",
        "lat°",
        "outcome",
        fall(Recipe.DEBRIS, end, context, OrekitService.get().getEarthEllipsoid()));
  }

  /**
   * Re-flies each tail stage the way {@code StageLegRunner} builds it — its flight context and max
   * step, {@code ReentryGuard.arm}, then {@code configure} — to count the integrator steps, and
   * checks the re-flight lands on the appended tail's last point.
   */
  private static void reflyTailCountingSteps(
      DeorbitSequence sequence,
      SpacecraftState horizon,
      Mission mission,
      MissionEphemerisPoint last) {
    SpacecraftState state = horizon;
    long burnSteps = 0L;
    long coastSteps = 0L;
    for (MissionStage stage : sequence.stages()) {
      mission.setCurrentState(state);
      FlightContext context = stage.flightContext(state, mission);
      NumericalPropagator propagator =
          OrekitService.get()
              .createOptimizationPropagator(context, stage.maxStepSeconds(state, mission));
      propagator.setInitialState(ArcTransition.convert(state, context.gravity()));
      ReentryGuard.arm(propagator, stage.getName(), context.gravity());
      stage.configure(propagator, mission);
      long[] steps = {0L};
      propagator.getMultiplexer().add(interpolator -> steps[0]++);
      state = propagator.propagate(stage.getConfiguredEndDate());
      if (stage.isPropulsive()) {
        burnSteps += steps[0];
      } else {
        coastSteps += steps[0];
      }
    }
    mission.setCurrentState(horizon);
    System.out.printf(
        Locale.ROOT,
        "    integrator steps: %d in the burns, %d in the coasts; re-flight ends %.6f m from the"
            + " appended tail's last point%n",
        burnSteps,
        coastSteps,
        Vector3D.distance(state.getPosition(), last.position()));
  }

  private static MissionEphemerisPoint lastPointOf(
      List<MissionEphemerisPoint> points, String stageName) {
    return points.get(indexAfterLast(points, stageName) - 1);
  }

  private static int indexAfterLast(List<MissionEphemerisPoint> points, String stageName) {
    for (int index = points.size() - 1; index >= 0; index--) {
      if (points.get(index).stageName().equals(stageName)) {
        return index + 1;
      }
    }
    throw new IllegalStateException("no point of stage '" + stageName + "'");
  }

  private static MissionSpec.EarthOrbit withConfiguration(
      MissionSpec.EarthOrbit spec, LaunchConfiguration configuration) {
    return new MissionSpec.EarthOrbit(
        spec.name(),
        configuration,
        spec.perigeeAltitude(),
        spec.apogeeAltitude(),
        spec.targetInclination(),
        spec.nodeBranch(),
        spec.targetRaan(),
        spec.siteName(),
        spec.latitude(),
        spec.longitude(),
        spec.altitude(),
        spec.horizon(),
        spec.atmosphere());
  }

  private static final double[] ARC_CAPS = {0.05, 0.10, 0.15, 0.25};

  /** Safety bound on the number of burns of one closed loop. */
  private static final int MAX_BURNS = 200;

  private enum LoopEnd {
    TARGET,
    RESERVE_SPENT,
    FELL_BEFORE_NEXT_BURN,
    MAX_BURNS
  }

  private record ClosedLoopOutcome(SpacecraftState end, int burns, LoopEnd reason) {}

  private static ClosedLoopOutcome flyClosedLoop(
      SpacecraftState initial,
      FlightContext context,
      PropulsionSystem engine,
      double dryMass,
      double arcCap,
      double targetPerigee,
      GravitationalContext earth,
      boolean centreFirstBurn) {
    double massFlow = engine.thrust() / (engine.isp() * Constants.G0_STANDARD_GRAVITY);
    double targetRadius = earth.equatorialRadius() + targetPerigee;
    SpacecraftState state = initial;
    int burns = 0;
    while (burns < MAX_BURNS) {
      double remaining = state.getMass() - dryMass;
      if (remaining < 1.0e-6) {
        return new ClosedLoopOutcome(state, burns, LoopEnd.RESERVE_SPENT);
      }
      double burnSeconds =
          Math.min(arcCap * state.getOrbit().getKeplerianPeriod(), remaining / massFlow);
      AbsoluteDate start = state.getDate().shiftedBy(IGNITION_OFFSET_SECONDS);
      if (burns > 0 || centreFirstBurn) {
        AbsoluteDate apogee;
        try {
          apogee = nextApogee(state, context);
        } catch (IllegalStateException e) {
          return new ClosedLoopOutcome(state, burns, LoopEnd.FELL_BEFORE_NEXT_BURN);
        }
        AbsoluteDate centred = apogee.shiftedBy(-burnSeconds / 2.0);
        start = centred.isAfter(start) ? centred : start;
      }
      double maxStep =
          OrekitService.burnLimitedMaxStep(
              new OrekitService.BurnSpec(engine.thrust(), engine.isp(), state.getMass()));
      NumericalPropagator propagator =
          OrekitService.get().createOptimizationPropagator(context, maxStep);
      propagator.setInitialState(state);
      ReentryGuard.armQuiet(propagator, context.gravity());
      propagator.addForceModel(
          new ConstantThrustManeuver(
              start,
              burnSeconds,
              engine.thrust(),
              engine.isp(),
              new LofOffset(state.getFrame(), LOFType.TNW),
              Vector3D.MINUS_I));
      boolean[] reached = {false};
      propagator.addEventDetector(
          new FunctionalDetector()
              .withFunction(s -> perigeeRadius(s, earth) - targetRadius)
              .withMaxCheck(10.0)
              .withThreshold(0.1)
              .withHandler(
                  (s, detector, increasing) -> {
                    if (increasing) {
                      return Action.CONTINUE;
                    }
                    reached[0] = true;
                    return Action.STOP;
                  }));
      state = propagator.propagate(start.shiftedBy(burnSeconds));
      burns++;
      if (reached[0] || perigeeRadius(state, earth) <= targetRadius) {
        return new ClosedLoopOutcome(state, burns, LoopEnd.TARGET);
      }
    }
    return new ClosedLoopOutcome(state, burns, LoopEnd.MAX_BURNS);
  }

  private static double perigeeRadius(SpacecraftState state, GravitationalContext earth) {
    KeplerianOrbit orbit = keplerian(state, earth);
    return orbit.getA() * (1.0 - orbit.getE());
  }

  /** How the reserve is burnt: all at once or split, tracking the velocity or inertially fixed. */
  private enum Geometry {
    TRACK_1(1, true),
    FIXED_1(1, false),
    TRACK_2(2, true),
    TRACK_3(3, true);

    private final int burns;
    private final boolean tracking;

    Geometry(int burns, boolean tracking) {
      this.burns = burns;
      this.tracking = tracking;
    }
  }

  private static final double[] GEOMETRY_ALTITUDES = {400_000.0, 1_000_000.0, 1_800_000.0};

  private static final double[] GEOMETRY_TARGETS = {50_000.0, 0.0};

  /**
   * Flies the reserve as {@code geometry.burns} equal burns. The first ignites at once — a circular
   * orbit has no apsis to wait for — and each later one is centred on the next apogee, where a
   * retrograde burn lowers the perigee most.
   */
  private static SpacecraftState flyGeometry(
      Geometry geometry,
      SpacecraftState initial,
      FlightContext context,
      PropulsionSystem engine,
      double totalBurnSeconds) {
    double burnSeconds = totalBurnSeconds / geometry.burns;
    SpacecraftState state = initial;
    for (int index = 0; index < geometry.burns; index++) {
      AbsoluteDate start = state.getDate().shiftedBy(IGNITION_OFFSET_SECONDS);
      if (index > 0) {
        AbsoluteDate apogee = nextApogee(state, context);
        AbsoluteDate centred = apogee.shiftedBy(-burnSeconds / 2.0);
        start = centred.isAfter(start) ? centred : start;
      }
      AttitudeProvider attitude;
      Vector3D thrustDirection;
      if (geometry.tracking) {
        attitude = new LofOffset(state.getFrame(), LOFType.TNW);
        thrustDirection = Vector3D.MINUS_I;
      } else {
        Vector3D midVelocity =
            state
                .getOrbit()
                .shiftedBy(start.durationFrom(state.getDate()) + burnSeconds / 2.0)
                .getPVCoordinates()
                .getVelocity();
        attitude =
            new FrameAlignedProvider(
                new Rotation(midVelocity.negate().normalize(), Vector3D.PLUS_I), state.getFrame());
        thrustDirection = Vector3D.PLUS_I;
      }
      state = flyBurn(state, context, engine, start, burnSeconds, attitude, thrustDirection);
    }
    return state;
  }

  private static SpacecraftState flyBurn(
      SpacecraftState initial,
      FlightContext context,
      PropulsionSystem engine,
      AbsoluteDate start,
      double burnSeconds,
      AttitudeProvider attitude,
      Vector3D thrustDirection) {
    double maxStep =
        OrekitService.burnLimitedMaxStep(
            new OrekitService.BurnSpec(engine.thrust(), engine.isp(), initial.getMass()));
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, maxStep);
    propagator.setInitialState(initial);
    ReentryGuard.armQuiet(propagator, context.gravity());
    propagator.addForceModel(
        new ConstantThrustManeuver(
            start, burnSeconds, engine.thrust(), engine.isp(), attitude, thrustDirection));
    return propagator.propagate(start.shiftedBy(burnSeconds));
  }

  private static AbsoluteDate nextApogee(SpacecraftState state, FlightContext context) {
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, OrekitService.COAST_MAX_STEP);
    propagator.setInitialState(state);
    ReentryGuard.armQuiet(propagator, context.gravity());
    AbsoluteDate[] apogee = {null};
    propagator.addEventDetector(
        new ApsideDetector(state.getOrbit())
            .withHandler(
                (s, detector, increasing) -> {
                  if (!increasing && s.getDate().durationFrom(state.getDate()) > 1.0) {
                    apogee[0] = s.getDate();
                    return Action.STOP;
                  }
                  return Action.CONTINUE;
                }));
    propagator.propagate(state.getDate().shiftedBy(1.2 * state.getOrbit().getKeplerianPeriod()));
    if (apogee[0] == null) {
      throw new IllegalStateException("no apogee within 1.2 periods of " + state.getDate());
    }
    return apogee[0];
  }

  private static SpacecraftState circularState(
      GravitationalContext earth, double semiMajor, double inclination, double mass) {
    return new SpacecraftState(
        new KeplerianOrbit(
            semiMajor,
            0.0,
            inclination,
            0.0,
            0.0,
            0.0,
            PositionAngleType.TRUE,
            OrekitService.get().gcrf(),
            epoch,
            earth.mu()),
        mass);
  }

  /** The perigee the whole reserve would reach as one impulsive retrograde burn, from here. */
  private static double impulsivePerigee(
      SpacecraftState state, double deltaV, GravitationalContext earth) {
    PVCoordinates pv = state.getPVCoordinates();
    Vector3D velocity = pv.getVelocity();
    Vector3D braked = velocity.scalarMultiply(1.0 - deltaV / velocity.getNorm());
    return perigeeAltitude(
        new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(pv.getPosition(), braked),
                state.getFrame(),
                state.getDate(),
                earth.mu())),
        earth);
  }

  /**
   * Burns the whole reserve on the payload engine, tracking the anti-velocity direction, with the
   * production propagator sized by the late-ignition invariant.
   */
  private static SpacecraftState flyRetrogradeBurn(
      SpacecraftState initial, FlightContext context, PropulsionSystem engine, double burnSeconds) {
    double maxStep =
        OrekitService.burnLimitedMaxStep(
            new OrekitService.BurnSpec(engine.thrust(), engine.isp(), initial.getMass()));
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, maxStep);
    propagator.setInitialState(initial);
    ReentryGuard.armQuiet(propagator, context.gravity());
    AbsoluteDate ignition = initial.getDate().shiftedBy(IGNITION_OFFSET_SECONDS);
    propagator.addForceModel(
        new ConstantThrustManeuver(
            ignition,
            burnSeconds,
            engine.thrust(),
            engine.isp(),
            new LofOffset(initial.getFrame(), LOFType.TNW),
            Vector3D.MINUS_I));
    return propagator.propagate(ignition.shiftedBy(burnSeconds));
  }

  private static String fall(
      Recipe recipe, SpacecraftState start, FlightContext context, OneAxisEllipsoid ellipsoid) {
    double maxStep = recipe == Recipe.DEBRIS ? DEBRIS_MAX_STEP : OrekitService.COAST_MAX_STEP;
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, maxStep);
    propagator.setInitialState(start);
    if (recipe == Recipe.DEBRIS) {
      propagator.addEventDetector(
          new AltitudeDetector(0.0, ellipsoid)
              .withMaxCheck(30.0)
              .withThreshold(1.0)
              .withHandler((s, detector, increasing) -> Action.STOP));
    } else {
      ReentryGuard.arm(propagator, "MIS-10 L0 probe", context.gravity());
    }
    AtmosphericInterfaceDetector karman =
        new AtmosphericInterfaceDetector(context.gravity().equatorialRadius());
    propagator.addEventDetector(karman);
    long[] steps = {0L};
    propagator
        .getMultiplexer()
        .add(
            interpolator -> {
              steps[0]++;
              if (steps[0] > STEP_CAP) {
                throw new IllegalStateException("step cap " + STEP_CAP + " reached");
              }
            });

    long t0 = System.nanoTime();
    AbsoluteDate horizon = start.getDate().shiftedBy(TAIL_HORIZON_SECONDS);
    String tStop;
    String geodeticAltitude;
    String latitude;
    String outcome;
    try {
      SpacecraftState end = propagator.propagate(horizon);
      GeodeticPoint ground = geodetic(end, ellipsoid);
      double spherical = end.getPosition().getNorm() - context.gravity().equatorialRadius();
      boolean stopped = end.getDate().durationFrom(horizon) < -1.0;
      tStop = String.format(Locale.ROOT, "%.0f", end.getDate().durationFrom(start.getDate()));
      geodeticAltitude = String.format(Locale.ROOT, "%.0f", ground.getAltitude());
      latitude = String.format(Locale.ROOT, "%.1f", FastMath.toDegrees(ground.getLatitude()));
      outcome =
          !stopped
              ? String.format(Locale.ROOT, "horizon, still %.0f km", ground.getAltitude() / 1000.0)
              : String.format(
                  Locale.ROOT,
                  "stopped, spherical %.1f km, %s",
                  spherical / 1000.0,
                  ground.getAltitude() < LANDED_ALTITUDE_METERS ? "LANDED" : "NOT landed");
    } catch (RuntimeException e) {
      tStop = "-";
      geodeticAltitude = "-";
      latitude = "-";
      outcome = "THREW: " + e.getClass().getSimpleName() + " " + e.getMessage();
    }
    long wallMs = (System.nanoTime() - t0) / 1_000_000L;
    String tKarman =
        karman
            .firstDescendingCrossing()
            .map(date -> String.format(Locale.ROOT, "%.0f", date.durationFrom(start.getDate())))
            .orElse("never");
    return String.format(
        Locale.ROOT,
        "%-12s %-7d %-8d %-9s %-9s %-10s %-7s %s",
        recipe,
        steps[0],
        wallMs,
        tKarman,
        tStop,
        geodeticAltitude,
        latitude,
        outcome);
  }

  private void flyAndReport(String label, MissionType type, Map<String, Object> values) {
    MissionSpec spec = MissionFactory.specFromWizardValues(values, type);
    MissionEntry entry = new MissionEntry(spec);
    entry.setOptimizationType(OptimizationType.FAST);

    long t0 = System.nanoTime();
    MissionPlan plan = new MissionPlanOptimizer(entry, epoch).compute();
    double computeSeconds = (System.nanoTime() - t0) / 1e9;

    MissionComputeResult result = plan.computation();
    Mission mission = result.mission();
    MissionStage last = mission.getStages().getLast();
    List<MissionEphemerisPoint> points = result.ephemeris().allPoints();
    int first = points.size() - 1;
    while (first > 0 && points.get(first - 1).stageName().equals(last.getName())) {
      first--;
    }
    MissionEphemerisPoint insertion = points.get(first);
    MissionEphemerisPoint horizon = points.getLast();
    double minAltitude = Double.POSITIVE_INFINITY;
    for (int index = first; index < points.size(); index++) {
      minAltitude = Math.min(minAltitude, points.get(index).altitudeMeters());
    }
    GravitationalContext earth = GravitationalContext.earth();

    System.out.printf(
        Locale.ROOT,
        "%nMIS-10 / L0 (b) — %s: production computation %.1f s (FAST), atmosphere %s%n",
        label,
        computeSeconds,
        spec.atmosphere());
    System.out.printf(
        Locale.ROOT,
        "  horizon %s; last stage '%s' (%s), %d of %d points, ephemeris complete=%s%n",
        mission.getHorizon().describe(),
        last.getName(),
        last.getClass().getSimpleName(),
        points.size() - first,
        points.size(),
        result.ephemeris().isComplete());
    System.out.printf(
        Locale.ROOT,
        "  insertion %s, mass %.1f kg: hp %.1f km, ha %.1f km (spherical)%n",
        insertion.time(),
        insertion.mass(),
        perigeeAltitude(stateOf(insertion, earth), earth) / 1000.0,
        apogeeAltitude(stateOf(insertion, earth), earth) / 1000.0);
    System.out.printf(
        Locale.ROOT,
        "  horizon   %s, +%.0f s: hp %.1f km, ha %.1f km, altitude %.1f km, lowest over the coast"
            + " %.1f km%n",
        horizon.time(),
        horizon.time().durationFrom(insertion.time()),
        perigeeAltitude(stateOf(horizon, earth), earth) / 1000.0,
        apogeeAltitude(stateOf(horizon, earth), earth) / 1000.0,
        horizon.altitudeMeters() / 1000.0,
        minAltitude / 1000.0);

    reflyTrailingCoast(mission, last, insertion, horizon, earth);

    for (DebrisTrack track : result.debris()) {
      MissionEphemerisPoint end = track.ephemeris().lastPoint();
      System.out.printf(
          Locale.ROOT,
          "  debris %s #%d: %d points, +%.0f s, last altitude %.0f m -> %s, complete=%s%n",
          track.role(),
          track.exemplarIndex(),
          track.ephemeris().size(),
          end.time().durationFrom(track.ephemeris().firstPoint().time()),
          end.altitudeMeters(),
          end.altitudeMeters() < LANDED_ALTITUDE_METERS ? "landed" : "in flight",
          track.ephemeris().isComplete());
    }
  }

  /**
   * Re-flies the trailing coast the way {@code StageLegRunner} builds it — the stage's own flight
   * context and max step, {@code ReentryGuard.arm}, then {@code configure} — to count its steps and
   * list what is armed on it. The gap to the recorded horizon point says whether the re-flight is
   * the production one.
   */
  private static void reflyTrailingCoast(
      Mission mission,
      MissionStage last,
      MissionEphemerisPoint insertion,
      MissionEphemerisPoint horizon,
      GravitationalContext earth) {
    SpacecraftState entryState = stateOf(insertion, earth);
    FlightContext context = last.flightContext(entryState, mission);
    double maxStep = last.maxStepSeconds(entryState, mission);
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, maxStep);
    propagator.setInitialState(entryState);
    ReentryGuard.arm(propagator, last.getName(), context.gravity());
    last.configure(propagator, mission);

    int reentryDetectors = 0;
    int interfaceDetectors = 0;
    int otherDetectors = 0;
    for (EventDetector detector : propagator.getEventDetectors()) {
      if (detector instanceof ReentryDetector) {
        reentryDetectors++;
      } else if (detector instanceof AtmosphericInterfaceDetector) {
        interfaceDetectors++;
      } else {
        otherDetectors++;
      }
    }
    long[] steps = {0L};
    propagator.getMultiplexer().add(interpolator -> steps[0]++);

    long t0 = System.nanoTime();
    SpacecraftState end = propagator.propagate(horizon.time());
    long wallMs = (System.nanoTime() - t0) / 1_000_000L;

    System.out.printf(
        Locale.ROOT,
        "  trailing coast re-flown: drag=%s%s, maxStep %.0f s, detectors: %d ReentryDetector, %d"
            + " AtmosphericInterfaceDetector, %d other; %d steps, %d ms; ended %s, gap to the"
            + " recorded horizon point %.3f m%n",
        context.hasDrag(),
        context.hasDrag()
            ? String.format(
                Locale.ROOT,
                " (%.2f m² Cd %.1f, %s)",
                context.drag().aero().crossSection(),
                context.drag().aero().dragCoefficient(),
                context.drag().model())
            : "",
        maxStep,
        reentryDetectors,
        interfaceDetectors,
        otherDetectors,
        steps[0],
        wallMs,
        end.getDate(),
        Vector3D.distance(end.getPosition(), horizon.position()));
  }

  private static SpacecraftState stateOf(MissionEphemerisPoint point, GravitationalContext earth) {
    return new SpacecraftState(
        new CartesianOrbit(
            new PVCoordinates(point.position(), point.velocity()),
            OrekitService.get().gcrf(),
            point.time(),
            earth.mu()),
        point.mass());
  }

  private static double perigeeAltitude(SpacecraftState state, GravitationalContext earth) {
    KeplerianOrbit orbit = keplerian(state, earth);
    return orbit.getA() * (1.0 - orbit.getE()) - earth.equatorialRadius();
  }

  private static double apogeeAltitude(SpacecraftState state, GravitationalContext earth) {
    KeplerianOrbit orbit = keplerian(state, earth);
    return orbit.getA() * (1.0 + orbit.getE()) - earth.equatorialRadius();
  }

  private static KeplerianOrbit keplerian(SpacecraftState state, GravitationalContext earth) {
    return new KeplerianOrbit(
        state.getPVCoordinates(), state.getFrame(), state.getDate(), earth.mu());
  }

  private static GeodeticPoint geodetic(SpacecraftState state, OneAxisEllipsoid ellipsoid) {
    return ellipsoid.transform(
        state.getPosition(ellipsoid.getBodyFrame()), ellipsoid.getBodyFrame(), state.getDate());
  }

  private static Map<String, Object> commonWizardValues(String name, PayloadModel payload) {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", name);
    values.put("LAUNCH_SITE_NAME", "Kourou - French Guiana");
    values.put("LAUNCH_SITE_LAT", 5.23);
    values.put("LAUNCH_SITE_LONG", -52.77);
    values.put("LAUNCH_SITE_ALT", 0.0);
    values.put("LAUNCHER_TYPE", Launchers.FALCON_HEAVY.id());
    values.put("PAYLOAD_TYPE", payload.id());
    values.put("PAYLOAD_MASS", payload.defaultDryMass());
    return values;
  }

  private static Map<String, Object> leoWizardValues() {
    Map<String, Object> values = commonWizardValues("MIS-10 L0 LEO 400", LEO_PAYLOAD);
    values.put("LEO_PERIGEE_ALT", ORBIT_ALTITUDE / 1000.0);
    values.put("LEO_APOGEE_ALT", ORBIT_ALTITUDE / 1000.0);
    return values;
  }

  private static Map<String, Object> geoWizardValues() {
    Map<String, Object> values = commonWizardValues("MIS-10 L0 GEO", Payloads.GEO_SAT);
    values.put("GTO_PARKING_ALT", ORBIT_ALTITUDE / 1000.0);
    return values;
  }
}
