package com.smousseur.orbitlab.ui.boot;

import com.jme3.font.BitmapFont;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
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
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.app.dataset.DatasetInstallOutcome;
import com.smousseur.orbitlab.app.dataset.DatasetProgress.Snapshot;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.UiLayers;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.ProgressBar;

/**
 * The card shown at start-up while the dataset is checked or downloaded, alone on a black window:
 * nothing else is attached before the dataset is ready.
 *
 * <p>Two faces at the same place and size. The progress face carries the phase, the current file,
 * one bar per phase, the amounts and the rate, and a Cancel button; the error face carries the
 * cause, the technical detail, and Retry and Quit. The card only displays and reports clicks: what
 * a click does is the caller's.
 *
 * <p>Sizes are in pixels for the fixed 1280 x 720 window, the card centred in it. Every text comes
 * from {@link DatasetProgressText}.
 */
public final class DatasetBootScreen {

  private static final float WIDTH = 560f;
  private static final float HEIGHT = 236f;
  private static final float PAD = 32f;
  private static final float INNER_WIDTH = WIDTH - 2 * PAD;

  private static final float BRAND_HEIGHT = 18f;
  private static final float TITLE_HEIGHT = 21f;
  private static final float LINE_HEIGHT = 18f;
  private static final float SMALL_LINE_HEIGHT = 15f;
  private static final float BAR_HEIGHT = 8f;
  private static final float PERCENT_WIDTH = 60f;
  private static final float AMOUNT_WIDTH = 180f;
  private static final float BUTTON_WIDTH = 120f;
  private static final float BUTTON_HEIGHT = 34f;
  private static final float BUTTON_GAP = 12f;

  /**
   * Characters per line of the error face's message and detail: the widths of {@code ibmplexmono}
   * 13 and 11 are 8 and 7 pixels a glyph, which puts 62 and 70 in the inner width; one is kept
   * spare on the message.
   */
  private static final int MESSAGE_COLUMNS = 61;

  private static final int DETAIL_COLUMNS = 70;
  private static final int MAX_LINES = 2;

  private static final String CANCEL = "Cancel";

  private final Container root;
  private final Container progressFace;
  private final Container failureFace;

  private final Label title;
  private final Label fileLine;
  private final Label percent;
  private final ProgressBar bar;
  private final Label amount;
  private final Label speed;
  private final Button cancelButton;

  private final Label failureTitle;
  private final Label failureMessage;
  private final Label failureDetail;

  private Runnable onCancel = () -> {};
  private Runnable onRetry = () -> {};
  private Runnable onQuit = () -> {};

  /** Builds the card on its progress face, before any phase. */
  public DatasetBootScreen() {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(WIDTH, HEIGHT, 0));
    // The padding is the 9-slice's margin: the skin is drawn over the whole box and the children
    // inside the margin, whereas insets would shrink the skin itself.
    TbtQuadBackgroundComponent shell = FormStyles.shellBg();
    shell.setMargin(PAD, PAD);
    root.setBackground(shell);
    root.setBorder(null);

    progressFace = column();
    progressFace.addChild(brandRow());
    progressFace.addChild(UiKit.vSpacer(12));
    title = progressFace.addChild(label(UiKit.orbitron(16), FormStyles.TEXT_PRIMARY));
    title.setPreferredSize(new Vector3f(INNER_WIDTH, TITLE_HEIGHT, 0));
    progressFace.addChild(UiKit.vSpacer(14));

    Container fileRow = progressFace.addChild(row(LINE_HEIGHT));
    fileLine = fileRow.addChild(label(UiKit.ibmPlexMono(13), FormStyles.TEXT_PRIMARY));
    fileLine.setPreferredSize(new Vector3f(INNER_WIDTH - PERCENT_WIDTH, LINE_HEIGHT, 0));
    percent = fileRow.addChild(label(UiKit.ibmPlexMono(13), FormStyles.ACCENT_BRIGHT));
    percent.setPreferredSize(new Vector3f(PERCENT_WIDTH, LINE_HEIGHT, 0));
    percent.setTextHAlignment(HAlignment.Right);
    progressFace.addChild(UiKit.vSpacer(6));

    bar = new ProgressBar(INNER_WIDTH, BAR_HEIGHT);
    progressFace.addChild(bar.getNode());
    progressFace.addChild(UiKit.vSpacer(6));

