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
   * Typed accessor.
   *
   * @return the value for {@code field}, or {@code null} if this step does not expose it.
   */
  default <T> T getValue(FormField<T> field) {
    Object raw = getValues().get(field.key());
    return raw == null ? null : field.cast(raw);
  }
}
