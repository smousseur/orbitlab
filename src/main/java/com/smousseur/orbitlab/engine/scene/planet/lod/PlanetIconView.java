package com.smousseur.orbitlab.engine.scene.planet.lod;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.IconComponent;
import com.smousseur.orbitlab.engine.scene.planet.PlanetDescriptor;

public class PlanetIconView {
  private static final float ICON_SIZE = 16f;

  private final Container container;

  private boolean visible = true;

  public PlanetIconView(Node guiNode, PlanetDescriptor planetDescriptor) {
    container = new Container();
    container.setBackground(null);
    container.setLayout(new BoxLayout(Axis.Y, FillMode.None));

    ColorRGBA color = planetDescriptor.orbitColor();

    Label label = new Label(planetDescriptor.displayName());
    label.setColor(color);
    container.addChild(label);

    Label dotLabel = container.addChild(new Label(""));
    dotLabel.setTextHAlignment(HAlignment.Center);
    IconComponent icon = new IconComponent("textures/white-dot.png");
    icon.setHAlignment(HAlignment.Center);
    icon.setIconSize(new Vector2f(ICON_SIZE, ICON_SIZE));
    icon.setColor(color);

    Texture tex = icon.getImageTexture();
    tex.setMagFilter(Texture.MagFilter.Bilinear);
    tex.setMinFilter(Texture.MinFilter.Trilinear);
    dotLabel.setIcon(icon);

    guiNode.attachChild(container);
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
    container.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
  }

  public void updateScreenPosition(Camera cam, Node anchor3d) {
    if (!visible) {
      return;
    }
    Vector3f world = anchor3d.getWorldTranslation();
    Vector3f screen = cam.getScreenCoordinates(world);
    // z en dehors => derrière caméra / non visible
    if (screen.z < 0f || screen.z > 1f) {
      container.setCullHint(Spatial.CullHint.Always);
      return;
    }
    container.setCullHint(Spatial.CullHint.Inherit);
    Vector3f size = container.getPreferredSize();
    float x = screen.x - (size.x * 0.5f);
    float y = screen.y + (ICON_SIZE + size.y) * 0.5f;

    container.setLocalTranslation(x, y, 0f);
  }

  public void detach() {
    container.removeFromParent();
  }
}
