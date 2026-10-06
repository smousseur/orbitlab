package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan.Departure;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.operation.EarthMission;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.LunarOrbitMission;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.operation.NodeBranch;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.planner.FixedLoadPlanner;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlan;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticParkingInsertionStage;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.stage.LunarApproachCoastStage;
import com.smousseur.orbitlab.simulation.mission.stage.LunarInsertionStage;
import com.smousseur.orbitlab.simulation.mission.stage.ParkingCoastStage;
import com.smousseur.orbitlab.simulation.mission.stage.StageNames;
import com.smousseur.orbitlab.simulation.mission.stage.StageSeparationStage;
import com.smousseur.orbitlab.simulation.mission.stage.TLIBurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.TranslunarCoastStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentSequence;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.VerticalAscentStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;

/** Probe for the design of MIS-15 / L2: the free-azimuth plane, swept and flown. Not committed. */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
class L2DesignProbe {
  private static final Logger logger = LogManager.getLogger(L2DesignProbe.class);
  private static final double PARKING = LunarOrbitMission.DEFAULT_PARKING_ALTITUDE;
  private static final double RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + PARKING;
  private static final double TOF = TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS;

  private static final double KOUROU_LAT = 5.236;
  private static final double KOUROU_LON = -52.775;
  private static final double KOUROU_ALT = 0.0;
  private static final double CANAVERAL_LAT = 28.562;
  private static final double CANAVERAL_LON = -80.577;
  private static final double CANAVERAL_ALT = 3.0;

  private Method ascent;

  /** The free-azimuth plane over 48 h, every 10 min, against due east. */
  @Test
  void sweep() throws Exception {
    OrekitService.get().initialize();
    ascent = ascentMethod();
    AbsoluteDate floor = date("2026-10-06T08:00:00.000Z");
    sweepSite("kourou", KOUROU_LAT, KOUROU_LON, KOUROU_ALT, floor);
    sweepSite("canaveral", CANAVERAL_LAT, CANAVERAL_LON, CANAVERAL_ALT, floor);
  }

  private void sweepSite(String label, double lat, double lon, double alt, AbsoluteDate floor)
      throws Exception {
    double eastAscent = (double) ascent.invoke(null, PARKING, lat, FastMath.PI / 2);
    StringBuilder table = new StringBuilder();
    double bestTotal = Double.POSITIVE_INFINITY;
    String best = "";
    for (int k = 0; k <= 48 * 6; k++) {
      AbsoluteDate epoch = floor.shiftedBy(k * 600.0);
      Free free = freeAzimuth(lat, lon, alt, epoch);
      Free east = planeAt(lat, lon, alt, FastMath.PI / 2, epoch);
      double surcharge = (double) ascent.invoke(null, PARKING, lat, free.azimuth) - eastAscent;
      String row =
          String.format(
              Locale.ROOT,
              "%s A %6.2f° i %5.2f° %s β %+.4f° tli %6.1f surcharge %5.1f total %6.1f | east β %+6.2f° tli %7.1f",
              epoch,
              FastMath.toDegrees(free.azimuth),
              FastMath.toDegrees(free.inclination),
              free.azimuth <= FastMath.PI / 2 ? "asc " : "desc",
              FastMath.toDegrees(free.beta),
              free.tli,
              surcharge,
              free.tli + surcharge,
              FastMath.toDegrees(east.beta),
              east.tli);
      if (k % 6 == 0) {
        table.append(System.lineSeparator()).append("  ").append(row);
      }
      if (free.tli + surcharge < bestTotal) {
        bestTotal = free.tli + surcharge;
        best = row;
      }
    }
    logger.info("L2 SWEEP {} (hourly rows; due-east ascent {} m/s):{}", label, eastAscent, table);
    logger.info("L2 SWEEP {} best of 48 h: {}", label, best);
  }

