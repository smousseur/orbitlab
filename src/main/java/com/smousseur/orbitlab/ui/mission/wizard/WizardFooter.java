package com.smousseur.orbitlab.ui.mission.wizard;

import com.smousseur.orbitlab.ui.form.FormStyles;

import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.IconComponent;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.mission.wizard.component.ProgressBar;

public class WizardFooter {

  private static final float FOOTER_HEIGHT = 92f;
  private static final float BUTTON_HEIGHT = 38f;
  private static final float CANCEL_BTN_W = 140f;
  private static final float PREVIOUS_BTN_W = 140f;
  private static final float NEXT_BTN_W = 140f;
  private static final float BUTTON_GAP = 12f;
  private static final float PAD_X = 32f;
  private static final float PAD_Y = 18f;
  private static final float PROGRESS_W = 120f;
  private static final float PROGRESS_H = 4f;

  private final Container root;
  private final Button cancelButton;
  private final Button previousButton;
  private final Button nextButton;
  private final ProgressBar progressBar;
  private final Label progressLabel;

  private String nextBaseTex = "btn-primary";
  private String nextHoverTex = "btn-primary-hover";

  /** Largeur utile du footer (entre ses paddings horizontaux). */
  private final float innerWidth;

  private Runnable onCancel = () -> {};
  private Runnable onPrevious = () -> {};
  private Runnable onNext = () -> {};

  public WizardFooter() {
    this(0f);
  }

  public WizardFooter(float preferredWidth) {
    this.innerWidth = Math.max(0f, preferredWidth - 2 * PAD_X);
    root = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(preferredWidth, FOOTER_HEIGHT, 0));
    root.setInsetsComponent(new InsetsComponent(new Insets3f(PAD_Y, PAD_X, PAD_Y, PAD_X)));

    cancelButton = newButton("  Cancel", CANCEL_BTN_W);
    cancelButton.setIcon(UiKit.wizardIconComponent("icon-close-red"));
    cancelButton.setColor(FormStyles.DANGER);
    attachHoverSkin(cancelButton, "btn-cancel", "btn-cancel-hover");
    cancelButton.addClickCommands(src -> onCancel.run());

    previousButton = newButton("  Previous", PREVIOUS_BTN_W);
    previousButton.setIcon(UiKit.wizardIconComponent("icon-chevron-left-mid"));
    previousButton.setColor(FormStyles.TEXT_SECONDARY);
    attachHoverSkin(previousButton, "btn-ghost", "btn-ghost-hover");
    previousButton.addClickCommands(src -> onPrevious.run());

    nextButton = newButton("Next  ", NEXT_BTN_W);
    IconComponent chevron = UiKit.wizardIconComponent("icon-chevron-right-white");
    chevron.setHAlignment(HAlignment.Right);
    nextButton.setIcon(chevron);
    nextButton.setColor(FormStyles.TEXT_PRIMARY);
    attachHoverSkin(nextButton, () -> nextBaseTex, () -> nextHoverTex);
    nextButton.addClickCommands(src -> onNext.run());
    applyNextSkin();

    progressBar = new ProgressBar(PROGRESS_W, PROGRESS_H);
    progressLabel = new Label("PROGRESS", FormStyles.STYLE);
    progressLabel.setFont(UiKit.ibmPlexMono(11));
    progressLabel.setColor(FormStyles.TEXT_LO);
  }

  public Container getNode() {
    return root;
  }

  public void setOnCancel(Runnable action) {
    this.onCancel = action != null ? action : () -> {};
  }

  public void setOnPrevious(Runnable action) {
    this.onPrevious = action != null ? action : () -> {};
  }

  public void setOnNext(Runnable action) {
    this.onNext = action != null ? action : () -> {};
  }

  public void setStep(MissionWizardStep step) {
    root.clearChildren();

    Container progressCol = new Container(new BoxLayout(Axis.Y, FillMode.None));
    progressCol.setBackground(null);
    progressCol.setPreferredSize(new Vector3f(PROGRESS_W, FOOTER_HEIGHT - 2 * PAD_Y, 0));
    progressCol.addChild(UiKit.vSpacer(PAD_Y));
    progressCol.addChild(progressLabel);
    progressCol.addChild(progressBar.getNode());
    root.addChild(UiKit.hSpacer(10));
    root.addChild(progressCol);

    float clusterW = CANCEL_BTN_W + BUTTON_GAP + NEXT_BTN_W;
    if (step.index() > 0) {
      clusterW += PREVIOUS_BTN_W + BUTTON_GAP;
    }
    float leadingW = Math.max(0f, innerWidth - PROGRESS_W - clusterW);
    root.addChild(UiKit.hSpacer(leadingW));

    Container cluster = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    cluster.setBackground(null);
    cluster.setPreferredSize(new Vector3f(clusterW, BUTTON_HEIGHT, 0));
    cluster.addChild(cancelButton);
    cluster.addChild(UiKit.hSpacer(BUTTON_GAP));
    if (step.index() > 0) {
      cluster.addChild(previousButton);
      cluster.addChild(UiKit.hSpacer(BUTTON_GAP));
    }
    cluster.addChild(nextButton);

    if (step == MissionWizardStep.LAUNCHER) {
      nextButton.setText("  Create");
      IconComponent check = UiKit.wizardIconComponent("icon-check-white");
      check.setHAlignment(HAlignment.Left);
      nextButton.setIcon(check);
      nextBaseTex = "btn-success";
      nextHoverTex = "btn-success";
    } else {
      nextButton.setText("Next  ");
      IconComponent chevron = UiKit.wizardIconComponent("icon-chevron-right-white");
      chevron.setHAlignment(HAlignment.Right);
      nextButton.setIcon(chevron);
      nextBaseTex = "btn-primary";
      nextHoverTex = "btn-primary-hover";
    }
    applyNextSkin();

    progressBar.setProgress((step.index() + 1f) / (float) MissionWizardStep.COUNT);
  }

  private Button newButton(String text, float width) {
    Button btn = new Button(text, FormStyles.STYLE);
    btn.setPreferredSize(new Vector3f(width, BUTTON_HEIGHT, 0));
    btn.setFont(UiKit.sora(13));
    return btn;
  }

  private void applyNextSkin() {
    nextButton.setBackground(UiKit.wizardBg9(nextBaseTex, 8));
  }

  private void attachHoverSkin(Button btn, String baseTex, String hoverTex) {
    attachHoverSkin(btn, () -> baseTex, () -> hoverTex);
  }

  private void attachHoverSkin(
      Button btn,
      java.util.function.Supplier<String> baseSupplier,
      java.util.function.Supplier<String> hoverSupplier) {
    btn.setBackground(UiKit.wizardBg9(baseSupplier.get(), 8));
    MouseEventControl.addListenersToSpatial(
        btn,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent event, Spatial target, Spatial capture) {
            btn.setBackground(UiKit.wizardBg9(hoverSupplier.get(), 8));
          }

          @Override
          public void mouseExited(MouseMotionEvent event, Spatial target, Spatial capture) {
            btn.setBackground(UiKit.wizardBg9(baseSupplier.get(), 8));
          }
        });
  }
}
