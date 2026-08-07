package com.smousseur.orbitlab.ui.mission.wizard;

import java.util.Map;

/** Contract for wizard steps that expose their current widget values. */
public interface StepValues {

  /**
   * @return the values currently held by this step's widgets, keyed by {@link FormField#key()}.
   *     Values are either {@link String} or {@link Double}.
   */
  Map<String, Object> getValues();

  /**
   * Prefills this step's widgets from values previously produced by {@link #getValues()} — the
   * wizard's edit mode reopens on the mission's own configuration. Keys this step does not own are
   * ignored, and a missing key leaves the corresponding widget at its default.
   *
   * <p>Numbers are read leniently: the map may carry them as {@link Double}, {@link Long} or even
   * their {@link String} form depending on which widget produced them.
   *
   * @param values the values to apply, keyed by {@link FormField#key()}
   */
  default void applyValues(Map<String, Object> values) {}

  /**
   * Typed accessor.
   *
   * @return the value for {@code field}, or {@code null} if this step does not expose it.
   */
  default <T> T getValue(FormField<T> field) {
    Object raw = getValues().get(field.key());
    return raw == null ? null : field.cast(raw);
  }
}