  /** The least-surcharge free-azimuth epoch of each day of a lunation, from Kourou. */
  @Test
  void bestPerDay() throws Exception {
    OrekitService.get().initialize();
    ascent = ascentMethod();
    double eastAscent = (double) ascent.invoke(null, PARKING, KOUROU_LAT, FastMath.PI / 2);
    AbsoluteDate start = date("2026-10-06T00:00:00.000Z");
    StringBuilder table = new StringBuilder();
    for (int day = 0; day < 28; day++) {
      String best = "";
      double bestTotal = Double.POSITIVE_INFINITY;
      for (int k = 0; k < 144; k++) {
        AbsoluteDate epoch = start.shiftedBy(day * 86_400.0 + k * 600.0);
        Free free = freeAzimuth(KOUROU_LAT, KOUROU_LON, KOUROU_ALT, epoch);
        double surcharge =
            (double) ascent.invoke(null, PARKING, KOUROU_LAT, free.azimuth) - eastAscent;
        double residual =
            FastMath.toDegrees(
                FastMath.asin(
                    465.0
                        * FastMath.cos(FastMath.toRadians(KOUROU_LAT))
                        * FastMath.abs(FastMath.cos(free.azimuth))
                        / 7_700.0));
        if (free.tli + surcharge < bestTotal) {
          bestTotal = free.tli + surcharge;
          best =
              String.format(
                  Locale.ROOT,
                  "%s A %6.2f° i %5.2f° %s surcharge %5.1f tli %6.1f MIS-7 residual ~%.2f°",
                  epoch,
                  FastMath.toDegrees(free.azimuth),
                  FastMath.toDegrees(free.inclination),
                  free.azimuth <= FastMath.PI / 2 ? "asc " : "desc",
                  surcharge,
                  free.tli,
                  residual);
        }
      }
      table.append(System.lineSeparator()).append("  d").append(day).append(' ').append(best);
    }
    logger.info("L2 BEST PER DAY kourou:{}", table);
  }

  /** Speed at main engine cut-off the residual model divides by (m/s). */
  private static final double MECO_SPEED =
      Double.parseDouble(System.getenv().getOrDefault("PROBE_MECO_SPEED", "7700"));

  /**
   * β the residual model predicts for a commanded azimuth: the pad's entrainment velocity out of
   * the commanded plane, left uncorrected by the ascent, tilts the plane about the vertical by
   * {@code atan(w / v)}.
   */
  private static double predictedBeta(
      double lat, double lon, double alt, double azimuth, AbsoluteDate epoch, Vector3D moonDir) {
    LaunchSitePlane site = new LaunchSitePlane(lat, lon, alt, azimuth);
    Vector3D r = site.positionAt(epoch);
    Vector3D commanded = site.normalOn(r);
    Vector3D entrainment =
        Vector3D.crossProduct(new Vector3D(0, 0, Constants.WGS84_EARTH_ANGULAR_VELOCITY), r);
    double w = entrainment.dotProduct(commanded);
    Vector3D along = Vector3D.crossProduct(commanded, r.normalize());
    Vector3D flown =
        commanded.scalarMultiply(MECO_SPEED).subtract(along.scalarMultiply(w)).normalize();
    return FastMath.asin(flown.dotProduct(moonDir));
  }

  /** The azimuth whose predicted flown plane contains the Moon's direction, by secant from A*. */
  private Free compensated(double lat, double lon, double alt, AbsoluteDate epoch) {
    Free free = freeAzimuth(lat, lon, alt, epoch);
    double a0 = free.azimuth;
    double a1 = free.azimuth + 1.0e-3;
    double f0 = compensationResidual(lat, lon, alt, a0, epoch);
    double f1 = compensationResidual(lat, lon, alt, a1, epoch);
    for (int i = 0; i < 20 && FastMath.abs(f1) > 1.0e-12; i++) {
      double a2 = a1 - f1 * (a1 - a0) / (f1 - f0);
      a0 = a1;
      f0 = f1;
      a1 = a2;
      f1 = compensationResidual(lat, lon, alt, a1, epoch);
    }
    return planeAt(lat, lon, alt, a1, epoch);
  }

  private double compensationResidual(
      double lat, double lon, double alt, double azimuth, AbsoluteDate epoch) {
    Free at = planeAt(lat, lon, alt, azimuth, epoch);
    Vector3D moonDir = moon(epoch.shiftedBy(at.coast + TOF)).normalize();
    return predictedBeta(lat, lon, alt, azimuth, epoch, moonDir);
  }

  /** The model's β against the four flights, and the compensated azimuth of each. */
  @Test
  void residualModel() {
    OrekitService.get().initialize();
    String[] epochs = {
      "2026-10-06T08:00:00.000Z",
      "2026-10-06T13:00:00.000Z",
      "2026-10-12T13:30:00.000Z",
      "2026-10-11T01:30:00.000Z"
    };
    for (String iso : epochs) {
      AbsoluteDate epoch = date(iso);
      Free free = freeAzimuth(KOUROU_LAT, KOUROU_LON, KOUROU_ALT, epoch);
      Vector3D moonDir = moon(epoch.shiftedBy(free.coast + TOF)).normalize();
      double predicted =
          predictedBeta(KOUROU_LAT, KOUROU_LON, KOUROU_ALT, free.azimuth, epoch, moonDir);
      Free comp = compensated(KOUROU_LAT, KOUROU_LON, KOUROU_ALT, epoch);
      logger.info(
          String.format(
              Locale.ROOT,
              "L2 MODEL %s A* %.4f° predicted flown β %+.4f° | compensated A %.4f° (%+.4f°),"
                  + " window β there %+.4f°, tli %.1f",
              iso,
              FastMath.toDegrees(free.azimuth),
              FastMath.toDegrees(predicted),
              FastMath.toDegrees(comp.azimuth),
              FastMath.toDegrees(comp.azimuth - free.azimuth),
              FastMath.toDegrees(comp.beta),
              comp.tli));
    }
  }

