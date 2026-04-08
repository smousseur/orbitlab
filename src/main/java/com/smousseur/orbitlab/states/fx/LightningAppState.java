package com.smousseur.orbitlab.states.fx;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.core.SolarSystemBody;

/**
 * Application state that manages scene lighting to simulate sunlight in the solar system.
 *
 * <p>Adds an ambient light for baseline illumination and a directional light representing
 * sunlight. Each frame, the directional light is oriented to point from the Sun toward the
 * currently focused celestial body, ensuring realistic lighting as the view changes.
 */
public class LightningAppState extends BaseAppState {
  private final ApplicationContext context;
  private Node rootNode;
  private AmbientLight ambientLight;
  private DirectionalLight sunLight;

  /**
   * Creates a new lighting state.
   *
   * @param context the application context providing scene graph and focus view information
   */
  public LightningAppState(ApplicationContext context) {
    this.context = context;
  }

  @Override
  protected void initialize(Application app) {
    rootNode = context.sceneGraph().getRootNode();
    ambientLight = new AmbientLight();
    ambientLight.setColor(ColorRGBA.White.mult(0.3f)); // light intensity

    sunLight = new DirectionalLight();
    sunLight.setColor(ColorRGBA.White.mult(1.2f));
  }

  @Override
  public void update(float tpf) {
    Vector3f sunPosition = context.getBodySpatial(SolarSystemBody.SUN).getWorldTranslation();
    SolarSystemBody focusBody = context.focusView().getBody();
    if (focusBody != null) {
      Vector3f bodyPosition = context.getBodySpatial(focusBody).getWorldTranslation();
      sunLight.setDirection(bodyPosition.subtract(sunPosition).normalizeLocal());
    }
  }

  @Override
  protected void onEnable() {
    rootNode.addLight(ambientLight);
    rootNode.addLight(sunLight);
  }

  @Override
  protected void onDisable() {
    rootNode.removeLight(ambientLight);
    rootNode.removeLight(sunLight);
  }

  @Override
  protected void cleanup(Application app) {}
}
