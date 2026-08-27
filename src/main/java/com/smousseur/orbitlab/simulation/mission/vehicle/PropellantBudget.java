package com.smousseur.orbitlab.simulation.mission.vehicle;

import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;
import java.util.List;
import org.hipparchus.util.FastMath;
import org.orekit.utils.Constants;

/**
 * Analytic propellant sizing — "just enough" loads per mission (spec 06 §4.3). Inverse Tsiolkovsky
 * computed top-down from the payload: every stage below the launcher's top stage flies fully loaded
 * (v1 — the gravity turn consumes them entirely anyway), only the top stage and the payload's AKM
 * are sized from the ΔV budget. A safety margin absorbs finite-burn and steering losses; loads are
 * clamped to capacity (an infeasibility diagnostic is a later increment).
 */
public final class PropellantBudget {
  private PropellantBudget() {}

  /** Safety margin applied to sized loads (finite-burn + steering losses + reserve). */
  public static final double SAFETY_MARGIN = 0.10;

  /**
   * Gravity + steering losses of the ascent (m/s). Calibrated on two MissionPerformanceReport
   * points of the FH LEO 400 km budget run (1 600 → 37.9 % S2 residual, 1 400 → 26.2 %; the
   * residual converges slowly because consumption shrinks with the load, slope ≈ 0.31 kg/kg). 1 260
   * is the fixed point for a ~12 % residual on the reference mission, and stays above the measured
   * real losses (≈ 1 100 m/s). Do not tighten further without the MassDepletionDetector guard: the
   * headroom over actual consumption is down to ~1 s of upper-stage burn.
   */
  public static final double ASCENT_LOSSES_MS = 1_260.0;

  /** Eastward velocity gained from Earth rotation at the equator (m/s). */
  private static final double EQUATORIAL_ROTATION_MS = 465.0;

  /** Mirrors GEOMission.GEO_ALTITUDE without depending on the operation package. */
  private static final double GEO_ALTITUDE_M = 35_786_000.0;

  /** Mean Earth-Moon distance (m): the semi-major axis of the lunar orbit, rounded. */
  private static final double LUNAR_DISTANCE_M = 384_400_000.0;

  // Off-flight sizing, left Earth-fixed by PHY-4 / L1 (spec docs/multi-corps/03-conception-L1.md
  // §4.1): propellant budgeting runs before any propagation and never sees an arc, so the L1 seam
  // does not run through it. It becomes contextual when a mission has to be sized around another
  // body — no earlier than L6.
  private static final double MU = Constants.WGS84_EARTH_MU;
  private static final double RE = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static final double G0 = Constants.G0_STANDARD_GRAVITY;

  /** Fixed-point iterations of the top-stage sizing (monotone contraction, converges fast). */
  private static final int SIZING_ITERATIONS = 12;

  /** Launcher loads plus the payload's apogee-kick-motor load for a GEO mission. */
  public record GeoLoads(double[] launcherLoads, double akmLoad) {}

  /**
   * Launcher loads plus the mass the translunar injection ignites at, for a lunar mission.
   *
   * <p><b>The second component is not an extra.</b> It is the very figure {@code
   * LunarLaunchWindowProblem}'s confirming solve asks for, and the top-down sizing already knows
   * it: one definition, two consumers (MIS-4 / L5 §5.3).
   *
   * @param launcherLoads the propellant load per stage, same order as the launcher stages
   * @param massAtInjection the vehicle mass when the injection burn ignites (kg)
   */
  public record LunarLoads(double[] launcherLoads, double massAtInjection) {}

  /**
   * Per-stage loads for an Earth-orbit mission launched due east — the site's free plane.
   *
   * @param launcher the launcher model
   * @param payload the payload as flown (its mass anchors the top-down sizing)
   * @param targetAltitude the target orbit altitude (m); use the apogee for elliptic targets
   * @param launchLatitudeDeg the launch site latitude (degrees)
   * @return the propellant load per stage, same order as the launcher stages
   */
  public static double[] loadsForLeo(
      LauncherModel launcher, Spacecraft payload, double targetAltitude, double launchLatitudeDeg) {
    return loadsForLeo(
        launcher, payload, targetAltitude, launchLatitudeDeg, Physics.getLaunchAzimuth());
  }