  /** Flies the free-azimuth plane from Kourou through the production planner. */
  @Test
  void fly() throws Exception {
    OrekitService.get().initialize();
    ascent = ascentMethod();
    String[] epochs =
        System.getenv().getOrDefault("PROBE_EPOCHS", "2026-10-06T08:00:00.000Z").split(",");
    for (String iso : epochs) {
      flyAt(date(iso.trim()));
    }
  }

  private void flyAt(AbsoluteDate epoch) {
    Free free =
        "true".equals(System.getenv("PROBE_COMPENSATE"))
            ? compensated(KOUROU_LAT, KOUROU_LON, KOUROU_ALT, epoch)
            : freeAzimuth(KOUROU_LAT, KOUROU_LON, KOUROU_ALT, epoch);
    double inclination =
        FastMath.acos(FastMath.sin(free.azimuth) * FastMath.cos(FastMath.toRadians(KOUROU_LAT)));
    NodeBranch branch =
        free.azimuth <= FastMath.PI / 2 ? NodeBranch.ASCENDING : NodeBranch.DESCENDING;
    LaunchPlane plane = new LaunchPlane(inclination, branch);
    double replayedAzimuth = plane.launchAzimuth(FastMath.toRadians(KOUROU_LAT));
    logger.info(
        String.format(
            Locale.ROOT,
            "L2 FLY %s: free azimuth %.6f° → plane i %.6f° %s → azimuth back %.6f° (diff %.3e rad);"
                + " window β %+.5f°, tli %.1f m/s",
            epoch,
            FastMath.toDegrees(free.azimuth),
            FastMath.toDegrees(inclination),
            branch,
            FastMath.toDegrees(replayedAzimuth),
            replayedAzimuth - free.azimuth,
            FastMath.toDegrees(free.beta),
            free.tli));

    MissionSpec.LunarOrbit spec = kourouSpec();
    PlaneLunarOrbitMission mission =
        PlaneLunarOrbitMission.of(spec, plane, KOUROU_LAT, KOUROU_LON, KOUROU_ALT);
    mission.setHorizon(spec.horizon());
    mission.setAtmosphere(spec.atmosphere());
    mission.setCurrentState(mission.getInitialState(epoch));
    long start = System.nanoTime();
    MissionPlan plan = null;
    String outcome;
    try {
      plan = new FixedLoadPlanner(mission, 40_000, 42L).plan();
      outcome = "OK";
    } catch (RuntimeException e) {
      outcome = "FAILED " + e.getClass().getSimpleName() + " " + e.getMessage();
    }
    logger.info(
        "L2 FLY {} outcome after {} s: {}",
        epoch,
        String.format(Locale.ROOT, "%.1f", (System.nanoTime() - start) / 1e9),
        outcome);

    Vector3D commanded =
        new LaunchSitePlane(KOUROU_LAT, KOUROU_LON, KOUROU_ALT, free.azimuth).normalAt(epoch);
    SpacecraftState ignition = plan == null ? mission.getCurrentState() : parkingEnd(plan);
    Departure flown = TranslunarInjectionPlan.departureFrom(ignition);
    Vector3D moonAtArrival = moon(flown.arrivalDate()).normalize();
    logger.info(
        String.format(
            Locale.ROOT,
            "L2 FLY %s TLI ignition %s mass %.1f: flown β %+.4f° (window %+.4f°); flown plane %.4f°"
                + " from the commanded one",
            epoch,
            ignition.getDate(),
            ignition.getMass(),
            FastMath.toDegrees(flown.planeMisalignment()),
            FastMath.toDegrees(free.beta),
            FastMath.toDegrees(Vector3D.angle(commanded, normalOf(ignition)))));
    if (plan != null) {
      for (Map.Entry<String, MissionEphemerisPoint> e : stageEnds(plan).entrySet()) {
        MissionEphemerisPoint p = e.getValue();
        Vector3D n = Vector3D.crossProduct(p.position(), p.velocity()).normalize();
        logger.info(
            String.format(
                Locale.ROOT,
                "L2 FLY %s   end of %-28s %s: plane %.4f° from commanded, β vs flown arrival %+.4f°,"
                    + " i %.4f°, |v| %.1f m/s, alt %.1f km",
                epoch,
                e.getKey(),
                p.time(),
                FastMath.toDegrees(Vector3D.angle(commanded, n)),
                FastMath.toDegrees(FastMath.asin(n.dotProduct(moonAtArrival))),
                FastMath.toDegrees(FastMath.acos(n.getZ())),
                p.velocity().getNorm(),
                (p.position().getNorm() - Constants.WGS84_EARTH_EQUATORIAL_RADIUS) / 1000.0));
      }
    }
  }

