package com.smousseur.orbitlab.ui.mission.wizard;

import java.util.Objects;

/**
 * Typed key for a wizard form field.
 *
 * <p>Each field is stored in the values map under {@link #key()}; {@link #cast(Object)} performs a
 * runtime check using {@link #type()} so consumers retrieve the value without explicit casts.
 *
 * @param <T> the runtime value type ({@link String} or {@link Double})
 */
public final class FormField<T> {

  private final String key;
  private final Class<T> type;

  private FormField(String key, Class<T> type) {
    this.key = Objects.requireNonNull(key, "key");
    this.type = Objects.requireNonNull(type, "type");
  }

  public String key() {
    return key;
  }

  public Class<T> type() {
    return type;
  }

  public T cast(Object raw) {
    return type.cast(raw);
  }

  // --- Available form fields ---

  public static final FormField<String> MISSION_TYPE =
      new FormField<>("MISSION_TYPE", String.class);
  public static final FormField<String> MISSION_NAME =
      new FormField<>("MISSION_NAME", String.class);
  public static final FormField<Double> LEO_TARGET_ALT =
      new FormField<>("LEO_TARGET_ALT", Double.class);
  public static final FormField<String> LAUNCH_DATE =
      new FormField<>("LAUNCH_DATE", String.class);
  public static final FormField<String> LAUNCH_SITE_NAME =
      new FormField<>("LAUNCH_SITE_NAME", String.class);
  public static final FormField<Double> LAUNCH_SITE_LAT =
      new FormField<>("LAUNCH_SITE_LAT", Double.class);
  public static final FormField<Double> LAUNCH_SITE_LONG =
      new FormField<>("LAUNCH_SITE_LONG", Double.class);
  public static final FormField<Double> LAUNCH_SITE_ALT =
      new FormField<>("LAUNCH_SITE_ALT", Double.class);
  public static final FormField<String> LAUNCHER_TYPE =
      new FormField<>("LAUNCHER_TYPE", String.class);
  public static final FormField<String> PAYLOAD_TYPE =
      new FormField<>("PAYLOAD_TYPE", String.class);
  public static final FormField<Double> PAYLOAD_MASS =
      new FormField<>("PAYLOAD_MASS", Double.class);
}
