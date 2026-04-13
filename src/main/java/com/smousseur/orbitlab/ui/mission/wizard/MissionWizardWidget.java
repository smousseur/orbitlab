package com.smousseur.orbitlab.ui.mission.wizard;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.ui.mission.wizard.step.*;
import java.util.EnumMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MissionWizardWidget implements AutoCloseable {
  private static final Logger logger = LogManager.getLogger(MissionWizardWidget.class);

  private static final float WINDOW_WIDTH = 880f;
  private static final float WINDOW_HEIGHT = 640f;
  private static final float MIN_VIEWPORT_MARGIN = 32f;
  private static final float OUTER_PADDING = 24f;
  private static final float HEADER_HEIGHT = 88f;

  private final ModalBackdrop backdrop;
  private final Container root;
  private final WizardStepper stepper;
  private final WizardFooter footer;
  private final Container content;

  private final Map<MissionWizardStep, Container> stepPanels =
      new EnumMap<>(MissionWizardStep.class);
  private MissionWizardStep currentStep = MissionWizardStep.MISSION;
  private boolean visible = false;

  public MissionWizardWidget(ApplicationContext context) {
    backdrop = new ModalBackdrop();

    root =
        new Container(
            new BoxLayout(Axis.Y, FillMode.None), MissionWizardStyles.STYLE);
    root.setPreferredSize(new Vector3f(WINDOW_WIDTH, WINDOW_HEIGHT, 0));
    root.setBackground(
        MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_BG_DEEP));
    root.setInsetsComponent(
        new InsetsComponent(
            new Insets3f(
                OUTER_PADDING, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING)));

    // Header
    Container header =
        root.addChild(
            new Container(new BoxLayout(Axis.Y, FillMode.None)));
    header.setBackground(null);
    header.setPreferredSize(new Vector3f(0, HEADER_HEIGHT, 0));

    Container brandRow =
        header.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.None)));
    brandRow.setBackground(null);

    brandRow.addChild(
        MissionWizardStyles.iconPlaceholder("icons/wizard/brand-globe.png", 24, 24));

    Label brandName =
        brandRow.addChild(new Label(" ORBITLAB", MissionWizardStyles.STYLE));
    brandName.setFont(MissionWizardStyles.rajdhani(18));
    brandName.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

    Label brandSep =
        brandRow.addChild(new Label("  /  ", MissionWizardStyles.STYLE));
    brandSep.setFont(MissionWizardStyles.rajdhani(18));
    brandSep.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

    Label brandVersion =
        brandRow.addChild(
            new Label("MISSION WIZARD v2.1", MissionWizardStyles.STYLE));
    brandVersion.setFont(MissionWizardStyles.rajdhani(18));
    brandVersion.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

    stepper = new WizardStepper();
    header.addChild(stepper.getNode());

    content =
        root.addChild(
            new Container(new BoxLayout(Axis.Y, FillMode.None)));
    content.setBackground(null);

    footer = new WizardFooter();
    root.addChild(footer.getNode());

    stepPanels.put(MissionWizardStep.MISSION, new StepMissionType().getNode());
    stepPanels.put(MissionWizardStep.PARAMETERS, new StepParameters().getNode());
    stepPanels.put(MissionWizardStep.SITE, new StepLaunchSite().getNode());
    stepPanels.put(MissionWizardStep.LAUNCHER, new StepLauncher().getNode());

    showStep(currentStep);
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

  public void update(float tpf, Camera cam) {
    if (!visible) return;
    backdrop.update(cam);
    centerOnScreen(cam.getWidth(), cam.getHeight());
  }

  public void showStep(MissionWizardStep step) {
    currentStep = step;
    content.clearChildren();
    Container panel = stepPanels.get(step);
    if (panel != null) content.addChild(panel);
    stepper.setActiveStep(step);
    footer.setStep(step);
    logger.debug("Wizard: showing step {}", step);
  }

  public void cycleStep() {
    MissionWizardStep next = currentStep.next();
    showStep(next != null ? next : MissionWizardStep.MISSION);
  }

  public void setOnCancel(Runnable action) {
    footer.setOnCancel(action);
  }

  private void centerOnScreen(int screenWidth, int screenHeight) {
    if (screenWidth < WINDOW_WIDTH + 2 * MIN_VIEWPORT_MARGIN
        || screenHeight < WINDOW_HEIGHT + 2 * MIN_VIEWPORT_MARGIN) {
      logger.warn(
          "Viewport {}x{} smaller than wizard minimum",
          screenWidth,
          screenHeight);
    }
    float x = Math.round((screenWidth - WINDOW_WIDTH) / 2f);
    float y = Math.round((screenHeight + WINDOW_HEIGHT) / 2f);
    root.setLocalTranslation(x, y, 1f);
  }
}
