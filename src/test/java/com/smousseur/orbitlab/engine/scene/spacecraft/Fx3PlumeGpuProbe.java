package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.profile.AppProfiler;
import com.jme3.profile.AppStep;
import com.jme3.profile.SpStep;
import com.jme3.profile.VpStep;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.util.BufferUtils;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.NozzlePlumeConfig;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.function.Executable;

/**
 * Measures on the real driver the GPU time the exhaust plumes take to draw, through {@link
 * NozzlePlumeView} and the production shader: the Falcon Heavy and the Ariane 64 firing every
 * first-stage cluster, at sea level and in vacuum, and each payload mesh firing its engine in
 * vacuum, which the budget rules; and, for information, the launchers' core and upper stages firing
 * alone in vacuum.
 *
 * <p>A probe, not a test: run with {@code -Dorbitlab.probe=true --tests '*Fx3PlumeGpuProbe*'}, with
 * nothing else drawing on the GPU — OrbitLab closed. The report and one image of each view go to
 * {@code build/fx3-gpu/}.
 *
 * <p>The frame is the application's: 1280 × 720, a filter chain with 4 × MSAA and an object bloom,
 * a 45° field of view. Frame rate and v-sync are uncapped, since a capped rate lets the GPU lower
 * its clocks and a small load then reads several times its cost. The time is that of the main
 * pass's transparent bucket, between two {@code GL_TIME_ELAPSED} marks: the plumes are all it
 * holds, and, having no Glow technique, the bloom's pass does not draw them. JME's own bloom stands
 * in for the application's smoothed one, which draws the same objects. No launcher is drawn, so
 * nothing hides any part of a jet: the times are an upper bound.
 *
 * <p>The views, in stack heights, the model being drawn one unit tall — a payload its catalog size
 * across, as it is normalized: the default follow, from 350 m; side on from 1, 0.5 and 0.25 of the
 * stack, aimed at the longest jet's first half; from inside that jet, a quarter of its length down,
 * looking up at the nozzle, down the jet and across it; and from below its tail, along the stack's
 * axis and 30° off it, where a ray runs the length of a jet. Every view is timed in each of {@value
 * #PASSES} passes; each median of the first stages must stay within {@value #MAX_MEDIAN_MS} ms.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class Fx3PlumeGpuProbe {

  private static final int WIDTH = 1280;
  private static final int HEIGHT = 720;
  private static final int SAMPLES = 4;
  private static final float FOV_Y_DEG = 45f;
  private static final float FOLLOW_DISTANCE_M = 350f;
  private static final int PASSES = 3;

  /** The frames a view is drawn before it is timed, for the GPU to settle after the switch. */
  private static final int SETTLE_FRAMES = 30;

  private static final int TIMED_FRAMES = 200;

  /** The frames a timer query is left before it is read, so that reading it rarely waits. */
  private static final int QUERY_RING = 8;

  private static final float FLOW_SECONDS = 10f;
  private static final double MAX_MEDIAN_MS = 1.0;
  private static final Path OUT = Path.of("build", "fx3-gpu");

  private record View(String name, Vector3f location, Vector3f lookAt, Vector3f up) {}

  /**
   * A silhouette firing its role's clusters, drawn {@code modelScale} per mesh unit so that it is
   * one unit tall or across; {@code ruled} when the budget applies to it, the others being measured
   * for information.
   */
  private record Firing(
      String prefix,
      String meshPath,
      float modelScale,
      double sizeMeters,
      StageRole role,
      float coreThrottle,
      boolean ruled) {

    static Firing launcher(
        String prefix, LauncherModel launcher, String suffix, StageRole role, boolean ruled) {
      return new Firing(
          prefix,
          LauncherAssets.modelPath(launcher.id()).replace(".gltf", suffix + ".gltf"),
          1f,
          launcher.heightMeters(),
          role,
          (float) launcher.ascentProfile().coreThrottle(),
          ruled);
    }

    /** A payload mesh firing its engine, mounted and normalized as the renderer draws it. */
    static Firing payload(String prefix, PayloadModel payload) {
      PayloadAssets.PayloadAsset asset = PayloadAssets.forPayload(payload.id()).orElseThrow();
      Spatial model = new DesktopAssetManager(true).loadModel(asset.meshPath());
      model.updateGeometricState();
      BoundingBox box = (BoundingBox) model.getWorldBound();
      float extent = 2f * Math.max(box.getXExtent(), Math.max(box.getYExtent(), box.getZExtent()));
      return new Firing(
          prefix, asset.meshPath(), 1f / extent, asset.drawnSizeMeters(), StageRole.KICK, 1f, true);
    }

    /** The air the budget is ruled in: a payload only fires in orbit. */
    float[] pressureFactors() {
      return ruled && role != StageRole.KICK ? new float[] {0f, 1f} : new float[] {1f};
    }
  }

  private record Shot(String name, Firing firing, float pressureFactor, View view) {}

  private static final List<Firing> FIRINGS =
      List.of(
          Firing.launcher("fh-full", Launchers.FALCON_HEAVY, "", StageRole.BOOSTER, true),
          Firing.launcher("a64-full", Launchers.ARIANE_64, "", StageRole.BOOSTER, true),
          Firing.payload("goes", Payloads.GEO_SAT),
          Firing.payload("landsat8", Payloads.EARTH_OBSERVATION_SAT),
          Firing.payload("lro", Payloads.LUNAR_ORBITER),
          Firing.launcher(
              "fh-core", Launchers.FALCON_HEAVY, "-after_boosters", StageRole.CORE, false),
          Firing.launcher("fh-mvac", Launchers.FALCON_HEAVY, "-after_s1", StageRole.UPPER, false),
          Firing.launcher(
              "a64-vulcain", Launchers.ARIANE_64, "-after_boosters", StageRole.CORE, false),
          Firing.launcher("a64-vinci", Launchers.ARIANE_64, "-after_s1", StageRole.UPPER, false));

  @Test
  void thePlumesStayWithinTheirGpuBudget() throws Exception {
    NozzlePlumeConfig config = NozzlePlumeConfig.defaults();
    List<Shot> shots = new ArrayList<>();
    for (Firing firing : FIRINGS) {
      for (float pressureFactor : firing.pressureFactors()) {
        String air = pressureFactor == 0f ? "sea" : "vac";
        for (View view : views(config, firing, pressureFactor)) {
          shots.add(
              new Shot(
                  firing.prefix() + "-" + air + "-" + view.name(), firing, pressureFactor, view));
        }
      }
    }
    float[][] millis = render(config, shots);

    StringBuilder report = new StringBuilder();
    report.append(String.format(Locale.ROOT, "%-28s", "view"));
    for (int pass = 1; pass <= PASSES; pass++) {
      report.append(String.format(Locale.ROOT, "   pass %d med / p95", pass));
    }
    report.append(String.format(Locale.ROOT, "     max   (ms)%n"));
    List<Executable> checks = new ArrayList<>();
    double[] worstMedian = new double[PASSES];
    for (int s = 0; s < shots.size(); s++) {
      Shot shot = shots.get(s);
      String name = shot.name();
      report.append(
          String.format(Locale.ROOT, "%-28s", name + (shot.firing().ruled() ? "" : " *")));
      double max = 0.0;
      for (int pass = 0; pass < PASSES; pass++) {
        float[] sorted = millis[pass * shots.size() + s].clone();
        Arrays.sort(sorted);
        double median = 0.5 * (sorted[TIMED_FRAMES / 2 - 1] + sorted[TIMED_FRAMES / 2]);
        double p95 = sorted[(int) Math.ceil(0.95 * TIMED_FRAMES) - 1];
        max = Math.max(max, sorted[TIMED_FRAMES - 1]);
        report.append(String.format(Locale.ROOT, "      %6.3f / %6.3f", median, p95));
        if (!shot.firing().ruled()) {
          continue;
        }
        worstMedian[pass] = Math.max(worstMedian[pass], median);
        int which = pass + 1;
        checks.add(
            () ->
                assertTrue(
                    median <= MAX_MEDIAN_MS,
                    name + " pass " + which + ": median " + median + " ms"));
      }
      report.append(String.format(Locale.ROOT, "   %6.3f%n", max));
    }
    report.append(String.format(Locale.ROOT, "%-28s", "worst ruled median"));
    for (double worst : worstMedian) {
      report.append(String.format(Locale.ROOT, "      %6.3f         ", worst));
    }
    report
        .append(System.lineSeparator())
        .append("* outside the rule, for information")
        .append(System.lineSeparator());
    Files.writeString(OUT.resolve("report.txt"), report.toString(), StandardCharsets.UTF_8);
    System.out.print(report);
    assertAll(checks);
  }

  private static List<View> views(NozzlePlumeConfig config, Firing firing, float pressureFactor) {
    LauncherNozzles.Cluster longest = null;
    float length = 0f;
    for (LauncherNozzles.Cluster cluster : LauncherNozzles.clustersOf(firing.meshPath())) {
      float reach =
          config.look(cluster.look()).shapeAt(pressureFactor).lengthRadii()
              * cluster.radius()
              * firing.modelScale();
      if (reach > length) {
        longest = cluster;
        length = reach;
      }
    }
    if (longest == null) {
      throw new IllegalStateException("no cluster for " + firing.meshPath());
    }
    Vector3f exit =
        NozzlePlumeView.exitPoint(Vector3f.ZERO, Quaternion.IDENTITY, firing.modelScale(), longest);
    Vector3f firstHalf = new Vector3f(0f, exit.y - 0.4f * length, 0f);
    Vector3f inJet = exit.add(0f, -0.25f * length, 0f);
    Vector3f tail = new Vector3f(0f, exit.y - length, 0f);
    float follow = FOLLOW_DISTANCE_M / (float) firing.sizeMeters();
    float off = 30f * FastMath.DEG_TO_RAD;
    return List.of(
        new View("follow", new Vector3f(follow, 0f, 0f), Vector3f.ZERO, Vector3f.UNIT_Y),
        new View("side-1", firstHalf.add(1f, 0f, 0f), firstHalf, Vector3f.UNIT_Y),
        new View("side-0.5", firstHalf.add(0.5f, 0f, 0f), firstHalf, Vector3f.UNIT_Y),
        new View("side-0.25", firstHalf.add(0.25f, 0f, 0f), firstHalf, Vector3f.UNIT_Y),
        new View("inside-up", inJet, inJet.add(0f, 1f, 0f), Vector3f.UNIT_Z),
        new View("inside-down", inJet, inJet.add(0f, -1f, 0f), Vector3f.UNIT_Z),
        new View("inside-across", inJet, inJet.add(1f, 0f, 0f), Vector3f.UNIT_Y),
        new View("below-axis", tail.add(0f, -0.5f, 0f), tail, Vector3f.UNIT_Z),
        new View(
            "below-oblique",
            firstHalf.add(FastMath.sin(off), -FastMath.cos(off), 0f),
            firstHalf,
            Vector3f.UNIT_Y));
  }

  private static float[][] render(NozzlePlumeConfig config, List<Shot> shots) throws Exception {
    Files.createDirectories(OUT);
    int framesPerShot = SETTLE_FRAMES + TIMED_FRAMES;
    int timedFrames = PASSES * shots.size() * framesPerShot;
    float[][] millis = new float[PASSES * shots.size()][TIMED_FRAMES];
    CompletableFuture<float[][]> result = new CompletableFuture<>();
    SimpleApplication app =
        new SimpleApplication() {
          private final Node body = new Node("body");
          private BucketTimer timer;
          private int frame;
          private Node anchor;
          private NozzlePlumeView view;
          private String capture;

          @Override
          public void simpleInitApp() {
            try {
              AssetFactory.init(assetManager);
            } catch (OrbitlabException alreadyInitialized) {
              // A previous probe of this JVM initialized it.
            }
            viewPort.setBackgroundColor(ColorRGBA.Black);
            FilterPostProcessor processor = new FilterPostProcessor(assetManager);
            processor.setNumSamples(SAMPLES);
            BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Objects);
            bloom.setDownSamplingFactor(2f);
            bloom.setBlurScale(1f);
            bloom.setExposurePower(1f);
            bloom.setBloomIntensity(1.2f);
            processor.addFilter(bloom);
            viewPort.addProcessor(processor);
            cam.setFrustumPerspective(FOV_Y_DEG, (float) WIDTH / HEIGHT, 0.005f, 100f);
            flyCam.setEnabled(false);
            setDisplayFps(false);
            setDisplayStatView(false);
            timer =
                new BucketTimer(
                    renderManager, viewPort, (run, index, time) -> millis[run][index] = time);
            setAppProfiler(timer);
          }

          @Override
          public void simpleUpdate(float tpf) {
            try {
              int current = frame++;
              capture = null;
              if (current >= timedFrames) {
                timer.label(-1, -1);
                if (current >= timedFrames + QUERY_RING) {
                  result.complete(millis);
                  stop();
                }
                return;
              }
              int run = current / framesPerShot;
              int inShot = current % framesPerShot;
              Shot shot = shots.get(run % shots.size());
              if (inShot == 0) {
                stage(shot);
              }
              cam.setLocation(shot.view().location());
              cam.lookAt(shot.view().lookAt(), shot.view().up());
              view.update(drive(shot), body, true, cam.getLocation());
              timer.label(run, inShot - SETTLE_FRAMES);
              if (run < shots.size() && inShot == framesPerShot - 1) {
                capture = shot.name();
              }
            } catch (RuntimeException e) {
              result.completeExceptionally(e);
              stop();
            }
          }

          @Override
          public void simpleRender(RenderManager rm) {
            if (capture != null) {
              save(rm.getRenderer(), capture);
            }
          }

          private void stage(Shot shot) {
            if (anchor != null) {
              rootNode.detachChild(anchor);
            }
            anchor = new Node("anchor-" + shot.name());
            rootNode.attachChild(anchor);
            view = new NozzlePlumeView(anchor, config);
            view.setMesh(shot.firing().meshPath(), shot.firing().modelScale());
          }

          private PlumeDrive drive(Shot shot) {
            return new PlumeDrive(
                shot.firing().role(),
                1f,
                FLOW_SECONDS,
                shot.pressureFactor(),
                1.0,
                shot.firing().coreThrottle());
          }
        };
    var settings = new AppSettings(true);
    settings.setResolution(WIDTH, HEIGHT);
    settings.setSamples(SAMPLES);
    settings.setFrameRate(-1);
    settings.setVSync(false);
    app.setSettings(settings);
    app.setShowSettings(false);
    app.start(JmeContext.Type.OffscreenSurface);
    return result.get(600, TimeUnit.SECONDS);
  }

  private static void save(Renderer renderer, String name) {
    ByteBuffer pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
    renderer.readFrameBuffer(null, pixels);
    BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < HEIGHT; y++) {
      for (int x = 0; x < WIDTH; x++) {
        int i = (y * WIDTH + x) * 4;
        int r = pixels.get(i) & 0xff;
        int g = pixels.get(i + 1) & 0xff;
        int b = pixels.get(i + 2) & 0xff;
        image.setRGB(x, HEIGHT - 1 - y, (r << 16) | (g << 8) | b);
      }
    }
    try {
      ImageIO.write(image, "png", OUT.resolve(name + ".png").toFile());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Where a timed frame goes: its shot's run, its index among the run's timed frames, its time. */
  @FunctionalInterface
  private interface TimeSink {
    void accept(int run, int index, float millis);
  }

  /**
   * Times the main pass's transparent bucket of one viewport: opens a {@code GL_TIME_ELAPSED} query
   * when that bucket starts and closes it at the next profiler event, which comes once the bucket
   * is drawn. The bloom's pass draws the same buckets under a forced technique and is left out.
   * Each query is read {@value #QUERY_RING} frames later, when its slot comes round again.
   */
  private static final class BucketTimer implements AppProfiler {
    private final RenderManager renderManager;
    private final ViewPort viewPort;
    private final TimeSink sink;
    private final int[] pendingRun = new int[QUERY_RING];
    private final int[] pendingIndex = new int[QUERY_RING];
    private int[] queries;
    private int frame;
    private int run = -1;
    private int index = -1;
    private boolean open;

    BucketTimer(RenderManager renderManager, ViewPort viewPort, TimeSink sink) {
      this.renderManager = renderManager;
      this.viewPort = viewPort;
      this.sink = sink;
      Arrays.fill(pendingIndex, -1);
    }

    /**
     * Files the frames to come under a shot's run and a frame index; a negative index times none.
     */
    void label(int run, int index) {
      this.run = run;
      this.index = index;
    }

    @Override
    public void appStep(AppStep step) {
      close();
      if (step == AppStep.BeginFrame) {
        frame++;
        harvest(frame % QUERY_RING);
      }
    }

    @Override
    public void appSubStep(String... additionalInfo) {
      close();
    }

    @Override
    public void vpStep(VpStep step, ViewPort vp, RenderQueue.Bucket bucket) {
      close();
      if (step == VpStep.RenderBucket
          && bucket == RenderQueue.Bucket.Transparent
          && vp == viewPort
          && renderManager.getForcedTechnique() == null
          && index >= 0) {
        Renderer renderer = renderManager.getRenderer();
        if (queries == null) {
          queries = renderer.generateProfilingTasks(QUERY_RING);
        }
        int slot = frame % QUERY_RING;
        renderer.startProfiling(queries[slot]);
        pendingRun[slot] = run;
        pendingIndex[slot] = index;
        open = true;
      }
    }

    @Override
    public void spStep(SpStep step, String... additionalInfo) {
      close();
    }

    private void harvest(int slot) {
      if (pendingIndex[slot] < 0) {
        return;
      }
      long nanos = renderManager.getRenderer().getProfilingTime(queries[slot]);
      sink.accept(pendingRun[slot], pendingIndex[slot], nanos / 1e6f);
      pendingIndex[slot] = -1;
    }

    private void close() {
      if (open) {
        renderManager.getRenderer().stopProfiling();
        open = false;
      }
    }
  }
}