    Container numbersRow = progressFace.addChild(row(LINE_HEIGHT));
    amount = numbersRow.addChild(label(UiKit.ibmPlexMono(13), FormStyles.TEXT_SECONDARY));
    amount.setPreferredSize(new Vector3f(AMOUNT_WIDTH, LINE_HEIGHT, 0));
    speed = numbersRow.addChild(label(UiKit.ibmPlexMono(13), FormStyles.TEXT_SECONDARY));
    speed.setPreferredSize(new Vector3f(INNER_WIDTH - AMOUNT_WIDTH, LINE_HEIGHT, 0));
    speed.setTextHAlignment(HAlignment.Right);
    progressFace.addChild(UiKit.vSpacer(17));

    cancelButton = button(CANCEL, FormStyles.DANGER, "btn-cancel", "btn-cancel-hover");
    cancelButton.addClickCommands(src -> onCancel.run());
    progressFace.addChild(buttonRow(cancelButton));

    failureFace = column();
    failureFace.addChild(brandRow());
    failureFace.addChild(UiKit.vSpacer(10));
    failureTitle = failureFace.addChild(label(UiKit.orbitron(16), FormStyles.DANGER));
    failureTitle.setPreferredSize(new Vector3f(INNER_WIDTH, TITLE_HEIGHT, 0));
    failureFace.addChild(UiKit.vSpacer(8));
    failureMessage = failureFace.addChild(label(UiKit.ibmPlexMono(13), FormStyles.TEXT_PRIMARY));
    failureMessage.setPreferredSize(new Vector3f(INNER_WIDTH, MAX_LINES * LINE_HEIGHT, 0));
    failureFace.addChild(UiKit.vSpacer(6));
    failureDetail = failureFace.addChild(label(UiKit.ibmPlexMono(11), FormStyles.TEXT_LO));
    failureDetail.setPreferredSize(new Vector3f(INNER_WIDTH, MAX_LINES * SMALL_LINE_HEIGHT, 0));
    failureFace.addChild(UiKit.vSpacer(9));

    Button quitButton = button("Quit", FormStyles.TEXT_SECONDARY, "btn-ghost", "btn-ghost-hover");
    quitButton.addClickCommands(src -> onQuit.run());
    Button retryButton =
        button("Retry", FormStyles.TEXT_PRIMARY, "btn-primary", "btn-primary-hover");
    retryButton.addClickCommands(src -> onRetry.run());
    failureFace.addChild(buttonRow(quitButton, retryButton));