  /** The plane through the pad and the Moon's direction at arrival, closed on the coast. */
  private Free freeAzimuth(double lat, double lon, double alt, AbsoluteDate epoch) {
    Vector3D r = new LaunchSitePlane(lat, lon, alt, FastMath.PI / 2).positionAt(epoch);
    Vector3D north = Physics.localHorizontalDirection(r, 0.0);
    Vector3D east = Physics.localHorizontalDirection(r, FastMath.PI / 2);
    double coast = 0.0;
    double azimuth = FastMath.PI / 2;
    for (int pass = 0; pass < 6; pass++) {
      Vector3D moon = moon(epoch.shiftedBy(coast + TOF)).normalize();
      Vector3D n = Vector3D.crossProduct(r, moon).normalize();
      if (n.getZ() < 0.0) {
        n = n.negate();
      }
      Vector3D motion = Vector3D.crossProduct(n, r).normalize();
      azimuth = FastMath.atan2(motion.dotProduct(east), motion.dotProduct(north));
      Free free = planeAt(lat, lon, alt, azimuth, epoch);
      coast = free.coast;
    }
    return planeAt(lat, lon, alt, azimuth, epoch);
  }

  private Free planeAt(double lat, double lon, double alt, double azimuth, AbsoluteDate epoch) {
    LaunchSitePlane site = new LaunchSitePlane(lat, lon, alt, azimuth);
    Vector3D position = site.positionAt(epoch);
    Vector3D normal = site.normalOn(position);
    Departure departure = TranslunarInjectionPlan.departureFrom(circular(normal, position, epoch));
    SpacecraftState injection =
        circular(normal, departure.injectionDirection(), departure.injectionDate());
    double tli =
        TranslunarInjectionPlan.keplerianInjectionDeltaV(injection, departure.arrivalDate());
    return new Free(
        azimuth,
        FastMath.acos(normal.getZ()),
        departure.planeMisalignment(),
        tli,
        departure.coastDuration());
  }

  private record Free(double azimuth, double inclination, double beta, double tli, double coast) {}

  private static SpacecraftState parkingEnd(MissionPlan plan) {
    MissionEphemerisPoint last = null;
    for (MissionEphemerisPoint p : plan.computation().ephemeris().allPoints()) {
      if (LunarOrbitMission.PARKING_COAST_NAME.equals(p.stageName())) {
        last = p;
      }
    }
    return new SpacecraftState(
            new CartesianOrbit(
                new TimeStampedPVCoordinates(last.time(), last.position(), last.velocity()),
                OrekitService.get().gcrf(),
                Constants.WGS84_EARTH_MU))
        .withMass(last.mass());
  }

  private static Map<String, MissionEphemerisPoint> stageEnds(MissionPlan plan) {
    Map<String, MissionEphemerisPoint> ends = new LinkedHashMap<>();
    for (MissionEphemerisPoint p : plan.computation().ephemeris().allPoints()) {
      if (p.time().isAfter(stopAfterTli(plan))) {
        break;
      }
      ends.put(p.stageName(), p);
    }
    return ends;
  }

  private static AbsoluteDate stopAfterTli(MissionPlan plan) {
    AbsoluteDate first = plan.computation().ephemeris().startDate();
    return first.shiftedBy(4 * 3_600.0);
  }

  private static Vector3D normalOf(SpacecraftState s) {
    return Vector3D.crossProduct(s.getPosition(), s.getPVCoordinates().getVelocity()).normalize();
  }

  private static SpacecraftState circular(Vector3D normal, Vector3D towards, AbsoluteDate date) {
    Vector3D direction =
        towards.subtract(normal.scalarMultiply(towards.dotProduct(normal))).normalize();
    Vector3D velocity =
        Vector3D.crossProduct(normal, direction)
            .scalarMultiply(FastMath.sqrt(Constants.WGS84_EARTH_MU / RADIUS));
    return new SpacecraftState(
            new CartesianOrbit(
                new TimeStampedPVCoordinates(date, direction.scalarMultiply(RADIUS), velocity),
                OrekitService.get().gcrf(),
                Constants.WGS84_EARTH_MU))
        .withMass(1_000.0);
  }

