package com.smousseur.orbitlab.ui.mission.wizard;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;
import java.util.function.Consumer;

public class WizardStepper {

  private static final float STEPPER_HEIGHT = 48f;
  private static final float CIRCLE_SIZE = 28f;
  private static final float LABEL_GAP = 6f;
  private static final float CONNECTOR_WIDTH = 48f;
  private static final float CONNECTOR_HEIGHT = 1f;
  private static final float STEP_TAB_WIDTH = 92f;

  private final Container root;
  private final Container[] stepNodes = new Container[MissionWizardStep.COUNT];
  private final Container[] connectors = new Container[MissionWizardStep.COUNT - 1];
  private Consumer<MissionWizardStep> onStepClicked = step -> {};

  public WizardStepper() {
    root = new Container(new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE);
    root.setPreferredSize(new Vector3f(0, STEPPER_HEIGHT, 0));
    root.setBackground(null);

    for (int i = 0; i < MissionWizardStep.COUNT; i++) {
      if (i > 0) {
        Container connector = buildConnector();
        connectors[i - 1] = connector;
        root.addChild(connector);
      }
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
    for (int i = 0; i < connectors.length; i++) {
      Container connector = connectors[i];
      boolean done = i < activeStep.index();
      connector.setBackground(
          new QuadBackgroundComponent(
              done
                  ? MissionWizardStyles.WIZARD_SUCCESS
                  : MissionWizardStyles.WIZARD_BORDER));
    }
  }

  private Container buildStepNode(MissionWizardStep step) {
    Container col = new Container(new BoxLayout(Axis.Y, FillMode.None));
    col.setBackground(null);
    col.setPreferredSize(new Vector3f(STEP_TAB_WIDTH, STEPPER_HEIGHT, 0));

    Container circleRow = col.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    circleRow.setBackground(null);
    float pad = Math.max(0f, (STEP_TAB_WIDTH - CIRCLE_SIZE) / 2f);
    circleRow.addChild(UiKit.hSpacer(pad));

    Container circle = new Container();
    circle.setPreferredSize(new Vector3f(CIRCLE_SIZE, CIRCLE_SIZE, 0));
    circle.setBackground(UiKit.wizardBg9("step-dot-default", 14));
    Label number =
        circle.addChild(new Label(String.valueOf(step.index() + 1), MissionWizardStyles.STYLE));
    number.setFont(UiKit.orbitron(13));
    number.setTextHAlignment(HAlignment.Center);
    number.setTextVAlignment(VAlignment.Center);
    number.setPreferredSize(new Vector3f(CIRCLE_SIZE, CIRCLE_SIZE, 0));
    circleRow.addChild(circle);
    circleRow.addChild(UiKit.hSpacer(pad));

    col.addChild(UiKit.vSpacer(LABEL_GAP));

    Label label = col.addChild(new Label(step.label(), MissionWizardStyles.STYLE));
    label.setFont(UiKit.ibmPlexMono(11));
    label.setTextHAlignment(HAlignment.Center);
    label.setPreferredSize(new Vector3f(STEP_TAB_WIDTH, label.getPreferredSize().y, 0));
    label.setColor(MissionWizardStyles.WIZARD_TEXT_LO);

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

  private Container buildConnector() {
    Container c = new Container();
    c.setPreferredSize(new Vector3f(CONNECTOR_WIDTH, CONNECTOR_HEIGHT, 0));
    c.setBackground(new QuadBackgroundComponent(MissionWizardStyles.WIZARD_BORDER));
    return c;
  }

  private void applyDoneState(Container node, MissionWizardStep step) {
    Container circleRow = (Container) node.getChild(0);
    Container circle = (Container) circleRow.getChild(1);
    circle.setBackground(UiKit.wizardBg9("step-dot-done", 14));
    Label number = (Label) circle.getChild(0);
    number.setText("");
    number.setIcon(UiKit.wizardIconComponent("icon-check-success"));
    ((Label) node.getChild(2)).setColor(MissionWizardStyles.WIZARD_SUCCESS);
  }

  private void applyActiveState(Container node, MissionWizardStep step) {
    Container circleRow = (Container) node.getChild(0);
    Container circle = (Container) circleRow.getChild(1);
    circle.setBackground(UiKit.wizardBg9("step-dot-active", 14));
    Label number = (Label) circle.getChild(0);
    number.setIcon(null);
    number.setText(String.valueOf(step.index() + 1));
    number.setColor(MissionWizardStyles.WIZARD_ACCENT_BRIGHT);
    ((Label) node.getChild(2)).setColor(MissionWizardStyles.WIZARD_ACCENT_BRIGHT);
  }

  private void applyPendingState(Container node, MissionWizardStep step) {
    Container circleRow = (Container) node.getChild(0);
    Container circle = (Container) circleRow.getChild(1);
    circle.setBackground(UiKit.wizardBg9("step-dot-default", 14));
    Label number = (Label) circle.getChild(0);
    number.setIcon(null);
    number.setText(String.valueOf(step.index() + 1));
    number.setColor(MissionWizardStyles.WIZARD_TEXT_LO);
    ((Label) node.getChild(2)).setColor(MissionWizardStyles.WIZARD_TEXT_LO);
  }
}