  /**
   * Per-stage loads for an Earth-orbit mission launched at a given azimuth.
   *
   * @param launcher the launcher model
   * @param payload the payload as flown (its mass anchors the top-down sizing)
   * @param targetAltitude the target orbit altitude (m); use the apogee for elliptic targets
   * @param launchLatitudeDeg the launch site latitude (degrees)
   * @param launchAzimuth the launch azimuth (radians, clockwise from north)
   * @return the propellant load per stage, same order as the launcher stages
   */
  public static double[] loadsForLeo(
      LauncherModel launcher,
      Spacecraft payload,
      double targetAltitude,
      double launchLatitudeDeg,
      double launchAzimuth) {
    double dvTotal = ascentDeltaV(targetAltitude, launchLatitudeDeg, launchAzimuth);
    return sizeTopStage(launcher, payload.getMass(), dvTotal);
  }

  /**
   * Launcher loads and AKM load for a GEO mission (parking → GTO → GEO). The split GEO profile
   * (spec 06 I5) assigns the ascent residual and the GTO injection to the launcher's top stage, and
   * the apogee circularization + plane change to the payload's kick motor.
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
    return loadsForHighOrbit(
        launcher,
        payload,
        payloadDryMass,
        parkingAltitude,
        GEO_ALTITUDE_M,
        launchLatitudeDeg,
        launchLatitudeDeg,
        Physics.getLaunchAzimuth());
  }

  /**
   * Per-stage loads for a lunar mission: ascent to the parking orbit, then one translunar
   * injection. <b>Simpler than {@link #loadsForGeo}</b> — the payload is inert, so there is no kick
   * motor to delegate a burn to and nothing to split the budget with (MIS-4 / L5 §5.3).
   *
   * <p><b>The injection ΔV is taken in closed form</b> rather than written as a constant, because
   * the parking altitude is a component of the spec and a constant would freeze one value of it in
   * a class whose whole idiom is the closed form. It reads some 40 m/s under the 3 124 m/s L4
   * measured at 400 km — 1.3 %, the 170° transfer angle and the aim offset not being in the
   * formula. The 10 % margin is worth 312 m/s at this scale, so the gap is absorbed three times
   * over, and the window's own 50 m/s acceptance margin absorbs it once more.
   *
   * <p><b>A heuristic seed, not a verdict.</b> {@code MissionPlanOptimizer} sweeps λ from these
   * loads on {@code PRECISE}; on {@code FAST} — what every mission created in the wizard flies —
   * they are what the vehicle takes off with.
   *
   * @param launcher the launcher model
   * @param payload the payload as flown (its mass anchors the top-down sizing)
   * @param parkingAltitude the circular parking altitude the injection leaves from (m)
   * @param launchLatitudeDeg the launch site latitude (degrees)
   * @param launchAzimuth the launch azimuth (radians, clockwise from north)
   * @return the launcher loads and the mass at injection
   */
  public static LunarLoads loadsForLunar(
      LauncherModel launcher,
      Spacecraft payload,
      double parkingAltitude,
      double launchLatitudeDeg,
      double launchAzimuth) {
    double payloadMass = payload.getMass();
    double dvInjection = translunarInjectionDeltaV(parkingAltitude);
    double dvTotal = ascentDeltaV(parkingAltitude, launchLatitudeDeg, launchAzimuth) + dvInjection;
    double[] loads = sizeTopStage(launcher, payloadMass, dvTotal);

    StageModel top = launcher.stages().getLast();
    double afterInjection = payloadMass + top.dryMass();
    double injectionPropellant =
        afterInjection
            * (FastMath.exp(dvInjection / (top.propulsion().isp() * G0)) - 1.0)
            * (1.0 + SAFETY_MARGIN);
    // The stage cannot ignite heavier than it lifted off loaded. The two sizings share a margin, so
    // this only bites on a payload the launcher barely lifts — where the load is clamped to
    // capacity and the ascent has already eaten into what the injection was meant to keep.
    double loaded = afterInjection + loads[loads.length - 1];
    return new LunarLoads(loads, FastMath.min(afterInjection + injectionPropellant, loaded));
  }

