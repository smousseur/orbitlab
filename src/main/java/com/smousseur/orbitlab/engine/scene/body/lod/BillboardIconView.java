package com.smousseur.orbitlab.engine.scene.body.lod;

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
import com.smousseur.orbitlab.engine.scene.body.BodyRenderConfig;

/**
 * Renders a body as a simple 2D icon with a colored dot and label in the GUI overlay. Used when the
 * camera is too far from the body for the 3D model to be meaningful.
 *
 * <p>The icon tracks the body's 3D position by projecting it to screen coordinates and supports
 * mouse interaction: clicking triggers the optional onClick handler, hovering highlights the icon.
 */
public class BillboardIconView {
  private static final float ICON_SIZE = 16f;

  private final Container container;
  private boolean visible = true;

  private final IconComponent dotIcon;
  private final ColorRGBA dotIconColor;

  /**
   * Creates a new billboard icon view and attaches it to the GUI node.
   *
   * @param guiNode the GUI node to attach the icon container to
   * @param config the render configuration defining display name and color
   * @param onClick optional click handler; if null, no click listener is added
   */
  public BillboardIconView(Node guiNode, BodyRenderConfig config, Runnable onClick) {
    container = new Container();
    container.setBackground(null);
    container.setLayout(new BoxLayout(Axis.Y, FillMode.None));
    dotIconColor = config.color();

    Label label = new Label(config.displayName());
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
    if (onClick != null) {
      addEventListener(container, onClick);
    }
  }

  /**
   * Sets the visibility of the icon.
   *
   * @param visible {@code true} to show the icon, {@code false} to hide it
   */
  public void setVisible(boolean visible) {
    this.visible = visible;
    container.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
  }

  /**
   * Updates the icon's screen position by projecting the body's 3D world position to screen
   * coordinates. Hides the icon if the body is behind the camera.
   *
   * @param cam the active camera used for projection
   * @param anchor3d the body's anchor node providing the world position
   */
  public void updateScreenPosition(Camera cam, Node anchor3d) {
    if (!visible) {
      return;
    }
    Vector3f world = anchor3d.getWorldTranslation();
    Vector3f screen = cam.getScreenCoordinates(world);
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

  private void addEventListener(Container container, Runnable onClick) {
    container.addMouseListener(
        new DefaultMouseListener() {
          @Override
          public void mouseButtonEvent(
              MouseButtonEvent event, Spatial target, Spatial capture) {
            if (event.isPressed() && event.getButtonIndex() == MouseInput.BUTTON_LEFT) {
              onClick.run();
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

  /** Detaches the icon container from the GUI node. */
  public void detach() {
    container.removeFromParent();
  }

  private ColorRGBA saturate(ColorRGBA c, float s) {
    float gray = (c.r + c.g + c.b) / 3f;
    return new ColorRGBA(
        gray + (c.r - gray) * s, gray + (c.g - gray) * s, gray + (c.b - gray) * s, c.a);
  }
}
