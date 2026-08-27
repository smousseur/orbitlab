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
  public static final FormField<Double> LEO_PERIGEE_ALT =
      new FormField<>("LEO_PERIGEE_ALT", Double.class);
  public static final FormField<Double> LEO_APOGEE_ALT =
      new FormField<>("LEO_APOGEE_ALT", Double.class);
  public static final FormField<Double> GTO_PARKING_ALT =
      new FormField<>("GTO_PARKING_ALT", Double.class);

  /**
   * Perilune altitude in <b>kilometres</b>, the one parameter a lunar flyby offers (MIS-4 / L5 §3).
   *
   * <p>The parking altitude is deliberately not a field beside it: it is {@code
   * LunarFlybyMission.DEFAULT_PARKING_ALTITUDE}, and MIS-4 / L0 measured the aim to converge
   * identically from 185 to 400 km — a slider there would be a choice with nothing behind it.
   */
  public static final FormField<Double> LUNAR_PERILUNE_ALT =
      new FormField<>("LUNAR_PERILUNE_ALT", Double.class);

  public static final FormField<String> LAUNCH_DATE = new FormField<>("LAUNCH_DATE", String.class);
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

  /**
   * Target orbit inclination in <b>degrees</b>, written only when it is an intention rather than
   * whatever the site gives for free. Its <b>absence</b> is meaningful, exactly as {@link
   * #MISSION_HORIZON_DAYS}'s is: it means "the plane a due-east launch reaches", and {@code
   * MissionFactory} then builds {@code LaunchPlane.dueEast(latitude)} from the latitude in double
   * rather than from the rounded number a form field shows.
   *
   * <p>That distinction is the non-regression seam of spec {@code
   * docs/earth-orbit/02-wizard-orbites-terrestres.md} §2.0: publishing the derived value would move
   * the azimuth by a few thousandths of a degree, hence the signed launch assist {@code
   * PropellantBudget} charges, hence the propellant loads — a trajectory shift that no inclination
   * assertion would ever catch, because the plane itself would still be right.
   */
  public static final FormField<Double> TARGET_INCLINATION =
      new FormField<>("TARGET_INCLINATION", Double.class);

  /**
   * Right ascension of the target plane's ascending node, in <b>degrees</b>, written only when the
   * mission is aiming at a plane that already exists — a station to meet, a constellation slot to
   * fill. Its <b>absence</b> is meaningful, as {@link #TARGET_INCLINATION}'s is: it means no plane
   * is being waited for, and the mission then launches at the date the user typed rather than at
   * the next opening of a window (MIS-2).
   *
   * <p>An inclination alone does not need a window — every instant of the day reaches it — which is
   * why this is a second field and not a mode of the first.
   */
  public static final FormField<Double> TARGET_RAAN = new FormField<>("TARGET_RAAN", Double.class);

  /**
   * The {@link MissionProfile} the mission was created on, by name. UI-only: no spec component
   * corresponds to it, and {@code MissionFactory} ignores the key entirely — the profile is a way
   * of offering parameters, not a property of the mission (spec {@code
   * docs/earth-orbit/02-wizard-orbites-terrestres.md} §1).
   */
  public static final FormField<String> MISSION_PROFILE =
      new FormField<>("MISSION_PROFILE", String.class);

  /**
   * Total mission duration in days, written only when the user overrode the derived default. Its
   * <b>absence</b> is meaningful: it is how the wizard says "auto", so {@code MissionFactory} falls
   * back to {@code MissionHorizon.defaultFor(type)} and reopening the mission restores the auto
   * state without any extra flag to carry (spec {@code
   * docs/mission-horizon/01-horizon-explicite.md} §7).
   */
  public static final FormField<Double> MISSION_HORIZON_DAYS =
      new FormField<>("MISSION_HORIZON_DAYS", Double.class);
}
