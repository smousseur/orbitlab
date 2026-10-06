package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.problem.LunarLaunchWindowPlanner;
import java.io.IOException;
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

/** Probe: replays the production lunar-orbit compute on consecutive windows. Not committed. */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class LunarComputeCrashProbe {
  private static final Logger logger = LogManager.getLogger(LunarComputeCrashProbe.class);

  private static final int WINDOWS = 1;
  private static final String SITE = "kourou";
  private static final Path OUT = Path.of("build/probe-tli-states.txt");

  @Test
  void replay() throws IOException {
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

    AbsoluteDate floor = new AbsoluteDate(2026, 10, 6, 8, 0, 0.0, TimeScalesFactory.getUTC());
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
        new MissionPlanOptimizer(entry, date).compute();
        logger.info(
            "PROBE RESULT {} {}: OK in {} s",
            i,
            date,
            String.format(Locale.ROOT, "%.1f", (System.nanoTime() - started) / 1e9));
      } catch (RuntimeException failure) {
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
}
