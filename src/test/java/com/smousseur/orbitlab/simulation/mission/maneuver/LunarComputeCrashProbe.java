package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.LunarOrbitMission;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlan;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.scenario.ScenarioCodec;
import com.smousseur.orbitlab.simulation.mission.scenario.ScenarioMapper;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioFile;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioMission;
import com.smousseur.orbitlab.simulation.mission.stage.TLIBurnStage;
import com.smousseur.orbitlab.simulation.mission.window.problem.MissionScheduler;
import com.smousseur.orbitlab.ui.mission.wizard.WizardPrefill;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

/**
 * Probe: replays the production lunar compute on consecutive windows, each mission created the way
 * the wizard creates it — {@code MissionScheduler}, due east first and the free azimuth after.
 * Site, window count, search floor and profile come from the environment variables {@code
 * PROBE_SITE}, {@code PROBE_WINDOWS}, {@code PROBE_FLOOR} and {@code PROBE_TYPE} ({@code orbit} or
 * {@code flyby}; the build forwards no other system property than the probe switch). Every
 * computation is appended in full precision to {@code build/probe-l1-<site>.txt}, unchanged in
 * format so two runs can be compared bit for bit; the plane, the flown misalignment and the burn
 * centring at the injection and the scenario round trip go to {@code build/probe-l2-<site>.txt}.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
class LunarComputeCrashProbe {
  private static final Logger logger = LogManager.getLogger(LunarComputeCrashProbe.class);

  private static final int WINDOWS = Integer.parseInt(env("PROBE_WINDOWS", "5"));
  private static final String SITE = env("PROBE_SITE", "canaveral");
  private static final String FLOOR = env("PROBE_FLOOR", "2026-10-06T00:00:00Z");
  private static final boolean FLYBY = "flyby".equals(env("PROBE_TYPE", "orbit"));
  private static final String LABEL = SITE + (FLYBY ? "-flyby" : "");
  private static final Path OUT = Path.of("build/probe-tli-states.txt");
  private static final Path RECORD = Path.of("build/probe-l1-" + LABEL + ".txt");
  private static final Path PLANE_RECORD = Path.of("build/probe-l2-" + LABEL + ".txt");

  @Test
  void replay() throws IOException, ReflectiveOperationException {
    OrekitService.get().initialize();
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "probe");
    if ("kourou".equals(SITE)) {
      values.put("LAUNCH_SITE_NAME", "Kourou");
      values.put("LAUNCH_SITE_LAT", 5.236);
      values.put("LAUNCH_SITE_LONG", -52.775);
      values.put("LAUNCH_SITE_ALT", 0.0);
    } else {
      values.put("LAUNCH_SITE_NAME", "Cape Canaveral");
      values.put("LAUNCH_SITE_LAT", 28.562);
      values.put("LAUNCH_SITE_LONG", -80.577);
      values.put("LAUNCH_SITE_ALT", 3.0);
    }
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_MASS", 2_000.0);
    MissionType type;
    if (FLYBY) {
      values.put("PAYLOAD_TYPE", "LUNAR_PROBE");
      values.put("LUNAR_PERILUNE_ALT", 100.0);
      type = MissionType.LUNAR_FLYBY;
    } else {
      values.put("PAYLOAD_TYPE", "LUNAR_ORBITER");
      values.put("LUNAR_ORBIT_ALT", 100.0);
      type = MissionType.LUNAR_ORBIT;
    }

    AbsoluteDate floor = new AbsoluteDate(FLOOR, TimeScalesFactory.getUTC());
    for (int i = 0; i < WINDOWS; i++) {
      // The wizard's own creation path: the spec on no plane, the entry, then the schedule, which
      // re-specifies the entry when the free azimuth chose a plane.
      MissionEntry entry = new MissionEntry(MissionScheduler.unplannedSpec(values, type));
      long scheduling = System.nanoTime();
      MissionScheduler.Schedule schedule = MissionScheduler.schedule(entry, values, floor);
      double schedulingSeconds = (System.nanoTime() - scheduling) / 1e9;
      AbsoluteDate date = schedule.date();
      MissionSpec spec = entry.spec().orElseThrow();
      LaunchPlane plane = planeOf(spec);
      logger.info(
          "PROBE window {}: {} in {} s, plane {}, refusal {}",
          i,
          date,
          String.format(Locale.ROOT, "%.1f", schedulingSeconds),
          plane,
          schedule.refusal());
      Files.writeString(
          PLANE_RECORD,
          String.format(
              Locale.ROOT,
              "%s %d %s scheduled in %.1f s plane %s refusal %s scenario round trip %s%n",
              LABEL,
              i,
              date,
              schedulingSeconds,
              plane == null
                  ? "due east"
                  : String.format(
                      Locale.ROOT,
                      "i %.6f° %s (A %.4f°)",
                      plane.targetInclinationDeg(),
                      plane.nodeBranch(),
                      FastMath.toDegrees(plane.launchAzimuth(FastMath.toRadians(spec.latitude())))),
              schedule.refusal(),
              scenarioRoundTrip(entry, date)),
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);

      long started = System.nanoTime();
      try {
        MissionPlan plan = new MissionPlanOptimizer(entry, date).compute();
        double seconds = (System.nanoTime() - started) / 1e9;
        logger.info(
            "PROBE RESULT {} {}: OK in {} s", i, date, String.format(Locale.ROOT, "%.1f", seconds));
        record(i, date, "OK", seconds, describe(plan));
        Files.writeString(
            PLANE_RECORD,
            String.format(
                Locale.ROOT,
                "%s %d %s computed in %.1f s %s%n",
                LABEL,
                i,
                date,
                seconds,
                flown(plan)),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
      } catch (RuntimeException failure) {
        record(
            i,
            date,
            "FAILED",
            (System.nanoTime() - started) / 1e9,
            failure.getClass().getSimpleName() + " " + failure.getMessage());
        SpacecraftState at = entry.mission().getCurrentState();
        Vector3D p = at.getPosition();
        Vector3D v = at.getPVCoordinates().getVelocity();
        logger.info(
            "PROBE RESULT {} {}: FAILED {} — {} (in {} s); state {} in {} mass {}",
            i,
            date,
            failure.getClass().getSimpleName(),
            failure.getMessage(),
            String.format(Locale.ROOT, "%.1f", (System.nanoTime() - started) / 1e9),
            at.getDate(),
            at.getFrame().getName(),
            at.getMass());
        String line =
            String.format(
                Locale.ROOT,
                "%s %s %s %.6f %.6f %.6f %.9f %.9f %.9f %.6f %s%n",
                SITE,
                date,
                at.getDate(),
                p.getX(),
                p.getY(),
                p.getZ(),
                v.getX(),
                v.getY(),
                v.getZ(),
                at.getMass(),
                failure.getClass().getSimpleName());
        Files.writeString(OUT, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      }
      floor = date.shiftedBy(3_600.0);
    }
  }

  /** The burn the last pass flew, as the TLI stage resolved it at its entry. */
  private static TranslunarInjectionPlan.Burn burnOf(MissionPlan plan)
      throws ReflectiveOperationException {
    Mission mission = plan.computation().mission();
    MissionStage tli =
        mission.getStages().stream().filter(s -> s instanceof TLIBurnStage).findFirst().get();
    Field field = TLIBurnStage.class.getDeclaredField("burn");
    field.setAccessible(true);
    return (TranslunarInjectionPlan.Burn) field.get(tli);
  }

  private static String describe(MissionPlan plan) throws ReflectiveOperationException {
    TranslunarInjectionPlan.Burn burn = burnOf(plan);
    MissionEphemerisPoint last = plan.computation().ephemeris().lastPoint();
    return "burn dir "
        + exact(burn.direction())
        + " dt "
        + burn.duration()
        + " commanded "
        + burn.commandedDeltaV()
        + " endMass "
        + burn.endMass()
        + " impulsive "
        + exact(burn.plan().deltaV())
        + " aimOffset "
        + burn.plan().aimOffset()
        + " perilune "
        + burn.plan().perileneAltitude()
        + " | last "
        + last.time()
        + " pos "
        + exact(last.position())
        + " vel "
        + exact(last.velocity())
        + " mass "
        + last.mass()
        + " stage "
        + last.stageName()
        + " points "
        + plan.computation().ephemeris().size();
  }

  /**
   * The misalignment of the Moon at arrival from the plane actually flown, read at the TLI ignition
   * — the last sample of the parking coast — the way the window reads it on the plane it planned;
   * and how far past the injection point found from that ignition the burn is centred.
   */
  private static String flown(MissionPlan plan) throws ReflectiveOperationException {
    MissionEphemerisPoint ignition = null;
    for (MissionEphemerisPoint p : plan.computation().ephemeris().allPoints()) {
      if (LunarOrbitMission.PARKING_COAST_NAME.equals(p.stageName())) {
        ignition = p;
      }
    }
    if (ignition == null) {
      return "no parking coast sample";
    }
    SpacecraftState state =
        new SpacecraftState(
                new CartesianOrbit(
                    new TimeStampedPVCoordinates(
                        ignition.time(), ignition.position(), ignition.velocity()),
                    OrekitService.get().gcrf(),
                    Constants.WGS84_EARTH_MU))
            .withMass(ignition.mass());
    TranslunarInjectionPlan.Departure ahead = TranslunarInjectionPlan.departureFrom(state);
    TranslunarInjectionPlan.Burn burn = burnOf(plan);
    return String.format(
        Locale.ROOT,
        "flown β at TLI ignition %+.4f° (ignition %s, mass %.1f kg); burn %.3f s centred %+.3f s"
            + " past the injection point %.3f s ahead",
        FastMath.toDegrees(ahead.planeMisalignment()),
        ignition.time(),
        ignition.mass(),
        burn.duration(),
        0.5 * burn.duration() - ahead.coastDuration(),
        ahead.coastDuration());
  }

  /**
   * Saves the scheduled mission to a scenario text and reads it back: the same plane and the same
   * loads, or the save path dropped something.
   */
  private static boolean scenarioRoundTrip(MissionEntry entry, AbsoluteDate date) {
    entry.setScheduledDate(date);
    ScenarioMission saved =
        ScenarioMapper.toScenarioMission(entry, WizardPrefill.fromEntry(entry), null);
    String iso = TimeConverter.toUtcIsoString(date);
    ScenarioMission read =
        ScenarioCodec.read(
                ScenarioCodec.write(
                    new ScenarioFile(
                        ScenarioFile.CURRENT_FORMAT_VERSION, iso, iso, List.of(saved))))
            .missions()
            .getFirst();
    MissionSpec original = entry.spec().orElseThrow();
    MissionSpec restored =
        MissionFactory.specFromWizardValues(ScenarioMapper.toMissionValues(read), read.type());
    return Objects.equals(planeOf(original), planeOf(restored))
        && Arrays.equals(
            original.configuration().propellantLoads(), restored.configuration().propellantLoads());
  }

  private static LaunchPlane planeOf(MissionSpec spec) {
    return switch (spec) {
      case MissionSpec.Lunar lunar -> lunar.plane();
      case MissionSpec.LunarOrbit lunarOrbit -> lunarOrbit.plane();
      default -> null;
    };
  }

  private static String env(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String exact(Vector3D v) {
    return "(" + v.getX() + "; " + v.getY() + "; " + v.getZ() + ")";
  }

  private static void record(int i, AbsoluteDate date, String outcome, double seconds, String what)
      throws IOException {
    Files.writeString(
        RECORD,
        String.format(
            Locale.ROOT, "%s %d %s %s %.1f s %s%n", SITE, i, date, outcome, seconds, what),
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
  }
}
