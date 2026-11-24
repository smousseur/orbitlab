package com.smousseur.orbitlab.simulation.mission.bench;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.DebrisTrack;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import com.smousseur.orbitlab.states.mission.ReentryHeatProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * and for how long, sample by sample — and what the render-side {@link ReentryHeatProfile} costs to
 * build. NOT a gate: it changes no {@code src/main} and asserts nothing. Run with {@code
 * -Dorbitlab.probe=true --tests '*Fx4ReentryRegimeProbe*'}.
 *
 * <p>Five missions, all built from wizard values by {@code MissionFactory.specFromWizardValues} and
 * computed by {@code MissionPlanOptimizer.compute()} in FAST, from Kourou: Falcon Heavy and Ariane
 * 64 to a 400 km LEO and to GEO, and a Falcon Heavy LEO with {@code DEORBIT = TRUE}, whose payload
 * falls. Each entry is built from its spec, as the wizard builds it: an entry that carries its spec
 * routes an Earth orbit in FAST through the measured-load planner, as in production, where an entry
 * wrapping a pre-composed mission would take the fixed-load one.
 *
 * <p>The flux of every sample is read through {@link ReentryHeatProfile#samples()}, on the raw
 * ephemeris {@code compute()} returns, against the atmosphere the mission flies ({@code
 * Mission.getAtmosphere()}). A <em>stretch</em> is a run of consecutive samples in the profile's
 * heating domain — Earth arc, descending, below {@link #WINDOW_TOP_M}. The probe evaluates the
 * air-relative speed, the density and the dynamic pressure {@code q = ½ ρ v_rel²} itself for the
 * extra columns, and the flux at the downward crossing of {@link #WINDOW_TOP_M}, where the sample
 * above lies outside the profile's domain.
 *
 * <p>The cost of {@link ReentryHeatProfile#forMission} is timed per object: the first call on that
 * object, then the median of {@link #WARM_REPETITIONS} repetitions. The closing section applies the
 * rules fixed before the measurement — threshold, ceiling, thread, atmosphere failures — and prints
 * the intensity each piece reaches at its peak under the retained threshold and ceiling.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class Fx4ReentryRegimeProbe {

  /** Sutton-Graves constant for Earth air (kg^0.5 / m), as in {@link ReentryHeatProfile}. */
  private static final double SUTTON_GRAVES_K = 1.7415e-4;

  /** Nominal nose radius the heat-flux proxy is quoted for (m), as in the profile. */
  private static final double NOSE_RADIUS_M = 1.0;

  /** Top of the profile's heating domain (m). */
  private static final double WINDOW_TOP_M = 120_000.0;

  /** The threshold under test (W/m²). */
  private static final double THRESHOLD_WM2 = 100_000.0;

  /**
   * Any ceiling above the threshold: the measurement pass reads only the flux, which does not
   * depend on it. The retained ceiling is derived at the end and the profiles rebuilt with it.
   */
  private static final double PROVISIONAL_CEILING_WM2 = 3_000_000.0;

  /**
   * One frame at 60 Hz (ms): the budget a whole mission's profile must fit to stay on the render
   * thread.
   */
  private static final double FRAME_BUDGET_MS = 16.0;

  private static final int WARM_REPETITIONS = 10;

  private static final double[] PROFILE_LEVELS_KM = {110, 100, 90, 80, 70, 60, 50, 40, 30, 20, 10};

  private static final double[] HEAT_LEVELS_KW = {1.0, 10.0, 100.0, 1000.0};

  private static final double ORBIT_ALTITUDE_M = 400_000.0;

  /** Mirror of the private {@code MissionRenderer.LANDED_ALTITUDE_METERS}. */
  private static final double LANDED_ALTITUDE_METERS = 1000.0;

  private static AbsoluteDate epoch;

  private final List<Piece> pieces = new ArrayList<>();
  private final List<Crossing> crossings = new ArrayList<>();
  private final Map<String, Double> warmMissionMediansMs = new LinkedHashMap<>();
  private final List<String> failedMissions = new ArrayList<>();
  private int attemptedMissions;
  private int totalFailures;

  /**
   * An object that re-entered, with what the closing rules need of it. Re-entering means reaching
   * the ground: a primary left in orbit still shows heating stretches (its pad sample, a transfer
   * dip below {@link #WINDOW_TOP_M}) without re-entering, and the threshold rule does not apply to
   * it.
   */
  private record Piece(
      String label,
      MissionEphemeris ephemeris,
      AtmosphereModel model,
      double peakFluxWm2,
      int peakIndex) {}

  /** The flux one stretch carried as it crossed {@link #WINDOW_TOP_M} downwards. */
  private record Crossing(String label, double fluxWm2) {}

  private record TrackedObject(String label, MissionEphemeris ephemeris) {}

  private record Sample(
      double t, double altitudeKm, double vRelKmS, double density, double qKpa, double heatKw) {}

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  void reentryRegimeOfProductionMissions() {
    fly(
        "Falcon Heavy LEO 400 km, Kourou, EARTH_OBS_SAT",
        MissionType.LEO,
        leoValues(Launchers.FALCON_HEAVY));
    fly(
        "Ariane 64 LEO 400 km, Kourou, EARTH_OBS_SAT",
        MissionType.LEO,
        leoValues(Launchers.ARIANE_64));
    fly("Falcon Heavy GEO, Kourou, GEO_SAT", MissionType.GEO, geoValues(Launchers.FALCON_HEAVY));
    fly("Ariane 64 GEO, Kourou, GEO_SAT", MissionType.GEO, geoValues(Launchers.ARIANE_64));
    Map<String, Object> deorbit = leoValues(Launchers.FALCON_HEAVY);
    deorbit.put("DEORBIT", Boolean.TRUE);
    fly("Falcon Heavy LEO 400 km, DEORBIT = TRUE, EARTH_OBS_SAT", MissionType.LEO, deorbit);
    printRules();
  }

  private void fly(String label, MissionType type, Map<String, Object> values) {
    attemptedMissions++;
    long t0 = System.nanoTime();
    MissionComputeResult result;
    try {
      MissionSpec spec = MissionFactory.specFromWizardValues(values, type);
      MissionEntry entry = new MissionEntry(spec);
      if (entry.getOptimizationType() != OptimizationType.FAST) {
        throw new IllegalStateException(
            "the entry composed " + entry.getOptimizationType() + " instead of FAST");
      }
      result = new MissionPlanOptimizer(entry, epoch).compute().computation();
    } catch (RuntimeException e) {
      System.out.printf(Locale.ROOT, "%n=== %s — compute() FAILED: %s%n", label, e);
      failedMissions.add(label);
      return;
    }
    double seconds = (System.nanoTime() - t0) / 1e9;
    AtmosphereModel model = result.mission().getAtmosphere();

    List<TrackedObject> objects = new ArrayList<>();
    objects.add(new TrackedObject("primary", result.ephemeris()));
    for (DebrisTrack track : result.debris()) {
      objects.add(
          new TrackedObject(
              "debris " + track.role() + " #" + track.exemplarIndex(), track.ephemeris()));
    }
    System.out.printf(
        Locale.ROOT,
        "%n=== %s — compute() %.1f s, atmosphere %s, ephemeris %s, %d debris%n",
        label,
        seconds,
        model,
        result.ephemeris().isComplete() ? "complete" : "INCOMPLETE",
        result.debris().size());
    if (model == AtmosphereModel.NONE) {
      System.out.println("  no atmosphere: every profile is empty, nothing to read");
      return;
    }

    ReentryHeatProfile[] profiles = new ReentryHeatProfile[objects.size()];
    double[] coldMs = new double[objects.size()];
    for (int i = 0; i < objects.size(); i++) {
      long start = System.nanoTime();
      profiles[i] =
          ReentryHeatProfile.forMission(
              objects.get(i).ephemeris(), model, THRESHOLD_WM2, PROVISIONAL_CEILING_WM2);
      coldMs[i] = (System.nanoTime() - start) / 1e6;
    }
    double[][] warmMs = new double[objects.size()][WARM_REPETITIONS];
    double[] passTotalsMs = new double[WARM_REPETITIONS];
    for (int r = 0; r < WARM_REPETITIONS; r++) {
      for (int i = 0; i < objects.size(); i++) {
        long start = System.nanoTime();
        ReentryHeatProfile.forMission(
            objects.get(i).ephemeris(), model, THRESHOLD_WM2, PROVISIONAL_CEILING_WM2);
        warmMs[i][r] = (System.nanoTime() - start) / 1e6;
        passTotalsMs[r] += warmMs[i][r];
      }
    }

    Atmosphere air = OrekitService.get().atmosphere(model, GravitationalContext.earth());
    int missionFailures = 0;
    for (int i = 0; i < objects.size(); i++) {
      missionFailures += profiles[i].atmosphereFailures();
      report(label, objects.get(i), profiles[i], air, model);
    }
    totalFailures += missionFailures;
    System.out.printf(
        Locale.ROOT, "%n  atmosphere failures in the heating domain: %d%n", missionFailures);

    System.out.printf(
        Locale.ROOT,
        "  profile cost (forMission; cold = first call on the object in this JVM, warm = median"
            + " of %d)%n",
        WARM_REPETITIONS);
    double coldTotal = 0.0;
    for (int i = 0; i < objects.size(); i++) {
      coldTotal += coldMs[i];
      System.out.printf(
          Locale.ROOT,
          "    %-28s %7d points  cold %8.2f ms  warm %8.2f ms%n",
          objects.get(i).label(),
          objects.get(i).ephemeris().size(),
          coldMs[i],
          median(warmMs[i]));
    }
    double warmMission = median(passTotalsMs);
    warmMissionMediansMs.put(label, warmMission);
    System.out.printf(
        Locale.ROOT,
        "    whole mission                 cold %8.2f ms  warm %8.2f ms (frame budget %.0f ms)%n",
        coldTotal,
        warmMission,
        FRAME_BUDGET_MS);
  }

  private void report(
      String mission,
      TrackedObject object,
      ReentryHeatProfile profile,
      Atmosphere air,
      AtmosphereModel model) {
    List<MissionEphemerisPoint> points = object.ephemeris().allPoints();
    List<ReentryHeatProfile.HeatSample> heat = profile.samples();
    MissionEphemerisPoint last = points.getLast();
    double minAltitude =
        points.stream().mapToDouble(MissionEphemerisPoint::altitudeMeters).min().orElse(0.0);
    List<int[]> stretches = heatingStretches(points);
    boolean landed = last.altitudeMeters() < LANDED_ALTITUDE_METERS;
    System.out.printf(
        Locale.ROOT,
        "%n  -- %s: %d points, last altitude %.1f km (%s), lowest %.1f km, %d heating stretch(es),"
            + " %d atmosphere failure(s)%n",
        object.label(),
        points.size(),
        last.altitudeMeters() / 1000.0,
        landed ? "landed" : "in flight",
        minAltitude / 1000.0,
        stretches.size(),
        profile.atmosphereFailures());
    if (stretches.isEmpty()) {
      return;
    }
    String label = mission + " / " + object.label();
    double peakFlux = 0.0;
    int peakIndex = stretches.getFirst()[0];
    double disagreement = 0.0;
    for (int[] stretch : stretches) {
      for (int k = stretch[0]; k < stretch[1]; k++) {
        double flux = heat.get(k).fluxWm2();
        if (flux > peakFlux) {
          peakFlux = flux;
          peakIndex = k;
        }
      }
      disagreement = Math.max(disagreement, reportStretch(label, points, heat, stretch, air));
    }
    System.out.printf(
        Locale.ROOT,
        "     flux check: largest relative gap between the profile and the probe's own"
            + " evaluation %.2e%n",
        disagreement);
    if (landed) {
      pieces.add(new Piece(label, object.ephemeris(), model, peakFlux, peakIndex));
    }
  }

  /**
   * The runs of consecutive samples inside {@link ReentryHeatProfile}'s heating domain, as {@code
   * [first, end)} index pairs. The domain is restated here because the profile does not expose it.
   */
  private static List<int[]> heatingStretches(List<MissionEphemerisPoint> points) {
    List<int[]> stretches = new ArrayList<>();
    int first = -1;
    for (int i = 0; i <= points.size(); i++) {
      boolean inside = i < points.size() && inHeatingDomain(points.get(i));
      if (inside && first < 0) {
        first = i;
      } else if (!inside && first >= 0) {
        stretches.add(new int[] {first, i});
        first = -1;
      }
    }
    return stretches;
  }

  private static boolean inHeatingDomain(MissionEphemerisPoint point) {
    return point.arc().body() == SolarSystemBody.EARTH
        && point.position().dotProduct(point.velocity()) < 0.0
        && point.altitudeMeters() < WINDOW_TOP_M;
  }

  /**
   * Prints one stretch, its flux read from the profile and its other columns evaluated by the
   * probe.
   *
   * @return the largest relative gap between the profile's flux and the probe's own evaluation of
   *     it, over the samples where both are available
   */
  private double reportStretch(
      String label,
      List<MissionEphemerisPoint> points,
      List<ReentryHeatProfile.HeatSample> heat,
      int[] stretch,
      Atmosphere air) {
    AbsoluteDate start = points.get(stretch[0]).time();
    List<Sample> samples = new ArrayList<>();
    double disagreement = 0.0;
    for (int k = stretch[0]; k < stretch[1]; k++) {
      Sample own = sampleOf(points.get(k), start, air);
      double flux = heat.get(k).fluxWm2();
      if (Double.isFinite(own.heatKw()) && flux > 0.0) {
        disagreement = Math.max(disagreement, Math.abs(own.heatKw() * 1000.0 - flux) / flux);
      }
      samples.add(
          new Sample(
              own.t(),
              own.altitudeKm(),
              own.vRelKmS(),
              own.density(),
              own.qKpa(),
              heat.get(k).fluxWm2() / 1000.0));
    }
    Sample first = samples.getFirst();
    Sample end = samples.getLast();
    Sample peakQ = first;
    Sample peakHeat = first;
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
        "     stretch '%s' -> '%s', %d samples over %.0f s (largest step %.1f s),"
            + " %.1f -> %.1f km, v_rel %.2f -> %.2f km/s%n",
        points.get(stretch[0]).stageName(),
        points.get(stretch[1] - 1).stageName(),
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
    Sample top = topCrossing(label, points, stretch, air);
    if (top == null) {
      System.out.printf(
          Locale.ROOT,
          "                  120 | no downward crossing: the stretch starts at %.1f km%n",
          first.altitudeKm());
    } else {
      printRow(top);
    }
    for (double level : PROFILE_LEVELS_KM) {
      Sample crossing = crossing(samples, level);
      if (crossing != null) {
        printRow(crossing);
      }
    }
    return disagreement;
  }

  /**
   * The state at the downward crossing of {@link #WINDOW_TOP_M}, between the last sample above it
   * and the first sample of the stretch, or {@code null} when the stretch does not begin with that
   * crossing (it starts at a culmination below the window, or at a jettison). Both samples are
   * evaluated by the probe, flux included: the one above the window lies outside the profile's
   * domain, where the profile reads zero. Its time is counted from the first sample of the stretch,
   * so it is slightly negative.
   */
  private Sample topCrossing(
      String label, List<MissionEphemerisPoint> points, int[] stretch, Atmosphere air) {
    int firstIndex = stretch[0];
    if (firstIndex == 0) {
      return null;
    }
    MissionEphemerisPoint above = points.get(firstIndex - 1);
    if (above.arc().body() != SolarSystemBody.EARTH || above.altitudeMeters() < WINDOW_TOP_M) {
      return null;
    }
    AbsoluteDate start = points.get(firstIndex).time();
    Sample a = sampleOf(above, start, air);
    Sample b = sampleOf(points.get(firstIndex), start, air);
    Sample top = interpolate(a, b, WINDOW_TOP_M / 1000.0);
    crossings.add(new Crossing(label, top.heatKw() * 1000.0));
    return top;
  }

  private static void printRow(Sample s) {
    System.out.printf(
        Locale.ROOT,
        "               %6.0f | %6.0f | %10.2f | %11.3e | %7.2f | %10.1f%n",
        s.altitudeKm(),
        s.t(),
        s.vRelKmS(),
        s.density(),
        s.qKpa(),
        s.heatKw());
  }

  /**
   * The probe's own evaluation of a point: air-relative speed, density, dynamic pressure and
   * Sutton-Graves flux, NaN density on an atmosphere failure.
   */
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

  /** The first downward crossing of {@code levelKm} inside a stretch, or {@code null}. */
  private static Sample crossing(List<Sample> samples, double levelKm) {
    for (int i = 1; i < samples.size(); i++) {
      Sample a = samples.get(i - 1);
      Sample b = samples.get(i);
      if (a.altitudeKm() >= levelKm && b.altitudeKm() < levelKm) {
        return interpolate(a, b, levelKm);
      }
    }
    return null;
  }

  /** Every column linearly interpolated in altitude between two samples straddling a level. */
  private static Sample interpolate(Sample a, Sample b, double levelKm) {
    double f = (a.altitudeKm() - levelKm) / (a.altitudeKm() - b.altitudeKm());
    return new Sample(
        lerp(a.t(), b.t(), f),
        levelKm,
        lerp(a.vRelKmS(), b.vRelKmS(), f),
        lerp(a.density(), b.density(), f),
        lerp(a.qKpa(), b.qKpa(), f),
        lerp(a.heatKw(), b.heatKw(), f));
  }

  private static double lerp(double a, double b, double f) {
    return a + f * (b - a);
  }

  private static double median(double[] values) {
    double[] sorted = values.clone();
    Arrays.sort(sorted);
    int mid = sorted.length / 2;
    return sorted.length % 2 == 0 ? 0.5 * (sorted[mid - 1] + sorted[mid]) : sorted[mid];
  }

  /** The rules fixed before the measurement, applied to what the five missions gave. */
  private void printRules() {
    System.out.printf(
        Locale.ROOT,
        "%n=== Rules fixed before the measurement (%d of %d missions computed%s) ===%n",
        attemptedMissions - failedMissions.size(),
        attemptedMissions,
        failedMissions.isEmpty() ? "" : "; failed: " + String.join(", ", failedMissions));

    System.out.printf(Locale.ROOT, "%nThreshold %.0f kW/m²%n", THRESHOLD_WM2 / 1000.0);
    boolean belowCrossing;
    if (crossings.isEmpty()) {
      belowCrossing = false;
      System.out.println(
          "  no downward 120 km crossing was measured: the first check cannot be applied");
    } else {
      Crossing hottest = crossings.getFirst();
      for (Crossing c : crossings) {
        if (c.fluxWm2() > hottest.fluxWm2()) {
          hottest = c;
        }
      }
      belowCrossing = 3.0 * hottest.fluxWm2() < THRESHOLD_WM2;
      System.out.printf(
          Locale.ROOT,
          "  highest flux at a 120 km downward crossing: %.3f kW/m² (%s), x3 = %.3f kW/m² -> %s%n",
          hottest.fluxWm2() / 1000.0,
          hottest.label(),
          3.0 * hottest.fluxWm2() / 1000.0,
          belowCrossing ? "below the threshold" : "NOT below the threshold");
    }
    List<String> cold = new ArrayList<>();
    for (Piece piece : pieces) {
      if (piece.peakFluxWm2() <= THRESHOLD_WM2) {
        cold.add(
            String.format(
                Locale.ROOT, "%s (%.1f kW/m²)", piece.label(), piece.peakFluxWm2() / 1000.0));
      }
    }
    System.out.printf(
        Locale.ROOT,
        "  pieces whose peak does not exceed the threshold: %s%n",
        cold.isEmpty() ? "none" : String.join("; ", cold));
    System.out.printf(
        Locale.ROOT,
        "  verdict: %s%n",
        belowCrossing && cold.isEmpty() && !pieces.isEmpty()
            ? "threshold confirmed"
            : "back to the threshold decision, before L1");

    if (pieces.isEmpty()) {
      System.out.println("\nCeiling: no piece re-entered, nothing to derive");
    } else {
      Piece hottest = pieces.getFirst();
      for (Piece piece : pieces) {
        if (piece.peakFluxWm2() > hottest.peakFluxWm2()) {
          hottest = piece;
        }
      }
      double ceiling = Math.ceil(hottest.peakFluxWm2() / 1e6) * 1e6;
      System.out.printf(
          Locale.ROOT,
          "%nCeiling: highest peak %.3f MW/m² (%s) -> ceiling %.0f MW/m²%n",
          hottest.peakFluxWm2() / 1e6,
          hottest.label(),
          ceiling / 1e6);
      System.out.printf(
          Locale.ROOT,
          "%nIntensity of each piece at its peak (threshold %.0f kW/m², ceiling %.0f MW/m²,"
              + " profile rebuilt with them)%n",
          THRESHOLD_WM2 / 1000.0,
          ceiling / 1e6);
      for (Piece piece : pieces) {
        double intensity =
            ReentryHeatProfile.forMission(piece.ephemeris(), piece.model(), THRESHOLD_WM2, ceiling)
                .samples()
                .get(piece.peakIndex())
                .intensity();
        System.out.printf(
            Locale.ROOT,
            "  %-80s peak %9.1f kW/m² -> intensity %.3f%n",
            piece.label(),
            piece.peakFluxWm2() / 1000.0,
            intensity);
      }
    }

    System.out.printf(
        Locale.ROOT, "%nThread: warm median of a whole mission vs %.0f ms%n", FRAME_BUDGET_MS);
    boolean fits = !warmMissionMediansMs.isEmpty();
    for (Map.Entry<String, Double> e : warmMissionMediansMs.entrySet()) {
      fits &= e.getValue() <= FRAME_BUDGET_MS;
      System.out.printf(Locale.ROOT, "  %-60s %8.2f ms%n", e.getKey(), e.getValue());
    }
    System.out.printf(
        Locale.ROOT,
        "  verdict: %s%n",
        fits ? "render thread" : "off the render thread (L1 designs the asynchronous build)");

    System.out.printf(
        Locale.ROOT,
        "%nAtmosphere failures in the heating domain: %d -> %s%n",
        totalFailures,
        totalFailures == 0 ? "none" : "to raise with the user before L1");
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

  private static Map<String, Object> leoValues(LauncherModel launcher) {
    Map<String, Object> values =
        commonValues("FX-4 probe LEO 400", launcher, Payloads.EARTH_OBSERVATION_SAT);
    values.put("LEO_PERIGEE_ALT", ORBIT_ALTITUDE_M / 1000.0);
    values.put("LEO_APOGEE_ALT", ORBIT_ALTITUDE_M / 1000.0);
    return values;
  }

  private static Map<String, Object> geoValues(LauncherModel launcher) {
    Map<String, Object> values = commonValues("FX-4 probe GEO", launcher, Payloads.GEO_SAT);
    values.put("GTO_PARKING_ALT", ORBIT_ALTITUDE_M / 1000.0);
    return values;
  }
}