  private static Vector3D moon(AbsoluteDate date) {
    return OrekitService.get()
        .body(SolarSystemBody.MOON)
        .getPosition(date, OrekitService.get().gcrf());
  }

  private static AbsoluteDate date(String iso) {
    return new AbsoluteDate(iso, TimeScalesFactory.getUTC());
  }

  private static Method ascentMethod() throws NoSuchMethodException {
    Method m =
        PropellantBudget.class.getDeclaredMethod(
            "ascentDeltaV", double.class, double.class, double.class);
    m.setAccessible(true);
    return m;
  }

  private static MissionSpec.LunarOrbit kourouSpec() {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "probe");
    values.put("LAUNCH_SITE_NAME", "Kourou");
    values.put("LAUNCH_SITE_LAT", KOUROU_LAT);
    values.put("LAUNCH_SITE_LONG", KOUROU_LON);
    values.put("LAUNCH_SITE_ALT", KOUROU_ALT);
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "LUNAR_ORBITER");
    values.put("PAYLOAD_MASS", 2_000.0);
    values.put("LUNAR_ORBIT_ALT", 100.0);
    return (MissionSpec.LunarOrbit)
        MissionFactory.specFromWizardValues(values, MissionType.LUNAR_ORBIT);
  }

  /** {@code LunarOrbitMission}'s chain with a commanded ascent plane instead of due east. */
  static final class PlaneLunarOrbitMission extends EarthMission {
    private final double latitude;
    private final double longitude;
    private final double altitude;

    private PlaneLunarOrbitMission(
        Vehicle vehicle,
        AscentProfile profile,
        LaunchPlane plane,
        double orbitAltitude,
        double latitude,
        double longitude,
        double altitude) {
      super(
          "probe",
          vehicle,
          stages(vehicle, profile, plane, orbitAltitude, latitude),
          OrbitInsertionObjective.circular(SolarSystemBody.MOON, orbitAltitude, Double.NaN));
      this.latitude = latitude;
      this.longitude = longitude;
      this.altitude = altitude;
    }

    static PlaneLunarOrbitMission of(
        MissionSpec.LunarOrbit spec,
        LaunchPlane plane,
        double latitude,
        double longitude,
        double altitude) {
      LaunchConfiguration configuration = spec.configuration();
      return new PlaneLunarOrbitMission(
          configuration.toVehicleStack(),
          configuration.ascentProfile(),
          plane,
          spec.orbitAltitude(),
          latitude,
          longitude,
          altitude);
    }

    @Override
    protected double getLatitude() {
      return latitude;
    }

    @Override
    protected double getLongitude() {
      return longitude;
    }

    @Override
    protected double getAltitude() {
      return altitude;
    }

    @Override
    public GravitationalContext gravitationalContext() {
      return GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN);
    }

    private static List<MissionStage> stages(
        Vehicle vehicle,
        AscentProfile profile,
        LaunchPlane plane,
        double orbitAltitude,
        double latitude) {
      List<MissionStage> stages = new ArrayList<>();
      stages.add(new VerticalAscentStage("Vertical Ascent", profile.verticalAscentDuration()));
      stages.addAll(
          AscentSequence.gravityTurn(
              vehicle, profile, GravityTurnConstraints.forTarget(PARKING), plane, latitude));
      stages.add(new AnalyticParkingInsertionStage("Parking", PARKING));
      stages.add(new ParkingCoastStage(LunarOrbitMission.PARKING_COAST_NAME));
      stages.add(new TLIBurnStage("Translunar injection", orbitAltitude));
      stages.add(
          new StageSeparationStage(
              StageNames.UPPER_SEPARATION, profile.interstageCoastDuration(), StageRole.UPPER));
      stages.add(
          new TranslunarCoastStage(
              LunarOrbitMission.TRANSLUNAR_COAST_NAME,
              LunarOrbitMission.TRANSLUNAR_COAST_BOUND_SECONDS));
      stages.add(new LunarApproachCoastStage(LunarOrbitMission.APPROACH_COAST_NAME));
      stages.add(new LunarInsertionStage(LunarOrbitMission.INSERTION_NAME));
      stages.add(new CoastingStage(StageNames.TERMINAL_COAST, null, SolarSystemBody.MOON));
      return List.copyOf(stages);
    }
  }
}
