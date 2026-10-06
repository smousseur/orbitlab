package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryDetector;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryGuard;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.stage.TLIBurnStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.window.problem.LunarLaunchWindowProblem;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.control.heuristics.lambert.LambertBoundaryConditions;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.AbstractDetector;
import org.orekit.propagation.events.EventDetectionSettings;
import org.orekit.propagation.events.handlers.ContinueOnEvent;
import org.orekit.propagation.events.handlers.EventHandler;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.propagation.sampling.OrekitFixedStepHandler;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;

/** Probe for the design of MIS-15 / L1. Not committed. */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
class L1DesignProbe {
  private static final Logger logger = LogManager.getLogger(L1DesignProbe.class);
  private static final double TARGET = 100_000.0;
  private static final double PARKING = 400_000.0;

  private Method perileneRadius;
  private Method propagatorOf;
  private Method calibrate;
  private Method aimDirection;

  @Test
  void design() throws Exception {
    OrekitService.get().initialize();
    perileneRadius =
        method("perileneRadius", SpacecraftState.class, AbsoluteDate.class, FlightContext.class);
    propagatorOf = method("propagator", FlightContext.class, SpacecraftState.class);
    calibrate =
        method(
            "calibrateBurn",
            SpacecraftState.class,
            SpacecraftState.class,
            Vector3D.class,
            ActiveStageInfo.class,
            FlightContext.class,
            boolean.class);
    aimDirection =
        method("aimOffsetDirection", SpacecraftState.class, Vector3D.class, AbsoluteDate.class);

    java.util.Set<String> seen = new java.util.HashSet<>();
    for (String line : Files.readAllLines(Path.of("build/probe-tli-states.txt"))) {
      String[] f = line.trim().split("\\s+");
      if (!seen.add(f[0] + " " + f[2])) {
        continue;
      }
      SpacecraftState ignition =
          state(
              new AbsoluteDate(f[2], TimeScalesFactory.getUTC()),
              new Vector3D(d(f[3]), d(f[4]), d(f[5])),
              new Vector3D(d(f[6]), d(f[7]), d(f[8])),
              d(f[9]));
      flownCase(f[0], ignition);
    }

    Counters total = new Counters();
    for (String site : new String[] {"canaveral", "kourou"}) {
      sweep(site, total);
    }
    logger.info("L1 SWEEP TOTAL {}", total);
  }

  @Test
  void confirmMass() {
    OrekitService.get().initialize();
    for (String site : new String[] {"canaveral", "kourou"}) {
      MissionSpec.LunarOrbit spec = spec(site);
      Mission mission = new MissionEntry(spec).mission();
      double massAtInjection =
          PropellantBudget.loadsForLunar(
                  spec.configuration().launcher(),
                  spec.configuration().payload(),
                  PARKING,
                  spec.latitude(),
                  FastMath.PI / 2)
              .massAtInjection();
      ActiveStageInfo active = mission.getVehicle().resolveActiveStage(massAtInjection);
      double ve = active.propulsion().isp() * Constants.G0_STANDARD_GRAVITY;
      double fuel = active.remainingFuel(massAtInjection);
      logger.info(
          String.format(
              Locale.ROOT,
              "L1 CONFIRM %s budget massAtInjection %.1f kg, floor %.1f kg, fuel %.1f kg,"
                  + " available dv %.1f m/s (Isp %.1f s)",
              site,
              massAtInjection,
              active.depletionFloor(),
              fuel,
              ve * Math.log(massAtInjection / (massAtInjection - fuel)),
              active.propulsion().isp()));
    }
  }

