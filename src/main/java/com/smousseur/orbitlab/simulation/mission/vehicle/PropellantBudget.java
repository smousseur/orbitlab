package com.smousseur.orbitlab.simulation.mission.vehicle;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;
import java.util.List;
import org.hipparchus.util.FastMath;
import org.orekit.utils.Constants;

/**
 * Analytic propellant sizing — "just enough" loads per mission (spec 06 §4.3). Inverse Tsiolkovsky
 * computed top-down from the payload: every stage below the launcher's top stage flies fully
 * loaded (v1 — the gravity turn consumes them entirely anyway), only the top stage and the
 * payload's AKM are sized from the ΔV budget. A safety margin absorbs finite-burn and steering
 * losses; loads are clamped to capacity (an infeasibility diagnostic is a later increment).
 */
public final class PropellantBudget {
  private PropellantBudget() {}

  /** Safety margin applied to sized loads (finite-burn + steering losses + reserve). */
  public static final double SAFETY_MARGIN = 0.10;

  /**
   * Gravity + steering losses of the ascent (m/s). Calibrated on two MissionPerformanceReport
   * points of the FH LEO 400 km budget run (1 600 → 37.9 % S2 residual, 1 400 → 26.2 %; the
   * residual converges slowly because consumption shrinks with the load, slope ≈ 0.31 kg/kg).
   * 1 260 is the fixed point for a ~12 % residual on the reference mission, and stays above the
   * measured real losses (≈ 1 100 m/s). Do not tighten further without the MassDepletionDetector
   * guard: the headroom over actual consumption is down to ~1 s of upper-stage burn.
   */
  public static final double ASCENT_LOSSES_MS = 1_260.0;

  /** Eastward velocity gained from Earth rotation at the equator (m/s). */
  private static final double EQUATORIAL_ROTATION_MS = 465.0;

  /** Mirrors GEOMission.GEO_ALTITUDE without depending on the operation package. */
  private static final double GEO_ALTITUDE_M = 35_786_000.0;

  private static final double MU = Constants.WGS84_EARTH_MU;
  private static final double RE = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static final double G0 = Constants.G0_STANDARD_GRAVITY;

  /** Fixed-point iterations of the top-stage sizing (monotone contraction, converges fast). */
  private static final int SIZING_ITERATIONS = 12;

  /** Launcher loads plus the payload's apogee-kick-motor load for a GEO mission. */
  public record GeoLoads(double[] launcherLoads, double akmLoad) {}

  /**
   * Per-stage loads for a LEO mission.
   *
   * @param launcher the launcher model
   * @param payload the payload as flown (its mass anchors the top-down sizing)
   * @param targetAltitude the target orbit altitude (m); use the apogee for elliptic targets
   * @param launchLatitudeDeg the launch site latitude (degrees)
   * @return the propellant load per stage, same order as the launcher stages
   */
  public static double[] loadsForLeo(
      LauncherModel launcher, Spacecraft payload, double targetAltitude, double launchLatitudeDeg) {
    double dvTotal = ascentDeltaV(targetAltitude, launchLatitudeDeg);
    return sizeTopStage(launcher, payload.getMass(), dvTotal);
  }

  /**
   * Launcher loads and AKM load for a GEO mission (parking → GTO → GEO).
   *
   * <p>Until the GEO profile split (spec 06 I5), the launcher's top stage performs the whole
   * flight — ascent residual, GTO injection AND apogee circularization/plane change — so its
   * budget includes all three. The AKM is sized for the apogee burn it will take over in I5 and
   * rides along until then.
   *
   * @param launcher the launcher model
   * @param payload the payload model (provides the AKM characteristics)
   * @param payloadDryMass the dry mass entered at mission creation (kg)
   * @param parkingAltitude the parking orbit altitude (m)
   * @param launchLatitudeDeg the launch site latitude (degrees); also the plane change to cancel
   * @return the launcher loads and the AKM load
   */
  public static GeoLoads loadsForGeo(
      LauncherModel launcher,
      PayloadModel payload,
      double payloadDryMass,
      double parkingAltitude,
      double launchLatitudeDeg) {
    double dvApogee = apogeeCircularizationDeltaV(parkingAltitude, launchLatitudeDeg);

    double akmLoad = 0.0;
    if (payload.akmPropellantCapacity() > 0) {
      double exhaustVelocity = payload.akmPropulsion().isp() * G0;
      double raw =
          payloadDryMass
              * (FastMath.exp(dvApogee / exhaustVelocity) - 1.0)
              * (1.0 + SAFETY_MARGIN);
      akmLoad = FastMath.min(raw, payload.akmPropellantCapacity());
    }

    double dvTotal =
        ascentDeltaV(parkingAltitude, launchLatitudeDeg)
            + gtoInjectionDeltaV(parkingAltitude)
            + dvApogee;
    double[] launcherLoads = sizeTopStage(launcher, payloadDryMass + akmLoad, dvTotal);
    return new GeoLoads(launcherLoads, akmLoad);
  }

