package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.function.Executable;

/**
 * Renders the plumes offscreen on the real driver, side on over black, through {@link
 * NozzlePlumeView} and the production shader, and measures how bright the longest jet of each mesh
 * is along its axis — each launcher silhouette that fires, and each payload firing in orbit, drawn
 * one unit across.
 *
 * <p>A probe, not a test: run with {@code -Dorbitlab.probe=true --tests '*Fx3PlumeLookProbe*'}. The
 * images and the report go to {@code build/fx3-look/}. The brightness along a jet is the mean
 * luminance of a band one exit radius either side of its axis, sampled at each hundredth of its
 * length and smoothed over five hundredths, so the shock diamonds do not decide the shape.
 *
 * <p>Three properties are asserted on every jet, the proxies the on-screen judgement is checked
 * against: it is <b>brightest in its first quarter</b>, as a jet is at its exit; it <b>fades over
 * at least {@value #MIN_FALL} of its length</b> from 90 % to 10 % of its peak, rather than ending
 * on a cut; and it <b>still shows at mid-length</b>, at {@value #MIN_MID} of its peak or more.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class Fx3PlumeLookProbe {

  private static final int WIDTH = 1000;
  private static final int HEIGHT = 500;
  private static final float FOV_Y_DEG = 20f;
  private static final Vector3f LOOK_AT = new Vector3f(0.6f, 0f, 0f);
  private static final float CAMERA_DISTANCE = 2.13f;
  private static final int WARM_UP_FRAMES = 3;
  private static final float FLOW_SECONDS = 10f;
  private static final Path OUT = Path.of("build", "fx3-look");

  private static final float MAX_PEAK_AT = 0.25f;
  private static final float MIN_FALL = 0.4f;
  private static final float MIN_MID = 0.15f;

  /**
   * The jet runs along the mesh's {@code -Y}: turned to run along the screen's {@code +X}, with the
   * mesh's {@code +Z}, along which the Falcon Heavy's boosters sit, up the screen.
   */
  private static final Quaternion SIDE_ON =
      new Quaternion()
          .fromAxes(new Vector3f(0f, 0f, -1f), new Vector3f(-1f, 0f, 0f), new Vector3f(0f, 1f, 0f));

  /** A silhouette firing its role's clusters, drawn {@code modelScale} per mesh unit. */
  private record Case(
      String name,
      String meshPath,
      float modelScale,
      StageRole role,
      double altitudeM,
      float coreThrottle) {

    static Case launcher(
        String name, LauncherModel launcher, String suffix, StageRole role, double altitudeM) {
      return new Case(
          name,
          LauncherAssets.modelPath(launcher.id()).replace(".gltf", suffix + ".gltf"),
          1f,
          role,
          altitudeM,
          (float) launcher.ascentProfile().coreThrottle());
    }

    /** A payload mesh firing its engine in orbit, one unit across as it is normalized. */
    static Case payload(String name, PayloadModel payload) {
      String path = PayloadAssets.forPayload(payload.id()).orElseThrow().meshPath();
      Spatial model = new DesktopAssetManager(true).loadModel(path);
      model.updateGeometricState();
      BoundingBox box = (BoundingBox) model.getWorldBound();
      float extent = 2f * Math.max(box.getXExtent(), Math.max(box.getYExtent(), box.getZExtent()));
      return new Case(name, path, 1f / extent, StageRole.KICK, 400_000.0, 1f);
    }
  }

  private record Measured(
      String name, float[] smoothed, float peak, float peakAt, float fall, float mid) {}

  private static final List<Case> CASES =
      List.of(
          Case.launcher("fh-full-0km", Launchers.FALCON_HEAVY, "", StageRole.BOOSTER, 0.0),
          Case.launcher("fh-full-20km", Launchers.FALCON_HEAVY, "", StageRole.BOOSTER, 20_000.0),
          Case.launcher(
              "fh-core-45km", Launchers.FALCON_HEAVY, "-after_boosters", StageRole.CORE, 45_000.0),
          Case.launcher(
              "fh-mvac-vacuum", Launchers.FALCON_HEAVY, "-after_s1", StageRole.UPPER, 150_000.0),
          Case.launcher("a64-full-0km", Launchers.ARIANE_64, "", StageRole.BOOSTER, 0.0),
          Case.launcher("a64-full-20km", Launchers.ARIANE_64, "", StageRole.BOOSTER, 20_000.0),
          Case.launcher(
              "a64-vulcain-60km", Launchers.ARIANE_64, "-after_boosters", StageRole.CORE, 60_000.0),
          Case.launcher(
              "a64-vinci-vacuum", Launchers.ARIANE_64, "-after_s1", StageRole.UPPER, 150_000.0),
          Case.payload("goes-orbit", Payloads.GEO_SAT),
          Case.payload("landsat8-orbit", Payloads.EARTH_OBSERVATION_SAT),
          Case.payload("lro-orbit", Payloads.LUNAR_ORBITER));

  @Test
  void everyJetIsBrightestAtItsExitAndFadesToItsTail() throws Exception {
    List<Measured> measured = render();
    StringBuilder report = new StringBuilder();
    report.append(
        String.format(
            Locale.ROOT,
            "%-18s %5s %7s %6s %6s   profile at 2/10/25/50/75/90 %% of the length (%% of peak)%n",
            "case",
            "peak",
            "peakAt",
            "fall",
            "mid"));
    List<Executable> checks = new ArrayList<>();
    for (Measured m : measured) {
      report.append(
          String.format(
              Locale.ROOT,
              "%-18s %5.0f %6.0f%% %5.0f%% %5.0f%%   %s%n",
              m.name(),
              m.peak(),
              100 * m.peakAt(),
              100 * m.fall(),
              100 * m.mid(),
              percents(m)));
      checks.add(
          () -> assertTrue(m.peakAt() <= MAX_PEAK_AT, m.name() + " brightest at " + m.peakAt()));
      checks.add(() -> assertTrue(m.fall() >= MIN_FALL, m.name() + " fades over " + m.fall()));
      checks.add(() -> assertTrue(m.mid() >= MIN_MID, m.name() + " at mid-length " + m.mid()));
    }
    Files.writeString(OUT.resolve("report.txt"), report.toString(), StandardCharsets.UTF_8);
    System.out.print(report);
    assertAll(checks);
  }

  private static String percents(Measured m) {
    StringBuilder line = new StringBuilder();
    for (int k : new int[] {2, 10, 25, 50, 75, 90}) {
      line.append(String.format(Locale.ROOT, "%4.0f", 100 * m.smoothed()[k] / m.peak()));
    }
    return line.toString();
  }

  private static List<Measured> render() throws Exception {
    Files.createDirectories(OUT);
    CompletableFuture<List<Measured>> result = new CompletableFuture<>();
    NozzlePlumeConfig config = NozzlePlumeConfig.defaults();
    SimpleApplication app =
        new SimpleApplication() {
          private final List<Measured> measured = new ArrayList<>();
          private final Capture capture = new Capture();
          private int frame;
          private Node anchor;
          private Supplier<Measured> pending;

          @Override
          public void simpleInitApp() {
            try {
              AssetFactory.init(assetManager);
            } catch (OrbitlabException alreadyInitialized) {
              // A previous probe of this JVM initialized it.
            }
            viewPort.setBackgroundColor(ColorRGBA.Black);
            viewPort.addProcessor(capture);
            cam.setFrustumPerspective(FOV_Y_DEG, (float) WIDTH / HEIGHT, 0.01f, 100f);
            cam.setLocation(LOOK_AT.add(0f, 0f, CAMERA_DISTANCE));
            cam.lookAt(LOOK_AT, Vector3f.UNIT_Y);
            flyCam.setEnabled(false);
          }

          @Override
          public void simpleUpdate(float tpf) {
            try {
              if (pending != null) {
                measured.add(pending.get());
                pending = null;
              }
              int index = frame++ - WARM_UP_FRAMES;
              if (index < 0) {
                return;
              }
              if (index >= CASES.size()) {
                result.complete(measured);
                stop();
                return;
              }
              pending = stage(CASES.get(index));
            } catch (RuntimeException e) {
              result.completeExceptionally(e);
              stop();
            }
          }

          private Supplier<Measured> stage(Case c) {
            if (anchor != null) {
              rootNode.detachChild(anchor);
            }
            anchor = new Node("anchor-" + c.name());
            rootNode.attachChild(anchor);
            NozzlePlumeView view = new NozzlePlumeView(anchor, config);
            view.setMesh(c.meshPath(), c.modelScale());
            Node body = new Node("body");
            body.setLocalRotation(SIDE_ON);
            PlumeDrive drive =
                new PlumeDrive(
                    c.role(),
                    1f,
                    FLOW_SECONDS,
                    config.pressureFactor(c.altitudeM()),
                    1.0,
                    c.coreThrottle());
            view.update(drive, body, true, cam.getLocation());
            LauncherNozzles.Cluster axis = null;
            float length = 0f;
            for (LauncherNozzles.Cluster cluster : LauncherNozzles.clustersOf(c.meshPath())) {
              float reach =
                  config.look(cluster.look()).shapeAt(drive.pressureFactor()).lengthRadii()
                      * cluster.radius()
                      * c.modelScale();
              if (reach > length) {
                axis = cluster;
                length = reach;
              }
            }
            Vector3f exit = NozzlePlumeView.exitPoint(Vector3f.ZERO, SIDE_ON, c.modelScale(), axis);
            Vector3f exitPx = cam.getScreenCoordinates(exit);
            Vector3f endPx = cam.getScreenCoordinates(exit.add(length, 0f, 0f));
            Vector3f rimPx =
                cam.getScreenCoordinates(exit.add(0f, axis.radius() * c.modelScale(), 0f));
            capture.arm();
            return () -> measure(c.name(), capture.take(), exitPx, endPx, rimPx.y - exitPx.y);
          }
        };
    var settings = new AppSettings(true);
    settings.setResolution(WIDTH, HEIGHT);
    app.setSettings(settings);
    app.setShowSettings(false);
    app.start(JmeContext.Type.OffscreenSurface);
    return result.get(180, TimeUnit.SECONDS);
  }

  private static Measured measure(
      String name, BufferedImage image, Vector3f exitPx, Vector3f endPx, float radiusPx) {
    try {
      ImageIO.write(image, "png", OUT.resolve(name + ".png").toFile());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    int band = Math.max(2, Math.round(Math.abs(radiusPx)));
    float[] raw = new float[101];
    for (int k = 0; k <= 100; k++) {
      float x = exitPx.x + k / 100f * (endPx.x - exitPx.x);
      float y = exitPx.y + k / 100f * (endPx.y - exitPx.y);
      raw[k] = bandMean(image, Math.round(x), Math.round(y), band);
    }
    float[] smoothed = new float[101];
    for (int k = 0; k <= 100; k++) {
      float sum = 0f;
      int n = 0;
      for (int j = Math.max(0, k - 2); j <= Math.min(100, k + 2); j++) {
        sum += raw[j];
        n++;
      }
      smoothed[k] = sum / n;
    }
    int peakIndex = 2;
    for (int k = 2; k <= 100; k++) {
      if (smoothed[k] > smoothed[peakIndex]) {
        peakIndex = k;
      }
    }
    float peak = smoothed[peakIndex];
    int last90 = lastAtLeast(smoothed, 0.9f * peak);
    int last10 = lastAtLeast(smoothed, 0.1f * peak);
    return new Measured(
        name, smoothed, peak, peakIndex / 100f, (last10 - last90) / 100f, smoothed[50] / peak);
  }

  private static int lastAtLeast(float[] values, float threshold) {
    for (int k = values.length - 1; k >= 0; k--) {
      if (values[k] >= threshold) {
        return k;
      }
    }
    return 0;
  }

  /** Mean luminance, 0 to 255, of a column of pixels; y counted from the bottom, as GL reads. */
  private static float bandMean(BufferedImage image, int x, int yFromBottom, int halfHeight) {
    float sum = 0f;
    int n = 0;
    for (int dy = -halfHeight; dy <= halfHeight; dy++) {
      int row = image.getHeight() - 1 - (yFromBottom + dy);
      if (x < 0 || x >= image.getWidth() || row < 0 || row >= image.getHeight()) {
        continue;
      }
      int rgb = image.getRGB(x, row);
      sum +=
          0.2126f * ((rgb >> 16) & 0xff) + 0.7152f * ((rgb >> 8) & 0xff) + 0.0722f * (rgb & 0xff);
      n++;
    }
    return n == 0 ? 0f : sum / n;
  }

  /** Reads the frame back once armed, the way JME's own screenshot state does. */
  private static final class Capture implements SceneProcessor {
    private RenderManager renderManager;
    private boolean armed;
    private BufferedImage image;

    void arm() {
      armed = true;
      image = null;
    }

    BufferedImage take() {
      if (image == null) {
        throw new IllegalStateException("no frame captured");
      }
      return image;
    }

    @Override
    public void initialize(RenderManager rm, ViewPort vp) {
      renderManager = rm;
    }

    @Override
    public void reshape(ViewPort vp, int w, int h) {
      // Fixed size.
    }

    @Override
    public boolean isInitialized() {
      return renderManager != null;
    }

    @Override
    public void preFrame(float tpf) {
      // Nothing to prepare.
    }

    @Override
    public void postQueue(RenderQueue rq) {
      // Nothing to queue.
    }

    @Override
    public void postFrame(FrameBuffer out) {
      if (!armed) {
        return;
      }
      armed = false;
      ByteBuffer pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
      renderManager.getRenderer().readFrameBufferWithFormat(out, pixels, Image.Format.RGBA8);
      BufferedImage read = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
      for (int y = 0; y < HEIGHT; y++) {
        for (int x = 0; x < WIDTH; x++) {
          int i = (y * WIDTH + x) * 4;
          int r = pixels.get(i) & 0xff;
          int g = pixels.get(i + 1) & 0xff;
          int b = pixels.get(i + 2) & 0xff;
          read.setRGB(x, HEIGHT - 1 - y, (r << 16) | (g << 8) | b);
        }
      }
      image = read;
    }

    @Override
    public void cleanup() {
      // Nothing held.
    }

    @Override
    public void setProfiler(AppProfiler profiler) {
      // Not profiled.
    }
  }
}
