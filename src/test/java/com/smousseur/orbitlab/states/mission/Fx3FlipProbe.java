package com.smousseur.orbitlab.states.mission;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.ReentryFxConfig;
import com.smousseur.orbitlab.engine.RetrogradeFlipConfig;
import com.smousseur.orbitlab.engine.scene.body.BodyView;
import com.smousseur.orbitlab.engine.scene.spacecraft.SpacecraftPresenter;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
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
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * The retrograde flips of production flights, against the rules fixed before this ran. NOT a gate:
 * it asserts nothing. Run with {@code -Dorbitlab.probe=true --tests '*Fx3FlipProbe*'}; the report
 * is also written to {@code build/fx3-flip.txt}.
 *
 * <p>Four missions computed by {@code MissionPlanOptimizer.compute()} in FAST: a Falcon Heavy to
 * GEO and one to a 100 km lunar orbit, whose upper stage brakes on its second Parking burn and
 * whose lunar orbiter brakes into orbit; a Falcon Heavy LEO whose payload deorbits; and an Ariane
 * 64 LEO on a 97.5° plane whose payload deorbits, the polar case the turn axis is furthest from
 * celestial north on. The schedule is the renderer's own ({@link MissionRenderer#flipsOf}), built
 * from the burn timeline of the raw ephemeris, the silhouette the debris give and the primary's
 * heat profile; the turned attitude is the presenter's own, settled on its target.
 *
 * <p>Rules: <b>V1</b> every burn whose cosine is below the threshold has its turn, and no other
 * (100 %); <b>V2</b> sampled finely inside every burn, the turn is π in each retrograde burn and 0
 * in every other, and it is 0 or π at every silhouette change; <b>V3</b> during each retrograde
 * burn the drawn nose points against the flight direction, {@code nose · v̂ <= }{@value
 * #V3_MAX_DOT}; <b>V4</b> the turn is 0 at every heated sample and after the touchdown, and the
 * margin between the end of the last turn and the first heated sample is reported; <b>V5</b> the
 * schedule and the turned attitude cost at most {@value #V5_MAX_MS} ms a frame at the median.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class Fx3FlipProbe {

  private static final double V3_MAX_DOT = -0.999;
  private static final double V5_MAX_MS = 0.05;

  private static final double ORBIT_ALTITUDE_KM = 400.0;
  private static final String LUNAR_FLOOR = "2026-10-06T00:00:00Z";
  private static final double LUNAR_PAYLOAD_MASS_KG = 2_000.0;
  private static final int SAMPLES_PER_BURN = 400;
  private static final int BATCHES = 21;
  private static final int FRAMES_PER_BATCH = 2_000;
  private static final float SETTLED_SECONDS = 10f;

  private static final RetrogradeFlipConfig CONFIG = RetrogradeFlipConfig.defaults();
  private static final RenderContext CTX = RenderContext.planet(SolarSystemBody.EARTH);

  private static AbsoluteDate epoch;

  private final List<String> report = new ArrayList<>();
  private long retrogrades;
  private long misclassified;
  private long burnInstants;
  private long wrongAngleInBurn;
  private long silhouetteChanges;
  private long turningAtSilhouette;
  private long retrogradeInstants;
  private long notReversed;
  private double worstDot = -1.0;
  private long heatedSamples;
  private long turnedWhileHeated;
  private long turnedAfterTouchdown;
  private final List<Double> frameMedians = new ArrayList<>();

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    OrekitService.get().warmUpFrames();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  void flipsOfProductionFlights() throws IOException {
    flyEarth("FH GEO", MissionType.GEO, geoValues(Launchers.FALCON_HEAVY));
    flyLunarOrbit("FH LUNAR ORBIT 100");
    Map<String, Object> deorbit = leoValues(Launchers.FALCON_HEAVY);
    deorbit.put("DEORBIT", Boolean.TRUE);
    flyEarth("FH LEO 400 DEORBIT", MissionType.LEO, deorbit);
    Map<String, Object> polar = leoValues(Launchers.ARIANE_64);
    polar.put("TARGET_INCLINATION", 97.5);
    polar.put("DEORBIT", Boolean.TRUE);
    flyEarth("A64 LEO 400 i=97.5 DEORBIT", MissionType.LEO, polar);

    double worstFrame = frameMedians.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    line("%n=== VERDICTS (rules fixed before the measurement)");
    line(
        "  V1 classification: %d of %d retrograde burns without their turn, or another burn with"
            + " one (rule: 0) -> %s",
        misclassified, retrogrades, misclassified == 0 ? "PASS" : "FAIL");
    line(
        "  V2 no turning during a burn or a jettison: %d of %d burn instants off 0/pi as their"
            + " class requires, %d of %d silhouette changes mid-turn (rule: 0 and 0) -> %s",
        wrongAngleInBurn,
        burnInstants,
        turningAtSilhouette,
        silhouetteChanges,
        wrongAngleInBurn == 0 && turningAtSilhouette == 0 ? "PASS" : "FAIL");
    line(
        "  V3 tail first: %d of %d retrograde instants with nose.v > %.3f, worst %.6f -> %s",
        notReversed, retrogradeInstants, V3_MAX_DOT, worstDot, notReversed == 0 ? "PASS" : "FAIL");
    line(
        "  V4 upright for re-entry: %d of %d heated samples turned, %d instants after touchdown"
            + " turned (rule: 0 and 0) -> %s",
        turnedWhileHeated,
        heatedSamples,
        turnedAfterTouchdown,
        turnedWhileHeated == 0 && turnedAfterTouchdown == 0 ? "PASS" : "FAIL");
    line(
        "  V5 frame cost: worst median %.4f ms (rule <= %.2f) -> %s",
        worstFrame, V5_MAX_MS, worstFrame <= V5_MAX_MS ? "PASS" : "FAIL");

    Path out = Path.of("build/fx3-flip.txt");
    Files.createDirectories(out.getParent());
    Files.write(out, report, StandardCharsets.UTF_8);
  }

  private void flyEarth(String label, MissionType type, Map<String, Object> values) {
    MissionEntry entry = new MissionEntry(MissionFactory.specFromWizardValues(values, type));
    requireFast(entry);
    long started = System.nanoTime();
    MissionComputeResult result = new MissionPlanOptimizer(entry, epoch).compute().computation();
    measure(label, result, (System.nanoTime() - started) / 1e9);
  }

  private void flyLunarOrbit(String label) {
    Map<String, Object> values =
        commonValues(Launchers.FALCON_HEAVY, Payloads.LUNAR_ORBITER, LUNAR_PAYLOAD_MASS_KG);
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
    measure(label, result, (System.nanoTime() - started) / 1e9);
  }

  private void measure(String label, MissionComputeResult result, double computeSeconds) {
    MissionEphemeris ephemeris = result.ephemeris();
    BurnTimeline timeline = BurnTimeline.of(ephemeris, result.mission().getVehicle());
    PrimarySilhouette silhouette = PrimarySilhouette.from(result.debris());
    ReentryFxConfig reentry = ReentryFxConfig.defaults();
    ReentryHeatProfile heat =
        MissionRenderer.endsOnTheGround(ephemeris)
            ? ReentryHeatProfile.forMission(
                ephemeris,
                result.mission().getAtmosphere(),
                reentry.thresholdWm2(),
                reentry.ceilingWm2())
            : new ReentryHeatProfile(List.of(), null, 1.0, 2.0);
    RetrogradeFlips flips = MissionRenderer.flipsOf(timeline, silhouette, heat, ephemeris, CONFIG);
    line(
        "%n=== %s: compute %.1f s, %d burns, %d turns, %s",
        label,
        computeSeconds,
        timeline.burns().size(),
        flips.windows().size(),
        heat.firstHeatDate()
            .map(
                d ->
                    String.format(
                        Locale.ROOT, "heats from T+%.1f", d.durationFrom(epochOf(ephemeris))))
            .orElse("never heats"));
    for (RetrogradeFlips.Window window : flips.windows()) {
      line(
          "  turn T+%.2f over %.2f s, turned T+%.2f to T+%.2f, back over %.2f s",
          window.turnStart(),
          window.turnedFrom() - window.turnStart(),
          window.turnedFrom(),
          window.turnedUntil(),
          window.turnEnd() - window.turnedUntil());
    }
    for (BurnTimeline.Burn burn : timeline.burns()) {
      checkBurn(burn, flips, ephemeris);
    }
    for (AbsoluteDate change : silhouette.transitionDates()) {
      silhouetteChanges++;
      double angle = flips.angleAt(change);
      if (angle != 0.0 && angle != Math.PI) {
        turningAtSilhouette++;
      }
    }
    checkReentry(flips, heat, ephemeris);
    timeFrames(label, flips, ephemeris);
  }

  private void checkBurn(
      BurnTimeline.Burn burn, RetrogradeFlips flips, MissionEphemeris ephemeris) {
    boolean retrograde = burn.cosTheta() < CONFIG.retrogradeCosine();
    double duration = burn.end().durationFrom(burn.start());
    double expected = retrograde ? Math.PI : 0.0;
    AbsoluteDate middle = burn.start().shiftedBy(duration / 2.0);
    if (retrograde) {
      retrogrades++;
    }
    if ((flips.angleAt(middle) == Math.PI) != retrograde) {
      misclassified++;
    }
    long wrong = 0;
    double worst = -1.0;
    for (int k = 0; k < SAMPLES_PER_BURN; k++) {
      AbsoluteDate t = burn.start().shiftedBy(duration * k / SAMPLES_PER_BURN);
      burnInstants++;
      double angle = flips.angleAt(t);
      if (angle != expected) {
        wrong++;
      }
      if (retrograde) {
        MissionEphemerisPoint point = ephemeris.displayPointAt(t);
        Vector3D nose = DrawnFlightAxis.of(point);
        Vector3f drawn = turnedNose(point, nose, angle);
        double dot = drawn.dot(jme(nose));
        retrogradeInstants++;
        worst = Math.max(worst, dot);
        if (dot > V3_MAX_DOT) {
          notReversed++;
        }
      }
    }
    wrongAngleInBurn += wrong;
    if (retrograde) {
      worstDot = Math.max(worstDot, worst);
    }
    line(
        "  burn %-7s T+%11.2f %9.2f s cos %8.4f %-10s %d off its angle%s",
        burn.role(),
        burn.start().durationFrom(epochOf(ephemeris)),
        duration,
        burn.cosTheta(),
        retrograde ? "retrograde" : "prograde",
        wrong,
        retrograde ? String.format(Locale.ROOT, ", worst nose.v %.6f", worst) : "");
  }

  private void checkReentry(
      RetrogradeFlips flips, ReentryHeatProfile heat, MissionEphemeris ephemeris) {
    if (heat.firstHeatDate().isEmpty()) {
      return;
    }
    for (MissionEphemerisPoint point : ephemeris.allPoints()) {
      if (heat.intensityAt(point.time()) > 0.0) {
        heatedSamples++;
        if (flips.angleAt(point.time()) != 0.0) {
          turnedWhileHeated++;
        }
      }
    }
    AbsoluteDate end = ephemeris.endDate();
    for (double after : new double[] {0.5, 1.0, 60.0, 3_600.0}) {
      if (flips.angleAt(end.shiftedBy(after)) != 0.0) {
        turnedAfterTouchdown++;
      }
    }
    double firstHeat = heat.firstHeatDate().get().durationFrom(epochOf(ephemeris));
    double lastTurnEnd =
        flips.windows().isEmpty() ? Double.NaN : flips.windows().getLast().turnEnd();
    line(
        "  re-entry: first heated sample T+%.1f, last turn back done T+%.1f, margin %.1f s",
        firstHeat, lastTurnEnd, firstHeat - lastTurnEnd);
  }

  /** V5: the schedule look-up and the turned attitude, as the renderer runs them each frame. */
  private void timeFrames(String label, RetrogradeFlips flips, MissionEphemeris ephemeris) {
    if (flips.windows().isEmpty()) {
      return;
    }
    RetrogradeFlips.Window window = flips.windows().get(0);
    AbsoluteDate from = epochOf(ephemeris).shiftedBy(window.turnStart());
    double span = window.turnEnd() - window.turnStart();
    List<MissionEphemerisPoint> points = new ArrayList<>(FRAMES_PER_BATCH);
    for (int f = 0; f < FRAMES_PER_BATCH; f++) {
      points.add(ephemeris.displayPointAt(from.shiftedBy(span * (f + 0.5) / FRAMES_PER_BATCH)));
    }
    SpacecraftPresenter presenter = new SpacecraftPresenter("timed", new RecordingBodyView());
    Vector3f pivot = new Vector3f(0f, 0.19f, 0f);
    double[] perFrame = new double[BATCHES];
    for (int b = 0; b < BATCHES; b++) {
      long t0 = System.nanoTime();
      for (MissionEphemerisPoint point : points) {
        double angle = flips.angleAt(point.time());
        presenter.updatePose(
            point.position(),
            DrawnFlightAxis.of(point),
            0.016f,
            CTX,
            Vector3f.UNIT_Y,
            angle,
            point.position());
        presenter.flipCompensation(pivot);
      }
      perFrame[b] = (System.nanoTime() - t0) / 1e6 / FRAMES_PER_BATCH;
    }
    Arrays.sort(perFrame);
    double median = perFrame[BATCHES / 2];
    frameMedians.add(median);
    line("  frame cost on %s: median %.4f ms", label, median);
  }

  private static Vector3f turnedNose(MissionEphemerisPoint point, Vector3D nose, double angle) {
    RecordingBodyView view = new RecordingBodyView();
    SpacecraftPresenter settled = new SpacecraftPresenter("settled", view);
    settled.updatePose(
        point.position(), nose, SETTLED_SECONDS, CTX, Vector3f.UNIT_Y, angle, point.position());
    return view.rotation.mult(Vector3f.UNIT_Y);
  }

  private static Vector3f jme(Vector3D icrf) {
    return JmeVectorAdapter.toVector3f(CTX.axisConvention().icrfToJme(icrf.normalize()));
  }

  private static AbsoluteDate epochOf(MissionEphemeris ephemeris) {
    return ephemeris.startDate();
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

  private static Map<String, Object> commonValues(
      LauncherModel launcher, PayloadModel payload, double payloadMass) {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "FX-3 flip probe");
    values.put("LAUNCH_SITE_NAME", "Kourou - French Guiana");
    values.put("LAUNCH_SITE_LAT", 5.23);
    values.put("LAUNCH_SITE_LONG", -52.77);
    values.put("LAUNCH_SITE_ALT", 0.0);
    values.put("LAUNCHER_TYPE", launcher.id());
    values.put("PAYLOAD_TYPE", payload.id());
    values.put("PAYLOAD_MASS", payloadMass);
    return values;
  }

  private static Map<String, Object> leoValues(LauncherModel launcher) {
    Map<String, Object> values =
        commonValues(
            launcher,
            Payloads.EARTH_OBSERVATION_SAT,
            Payloads.EARTH_OBSERVATION_SAT.defaultDryMass());
    values.put("LEO_PERIGEE_ALT", ORBIT_ALTITUDE_KM);
    values.put("LEO_APOGEE_ALT", ORBIT_ALTITUDE_KM);
    return values;
  }

  private static Map<String, Object> geoValues(LauncherModel launcher) {
    Map<String, Object> values =
        commonValues(launcher, Payloads.GEO_SAT, Payloads.GEO_SAT.defaultDryMass());
    values.put("GTO_PARKING_ALT", ORBIT_ALTITUDE_KM);
    return values;
  }

  /** A {@link BodyView} that only keeps the last rotation it is given. */
  private static final class RecordingBodyView implements BodyView {
    private Quaternion rotation = new Quaternion();

    @Override
    public Spatial spatial() {
      return null;
    }

    @Override
    public Spatial nearSpatial() {
      return null;
    }

    @Override
    public void setPositionWorld(Vector3f position) {}

    @Override
    public void setRotationWorld(Quaternion rotation) {
      this.rotation = new Quaternion(rotation);
    }

    @Override
    public void updateScreen(Camera cam, boolean allow3d) {}

    @Override
    public void setVisible(boolean visible) {}

    @Override
    public void detach() {}
  }
}
