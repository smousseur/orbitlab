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
import com.smousseur.orbitlab.engine.HoverConfig;
import com.smousseur.orbitlab.engine.scene.body.BodyRenderConfig;
import java.util.function.Consumer;

/**
 * Renders a body as a simple 2D icon with a colored dot and label in the GUI overlay. Used when the
 * camera is too far from the body for the 3D model to be meaningful.
 *
 * <p>The icon tracks the body's 3D position by projecting it to screen coordinates and supports
 * mouse interaction: a left press triggers the optional onClick handler, and the cursor entering
 * and leaving the icon is reported to the optional hover handler. How lit or dimmed the icon looks
 * is not its own decision: it shows the hover intensity and the dimming it is given, see {@link
 * #setHoverIntensity} and {@link #setHoverDim}.
 *
 * <p>A hidden icon is neither hoverable nor clickable, although Lemur still picks it — see {@link
 * IconHoverGuard}.
 */
public class BillboardIconView {

  private final Container container;
  private boolean visible = true;

  private final Label label;
  private final Label labelIcon;
  private final IconComponent dotIcon;
  private final ColorRGBA dotIconColor;
  private final HoverConfig hoverConfig;
  private final IconHoverGuard hoverGuard;
  private float hoverIntensity;
  private float hoverDim = 1f;

  /**
   * Creates a new billboard icon view and attaches it to the GUI node.
   *
   * @param guiNode the GUI node to attach the icon container to
   * @param config the render configuration defining display name and color
   * @param hoverConfig the hover tuning: the icon's sizes and how it brightens
   * @param onClick optional click handler, run on a left press; may be null
   * @param onHover optional hover handler, told {@code true} when the cursor enters the icon and
   *     {@code false} when it leaves it or the icon is hidden under it; may be null
   */
  public BillboardIconView(
      Node guiNode,
      BodyRenderConfig config,
      HoverConfig hoverConfig,
      Runnable onClick,
      Consumer<Boolean> onHover) {
    this.hoverConfig = hoverConfig;
    container = new Container();
    container.setBackground(null);
    container.setLayout(new BoxLayout(Axis.Y, FillMode.None));
    dotIconColor = config.color();

    label = new Label(config.displayName());
    label.setColor(dotIconColor);
    container.addChild(label);

    labelIcon = container.addChild(new Label(""));
    labelIcon.setTextHAlignment(HAlignment.Center);
    dotIcon = new IconComponent("textures/white-dot.png");
    dotIcon.setHAlignment(HAlignment.Center);
    float restPx = hoverConfig.iconRestPx();
    dotIcon.setIconSize(new Vector2f(restPx, restPx));
    dotIcon.setColor(dotIconColor);

    Texture tex = dotIcon.getImageTexture();
    tex.setMagFilter(Texture.MagFilter.Bilinear);
    tex.setMinFilter(Texture.MinFilter.Trilinear);
    labelIcon.setIcon(dotIcon);

    guiNode.attachChild(container);
    hoverGuard = new IconHoverGuard(container, onHover);
    if (onClick != null || onHover != null) {
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
    show(visible);
  }

  /**
   * Shows how strongly the icon is hovered: it grows from its rest size and its ring and name blend
   * toward white, as {@link IconHoverLook} computes. The icon is only touched when the intensity
   * changes, so a resting icon costs nothing.
   *
   * @param intensity the hover intensity, 0 at rest and 1 fully lit
   */
  public void setHoverIntensity(float intensity) {
    if (intensity == hoverIntensity) {
      return;
    }
    hoverIntensity = intensity;
    IconHoverLook look = look();
    // Lemur 1.16.0's IconComponent builds a new quad on every resize and attaches it without
    // detaching the previous one: resized in place, each frame of a fade would leave a ring drawn
    // behind. Detached from its label first, the icon takes its current quad away with it, and
    // only the new one is attached back.
    labelIcon.setIcon(null);
    dotIcon.setIconSize(new Vector2f(look.iconPx(), look.iconPx()));
    labelIcon.setIcon(dotIcon);
    recolor(look);
  }

  /**
   * Shows how far the icon fades back while another body is lit: its ring and its name fade
   * together, keeping their colour and size, as {@link IconHoverLook} computes it. The icon is only
   * recoloured when the dimming changes, and never resized.
   *
   * @param dim the opacity factor, 1 for an icon that is not dimmed
   */
  public void setHoverDim(float dim) {
    if (dim == hoverDim) {
      return;
    }
    hoverDim = dim;
    recolor(look());
  }

  private IconHoverLook look() {
    return IconHoverLook.of(hoverIntensity, hoverDim, dotIconColor, hoverConfig);
  }

  private void recolor(IconHoverLook look) {
    dotIcon.setColor(look.ring());
    label.setColor(look.label());
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
      show(false);
      return;
    }
    Vector3f screen = cam.getScreenCoordinates(world);
    if (screen.z < 0f || screen.z > 1f) {
      show(false);
      return;
    }
    show(true);
    Vector3f size = container.getPreferredSize();
    float x = screen.x - (size.x * 0.5f);
    // The rest size, not the current one: as the icon grows, the dot stays in place and the name
    // above it rises by half the growth.
    float y = screen.y + (hoverConfig.iconRestPx() + size.y) * 0.5f;

    container.setLocalTranslation(x, y, 0f);
  }

  private void show(boolean shown) {
    container.setCullHint(shown ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    hoverGuard.setShown(shown);
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
            if (onClick != null
                && event.isPressed()
                && event.getButtonIndex() == MouseInput.BUTTON_LEFT) {
              onClick.run();
            }
          }

          @Override
          public void mouseEntered(MouseMotionEvent event, Spatial target, Spatial capture) {
            hoverGuard.entered();
          }

          @Override
          public void mouseExited(MouseMotionEvent event, Spatial target, Spatial capture) {
            hoverGuard.exited();
          }
        });
  }

  /** Detaches the icon container from the GUI node. */
  public void detach() {
    container.removeFromParent();
  }
}
