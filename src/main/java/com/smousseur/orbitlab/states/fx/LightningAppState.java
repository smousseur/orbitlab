package com.smousseur.orbitlab.states.fx;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.core.SolarSystemBody;

/**
 * Application state that manages scene lighting to simulate sunlight in the solar system.
 *
 * <p>Adds an ambient light for baseline illumination and a directional light representing sunlight.
 * Each frame, the directional light is oriented to point from the Sun towards the body the rendered
 * frame is centred on, which is the one the near viewport draws.
 */
public class LightningAppState extends BaseAppState {

  /**
   * Shortest Sun-to-body baseline that still carries a usable direction, in solar units. Only the
   * Sun itself falls under it — the closest other body is Mercury, at some 57 units.
   */
  private static final float MIN_SUNLIGHT_BASELINE = 1e-6f;

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

  /**
   * Aims the sunlight at the body the rendered frame is centred on.
   *
   * <p><b>The frame's centre, not the focus.</b> A transition hands the two apart partway through
   * its flight, and it is the centre that the near viewport draws: lighting the source instead
   * leaves the body actually on screen lit from the wrong direction — or not lit at all when the
   * source is the Sun, since the Sun-to-Sun baseline is zero and {@code normalizeLocal} answers the
   * zero vector, which switches a directional light off. That is a black globe on approach, and it
   * is what the guard below prevents rather than merely the focus change.
   */
  @Override
  public void update(float tpf) {
    SolarSystemBody centre = context.focusView().renderCentreBody();
    Spatial sun = context.getBodySpatial(SolarSystemBody.SUN);
    Spatial centreSpatial = centre == null ? null : context.getBodySpatial(centre);
    if (sun == null || centreSpatial == null) {
      return;
    }
    Vector3f direction = centreSpatial.getWorldTranslation().subtract(sun.getWorldTranslation());
    if (direction.length() < MIN_SUNLIGHT_BASELINE) {
      // The frame is centred on the Sun: there is no direction to derive, and writing the zero
      // vector would put the scene out. Keep the last usable one.
      return;
    }
    sunLight.setDirection(direction.normalizeLocal());
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