  /**
   * Sizes the top stage for the ΔV left over by the fully-loaded lower stages. Fixed-point
   * iteration: the lower stages' ΔV depends on the mass above them, which depends on the sized
   * top load. Solid top stages fly full (no sizing degree of freedom).
   */
  private static double[] sizeTopStage(
      LauncherModel launcher, double payloadMass, double dvTotal) {
    List<StageModel> stages = launcher.stages();
    int n = stages.size();
    double[] loads = new double[n];
    for (int i = 0; i < n; i++) {
      loads[i] = stages.get(i).propellantCapacity();
    }

    StageModel top = stages.getLast();
    if (!top.capabilities().variableLoad()) {
      return loads;
    }

    double exhaustVelocityTop = top.propulsion().isp() * G0;
    double topLoad = top.propellantCapacity();
    for (int iter = 0; iter < SIZING_ITERATIONS; iter++) {
      double dvLower = 0.0;
      for (int i = 0; i < n - 1; i++) {
        StageModel stage = stages.get(i);
        double massAbove = payloadMass + top.dryMass() + topLoad;
        for (int j = i + 1; j < n - 1; j++) {
          massAbove += stages.get(j).dryMass() + loads[j];
        }
        double burnout = massAbove + stage.dryMass();
        dvLower +=
            stage.propulsion().isp()
                * G0
                * FastMath.log((burnout + stage.propellantCapacity()) / burnout);
      }
      double dvTop = FastMath.max(0.0, dvTotal - dvLower);
      double finalMass = top.dryMass() + payloadMass;
      double raw =
          finalMass * (FastMath.exp(dvTop / exhaustVelocityTop) - 1.0) * (1.0 + SAFETY_MARGIN);
      topLoad = FastMath.min(raw, top.propellantCapacity());
    }
    loads[n - 1] = topLoad;
    return loads;
  }

  /**
   * Ideal ascent ΔV to a circular orbit (m/s): orbital speed plus gravity/steering losses minus
   * the Earth-rotation assist at the launch latitude.
   */
  static double ascentDeltaV(double targetAltitude, double launchLatitudeDeg) {
    double r = RE + targetAltitude;
    return FastMath.sqrt(MU / r)
        + ASCENT_LOSSES_MS
        - EQUATORIAL_ROTATION_MS * FastMath.cos(FastMath.toRadians(launchLatitudeDeg));
  }

  /** Hohmann perigee-injection ΔV from a circular parking orbit to a GEO-apogee transfer (m/s). */
  static double gtoInjectionDeltaV(double parkingAltitude) {
    double rLeo = RE + parkingAltitude;
    double rGeo = RE + GEO_ALTITUDE_M;
    return FastMath.sqrt(MU / rLeo) * (FastMath.sqrt(2.0 * rGeo / (rLeo + rGeo)) - 1.0);
  }

  /**
   * Combined circularization + plane-change ΔV at the GTO apogee (m/s). The plane change equals
   * the launch latitude (inclination of a due-east parking orbit).
   */
  static double apogeeCircularizationDeltaV(double parkingAltitude, double launchLatitudeDeg) {
    double rLeo = RE + parkingAltitude;
    double rGeo = RE + GEO_ALTITUDE_M;
    double semiMajor = 0.5 * (rLeo + rGeo);
    double vApogee = FastMath.sqrt(MU * (2.0 / rGeo - 1.0 / semiMajor));
    double vCircular = FastMath.sqrt(MU / rGeo);
    double planeChange = FastMath.toRadians(launchLatitudeDeg);
    return FastMath.sqrt(
        vApogee * vApogee
            + vCircular * vCircular
            - 2.0 * vApogee * vCircular * FastMath.cos(planeChange));
  }
}
