package com.smousseur.orbitlab.simulation.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MissionIdTest {

  @Test
  void newIdMintsDistinctValues() {
    Set<MissionId> ids = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      ids.add(MissionId.newId());
    }
    assertEquals(1000, ids.size(), "every generated id must be distinct");
  }

  @Test
  void equalityIsByValueSoIdsWorkAsMapKeys() {
    MissionId a = MissionId.newId();
    MissionId copy = MissionId.parse(a.toString());

    assertEquals(a, copy);
    assertEquals(a.hashCode(), copy.hashCode());
    assertNotEquals(a, MissionId.newId());
  }

  @Test
  void parseRoundTripsToString() {
    MissionId id = MissionId.newId();

    assertEquals(id, MissionId.parse(id.toString()));
  }

  @Test
  void parseRejectsMalformedText() {
    assertThrows(IllegalArgumentException.class, () -> MissionId.parse("not-a-uuid"));
    assertThrows(NullPointerException.class, () -> MissionId.parse(null));
  }

  @Test
  void constructorRejectsNullValue() {
    assertThrows(NullPointerException.class, () -> new MissionId(null));
  }

  /**
   * Guards the deliberate choice documented on {@link MissionId#toString()}: the lossless form is
   * the one that must land in accidental string contexts (concatenation, log placeholders). A
   * regression to the abbreviation here would silently corrupt anything persisted through
   * {@code toString()}.
   */
  @Test
  void toStringIsTheCanonicalFormNotTheAbbreviation() {
    MissionId id = MissionId.newId();

    assertEquals(36, id.toString().length());
    assertEquals(id.value().toString(), id.toString());
    assertNotEquals(id.shortForm(), id.toString());
  }

  @Test
  void shortFormIsAnEightCharacterPrefix() {
    MissionId id = MissionId.newId();

    assertEquals(8, id.shortForm().length());
    assertTrue(id.toString().startsWith(id.shortForm()));
  }
}
