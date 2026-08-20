package com.smousseur.orbitlab.ui.mission.wizard;

import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.operation.MissionComposer;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
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
import com.smousseur.orbitlab.ui.UiLayers;
import com.smousseur.orbitlab.ui.mission.wizard.step.*;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MissionWizardWidget implements AutoCloseable {
  private static final Logger logger = LogManager.getLogger(MissionWizardWidget.class);

  private static final float WINDOW_WIDTH = 880f;
  private static final float WINDOW_HEIGHT = 680f;
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
  private final boolean editMode;
  private MissionWizardStep currentStep = MissionWizardStep.MISSION;
  private boolean visible = false;

  private Consumer<Map<String, Object>> onSubmit = values -> {};

  /**
   * Opens the wizard on a new mission.
   *
   * @param context the application context
   */
  public MissionWizardWidget(ApplicationContext context) {
    this(context, null);
  }

  /**
   * Opens the wizard, prefilled on the values of an existing mission when {@code initialValues} is
   * non-null — the edit mode. Editing differs from creating on three points: the header says so,
   * the last step confirms with "Update" rather than "Create", and the mission type is locked,
   * since a mission's stages, propellant budget and eligible payloads all derive from it.
   *
   * <p>The type on display is the one {@code context.missionContext()} currently selects, so a
   * caller opening the wizard on a mission must select that mission's type first: every step reads
   * the type from the context, not from {@code initialValues}.
   *
   * @param context the application context
   * @param initialValues values to prefill the steps with, or {@code null} to open blank
   */
  public MissionWizardWidget(ApplicationContext context, Map<String, Object> initialValues) {
    this.editMode = initialValues != null;
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

    Label brandSub =
        brandRow.addChild(
            new Label(editMode ? "EDIT MISSION" : "MISSION WIZARD", FormStyles.STYLE));
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

    MissionContext missionContext = context.missionContext();
    stepLaunchSite = new StepLaunchSite();
    stepPanels.put(MissionWizardStep.SITE, stepLaunchSite.getNode());
    // The latitude is read live, not captured: the coordinate fields stay editable after a
    // cosmodrome is picked, and the inclination bounds have to follow them (spec
    // docs/earth-orbit/02-wizard-orbites-terrestres.md §5).
    stepParameters = new StepParameters(missionContext, stepLaunchSite::currentLatitude);
    stepPanels.put(MissionWizardStep.PARAMETERS, stepParameters.getNode());
    stepMissionType =
        new StepMissionType(missionContext, initialProfile(missionContext, initialValues), editMode);
    stepPanels.put(MissionWizardStep.MISSION, stepMissionType.getNode());
    stepParameters.setProfile(stepMissionType.selectedProfile());
    stepMissionType.setOnProfileSelected(stepParameters::setProfile);
    stepLauncher = new StepLauncher(missionContext);
    stepPanels.put(MissionWizardStep.LAUNCHER, stepLauncher.getNode());

    footer.setOnNext(this::goNext);
    footer.setOnPrevious(this::goPrevious);
    stepper.setOnStepClicked(this::goToStep);

    if (editMode) {
      footer.setSubmitLabel("Update");
      // The mission-type step is absent from this list on purpose: its value is fixed at
      // construction (locked card), there is nothing left to prefill.
      for (StepValues step : List.of(stepParameters, stepLaunchSite, stepLauncher)) {
        step.applyValues(initialValues);
      }
    }

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
    stepLauncher.update();
  }

  public void showStep(MissionWizardStep step) {
    currentStep = step;
    content.clearChildren();
    Container panel = stepPanels.get(step);
    if (panel != null) content.addChild(panel);
    if (step == MissionWizardStep.PARAMETERS) {
      stepParameters.onStepEntered();
    }
    stepper.setActiveStep(step);
    footer.setStep(step);
    logger.debug("Wizard: showing step {}", step);
  }

  public void goNext() {
    if (currentStep == MissionWizardStep.LAUNCHER) {
      // Checked again here, and not only when leaving the parameters step, because the stepper lets
      // the user jump over it.
      if (parametersRefused()) {
        showStep(MissionWizardStep.PARAMETERS);
        // Revealed again after the step is shown, not only inside the check: entering a step opens
        // it on its fields page, which would undo the page the refusal had just selected.
        stepParameters.revealRefusal();
        return;
      }
      Map<String, Object> values = getAllValues();
      Optional<String> refusal = compositionRefused(values);
      if (refusal.isPresent()) {
        stepLauncher.showRefusal(refusal.get());
        return;
      }
      stepLauncher.clearRefusal();
      onSubmit.accept(values);
      return;
    }
    if (currentStep == MissionWizardStep.PARAMETERS && parametersRefused()) {
      return;
    }
    MissionWizardStep next = currentStep.next();
    if (next != null) showStep(next);
  }

  /**
   * Keeps the wizard open on a parameter the application cannot use, the offending field marked.
   *
   * <p>Every check runs, and none short-circuits the others: a user who has a bad date <em>and</em>
   * a bad duration should see both fields marked at once rather than discover the second only after
   * fixing the first.
   *
   * <p>Marking is not enough to be seen, though: the step has two pages and mounts one at a time, so
   * a refusal is also revealed on the page that carries it. Without that, refusing a field of the
   * fields page while the planning page is up leaves nothing on screen changed and the Next button
   * looking dead.
   */
  private boolean parametersRefused() {
    Optional<String> dateError = stepParameters.validateLaunchDate();
    dateError.ifPresent(reason -> logger.info("Wizard: launch date refused ({})", reason));
    Optional<String> horizonError = stepParameters.validateHorizon();
    horizonError.ifPresent(reason -> logger.info("Wizard: mission duration refused ({})", reason));
    Optional<String> planeError = stepParameters.validateTargetPlane();
    planeError.ifPresent(reason -> logger.info("Wizard: target plane refused ({})", reason));
    boolean refused = dateError.isPresent() || horizonError.isPresent() || planeError.isPresent();
    if (refused) {
      stepParameters.revealRefusal();
    }
    return refused;
  }

  /**
   * Builds the mission the confirmation button would create, and reports why it cannot be built.
   *
   * <p><b>Why here and not at creation.</b> Some refusals depend on the whole form: a target beyond
   * the ascent's reach needs an upper stage that holds the coast to apogee, or a payload whose kick
   * motor takes the burn over — so the target chosen at step 3 is only refutable once the vehicle is
   * picked at step 4 (spec {@code docs/earth-orbit/02-wizard-orbites-terrestres.md} §6). Left to
   * {@code MissionWizardAppState}, the exception lands in a log line with the wizard already closed
   * and no mission created, which is indistinguishable from the application ignoring the user.
   *
   * <p>Nothing is propagated: composing resolves the catalogs, sizes the propellant analytically and
   * assembles the stages. The mission built here is thrown away — the submit path rebuilds it, from
   * the same values.
   *
   * @param values the aggregated form values
   * @return the refusal, worded by the model, or empty when the mission composes
   */
  private Optional<String> compositionRefused(Map<String, Object> values) {
    try {
      MissionComposer.compose(
          MissionFactory.specFromWizardValues(
              values, stepMissionType.selectedProfile().missionType()),
          OptimizationType.FAST);
      return Optional.empty();
    } catch (RuntimeException e) {
      String reason = e.getMessage() == null ? e.toString() : e.getMessage();
      logger.info("Wizard: mission refused at the launcher step ({})", reason);
      return Optional.of(reason);
    }
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
    showStep(step);
  }

  /**
   * The card the wizard opens on: the one the edited mission was built from, or the mission type the
   * context currently selects when creating.
   *
   * <p>Reopening reads the profile the prefill derived from the spec — no spec component carries it
   * (spec {@code docs/earth-orbit/02-wizard-orbites-terrestres.md} §2.1) — and falls back on the
   * type alone if the values predate P2 or name a profile this build does not know.
   */
  private static MissionProfile initialProfile(
      MissionContext missionContext, Map<String, Object> initialValues) {
    if (initialValues != null) {
      Object raw = initialValues.get(FormField.MISSION_PROFILE.key());
      if (raw != null) {
        try {
          return MissionProfile.valueOf(raw.toString());
        } catch (IllegalArgumentException ignored) {
          // Falls through to the type-based reading below.
        }
      }
    }
    return missionContext.getSelectedMissionType() == MissionType.GEO
        ? MissionProfile.GEO
        : MissionProfile.LEO;
  }

  public void setOnCancel(Runnable action) {
    footer.setOnCancel(action);
  }

  /**
   * Sets what the last step's confirmation button does with the aggregated values — create a
   * mission or update the one the wizard was opened on.
   *
   * @param action the submit handler, or {@code null} to ignore submissions
   */
  public void setOnSubmit(Consumer<Map<String, Object>> action) {
    this.onSubmit = action != null ? action : values -> {};
  }

  private void centerOnScreen(int screenWidth, int screenHeight) {
    float x = Math.round((screenWidth - WINDOW_WIDTH) / 2f);
    float y = Math.round((screenHeight + WINDOW_HEIGHT) / 2f);
    root.setLocalTranslation(x, y, UiLayers.MODAL);
  }
}
