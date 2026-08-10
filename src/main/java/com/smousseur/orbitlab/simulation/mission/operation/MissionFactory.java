package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionHorizon;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import java.util.Map;

/**
 * Builds missions from the wizard's raw form values. Extracted from the wizard AppState so the
 * construction logic stays testable and out of the JME state. The map keys mirror the {@code
 * ui.mission.wizard.FormField} keys without depending on the UI layer.
 */
public final class MissionFactory {
  private MissionFactory() {}

  /**
   * Builds a mission from the wizard values in the default {@link OptimizationType#FAST} mode
   * (analytic profile). Equivalent to {@code MissionComposer.compose(specFromWizardValues(values,
   * type), OptimizationType.FAST)}.
   *
   * @param values the raw wizard values
   * @param type the selected mission type
   * @return the mission to schedule
   * @throws IllegalArgumentException if a catalog id is unknown or a required value is missing
   */
  public static Mission fromWizardValues(Map<String, Object> values, MissionType type) {
    return fromWizardValues(values, type, OptimizationType.FAST);
  }

  /**
   * Builds a mission from the wizard values in the given optimization mode. The spec (targets,
   * vehicle, site) is resolved once from the wizard values, then {@link MissionComposer} picks the
   * stage composition matching the mode.
   *
   * @param values the raw wizard values
   * @param type the selected mission type
   * @param mode the optimization mode driving the stage composition
   * @return the mission to schedule
   * @throws IllegalArgumentException if a catalog id is unknown or a required value is missing
   */
  public static Mission fromWizardValues(
      Map<String, Object> values, MissionType type, OptimizationType mode) {
    return MissionComposer.compose(specFromWizardValues(values, type), mode);
  }

  /**
   * Resolves the immutable {@link MissionSpec} from the wizard values: launcher and payload
   * resolved from the catalogs, the payload instantiated with the mass entered by the user, and
   * propellant loads sized analytically. Carries no stage decomposition — that is {@link
   * MissionComposer}'s job.
   *
   * @param values the raw wizard values
   * @param type the selected mission type
   * @return the resolved mission spec
   * @throws IllegalArgumentException if a catalog id is unknown or a required value is missing
   */
  public static MissionSpec specFromWizardValues(Map<String, Object> values, MissionType type) {
    String name = String.valueOf(values.get("MISSION_NAME"));
    // Descriptive only, and optional: the wizard always supplies it, but any caller assembling
    // values by hand may omit it, in which case the spec stays unnamed rather than carrying "null".
    String siteName = stringValueOrNull(values, "LAUNCH_SITE_NAME");
    double latitude = doubleValue(values, "LAUNCH_SITE_LAT");
    double longitude = doubleValue(values, "LAUNCH_SITE_LONG");
    double altitude = doubleValue(values, "LAUNCH_SITE_ALT");

    // Absent means "auto": the wizard omits the key while the user leaves the derived default in
    // place, so an unedited (or reopened) mission gets MissionHorizon.defaultFor(type) rather than
    // a frozen number (spec docs/mission-horizon/01-horizon-explicite.md §7).
    MissionHorizon horizon = horizonOrNull(values, type);

    LauncherModel launcher = Launchers.byId(String.valueOf(values.get("LAUNCHER_TYPE")));
    PayloadModel payloadModel = Payloads.byId(String.valueOf(values.get("PAYLOAD_TYPE")));
    double payloadMass = doubleValue(values, "PAYLOAD_MASS");
    if (payloadMass <= 0) {
      payloadMass = payloadModel.defaultDryMass();
    }

    return switch (type) {
      case LEO -> {
        double perigeeKm = doubleValue(values, "LEO_PERIGEE_ALT");
        double apogeeKm = doubleValue(values, "LEO_APOGEE_ALT");
        double perigeeAlt = Math.min(perigeeKm, apogeeKm) * 1000.0;
        double apogeeAlt = Math.max(perigeeKm, apogeeKm) * 1000.0;
        // The AKM has no role on a LEO mission: flown empty. Loads sized on the apogee
        // (conservative for elliptic targets).
        Spacecraft payload = payloadModel.toSpacecraft(payloadMass, 0.0);
        double[] loads = PropellantBudget.loadsForLeo(launcher, payload, apogeeAlt, latitude);
        LaunchConfiguration configuration =
            new LaunchConfiguration(launcher, loads, payload, payloadModel.id());
        yield new MissionSpec.Leo(
            name,
            configuration,
            perigeeAlt,
            apogeeAlt,
            siteName,
            latitude,
            longitude,
            altitude,
            horizon);
      }
      case GEO -> {
        // The apogee circularization is flown by the payload's kick motor; without one the burn
        // would only fail during propagation, on a background thread.
        if (!payloadModel.hasAkm()) {
          throw new IllegalArgumentException(
              "GEO mission requires a payload with an apogee kick motor: " + payloadModel.id());
        }
        double parkingAlt = doubleValue(values, "GTO_PARKING_ALT") * 1000.0;
        PropellantBudget.GeoLoads geoLoads =
            PropellantBudget.loadsForGeo(launcher, payloadModel, payloadMass, parkingAlt, latitude);
        Spacecraft payload = payloadModel.toSpacecraft(payloadMass, geoLoads.akmLoad());
        LaunchConfiguration configuration =
            new LaunchConfiguration(launcher, geoLoads.launcherLoads(), payload, payloadModel.id());
        yield new MissionSpec.Geo(
            name,
            configuration,
            parkingAlt,
            GEOMission.GEO_ALTITUDE,
            0.0,
            siteName,
            latitude,
            longitude,
            altitude,
            horizon);
      }
    };
  }

  /**
   * Reads the optional mission-duration override. Returns {@code null} — which the spec records
   * normalise into {@link MissionHorizon#defaultFor(MissionType)} — when the key is absent, blank
   * or unusable, so a malformed value degrades to the derived default instead of failing the whole
   * mission creation.
   *
   * @param values the raw wizard values
   * @param type the mission type, for the default
   * @return the overridden horizon, or {@code null} to take the derived default
   */
  private static MissionHorizon horizonOrNull(Map<String, Object> values, MissionType type) {
    Object raw = values.get("MISSION_HORIZON_DAYS");
    if (raw == null || raw.toString().isBlank()) {
      return null;
    }
    try {
      double days = Double.parseDouble(raw.toString().trim());
      return days > 0.0
          ? new MissionHorizon.FixedDuration(days * MissionHorizon.SECONDS_PER_DAY)
          : null;
    } catch (IllegalArgumentException e) {
      // Covers both the parse failure and a FixedDuration rejecting a non-finite value.
      return null;
    }
  }

  private static String stringValueOrNull(Map<String, Object> values, String key) {
    Object raw = values.get(key);
    return raw == null ? null : raw.toString();
  }

  private static double doubleValue(Map<String, Object> values, String key) {
    Object raw = values.get(key);
    if (raw == null) {
      throw new IllegalArgumentException("Missing wizard value: " + key);
    }
    return Double.parseDouble(raw.toString());
  }
}
