package com.smousseur.orbitlab.simulation.mission.scenario;

import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioMission;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioSite;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioSolution;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioVehicle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Translates between the scenario DTO and the <b>wizard values map</b> — never between the DTO and
 * a {@code MissionSpec} (spec {@code docs/scenario/01-persistance-missions.md} §4.2).
 *
 * <p>That is the whole point of the class. {@code WizardPrefill.fromEntry} and {@code
 * MissionFactory.specFromWizardValues} are already an exact inverse pair, exercised on every wizard
 * edit; going through the map means the file inherits the "absence is meaningful" rule from the one
 * place that implements it, instead of restating it. Rebuilding a spec straight from the file would
 * duplicate the propellant sizing and the due-east derivation — some 150 delicate lines — and would
 * drift trajectories with no assertion noticing.
 *
 * <p>It stays out of the UI layer: like {@code MissionFactory}, it uses the same key literals as
 * {@code ui.mission.wizard.FormField} without depending on it.
 *
 * <p><b>Units are the wizard units</b> — kilometres, degrees, days — not the metres and radians of
 * the spec (§3.1, rule 2): the file is meant to be read and diffed by a human, and it is the shape
 * the map carries anyway. Dates are normalised to ISO UTC on the way out and back to the display
 * format on the way in, both through {@code TimeConverter}, which parses either.
 */
public final class ScenarioMapper {

  private static final int HEX_DIGITS = 6;

  private ScenarioMapper() {}

  /**
   * Builds the DTO for one mission.
   *
   * @param entry the mission entry, for the four values the wizard does not own — mode, colour,
   *     visibility, and the atmosphere, which lives on the spec because no form field faces it yet
   * @param values the wizard values, as {@code WizardPrefill.fromEntry} produced them
   * @param solution what the optimization found, or {@code null} for a mission that never flew
   * @return the mission as the file carries it
   * @throws OrbitlabException if a required value is missing or unreadable
   */
  public static ScenarioMission toScenarioMission(
      MissionEntry entry, Map<String, Object> values, ScenarioSolution solution) {
    MissionType type = missionType(values);
    ScenarioSite site =
        new ScenarioSite(
            stringOrNull(values, "LAUNCH_SITE_NAME"),
            doubleValue(values, "LAUNCH_SITE_LAT"),
            doubleValue(values, "LAUNCH_SITE_LONG"),
            doubleValue(values, "LAUNCH_SITE_ALT"));
    ScenarioVehicle vehicle =
        new ScenarioVehicle(
            requiredString(values, "LAUNCHER_TYPE"),
            stringOrNull(values, "PAYLOAD_TYPE"),
            doubleValue(values, "PAYLOAD_MASS"));

    String name = requiredString(values, "MISSION_NAME");
    String launchDate = toIsoDate(stringOrNull(values, "LAUNCH_DATE"));
    Double horizonDays = doubleOrNull(values, "MISSION_HORIZON_DAYS");
    String atmosphere =
        entry.spec().map(spec -> spec.atmosphere().name()).orElse(AtmosphereModel.NONE.name());
    String mode = entry.getOptimizationType().name();
    String color = toHex(entry.getColor());
    boolean visible = entry.isVisible();

    return switch (type) {
      case LEO ->
          new ScenarioMission.EarthOrbit(
              type,
              name,
              launchDate,
              site,
              vehicle,
              horizonDays,
              atmosphere,
              mode,
              color,
              visible,
              solution,
              doubleValue(values, "LEO_PERIGEE_ALT"),
              doubleValue(values, "LEO_APOGEE_ALT"),
              doubleOrNull(values, "TARGET_INCLINATION"),
              doubleOrNull(values, "TARGET_RAAN"));
      case GEO ->
          new ScenarioMission.Geo(
              type,
              name,
              launchDate,
              site,
              vehicle,
              horizonDays,
              atmosphere,
              mode,
              color,
              visible,
              solution,
              doubleValue(values, "GTO_PARKING_ALT"));
      // MIS-4 / L5 §6.2. The parking altitude is absent on purpose: it is the mission's own
      // constant, no wizard field carries it, and the factory reads it back from there.
      case LUNAR_FLYBY ->
          new ScenarioMission.Lunar(
              type,
              name,
              launchDate,
              site,
              vehicle,
              horizonDays,
              atmosphere,
              mode,
              color,
              visible,
              solution,
              doubleValue(values, "LUNAR_PERILUNE_ALT"));
      case LUNAR_ORBIT ->
          new ScenarioMission.LunarOrbit(
              type,
              name,
              launchDate,
              site,
              vehicle,
              horizonDays,
              atmosphere,
              mode,
              color,
              visible,
              solution,
              doubleValue(values, "LUNAR_ORBIT_ALT"));
    };
  }

  /**
   * Rebuilds the wizard values a mission was created from, ready for {@code
   * MissionFactory.specFromWizardValues}.
   *
   * <p>Exactly symmetric to {@link #toScenarioMission(MissionEntry, Map, ScenarioSolution)} on
   * everything the map owns, with one deliberate asymmetry: {@code MISSION_PROFILE} is not
   * reproduced. It is derived — {@code MissionProfile.of(spec)} rebuilds it — and the factory
   * ignores the key entirely; persisting it would create a second truth about the mission (§2).
   *
   * @param mission the mission as read from the file
   * @return the wizard values, keyed as {@code FormField} keys them
   * @throws OrbitlabException if the launch date cannot be read
   */
  public static Map<String, Object> toMissionValues(ScenarioMission mission) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("MISSION_TYPE", mission.type().name());
    values.put("MISSION_NAME", mission.name());
    putIfPresent(values, "LAUNCH_DATE", toDisplayDate(mission.launchDate()));

