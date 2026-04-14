package com.smousseur.orbitlab.ui.mission.wizard;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;

public class WizardFooter {

  private static final float FOOTER_HEIGHT = 72f;
  private static final float BUTTON_HEIGHT = 36f;

  private final Container root;
  private final Container buttonRow;
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

    buttonRow =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    buttonRow.setBackground(null);

    cancelButton = new Button("x  Cancel", MissionWizardStyles.STYLE);
    cancelButton.setBackground(
        MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_DANGER));
    cancelButton.setFont(MissionWizardStyles.rajdhani(14));
    cancelButton.setPreferredSize(new Vector3f(0, BUTTON_HEIGHT, 0));
    cancelButton.addClickCommands(src -> onCancel.run());

    previousButton = new Button("<  Previous", MissionWizardStyles.STYLE);
    previousButton.setBackground(
        MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_BG_CARD));
    previousButton.setFont(MissionWizardStyles.rajdhani(14));
    previousButton.setPreferredSize(new Vector3f(0, BUTTON_HEIGHT, 0));
    previousButton.addClickCommands(src -> onPrevious.run());

    nextButton = new Button("Next  >", MissionWizardStyles.STYLE);
    nextButton.setBackground(
        MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_ACCENT));
    nextButton.setFont(MissionWizardStyles.rajdhani(14));
    nextButton.setPreferredSize(new Vector3f(0, BUTTON_HEIGHT, 0));
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
    buttonRow.clearChildren();
    buttonRow.addChild(cancelButton);
    if (step.index() > 0) {
      buttonRow.addChild(previousButton);
    }
    buttonRow.addChild(nextButton);

    if (step == MissionWizardStep.LAUNCHER) {
      nextButton.setText("v  Create mission");
      nextButton.setBackground(
          MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_SUCCESS));
    } else {
      nextButton.setText("Next  >");
      nextButton.setBackground(
          MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_ACCENT));
    }
  }
}
