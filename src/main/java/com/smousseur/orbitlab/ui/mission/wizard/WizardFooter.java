package com.smousseur.orbitlab.ui.mission.wizard;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.UiKit;

public class WizardFooter {

  private static final float FOOTER_HEIGHT = 72f;
  private static final float BUTTON_HEIGHT = 36f;
  private static final float CANCEL_BTN_W = 120f;
  private static final float PREVIOUS_BTN_W = 120f;
  private static final float NEXT_BTN_W = 140f;
  private static final float BUTTON_GAP = 12f;

  private final Container root;
  private final Button cancelButton;
  private final Button previousButton;
  private final Button nextButton;

  private Runnable onCancel = () -> {};
  private Runnable onPrevious = () -> {};
  private Runnable onNext = () -> {};

  public WizardFooter() {
    root =
        new Container(new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE);
    root.setPreferredSize(new Vector3f(0, FOOTER_HEIGHT, 0));
    root.setBackground(null);

    cancelButton = new Button("x  Cancel", MissionWizardStyles.STYLE);
    cancelButton.setBackground(
        UiKit.gradientBackground(MissionWizardStyles.WIZARD_DANGER));
    cancelButton.setFont(UiKit.rajdhani(14));
    cancelButton.setPreferredSize(new Vector3f(CANCEL_BTN_W, BUTTON_HEIGHT, 0));
    cancelButton.addClickCommands(src -> onCancel.run());

    previousButton = new Button("<  Previous", MissionWizardStyles.STYLE);
    previousButton.setBackground(
        UiKit.gradientBackground(MissionWizardStyles.WIZARD_BG_CARD));
    previousButton.setFont(UiKit.rajdhani(14));
    previousButton.setPreferredSize(new Vector3f(PREVIOUS_BTN_W, BUTTON_HEIGHT, 0));
    previousButton.addClickCommands(src -> onPrevious.run());

    nextButton = new Button("Next  >", MissionWizardStyles.STYLE);
    nextButton.setBackground(
        UiKit.gradientBackground(MissionWizardStyles.WIZARD_ACCENT));
    nextButton.setFont(UiKit.rajdhani(14));
    nextButton.setPreferredSize(new Vector3f(NEXT_BTN_W, BUTTON_HEIGHT, 0));
    nextButton.addClickCommands(src -> onNext.run());
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

    // Leading spacer pushes the button cluster to the right.
    float clusterW = CANCEL_BTN_W + BUTTON_GAP + NEXT_BTN_W;
    if (step.index() > 0) {
      clusterW += PREVIOUS_BTN_W + BUTTON_GAP;
    }
    float leadingW =
        Math.max(0f, MissionWizardStyles.WIZARD_CONTENT_WIDTH - clusterW);
    root.addChild(UiKit.hSpacer(leadingW));

    // Right-aligned button cluster.
    Container cluster =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    cluster.setBackground(null);
    cluster.addChild(cancelButton);
    cluster.addChild(UiKit.hSpacer(BUTTON_GAP));
    if (step.index() > 0) {
      cluster.addChild(previousButton);
      cluster.addChild(UiKit.hSpacer(BUTTON_GAP));
    }
    cluster.addChild(nextButton);

    if (step == MissionWizardStep.LAUNCHER) {
      nextButton.setText("Create");
      nextButton.setBackground(
          UiKit.gradientBackground(MissionWizardStyles.WIZARD_SUCCESS));
    } else {
      nextButton.setText("Next  >");
      nextButton.setBackground(
          UiKit.gradientBackground(MissionWizardStyles.WIZARD_ACCENT));
    }
  }
}
