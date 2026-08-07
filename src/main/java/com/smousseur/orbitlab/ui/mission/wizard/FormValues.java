package com.smousseur.orbitlab.ui.mission.wizard;

import java.util.Map;

/**
 * Lenient readers for the wizard's raw value map, used by {@link StepValues#applyValues(Map)}.
 *
 * <p>The map is deliberately untyped: a step publishes whatever its widget holds, so an altitude
 * comes out as a {@link Long} from a slider but as a {@link Double} from the spec-derived prefill,
 * and {@link FormField#cast(Object)} would reject one of the two. Prefilling is also a best-effort
 * operation — a value the wizard cannot use must leave the widget on its default rather than abort
 * the whole edit — hence the fallbacks instead of exceptions.
 */
public final class FormValues {

  private FormValues() {}

  /**
   * Reads a text value.
   *
   * @param values the raw wizard values
   * @param field the field to read
   * @return the value, or {@code null} when absent
   */
  public static String string(Map<String, Object> values, FormField<String> field) {
    Object raw = values.get(field.key());
    return raw == null ? null : raw.toString();
  }

  /**
   * Reads a numeric value, whatever concrete type carries it.
   *
   * @param values the raw wizard values
   * @param field the field to read
   * @param fallback the value to return when the field is absent or unparsable
   * @return the parsed number, or {@code fallback}
   */
  public static double number(Map<String, Object> values, FormField<Double> field, double fallback) {
    Object raw = values.get(field.key());
    if (raw == null) {
      return fallback;
    }
    if (raw instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.parseDouble(raw.toString().trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
