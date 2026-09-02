package com.smousseur.orbitlab.simulation.mission.scenario;

import com.smousseur.orbitlab.app.OrekitTime;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionSolutions;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioFile;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioMission;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioSolution;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.time.AbsoluteDate;

/**
 * The meaning of a scenario, with no disk and no JME under it: a list of missions becomes a {@link
 * ScenarioFile}, and a {@link ScenarioFile} becomes a list of missions plus its rejections.
 *
 * <p>This is the single place where {@code ScenarioSolution} and {@code MissionSolutions} are put
 * face to face — one a file format that must not break, the other a type the optimization core owns
 * — because it is the only piece that depends on both the format and the replay.
 *
 * <p><b>It installs nothing.</b> {@link #restore(ScenarioFile)} <em>returns</em> entries; swapping
 * the session is the caller's business. That is what makes the file entirely converted before a
 * single current mission is destroyed, so a corrupt file never costs the session on screen (spec
 * {@code docs/scenario/01-persistance-missions.md} §6.3).
 */
public final class ScenarioSession {

  private static final Logger logger = LogManager.getLogger(ScenarioSession.class);

  private ScenarioSession() {}

  /**
   * Captures the current session.
   *
   * <p>The prefill is handed in rather than called directly: {@code WizardPrefill} is UI-layer, and
   * the whole point of going through the wizard values map is that the "absence is meaningful" rule
   * has one implementation (§4.2). The caller — the scenario {@code AppState} — supplies {@code
   * WizardPrefill::fromEntry}.
   *
   * <p>A mission carrying no spec is skipped rather than failing the save: a legacy entry has no
   * wizard values to go back to, which is also why the roster does not offer to edit it.
   *
   * @param entries the missions currently open, in roster order
   * @param prefill turns an entry into its wizard values, i.e. {@code WizardPrefill::fromEntry}
   * @param clockDate the simulation clock at save time, or {@code null}
   * @return the scenario to write
   */
  public static ScenarioFile capture(
      List<MissionEntry> entries,
      Function<MissionEntry, Map<String, Object>> prefill,
      AbsoluteDate clockDate) {
    Objects.requireNonNull(entries, "entries");
    Objects.requireNonNull(prefill, "prefill");

    List<ScenarioMission> missions = new ArrayList<>();
    for (MissionEntry entry : entries) {
      try {
        missions.add(
            ScenarioMapper.toScenarioMission(entry, prefill.apply(entry), solutionOf(entry)));
      } catch (RuntimeException e) {
        logger.warn(
            "Mission [{}] cannot be saved and is left out of the scenario: {}",
            entry.id().shortForm(),
            MissionEntry.describeFailure(e));
      }
    }
    return new ScenarioFile(
        ScenarioFile.CURRENT_FORMAT_VERSION,
        TimeConverter.toUtcIsoString(OrekitTime.utcNow()),
        clockDate == null ? null : TimeConverter.toUtcIsoString(clockDate),
        missions);
  }

  /**
   * Rebuilds the missions a scenario describes.
   *
   * <p>Every mission is tried on its own. One whose launcher left the catalog, whose inclination is
   * no longer reachable from its site, or whose atmosphere this build cannot fly is set aside with
   * its raw reason; the others come back (§7).
   *
   * @param file the scenario as read
   * @return the rebuilt missions, the clock to restore, and the rejections
   * @throws OrbitlabException if the file was written by a later format version
   */
  public static ScenarioLoadReport restore(ScenarioFile file) {
    Objects.requireNonNull(file, "file");
    if (file.formatVersion() > ScenarioFile.CURRENT_FORMAT_VERSION) {
      throw new OrbitlabException(
          "Scenario format version "
              + file.formatVersion()
              + " is newer than this build reads (version "
              + ScenarioFile.CURRENT_FORMAT_VERSION
              + ")");
    }

    List<MissionEntry> entries = new ArrayList<>();
    List<ScenarioLoadReport.Rejection> rejections = new ArrayList<>();
    for (ScenarioMission mission : file.missions()) {
      try {
        entries.add(restoreMission(mission));
      } catch (RuntimeException e) {
        rejections.add(
            new ScenarioLoadReport.Rejection(mission.name(), MissionEntry.describeFailure(e)));
        logger.warn("Mission '{}' was rejected: {}", mission.name(), e.getMessage(), e);
      }
    }
    return new ScenarioLoadReport(entries, clockDateOf(file), rejections);
  }