    root.addChild(progressFace);
  }

  /**
   * Attaches the card.
   *
   * @param parent the GUI node to draw it in
   */
  public void attachTo(Node parent) {
    parent.attachChild(root);
  }

  /** Detaches the card; nothing happens when it is not attached. */
  public void detach() {
    root.removeFromParent();
  }

  /**
   * Centres the card in the window.
   *
   * @param screenWidth the window's width
   * @param screenHeight the window's height
   */
  public void center(int screenWidth, int screenHeight) {
    float x = Math.round((screenWidth - WIDTH) / 2f);
    float y = Math.round((screenHeight + HEIGHT) / 2f);
    root.setLocalTranslation(x, y, UiLayers.MODAL);
  }

  /**
   * Shows where the installation stands, with its rate and time left.
   *
   * @param snapshot the installation's state
   */
  public void showProgress(Snapshot snapshot) {
    showSnapshot(snapshot, DatasetProgressText.speedLine(snapshot), FormStyles.TEXT_SECONDARY);
  }

  /**
   * Shows where the installation stands while it waits for another attempt, the speed line replaced
   * by the reason and the countdown.
   *
   * @param snapshot the installation's state
   * @param retryLine the line from {@link DatasetProgressText#retryLine}
   */
  public void showRetry(Snapshot snapshot, String retryLine) {
    showSnapshot(snapshot, retryLine, AppStyles.TL_AMBER);
  }

  /** Disables Cancel once it is clicked, until the installation stops. */
  public void showCancelling() {
    cancelButton.setEnabled(false);
    cancelButton.setText("Cancelling...");
    cancelButton.setColor(FormStyles.TEXT_LO);
  }

  /**
   * Turns the card to its error face.
   *
   * @param snapshot the installation's state when it ended
   * @param failed how it ended
   */
  public void showFailure(Snapshot snapshot, DatasetInstallOutcome.Failed failed) {
    failureTitle.setText(DatasetProgressText.failureTitle(snapshot, failed));
    failureMessage.setText(
        String.join(
            "\n",
            DatasetProgressText.wrap(
                DatasetProgressText.failureMessage(failed.cause()), MESSAGE_COLUMNS, MAX_LINES)));
    failureDetail.setText(
        String.join(
            "\n",
            DatasetProgressText.wrap(
                DatasetProgressText.failureDetail(failed), DETAIL_COLUMNS, MAX_LINES)));
    face(failureFace);
  }

  /**
   * Sets what clicking Cancel does.
   *
   * @param action the action, or {@code null} for none
   */
  public void setOnCancel(Runnable action) {
    this.onCancel = action != null ? action : () -> {};
  }

  /**
   * Sets what clicking Retry does.
   *
   * @param action the action, or {@code null} for none
   */
  public void setOnRetry(Runnable action) {
    this.onRetry = action != null ? action : () -> {};
  }

  /**
   * Sets what clicking Quit does.
   *
   * @param action the action, or {@code null} for none
   */
  public void setOnQuit(Runnable action) {
    this.onQuit = action != null ? action : () -> {};
  }

  private void showSnapshot(Snapshot snapshot, String speedText, ColorRGBA speedColor) {
    if (face(progressFace)) {
      cancelButton.setEnabled(true);
      cancelButton.setText(CANCEL);
      cancelButton.setColor(FormStyles.DANGER);
    }
    setText(title, DatasetProgressText.title(snapshot));
    setText(fileLine, DatasetProgressText.fileLine(snapshot));
    setText(percent, DatasetProgressText.percent(snapshot));
    bar.setProgress((float) snapshot.phaseFraction());
    setText(amount, DatasetProgressText.amountLine(snapshot));
    setText(speed, speedText);
    if (!speedColor.equals(speed.getColor())) {
      speed.setColor(speedColor);
    }
  }

  /**
   * Puts a face on the card.
   *
   * @return true when the card wore the other face
   */
  private boolean face(Container face) {
    if (face.getParent() == root) {
      return false;
    }
    root.clearChildren();
    root.addChild(face);
    return true;
  }

  /** Sets a label's text only when it changes, the label laying itself out again on every set. */
  private static void setText(Label label, String text) {
    if (!text.equals(label.getText())) {
      label.setText(text);
    }
  }

  private static Container column() {
    Container column = new Container(new BoxLayout(Axis.Y, FillMode.None));
    column.setBackground(null);
    return column;
  }

  private static Container row(float height) {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    row.setPreferredSize(new Vector3f(INNER_WIDTH, height, 0));
    return row;
  }

  /** The wizard's brand line, its subtitle naming this screen. */
  private static Container brandRow() {
    Container brand = row(BRAND_HEIGHT);
    brand.addChild(UiKit.wizardIcon("icon-brand-globe", BRAND_HEIGHT, BRAND_HEIGHT));
    brand.addChild(UiKit.hSpacer(8));
    brand.addChild(label("ORBITLAB", UiKit.orbitron(13), FormStyles.ACCENT_BRIGHT));
    brand.addChild(label("  /  ", UiKit.ibmPlexMono(11), FormStyles.TEXT_LO));
    brand.addChild(label("DATA SETUP", UiKit.ibmPlexMono(11), FormStyles.TEXT_LO));
    return brand;
  }

  /** Buttons aligned on the card's right edge, in the order given. */
  private static Container buttonRow(Button... buttons) {
    Container buttonRow = row(BUTTON_HEIGHT);
    float cluster = buttons.length * BUTTON_WIDTH + (buttons.length - 1) * BUTTON_GAP;
    buttonRow.addChild(UiKit.hSpacer(INNER_WIDTH - cluster));
    for (int i = 0; i < buttons.length; i++) {
      if (i > 0) {
        buttonRow.addChild(UiKit.hSpacer(BUTTON_GAP));
      }
      buttonRow.addChild(buttons[i]);
    }
    return buttonRow;
  }

  private static Label label(BitmapFont font, ColorRGBA color) {
    return label("", font, color);
  }

  private static Label label(String text, BitmapFont font, ColorRGBA color) {
    Label label = new Label(text, FormStyles.STYLE);
    label.setFont(font);
    label.setColor(color);
    label.setTextVAlignment(VAlignment.Top);
    return label;
  }

  private static Button button(String text, ColorRGBA color, String skin, String hoverSkin) {
    Button button = new Button(text, FormStyles.STYLE);
    button.setInsetsComponent(new InsetsComponent(new Insets3f(0, 0, 0, 0)));
    button.setPreferredSize(new Vector3f(BUTTON_WIDTH, BUTTON_HEIGHT, 0));
    button.setFont(UiKit.sora(13));
    button.setColor(color);
    button.setTextHAlignment(HAlignment.Center);
    button.setTextVAlignment(VAlignment.Center);
    button.setBackground(UiKit.wizardBg9(skin, 8));
    MouseEventControl.addListenersToSpatial(
        button,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent event, Spatial target, Spatial capture) {
            button.setBackground(UiKit.wizardBg9(hoverSkin, 8));
          }

          @Override
          public void mouseExited(MouseMotionEvent event, Spatial target, Spatial capture) {
            button.setBackground(UiKit.wizardBg9(skin, 8));
          }
        });
    return button;
  }
}
