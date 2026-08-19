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
import com.smousseur.orbitlab.core.PropertiesService;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.TextureDiagnostics;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.LunarTransferMission;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSearch;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSolver;
import com.smousseur.orbitlab.simulation.mission.window.problem.TranslunarInjectionPlanWindowProblem;
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
import com.smousseur.orbitlab.states.mission.TelemetryWidgetAppState;
import com.smousseur.orbitlab.states.orbits.OrbitInitAppState;
import com.smousseur.orbitlab.states.orbits.OrbitRuntimeAppState;
import com.smousseur.orbitlab.states.scene.BreadcrumbWidgetAppState;
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
import org.orekit.time.AbsoluteDate;

import java.time.Duration;
import java.util.List;

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

  /** Days the lunar demonstration walks forward looking for a flyable encounter geometry. */
  private static final int LUNAR_DEMO_DAYS = 30;

  /** Injection cost above which the lunar demonstration does not offer an epoch (m/s). */
  private static final double LUNAR_DEMO_BUDGET = 4_000.0;

  /**
   * How much dearer than the month's cheapest epoch the demonstration still accepts (m/s).
   *
   * <p>Five, because the criterion was measured swinging only 14 m/s over a month (3 182.8 to
   * 3 196.9, {@code TranslunarInjectionPlanWindowProblemTest}) on a 3 183 m/s floor: the 4 000 m/s
   * budget sits above the whole month and cuts nothing, so without a margin the slot would be the
   * searched range itself.
   */
  private static final double LUNAR_DEMO_MARGIN = 5.0;

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
    stateManager.attach(new SolarSystemSceneAppState(applicationContext));
    stateManager.attach(new OrbitInitAppState(applicationContext));
    stateManager.attach(new OrbitRuntimeAppState(applicationContext));
    stateManager.attach(new TimelineWidgetAppState(applicationContext));
    stateManager.attach(new BreadcrumbWidgetAppState(applicationContext));
    stateManager.attach(new TelemetryWidgetAppState(applicationContext));
    stateManager.attach(new MissionTimelineAppState(applicationContext));
    stateManager.attach(new MissionDisplayPanelAppState(applicationContext));
    stateManager.attach(new MissionPanelWidgetAppState(applicationContext));
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

    loadLunarDemoIfRequested(applicationContext);
  }

  private void loadLunarDemoIfRequested(ApplicationContext applicationContext) {
    if (!Boolean.parseBoolean(PropertiesService.get().getProperty("mission.lunarDemo"))) {
      return;
    }
    LunarTransferMission mission = new LunarTransferMission("Translunar transfer");
    AbsoluteDate now = applicationContext.clock().now();

    TranslunarInjectionPlanWindowProblem problem =
        new TranslunarInjectionPlanWindowProblem(mission);
    LaunchWindowSearch search =
        new LaunchWindowSearch(
            now,
            Duration.ofDays(LUNAR_DEMO_DAYS),
            problem.coarseStep(),
            Duration.ofMinutes(10),
            LUNAR_DEMO_BUDGET,
            LUNAR_DEMO_MARGIN,
            1);
    LaunchWindowSolver solver = new LaunchWindowSolver(problem);
    List<LaunchWindow> windows = solver.solve(search);
    if (windows.isEmpty()) {
      LOGGER.warn(
          "mission.lunarDemo is on, but no epoch in the next {} days can deliver the aimed perilune;"
              + " not loading the demonstration mission",
          LUNAR_DEMO_DAYS);
      return;
    }

    MissionEntry entry = new MissionEntry(mission);
    AbsoluteDate scheduledDate = windows.getFirst().best().epoch();
    LOGGER.info("Scheduled date for lunar demo mission: {}", scheduledDate);
    entry.setScheduledDate(scheduledDate);
    entry.setVisible(true);
    applicationContext.missionContext().addMission(entry);
    applicationContext.eventBus().publishMissionAction(entry.id(), EventBus.MissionAction.OPTIMIZE);
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
