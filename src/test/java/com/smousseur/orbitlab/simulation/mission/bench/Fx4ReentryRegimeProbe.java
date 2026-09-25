package com.smousseur.orbitlab.simulation.mission.bench;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.disposal.DeorbitTail;
import com.smousseur.orbitlab.simulation.mission.ephemeris.DebrisTrack;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.MissionComposer;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.models.earth.atmosphere.Atmosphere;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * The re-entry regime of every object a production mission lets fall: how fast, how dense, how hot,
 * and for how long, sample by sample. NOT a gate: it changes no {@code src/main} and asserts
 * nothing. Run with {@code -Dorbitlab.probe=true --tests '*Fx4ReentryRegimeProbe*'}.
 *
 * <p>Each mission goes through the production computation ({@code MissionPlanOptimizer.compute()},
 * FAST). Every stretch of a trajectory below {@link #WINDOW_TOP_M} is then read against the very
 * atmosphere the mission flies ({@code OrekitService.atmosphere}): the air-relative speed, the
 * dynamic pressure {@code q = ½ ρ v_rel²}, and the Sutton-Graves stagnation heat flux {@code k
 * √(ρ/Rn) v_rel³} for a nominal one-metre nose, the textbook proxy for how brightly a shock layer
 * glows.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class Fx4ReentryRegimeProbe {

  /** Sutton-Graves constant for Earth air (kg^0.5 / m). */
  private static final double SUTTON_GRAVES_K = 1.7415e-4;

  /** Nominal nose radius the heat-flux proxy is quoted for (m). */
  private static final double NOSE_RADIUS_M = 1.0;

  /** A stretch of trajectory is read from the moment it drops below this altitude. */
  private static final double WINDOW_TOP_M = 120_000.0;

  private static final double[] PROFILE_LEVELS_KM = {
    120, 110, 100, 90, 80, 70, 60, 50, 40, 30, 20, 10
  };

  private static final double[] HEAT_LEVELS_KW = {1.0, 10.0, 100.0, 1000.0};

  private static final double ORBIT_ALTITUDE_M = 400_000.0;

  /** Mirror of the private {@code MissionRenderer.LANDED_ALTITUDE_METERS}. */
  private static final double LANDED_ALTITUDE_METERS = 1000.0;

  private static AbsoluteDate epoch;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  void reentryRegimeOfProductionMissions() {
    fly(
        "Falcon Heavy LEO 400 km, Kourou, EARTH_OBS_SAT",
        MissionFactory.specFromWizardValues(
            leoValues(Launchers.FALCON_HEAVY, Payloads.EARTH_OBSERVATION_SAT), MissionType.LEO));
    fly(
        "Ariane 64 LEO 400 km, Kourou, EARTH_OBS_SAT",
        MissionFactory.specFromWizardValues(
            leoValues(Launchers.ARIANE_64, Payloads.EARTH_OBSERVATION_SAT), MissionType.LEO));
    fly(
        "Falcon Heavy GEO, Kourou, GEO_SAT",
        MissionFactory.specFromWizardValues(geoValues(), MissionType.GEO));
    fly("Falcon Heavy LEO 400 km + disposal reserve (payload deorbited)", disposedLeoSpec());
  }

  private static void fly(String label, MissionSpec spec) {
    MissionEntry entry = new MissionEntry(MissionComposer.compose(spec, OptimizationType.FAST));
    long t0 = System.nanoTime();
    MissionComputeResult result = new MissionPlanOptimizer(entry, epoch).compute().computation();
    double seconds = (System.nanoTime() - t0) / 1e9;

    AtmosphereModel model = spec.atmosphere();
    AtmosphereModel read = model == AtmosphereModel.NONE ? AtmosphereModel.NRLMSISE : model;
    Atmosphere atmosphere = OrekitService.get().atmosphere(read, GravitationalContext.earth());

    System.out.printf(
        Locale.ROOT,
        "%n=== %s — compute() %.1f s, mission atmosphere %s (read against %s), %d debris%n",
        label,
        seconds,
        model,
        read,
        result.debris().size());

    report("primary", result.ephemeris().allPoints(), atmosphere);
    for (DebrisTrack track : result.debris()) {
      report(
          "debris " + track.role() + " #" + track.exemplarIndex(),
          track.ephemeris().allPoints(),
          atmosphere);
    }
  }

  private static void report(String object, List<MissionEphemerisPoint> points, Atmosphere air) {
    MissionEphemerisPoint last = points.getLast();
    double minAltitude =
        points.stream().mapToDouble(MissionEphemerisPoint::altitudeMeters).min().orElse(0.0);
    List<List<MissionEphemerisPoint>> stretches = stretchesBelowWindow(points);
    System.out.printf(
        Locale.ROOT,
        "%n  -- %s: %d points, last altitude %.1f km (%s), lowest %.1f km, %d stretch(es) below"
            + " %.0f km%n",
        object,
        points.size(),
        last.altitudeMeters() / 1000.0,
        last.altitudeMeters() < LANDED_ALTITUDE_METERS ? "landed" : "in flight",
        minAltitude / 1000.0,
        stretches.size(),
        WINDOW_TOP_M / 1000.0);
    for (List<MissionEphemerisPoint> stretch : stretches) {
      reportStretch(stretch, air);
    }
  }

  private static List<List<MissionEphemerisPoint>> stretchesBelowWindow(
      List<MissionEphemerisPoint> points) {
    List<List<MissionEphemerisPoint>> stretches = new ArrayList<>();
    List<MissionEphemerisPoint> current = null;
    for (MissionEphemerisPoint point : points) {
      if (point.altitudeMeters() < WINDOW_TOP_M) {
        if (current == null) {
          current = new ArrayList<>();
          stretches.add(current);
        }
        current.add(point);
      } else {
        current = null;
      }
    }
    return stretches;
  }

  private record Sample(
      double t, double altitudeKm, double vRelKmS, double density, double qKpa, double heatKw) {}

  private static void reportStretch(List<MissionEphemerisPoint> stretch, Atmosphere air) {
    AbsoluteDate start = stretch.getFirst().time();
    List<Sample> samples = new ArrayList<>();
    for (MissionEphemerisPoint point : stretch) {
      samples.add(sampleOf(point, start, air));
    }
    Sample first = samples.getFirst();
    Sample end = samples.getLast();
    boolean descending = end.altitudeKm() < first.altitudeKm();
    Sample peakQ = samples.getFirst();
    Sample peakHeat = samples.getFirst();
    double maxStep = 0.0;
    for (int i = 0; i < samples.size(); i++) {
      Sample s = samples.get(i);
      if (s.qKpa() > peakQ.qKpa()) {
        peakQ = s;
      }
      if (s.heatKw() > peakHeat.heatKw()) {
        peakHeat = s;
      }
      if (i > 0) {
        maxStep = Math.max(maxStep, s.t() - samples.get(i - 1).t());
      }
    }
    System.out.printf(
        Locale.ROOT,
        "     stretch '%s' -> '%s' (%s), %d samples over %.0f s (largest step %.1f s),"
            + " %.1f -> %.1f km, v_rel %.2f -> %.2f km/s%n",
        stretch.getFirst().stageName(),
        stretch.getLast().stageName(),
        descending ? "descending" : "ascending",
        samples.size(),
        end.t(),
        maxStep,
        first.altitudeKm(),
        end.altitudeKm(),
        first.vRelKmS(),
        end.vRelKmS());
    System.out.printf(
        Locale.ROOT,
        "     peak q %.2f kPa at %.1f km, v_rel %.2f km/s, t+%.0f s | peak heat %.1f kW/m² (Rn 1 m)"
            + " at %.1f km, v_rel %.2f km/s, t+%.0f s%n",
        peakQ.qKpa(),
        peakQ.altitudeKm(),
        peakQ.vRelKmS(),
        peakQ.t(),
        peakHeat.heatKw(),
        peakHeat.altitudeKm(),
        peakHeat.vRelKmS(),
        peakHeat.t());
    StringBuilder above = new StringBuilder("     time with heat above:");
    for (double level : HEAT_LEVELS_KW) {
      above.append(
          String.format(Locale.ROOT, "  %.0f kW/m² %.0f s", level, secondsAbove(samples, level)));
    }
    System.out.println(above);
    System.out.println(
        "     profile   alt km |   t+ s | v_rel km/s |   rho kg/m³ |   q kPa | heat kW/m²");
    for (double level : PROFILE_LEVELS_KM) {
      Sample crossing = crossing(samples, level, descending);
      if (crossing != null) {
        System.out.printf(
            Locale.ROOT,
            "               %6.0f | %6.0f | %10.2f | %11.3e | %7.2f | %10.1f%n",
            level,
            crossing.t(),
            crossing.vRelKmS(),
            crossing.density(),
            crossing.qKpa(),
            crossing.heatKw());
      }
    }
  }

  private static Sample sampleOf(MissionEphemerisPoint point, AbsoluteDate start, Atmosphere air) {
    double density;
    Vector3D airVelocity;
    try {
      density = air.getDensity(point.time(), point.position(), point.arc().frame());
      airVelocity = air.getVelocity(point.time(), point.position(), point.arc().frame());
    } catch (RuntimeException e) {
      density = Double.NaN;
      airVelocity = Vector3D.ZERO;
    }
    double vRel = point.velocity().subtract(airVelocity).getNorm();
    double q = 0.5 * density * vRel * vRel;
    double heat = SUTTON_GRAVES_K * Math.sqrt(density / NOSE_RADIUS_M) * vRel * vRel * vRel;
    return new Sample(
        point.time().durationFrom(start),
        point.altitudeMeters() / 1000.0,
        vRel / 1000.0,
        density,
        q / 1000.0,
        heat / 1000.0);
  }

  private static double secondsAbove(List<Sample> samples, double levelKw) {
    double total = 0.0;
    for (int i = 1; i < samples.size(); i++) {
      Sample a = samples.get(i - 1);
      Sample b = samples.get(i);
      if (0.5 * (a.heatKw() + b.heatKw()) >= levelKw) {
        total += b.t() - a.t();
      }
    }
    return total;
  }

  /**
   * The state where the stretch crosses {@code levelKm}, linearly interpolated between the two
   * samples that straddle it: the first downward crossing on a descent, the first upward one on an
   * ascent.
   */
  private static Sample crossing(List<Sample> samples, double levelKm, boolean descending) {
    for (int i = 1; i < samples.size(); i++) {
      Sample a = samples.get(i - 1);
      Sample b = samples.get(i);
      boolean straddles =
          descending
              ? a.altitudeKm() >= levelKm && b.altitudeKm() < levelKm
              : a.altitudeKm() <= levelKm && b.altitudeKm() > levelKm;
      if (straddles) {
        double f = (a.altitudeKm() - levelKm) / (a.altitudeKm() - b.altitudeKm());
        return new Sample(
            lerp(a.t(), b.t(), f),
            levelKm,
            lerp(a.vRelKmS(), b.vRelKmS(), f),
            lerp(a.density(), b.density(), f),
            lerp(a.qKpa(), b.qKpa(), f),
            lerp(a.heatKw(), b.heatKw(), f));
      }
    }
    return null;
  }

  private static double lerp(double a, double b, double f) {
    return a + f * (b - a);
  }

  private static MissionSpec disposedLeoSpec() {
    PayloadModel model = Payloads.EARTH_OBSERVATION_SAT;
    MissionSpec.EarthOrbit nominal =
        (MissionSpec.EarthOrbit)
            MissionFactory.specFromWizardValues(
                leoValues(Launchers.FALCON_HEAVY, model), MissionType.LEO);
    Spacecraft payload = nominal.configuration().payload();
    LauncherModel launcher = nominal.configuration().launcher();
    double reserve =
        PropellantBudget.disposalReserveFor(
            model, payload.dryMass(), ORBIT_ALTITUDE_M, DeorbitTail.REENTRY_PERIGEE_ALTITUDE_M);
    Spacecraft disposable =
        model.toSpacecraft(payload.dryMass(), payload.propellantLoad(), reserve);
    double[] loads =
        PropellantBudget.loadsForLeo(launcher, disposable, ORBIT_ALTITUDE_M, nominal.latitude());
    return new MissionSpec.EarthOrbit(
        nominal.name(),
        new LaunchConfiguration(launcher, loads, disposable, nominal.configuration().payloadId()),
        nominal.perigeeAltitude(),
        nominal.apogeeAltitude(),
        nominal.targetInclination(),
        nominal.nodeBranch(),
        nominal.targetRaan(),
        nominal.siteName(),
        nominal.latitude(),
        nominal.longitude(),
        nominal.altitude(),
        nominal.horizon(),
        nominal.atmosphere());
  }

  private static Map<String, Object> commonValues(
      String name, LauncherModel launcher, PayloadModel payload) {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", name);
    values.put("LAUNCH_SITE_NAME", "Kourou - French Guiana");
    values.put("LAUNCH_SITE_LAT", 5.23);
    values.put("LAUNCH_SITE_LONG", -52.77);
    values.put("LAUNCH_SITE_ALT", 0.0);
    values.put("LAUNCHER_TYPE", launcher.id());
    values.put("PAYLOAD_TYPE", payload.id());
    values.put("PAYLOAD_MASS", payload.defaultDryMass());
    return values;
  }

  private static Map<String, Object> leoValues(LauncherModel launcher, PayloadModel payload) {
    Map<String, Object> values = commonValues("FX-4 probe LEO 400", launcher, payload);
    values.put("LEO_PERIGEE_ALT", ORBIT_ALTITUDE_M / 1000.0);
    values.put("LEO_APOGEE_ALT", ORBIT_ALTITUDE_M / 1000.0);
    return values;
  }

  private static Map<String, Object> geoValues() {
    Map<String, Object> values =
        commonValues("FX-4 probe GEO", Launchers.FALCON_HEAVY, Payloads.GEO_SAT);
    values.put("GTO_PARKING_ALT", ORBIT_ALTITUDE_M / 1000.0);
    return values;
  }
}
