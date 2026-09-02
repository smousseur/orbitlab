package com.smousseur.orbitlab;

import com.jme3.app.SimpleApplication;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.system.AppSettings;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.event.PickState;
import com.simsilica.lemur.style.BaseStyles;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.TextureDiagnostics;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.window.problem.EarthLaunchWindowPlanner;
import com.smousseur.orbitlab.states.InitAppState;
import com.smousseur.orbitlab.states.camera.CameraTransitionAppState;
import com.smousseur.orbitlab.states.camera.FloatingOriginAppState;
import com.smousseur.orbitlab.states.camera.NearCameraSyncAppState;
import com.smousseur.orbitlab.states.camera.OrbitCameraAppState;
import com.smousseur.orbitlab.states.camera.ViewModeAppState;
import com.smousseur.orbitlab.states.ephemeris.EphemerisAppState;
import com.smousseur.orbitlab.states.fx.LightningAppState;
import com.smousseur.orbitlab.states.fx.PostFxAppState;
import com.smousseur.orbitlab.states.mission.MissionDisplayPanelAppState;
import com.smousseur.orbitlab.states.mission.MissionOrchestratorAppState;
import com.smousseur.orbitlab.states.mission.MissionPanelWidgetAppState;
import com.smousseur.orbitlab.states.mission.MissionWizardAppState;
import com.smousseur.orbitlab.states.mission.ScenarioAppState;
import com.smousseur.orbitlab.states.mission.TelemetryWidgetAppState;
import com.smousseur.orbitlab.states.orbits.OrbitInitAppState;
import com.smousseur.orbitlab.states.orbits.OrbitRuntimeAppState;
import com.smousseur.orbitlab.states.scene.BreadcrumbWidgetAppState;
import com.smousseur.orbitlab.states.scene.MeshCalibrationAppState;
import com.smousseur.orbitlab.states.scene.PlanetHudMarkersAppState;
import com.smousseur.orbitlab.states.scene.PlanetPoseAppState;
import com.smousseur.orbitlab.states.scene.SkyboxAppState;
import com.smousseur.orbitlab.states.scene.SolarSystemSceneAppState;
import com.smousseur.orbitlab.states.time.MissionTimelineAppState;
import com.smousseur.orbitlab.states.time.SimulationClockAppState;
import com.smousseur.orbitlab.states.time.TimelineWidgetAppState;
import com.smousseur.orbitlab.ui.AppStyles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main entry point for the OrbitLab application.
 *
 * <p>Extends JMonkeyEngine's {@link SimpleApplication} to set up a dual-viewport 3D rendering
 * environment for orbital mechanics simulation. Initializes the Orekit astrodynamics library,
 * configures the GUI (Lemur), and registers all application states that drive the simulation,
 * rendering, and user interaction.
 */
public class OrbitLabApplication extends SimpleApplication {
  /** Global application instance. */
  public static OrbitLabApplication app;

  private static final int ANISOTROPIC_FILTER_LEVEL = 8;

  private static final Logger LOGGER = LogManager.getLogger(OrbitLabApplication.class);

  /** Name of the frame warm-up thread, so a thread dump taken during it explains itself. */
  private static final String FRAME_WARM_UP_THREAD_NAME = "orekit-frame-warmup";

  /**
   * Application entry point. Configures window settings and starts the JME3 application loop.
   *
   * @param args command-line arguments (currently unused)
   */
  public static void main(String[] args) {
    app = new OrbitLabApplication();
    AppSettings settings = new AppSettings(true);
    settings.setResolution(1280, 720);
    settings.setTitle("Orbitlab");
    settings.setSamples(4);
    app.setSettings(settings);
    app.setShowSettings(false);
    app.start();
  }

  @Override
  public void simpleInitApp() {
    GuiGlobals.initialize(this);
    OrekitService.get().initialize();
    startFrameWarmUp();
    BaseStyles.loadGlassStyle();
    GuiGlobals.getInstance().getStyles().setDefaultStyle("base");
    AssetFactory.init(assetManager);
    AppStyles.init(assetManager);
    renderer.setDefaultAnisotropicFilter(ANISOTROPIC_FILTER_LEVEL);
    TextureDiagnostics.logRendererCaps(renderer);

    ApplicationContext applicationContext = new ApplicationContext(rootNode, guiNode);
    stateManager.attach(new InitAppState());
    stateManager.attach(new SkyboxAppState(applicationContext));
    stateManager.attach(new SimulationClockAppState(applicationContext));
    stateManager.attach(new EphemerisAppState(applicationContext));
    stateManager.attach(new PlanetPoseAppState(applicationContext));
    stateManager.attach(new ViewModeAppState(applicationContext));
    CameraTransitionAppState cameraTransition = new CameraTransitionAppState(applicationContext);
    applicationContext.setCameraTransition(cameraTransition);
    stateManager.attach(cameraTransition);
    stateManager.attach(new FloatingOriginAppState(applicationContext));
    MissionWizardAppState wizardState = new MissionWizardAppState(applicationContext);
    flyCam.setEnabled(false);
    OrbitCameraAppState orbitCam =
        new OrbitCameraAppState(
            applicationContext,
            () -> Vector3f.ZERO,
            () -> wizardState.isWizardVisible() || cameraTransition.isActive());
    applicationContext.setOrbitCamera(orbitCam);
    stateManager.attach(orbitCam);

    stateManager.attach(new MissionOrchestratorAppState(applicationContext));
    stateManager.attach(new PlanetHudMarkersAppState(applicationContext));
    stateManager.attach(new MeshCalibrationAppState(applicationContext));
    stateManager.attach(new SolarSystemSceneAppState(applicationContext));
    stateManager.attach(new OrbitInitAppState(applicationContext));
    stateManager.attach(new OrbitRuntimeAppState(applicationContext));
    stateManager.attach(new TimelineWidgetAppState(applicationContext));
    stateManager.attach(new BreadcrumbWidgetAppState(applicationContext));
    stateManager.attach(new TelemetryWidgetAppState(applicationContext));
    stateManager.attach(new MissionTimelineAppState(applicationContext));
    stateManager.attach(new MissionDisplayPanelAppState(applicationContext));
    stateManager.attach(new MissionPanelWidgetAppState(applicationContext));
    stateManager.attach(new ScenarioAppState(applicationContext));
    stateManager.attach(new LightningAppState(applicationContext));
    stateManager.attach(wizardState);

    cam.setLocation(new Vector3f(0f, 0f, 9000f));
    cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);

