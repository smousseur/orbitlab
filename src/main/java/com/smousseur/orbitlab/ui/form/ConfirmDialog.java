package com.smousseur.orbitlab.ui.form;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.UiLayers;
import java.util.Objects;

/**
 * Minimal confirmation modal: a single message line and a Cancel / OK button pair. It has no close
 * button — the ways out are the two buttons and ESC, which is the keyboard equivalent of Cancel, so
 * the caller always learns the outcome.
 *
 * <p>Its own {@link ModalBackdrop} sits above whatever modal opened it (see {@link
 * UiLayers#DIALOG_BACKDROP}) and swallows every mouse event, which is what makes the dialog modal
 * against an already-modal panel: the widget underneath keeps rendering but cannot be clicked.
 * Clicking the backdrop still does nothing — dismissing with the mouse has to be an explicit
 * Cancel.
 *
 * <p>The dialog does not touch the simulation clock: opening one leaves time running exactly as it
 * was.
 */
public class ConfirmDialog implements AutoCloseable {

  private static final float WIDTH = 380f;
  private static final float HEIGHT = 150f;
  private static final float PAD_X = 24f;
  private static final float PAD_Y = 24f;
  private static final float MESSAGE_HEIGHT = 40f;
  private static final float BUTTON_WIDTH = 130f;
  private static final float BUTTON_HEIGHT = 38f;
  private static final float BUTTON_GAP = 12f;

  private final ModalBackdrop backdrop;
  private final Container root;

  private Runnable onConfirm = () -> {};
  private Runnable onCancel = () -> {};
  private boolean visible = false;

  /**
   * @param message the single line shown above the buttons
   */
  public ConfirmDialog(String message) {
    Objects.requireNonNull(message, "message");

    // No click callback: the backdrop only shields, it never dismisses.
    backdrop = new ModalBackdrop(UiLayers.DIALOG_BACKDROP);

    root = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(WIDTH, HEIGHT, 0));
    root.setBackground(FormStyles.shellBg());
    root.setBorder(null);
    root.setInsetsComponent(new InsetsComponent(new Insets3f(PAD_Y, PAD_X, PAD_Y, PAD_X)));
    FormStyles.clearMargin(root.getBackground());

    float innerWidth = WIDTH - 2 * PAD_X;

    Label messageLabel = root.addChild(new Label(message, FormStyles.STYLE));
    messageLabel.setPreferredSize(new Vector3f(innerWidth, MESSAGE_HEIGHT, 0));
    messageLabel.setFont(UiKit.sora(15));
    messageLabel.setColor(FormStyles.TEXT_PRIMARY);
    messageLabel.setTextHAlignment(HAlignment.Center);
    messageLabel.setTextVAlignment(VAlignment.Center);

    Container buttons = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    buttons.setBackground(null);
    buttons.setPreferredSize(new Vector3f(innerWidth, BUTTON_HEIGHT, 0));

    float clusterWidth = 2 * BUTTON_WIDTH + BUTTON_GAP;
    buttons.addChild(UiKit.hSpacer(Math.max(0f, (innerWidth - clusterWidth) / 2f)));

    Button cancelButton = buttons.addChild(newButton("Cancel", FormStyles.TEXT_SECONDARY));
    attachHoverSkin(cancelButton, "btn-delete", "btn-delete-hover");
    cancelButton.addClickCommands(src -> onCancel.run());

    buttons.addChild(UiKit.hSpacer(BUTTON_GAP));

    // The confirming button carries the destructive skin: OK is the irreversible half of the pair.
    Button okButton = buttons.addChild(newButton("OK", FormStyles.TEXT_PRIMARY));
    attachHoverSkin(okButton, "btn-ghost", "btn-ghost-hover");
    okButton.addClickCommands(src -> onConfirm.run());

    // Mouse on the dialog shell must not leak through to the backdrop.
    MouseEventControl.addListenersToSpatial(
        root,
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            event.setConsumed();
          }
        });
  }

  public void attachTo(Node modalNode) {
    modalNode.attachChild(backdrop.getNode());
    modalNode.attachChild(root);
    visible = true;
  }

  @Override
  public void close() {
    backdrop.getNode().removeFromParent();
    root.removeFromParent();
    visible = false;
  }

  public boolean isVisible() {
    return visible;
  }

  public void update(Camera cam) {
    if (!visible) return;
    backdrop.update(cam);
    centerOnScreen(cam.getWidth(), cam.getHeight());
  }

  /** Action run when the user clicks OK. The caller is responsible for closing the dialog. */
  public void setOnConfirm(Runnable action) {
    this.onConfirm = action != null ? action : () -> {};
  }

  /** Action run when the user clicks Cancel. The caller is responsible for closing the dialog. */
  public void setOnCancel(Runnable action) {
    this.onCancel = action != null ? action : () -> {};
  }

  private void centerOnScreen(int screenWidth, int screenHeight) {
    float x = Math.round((screenWidth - WIDTH) / 2f);
    float y = Math.round((screenHeight + HEIGHT) / 2f);
    root.setLocalTranslation(x, y, UiLayers.DIALOG);
  }

  private static Button newButton(String text, ColorRGBA color) {
    Button button = new Button(text, FormStyles.STYLE);
    button.setInsetsComponent(new InsetsComponent(new Insets3f(8, 0, 0, 0)));

    button.setPreferredSize(new Vector3f(BUTTON_WIDTH, BUTTON_HEIGHT, 0));
    button.setFont(UiKit.sora(13));
    button.setColor(color);
    button.setTextHAlignment(HAlignment.Center);
    button.setTextVAlignment(VAlignment.Center);
    return button;
  }

  private static void attachHoverSkin(Button button, String baseTexture, String hoverTexture) {
    button.setBackground(UiKit.wizardBg9(baseTexture, 8));
    MouseEventControl.addListenersToSpatial(
        button,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent event, Spatial target, Spatial capture) {
            button.setBackground(UiKit.wizardBg9(hoverTexture, 8));
          }

          @Override
          public void mouseExited(MouseMotionEvent event, Spatial target, Spatial capture) {
            button.setBackground(UiKit.wizardBg9(baseTexture, 8));
          }
        });
  }
}