    ScenarioSite site = mission.site();
    putIfPresent(values, "LAUNCH_SITE_NAME", site.name());
    values.put("LAUNCH_SITE_LAT", site.latitudeDeg());
    values.put("LAUNCH_SITE_LONG", site.longitudeDeg());
    values.put("LAUNCH_SITE_ALT", site.altitudeM());

    ScenarioVehicle vehicle = mission.vehicle();
    values.put("LAUNCHER_TYPE", vehicle.launcherId());
    putIfPresent(values, "PAYLOAD_TYPE", vehicle.payloadId());
    values.put("PAYLOAD_MASS", vehicle.payloadDryMassKg());

    putIfPresent(values, "MISSION_HORIZON_DAYS", mission.horizonDays());

    switch (mission) {
      case ScenarioMission.EarthOrbit earthOrbit -> {
        values.put("LEO_PERIGEE_ALT", earthOrbit.perigeeKm());
        values.put("LEO_APOGEE_ALT", earthOrbit.apogeeKm());
        putIfPresent(values, "TARGET_INCLINATION", earthOrbit.inclinationDeg());
        putIfPresent(values, "TARGET_RAAN", earthOrbit.raanDeg());
      }
      case ScenarioMission.Geo geo -> values.put("GTO_PARKING_ALT", geo.parkingKm());
      case ScenarioMission.Lunar lunar -> values.put("LUNAR_PERILUNE_ALT", lunar.periluneKm());
      case ScenarioMission.LunarOrbit lunarOrbit ->
          values.put("LUNAR_ORBIT_ALT", lunarOrbit.orbitAltitudeKm());
    }
    return values;
  }

  /**
   * Reads a colour back from its {@code #RRGGBB} form.
   *
   * @param hex the colour, or {@code null}
   * @return the opaque colour, or {@code null} when none was written
   * @throws OrbitlabException if the text is present but is not a colour
   */
  public static ColorRGBA fromHex(String hex) {
    if (hex == null || hex.isBlank()) {
      return null;
    }
    String digits = hex.startsWith("#") ? hex.substring(1) : hex;
    int rgb;
    try {
      if (digits.length() != HEX_DIGITS) {
        throw new NumberFormatException(hex);
      }
      rgb = Integer.parseInt(digits, 16);
    } catch (NumberFormatException e) {
      throw new OrbitlabException("Mission colour is not a #RRGGBB value: " + hex);
    }
    return new ColorRGBA(
        ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
  }

  private static String toHex(ColorRGBA color) {
    if (color == null) {
      return null;
    }
    return String.format(
        Locale.ROOT,
        "#%02X%02X%02X",
        Math.round(color.r * 255),
        Math.round(color.g * 255),
        Math.round(color.b * 255));
  }

  /**
   * The mission type is the JSON discriminator, so an unreadable one has no record to land in: it
   * is refused here rather than defaulted, the rule {@code MissionFactory} already applies to an
   * unreadable RAAN (§7).
   */
  private static MissionType missionType(Map<String, Object> values) {
    String raw = requiredString(values, "MISSION_TYPE");
    try {
      return MissionType.valueOf(raw);
    } catch (IllegalArgumentException e) {
      throw new OrbitlabException("Unknown mission type: " + raw);
    }
  }

  private static String toIsoDate(String displayed) {
    if (displayed == null) {
      return null;
    }
    return TimeConverter.parseUtcDate(displayed)
        .map(TimeConverter::toUtcIsoString)
        .orElseThrow(() -> new OrbitlabException("Launch date is not a UTC date: " + displayed));
  }

  private static String toDisplayDate(String iso) {
    if (iso == null) {
      return null;
    }
    return TimeConverter.parseUtcDate(iso)
        .map(TimeConverter::formatDate)
        .orElseThrow(() -> new OrbitlabException("Launch date is not a UTC date: " + iso));
  }

  private static void putIfPresent(Map<String, Object> values, String key, Object value) {
    if (value != null) {
      values.put(key, value);
    }
  }

  private static String requiredString(Map<String, Object> values, String key) {
    String value = stringOrNull(values, key);
    if (value == null) {
      throw new OrbitlabException("Missing mission value: " + key);
    }
    return value;
  }

  private static String stringOrNull(Map<String, Object> values, String key) {
    Object raw = values.get(key);
    if (raw == null || raw.toString().isBlank()) {
      return null;
    }
    return raw.toString();
  }

  private static double doubleValue(Map<String, Object> values, String key) {
    Double value = doubleOrNull(values, key);
    if (value == null) {
      throw new OrbitlabException("Missing mission value: " + key);
    }
    return value;
  }

  /**
   * Reads an optional number. An absent key stays absent — it is the very thing the format has to
   * carry for an uncommanded inclination, an unwaited node and an "auto" horizon (§3.1, rule 1) —
   * while a present but unreadable one is refused rather than dropped.
   */
  private static Double doubleOrNull(Map<String, Object> values, String key) {
    Object raw = values.get(key);
    if (raw == null || raw.toString().isBlank()) {
      return null;
    }
    if (raw instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.parseDouble(raw.toString().trim());
    } catch (NumberFormatException e) {
      throw new OrbitlabException("Mission value " + key + " is not a number: " + raw);
    }
  }
}
