package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.stage.TLIBurnStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.control.heuristics.lambert.LambertBoundaryConditions;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.propagation.sampling.OrekitStepHandler;
import org.orekit.propagation.sampling.OrekitStepInterpolator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;

/** Probe: replays the TLI aim from a captured ignition state, candidate by candidate. */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
class TliAimReplayProbe {
  private static final Logger logger = LogManager.getLogger(TliAimReplayProbe.class);
  private static final Path IN = Path.of("build/probe-tli-states.txt");
  private static final double TARGET = 100_000.0;

  @Test
  void replay() throws Exception {
    OrekitService.get().initialize();
    for (String line : Files.readAllLines(IN)) {
      String[] f = line.trim().split("\\s+");
      String site = f[0];
      AbsoluteDate date = new AbsoluteDate(f[2], org.orekit.time.TimeScalesFactory.getUTC());
      Frame gcrf = OrekitService.get().gcrf();
      SpacecraftState ignition =
          new SpacecraftState(
                  new CartesianOrbit(
                      new TimeStampedPVCoordinates(
                          date,
                          new Vector3D(d(f[3]), d(f[4]), d(f[5])),
                          new Vector3D(d(f[6]), d(f[7]), d(f[8]))),
                      gcrf,
                      Constants.WGS84_EARTH_MU))
              .withMass(d(f[9]));
      logger.info("REPLAY {} ignition {} mass {}", site, date, ignition.getMass());
      replayOne(site, ignition);
    }
  }

  private void replayOne(String site, SpacecraftState ignition) throws Exception {
    Mission mission = new MissionEntry(spec(site)).mission();
    MissionStage tli =
        mission.getStages().stream().filter(s -> s instanceof TLIBurnStage).findFirst().get();
    FlightContext context = tli.flightContext(ignition, mission);
    logger.info("REPLAY context drag={}", context.drag() != null);

    TranslunarInjectionPlan.Departure departure = TranslunarInjectionPlan.departureFrom(ignition);
    NumericalPropagator ballistic =
        OrekitService.get()
            .createOptimizationPropagator(context, tli.maxStepSeconds(ignition, mission));
    ballistic.setInitialState(ignition);
    SpacecraftState atInjection = ballistic.propagate(departure.injectionDate());
    ActiveStageInfo active = mission.getVehicle().resolveActiveStage(ignition.getMass());

    try {
      TranslunarInjectionPlan.Burn burn =
          TranslunarInjectionPlan.inject(ignition, atInjection, TARGET, active, context);
      logger.info("REPLAY inject OK: perilune {} km", burn.plan().perileneAltitude() / 1000.0);
    } catch (RuntimeException e) {
      logger.info("REPLAY inject FAILED: {} {}", e.getClass().getSimpleName(), e.getMessage());
    }

    AbsoluteDate arrival =
        atInjection.getDate().shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS);
    Vector3D moonAtArrival = moon(arrival, atInjection.getFrame());
    Method aimDir =
        TranslunarInjectionPlan.class.getDeclaredMethod(
            "aimOffsetDirection", SpacecraftState.class, Vector3D.class, AbsoluteDate.class);
    aimDir.setAccessible(true);
    Vector3D offsetDirection = (Vector3D) aimDir.invoke(null, atInjection, moonAtArrival, arrival);
    Method calibrate =
        TranslunarInjectionPlan.class.getDeclaredMethod(
            "calibrateBurn",
            SpacecraftState.class,
            SpacecraftState.class,
            Vector3D.class,
            ActiveStageInfo.class,
            FlightContext.class,
            boolean.class);
    calibrate.setAccessible(true);

    double lunarRadius = GravitationalContext.moon().shape().getEquatorialRadius();
    double start = lunarRadius + TARGET;
    List<Double> offsets = new ArrayList<>();
    offsets.add(start);
    for (int i = 1; i <= 8; i++) {
      offsets.add(start * Math.pow(2.0, i));
    }
    for (int i = 1; i <= 8; i++) {
      offsets.add(start * Math.pow(0.5, i));
    }

