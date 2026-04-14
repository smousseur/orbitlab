package com.smousseur.orbitlab.ui.mission.wizard;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import java.util.function.Consumer;

public class WizardStepper {

  private static final float STEPPER_HEIGHT = 44f;
  private static final float CIRCLE_SIZE = 28f;
  private static final float DASH_WIDTH = 6f;
  private static final float DASH_HEIGHT = 1f;
  private static final float DASH_GAP = 4f;
  private static final int DASH_COUNT = 3;

  private final Container root;
  private final Container[] stepNodes = new Container[MissionWizardStep.COUNT];
  private Consumer<MissionWizardStep> onStepClicked = step -> {};

  public WizardStepper() {
    root =
        new Container(new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE);
    root.setPreferredSize(new Vector3f(0, STEPPER_HEIGHT, 0));
    root.setBackground(null);

    for (int i = 0; i < MissionWizardStep.COUNT; i++) {
      if (i > 0) root.addChild(buildDashSeparator());
      MissionWizardStep step = MissionWizardStep.values()[i];
      Container node = buildStepNode(step);
      stepNodes[i] = node;
      root.addChild(node);
    }
  }

  public Container getNode() {
    return root;
  }

  public void setOnStepClicked(Consumer<MissionWizardStep> listener) {
    this.onStepClicked = listener != null ? listener : step -> {};
  }

  public void setActiveStep(MissionWizardStep activeStep) {
    for (MissionWizardStep step : MissionWizardStep.values()) {
      Container node = stepNodes[step.index()];
      if (step.index() < activeStep.index()) {
        applyDoneState(node, step);
      } else if (step == activeStep) {
        applyActiveState(node, step);
      } else {
        applyPendingState(node, step);
      }
    }
  }

  private Container buildStepNode(MissionWizardStep step) {
    Container col = new Container(new BoxLayout(Axis.Y, FillMode.None));
    col.setBackground(null);

    Container circle = new Container();
    circle.setPreferredSize(new Vector3f(CIRCLE_SIZE, CIRCLE_SIZE, 0));
    Label number =
        circle.addChild(
            new Label(String.valueOf(step.index() + 1), MissionWizardStyles.STYLE));
    number.setFont(MissionWizardStyles.rajdhani(14));
    number.setTextHAlignment(HAlignment.Center);
    col.addChild(circle);

    Label label =
        col.addChild(new Label(step.label(), MissionWizardStyles.STYLE));
    label.setFont(MissionWizardStyles.rajdhani(12));
    label.setTextHAlignment(HAlignment.Center);

    MouseEventControl.addListenersToSpatial(
        col,
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent evt, Spatial target, Spatial capture) {
            onStepClicked.accept(step);
          }
        });

    return col;
  }

  private Container buildDashSeparator() {
    float totalW = DASH_COUNT * DASH_WIDTH + (DASH_COUNT - 1) * DASH_GAP;
    return MissionWizardStyles.spacer(totalW, DASH_HEIGHT);
  }

  private void applyDoneState(Container node, MissionWizardStep step) {
    Container circle = (Container) node.getChild(0);
    circle.setBackground(
        MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_SUCCESS));
    ((Label) circle.getChild(0)).setText("v");
    ((Label) circle.getChild(0)).setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);
    ((Label) node.getChild(1)).setColor(MissionWizardStyles.WIZARD_SUCCESS);
  }

  private void applyActiveState(Container node, MissionWizardStep step) {
    Container circle = (Container) node.getChild(0);
    circle.setBackground(
        MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_ACCENT));
    ((Label) circle.getChild(0)).setText(String.valueOf(step.index() + 1));
    ((Label) circle.getChild(0)).setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);
    ((Label) node.getChild(1)).setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);
  }

  private void applyPendingState(Container node, MissionWizardStep step) {
    Container circle = (Container) node.getChild(0);
    circle.setBackground(
        MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_BORDER));
    ((Label) circle.getChild(0)).setText(String.valueOf(step.index() + 1));
    ((Label) circle.getChild(0)).setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
    ((Label) node.getChild(1)).setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
  }
}