  @Test
  void nrlmsiseFailure() throws Exception {
    OrekitService.get().initialize();
    propagatorOf = method("propagator", FlightContext.class, SpacecraftState.class);
    calibrate =
        method(
            "calibrateBurn",
            SpacecraftState.class,
            SpacecraftState.class,
            Vector3D.class,
            ActiveStageInfo.class,
            FlightContext.class,
            boolean.class);
    aimDirection =
        method("aimOffsetDirection", SpacecraftState.class, Vector3D.class, AbsoluteDate.class);
    MissionSpec.LunarOrbit spec = spec("kourou");
    Mission mission = new MissionEntry(spec).mission();
    MissionStage tli =
        mission.getStages().stream().filter(s -> s instanceof TLIBurnStage).findFirst().get();
    double massAtInjection =
        PropellantBudget.loadsForLunar(
                spec.configuration().launcher(),
                spec.configuration().payload(),
                PARKING,
                spec.latitude(),
                FastMath.PI / 2)
            .massAtInjection();
    LunarLaunchWindowProblem problem =
        new LunarLaunchWindowProblem(
            spec.latitude(),
            spec.longitude(),
            spec.altitude(),
            PARKING,
            TARGET,
            spec.configuration().toVehicleStack(),
            massAtInjection);
    ActiveStageInfo active = mission.getVehicle().resolveActiveStage(massAtInjection);
    SpacecraftState parking =
        problem
            .injectionAt(new AbsoluteDate("2026-10-11T00:00:00.000Z", TimeScalesFactory.getUTC()))
            .state();
    FlightContext context = tli.flightContext(parking, mission);
    double lead =
        TranslunarInjectionPlan.ignitionLead(
            parking, TranslunarInjectionPlan.departureFrom(parking), active);
    NumericalPropagator backwards = OrekitService.get().createOptimizationPropagator(context, 30.0);
    backwards.setInitialState(parking);
    SpacecraftState ignition = backwards.propagate(parking.getDate().shiftedBy(-lead));
    AbsoluteDate arrival =
        parking.getDate().shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS);
    Vector3D moonAtArrival = moon(arrival, parking.getFrame());
    Vector3D offsetDirection =
        (Vector3D) aimDirection.invoke(null, parking, moonAtArrival, arrival);
    double offset = GravitationalContext.moon().shape().getEquatorialRadius() + TARGET;
    Vector3D deltaV =
        TranslunarInjectionPlan.keplerianSeedVelocity(
                parking,
                TranslunarInjectionPlan.boundaryConditions(
                    parking, arrival, moonAtArrival.add(offsetDirection.scalarMultiply(offset))))
            .subtract(parking.getPVCoordinates().getVelocity());
    SpacecraftState injected =
        (SpacecraftState)
            accessor(
                calibrate.invoke(null, ignition, parking, deltaV, active, context, false),
                "endState");
    double re = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
    logger.info(
        String.format(
            Locale.ROOT,
            "L1 NRL injected: r-Re %.1f km, vr %.1f m/s, |v| %.1f m/s, perigee r-Re %.1f km",
            (injected.getPosition().getNorm() - re) / 1000.0,
            injected
                .getPVCoordinates()
                .getVelocity()
                .dotProduct(injected.getPosition().normalize()),
            injected.getPVCoordinates().getVelocity().getNorm(),
            (injected.getOrbit().getA() * (1.0 - injected.getOrbit().getE()) - re) / 1000.0));
    NumericalPropagator propagator =
        (NumericalPropagator) propagatorOf.invoke(null, context, injected);
    ReentryGuard.armQuiet(propagator, context.gravity());
    double[] last = new double[4];
    propagator
        .getMultiplexer()
        .add(
            interpolator -> {
              SpacecraftState s = interpolator.getCurrentState();
              last[0] = s.getDate().durationFrom(injected.getDate());
              last[1] = (s.getPosition().getNorm() - re) / 1000.0;
              last[2] = s.getPVCoordinates().getVelocity().dotProduct(s.getPosition().normalize());
              last[3] =
                  interpolator
                      .getCurrentState()
                      .getDate()
                      .durationFrom(interpolator.getPreviousState().getDate());
            });
    try {
      propagator.propagate(injected.getDate().shiftedBy(5 * 86_400.0));
      logger.info("L1 NRL no failure");
    } catch (RuntimeException e) {
      logger.info(
          String.format(
              Locale.ROOT,
              "L1 NRL failure %s after last accepted step: t+%.1f s, r-Re %.2f km, vr %.1f m/s,"
                  + " step %.3f s",
              e.getMessage(),
              last[0],
              last[1],
              last[2],
              last[3]));
    }
  }

  @Test
  void windows() throws Exception {
    OrekitService.get().initialize();
    perileneRadius =
        method("perileneRadius", SpacecraftState.class, AbsoluteDate.class, FlightContext.class);
    propagatorOf = method("propagator", FlightContext.class, SpacecraftState.class);
    calibrate =
        method(
            "calibrateBurn",
            SpacecraftState.class,
            SpacecraftState.class,
            Vector3D.class,
            ActiveStageInfo.class,
            FlightContext.class,
            boolean.class);
    aimDirection =
        method("aimOffsetDirection", SpacecraftState.class, Vector3D.class, AbsoluteDate.class);
    String[] epochs = {
      "2026-10-06T10:02:16.000Z",
      "2026-10-07T01:13:36.000Z",
      "2026-10-07T09:59:27.000Z",
      "2026-10-08T02:52:04.000Z",
      "2026-10-08T09:57:01.000Z",
      "2026-10-09T04:33:44.000Z"
    };
    MissionSpec.LunarOrbit spec = spec("canaveral");
    Mission mission = new MissionEntry(spec).mission();
    MissionStage tli =
        mission.getStages().stream().filter(s -> s instanceof TLIBurnStage).findFirst().get();
    double massAtInjection =
        PropellantBudget.loadsForLunar(
                spec.configuration().launcher(),
                spec.configuration().payload(),
                PARKING,
                spec.latitude(),
                FastMath.PI / 2)
            .massAtInjection();
    LunarLaunchWindowProblem problem =
        new LunarLaunchWindowProblem(
            spec.latitude(),
            spec.longitude(),
            spec.altitude(),
            PARKING,
            TARGET,
            spec.configuration().toVehicleStack(),
            massAtInjection);
    ActiveStageInfo active = mission.getVehicle().resolveActiveStage(massAtInjection);
    Counters counters = new Counters();
    for (String iso : epochs) {
      SpacecraftState parking =
          problem.injectionAt(new AbsoluteDate(iso, TimeScalesFactory.getUTC())).state();
      FlightContext context = tli.flightContext(parking, mission);
      double lead =
          TranslunarInjectionPlan.ignitionLead(
              parking, TranslunarInjectionPlan.departureFrom(parking), active);
      NumericalPropagator backwards =
          OrekitService.get().createOptimizationPropagator(context, 30.0);
      backwards.setInitialState(parking);
      SpacecraftState ignition = backwards.propagate(parking.getDate().shiftedBy(-lead));
      attempts("window@" + iso, ignition, parking, active, context, counters, true);
    }
    logger.info("L1 WINDOWS TOTAL {}", counters);
  }

  /** Decomposes the cost of the perilune-search guards on candidates that do not stop. */
  @Test
  void guardCost() throws Exception {
    OrekitService.get().initialize();
    propagatorOf = method("propagator", FlightContext.class, SpacecraftState.class);
    calibrate =
        method(
            "calibrateBurn",
            SpacecraftState.class,
            SpacecraftState.class,
            Vector3D.class,
            ActiveStageInfo.class,
            FlightContext.class,
            boolean.class);
    aimDirection =
        method("aimOffsetDirection", SpacecraftState.class, Vector3D.class, AbsoluteDate.class);
    MissionSpec.LunarOrbit spec = spec("canaveral");
    Mission mission = new MissionEntry(spec).mission();
    MissionStage tli =
        mission.getStages().stream().filter(s -> s instanceof TLIBurnStage).findFirst().get();
    ActiveStageInfo active = mission.getVehicle().resolveActiveStage(budgetMass(spec));
    SpacecraftState parking =
        problem(spec)
            .injectionAt(new AbsoluteDate("2026-10-06T10:02:16.000Z", TimeScalesFactory.getUTC()))
            .state();
    FlightContext context = tli.flightContext(parking, mission);
    SpacecraftState ignition = ignitionFor(parking, active, context);
    AbsoluteDate arrival =
        parking.getDate().shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS);
    double targetRadius = GravitationalContext.moon().shape().getEquatorialRadius() + TARGET;
    SpacecraftState[] candidates = new SpacecraftState[4];
    for (int k = 0; k < candidates.length; k++) {
      candidates[k] =
          injected(ignition, parking, targetRadius * FastMath.pow(2.0, k), active, context);
    }
    String[] labels = {
      "none", "earth", "earth+moon10", "production", "earth+moonAdaptive", "allAdaptiveStopping"
    };
    for (int round = 0; round < 3; round++) {
      double[] seconds = new double[labels.length];
      int mismatches = 0;
      for (SpacecraftState c : candidates) {
        double[] readings = new double[labels.length];
        for (int v = 0; v < labels.length; v++) {
          long start = System.nanoTime();
          readings[v] =
              v == 3
                  ? TranslunarInjectionPlan.perileneRadius(c, arrival, context)
                  : v == 5 ? adaptive(c, arrival, context) : variant(c, arrival, context, v);
          seconds[v] += (System.nanoTime() - start) / 1.0e9;
          if (v == 5
              && Double.doubleToLongBits(readings[5]) != Double.doubleToLongBits(readings[3])) {
            logger.info(
                "L1 COST adaptive differs from production: {} vs {}", readings[5], readings[3]);
          }
          if (Double.doubleToLongBits(readings[v]) != Double.doubleToLongBits(readings[0])) {
            mismatches++;
            if (round == 0) {
              logger.info(
                  "L1 COST mismatch {} reading {} vs none {}", labels[v], readings[v], readings[0]);
            }
          }
        }
      }
      StringBuilder line = new StringBuilder();
      for (int v = 0; v < labels.length; v++) {
        line.append(String.format(Locale.ROOT, " %s %.3f s |", labels[v], seconds[v]));
      }
      logger.info("L1 COST round {} ({} candidates):{} mismatches {}", round, 4, line, mismatches);
    }
  }

  private double variant(
      SpacecraftState injected, AbsoluteDate arrival, FlightContext context, int v)
      throws Exception {
    double searchEnd = arrival.durationFrom(injected.getDate()) + 0.5 * 86_400.0;
    NumericalPropagator propagator =
        (NumericalPropagator) propagatorOf.invoke(null, context, injected);
    Tracker tracker = new Tracker(injected.getFrame());
    propagator.getMultiplexer().add(60.0, tracker);
    if (v >= 1) {
      ReentryGuard.armQuiet(propagator, context.gravity());
    }
    double lunarRadius = GravitationalContext.moon().shape().getEquatorialRadius();
    if (v == 2) {
      propagator.addEventDetector(new LunarSurfaceDetector(lunarRadius, injected.getFrame()));
    }
    if (v == 4) {
      propagator.addEventDetector(
          new LunarSurfaceDetector(lunarRadius, injected.getFrame())
              .withMaxCheck(
                  (s, forward) ->
                      FastMath.max(
                          10.0,
                          (s.getPosition().subtract(moon(s.getDate(), s.getFrame())).getNorm()
                                  - lunarRadius)
                              / 5_000.0)));
    }
    propagator.propagate(injected.getDate().shiftedBy(searchEnd));
    return tracker.refinedMinimum();
  }

  /**
   * The production guards with an adaptive check interval: the distance to each threshold divided
   * by a speed no translunar trajectory reaches, floored at the production 10 s.
   */
  private double adaptive(SpacecraftState injected, AbsoluteDate arrival, FlightContext context)
      throws Exception {
    double bound = 15_000.0;
    double searchEnd = arrival.durationFrom(injected.getDate()) + 0.5 * 86_400.0;
    AbsoluteDate end = injected.getDate().shiftedBy(searchEnd);
    NumericalPropagator propagator =
        (NumericalPropagator) propagatorOf.invoke(null, context, injected);
    Tracker tracker = new Tracker(injected.getFrame());
    propagator.getMultiplexer().add(60.0, tracker);
    double re = context.gravity().equatorialRadius();
    propagator.addEventDetector(
        new ReentryDetector(re, ReentryGuard.SUBSURFACE_FLOOR)
            .withMaxCheck(
                (s, forward) ->
                    FastMath.max(
                        10.0,
                        (s.getPosition().getNorm() - re - ReentryGuard.SUBSURFACE_FLOOR) / bound))
            .withHandler((s, d, increasing) -> Action.STOP));
    if (context.drag() != null) {
      propagator.addEventDetector(
          new ReentryDetector(re, ReentryGuard.DRAG_REENTRY_FLOOR)
              .withMaxCheck(
                  (s, forward) ->
                      FastMath.max(
                          10.0,
                          (s.getPosition().getNorm() - re - ReentryGuard.DRAG_REENTRY_FLOOR)
                              / bound))
              .withHandler(
                  (s, d, increasing) ->
                      s.getPVCoordinates().getVelocity().dotProduct(s.getPosition()) < 0.0
                          ? Action.STOP
                          : Action.CONTINUE));
    }
    double lunarRadius = GravitationalContext.moon().shape().getEquatorialRadius();
    propagator.addEventDetector(
        new LunarSurfaceDetector(lunarRadius, injected.getFrame())
            .withMaxCheck(
                (s, forward) ->
                    FastMath.max(
                        10.0,
                        (s.getPosition().subtract(moon(s.getDate(), s.getFrame())).getNorm()
                                - lunarRadius)
                            / bound))
            .withHandler((s, d, increasing) -> increasing ? Action.CONTINUE : Action.STOP));
    SpacecraftState reached = propagator.propagate(end);
    if (reached.getDate().compareTo(end) >= 0) {
      return tracker.refinedMinimum();
    }
    return FastMath.min(
        tracker.refinedMinimum(),
        reached.getPosition().subtract(moon(reached.getDate(), reached.getFrame())).getNorm());
  }

  /** Captures, in full precision, the post-burn states the L1 tests are pinned on. */
  @Test
  void fixtures() throws Exception {
    OrekitService.get().initialize();
    propagatorOf = method("propagator", FlightContext.class, SpacecraftState.class);
    calibrate =
        method(
            "calibrateBurn",
            SpacecraftState.class,
            SpacecraftState.class,
            Vector3D.class,
            ActiveStageInfo.class,
            FlightContext.class,
            boolean.class);
    aimDirection =
        method("aimOffsetDirection", SpacecraftState.class, Vector3D.class, AbsoluteDate.class);
    double targetRadius = GravitationalContext.moon().shape().getEquatorialRadius() + TARGET;

    SpacecraftState crashIgnition =
        state(
            new AbsoluteDate("2026-10-08T04:42:36.932610764234190952Z", TimeScalesFactory.getUTC()),
            new Vector3D(4973611.868168, 4314726.383995, 1608361.798281),
            new Vector3D(-3976.080372633, 5753.619853080, -3147.166622910),
            20220.267602);
    Mission canaveral = new MissionEntry(spec("canaveral")).mission();
    MissionStage canaveralTli =
        canaveral.getStages().stream().filter(s -> s instanceof TLIBurnStage).findFirst().get();
    FlightContext canaveralContext = canaveralTli.flightContext(crashIgnition, canaveral);
    NumericalPropagator ballistic =
        OrekitService.get()
            .createOptimizationPropagator(
                canaveralContext, canaveralTli.maxStepSeconds(crashIgnition, canaveral));
    ballistic.setInitialState(crashIgnition);
    ReentryGuard.armQuiet(ballistic, canaveralContext.gravity());
    SpacecraftState crashParking =
        ballistic.propagate(TranslunarInjectionPlan.departureFrom(crashIgnition).injectionDate());
    capture(
        "crash-candidate",
        crashIgnition,
        crashParking,
        targetRadius * 256.0,
        canaveral.getVehicle().resolveActiveStage(crashIgnition.getMass()),
        canaveralContext);

    MissionSpec.LunarOrbit kourouSpec = spec("kourou");
    Mission kourou = new MissionEntry(kourouSpec).mission();
    MissionStage kourouTli =
        kourou.getStages().stream().filter(s -> s instanceof TLIBurnStage).findFirst().get();
    LunarLaunchWindowProblem kourouProblem = problem(kourouSpec);
    ActiveStageInfo kourouActive = kourou.getVehicle().resolveActiveStage(budgetMass(kourouSpec));
    SpacecraftState nrlParking =
        kourouProblem
            .injectionAt(new AbsoluteDate("2026-10-11T00:00:00.000Z", TimeScalesFactory.getUTC()))
            .state();
    FlightContext kourouContext = kourouTli.flightContext(nrlParking, kourou);
    capture(
        "nrlmsise-candidate",
        ignitionFor(nrlParking, kourouActive, kourouContext),
        nrlParking,
        targetRadius,
        kourouActive,
        kourouContext);

    MissionSpec.LunarOrbit canaveralSpec = spec("canaveral");
    LunarLaunchWindowProblem canaveralProblem = problem(canaveralSpec);
    ActiveStageInfo canaveralActive =
        canaveral.getVehicle().resolveActiveStage(budgetMass(canaveralSpec));
    for (String iso : new String[] {"2026-10-06T10:02:16.000Z", "2026-10-07T01:13:36.000Z"}) {
      SpacecraftState parking =
          canaveralProblem.injectionAt(new AbsoluteDate(iso, TimeScalesFactory.getUTC())).state();
      FlightContext context = canaveralTli.flightContext(parking, canaveral);
      SpacecraftState ignition = ignitionFor(parking, canaveralActive, context);
      AbsoluteDate arrival =
          parking.getDate().shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS);
      for (int k = -8; k <= 0; k++) {
        SpacecraftState injected =
            injected(
                ignition, parking, targetRadius * FastMath.pow(2.0, k), canaveralActive, context);
        Guarded guarded = guarded(injected, arrival, context);
        if ("moon-surface".equals(guarded.stop)) {
          logger.info(
              String.format(
                  Locale.ROOT,
                  "L1 FIXTURE lunar-impact %s k=%d guarded reading %s (lunar radius %s)",
                  iso,
                  k,
                  guarded.reading,
                  GravitationalContext.moon().shape().getEquatorialRadius()));
          logFixture("lunar-impact", injected, arrival);
          return;
        }
      }
    }
  }

  private void capture(
      String label,
      SpacecraftState ignition,
      SpacecraftState parking,
      double offset,
      ActiveStageInfo active,
      FlightContext context)
      throws Exception {
    SpacecraftState injected = injected(ignition, parking, offset, active, context);
    AbsoluteDate arrival =
        parking.getDate().shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS);
    Guarded guarded = guarded(injected, arrival, context);
    logger.info(
        "L1 FIXTURE {} guarded stop {} reading {} error {}",
        label,
        guarded.stop,
        guarded.reading,
        guarded.error);
    logFixture(label, injected, arrival);
  }

  private SpacecraftState injected(
      SpacecraftState ignition,
      SpacecraftState parking,
      double offset,
      ActiveStageInfo active,
      FlightContext context)
      throws Exception {
    AbsoluteDate arrival =
        parking.getDate().shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS);
    Vector3D moonAtArrival = moon(arrival, parking.getFrame());
    Vector3D offsetDirection =
        (Vector3D) aimDirection.invoke(null, parking, moonAtArrival, arrival);
    Vector3D deltaV =
        TranslunarInjectionPlan.keplerianSeedVelocity(
                parking,
                TranslunarInjectionPlan.boundaryConditions(
                    parking, arrival, moonAtArrival.add(offsetDirection.scalarMultiply(offset))))
            .subtract(parking.getPVCoordinates().getVelocity());
    return (SpacecraftState)
        accessor(
            calibrate.invoke(null, ignition, parking, deltaV, active, context, false), "endState");
  }

  private static SpacecraftState ignitionFor(
      SpacecraftState parking, ActiveStageInfo active, FlightContext context) {
    double lead =
        TranslunarInjectionPlan.ignitionLead(
            parking, TranslunarInjectionPlan.departureFrom(parking), active);
    NumericalPropagator backwards = OrekitService.get().createOptimizationPropagator(context, 30.0);
    backwards.setInitialState(parking);
    return backwards.propagate(parking.getDate().shiftedBy(-lead));
  }

  private static double budgetMass(MissionSpec.LunarOrbit spec) {
    return PropellantBudget.loadsForLunar(
            spec.configuration().launcher(),
            spec.configuration().payload(),
            PARKING,
            spec.latitude(),
            FastMath.PI / 2)
        .massAtInjection();
  }

  private static LunarLaunchWindowProblem problem(MissionSpec.LunarOrbit spec) {
    return new LunarLaunchWindowProblem(
        spec.latitude(),
        spec.longitude(),
        spec.altitude(),
        PARKING,
        TARGET,
        spec.configuration().toVehicleStack(),
        budgetMass(spec));
  }

  private static void logFixture(String label, SpacecraftState s, AbsoluteDate arrival) {
    Vector3D p = s.getPosition();
    Vector3D v = s.getPVCoordinates().getVelocity();
    logger.info(
        "L1 FIXTURE {} state date {} pos {} {} {} vel {} {} {} mass {} frame {} arrival {}",
        label,
        s.getDate(),
        p.getX(),
        p.getY(),
        p.getZ(),
        v.getX(),
        v.getY(),
        v.getZ(),
        s.getMass(),
        s.getFrame().getName(),
        arrival);
  }

  private void flownCase(String site, SpacecraftState ignition) throws Exception {
    Mission mission = new MissionEntry(spec(site)).mission();
    MissionStage tli =
        mission.getStages().stream().filter(s -> s instanceof TLIBurnStage).findFirst().get();
    FlightContext context = tli.flightContext(ignition, mission);
    TranslunarInjectionPlan.Departure departure = TranslunarInjectionPlan.departureFrom(ignition);
    NumericalPropagator ballistic =
        OrekitService.get()
            .createOptimizationPropagator(context, tli.maxStepSeconds(ignition, mission));
    ballistic.setInitialState(ignition);
    ReentryGuard.armQuiet(ballistic, context.gravity());
    SpacecraftState atInjection = ballistic.propagate(departure.injectionDate());
    ActiveStageInfo active = mission.getVehicle().resolveActiveStage(ignition.getMass());
    logger.info(
        "L1 FLOWN {} depletionFloor {} remainingFuel {}",
        site,
        String.format(Locale.ROOT, "%.9f", active.depletionFloor()),
        String.format(Locale.ROOT, "%.9f", active.remainingFuel(ignition.getMass())));
    Counters counters = new Counters();
    attempts(site + "-flown", ignition, atInjection, active, context, counters, true);
    logger.info("L1 FLOWN {} {}", site, counters);
  }

  private void sweep(String site, Counters total) throws Exception {
    MissionSpec.LunarOrbit spec = spec(site);
    Mission mission = new MissionEntry(spec).mission();
    MissionStage tli =
        mission.getStages().stream().filter(s -> s instanceof TLIBurnStage).findFirst().get();
    double latitude = spec.latitude();
    double massAtInjection =
        PropellantBudget.loadsForLunar(
                spec.configuration().launcher(),
                spec.configuration().payload(),
                PARKING,
                latitude,
                FastMath.PI / 2)
            .massAtInjection();
    LunarLaunchWindowProblem problem =
        new LunarLaunchWindowProblem(
            latitude,
            spec.longitude(),
            spec.altitude(),
            PARKING,
            TARGET,
            spec.configuration().toVehicleStack(),
            massAtInjection);
    ActiveStageInfo active = mission.getVehicle().resolveActiveStage(massAtInjection);
    AbsoluteDate start = new AbsoluteDate(2026, 10, 6, 0, 0, 0.0, TimeScalesFactory.getUTC());
    Counters counters = new Counters();
    for (int k = 0; k < 14; k++) {
      AbsoluteDate epoch = start.shiftedBy(k * 12 * 3_600.0);
      SpacecraftState parking = problem.injectionAt(epoch).state();
      FlightContext context = tli.flightContext(parking, mission);
      double lead =
          TranslunarInjectionPlan.ignitionLead(
              parking, TranslunarInjectionPlan.departureFrom(parking), active);
      NumericalPropagator backwards =
          OrekitService.get().createOptimizationPropagator(context, 30.0);
      backwards.setInitialState(parking);
      SpacecraftState ignition = backwards.propagate(parking.getDate().shiftedBy(-lead));
      attempts(site + "@" + epoch, ignition, parking, active, context, counters, false);
    }
    logger.info("L1 SWEEP {} {}", site, counters);
    total.add(counters);
  }

  private void attempts(
      String label,
      SpacecraftState ignition,
      SpacecraftState parking,
      ActiveStageInfo active,
      FlightContext context,
      Counters counters,
      boolean verbose)
      throws Exception {
    AbsoluteDate arrival =
        parking.getDate().shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS);
    Vector3D moonAtArrival = moon(arrival, parking.getFrame());
    Vector3D offsetDirection =
        (Vector3D) aimDirection.invoke(null, parking, moonAtArrival, arrival);
    double lunarRadius = GravitationalContext.moon().shape().getEquatorialRadius();
    double targetRadius = lunarRadius + TARGET;
    for (int k = -8; k <= 8; k++) {
      double offset = targetRadius * FastMath.pow(2.0, k);
      Vector3D aimPoint = moonAtArrival.add(offsetDirection.scalarMultiply(offset));
      LambertBoundaryConditions conditions =
          TranslunarInjectionPlan.boundaryConditions(parking, arrival, aimPoint);
      Vector3D deltaV =
          TranslunarInjectionPlan.keplerianSeedVelocity(parking, conditions)
              .subtract(parking.getPVCoordinates().getVelocity());
      Object calibrated = calibrate.invoke(null, ignition, parking, deltaV, active, context, false);
      SpacecraftState injected = (SpacecraftState) accessor(calibrated, "endState");
      double endMinusFloor = injected.getMass() - active.depletionFloor();
      if (endMinusFloor <= 1.0e-3) {
        counters.saturated++;
        if (endMinusFloor < 0.0) {
          counters.belowFloor++;
        }
      }

      long guardedStart = System.nanoTime();
      Guarded guarded = guarded(injected, arrival, context);
      double guardedSeconds = (System.nanoTime() - guardedStart) / 1.0e9;
      counters.maxGuardedSeconds = FastMath.max(counters.maxGuardedSeconds, guardedSeconds);
      String prod;
      double prodReading = Double.NaN;
      long prodStart = System.nanoTime();
      try {
        prodReading = TranslunarInjectionPlan.perileneRadius(injected, arrival, context);
        prod = "ok";
      } catch (RuntimeException e) {
        prod = e.getClass().getSimpleName();
        counters.prodThrew++;
      }
      double prodSeconds = (System.nanoTime() - prodStart) / 1.0e9;
      counters.maxProdSeconds = FastMath.max(counters.maxProdSeconds, prodSeconds);
      counters.attempts++;
      if (guarded.error != null) {
        counters.guardedThrew++;
        if (!Double.isNaN(prodReading) && prodReading > targetRadius) {
          counters.thrownReadAbove++;
        }
      }
      if (guarded.stop != null) {
        counters.count(guarded.stop);
        if ("moon-surface".equals(guarded.stop)) {
          counters.maxMoonStopSpeed = FastMath.max(counters.maxMoonStopSpeed, guarded.stopSpeed);
        } else {
          counters.maxEarthStopSpeed = FastMath.max(counters.maxEarthStopSpeed, guarded.stopSpeed);
        }
        if (Double.isNaN(prodReading)
            || prodReading < targetRadius != guarded.reading < targetRadius) {
          counters.signFlips++;
        }
      } else if (guarded.error == null && !Double.isNaN(prodReading)) {
        long noneStart = System.nanoTime();
        double unguarded = variant(injected, arrival, context, 0);
        counters.noneSeconds += (System.nanoTime() - noneStart) / 1.0e9;
        counters.prodSecondsOnFree += prodSeconds;
        if (Double.doubleToLongBits(prodReading) == Double.doubleToLongBits(unguarded)) {
          counters.bitIdentical++;
        } else {
          counters.differs++;
          logger.info("L1 DIFF {} k={} prod {} unguarded {}", label, k, prodReading, unguarded);
        }
      }
      if (verbose || guarded.stop != null || guarded.error != null || !"ok".equals(prod)) {
        logger.info(
            String.format(
                Locale.ROOT,
                "L1 %s k=%+d offset %.0f km: impulsive %.0f, end-floor %.6e kg, prod %s %s,"
                    + " guarded %s reading %.1f km (target %.1f)%s",
                label,
                k,
                offset / 1000.0,
                deltaV.getNorm(),
                endMinusFloor,
                prod,
                Double.isNaN(prodReading)
                    ? "-"
                    : String.format(Locale.ROOT, "%.1f km", prodReading / 1000.0),
                guarded.stop == null ? "no-stop" : guarded.stop,
                guarded.reading / 1000.0,
                targetRadius / 1000.0,
                guarded.error == null ? "" : " ERROR " + guarded.error));
      }
    }
  }

  private Guarded guarded(SpacecraftState injected, AbsoluteDate arrival, FlightContext context)
      throws Exception {
    double searchEnd = arrival.durationFrom(injected.getDate()) + 0.5 * 86_400.0;
    AbsoluteDate end = injected.getDate().shiftedBy(searchEnd);
    NumericalPropagator propagator =
        (NumericalPropagator) propagatorOf.invoke(null, context, injected);
    Tracker tracker = new Tracker(injected.getFrame());
    propagator.getMultiplexer().add(60.0, tracker);
    ReentryGuard.armQuiet(propagator, context.gravity());
    String[] stoppedBy = new String[1];
    double lunarRadius = GravitationalContext.moon().shape().getEquatorialRadius();
    propagator.addEventDetector(
        new LunarSurfaceDetector(lunarRadius, injected.getFrame())
            .withHandler(
                (s, detector, increasing) -> {
                  if (!increasing) {
                    stoppedBy[0] = "moon-surface";
                    return Action.STOP;
                  }
                  return Action.CONTINUE;
                }));
    try {
      SpacecraftState last = propagator.propagate(end);
      double reading = tracker.refinedMinimum();
      String stop = null;
      double speed = Double.NaN;
      if (last.getDate().durationFrom(end) < -1.0e-6) {
        stop = stoppedBy[0] == null ? "earth-reentry" : stoppedBy[0];
        reading =
            FastMath.min(
                reading,
                last.getPosition().subtract(moon(last.getDate(), last.getFrame())).getNorm());
        Vector3D velocity = last.getPVCoordinates().getVelocity();
        speed =
            "moon-surface".equals(stop)
                ? velocity
                    .subtract(
                        OrekitService.get()
                            .body(SolarSystemBody.MOON)
                            .getPVCoordinates(last.getDate(), last.getFrame())
                            .getVelocity())
                    .getNorm()
                : velocity.getNorm();
      }
      return new Guarded(reading, stop, null, speed);
    } catch (RuntimeException e) {
      return new Guarded(
          Double.NaN, null, e.getClass().getSimpleName() + ": " + e.getMessage(), Double.NaN);
    }
  }

  private record Guarded(double reading, String stop, String error, double stopSpeed) {}

  private static final class Counters {
    int attempts;
    int prodThrew;
    int guardedThrew;
    int earthStops;
    int moonStops;
    int bitIdentical;
    int differs;
    int signFlips;
    int saturated;
    int belowFloor;
    int thrownReadAbove;
    double maxGuardedSeconds;
    double maxProdSeconds;
    double noneSeconds;
    double prodSecondsOnFree;
    double maxEarthStopSpeed;
    double maxMoonStopSpeed;

    void count(String stop) {
      if ("moon-surface".equals(stop)) {
        moonStops++;
      } else {
        earthStops++;
      }
    }

    void add(Counters o) {
      attempts += o.attempts;
      prodThrew += o.prodThrew;
      guardedThrew += o.guardedThrew;
      earthStops += o.earthStops;
      moonStops += o.moonStops;
      bitIdentical += o.bitIdentical;
      differs += o.differs;
      signFlips += o.signFlips;
      saturated += o.saturated;
      belowFloor += o.belowFloor;
      thrownReadAbove += o.thrownReadAbove;
      maxGuardedSeconds = FastMath.max(maxGuardedSeconds, o.maxGuardedSeconds);
      maxProdSeconds = FastMath.max(maxProdSeconds, o.maxProdSeconds);
      noneSeconds += o.noneSeconds;
      prodSecondsOnFree += o.prodSecondsOnFree;
      maxEarthStopSpeed = FastMath.max(maxEarthStopSpeed, o.maxEarthStopSpeed);
      maxMoonStopSpeed = FastMath.max(maxMoonStopSpeed, o.maxMoonStopSpeed);
    }

    @Override
    public String toString() {
      return String.format(
          Locale.ROOT,
          "attempts %d | prod threw %d | guarded threw %d (prod read above target %d) | earth"
              + " stops %d | moon stops %d | non-stopped prod vs unguarded bit-identical %d,"
              + " differ %d | stop side disagreements prod vs guarded %d | saturated burns %d"
              + " (end below floor %d) | slowest guarded %.1f s, slowest prod %.1f s | free"
              + " candidates: prod %.1f s vs unguarded %.1f s | max stop speed earth %.0f m/s,"
              + " moon %.0f m/s",
          attempts,
          prodThrew,
          guardedThrew,
          thrownReadAbove,
          earthStops,
          moonStops,
          bitIdentical,
          differs,
          signFlips,
          saturated,
          belowFloor,
          maxGuardedSeconds,
          maxProdSeconds,
          prodSecondsOnFree,
          noneSeconds,
          maxEarthStopSpeed,
          maxMoonStopSpeed);
    }
  }

  private static final class Tracker implements OrekitFixedStepHandler {
    private final Frame frame;
    private double before = Double.NaN;
    private double minimum = Double.POSITIVE_INFINITY;
    private double after = Double.NaN;
    private double previous = Double.NaN;
    private boolean minimumClosed;

    Tracker(Frame frame) {
      this.frame = frame;
    }

    @Override
    public void handleStep(SpacecraftState state) {
      double distance = state.getPosition().subtract(moon(state.getDate(), frame)).getNorm();
      if (distance < minimum) {
        minimum = distance;
        before = previous;
        minimumClosed = false;
      } else if (!minimumClosed && !Double.isNaN(previous)) {
        after = distance;
        minimumClosed = true;
      }
      previous = distance;
    }

    double refinedMinimum() {
      if (Double.isNaN(before) || Double.isNaN(after)) {
        return minimum;
      }
      double denominator = before - 2.0 * minimum + after;
      if (FastMath.abs(denominator) < 1.0e-9) {
        return minimum;
      }
      double shift = 0.5 * (before - after) / denominator;
      return minimum - 0.25 * (before - after) * shift;
    }
  }

  private static final class LunarSurfaceDetector extends AbstractDetector<LunarSurfaceDetector> {
    private final double radius;
    private final Frame frame;

    LunarSurfaceDetector(double radius, Frame frame) {
      super(10.0, 1.0, DEFAULT_MAX_ITER, new ContinueOnEvent());
      this.radius = radius;
      this.frame = frame;
    }

    private LunarSurfaceDetector(
        EventDetectionSettings settings, EventHandler handler, double radius, Frame frame) {
      super(settings, handler);
      this.radius = radius;
      this.frame = frame;
    }

    @Override
    protected LunarSurfaceDetector create(EventDetectionSettings settings, EventHandler handler) {
      return new LunarSurfaceDetector(settings, handler, radius, frame);
    }

    @Override
    public double g(SpacecraftState s) {
      return s.getPosition().subtract(moon(s.getDate(), frame)).getNorm() - radius;
    }
  }

  private static Method method(String name, Class<?>... types) throws NoSuchMethodException {
    Method m = TranslunarInjectionPlan.class.getDeclaredMethod(name, types);
    m.setAccessible(true);
    return m;
  }

  private static Object accessor(Object record, String name) throws Exception {
    Method m = record.getClass().getDeclaredMethod(name);
    m.setAccessible(true);
    return m.invoke(record);
  }

  private static Vector3D moon(AbsoluteDate date, Frame frame) {
    return OrekitService.get().body(SolarSystemBody.MOON).getPosition(date, frame);
  }

  private static SpacecraftState state(
      AbsoluteDate date, Vector3D position, Vector3D velocity, double mass) {
    return new SpacecraftState(
            new CartesianOrbit(
                new TimeStampedPVCoordinates(date, position, velocity),
                OrekitService.get().gcrf(),
                Constants.WGS84_EARTH_MU))
        .withMass(mass);
  }

  private static double d(String s) {
    return Double.parseDouble(s);
  }

  private static MissionSpec.LunarOrbit spec(String site) {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "probe");
    if ("kourou".equals(site)) {
      values.put("LAUNCH_SITE_LAT", 5.236);
      values.put("LAUNCH_SITE_LONG", -52.775);
      values.put("LAUNCH_SITE_ALT", 0.0);
    } else {
      values.put("LAUNCH_SITE_LAT", 28.562);
      values.put("LAUNCH_SITE_LONG", -80.577);
      values.put("LAUNCH_SITE_ALT", 3.0);
    }
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "LUNAR_ORBITER");
    values.put("PAYLOAD_MASS", 2_000.0);
    values.put("LUNAR_ORBIT_ALT", 100.0);
    return (MissionSpec.LunarOrbit)
        MissionFactory.specFromWizardValues(values, MissionType.LUNAR_ORBIT);
  }
}
