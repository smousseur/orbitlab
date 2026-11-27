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
 * The launcher plumes of production flights, against the rules fixed before this ran. NOT a gate:
 * it asserts nothing. Run with {@code -Dorbitlab.probe=true --tests '*Fx3PlumeProbe*'}; the report
 * is also written to {@code build/fx3-plume.txt}.
 *
 * <p>Four Earth missions from Kourou, built from wizard values and computed by {@code
 * MissionPlanOptimizer.compute()} in FAST — every launcher role, and the Falcon Heavy's one
 * launcher retrograde, the second Parking burn of its GEO flight. The plume is decided the way the
 * renderer decides it, from the burn timeline of the raw ephemeris and the silhouette the debris
 * give, at every instant sampled along each burn and across the coasts between them.
 *
 * <p>Rules: <b>V1</b> inside each launcher burn, past its ignition ramp and before its cut-off
 * ramp, every cluster the burn fires is lit at 100 % of the instants, and between burns none is;
 * <b>V2</b> at those instants the silhouette drawn is the one the burn fires from, at 100 %;
 * <b>V4</b> the decision and the plume's uniforms cost at most {@value #V4_MAX_MS} ms a frame at
 * the median, timed on the full stacks, which fire the most clusters. V3, the GPU cost, is the
 * closure's measure, taken offscreen with the application closed.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class Fx3PlumeProbe {

  private static final double V4_MAX_MS = 0.05;

  private static final double ORBIT_ALTITUDE_KM = 400.0;
  private static final int SAMPLES_PER_BURN = 400;
  private static final int SAMPLES_PER_GAP = 200;
  private static final int BATCHES = 21;
  private static final int FRAMES_PER_BATCH = 2_000;

  private static AbsoluteDate epoch;
  private static final NozzlePlumeConfig CONFIG = NozzlePlumeConfig.defaults();

  private final List<String> report = new ArrayList<>();
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
  void plumesOfProductionFlights() throws IOException {
    fly("FH LEO 400", MissionType.LEO, leoValues(Launchers.FALCON_HEAVY), Launchers.FALCON_HEAVY);
    fly("A64 LEO 400", MissionType.LEO, leoValues(Launchers.ARIANE_64), Launchers.ARIANE_64);
    Map<String, Object> polar = leoValues(Launchers.ARIANE_64);
    polar.put("TARGET_INCLINATION", 97.5);
    fly("A64 LEO 400 i=97.5", MissionType.LEO, polar, Launchers.ARIANE_64);
    fly("FH GEO", MissionType.GEO, geoValues(Launchers.FALCON_HEAVY), Launchers.FALCON_HEAVY);

    double worstFrame = frameMedians.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    line("%n=== VERDICTS (rules fixed before the measurement)");
    line(
        "  V1 lighting: %d of %d burn instants with a fired cluster unlit, %d of %d coast instants"
            + " with a cluster lit (rule: 0 and 0) -> %s",
        unlitInBurn,
        burnInstants,
        litInGap,
        gapInstants,
        unlitInBurn == 0 && litInGap == 0 ? "PASS" : "FAIL");
    line(
        "  V2 silhouette: %d of %d burn instants on another silhouette than the burn's (rule: 0)"
            + " -> %s",
        wrongSilhouette, burnInstants, wrongSilhouette == 0 ? "PASS" : "FAIL");
    line(
        "  V4 frame cost: worst median %.4f ms (rule <= %.2f) -> %s",
        worstFrame, V4_MAX_MS, worstFrame <= V4_MAX_MS ? "PASS" : "FAIL");

    Path out = Path.of("build/fx3-plume.txt");
    Files.createDirectories(out.getParent());
    Files.write(out, report, StandardCharsets.UTF_8);
  }

  private void fly(
      String label, MissionType type, Map<String, Object> values, LauncherModel launcher) {
    MissionEntry entry = new MissionEntry(MissionFactory.specFromWizardValues(values, type));
    if (entry.getOptimizationType() != OptimizationType.FAST) {
      throw new IllegalStateException("the entry composed " + entry.getOptimizationType());
    }
    long started = System.nanoTime();
    MissionComputeResult result = new MissionPlanOptimizer(entry, epoch).compute().computation();
    MissionEphemeris ephemeris = result.ephemeris();
    BurnTimeline timeline = BurnTimeline.of(ephemeris, result.mission().getVehicle());
    PrimarySilhouette silhouette = PrimarySilhouette.from(result.debris());
    String base = LauncherAssets.modelPath(launcher.id());
    float throttle = (float) launcher.ascentProfile().coreThrottle();
    line(
        "%n=== %s: compute %.1f s, %d burns, core throttle %.2f",
        label, (System.nanoTime() - started) / 1e9, timeline.burns().size(), throttle);

    List<BurnTimeline.Burn> burns = timeline.burns();
    for (BurnTimeline.Burn burn : burns) {
      checkBurn(burn, timeline, silhouette, base, throttle, ephemeris);
    }
    for (int i = 0; i + 1 < burns.size(); i++) {
      checkGap(
          burns.get(i).end(),
          burns.get(i + 1).start(),
          timeline,
          silhouette,
          base,
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
          throttle,
          ephemeris);
    }
    timeFrames(label, timeline, base, throttle);
  }

  private void checkBurn(
      BurnTimeline.Burn burn,
      BurnTimeline timeline,
      PrimarySilhouette silhouette,
      String base,
      float throttle,
      MissionEphemeris ephemeris) {
    double duration = burn.end().durationFrom(burn.start());
    double ramp = CONFIG.rampSeconds();
    double from = duration > 2 * ramp ? ramp : duration / 2;
    double to = duration > 2 * ramp ? duration - ramp : duration / 2;
    long unlit = 0;
    long wrong = 0;
    for (int k = 0; k < SAMPLES_PER_BURN; k++) {
      AbsoluteDate t = burn.start().shiftedBy(from + (to - from) * k / (SAMPLES_PER_BURN - 1.0));
      String mesh = meshOf(base, silhouette.phaseAt(t));
      float[] lit = intensitiesAt(t, timeline, mesh, throttle, ephemeris);
      List<LauncherNozzles.Cluster> clusters = LauncherNozzles.clustersOf(mesh);
      if (burn.role() == StageRole.KICK) {
        continue;
      }
      burnInstants++;
      if (!mesh.equals(meshOf(base, expectedPhase(burn.role())))) {
        wrong++;
      }
      for (int i = 0; i < clusters.size(); i++) {
        if (fires(burn.role(), clusters.get(i).block()) && !(lit[i] > 0f)) {
          unlit++;
          break;
        }
      }
    }
    unlitInBurn += unlit;
    wrongSilhouette += wrong;
    line(
        "  burn %-7s T+%9.2f %8.2f s: %s",
        burn.role(),
        burn.start().durationFrom(ephemeris.startDate()),
        duration,
        burn.role() == StageRole.KICK
            ? "payload motor, L4's"
            : String.format(Locale.ROOT, "%d unlit, %d on another silhouette", unlit, wrong));
  }

  private void checkGap(
      AbsoluteDate from,
      AbsoluteDate to,
      BurnTimeline timeline,
      PrimarySilhouette silhouette,
      String base,
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
          intensitiesAt(t, timeline, meshOf(base, silhouette.phaseAt(t)), throttle, ephemeris);
      for (float intensity : lit) {
        if (intensity > 0f) {
          litInGap++;
          break;
        }
      }
    }
  }

  /** V4: the decision the renderer makes each frame, and the plume's update, on the full stack. */
  private void timeFrames(String label, BurnTimeline timeline, String base, float throttle) {
    BurnTimeline.Burn first = timeline.burns().get(0);
    Node anchor = new Node("anchor3d");
    Node body = new Node("bucket");
    body.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.QUARTER_PI, Vector3f.UNIT_Z));
    anchor.attachChild(body);
    NozzlePlumeView view = new NozzlePlumeView(anchor, CONFIG);
    view.setMesh(base, 0.07f);
    Vector3f camera = new Vector3f(1f, 0f, 0f);
    double[] perFrame = new double[BATCHES];
    double span = first.end().durationFrom(first.start());
    for (int b = 0; b < BATCHES; b++) {
      long t0 = System.nanoTime();
      for (int f = 0; f < FRAMES_PER_BATCH; f++) {
        AbsoluteDate now = first.start().shiftedBy(span * (f + 0.5) / FRAMES_PER_BATCH);
        PlumeDrive drive =
            NozzlePlumeSchedule.driveAt(
                timeline.burnAt(now).orElse(null), now, 10_000.0, 1.0, throttle, CONFIG);
        view.update(drive, body, true, camera);
      }
      perFrame[b] = (System.nanoTime() - t0) / 1e6 / FRAMES_PER_BATCH;
    }
    Arrays.sort(perFrame);
    double median = perFrame[BATCHES / 2];
    frameMedians.add(median);
    line(
        "  frame cost on %s (%d clusters): median %.4f ms",
        label, LauncherNozzles.clustersOf(base).size(), median);
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

  private static boolean fires(StageRole burnRole, StageRole block) {
    return burnRole == StageRole.BOOSTER
        ? block == StageRole.BOOSTER || block == StageRole.CORE
        : block == burnRole;
  }

  private static PrimarySilhouette.SilhouettePhase expectedPhase(StageRole role) {
    return switch (role) {
      case BOOSTER -> PrimarySilhouette.SilhouettePhase.FULL;
      case CORE -> PrimarySilhouette.SilhouettePhase.AFTER_BOOSTERS;
      case UPPER -> PrimarySilhouette.SilhouettePhase.AFTER_S1;
      case KICK -> PrimarySilhouette.SilhouettePhase.PAYLOAD;
    };
  }

  private static String meshOf(String base, PrimarySilhouette.SilhouettePhase phase) {
    return switch (phase) {
      case FULL -> base;
      case AFTER_BOOSTERS -> base.replace(".gltf", "-after_boosters.gltf");
      case AFTER_S1 -> base.replace(".gltf", "-after_s1.gltf");
      case PAYLOAD -> "payload";
    };
  }

  private void line(String format, Object... args) {
    String text = String.format(Locale.ROOT, format, args);
    System.out.println(text);
    report.add(text);
  }

  private static Map<String, Object> commonValues(LauncherModel launcher, PayloadModel payload) {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "FX-3 plume probe");
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
    Map<String, Object> values = commonValues(launcher, Payloads.EARTH_OBSERVATION_SAT);
    values.put("LEO_PERIGEE_ALT", ORBIT_ALTITUDE_KM);
    values.put("LEO_APOGEE_ALT", ORBIT_ALTITUDE_KM);
    return values;
  }

  private static Map<String, Object> geoValues(LauncherModel launcher) {
    Map<String, Object> values = commonValues(launcher, Payloads.GEO_SAT);
    values.put("GTO_PARKING_ALT", ORBIT_ALTITUDE_KM);
    return values;
  }
}
