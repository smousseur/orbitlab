package com.smousseur.orbitlab.ui.mission.wizard.step;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smousseur.orbitlab.ui.mission.wizard.step.LaunchDateProvenance.Source;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LaunchDateProvenanceTest {

  private static final String WRITTEN = "2025-03-14 06:21:44";

  @Test
  @DisplayName("a date the planner never wrote is typed")
  void neverWrittenIsTyped() {
    assertEquals(Source.TYPED, LaunchDateProvenance.read(false, "", WRITTEN, false));
  }

  @Test
  @DisplayName("the text the planner wrote, still standing, is planned")
  void writtenAndUnchangedIsPlanned() {
    assertEquals(Source.PLANNED, LaunchDateProvenance.read(true, WRITTEN, WRITTEN, false));
  }

  @Nested
  @DisplayName("a hand edit takes the date back")
  class HandEdit {

    @Test
    @DisplayName("one character away from what was written is typed again")
    void editedIsTyped() {
      assertEquals(
          Source.TYPED, LaunchDateProvenance.read(true, WRITTEN, "2025-03-14 06:21:45", false));
    }

    @Test
    @DisplayName("an emptied field is typed again")
    void clearedIsTyped() {
      assertEquals(Source.TYPED, LaunchDateProvenance.read(true, WRITTEN, "", false));
    }
  }

  @Nested
  @DisplayName("a refusal hides the provenance without ending it")
  class Refusal {

    @Test
    @DisplayName("a refused planned date leaves the line to the reason")
    void refusedPlannedIsRefused() {
      assertEquals(Source.REFUSED, LaunchDateProvenance.read(true, WRITTEN, WRITTEN, true));
    }

    @Test
    @DisplayName("a refused typed date leaves the line to the reason")
    void refusedTypedIsRefused() {
      assertEquals(Source.REFUSED, LaunchDateProvenance.read(false, "", WRITTEN, true));
    }

    @Test
    @DisplayName("clearing the refusal shows the planner's line again")
    void clearedRefusalIsPlannedAgain() {
      assertEquals(Source.PLANNED, LaunchDateProvenance.read(true, WRITTEN, WRITTEN, false));
    }
  }
}