    cam.setFrustumNear(1f);
    cam.setFrustumFar(50000f);

    flyCam.setMoveSpeed(2000f);

    // Dual viewports:
    // - far: current cam + current viewport
    // - near: new cam + new viewport rendering nearRoot, with depth cleared
    var sceneGraph = applicationContext.sceneGraph();

    Camera farCam = cam;
    ViewPort farViewport = viewPort;

    farViewport.detachScene(rootNode);
    farViewport.attachScene(sceneGraph.getFarRoot());

    Camera skyCam = farCam.clone();
    applicationContext.setSkyCamera(skyCam);
    ViewPort skyViewport = renderManager.createPreView("SkyView", skyCam);
    skyViewport.setClearFlags(true, true, true);
    skyViewport.attachScene(sceneGraph.getSkyRoot());

    farViewport.setClearFlags(false, true, true);

    Camera nearCam = farCam.clone();
    applicationContext.setNearCamera(nearCam);
    ViewPort nearViewport = renderManager.createPostView("NearView", nearCam);

    nearViewport.setClearFlags(false, true, false); // don't clear color, DO clear depth
    nearViewport.attachScene(sceneGraph.getNearRoot());

    // Re-order the GUI viewport so it renders AFTER NearView.
    guiViewPort.detachScene(guiNode);
    ViewPort guiPost = renderManager.createPostView("GuiPost", cam);
    guiPost.setClearFlags(false, false, false);
    guiPost.attachScene(guiNode);

    getStateManager().getState(PickState.class).removeCollisionRoot(guiViewPort);
    getStateManager().getState(PickState.class).addCollisionRoot(guiPost, PickState.PICK_LAYER_GUI);

    stateManager.attach(new NearCameraSyncAppState(applicationContext, nearCam));

    stateManager.attach(new PostFxAppState(nearViewport, skyViewport, farViewport));
  }

  /**
   * Starts the Orekit terrestrial-frame warm-up on a daemon thread.
   *
   * <p><b>What it buys.</b> The first {@link OrekitService#itrf()} of a JVM costs 8 483 ms — Orekit
   * loading the Earth-orientation data the terrestrial chain hangs on — and every user of that
   * frame reaches it lazily, so the bill lands on whoever asks first. In this application that is
   * the render thread, whether through a mission being created or through the wizard's planning
   * page. A second tier of 549 ms, JIT and first-solve caching, follows it; {@link
   * EarthLaunchWindowPlanner#warmUp()} absorbs that one. Neither is a property of the launch window
   * criterion, which measures 4–9 ms per solve once warm.
   *
   * <p><b>Background rather than blocking, and that is a trade decided.</b> Blocking {@code
   * simpleInitApp} would guarantee no freeze but would add nine seconds to every start. Started
   * here, startup stays exactly as fast as it was; a user who reaches the wizard within the first
   * nine seconds still waits, which is rare and in any case strictly better than the state before
   * this warm-up, where every user waited.
   *
   * <p><b>Daemon</b>, so that a warm-up still running cannot hold up JVM shutdown, and
   * <b>named</b>, so that a thread dump taken during those nine seconds explains itself. A failure
   * is logged and swallowed: everything it warms works without it, only slowly, so it must never be
   * able to take the application down.
   */
  private void startFrameWarmUp() {
    Thread thread = new Thread(OrbitLabApplication::warmUpFrames, FRAME_WARM_UP_THREAD_NAME);
    thread.setDaemon(true);
    thread.start();
  }

  private static void warmUpFrames() {
    long startNanos = System.nanoTime();
    try {
      OrekitService.get().warmUpFrames();
      EarthLaunchWindowPlanner.warmUp();
      LOGGER.info(
          "Earth frame warm-up finished in {} ms", (System.nanoTime() - startNanos) / 1_000_000L);
    } catch (RuntimeException e) {
      LOGGER.warn(
          "Earth frame warm-up failed after {} ms; frames and launch windows still work, but the"
              + " first use of either will pay the cost on its own thread",
          (System.nanoTime() - startNanos) / 1_000_000L,
          e);
    }
  }

  @Override
  public void destroy() {
    try {
      AssetFactory.get().shutdown();
    } finally {
      super.destroy();
    }
  }
}
