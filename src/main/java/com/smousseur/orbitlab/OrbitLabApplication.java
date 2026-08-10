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
   * Anisotropic filtering level applied to every texture that does not carry one of its own, which
   * as of today is all of them — see {@link TextureDiagnostics}, whose boot report is what makes
   * this a global setting rather than a per-texture pass.
   *
   * <p>It sharpens planetary surfaces seen at a grazing angle, where a mipmapped filter alone has
   * to pick its level from the more compressed of the two UV axes and therefore blurs the axis that
   * needed no reduction. Sampling is adaptive, so face-on surfaces cost nothing extra.
   *
   * <p>Deliberately 8 and not the 16 the driver allows: past 8 the returns fall off sharply while
   * the sample count keeps doubling on exactly the oblique fragments that are already the most
   * expensive. 16 is available if a side-by-side ever justifies it.
   *
   * <p><b>This does not touch the orbit lines.</b> Anisotropy is a texture-sampling state and the
   * trajectories are GL line primitives — no texture, nothing to filter. Their aliasing is not
   * addressable from {@code AppSettings} at all (MSAA does not reliably antialias lines, and {@code
   * glLineWidth > 1} is silently clamped to 1 px in a core profile); that is RND-4's job.
   */
  private static final int ANISOTROPIC_FILTER_LEVEL = 8;

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
    // Before FloatingOriginAppState, and that is a requirement, not a preference: on the frame a
    // transition ends it flips the focus, and the floating origin has to re-centre the scene within
    // that same frame for the two moves to cancel — cf. CameraTransitionAppState.finish().
    CameraTransitionAppState cameraTransition = new CameraTransitionAppState(applicationContext);
    applicationContext.setCameraTransition(cameraTransition);
    stateManager.attach(cameraTransition);
    // Attach order IS update order (AppStateManager walks the states in insertion order), and it
    // matters here: FloatingOriginAppState owns the frame offsets, and every state that reads a
    // world position back out of the scene graph must come after it. MissionOrchestratorAppState
    // does exactly that (the LOD measures the camera-to-spacecraft distance), so it must not be
    // moved back above this line — cf. docs/graphics-effects/spacecraft-view-artefacts.md §3.
    stateManager.attach(new FloatingOriginAppState(applicationContext));

    // The orbit camera writes the far camera's pose for this frame, and it belongs BEFORE its
    // consumers. MissionOrchestratorAppState and PlanetHudMarkersAppState both measure a
    // camera-to-body distance and project world positions onto the screen; jME refreshes world
    // transforms on demand, so the positions they read are this frame's, but cam.getLocation() is
    // only written here — leaving them to pair a fresh position with last frame's camera. That was
    // invisible while the camera only ever moved at the speed a hand drags a mouse, and stopped
    // being invisible when transitions started moving it several degrees and several percent of
    // its distance per frame: the HUD icons lagged the scene they annotate.
    //
    // It still has to come after FloatingOriginAppState, which owns the frame offsets it reads and
    // sets the far-plane floor it applies.
    MissionWizardAppState wizardState = new MissionWizardAppState(applicationContext);
    flyCam.setEnabled(false);
    OrbitCameraAppState orbitCam =
        new OrbitCameraAppState(
            applicationContext,
            () -> Vector3f.ZERO,
            // A transition drives the pivot and the distance itself; letting the mouse in at the
            // same time would fight it. Same switch as the wizard's, for the same reason.
            () -> wizardState.isWizardVisible() || cameraTransition.isActive());
    applicationContext.setOrbitCamera(orbitCam);
    stateManager.attach(orbitCam);

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
    stateManager.attach(new NearCameraSyncAppState(applicationContext, nearCam));

    // Post-processing. The filter chain hangs off NearView because that is the viewport that draws
    // the bodies' 3D models — the far viewport only carries their position anchors and the orbit
    // lines. The sky and far viewports are handed to it so it can route them into its framebuffer:
    // its composite pass clears the screen, and they would otherwise be wiped out. Attached last so
    // both viewports it re-routes already exist.
    stateManager.attach(new PostFxAppState(nearViewport, skyViewport, farViewport));
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
