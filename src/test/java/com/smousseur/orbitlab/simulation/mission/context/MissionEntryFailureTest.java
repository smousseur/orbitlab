package com.smousseur.orbitlab.simulation.mission.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.runtime.AchievedOrbit;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionPerformanceReport;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * What a mission entry must remember about a computation, and what it must forget when the
 * composition is replaced (spec {@code specs/mission-detail/01-vue-detail.md} section 3).
 */
class MissionEntryFailureTest {

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static MissionSpec.Leo leoSpec() {
    return new MissionSpec.Leo(
        "Detail view fixture",
        LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY),
        400_000.0,
        400_000.0,
        "Kourou",
        5.23,
        -52.77,
        0.0,
        null);
  }

  private static MissionPerformanceReport emptyReport() {
    return new MissionPerformanceReport(List.of(), 0.0, 0.0, 0.0);
  }

  @Test
  void describeFailure_usesSimpleNameAndMessage() {
    assertEquals(
        "IllegalStateException: mass becomes negative",
        MissionEntry.describeFailure(new IllegalStateException("mass becomes negative")));
  }

  @Test
  void describeFailure_fallsBackToToStringWhenMessageIsNull() {
    // Orekit rebuilds exceptions without a message often enough that "SimpleName: null" would be a
    // routine sight in the UI.
    String described = MissionEntry.describeFailure(new IllegalStateException());
    assertFalse(
        described.contains("null"), () -> "must not surface a null message, got " + described);
    assertTrue(described.contains("IllegalStateException"));
  }

  @Test
  void lastError_roundTripsAndClears() {
    MissionEntry entry = new MissionEntry(leoSpec());
    assertTrue(entry.getLastError().isEmpty(), "a fresh entry has no error");

    entry.setLastError("OrekitException: boom");
    assertEquals("OrekitException: boom", entry.getLastError().orElseThrow());

    entry.clearLastError();
    assertTrue(entry.getLastError().isEmpty());
  }

  @Test
  void recomposingDropsTheResultOfThePreviousComposition() {
    MissionEntry entry = new MissionEntry(leoSpec());
    entry.setAchievedOrbit(AchievedOrbit.UNAVAILABLE);
    entry.setPerformanceReport(emptyReport());
    entry.setLastError("OrekitException: from the previous run");

    // A mode toggle recomposes the mission and returns it to DRAFT; everything derived from the
    // abandoned composition must go with it, or the footer keeps showing an orbit no longer flown.
    entry.setOptimizationType(OptimizationType.PRECISE);

    assertTrue(entry.getAchievedOrbit().isEmpty(), "achieved orbit must not survive a recompose");
    assertTrue(entry.getPerformanceReport().isEmpty(), "report must not survive a recompose");
    assertTrue(entry.getLastError().isEmpty(), "error must not survive a recompose");
  }
}
