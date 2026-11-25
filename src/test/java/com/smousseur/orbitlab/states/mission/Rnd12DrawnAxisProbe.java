package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * The axis production flights are drawn along, against the three rules fixed before this ran. NOT a
 * gate: it asserts nothing. Run with {@code -Dorbitlab.probe=true --tests '*Rnd12DrawnAxisProbe*'};
 * the report is also written to {@code build/rnd12-drawn-axis.txt}.
 *
 * <p>Two Earth orbits from Kourou, built from wizard values and computed by {@code
 * MissionPlanOptimizer.compute()} in FAST: a Falcon Heavy due east, and an Ariane 64 on a commanded
 * 97.5° plane — the case whose air-relative and inertial velocities part the most, up to 3.6° in
 * orbit. For each, the angles between the drawn axis and the radial vertical, and between the drawn
 * axis and the inertial velocity, along the ascent and over a stretch of orbit.
 *
 * <p>Rules: <b>V1</b> through the vertical climb the axis lies within {@value #V1_MAX_DEG}° of the
 * vertical; <b>V2</b> above the inertial floor it is the inertial velocity to {@value
 * #V2_MAX_DEG}°; <b>V3</b> the median call costs at most {@value #V3_MAX_MICROS} µs, timed on the
 * ascent samples below the floor — the branch that transforms into ITRF — once the terrestrial
 * frame is warm, as the application warms it at start-up.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class Rnd12DrawnAxisProbe {

  private static final double V1_MAX_DEG = 0.1;
  private static final double V2_MAX_DEG = 1e-6;
  private static final double V3_MAX_MICROS = 20.0;

  private static final double[] OFFSETS_S = {
    0, 1, 2, 5, 7, 10, 15, 20, 30, 45, 60, 90, 120, 150, 165, 180, 240, 300, 400, 600, 800
  };
  private static final double ORBIT_FROM_S = 10_000.0;
  private static final double ORBIT_TO_S = 16_000.0;
  private static final int BATCHES = 21;
  private static final int CALLS_PER_BATCH = 2_000;

  private static AbsoluteDate epoch;

  private final List<String> report = new ArrayList<>();
  private double worstVerticalDeg;
  private double worstInertialDeg;
  private final List<Double> medianMicros = new ArrayList<>();

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    OrekitService.get().warmUpFrames();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  void drawnAxisOfProductionFlights() throws IOException {
    fly("FH LEO 400 due east", values(Launchers.FALCON_HEAVY, null));
    fly("A64 LEO 400 i=97.5", values(Launchers.ARIANE_64, 97.5));

    double worstMedian = medianMicros.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    line("%n=== VERDICTS (rules fixed before the measurement)");
    line(
        "  V1 vertical climb: worst axis-to-vertical %.4f deg (rule <= %.1f) -> %s",
        worstVerticalDeg, V1_MAX_DEG, worstVerticalDeg <= V1_MAX_DEG ? "PASS" : "FAIL");
    line(
        "  V2 above %.0f km: worst axis-to-inertial %.3e deg (rule <= %.0e) -> %s",
        DrawnFlightAxis.INERTIAL_FLOOR_M / 1000.0,
        worstInertialDeg,
        V2_MAX_DEG,
        worstInertialDeg <= V2_MAX_DEG ? "PASS" : "FAIL");
    line(
        "  V3 cost: worst median %.2f us per call (rule <= %.0f) -> %s",
        worstMedian,
        V3_MAX_MICROS,
        worstMedian <= V3_MAX_MICROS ? "PASS, per frame on the render thread" : "FAIL");

    Path out = Path.of("build/rnd12-drawn-axis.txt");
    Files.createDirectories(out.getParent());
    Files.write(out, report, StandardCharsets.UTF_8);
  }

  private void fly(String label, Map<String, Object> values) {
    MissionEntry entry =
        new MissionEntry(MissionFactory.specFromWizardValues(values, MissionType.LEO));
    if (entry.getOptimizationType() != OptimizationType.FAST) {
      throw new IllegalStateException("the entry composed " + entry.getOptimizationType());
    }
    MissionEphemeris ephemeris =
        new MissionPlanOptimizer(entry, epoch).compute().computation().ephemeris();
    AbsoluteDate start = ephemeris.startDate();
    line("%n=== %s", label);
    line("  %7s %-22s %9s | %10s %10s", "T+", "stage", "alt km", "axis^up", "axis^v");
    for (double offset : OFFSETS_S) {
      MissionEphemerisPoint p = ephemeris.displayPointAt(start.shiftedBy(offset));
      Vector3D axis = DrawnFlightAxis.of(p);
      line(
          "  %7.0f %-22s %9.3f | %10.4f %10.4f",
          offset,
          p.stageName(),
          p.altitudeMeters() / 1000.0,
          degrees(axis, p.position()),
          degrees(axis, p.velocity()));
    }

    double climbWorst = 0.0;
    double aboveWorst = 0.0;
    double orbitMin = Double.POSITIVE_INFINITY;
    double orbitMax = 0.0;
    List<MissionEphemerisPoint> below = new ArrayList<>();
    for (MissionEphemerisPoint p : ephemeris.allPoints()) {
      Vector3D axis = DrawnFlightAxis.of(p);
      double t = p.time().durationFrom(start);
      if ("Vertical Ascent".equals(p.stageName())) {
        climbWorst = Math.max(climbWorst, degrees(axis, p.position()));
      }
      if (p.altitudeMeters() > DrawnFlightAxis.INERTIAL_FLOOR_M) {
        aboveWorst = Math.max(aboveWorst, degrees(axis, p.velocity()));
      } else if (t < ORBIT_FROM_S) {
        below.add(p);
      }
      if (t >= ORBIT_FROM_S && t <= ORBIT_TO_S) {
        double angle = degrees(axis, p.velocity());
        orbitMin = Math.min(orbitMin, angle);
        orbitMax = Math.max(orbitMax, angle);
      }
    }
    double median = timeCalls(below);
    worstVerticalDeg = Math.max(worstVerticalDeg, climbWorst);
    worstInertialDeg = Math.max(worstInertialDeg, aboveWorst);
    medianMicros.add(median);
    line("  vertical climb: worst axis-to-vertical %.4f deg", climbWorst);
    line(
        "  above %.0f km: worst axis-to-inertial %.3e deg",
        DrawnFlightAxis.INERTIAL_FLOOR_M / 1000.0, aboveWorst);
    line(
        "  in orbit (T+%.0f..%.0f): axis-to-inertial %.3e to %.3e deg",
        ORBIT_FROM_S, ORBIT_TO_S, orbitMin, orbitMax);
    line("  cost below the floor: median %.2f us per call over %d samples", median, below.size());
  }

  /** Median, over batches, of the mean cost of one call on the given samples, in microseconds. */
  private static double timeCalls(List<MissionEphemerisPoint> samples) {
    double[] perCall = new double[BATCHES];
    double sink = 0.0;
    for (int b = 0; b < BATCHES; b++) {
      long t0 = System.nanoTime();
      for (int i = 0; i < CALLS_PER_BATCH; i++) {
        sink += DrawnFlightAxis.of(samples.get(i % samples.size())).getX();
      }
      perCall[b] = (System.nanoTime() - t0) / 1e3 / CALLS_PER_BATCH;
    }
    if (Double.isNaN(sink)) {
      throw new IllegalStateException("a drawn axis came out NaN");
    }
    Arrays.sort(perCall);
    return perCall[BATCHES / 2];
  }

  private static double degrees(Vector3D a, Vector3D b) {
    return Math.toDegrees(Vector3D.angle(a, b));
  }

  private void line(String format, Object... args) {
    String text = String.format(Locale.ROOT, format, args);
    System.out.println(text);
    report.add(text);
  }

  private static Map<String, Object> values(LauncherModel launcher, Double inclination) {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "RND-12 drawn axis probe");
    values.put("LAUNCH_SITE_NAME", "Kourou - French Guiana");
    values.put("LAUNCH_SITE_LAT", 5.23);
    values.put("LAUNCH_SITE_LONG", -52.77);
    values.put("LAUNCH_SITE_ALT", 0.0);
    values.put("LAUNCHER_TYPE", launcher.id());
    values.put("PAYLOAD_TYPE", Payloads.EARTH_OBSERVATION_SAT.id());
    values.put("PAYLOAD_MASS", Payloads.EARTH_OBSERVATION_SAT.defaultDryMass());
    values.put("LEO_PERIGEE_ALT", 400.0);
    values.put("LEO_APOGEE_ALT", 400.0);
    if (inclination != null) {
      values.put("TARGET_INCLINATION", inclination);
    }
    return values;
  }
}
