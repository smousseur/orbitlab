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
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.states.InitAppState;
import com.smousseur.orbitlab.states.camera.FloatingOriginAppState;
import com.smousseur.orbitlab.states.camera.NearCameraSyncAppState;
import com.smousseur.orbitlab.states.camera.OrbitCameraAppState;
import com.smousseur.orbitlab.states.camera.ViewModeAppState;
import com.smousseur.orbitlab.states.ephemeris.EphemerisAppState;
import com.smousseur.orbitlab.states.fx.LightningAppState;
import com.smousseur.orbitlab.states.mission.MissionDisplayPanelAppState;
import com.smousseur.orbitlab.states.mission.MissionOrchestratorAppState;
import com.smousseur.orbitlab.states.mission.MissionPanelWidgetAppState;
import com.smousseur.orbitlab.states.mission.MissionWizardAppState;
import com.smousseur.orbitlab.states.mission.TelemetryWidgetAppState;
import com.smousseur.orbitlab.states.orbits.OrbitInitAppState;
import com.smousseur.orbitlab.states.orbits.OrbitRuntimeAppState;
import com.smousseur.orbitlab.states.scene.PlanetHudMarkersAppState;
import com.smousseur.orbitlab.states.scene.PlanetPoseAppState;
import com.smousseur.orbitlab.states.scene.SkyboxAppState;
import com.smousseur.orbitlab.states.scene.SolarSystemSceneAppState;
import com.smousseur.orbitlab.states.time.SimulationClockAppState;
import com.smousseur.orbitlab.states.time.TimelineWidgetAppState;
import com.smousseur.orbitlab.ui.AppStyles;

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

    ApplicationContext applicationContext = new ApplicationContext(rootNode, guiNode);
    stateManager.attach(new InitAppState());
    stateManager.attach(new SkyboxAppState(applicationContext));
    stateManager.attach(new SimulationClockAppState(applicationContext));
    stateManager.attach(new EphemerisAppState(applicationContext));
    stateManager.attach(new PlanetPoseAppState(applicationContext));
    stateManager.attach(new ViewModeAppState(applicationContext));
    // Attach order IS update order (AppStateManager walks the states in insertion order), and it
    // matters here: FloatingOriginAppState owns the frame offsets, and every state that reads a
    // world position back out of the scene graph must come after it. MissionOrchestratorAppState
    // does exactly that (the LOD measures the camera-to-spacecraft distance), so it must not be
    // moved back above this line — cf. specs/graphics-effects/spacecraft-view-artefacts.md §3.
    stateManager.attach(new FloatingOriginAppState(applicationContext));
    stateManager.attach(new MissionOrchestratorAppState(applicationContext));
    stateManager.attach(new PlanetHudMarkersAppState(applicationContext));
    stateManager.attach(new SolarSystemSceneAppState(applicationContext));
    stateManager.attach(new OrbitInitAppState(applicationContext));
    stateManager.attach(new OrbitRuntimeAppState(applicationContext));
    stateManager.attach(new TimelineWidgetAppState(applicationContext));
    stateManager.attach(new TelemetryWidgetAppState(applicationContext));
    stateManager.attach(new MissionDisplayPanelAppState(applicationContext));
    stateManager.attach(new MissionPanelWidgetAppState(applicationContext));
    stateManager.attach(new LightningAppState(applicationContext));

    MissionWizardAppState wizardState = new MissionWizardAppState(applicationContext);
    stateManager.attach(wizardState);

    flyCam.setEnabled(false);

    OrbitCameraAppState orbitCam =
        new OrbitCameraAppState(
            applicationContext, () -> Vector3f.ZERO, wizardState::isWizardVisible);
    stateManager.attach(orbitCam);

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

    // Sky pre-view: rendered before everything else, it owns the background color.
    // It needs its own camera because JME's sky shader places the sky mesh at its raw model
    // radius (10) in view space: the far camera's dynamic near plane grows with the zoom
    // distance and would clip the skybox away entirely past ~10 000 world units.
    // SkyboxAppState keeps this camera's rotation and FoV in sync with the far camera.
    Camera skyCam = farCam.clone();
    applicationContext.setSkyCamera(skyCam);
    ViewPort skyViewport = renderManager.createPreView("SkyView", skyCam);
    skyViewport.setClearFlags(true, true, true);
    skyViewport.attachScene(sceneGraph.getSkyRoot());

    // The far viewport must no longer clear the color buffer, otherwise it erases the sky.
    farViewport.setClearFlags(false, true, true);

    Camera nearCam = farCam.clone();
    applicationContext.setNearCamera(nearCam);
    ViewPort nearViewport = renderManager.createPostView("NearView", nearCam);

    nearViewport.setClearFlags(false, true, false); // don't clear color, DO clear depth
    nearViewport.attachScene(sceneGraph.getNearRoot());

    // Re-order the GUI viewport so it renders AFTER NearView.
    // The default guiViewPort is rendered before post-views, so NearView
    // was drawing on top of the GUI. Fix: detach guiNode from the default
    // guiViewPort and create a new post-view for the GUI that comes after NearView.
    guiViewPort.detachScene(guiNode);
    ViewPort guiPost = renderManager.createPostView("GuiPost", cam);
    guiPost.setClearFlags(false, false, false);
    guiPost.attachScene(guiNode);

    // Re-register the GUI picking with Lemur so mouse events (click, hover, etc.)
    // work in the new GUI viewport instead of the now-empty default guiViewPort.
    getStateManager().getState(PickState.class).removeCollisionRoot(guiViewPort);
    getStateManager().getState(PickState.class).addCollisionRoot(guiPost, PickState.PICK_LAYER_GUI);

    // Near frustum: NearCameraSyncAppState owns the near cam's depth range and FoV every frame.
    stateManager.attach(new NearCameraSyncAppState(nearCam));
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
