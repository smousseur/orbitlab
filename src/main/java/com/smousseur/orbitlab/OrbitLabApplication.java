package com.smousseur.orbitlab;

import com.jme3.app.SimpleApplication;
import com.jme3.math.Vector3f;
import com.jme3.system.AppSettings;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.style.BaseStyles;
import com.smousseur.orbitlab.app.SimulationContext;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.EngineConfig;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.states.camera.FloatingOriginAppState;
import com.smousseur.orbitlab.states.camera.ViewModeAppState;
import com.smousseur.orbitlab.states.scene.PlanetHudMarkersAppState;
import com.smousseur.orbitlab.states.scene.TimelineWidgetAppState;
import com.smousseur.orbitlab.states.time.SimulationClockAppState;
import com.smousseur.orbitlab.states.camera.OrbitCameraAppState;
import com.smousseur.orbitlab.states.ephemeris.EphemerisAppState;
import com.smousseur.orbitlab.states.ephemeris.OrbitOrchestrationAppState;
import com.smousseur.orbitlab.states.scene.PlanetPoseAppState;
import com.smousseur.orbitlab.states.scene.SolarSystemSceneAppState;
import com.smousseur.orbitlab.ui.clock.TimelineStyles;

public class OrbitLabApplication extends SimpleApplication {
  public static OrbitLabApplication app;

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
    TimelineStyles.init(assetManager);

    SimulationContext simulationContext = new SimulationContext(rootNode, guiNode);
    stateManager.attach(new SimulationClockAppState(simulationContext));
    stateManager.attach(new EphemerisAppState(simulationContext));
    stateManager.attach(new OrbitOrchestrationAppState(simulationContext));
    stateManager.attach(new PlanetPoseAppState(simulationContext, assetManager));
    stateManager.attach(new ViewModeAppState(simulationContext));
    stateManager.attach(new FloatingOriginAppState(simulationContext));
    stateManager.attach(new PlanetHudMarkersAppState(simulationContext));
    stateManager.attach(new SolarSystemSceneAppState(simulationContext, assetManager));

    stateManager.attach(new TimelineWidgetAppState(simulationContext));

    flyCam.setEnabled(false);

    EngineConfig engineConfig = EngineConfig.defaultSolarSystem();

    // TODO: wire this fallback to the actual SolarRoot world position when exposed by the scene
    // layer.
    OrbitCameraAppState orbitCam =
        new OrbitCameraAppState(
            engineConfig.orbitCamera(),
            () -> Vector3f.ZERO,
            () -> false // TODO: hook Lemur/GUI mouse capture here
            );
    stateManager.attach(orbitCam);

    cam.setLocation(new Vector3f(0f, 0f, 9000f));
    cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);

    cam.setFrustumNear(1f);
    cam.setFrustumFar(50000f);

    flyCam.setMoveSpeed(2000f);
  }
}