  /**
   * Launcher loads and AKM load for any high circular orbit reached through a parking orbit —
   * geostationary, medium Earth, or anything else the direct chain cannot reach (spec {@code
   * docs/earth-orbit/01-mission-terre-parametrable.md} §6).
   *
   * <p><b>The plane change is an argument, and that is the point.</b> A GEO mission cancels the
   * whole launch inclination at apogee, which is why {@link #loadsForGeo} passes the site latitude
   * for it. A MEO does not: since MIS-7 the ascent is <em>steered</em> into the target plane, so
   * what is left at apogee is the residual and not the latitude. Charging a 55° MEO for a 5.23°
   * plane change it has already flown would size its kick motor for a burn it never makes.
   *
   * @param launcher the launcher model
   * @param payload the payload model (provides the AKM characteristics)
   * @param payloadDryMass the dry mass entered at mission creation (kg)
   * @param parkingAltitude the parking orbit altitude (m)
   * @param targetAltitude the final circular orbit altitude (m)
   * @param launchLatitudeDeg the launch site latitude (degrees)
   * @param planeChangeDeg the plane change performed at apogee (degrees)
   * @param launchAzimuth the launch azimuth (radians, clockwise from north)
   * @return the launcher loads and the AKM load
   */
  public static GeoLoads loadsForHighOrbit(
      LauncherModel launcher,
      PayloadModel payload,
      double payloadDryMass,
      double parkingAltitude,
      double targetAltitude,
      double launchLatitudeDeg,
      double planeChangeDeg,
      double launchAzimuth) {
    double dvApogee = apogeeCircularizationDeltaV(parkingAltitude, targetAltitude, planeChangeDeg);

    double akmLoad = 0.0;
    if (payload.akmPropellantCapacity() > 0) {
      double exhaustVelocity = payload.akmPropulsion().isp() * G0;
      double raw =
          payloadDryMass * (FastMath.exp(dvApogee / exhaustVelocity) - 1.0) * (1.0 + SAFETY_MARGIN);
      akmLoad = FastMath.min(raw, payload.akmPropellantCapacity());
    }

    double dvTotal =
        ascentDeltaV(parkingAltitude, launchLatitudeDeg, launchAzimuth)
            + transferInjectionDeltaV(parkingAltitude, targetAltitude);
    double[] launcherLoads = sizeTopStage(launcher, payloadDryMass + akmLoad, dvTotal);
    return new GeoLoads(launcherLoads, akmLoad);
  }

