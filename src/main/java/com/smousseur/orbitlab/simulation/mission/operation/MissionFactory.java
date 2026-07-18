package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.Payloads;
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
   * Builds a mission from the wizard values: launcher and payload resolved from the catalogs, the
   * payload instantiated with the mass entered by the user.
   *
   * @param values the raw wizard values
   * @param type the selected mission type
   * @return the mission to schedule
   * @throws IllegalArgumentException if a catalog id is unknown or a required value is missing
   */
  public static Mission fromWizardValues(Map<String, Object> values, MissionType type) {
    String name = String.valueOf(values.get("MISSION_NAME"));
    double latitude = doubleValue(values, "LAUNCH_SITE_LAT");
    double longitude = doubleValue(values, "LAUNCH_SITE_LONG");
    double altitude = doubleValue(values, "LAUNCH_SITE_ALT");

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
        LaunchConfiguration configuration = new LaunchConfiguration(launcher, loads, payload);
        yield new LEOMission(
            name, configuration, perigeeAlt, apogeeAlt, latitude, longitude, altitude);
      }
      case GEO -> {
        double parkingAlt = doubleValue(values, "GTO_PARKING_ALT") * 1000.0;
        PropellantBudget.GeoLoads geoLoads =
            PropellantBudget.loadsForGeo(launcher, payloadModel, payloadMass, parkingAlt, latitude);
        Spacecraft payload = payloadModel.toSpacecraft(payloadMass, geoLoads.akmLoad());
        LaunchConfiguration configuration =
            new LaunchConfiguration(launcher, geoLoads.launcherLoads(), payload);
        yield new GEOMission(
            name,
            configuration,
            parkingAlt,
            GEOMission.GEO_ALTITUDE,
            latitude,
            longitude,
            altitude,
            0.0);
      }
    };
  }

  private static double doubleValue(Map<String, Object> values, String key) {
    Object raw = values.get(key);
    if (raw == null) {
      throw new IllegalArgumentException("Missing wizard value: " + key);
    }
    return Double.parseDouble(raw.toString());
  }
}
