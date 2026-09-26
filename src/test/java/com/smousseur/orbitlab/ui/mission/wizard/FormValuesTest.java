package com.smousseur.orbitlab.ui.mission.wizard;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link FormValues#flag(Map, FormField)} against the same lenient-reader contract as {@link
 * FormValues#string} and {@link FormValues#number}: the map is untyped, so the reader has to accept
 * whatever concrete type a step or a prefill put there.
 */
class FormValuesTest {

  @Test
  void absentKeyReadsAsFalse() {
    assertFalse(FormValues.flag(new HashMap<>(), FormField.DEORBIT));
  }

  @Test
  void booleanTrueReadsAsTrue() {
    Map<String, Object> values = new HashMap<>();
    values.put(FormField.DEORBIT.key(), Boolean.TRUE);
    assertTrue(FormValues.flag(values, FormField.DEORBIT));
  }

  @Test
  void booleanFalseReadsAsFalse() {
    Map<String, Object> values = new HashMap<>();
    values.put(FormField.DEORBIT.key(), Boolean.FALSE);
    assertFalse(FormValues.flag(values, FormField.DEORBIT));
  }

  @Test
  void textTrueReadsAsTrue() {
    Map<String, Object> values = new HashMap<>();
    values.put(FormField.DEORBIT.key(), "true");
    assertTrue(FormValues.flag(values, FormField.DEORBIT));
  }

  @Test
  void textFalseReadsAsFalse() {
    Map<String, Object> values = new HashMap<>();
    values.put(FormField.DEORBIT.key(), "false");
    assertFalse(FormValues.flag(values, FormField.DEORBIT));
  }

  @Test
  void textIsTrimmedAndCaseInsensitive() {
    Map<String, Object> values = new HashMap<>();
    values.put(FormField.DEORBIT.key(), " TRUE ");
    assertTrue(FormValues.flag(values, FormField.DEORBIT));
  }

  @Test
  void unrecognizedTextReadsAsFalse() {
    Map<String, Object> values = new HashMap<>();
    values.put(FormField.DEORBIT.key(), "yes");
    assertFalse(FormValues.flag(values, FormField.DEORBIT));
  }
}
