package com.smousseur.orbitlab.engine.scene.planet.lod;

import com.jme3.input.MouseInput;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
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
import com.simsilica.lemur.event.DefaultMouseListener;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.planet.PlanetDescriptor;

public class PlanetIconView {
  private static final float ICON_SIZE = 16f;

  private final Container container;
  private final FocusView focusView;
  private final SolarSystemBody body;
  private boolean visible = true;

  private final IconComponent dotIcon;
  private final ColorRGBA dotIconColor;

  public PlanetIconView(Node guiNode, FocusView focusView, PlanetDescriptor planetDescriptor) {
    container = new Container();
    container.setBackground(null);
    container.setLayout(new BoxLayout(Axis.Y, FillMode.None));
    this.focusView = focusView;
    body = planetDescriptor.body();
    dotIconColor = planetDescriptor.orbitColor();

    Label label = new Label(planetDescriptor.displayName());
    label.setColor(dotIconColor);
    container.addChild(label);

    Label labelIcon = container.addChild(new Label(""));
    labelIcon.setTextHAlignment(HAlignment.Center);
    dotIcon = new IconComponent("textures/white-dot.png");
    dotIcon.setHAlignment(HAlignment.Center);
    dotIcon.setIconSize(new Vector2f(ICON_SIZE, ICON_SIZE));
    dotIcon.setColor(dotIconColor);

    Texture tex = dotIcon.getImageTexture();
    tex.setMagFilter(Texture.MagFilter.Bilinear);
    tex.setMinFilter(Texture.MinFilter.Trilinear);
    labelIcon.setIcon(dotIcon);

    guiNode.attachChild(container);
    addEventListener(container);
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

  private void addEventListener(Container container) {
    container.addMouseListener(
        new DefaultMouseListener() {
          @Override
          public void mouseButtonEvent(MouseButtonEvent event, Spatial target, Spatial capture) {
            if (event.isPressed() && event.getButtonIndex() == MouseInput.BUTTON_LEFT) {
              focusView.viewPlanet(body);
            }
          }

          @Override
          public void mouseEntered(MouseMotionEvent event, Spatial target, Spatial capture) {
            dotIcon.setColor(saturate(dotIconColor, 3f));
          }

          @Override
          public void mouseExited(MouseMotionEvent event, Spatial target, Spatial capture) {
            dotIcon.setColor(dotIconColor);
          }
        });
  }

  public void detach() {
    container.removeFromParent();
  }

  private ColorRGBA saturate(ColorRGBA c, float s) {
    float gray = (c.r + c.g + c.b) / 3f;
    return new ColorRGBA(
        gray + (c.r - gray) * s, gray + (c.g - gray) * s, gray + (c.b - gray) * s, c.a);
  }
}
