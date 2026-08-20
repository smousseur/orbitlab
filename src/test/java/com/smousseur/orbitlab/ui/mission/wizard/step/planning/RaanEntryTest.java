package com.smousseur.orbitlab.ui.mission.wizard.step.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The target-node field's parse policy — the part of {@code PlanningPage} that can be exercised
 * without an initialised {@code AssetManager}.
 */
class RaanEntryTest {

  @Test
  @DisplayName("blank is accepted and carries no value")
  void blankIsAcceptedAndEmpty() {
    assertTrue(RaanEntry.refusal("").isEmpty());
    assertTrue(RaanEntry.parse("").isEmpty());
  }

  @Test
  @DisplayName("whitespace only behaves exactly as blank")
  void whitespaceBehavesAsBlank() {
    assertTrue(RaanEntry.refusal("   ").isEmpty());
    assertTrue(RaanEntry.parse("   ").isEmpty());
  }

  @Test
  @DisplayName("a null field content is read as blank rather than thrown on")
  void nullIsBlank() {
    assertTrue(RaanEntry.refusal(null).isEmpty());
    assertTrue(RaanEntry.parse(null).isEmpty());
  }

  @Test
  @DisplayName("a plain number parses, surrounding spaces included")
  void plainNumberParses() {
    assertEquals(120.0, RaanEntry.parse("120").orElseThrow(), 1e-12);
    assertEquals(42.5, RaanEntry.parse("  42.5  ").orElseThrow(), 1e-12);
    assertTrue(RaanEntry.refusal("  42.5  ").isEmpty());
  }

  /**
   * The criterion the node feeds is periodic, so neither bound is a defect: a negative reading and
   * one past a full turn both name a real plane, and clamping them here would refuse an entry the
   * model accepts.
   */
  @ParameterizedTest
  @ValueSource(strings = {"-30", "-0.5", "360", "375.25", "1080"})
  @DisplayName("no range check — the criterion is periodic")
  void outOfTurnValuesParse(String text) {
    assertTrue(RaanEntry.parse(text).isPresent());
    assertTrue(RaanEntry.refusal(text).isEmpty());
  }

  @Test
  @DisplayName("an unreadable entry is refused, with the value quoted in the reason")
  void unreadableIsRefused() {
    Optional<String> refusal = RaanEntry.refusal("abc");
    assertTrue(refusal.isPresent());
    assertTrue(refusal.get().contains("abc"), refusal.get());
    assertTrue(RaanEntry.parse("abc").isEmpty());
  }

  @Test
  @DisplayName("the reason quotes the trimmed entry, not the raw field content")
  void reasonQuotesTrimmedEntry() {
    assertEquals("Target RAAN is not a number: abc", RaanEntry.refusal("  abc  ").orElseThrow());
  }

  /**
   * The invariant that keeps the two halves of the policy from drifting: {@code parse} is what
   * decides whether a value is published, {@code refusal} is what decides whether the wizard stays
   * open, and an entry accepted by one but rejected by the other would either drop an intention
   * silently or block a usable form.
   */
  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "0", "-30", "120.5", "360", "1e2", "abc", "12abc", "--5", "."})
  @DisplayName("parse and refusal never disagree about whether an entry is usable")
  void parseAndRefusalAgree(String text) {
    boolean blank = text.trim().isEmpty();
    boolean refused = RaanEntry.refusal(text).isPresent();
    boolean parsed = RaanEntry.parse(text).isPresent();
    if (refused) {
      assertFalse(parsed, "refused yet parsed: " + text);
      assertFalse(blank, "blank must never be refused: " + text);
    } else {
      assertEquals(!blank, parsed, "accepted entry must parse unless it is blank: " + text);
    }
  }
}
