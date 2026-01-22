package com.smousseur.orbitlab.engine.scene.planet;

import com.jme3.asset.AssetManager;
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
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import java.util.Objects;

/**
 * Marker 2D (Lemur) attaché au guiNode, positionné par projection de la position monde. Côté 3D: un
 * Node "anchor" invisible sert de point de référence monde.
 */
public final class PlanetHudMarkerView implements PlanetView {

  private static final float NAME_BG_ALPHA = 0.08f;
  private static final float ICON_SIZE = 16f;

  private final Node anchor3d;

  private final Container container;
  private final Label label;
  private final IconComponent icon;

  private boolean visible = true;

  public PlanetHudMarkerView(AssetManager assets, Node guiNode, PlanetDescriptor planetDescriptor) {
    Objects.requireNonNull(assets, "assets");
    Objects.requireNonNull(guiNode, "guiNode");
    Objects.requireNonNull(planetDescriptor, "planetDescriptor");

    anchor3d = new Node(SceneGraph.PLANET_ANCHOR_PREFIX + planetDescriptor.body().name());
    container = new Container();
    container.setBackground(null);
    container.setLayout(new BoxLayout(Axis.Y, FillMode.None));

    this.label = new Label(planetDescriptor.displayName());
    container.addChild(label);

    Label dotLabel = container.addChild(new Label(""));
    dotLabel.setTextHAlignment(HAlignment.Center);
    this.icon = new IconComponent("textures/white-dot.png");
    icon.setHAlignment(HAlignment.Center);
    icon.setIconSize(new Vector2f(ICON_SIZE, ICON_SIZE));
    icon.setColor(planetDescriptor.orbitColor());
    Texture tex = icon.getImageTexture();
    tex.setMagFilter(Texture.MagFilter.Bilinear);
    tex.setMinFilter(Texture.MinFilter.Trilinear);
    dotLabel.setIcon(icon);

    // Le marker vit dans le HUD
    guiNode.attachChild(container);
  }

  @Override
  public Spatial spatial() {
    return anchor3d;
  }

  @Override
  public void setPositionWorld(Vector3f worldJmeUnits) {
    anchor3d.setLocalTranslation(worldJmeUnits);
  }

  @Override
  public void setColor(ColorRGBA color) {
    label.setColor(color);
    icon.setColor(color);
    // nameLabel.setBackground(
    //    new QuadBackgroundComponent(new ColorRGBA(color.r, color.g, color.b, NAME_BG_ALPHA)));

    // L'icône est blanche par défaut (white-dot.png). Si tu veux la teinter,
    // on pourra passer par une autre approche (ex: QuadBackground / material).
  }

  @Override
  public void setVisible(boolean visible) {
    this.visible = visible;
    anchor3d.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    container.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
  }

  public void updateScreen(Camera cam) {
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
