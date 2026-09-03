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
    if (isBehindCamera(cam, world)) {
      container.setCullHint(Spatial.CullHint.Always);
      return;
    }
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

  /**
   * Whether a world position sits behind the camera, and must therefore not be projected at all.
   *
   * <p><b>The projected depth cannot answer this, and that is {@code BUG-22}.</b> {@code
   * getScreenCoordinates} divides by a negative {@code w} for a point behind the camera, which
   * mirrors it onto the screen instead of rejecting it; the depth it returns is {@code 1 +
   * 2·near/distance}, so the {@code z > 1} test below only sees it while that excess stays above
   * {@code ulp(1f) = 1.19e-7}. In planet view the near plane drops to its {@code 1e-4} floor as
   * soon as the camera is closer than about 10 300 km to the pivot ({@code updateFrustum}'s
   * keep-pivot- visible clamp), and the excess then rounds back to exactly 1 for anything beyond
   * 22.4 AU: the whole solar system, seen from Pluto, drawn as icons behind the planet the camera
   * is looking at.
   *
   * <p>The sign of the distance along the view axis has no such resolution limit — it is the same
   * answer at every frustum, which is why the test is made here rather than by tightening the
   * comparison on {@code screen.z}.
   *
   * @param cam the camera the icon is projected with
   * @param world the body's world position
   * @return {@code true} when the position is on the camera plane or behind it
   */
  static boolean isBehindCamera(Camera cam, Vector3f world) {
    return cam.getDirection().dot(world.subtract(cam.getLocation())) <= 0f;
  }

  private void addEventListener(Container container, Runnable onClick) {
    container.addMouseListener(
        new DefaultMouseListener() {
          @Override
          public void mouseButtonEvent(MouseButtonEvent event, Spatial target, Spatial capture) {
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
