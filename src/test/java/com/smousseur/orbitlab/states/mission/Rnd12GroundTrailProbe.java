package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.DebrisTrack;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
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
 * The ribbon production flights are drawn with near the ground, against the three rules fixed
 * before this ran. NOT a gate: it asserts nothing. Run with {@code -Dorbitlab.probe=true --tests
 * '*Rnd12GroundTrailProbe*'}; the report is also written to {@code build/rnd12-ground-trail.txt}.
 *
 * <p>Three Earth orbits from Kourou, built from wizard values and computed by {@code
 * MissionPlanOptimizer.compute()} in FAST: a Falcon Heavy due east, an Ariane 64 on a commanded
 * 97.5° plane, and a Falcon Heavy whose payload is deorbited. Each ribbon is rebuilt the way {@link
 * MissionTrajectoryRenderer} draws it — every vertex moved by {@link CoRotatingTrail} — and ends on
 * the object's raw sample. Left out: the render-only stack seat, which lies along the nose and can
 * only narrow the angle measured, and the 5 km ground correction, which needs the drawn globe and
 * is the same with and without the carried ribbon. "before" is the ribbon as drawn without the
 * carried part: the inertial one, against the same nose.
 *
 * <p>Rules: <b>V1</b> while the primary climbs below the air's ceiling, the last drawn segment of
 * its ribbon lies within half the nose's turn over that segment, plus {@value #V1_MARGIN_DEG}°, of
 * the nose; falls are measured without a verdict. A straight segment between two samples of a
 * turning path follows its mean direction, half its turn off the tangent at either end for an arc
 * of circle: at the pitch-over, where the nose turns up to 4.9° between two samples 2 s apart, the
 * segment cannot follow it closer, however exactly the ribbon is carried with the ground. The rule
 * first fixed, {@value #V1_INITIAL_MAX_DEG}° flat, assumed 1 s segments and 1°/s; it is still
 * reported, as measured against. <b>V2</b> with the object above the inertial floor, no vertex
 * moves at all. <b>V3</b> a ribbon is prepared in at most {@value #V3_PREPARE_MAX_MS} ms, and one
 * frame of the primary's ribbon, at its most carried moment, costs at most {@value
 * #V3_FRAME_MAX_MS} ms.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class Rnd12GroundTrailProbe {

  private static final double V1_MARGIN_DEG = 0.1;
  private static final double V1_INITIAL_MAX_DEG = 2.0;
  private static final double V3_PREPARE_MAX_MS = 16.0;
  private static final double V3_FRAME_MAX_MS = 0.2;

  private static final double CLIMB_STEP_S = 0.5;
  private static final double FALL_STEP_S = 1.0;
  private static final double SWEEP_STEP_S = 10.0;
  private static final double MIN_SEGMENT_M = 1.0;
  private static final double OLD_VERTEX_S = 3_600.0;
  private static final double TEAR_M = 10_000.0;
  private static final double FRAME_S = 1.0 / 60.0;
  private static final double[] OFFSETS_S = {
    1, 5, 10, 20, 30, 45, 60, 90, 120, 150, 165, 180, 240, 300, 400, 500, 600
  };
  private static final int PREPARE_RUNS = 7;
  private static final int BATCHES = 21;
  private static final int FRAMES_PER_BATCH = 200;

  private static AbsoluteDate epoch;

  private final List<String> report = new ArrayList<>();
  private double worstClimbDeg;
  private double worstClimbBeforeDeg;
  private double worstClimbExcessDeg = Double.NEGATIVE_INFINITY;
  private double worstAboveFloorM;
  private long aboveFloorSamples;
  private double worstColdPrepareMs;
  private double worstWarmPrepareMs;
  private double worstFrameMs;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    OrekitService.get().warmUpFrames();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  void groundTrailOfProductionFlights() throws IOException {
    fly("FH LEO 400 due east", values(Launchers.FALCON_HEAVY, null, false));
    fly("A64 LEO 400 i=97.5", values(Launchers.ARIANE_64, 97.5, false));
    fly("FH LEO 400 deorbit", values(Launchers.FALCON_HEAVY, null, true));

    line("%n=== VERDICTS");
    line(
        "  V1 climb below %.0f km: last-segment-to-nose minus half the nose's turn over it, worst"
            + " %.3f deg (rule <= %.1f) -> %s",
        DrawnFlightAxis.AIR_CEILING_M / 1000.0,
        worstClimbExcessDeg,
        V1_MARGIN_DEG,
        worstClimbExcessDeg <= V1_MARGIN_DEG ? "PASS" : "FAIL");
    line(
        "     rule first fixed: worst last-segment-to-nose %.3f deg (before: %.3f) (rule <= %.1f)"
            + " -> %s, re-fixed after measuring the cause",
        worstClimbDeg,
        worstClimbBeforeDeg,
        V1_INITIAL_MAX_DEG,
        worstClimbDeg <= V1_INITIAL_MAX_DEG ? "PASS" : "FAIL");
    line(
        "  V2 above %.0f km: %d samples, worst displacement %.3e m (rule: exactly 0) -> %s",
        DrawnFlightAxis.INERTIAL_FLOOR_M / 1000.0,
        aboveFloorSamples,
        worstAboveFloorM,
        worstAboveFloorM == 0.0 ? "PASS" : "FAIL");
    line(
        "  V3 preparation: worst %.3f ms cold, %.3f ms warm median (rule <= %.0f) -> %s",
        worstColdPrepareMs,
        worstWarmPrepareMs,
        V3_PREPARE_MAX_MS,
        worstColdPrepareMs <= V3_PREPARE_MAX_MS ? "PASS" : "FAIL");
    line(
        "  V3 frame: worst median %.4f ms per frame (rule <= %.1f) -> %s",
        worstFrameMs, V3_FRAME_MAX_MS, worstFrameMs <= V3_FRAME_MAX_MS ? "PASS" : "FAIL");

    Path out = Path.of("build/rnd12-ground-trail.txt");
    Files.createDirectories(out.getParent());
    Files.write(out, report, StandardCharsets.UTF_8);
  }

  private void fly(String label, Map<String, Object> values) {
    MissionEntry entry =
        new MissionEntry(MissionFactory.specFromWizardValues(values, MissionType.LEO));
    if (entry.getOptimizationType() != OptimizationType.FAST) {
      throw new IllegalStateException("the entry composed " + entry.getOptimizationType());
    }
    MissionComputeResult result = new MissionPlanOptimizer(entry, epoch).compute().computation();
    MissionEphemeris primary = result.ephemeris();
    line(
        "%n=== %s: %.0f s of flight, %d ribbon vertices, %d debris",
        label,
        primary.endDate().durationFrom(primary.startDate()),
        primary.displayTrail().size(),
        result.debris().size());

    CoRotatingTrail primaryTrail = prepare("primary", primary.displayTrail());
    climb(primary, primaryTrail);
    if (primary.lastPoint().altitudeMeters() < DrawnFlightAxis.AIR_CEILING_M) {
      fall("primary", primary, primaryTrail, firstAbove(primary, DrawnFlightAxis.INERTIAL_FLOOR_M));
    }
    aboveFloor("primary", primary, primaryTrail);
    for (DebrisTrack track : result.debris()) {
      String name = track.role() + " #" + track.exemplarIndex();
      MissionEphemeris debris = track.ephemeris();
      CoRotatingTrail debrisTrail = prepare(name, debris.displayTrail());
      fall(name, debris, debrisTrail, debris.startDate());
      aboveFloor(name, debris, debrisTrail);
    }
    frameCost(primary, primaryTrail);
  }

  /** The climb below the air's ceiling, then the crossing of the ramp, of the primary. */
  private void climb(MissionEphemeris ephemeris, CoRotatingTrail carried) {
    TrajectoryPolyline trail = ephemeris.displayTrail();
    AbsoluteDate start = ephemeris.startDate();
    line("  climb:");
    line(
        "  %7s %-22s %8s %6s | %9s %7s %9s %9s %9s | %9s",
        "T+", "stage", "alt km", "g", "seg m", "age s", "before", "after", "turn", "pad km");
    for (double offset : OFFSETS_S) {
      MissionEphemerisPoint p = ephemeris.displayPointAt(start.shiftedBy(offset));
      Segment s = lastSegment(ephemeris, trail, carried, p);
      if (s == null) {
        line("  %7.0f no segment longer than %.0f m yet", offset, MIN_SEGMENT_M);
        continue;
      }
      line(
          "  %7.0f %-22s %8.3f %6.3f | %9.1f %7.2f %9.3f %9.3f %9.3f | %9.3f",
          offset,
          p.stageName(),
          p.altitudeMeters() / 1000.0,
          CoRotatingTrail.groundFollowing(p),
          s.lengthM(),
          s.ageS(),
          s.beforeDeg(),
          s.afterDeg(),
          s.turnDeg(),
          padDisplacement(carried, p) / 1000.0);
    }

    List<Double> after = new ArrayList<>();
    double worst = 0.0;
    double worstBefore = 0.0;
    double worstAt = 0.0;
    double worstTurn = 0.0;
    double worstExcess = Double.NEGATIVE_INFINITY;
    double t = CLIMB_STEP_S;
    MissionEphemerisPoint p = ephemeris.displayPointAt(start.shiftedBy(t));
    while (p.altitudeMeters() < DrawnFlightAxis.AIR_CEILING_M) {
      Segment s = lastSegment(ephemeris, trail, carried, p);
      if (s != null) {
        after.add(s.afterDeg());
        worstBefore = Math.max(worstBefore, s.beforeDeg());
        worstExcess = Math.max(worstExcess, s.afterDeg() - s.turnDeg() / 2.0);
        if (s.afterDeg() > worst) {
          worst = s.afterDeg();
          worstAt = t;
          worstTurn = s.turnDeg();
        }
      }
      t += CLIMB_STEP_S;
      p = ephemeris.displayPointAt(start.shiftedBy(t));
    }
    double ceilingAt = t;
    worstClimbDeg = Math.max(worstClimbDeg, worst);
    worstClimbBeforeDeg = Math.max(worstClimbBeforeDeg, worstBefore);
    worstClimbExcessDeg = Math.max(worstClimbExcessDeg, worstExcess);
    line(
        "  climb below %.0f km (T+%.1f..%.1f, %d samples): after worst %.3f deg at T+%.1f"
            + " (nose turned %.3f deg over that segment), median %.3f; before worst %.3f deg;"
            + " after minus half the turn at most %.3f deg",
        DrawnFlightAxis.AIR_CEILING_M / 1000.0,
        CLIMB_STEP_S,
        ceilingAt - CLIMB_STEP_S,
        after.size(),
        worst,
        worstAt,
        worstTurn,
        median(after),
        worstBefore,
        worstExcess);

    double padMax = 0.0;
    double slideMax = 0.0;
    double stretchMax = 0.0;
    double previous = padDisplacement(carried, p);
    double span = ephemeris.endDate().durationFrom(start);
    while (CoRotatingTrail.groundFollowing(p) > 0.0 && t < span) {
      t += 1.0;
      p = ephemeris.displayPointAt(start.shiftedBy(t));
      double pad = padDisplacement(carried, p);
      padMax = Math.max(padMax, pad);
      slideMax = Math.max(slideMax, Math.abs(pad - previous));
      stretchMax = Math.max(stretchMax, stretch(trail, carried, p, 0.0).stretchM());
      previous = pad;
    }
    line(
        "  ramp crossing T+%.0f..T+%.0f: pad-end displacement up to %.1f km, sliding at most %.3f"
            + " km/s, a segment stretched by at most %.1f km",
        ceilingAt, t, padMax / 1000.0, slideMax / 1000.0, stretchMax / 1000.0);
  }

  /** An object's descent below the air's ceiling, from {@code from} to its last sample. */
  private void fall(
      String name, MissionEphemeris ephemeris, CoRotatingTrail carried, AbsoluteDate from) {
    TrajectoryPolyline trail = ephemeris.displayTrail();
    List<Double> after = new ArrayList<>();
    double worst = 0.0;
    double worstBefore = 0.0;
    double worstAltKm = 0.0;
    double oldMax = 0.0;
    double stretchMax = 0.0;
    double worstTurn = 0.0;
    int torn = 0;
    double span = ephemeris.endDate().durationFrom(from);
    for (double t = 0.0; t < span; t += FALL_STEP_S) {
      MissionEphemerisPoint p = ephemeris.displayPointAt(from.shiftedBy(t));
      boolean descending = Vector3D.dotProduct(p.position(), p.velocity()) < 0.0;
      if (!descending
          || p.arc().body() != SolarSystemBody.EARTH
          || p.altitudeMeters() >= DrawnFlightAxis.AIR_CEILING_M) {
        continue;
      }
      Segment s = lastSegment(ephemeris, trail, carried, p);
      if (s == null) {
        continue;
      }
      after.add(s.afterDeg());
      worstBefore = Math.max(worstBefore, s.beforeDeg());
      if (s.afterDeg() > worst) {
        worst = s.afterDeg();
        worstAltKm = p.altitudeMeters() / 1000.0;
        worstTurn = s.turnDeg();
      }
      Stretch stretch = stretch(trail, carried, p, OLD_VERTEX_S);
      oldMax = Math.max(oldMax, stretch.oldDisplacementM());
      stretchMax = Math.max(stretchMax, stretch.stretchM());
      if (stretch.stretchM() > TEAR_M) {
        torn++;
      }
    }
    if (after.isEmpty()) {
      line(
          "  fall %-14s: no descending sample below %.0f km",
          name, DrawnFlightAxis.AIR_CEILING_M / 1000.0);
      return;
    }
    line(
        "  fall %-14s (%4d samples): after worst %7.3f deg at %6.2f km (turn %.3f), median"
            + " %6.3f; before worst %7.3f deg; vertices older than %.0f s moved up to %.1f km; a"
            + " segment stretched by at most %.1f km, by more than %.0f km on %d samples",
        name,
        after.size(),
        worst,
        worstAltKm,
        worstTurn,
        median(after),
        worstBefore,
        OLD_VERTEX_S,
        oldMax / 1000.0,
        stretchMax / 1000.0,
        TEAR_M / 1000.0,
        torn);
  }

  /** V2: with the object above the floor, nothing the renderer draws moves. */
  private void aboveFloor(String name, MissionEphemeris ephemeris, CoRotatingTrail carried) {
    TrajectoryPolyline trail = ephemeris.displayTrail();
    AbsoluteDate start = ephemeris.startDate();
    double span = ephemeris.endDate().durationFrom(start);
    long samples = 0;
    double worst = 0.0;
    for (double t = 0.0; t <= span; t += SWEEP_STEP_S) {
      MissionEphemerisPoint p = ephemeris.displayPointAt(start.shiftedBy(t));
      if (p.arc().body() != SolarSystemBody.EARTH
          || p.altitudeMeters() <= DrawnFlightAxis.INERTIAL_FLOOR_M) {
        continue;
      }
      samples++;
      CoRotatingTrail.Frame frame = carried.at(p.time(), CoRotatingTrail.groundFollowing(p));
      if (frame != null) {
        for (int i = 0; i <= trail.indexUpTo(p.time()); i++) {
          worst = Math.max(worst, frame.displacementAt(i).getNorm());
        }
      }
    }
    aboveFloorSamples += samples;
    worstAboveFloorM = Math.max(worstAboveFloorM, worst);
    line("  above floor %-14s: %d samples, worst displacement %.3e m", name, samples, worst);
  }

  /** V3: how long a ribbon takes to prepare — the first time, then once Orekit has it cached. */
  private CoRotatingTrail prepare(String name, TrajectoryPolyline trail) {
    double[] runs = new double[PREPARE_RUNS];
    CoRotatingTrail carried = null;
    for (int r = 0; r < PREPARE_RUNS; r++) {
      long t0 = System.nanoTime();
      carried = new CoRotatingTrail(trail);
      runs[r] = (System.nanoTime() - t0) / 1e6;
    }
    double cold = runs[0];
    double[] warm = Arrays.copyOfRange(runs, 1, PREPARE_RUNS);
    Arrays.sort(warm);
    double warmMedian = warm[warm.length / 2];
    worstColdPrepareMs = Math.max(worstColdPrepareMs, cold);
    worstWarmPrepareMs = Math.max(worstWarmPrepareMs, warmMedian);
    line(
        "  prepare %-14s: %5d vertices, %.3f ms cold, %.3f ms warm median",
        name, trail.size(), cold, warmMedian);
    return carried;
  }

  /**
   * V3: one frame's carried part of the primary's ribbon — the transform to now and a displacement
   * per drawn vertex — at its most carried moments, each frame a new date as the clock runs.
   */
  private void frameCost(MissionEphemeris ephemeris, CoRotatingTrail carried) {
    List<AbsoluteDate> moments = new ArrayList<>();
    AbsoluteDate floor = firstAbove(ephemeris, DrawnFlightAxis.INERTIAL_FLOOR_M);
    moments.add(ephemeris.startDate().shiftedBy(60.0));
    moments.add(floor.shiftedBy(-1.0));
    if (ephemeris.lastPoint().altitudeMeters() < DrawnFlightAxis.AIR_CEILING_M) {
      moments.add(ephemeris.endDate().shiftedBy(-1.0));
    }
    TrajectoryPolyline trail = ephemeris.displayTrail();
    for (AbsoluteDate moment : moments) {
      MissionEphemerisPoint p = ephemeris.displayPointAt(moment);
      double g = CoRotatingTrail.groundFollowing(p);
      int last = trail.indexUpTo(moment);
      double[] perFrame = new double[BATCHES];
      double sink = 0.0;
      AbsoluteDate now = moment;
      for (int b = 0; b < BATCHES; b++) {
        long t0 = System.nanoTime();
        for (int f = 0; f < FRAMES_PER_BATCH; f++) {
          now = now.shiftedBy(FRAME_S);
          CoRotatingTrail.Frame frame = carried.at(now, g);
          if (frame != null) {
            for (int i = 0; i <= last; i++) {
              sink += frame.displacementAt(i).getX();
            }
          }
        }
        perFrame[b] = (System.nanoTime() - t0) / 1e6 / FRAMES_PER_BATCH;
        now = moment;
      }
      if (Double.isNaN(sink)) {
        throw new IllegalStateException("a displacement came out NaN");
      }
      Arrays.sort(perFrame);
      double median = perFrame[BATCHES / 2];
      worstFrameMs = Math.max(worstFrameMs, median);
      line(
          "  frame at T+%.0f (alt %.1f km, g %.3f, %d vertices drawn): median %.4f ms",
          moment.durationFrom(ephemeris.startDate()),
          p.altitudeMeters() / 1000.0,
          g,
          last + 1,
          median);
    }
  }

  private record Segment(
      double afterDeg, double beforeDeg, double lengthM, double ageS, double turnDeg) {}

  /**
   * The last drawn segment of the ribbon, the one ending on the object: from the last vertex flown
   * that lies more than {@value #MIN_SEGMENT_M} m off the tip.
   */
  private static Segment lastSegment(
      MissionEphemeris ephemeris,
      TrajectoryPolyline trail,
      CoRotatingTrail carried,
      MissionEphemerisPoint p) {
    AbsoluteDate now = p.time();
    CoRotatingTrail.Frame frame = carried.at(now, CoRotatingTrail.groundFollowing(p));
    Vector3D nose = DrawnFlightAxis.of(p);
    Vector3D tip = MissionRenderer.renderPositionOf(p, SolarSystemBody.EARTH);
    for (int j = trail.indexUpTo(now); j >= 0; j--) {
      Vector3D chord = tip.subtract(drawn(trail, frame, j));
      if (chord.getNorm() > MIN_SEGMENT_M) {
        Vector3D inertial = tip.subtract(trail.positionAt(j, SolarSystemBody.EARTH));
        Vector3D noseThen = DrawnFlightAxis.of(ephemeris.displayPointAt(trail.timeAt(j)));
        return new Segment(
            degrees(chord, nose),
            degrees(inertial, nose),
            chord.getNorm(),
            now.durationFrom(trail.timeAt(j)),
            degrees(noseThen, nose));
      }
    }
    return null;
  }

  private record Stretch(double stretchM, double oldDisplacementM) {}

  /**
   * How much longer a drawn segment is than its inertial one, at worst, and how far the vertices
   * flown more than {@code oldAfterS} ago are moved; zero {@code oldAfterS} counts none as old.
   */
  private static Stretch stretch(
      TrajectoryPolyline trail,
      CoRotatingTrail carried,
      MissionEphemerisPoint p,
      double oldAfterS) {
    AbsoluteDate now = p.time();
    CoRotatingTrail.Frame frame = carried.at(now, CoRotatingTrail.groundFollowing(p));
    if (frame == null) {
      return new Stretch(0.0, 0.0);
    }
    int last = trail.indexUpTo(now);
    double stretch = 0.0;
    double old = 0.0;
    Vector3D previousDrawn = drawn(trail, frame, 0);
    Vector3D previousInertial = trail.positionAt(0, SolarSystemBody.EARTH);
    for (int i = 0; i <= last; i++) {
      Vector3D inertial = trail.positionAt(i, SolarSystemBody.EARTH);
      Vector3D displacement = frame.displacementAt(i);
      Vector3D drawn = inertial.add(displacement);
      stretch =
          Math.max(stretch, drawn.distance(previousDrawn) - inertial.distance(previousInertial));
      if (oldAfterS > 0.0 && now.durationFrom(trail.timeAt(i)) > oldAfterS) {
        old = Math.max(old, displacement.getNorm());
      }
      previousDrawn = drawn;
      previousInertial = inertial;
    }
    return new Stretch(stretch, old);
  }

  private static Vector3D drawn(TrajectoryPolyline trail, CoRotatingTrail.Frame frame, int i) {
    Vector3D inertial = trail.positionAt(i, SolarSystemBody.EARTH);
    return frame == null ? inertial : inertial.add(frame.displacementAt(i));
  }

  private static double padDisplacement(CoRotatingTrail carried, MissionEphemerisPoint p) {
    CoRotatingTrail.Frame frame = carried.at(p.time(), CoRotatingTrail.groundFollowing(p));
    return frame == null ? 0.0 : frame.displacementAt(0).getNorm();
  }

  private static AbsoluteDate firstAbove(MissionEphemeris ephemeris, double altitude) {
    for (MissionEphemerisPoint p : ephemeris.allPoints()) {
      if (p.altitudeMeters() >= altitude) {
        return p.time();
      }
    }
    return ephemeris.endDate();
  }

  private static double median(List<Double> values) {
    double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    return sorted.length == 0 ? 0.0 : sorted[sorted.length / 2];
  }

  private static double degrees(Vector3D a, Vector3D b) {
    return Math.toDegrees(Vector3D.angle(a, b));
  }

  private void line(String format, Object... args) {
    String text = String.format(Locale.ROOT, format, args);
    System.out.println(text);
    report.add(text);
  }

  private static Map<String, Object> values(
      LauncherModel launcher, Double inclination, boolean deorbit) {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "RND-12 ground trail probe");
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
    if (deorbit) {
      values.put("DEORBIT", Boolean.TRUE);
    }
    return values;
  }
}
