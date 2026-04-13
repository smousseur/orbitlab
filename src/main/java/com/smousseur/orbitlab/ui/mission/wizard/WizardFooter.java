package com.smousseur.orbitlab.ui.mission.wizard;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.mission.wizard.component.ProgressBar;

public class WizardFooter {

  private static final float FOOTER_HEIGHT = 72f;
  private static final float BUTTON_HEIGHT = 36f;

  private final Container root;
  private final ProgressBar progressBar;
  private final Button cancelButton;
  private final Button previousButton;
  private final Button nextButton;

  private Runnable onCancel = () -> {};

  public WizardFooter() {
    root =
        new Container(new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE);
    root.setPreferredSize(new Vector3f(0, FOOTER_HEIGHT, 0));
    root.setBackground(null);

    // Left — progress
    Container leftCol =
        root.addChild(new Container(new BoxLayout(Axis.Y, FillMode.None)));
    leftCol.setBackground(null);
    Label progressLabel =
        leftCol.addChild(new Label("PROGRESSION", MissionWizardStyles.STYLE));
    progressLabel.setFont(MissionWizardStyles.rajdhani(10));
    progressLabel.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
    progressBar = new ProgressBar(200f, 4f);
    leftCol.addChild(progressBar.getNode());

    // Spacer
    Container spacer = root.addChild(new Container());
    spacer.setBackground(null);
    spacer.setPreferredSize(new Vector3f(200, 0, 0));

    // Right — buttons
    Container buttonRow =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    buttonRow.setBackground(null);

    cancelButton =
        buttonRow.addChild(new Button("x  Cancel", MissionWizardStyles.STYLE));
    cancelButton.setBackground(
        MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_DANGER));
    cancelButton.setFont(MissionWizardStyles.rajdhani(14));
    cancelButton.setPreferredSize(new Vector3f(0, BUTTON_HEIGHT, 0));
    cancelButton.addClickCommands(src -> onCancel.run());

    previousButton =
        buttonRow.addChild(new Button("<  Previous", MissionWizardStyles.STYLE));
    previousButton.setBackground(
        MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_BG_CARD));
    previousButton.setFont(MissionWizardStyles.rajdhani(14));
    previousButton.setPreferredSize(new Vector3f(0, BUTTON_HEIGHT, 0));

    nextButton =
        buttonRow.addChild(new Button("Next  >", MissionWizardStyles.STYLE));
    nextButton.setBackground(
        MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_ACCENT));
    nextButton.setFont(MissionWizardStyles.rajdhani(14));
    nextButton.setPreferredSize(new Vector3f(0, BUTTON_HEIGHT, 0));
  }

  public Container getNode() {
    return root;
  }

  public void setOnCancel(Runnable action) {
    this.onCancel = action;
  }

  public void setStep(MissionWizardStep step) {
    progressBar.setProgress((step.index() + 1) / (float) MissionWizardStep.COUNT);

    previousButton.setColor(
        step.index() == 0
            ? MissionWizardStyles.WIZARD_TEXT_SECONDARY
            : MissionWizardStyles.WIZARD_TEXT_PRIMARY);

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
