package com.smousseur.orbitlab.ui.mission.wizard;

import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.form.ModalBackdrop;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.core.GuiComponent;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.mission.wizard.step.*;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MissionWizardWidget implements AutoCloseable {
  private static final Logger logger = LogManager.getLogger(MissionWizardWidget.class);

  private static final float WINDOW_WIDTH = 880f;
  private static final float WINDOW_HEIGHT = 680f;
  private static final float MIN_VIEWPORT_MARGIN = 32f;
  private static final float HEADER_HEIGHT = 120f;
  private static final float HEADER_PAD_X = 32f;
  private static final float HEADER_PAD_Y = 20f;

  /** Largeur utile à l'intérieur du header (entre ses paddings horizontaux). */
  private static final float HEADER_INNER_WIDTH = WINDOW_WIDTH - 2 * HEADER_PAD_X;

  private final ModalBackdrop backdrop;
  private final Container root;
  private final WizardStepper stepper;
  private final WizardFooter footer;
  private final Container content;
  private final StepMissionType stepMissionType;
  private final StepParameters stepParameters;
  private final StepLaunchSite stepLaunchSite;
  private final StepLauncher stepLauncher;

  private final Map<MissionWizardStep, Container> stepPanels =
      new EnumMap<>(MissionWizardStep.class);
  private MissionWizardStep currentStep = MissionWizardStep.MISSION;
  private boolean visible = false;

  private Consumer<Map<String, Object>> onCreate = values -> {};

  public MissionWizardWidget(ApplicationContext context) {
    backdrop = new ModalBackdrop();

    root = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(WINDOW_WIDTH, WINDOW_HEIGHT, 0));
    root.setBackground(FormStyles.shellBg());
    root.getInsetsComponent().setInsets(new Insets3f(0, 0, 0, 0));
    root.setBorder(null);
    GuiComponent bg = root.getBackground();
    if (bg instanceof TbtQuadBackgroundComponent quad) {
      quad.setMargin(0f, 0f);
    } else if (bg instanceof QuadBackgroundComponent quad) {
      quad.setMargin(0f, 0f);
    }

    // Header strip (deep flat bg) — spans full wizard width, touches the top edge.
    Container header = root.addChild(new Container(new BoxLayout(Axis.Y, FillMode.None)));
    header.setBackground(FormStyles.headerBg());
    header.setPreferredSize(new Vector3f(WINDOW_WIDTH, HEADER_HEIGHT, 0));
    header.setInsetsComponent(
        new InsetsComponent(new Insets3f(HEADER_PAD_Y, HEADER_PAD_X, HEADER_PAD_Y, HEADER_PAD_X)));

    Container brandRow = header.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    brandRow.setBackground(null);
    brandRow.setPreferredSize(new Vector3f(HEADER_INNER_WIDTH, 18f, 0));

    brandRow.addChild(UiKit.wizardIcon("icon-brand-globe", 18, 18));
    brandRow.addChild(UiKit.hSpacer(8));

    Label brandName = brandRow.addChild(new Label("ORBITLAB", FormStyles.STYLE));
    brandName.setFont(UiKit.orbitron(13));
    brandName.setColor(FormStyles.ACCENT_BRIGHT);

    Label brandSep = brandRow.addChild(new Label("  /  ", FormStyles.STYLE));
    brandSep.setFont(UiKit.ibmPlexMono(11));
    brandSep.setColor(FormStyles.TEXT_LO);

    Label brandSub = brandRow.addChild(new Label("MISSION WIZARD", FormStyles.STYLE));
    brandSub.setFont(UiKit.ibmPlexMono(11));
    brandSub.setColor(FormStyles.TEXT_LO);

    header.addChild(UiKit.vSpacer(7));

    stepper = new WizardStepper(HEADER_INNER_WIDTH);
    header.addChild(stepper.getNode());
    header.addChild(UiKit.vSpacer(12));

    // Content pane
    content = root.addChild(new Container(new BoxLayout(Axis.Y, FillMode.None)));
    content.setBackground(null);
    content.setPreferredSize(new Vector3f(WINDOW_WIDTH, FormStyles.CONTENT_HEIGHT, 0));
    content.setInsetsComponent(new InsetsComponent(new Insets3f(28, 32, 16, 32)));

    // Footer strip
    footer = new WizardFooter(WINDOW_WIDTH);
    footer.getNode().setBackground(FormStyles.footerBg());
    root.addChild(footer.getNode());

    stepMissionType = new StepMissionType();
    stepPanels.put(MissionWizardStep.MISSION, stepMissionType.getNode());
    stepParameters = new StepParameters();
    stepPanels.put(MissionWizardStep.PARAMETERS, stepParameters.getNode());
    stepLaunchSite = new StepLaunchSite();
    stepPanels.put(MissionWizardStep.SITE, stepLaunchSite.getNode());
    stepLauncher = new StepLauncher();
    stepPanels.put(MissionWizardStep.LAUNCHER, stepLauncher.getNode());

    footer.setOnNext(this::goNext);
    footer.setOnPrevious(this::goPrevious);
    stepper.setOnStepClicked(this::goToStep);

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
    stepParameters.update(tpf);
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

  public void goNext() {
    if (currentStep == MissionWizardStep.LAUNCHER) {
      onCreate.accept(getAllValues());
      return;
    }
    MissionWizardStep next = currentStep.next();
    if (next != null) showStep(next);
  }

  /** Aggregates values from every step. Throws if two steps publish the same key. */
  public Map<String, Object> getAllValues() {
    Map<String, Object> all = new LinkedHashMap<>();
    List<StepValues> steps = List.of(stepMissionType, stepParameters, stepLaunchSite, stepLauncher);
    for (StepValues step : steps) {
      step.getValues()
          .forEach(
              (k, v) -> {
                if (all.putIfAbsent(k, v) != null) {
                  throw new IllegalStateException("Duplicate FormField key across steps: " + k);
                }
              });
    }
    return all;
  }

  public void goPrevious() {
    MissionWizardStep previous = currentStep.previous();
    if (previous != null) showStep(previous);
  }

  public void goToStep(MissionWizardStep step) {
    if (step == MissionWizardStep.MISSION || stepMissionType.isMissionTypeSelected()) {
      showStep(step);
    }
  }

  public void setOnCancel(Runnable action) {
    footer.setOnCancel(action);
  }

  public void setOnCreate(Consumer<Map<String, Object>> action) {
    this.onCreate = action != null ? action : values -> {};
  }

  private void centerOnScreen(int screenWidth, int screenHeight) {
    float x = Math.round((screenWidth - WINDOW_WIDTH) / 2f);
    float y = Math.round((screenHeight + WINDOW_HEIGHT) / 2f);
    root.setLocalTranslation(x, y, 101f);
  }
}