  /**
   * Sizes the top stage for the ΔV left over by the fully-loaded lower stages. Fixed-point
   * iteration: the lower stages' ΔV depends on the mass above them, which depends on the sized top
   * load. Solid top stages fly full (no sizing degree of freedom).
   */
  private static double[] sizeTopStage(LauncherModel launcher, double payloadMass, double dvTotal) {
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
   * Ideal ascent ΔV to a circular orbit launched due east (m/s).
   *
   * @param targetAltitude the target orbit altitude (m)
   * @param launchLatitudeDeg the launch site latitude (degrees)
   * @return the ascent ΔV in m/s
   */
  static double ascentDeltaV(double targetAltitude, double launchLatitudeDeg) {
    return ascentDeltaV(targetAltitude, launchLatitudeDeg, Physics.getLaunchAzimuth());
  }

  /**
   * Ideal ascent ΔV to a circular orbit (m/s): orbital speed plus gravity/steering losses minus the
   * Earth-rotation assist.
   *
   * <p><b>The assist is signed and projected on the azimuth</b> (spec {@code
   * docs/earth-orbit/01-mission-terre-parametrable.md} §7). It used to be {@code 465 · cos φ}, the
   * full eastward entrainment, credited whatever the heading — correct due east and wrong
   * everywhere else. A polar launch from Kourou uses none of it (the entrainment is perpendicular
   * to the flight), and a retrograde sun-synchronous one <em>pays</em> for it. Getting this wrong
   * is not a margin detail: on an inverse-Tsiolkovsky budget, the 529 m/s error of an SSO from
   * Kourou is tonnes on the upper-stage load.
   *
   * <p>What is <em>not</em> in here is the steering loss of turning the plane during the climb
   * (spec §4.1). It has no closed form; {@link #SAFETY_MARGIN} absorbs it, and {@code
   * AscentPlaneControlTest} measures it. No value is hard-coded until it is measured.
   *
   * @param targetAltitude the target orbit altitude (m)
   * @param launchLatitudeDeg the launch site latitude (degrees)
   * @param launchAzimuth the launch azimuth (radians, clockwise from north)
   * @return the ascent ΔV in m/s
   */
  static double ascentDeltaV(
      double targetAltitude, double launchLatitudeDeg, double launchAzimuth) {
    double r = RE + targetAltitude;
    double assist =
        EQUATORIAL_ROTATION_MS
            * FastMath.cos(FastMath.toRadians(launchLatitudeDeg))
            * FastMath.sin(launchAzimuth);
    return FastMath.sqrt(MU / r) + ASCENT_LOSSES_MS - assist;
  }

  /** Hohmann perigee-injection ΔV from a circular parking orbit to a GEO-apogee transfer (m/s). */
  static double gtoInjectionDeltaV(double parkingAltitude) {
    return transferInjectionDeltaV(parkingAltitude, GEO_ALTITUDE_M);
  }

  /**
   * Hohmann perigee-injection ΔV from a circular parking orbit to a transfer reaching a given
   * apogee (m/s).
   *
   * @param parkingAltitude the parking orbit altitude (m)
   * @param targetAltitude the transfer apogee altitude (m)
   * @return the injection ΔV in m/s
   */
  static double transferInjectionDeltaV(double parkingAltitude, double targetAltitude) {
    double rLeo = RE + parkingAltitude;
    double rTarget = RE + targetAltitude;
    return FastMath.sqrt(MU / rLeo) * (FastMath.sqrt(2.0 * rTarget / (rLeo + rTarget)) - 1.0);
  }

  /**
   * Hohmann perigee-injection ΔV from a circular parking orbit to a transfer reaching the Moon's
   * mean distance (m/s).
   *
   * @param parkingAltitude the parking orbit altitude (m)
   * @return the translunar injection ΔV in m/s
   */
  static double translunarInjectionDeltaV(double parkingAltitude) {
    // transferInjectionDeltaV works in altitudes, so the lunar distance is handed over as one.
    return transferInjectionDeltaV(parkingAltitude, LUNAR_DISTANCE_M - RE);
  }

  /**
   * Combined circularization + plane-change ΔV at the GTO apogee (m/s). The plane change equals the
   * launch latitude (inclination of a due-east parking orbit).
   */
  static double apogeeCircularizationDeltaV(double parkingAltitude, double launchLatitudeDeg) {
    return apogeeCircularizationDeltaV(parkingAltitude, GEO_ALTITUDE_M, launchLatitudeDeg);
  }

  /**
   * Combined circularization + plane-change ΔV at the apogee of a transfer orbit (m/s).
   *
   * @param parkingAltitude the parking orbit altitude (m)
   * @param targetAltitude the final circular orbit altitude (m)
   * @param planeChangeDeg the plane rotation performed by the same burn (degrees)
   * @return the apogee burn ΔV in m/s
   */
  static double apogeeCircularizationDeltaV(
      double parkingAltitude, double targetAltitude, double planeChangeDeg) {
    double rLeo = RE + parkingAltitude;
    double rTarget = RE + targetAltitude;
    double semiMajor = 0.5 * (rLeo + rTarget);
    double vApogee = FastMath.sqrt(MU * (2.0 / rTarget - 1.0 / semiMajor));
    double vCircular = FastMath.sqrt(MU / rTarget);
    double planeChange = FastMath.toRadians(planeChangeDeg);
    return FastMath.sqrt(
        vApogee * vApogee
            + vCircular * vCircular
            - 2.0 * vApogee * vCircular * FastMath.cos(planeChange));
  }
}
