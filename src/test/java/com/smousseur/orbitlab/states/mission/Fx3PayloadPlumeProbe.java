package com.smousseur.orbitlab.states.mission;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.NozzlePlumeConfig;
import com.smousseur.orbitlab.engine.scene.spacecraft.LauncherAssets;
import com.smousseur.orbitlab.engine.scene.spacecraft.LauncherNozzles;
import com.smousseur.orbitlab.engine.scene.spacecraft.NozzlePlumeView;
import com.smousseur.orbitlab.engine.scene.spacecraft.PayloadAssets;
import com.smousseur.orbitlab.engine.scene.spacecraft.PlumeDrive;
import com.smousseur.orbitlab.engine.scene.spacecraft.PlumeLighting;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import com.smousseur.orbitlab.simulation.mission.window.problem.MissionScheduler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * The payload plumes of production flights, against the rules fixed before this ran. NOT a gate: it
 * asserts nothing. Run with {@code -Dorbitlab.probe=true --tests '*Fx3PayloadPlumeProbe*'}; the
 * report is also written to {@code build/fx3-payload-plume.txt}.
 *
 * <p>Three Falcon Heavy missions computed by {@code MissionPlanOptimizer.compute()} in FAST, one
 * per payload mesh that fires: GEO (goes, circularization, trim and plane correction), a 400 km LEO
 * whose payload deorbits (landsat8, trims and deorbit burns) and a 100 km lunar orbit planned by
 * {@code MissionScheduler} (lro, lunar orbit insertion). The plume is decided the way the renderer
 * decides it, from the burn timeline of the raw ephemeris and the silhouette the debris give, the
 * payload silhouette resolving to the payload's mesh as {@code MissionRenderer} resolves it.
 *
 * <p>Rules: <b>V1</b> inside each payload burn, past its ignition ramp and before its cut-off ramp,
 * the payload's cluster is lit at 100 % of the instants, and between burns no cluster is; <b>V2</b>
 * at those instants the silhouette drawn is the payload's, at 100 %; <b>V4</b> the decision and the
 * plume's uniforms cost at most {@value #V4_MAX_MS} ms a frame at the median, on the payload's
 * mesh. V3, the GPU cost, is {@code Fx3PlumeGpuProbe}'s, taken offscreen with the application
 * closed.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class Fx3PayloadPlumeProbe {

  private static final double V4_MAX_MS = 0.05;

  private static final double ORBIT_ALTITUDE_KM = 400.0;
  private static final String LUNAR_FLOOR = "2026-10-06T00:00:00Z";
  private static final double LUNAR_PAYLOAD_MASS_KG = 2_000.0;
  private static final int SAMPLES_PER_BURN = 400;
  private static final int SAMPLES_PER_GAP = 200;
  private static final int BATCHES = 21;
  private static final int FRAMES_PER_BATCH = 2_000;

  private static AbsoluteDate epoch;
  private static final NozzlePlumeConfig CONFIG = NozzlePlumeConfig.defaults();

  private final List<String> report = new ArrayList<>();
  private long kickBurns;
  private long burnInstants;
  private long unlitInBurn;
  private long wrongSilhouette;
  private long gapInstants;
  private long litInGap;
  private final List<Double> frameMedians = new ArrayList<>();

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    OrekitService.get().warmUpFrames();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    try {
      AssetFactory.init(new DesktopAssetManager(true));
    } catch (OrbitlabException alreadyInitialized) {
      // Another test class got there first: its asset manager serves just as well.
    }
  }

  @Test
  void payloadPlumesOfProductionFlights() throws IOException {
    flyEarth("FH GEO", MissionType.GEO, geoValues(), Payloads.GEO_SAT);
    Map<String, Object> deorbit = leoValues();
    deorbit.put("DEORBIT", Boolean.TRUE);
    flyEarth("FH LEO 400 DEORBIT", MissionType.LEO, deorbit, Payloads.EARTH_OBSERVATION_SAT);
    flyLunarOrbit("FH LUNAR ORBIT 100");

    double worstFrame = frameMedians.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    line("%n=== VERDICTS (rules fixed before the measurement)");
    line(
        "  V1 lighting: %d of %d payload-burn instants unlit over %d payload burns, %d of %d coast"
            + " instants with a cluster lit (rule: 0 and 0) -> %s",
        unlitInBurn,
        burnInstants,
        kickBurns,
        litInGap,
        gapInstants,
        unlitInBurn == 0 && litInGap == 0 && kickBurns > 0 ? "PASS" : "FAIL");
    line(
        "  V2 silhouette: %d of %d payload-burn instants on another silhouette than the payload's"
            + " (rule: 0) -> %s",
        wrongSilhouette, burnInstants, wrongSilhouette == 0 ? "PASS" : "FAIL");
    line(
        "  V4 frame cost: worst median %.4f ms (rule <= %.2f) -> %s",
        worstFrame, V4_MAX_MS, worstFrame <= V4_MAX_MS ? "PASS" : "FAIL");

    Path out = Path.of("build/fx3-payload-plume.txt");
    Files.createDirectories(out.getParent());
    Files.write(out, report, StandardCharsets.UTF_8);
  }

  private void flyEarth(
      String label, MissionType type, Map<String, Object> values, PayloadModel payload) {
    MissionEntry entry = new MissionEntry(MissionFactory.specFromWizardValues(values, type));
    requireFast(entry);
    long started = System.nanoTime();
    MissionComputeResult result = new MissionPlanOptimizer(entry, epoch).compute().computation();
    measure(label, result, payload, (System.nanoTime() - started) / 1e9);
  }

  private void flyLunarOrbit(String label) {
    Map<String, Object> values = commonValues(Payloads.LUNAR_ORBITER, LUNAR_PAYLOAD_MASS_KG);
    values.put("LUNAR_ORBIT_ALT", 100.0);
    long started = System.nanoTime();
    MissionEntry entry =
        new MissionEntry(MissionScheduler.unplannedSpec(values, MissionType.LUNAR_ORBIT));
    AbsoluteDate floor = new AbsoluteDate(LUNAR_FLOOR, TimeScalesFactory.getUTC());
    MissionScheduler.Schedule schedule = MissionScheduler.schedule(entry, values, floor);
    if (schedule.refusal() != null) {
      line("%n=== %s: no launch window, %s", label, schedule.refusal());
      return;
    }
    requireFast(entry);
    MissionComputeResult result =
        new MissionPlanOptimizer(entry, schedule.date()).compute().computation();
    measure(label, result, Payloads.LUNAR_ORBITER, (System.nanoTime() - started) / 1e9);
  }

  private void measure(
      String label, MissionComputeResult result, PayloadModel payload, double computeSeconds) {
    MissionEphemeris ephemeris = result.ephemeris();
    BurnTimeline timeline = BurnTimeline.of(ephemeris, result.mission().getVehicle());
    PrimarySilhouette silhouette = PrimarySilhouette.from(result.debris());
    LauncherModel launcher = Launchers.FALCON_HEAVY;
    String base = LauncherAssets.modelPath(launcher.id());
    float throttle = (float) launcher.ascentProfile().coreThrottle();
    PayloadAssets.PayloadAsset asset = PayloadAssets.forPayload(payload.id()).orElseThrow();
    LauncherNozzles.Cluster engine = LauncherNozzles.clustersOf(asset.meshPath()).get(0);
    float metresPerNative =
        AssetFactory.get().loadModelNormalized(asset.meshPath(), 1f).getLocalScale().y
            * (float) asset.drawnSizeMeters();
    float lengthRadii = CONFIG.look(engine.look()).shapeAt(1f).lengthRadii();
    line(
        "%n=== %s: compute %.1f s, %d burns; %s drawn %.2f m, %.2f m above its engine; exit radius"
            + " %.3f m, plume %.2f m",
        label,
        computeSeconds,
        timeline.burns().size(),
        asset.meshPath(),
        asset.drawnSizeMeters(),
        asset.heightAboveExitMeters(),
        engine.radius() * metresPerNative,
        lengthRadii * engine.radius() * metresPerNative);

    List<BurnTimeline.Burn> burns = timeline.burns();
    for (BurnTimeline.Burn burn : burns) {
      checkBurn(burn, timeline, silhouette, base, asset, throttle, ephemeris);
    }
    for (int i = 0; i + 1 < burns.size(); i++) {
      checkGap(
          burns.get(i).end(),
          burns.get(i + 1).start(),
          timeline,
          silhouette,
          base,
          asset,
          throttle,
          ephemeris);
    }
    if (!burns.isEmpty()) {
      checkGap(
          burns.get(burns.size() - 1).end(),
          ephemeris.endDate(),
          timeline,
          silhouette,
          base,
          asset,
          throttle,
          ephemeris);
    }
    timeFrames(label, timeline, asset);
  }

  private void checkBurn(
      BurnTimeline.Burn burn,
      BurnTimeline timeline,
      PrimarySilhouette silhouette,
      String base,
      PayloadAssets.PayloadAsset asset,
      float throttle,
      MissionEphemeris ephemeris) {
    double duration = burn.end().durationFrom(burn.start());
    if (burn.role() != StageRole.KICK) {
      line(
          "  burn %-7s T+%9.2f %8.2f s: launcher, L1's",
          burn.role(), burn.start().durationFrom(ephemeris.startDate()), duration);
      return;
    }
    kickBurns++;
    double ramp = CONFIG.rampSeconds();
    double from = duration > 2 * ramp ? ramp : duration / 2;
    double to = duration > 2 * ramp ? duration - ramp : duration / 2;
    long unlit = 0;
    long wrong = 0;
    for (int k = 0; k < SAMPLES_PER_BURN; k++) {
      AbsoluteDate t = burn.start().shiftedBy(from + (to - from) * k / (SAMPLES_PER_BURN - 1.0));
      PrimarySilhouette.SilhouettePhase phase = silhouette.phaseAt(t);
      String mesh = meshOf(base, asset, phase);
      float[] lit = intensitiesAt(t, timeline, mesh, throttle, ephemeris);
      burnInstants++;
      if (phase != PrimarySilhouette.SilhouettePhase.PAYLOAD) {
        wrong++;
      }
      List<LauncherNozzles.Cluster> clusters = LauncherNozzles.clustersOf(mesh);
      boolean lighted = false;
      for (int i = 0; i < clusters.size(); i++) {
        if (clusters.get(i).block() == StageRole.KICK && lit[i] > 0f) {
          lighted = true;
        }
      }
      if (!lighted) {
        unlit++;
      }
    }
    unlitInBurn += unlit;
    wrongSilhouette += wrong;
    line(
        "  burn %-7s T+%9.2f %8.2f s: %d unlit, %d on another silhouette",
        burn.role(), burn.start().durationFrom(ephemeris.startDate()), duration, unlit, wrong);
  }

  private void checkGap(
      AbsoluteDate from,
      AbsoluteDate to,
      BurnTimeline timeline,
      PrimarySilhouette silhouette,
      String base,
      PayloadAssets.PayloadAsset asset,
      float throttle,
      MissionEphemeris ephemeris) {
    double span = to.durationFrom(from);
    if (span <= 0.0) {
      return;
    }
    for (int k = 1; k < SAMPLES_PER_GAP; k++) {
      AbsoluteDate t = from.shiftedBy(span * k / SAMPLES_PER_GAP);
      if (timeline.burnAt(t).isPresent()) {
        continue;
      }
      gapInstants++;
      float[] lit =
          intensitiesAt(
              t, timeline, meshOf(base, asset, silhouette.phaseAt(t)), throttle, ephemeris);
      for (float intensity : lit) {
        if (intensity > 0f) {
          litInGap++;
          break;
        }
      }
    }
  }

  /** V4: the decision the renderer makes each frame, and the plume's update, on the payload. */
  private void timeFrames(String label, BurnTimeline timeline, PayloadAssets.PayloadAsset asset) {
    BurnTimeline.Burn first =
        timeline.burns().stream().filter(b -> b.role() == StageRole.KICK).findFirst().orElse(null);
    if (first == null) {
      line("  frame cost on %s: no payload burn to time", label);
      return;
    }
    Node anchor = new Node("anchor3d");
    Node body = new Node("bucket");
    body.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.QUARTER_PI, Vector3f.UNIT_Z));
    anchor.attachChild(body);
    NozzlePlumeView view = new NozzlePlumeView(anchor, CONFIG);
    view.setMesh(asset.meshPath(), 0.005f);
    Vector3f camera = new Vector3f(1f, 0f, 0f);
    double[] perFrame = new double[BATCHES];
    double span = first.end().durationFrom(first.start());
    for (int b = 0; b < BATCHES; b++) {
      long t0 = System.nanoTime();
      for (int f = 0; f < FRAMES_PER_BATCH; f++) {
        AbsoluteDate now = first.start().shiftedBy(span * (f + 0.5) / FRAMES_PER_BATCH);
        PlumeDrive drive =
            NozzlePlumeSchedule.driveAt(
                timeline.burnAt(now).orElse(null), now, 400_000.0, 1.0, 1f, CONFIG);
        view.update(drive, body, true, camera);
      }
      perFrame[b] = (System.nanoTime() - t0) / 1e6 / FRAMES_PER_BATCH;
    }
    Arrays.sort(perFrame);
    double median = perFrame[BATCHES / 2];
    frameMedians.add(median);
    line("  frame cost on %s's payload: median %.4f ms", label, median);
  }

  private static float[] intensitiesAt(
      AbsoluteDate t,
      BurnTimeline timeline,
      String mesh,
      float throttle,
      MissionEphemeris ephemeris) {
    PlumeDrive drive =
        NozzlePlumeSchedule.driveAt(
            timeline.burnAt(t).orElse(null),
            t,
            ephemeris.displayPointAt(t).altitudeMeters(),
            1.0,
            throttle,
            CONFIG);
    return PlumeLighting.intensities(
        LauncherNozzles.clustersOf(mesh), drive.role(), drive.coreThrottle(), drive.ramp());
  }

  private static String meshOf(
      String base, PayloadAssets.PayloadAsset asset, PrimarySilhouette.SilhouettePhase phase) {
    return switch (phase) {
      case FULL -> base;
      case AFTER_BOOSTERS -> base.replace(".gltf", "-after_boosters.gltf");
      case AFTER_S1 -> base.replace(".gltf", "-after_s1.gltf");
      case PAYLOAD -> asset.meshPath();
    };
  }

  private static void requireFast(MissionEntry entry) {
    if (entry.getOptimizationType() != OptimizationType.FAST) {
      throw new IllegalStateException("the entry composed " + entry.getOptimizationType());
    }
  }

  private void line(String format, Object... args) {
    String text = String.format(Locale.ROOT, format, args);
    System.out.println(text);
    report.add(text);
  }

  private static Map<String, Object> commonValues(PayloadModel payload, double payloadMass) {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "FX-3 payload plume probe");
    values.put("LAUNCH_SITE_NAME", "Kourou - French Guiana");
    values.put("LAUNCH_SITE_LAT", 5.23);
    values.put("LAUNCH_SITE_LONG", -52.77);
    values.put("LAUNCH_SITE_ALT", 0.0);
    values.put("LAUNCHER_TYPE", Launchers.FALCON_HEAVY.id());
    values.put("PAYLOAD_TYPE", payload.id());
    values.put("PAYLOAD_MASS", payloadMass);
    return values;
  }

  private static Map<String, Object> leoValues() {
    Map<String, Object> values =
        commonValues(
            Payloads.EARTH_OBSERVATION_SAT, Payloads.EARTH_OBSERVATION_SAT.defaultDryMass());
    values.put("LEO_PERIGEE_ALT", ORBIT_ALTITUDE_KM);
    values.put("LEO_APOGEE_ALT", ORBIT_ALTITUDE_KM);
    return values;
  }

  private static Map<String, Object> geoValues() {
    Map<String, Object> values = commonValues(Payloads.GEO_SAT, Payloads.GEO_SAT.defaultDryMass());
    values.put("GTO_PARKING_ALT", ORBIT_ALTITUDE_KM);
    return values;
  }
}