  private static MissionEntry restoreMission(ScenarioMission mission) {
    requireFlyableAtmosphere(mission);

    MissionSpec spec =
        MissionFactory.specFromWizardValues(
            ScenarioMapper.toMissionValues(mission), mission.type());
    MissionEntry entry = new MissionEntry(spec);
    // Before anything derived is posted: the mode recomposes, and a recomposition drops everything
    // the previous composition produced — the pending solutions included.
    entry.setOptimizationType(optimizationMode(mission));
    if (mission.launchDate() != null) {
      entry.setScheduledDate(
          TimeConverter.parseUtcDate(mission.launchDate())
              .orElseThrow(
                  () ->
                      new OrbitlabException(
                          "Launch date is not a UTC date: " + mission.launchDate())));
    }
    entry.setColor(ScenarioMapper.fromHex(mission.color()));
    entry.setVisible(mission.visible());
    applySolution(entry, mission);
    return entry;
  }

  /**
   * Posts the solutions to replay, if and only if they describe exactly the composition just built.
   *
   * <p>A mismatch is not a rejection: the mission is perfectly valid, it simply arrives in {@code
   * DRAFT} and is recomputed by an ordinary {@code OPTIMIZE} (§5.1). Replaying the stages that
   * happen to match would fly a trajectory nobody asked for.
   */
  private static void applySolution(MissionEntry entry, ScenarioMission mission) {
    ScenarioSolution solution = mission.solution();
    if (solution == null || solution.vectors() == null || solution.vectors().isEmpty()) {
      return;
    }
    MissionSolutions solutions = new MissionSolutions(solution.vectors(), solution.launcherLoads());
    if (!solutions.covers(entry.mission())) {
      logger.info(
          "Mission '{}' was saved with another stage composition; it will be optimized rather than"
              + " replayed",
          mission.name());
      return;
    }
    entry.setPendingSolutions(solutions);
    // Kept as well, so saving a loaded mission again writes the vehicle that flew rather than the
    // one PropellantBudget would rebuild from the spec.
    entry.setFlownLauncherLoads(solution.launcherLoads());
  }

  /**
   * Refuses a mission asking for a physics this build cannot mount.
   *
   * <p>The atmosphere is carried by the format from v1 and applied by none of it: {@code
   * MissionFactory} reads no such key, and no form field faces it before PHY-2 (§1.5). Rebuilding a
   * mission that asked for drag as a vacuum mission would replay it under another physics with
   * nothing to show for it, which is exactly what the field exists to prevent.
   */
  private static void requireFlyableAtmosphere(ScenarioMission mission) {
    String name = mission.atmosphere();
    if (name == null || name.isBlank()) {
      return;
    }
    AtmosphereModel model;
    try {
      model = AtmosphereModel.valueOf(name);
    } catch (IllegalArgumentException e) {
      throw new OrbitlabException("Unknown atmosphere model: " + name, e);
    }
    if (model != AtmosphereModel.NONE) {
      throw new OrbitlabException(
          "Atmosphere " + model + " cannot be restored yet: no wizard field carries it");
    }
  }

  private static OptimizationType optimizationMode(ScenarioMission mission) {
    String name = mission.optimizationMode();
    if (name == null || name.isBlank()) {
      return OptimizationType.FAST;
    }
    try {
      return OptimizationType.valueOf(name);
    } catch (IllegalArgumentException e) {
      throw new OrbitlabException("Unknown optimization mode: " + name, e);
    }
  }

  /** The solutions to write for this mission, or {@code null} when it never flew. */
  private static ScenarioSolution solutionOf(MissionEntry entry) {
    return entry
        .getOptimizerResult()
        .map(result -> MissionSolutions.from(result, entry.getFlownLauncherLoads()))
        .map(solutions -> new ScenarioSolution(solutions.vectors(), solutions.launcherLoads()))
        .orElse(null);
  }

  /**
   * The clock the file asks for, or {@code null} when it carries none this build can read — the
   * caller then keeps the clock it has rather than jumping to a date nobody chose.
   */
  private static AbsoluteDate clockDateOf(ScenarioFile file) {
    if (file.clockDate() == null) {
      return null;
    }
    AbsoluteDate clockDate = TimeConverter.parseUtcDate(file.clockDate()).orElse(null);
    if (clockDate == null) {
      logger.warn(
          "Scenario clock date is unreadable, keeping the current clock: {}", file.clockDate());
    }
    return clockDate;
  }
}
