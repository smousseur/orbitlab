package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlan;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.stage.TLIBurnStage;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.problem.LunarLaunchWindowPlanner;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * Probe: replays the production lunar-orbit compute on consecutive windows. Site, window count and
 * search floor come from the environment variables {@code PROBE_SITE}, {@code PROBE_WINDOWS} and
 * {@code PROBE_FLOOR} (the build forwards no other system property than the probe switch); every
 * outcome is appended in full precision to {@code build/probe-l1-<site>.txt} so two runs can be
 * compared bit for bit.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
class LunarComputeCrashProbe {
  private static final Logger logger = LogManager.getLogger(LunarComputeCrashProbe.class);

  private static final int WINDOWS = Integer.parseInt(env("PROBE_WINDOWS", "5"));
  private static final String SITE = env("PROBE_SITE", "canaveral");
  private static final String FLOOR = env("PROBE_FLOOR", "2026-10-06T00:00:00Z");
  private static final Path OUT = Path.of("build/probe-tli-states.txt");
  private static final Path RECORD = Path.of("build/probe-l1-" + SITE + ".txt");

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
    values.put("PAYLOAD_TYPE", "LUNAR_ORBITER");
    values.put("PAYLOAD_MASS", 2_000.0);
    values.put("LUNAR_ORBIT_ALT", 100.0);

    AbsoluteDate floor = new AbsoluteDate(FLOOR, TimeScalesFactory.getUTC());
    for (int i = 0; i < WINDOWS; i++) {
      MissionSpec spec = MissionFactory.specFromWizardValues(values, MissionType.LUNAR_ORBIT);
      Optional<LaunchWindow> window =
          LunarLaunchWindowPlanner.nextOpportunity((MissionSpec.LunarOrbit) spec, floor);
      AbsoluteDate date;
      if (window.isPresent()) {
        date = window.get().date();
        logger.info(
            "PROBE window {}: {} at {} m/s", i, date, Math.round(window.get().best().deltaV()));
      } else {
        date = floor;
        logger.info("PROBE window {}: none, keeping requested {}", i, floor);
      }

      MissionEntry entry = new MissionEntry(spec);
      long started = System.nanoTime();
      try {
        MissionPlan plan = new MissionPlanOptimizer(entry, date).compute();
        double seconds = (System.nanoTime() - started) / 1e9;
        logger.info(
            "PROBE RESULT {} {}: OK in {} s", i, date, String.format(Locale.ROOT, "%.1f", seconds));
        record(i, date, "OK", seconds, describe(plan));
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

  private static String describe(MissionPlan plan) throws ReflectiveOperationException {
    Mission mission = plan.computation().mission();
    MissionStage tli =
        mission.getStages().stream().filter(s -> s instanceof TLIBurnStage).findFirst().get();
    Field field = TLIBurnStage.class.getDeclaredField("burn");
    field.setAccessible(true);
    TranslunarInjectionPlan.Burn burn = (TranslunarInjectionPlan.Burn) field.get(tli);
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
