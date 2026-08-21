package com.smousseur.orbitlab.simulation.mission.scenario;

import static org.junit.jupiter.api.Assertions.*;

import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizerResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionSolutions;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioFile;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioMission;
import com.smousseur.orbitlab.ui.mission.wizard.WizardPrefill;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/**
 * Capture and restore, with no disk and no JME: a session becomes a file, a file becomes missions,
 * and a mission this build cannot rebuild is set aside with its reason rather than taking the
 * others down with it.
 */
class ScenarioSessionTest {

  private static final String LAUNCH_DATE = "2030-03-01 12:00:00";
  private static final String CLOCK_DATE = "2030-02-28 05:30:00";

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static Map<String, Object> leoValues(String name) {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", name);
    values.put("LAUNCH_DATE", LAUNCH_DATE);
    values.put("LAUNCH_SITE_NAME", "Kourou - French Guiana");
    values.put("LAUNCH_SITE_LAT", 5.236);
    values.put("LAUNCH_SITE_LONG", -52.769);
    values.put("LAUNCH_SITE_ALT", 14.0);
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "EARTH_OBS_SAT");
    values.put("PAYLOAD_MASS", 8_000.0);
    values.put("LEO_PERIGEE_ALT", 400.0);
    values.put("LEO_APOGEE_ALT", 400.0);
    return values;
  }

  private static MissionEntry entry(String name, OptimizationType mode, ColorRGBA color) {
    MissionEntry entry =
        new MissionEntry(MissionFactory.specFromWizardValues(leoValues(name), MissionType.LEO));
    entry.setScheduledDate(TimeConverter.parseUtcDate(LAUNCH_DATE).orElseThrow());
    entry.setOptimizationType(mode);
    entry.setColor(color);
    entry.setVisible(true);
    return entry;
  }

  private static List<String> stageKeys(MissionEntry entry) {
    return entry.mission().getStages().stream()
        .filter(OptimizableMissionStage.class::isInstance)
        .map(stage -> ((OptimizableMissionStage<?>) stage).optimizationKey())
        .toList();
  }

  /** Fakes a completed optimization: only {@code bestVariables} is ever read back. */
  private static void fakeOptimizerResult(MissionEntry entry) {
    Map<String, OptimizationResult> results = new LinkedHashMap<>();
    double value = 0.1;
    for (String key : stageKeys(entry)) {
      results.put(key, new OptimizationResult(new double[] {value, value + 1}, 0.0, null, 42));
      value += 1.0;
    }
    entry.setOptimizerResult(new MissionOptimizerResult(results));
  }

  private static ScenarioFile capture(List<MissionEntry> entries) {
    return ScenarioSession.capture(
        entries, WizardPrefill::fromEntry, TimeConverter.parseUtcDate(CLOCK_DATE).orElseThrow());
  }

  @Test
  void capturesTheWholeSession() {
    ScenarioFile file =
        capture(
            List.of(
                entry("First", OptimizationType.BALANCED, ColorRGBA.Cyan),
                entry("Second", OptimizationType.FAST, ColorRGBA.Red)));

    assertEquals(ScenarioFile.CURRENT_FORMAT_VERSION, file.formatVersion());
    assertEquals("2030-02-28T05:30:00Z", file.clockDate());
    assertEquals(List.of("First", "Second"), file.missions().stream().map(ScenarioMission::name).toList());
    assertEquals("BALANCED", file.missions().getFirst().optimizationMode());
  }

  @Test
  void restoresWhatItCaptured() {
    MissionEntry saved = entry("First", OptimizationType.BALANCED, ColorRGBA.Cyan);

    ScenarioLoadReport report = ScenarioSession.restore(capture(List.of(saved)));

    assertFalse(report.hasRejections());
    MissionEntry restored = report.missions().getFirst();
    assertEquals("First", restored.mission().getName());
    assertEquals(OptimizationType.BALANCED, restored.getOptimizationType());
    assertTrue(restored.isVisible());
    assertEquals(ColorRGBA.Cyan, restored.getColor());
    assertEquals(
        0.0,
        restored.getScheduledDate().orElseThrow().durationFrom(saved.getScheduledDate().orElseThrow()),
        1e-6);
    assertArrayEquals(
        saved.spec().orElseThrow().configuration().propellantLoads(),
        restored.spec().orElseThrow().configuration().propellantLoads(),
        0.0,
        "the vehicle must not be resized by a save and a load");
  }

  /** Without it the screen is black: the orchestrator hides any mission launching after "now". */
  @Test
  void restoresTheClock() {
    ScenarioLoadReport report =
        ScenarioSession.restore(capture(List.of(entry("First", OptimizationType.FAST, null))));

    assertTrue(report.hasClockDate());
    AbsoluteDate expected = TimeConverter.parseUtcDate(CLOCK_DATE).orElseThrow();
    assertEquals(0.0, report.clockDate().durationFrom(expected), 1e-6);
  }

  @Test
  void solvedVectorsComeBackReadyToReplay() {
    MissionEntry saved = entry("First", OptimizationType.BALANCED, ColorRGBA.Cyan);
    fakeOptimizerResult(saved);

    MissionEntry restored = ScenarioSession.restore(capture(List.of(saved))).missions().getFirst();

    MissionSolutions solutions =
        restored.getPendingSolutions().orElseThrow(() -> new AssertionError("no solutions"));
    assertTrue(solutions.covers(restored.mission()));
    assertArrayEquals(
        saved.getOptimizerResult().orElseThrow().findFor(stageKeys(saved).getFirst()).orElseThrow()
            .bestVariables(),
        solutions.vectorFor(stageKeys(restored).getFirst()),
        0.0);
  }

  /**
   * The PRECISE case (§2.3): the loads a sizing sweep searched for are the ones that come back, not
   * the ones {@code PropellantBudget} would derive again today.
   */
  @Test
  void flownLoadsComeBackInsteadOfTheBudgetedOnes() {
    MissionEntry saved = entry("First", OptimizationType.PRECISE, ColorRGBA.Cyan);
    fakeOptimizerResult(saved);
    double[] budgeted = saved.spec().orElseThrow().configuration().propellantLoads();
    double[] flown = budgeted.clone();
    flown[0] *= 0.9;
    saved.setFlownLauncherLoads(flown);

    MissionEntry restored = ScenarioSession.restore(capture(List.of(saved))).missions().getFirst();

    MissionSolutions solutions = restored.getPendingSolutions().orElseThrow();
    assertTrue(solutions.hasLauncherLoads());
    assertArrayEquals(flown, solutions.launcherLoads(), 1e-9);
    assertArrayEquals(
        budgeted,
        restored.spec().orElseThrow().configuration().propellantLoads(),
        1e-9,
        "the spec keeps the budgeted loads; the flown ones ride in the solution");
  }

  /** Outside PRECISE nothing was searched, so nothing is remembered. */
  @Test
  void fixedLoadMission_carriesNoFlownLoads() {
    MissionEntry saved = entry("First", OptimizationType.BALANCED, ColorRGBA.Cyan);
    fakeOptimizerResult(saved);

    MissionEntry restored = ScenarioSession.restore(capture(List.of(saved))).missions().getFirst();

    assertFalse(restored.getPendingSolutions().orElseThrow().hasLauncherLoads());
  }

  /** A scenario of two missions with one broken brings back one, not zero (§7). */
  @Test
  void oneBrokenMissionDoesNotTakeTheOthersDown() {
    ScenarioFile captured =
        capture(
            List.of(
                entry("Good", OptimizationType.FAST, ColorRGBA.Cyan),
                entry("Broken", OptimizationType.FAST, ColorRGBA.Red)));
    List<ScenarioMission> missions = new ArrayList<>(captured.missions());
    missions.set(1, withUnknownLauncher((ScenarioMission.EarthOrbit) missions.get(1)));
    ScenarioFile file =
        new ScenarioFile(
            captured.formatVersion(), captured.savedAt(), captured.clockDate(), missions);

    ScenarioLoadReport report = ScenarioSession.restore(file);

    assertEquals(1, report.missions().size());
    assertEquals(1, report.rejections().size());
    assertEquals("Broken", report.rejections().getFirst().missionName());
    assertTrue(
        report.rejections().getFirst().reason().contains("LEFT_THE_CATALOG"),
        report.rejections().getFirst().reason());
  }

  private static ScenarioMission.EarthOrbit withUnknownLauncher(ScenarioMission.EarthOrbit mission) {
    return new ScenarioMission.EarthOrbit(
        mission.type(),
        mission.name(),
        mission.launchDate(),
        mission.site(),
        new com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioVehicle(
            "LEFT_THE_CATALOG", mission.vehicle().payloadId(), mission.vehicle().payloadDryMassKg()),
        mission.horizonDays(),
        mission.atmosphere(),
        mission.optimizationMode(),
        mission.color(),
        mission.visible(),
        mission.solution(),
        mission.perigeeKm(),
        mission.apogeeKm(),
        mission.inclinationDeg(),
        mission.raanDeg());
  }

  /** We do not know what we are reading, so nothing is salvaged from it. */
  @Test
  void futureFormatVersionIsRefusedWhole() {
    ScenarioFile captured = capture(List.of(entry("First", OptimizationType.FAST, null)));
    ScenarioFile fromTheFuture =
        new ScenarioFile(99, captured.savedAt(), captured.clockDate(), captured.missions());

    OrbitlabException failure =
        assertThrows(OrbitlabException.class, () -> ScenarioSession.restore(fromTheFuture));
    assertTrue(failure.getMessage().contains("99"), failure.getMessage());
  }

  /** A legacy entry has no wizard values to go back to; it is left out rather than failing the save. */
  @Test
  void legacyEntryIsLeftOutOfTheFile() {
    MissionEntry legacy =
        new MissionEntry(MissionFactory.fromWizardValues(leoValues("Legacy"), MissionType.LEO));

    ScenarioFile file = capture(List.of(legacy, entry("Good", OptimizationType.FAST, null)));

    assertEquals(List.of("Good"), file.missions().stream().map(ScenarioMission::name).toList());
  }
}