    for (double offset : offsets) {
      Vector3D aimPoint = moonAtArrival.add(offsetDirection.scalarMultiply(offset));
      LambertBoundaryConditions conditions =
          TranslunarInjectionPlan.boundaryConditions(atInjection, arrival, aimPoint);
      Vector3D seed = TranslunarInjectionPlan.keplerianSeedVelocity(atInjection, conditions);
      Vector3D deltaV = seed.subtract(atInjection.getPVCoordinates().getVelocity());
      Object calibrated =
          calibrate.invoke(null, ignition, atInjection, deltaV, active, context, false);
      Method endState = calibrated.getClass().getDeclaredMethod("endState");
      endState.setAccessible(true);
      SpacecraftState injected = (SpacecraftState) endState.invoke(calibrated);
      Method duration = calibrated.getClass().getDeclaredMethod("duration");
      duration.setAccessible(true);
      double flownDuration = (double) duration.invoke(calibrated);
      double uncapped =
          com.smousseur.orbitlab.simulation.Physics.computeBurnDuration(
              deltaV.getNorm(),
              ignition.getMass(),
              active.propulsion().isp(),
              active.propulsion().thrust());
      double fuel = active.remainingFuel(ignition.getMass());
      double ve = active.propulsion().isp() * Constants.G0_STANDARD_GRAVITY;
      logger.info(
          String.format(
              Locale.ROOT,
              "REPLAY burn offset %9.0f km: impulsive %.0f m/s, uncapped dt %.1f s, flown dt %.1f"
                  + " s, fuel %.0f kg (max dv %.0f m/s), end mass %.0f kg",
              offset / 1000.0,
              deltaV.getNorm(),
              uncapped,
              flownDuration,
              fuel,
              ve * Math.log(ignition.getMass() / (ignition.getMass() - fuel)),
              injected.getMass()));
      if ("kourou".equals(site)) {
        continue;
      }
      fly("prod", offset, injected, arrival, context);
      fly("nodrag", offset, injected, arrival, new FlightContext(context.gravity()));
    }
  }

  private void fly(
      String label,
      double offset,
      SpacecraftState injected,
      AbsoluteDate arrival,
      FlightContext context) {
    double searchEnd = arrival.durationFrom(injected.getDate()) + 0.5 * 86_400.0;
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, OrekitService.COAST_MAX_STEP);
    propagator.setInitialState(injected);
    Tracker tracker = new Tracker(injected.getFrame(), injected.getDate());
    propagator.getMultiplexer().add(tracker);
    String outcome;
    try {
      propagator.propagate(injected.getDate().shiftedBy(searchEnd));
      outcome = "OK";
    } catch (RuntimeException e) {
      outcome = "FAILED " + e.getMessage();
    }
    logger.info(
        String.format(
            Locale.ROOT,
            "REPLAY %-6s offset %9.0f km: minMoonCentre %10.1f km at t+%.2f d, minEarthCentre"
                + " %10.1f km at t+%.2f d (after the first hour), last t+%.3f d moon %.1f km"
                + " earth %.1f km, steps %d -> %s",
            label,
            offset / 1000.0,
            tracker.minMoon / 1000.0,
            tracker.minMoonT / 86_400.0,
            tracker.minEarth / 1000.0,
            tracker.minEarthT / 86_400.0,
            tracker.lastT / 86_400.0,
            tracker.lastMoon / 1000.0,
            tracker.lastEarth / 1000.0,
            tracker.steps,
            outcome));
  }

  private static final class Tracker implements OrekitStepHandler {
    private final Frame frame;
    private final AbsoluteDate t0;
    double minMoon = Double.POSITIVE_INFINITY;
    double minMoonT;
    double minEarth = Double.POSITIVE_INFINITY;
    double minEarthT;
    double lastT;
    double lastMoon;
    double lastEarth;
    int steps;

    Tracker(Frame frame, AbsoluteDate t0) {
      this.frame = frame;
      this.t0 = t0;
    }

    @Override
    public void handleStep(OrekitStepInterpolator interpolator) {
      SpacecraftState s = interpolator.getCurrentState();
      steps++;
      double t = s.getDate().durationFrom(t0);
      double moonDistance = s.getPosition().subtract(moon(s.getDate(), frame)).getNorm();
      double earthDistance = s.getPosition().getNorm();
      if (moonDistance < minMoon) {
        minMoon = moonDistance;
        minMoonT = t;
      }
      if (t > 3_600.0 && earthDistance < minEarth) {
        minEarth = earthDistance;
        minEarthT = t;
      }
      lastT = t;
      lastMoon = moonDistance;
      lastEarth = earthDistance;
    }
  }

  private static Vector3D moon(AbsoluteDate date, Frame frame) {
    return OrekitService.get().body(SolarSystemBody.MOON).getPosition(date, frame);
  }

  private static double d(String s) {
    return Double.parseDouble(s);
  }

  private static MissionSpec spec(String site) {
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
    return MissionFactory.specFromWizardValues(values, MissionType.LUNAR_ORBIT);
  }
}
