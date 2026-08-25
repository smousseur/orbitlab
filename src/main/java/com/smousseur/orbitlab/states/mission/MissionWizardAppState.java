package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.HudSurface;
import com.smousseur.orbitlab.app.HudSurfaces;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.problem.EarthLaunchWindowPlanner;
import com.smousseur.orbitlab.ui.UiLayers;
import com.smousseur.orbitlab.ui.form.ConfirmDialog;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardWidget;
import com.smousseur.orbitlab.ui.mission.wizard.WizardPrefill;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.time.AbsoluteDate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class MissionWizardAppState extends BaseAppState {
  private static final Logger logger = LogManager.getLogger(MissionWizardAppState.class);

  private final ApplicationContext context;
  private MissionWizardWidget widget;
  private AutoCloseable surfaceHandle;
  private ConfirmDialog discardDialog;
  private AutoCloseable discardDialogHandle;

  /** True while the open wizard is editing an existing mission, which changes what we ask. */
  private boolean editing;

  public MissionWizardAppState(ApplicationContext context) {
    this.context = context;
  }

  public boolean isWizardVisible() {
    return widget != null && widget.isVisible();
  }

  @Override
  protected void initialize(Application app) {
    surfaceHandle =
        context
            .hudSurfaces()
            .register(
                new HudSurface(
                    HudSurface.MISSION_WIZARD,
                    UiLayers.MODAL,
                    this::isWizardVisible,
                    this::confirmDiscard));
  }

  @Override
  protected void cleanup(Application app) {
    closeDiscardDialog();
    closeWizard();
    HudSurfaces.closeQuietly(surfaceHandle, logger);
    surfaceHandle = null;
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}

  @Override
  public void update(float tpf) {
    EventBus bus = context.eventBus();
    EventBus.UiNavigationEvent.OpenMissionWizard open = bus.pollOpenWizard();
    if (open != null) {
      openWizard(open.missionId());
    }
    EventBus.UiNavigationEvent.CreateMission create = bus.pollCreateMission();
    if (create != null) {
      createMission(create);
    }
    EventBus.UiNavigationEvent.UpdateMission edit = bus.pollUpdateMission();
    if (edit != null) {
      updateMission(edit);
    }
    if (discardDialog != null) {
      discardDialog.update(getApplication().getCamera());
    }
    if (widget != null) {
      widget.update(tpf, getApplication().getCamera());
    }
  }

  /**
   * Asks before throwing away an open wizard. ESC is easy to hit by accident on a form four steps
   * deep, which is the whole reason the key stopped quitting the application; closing the wizard
   * without asking would just move the same loss one level down.
   *
   * <p>The question is asked every time, with no "has anything been typed" tracking: that
   * bookkeeping would exist only to save one click in the single case where the user opens the
   * wizard and leaves it untouched. The Cancel button stays immediate — it is already explicit.
   */
  private void confirmDiscard() {
    if (widget == null || discardDialog != null) return;

    discardDialog = new ConfirmDialog(editing ? "Discard changes?" : "Discard this mission?");
    discardDialog.setOnCancel(this::closeDiscardDialog);
    discardDialog.setOnConfirm(
        () -> {
          closeDiscardDialog();
          closeWizard();
        });
    discardDialog.attachTo(context.guiGraph().getModalNode());
    // Registered only now: before attachTo, isVisible() would report open with nothing yet on
    // screen, which HudSurface's contract does not allow.
    discardDialogHandle =
        context
            .hudSurfaces()
            .register(
                new HudSurface(
                    HudSurface.DISCARD_DIALOG,
                    UiLayers.DIALOG,
                    discardDialog::isVisible,
                    this::closeDiscardDialog));
  }

  private void closeDiscardDialog() {
    if (discardDialog != null) {
      discardDialog.close();
      discardDialog = null;
    }
    HudSurfaces.closeQuietly(discardDialogHandle, logger);
    discardDialogHandle = null;
  }

  private void createMission(EventBus.UiNavigationEvent.CreateMission createMission) {
    MissionContext missionContext = context.missionContext();
    Map<String, Object> values = createMission.values();
    String requestedName = String.valueOf(values.get(FormField.MISSION_NAME.key()));

    // Names are labels, not keys — a duplicate no longer costs the user their mission. It is
    // suffixed so the list stays readable, and the mission is created either way.
    String name = availableName(missionContext, requestedName, null);
    if (!name.equals(requestedName)) {
      logger.warn(
          "Mission name '{}' is already in use, creating '{}' instead", requestedName, name);
      values = new HashMap<>(values);
      values.put(FormField.MISSION_NAME.key(), name);
    }

    // Second line of defence behind the wizard's own validation: this runs in the render loop, and
    // Orekit's string constructor throws on anything it dislikes — including any date before 1970.
    Object rawDate = values.get("LAUNCH_DATE");
    Optional<AbsoluteDate> missionDate = TimeConverter.parseUtcDate(String.valueOf(rawDate));
    if (missionDate.isEmpty()) {
      logger.error("Mission creation failed for '{}': unusable launch date '{}'", name, rawDate);
      return;
    }
    try {
      MissionSpec spec =
          MissionFactory.specFromWizardValues(values, missionContext.getSelectedMissionType());
      MissionEntry missionEntry = new MissionEntry(spec);
      missionEntry.setScheduledDate(scheduledDateFor(spec, missionDate.get()));
      missionContext.addMission(missionEntry);
      logger.info("Mission '{}' created [{}]", name, missionEntry.id().shortForm());
    } catch (RuntimeException e) {
      // A bad wizard value must not crash the render loop; the mission is simply not created.
      logger.error("Mission creation failed for '{}': {}", name, e.getMessage());
    }
  }

  /**
   * The date the mission is actually scheduled at: the one the user typed, unless the mission names
   * a target plane, in which case it is the next opening of that plane's launch window (MIS-2).
   *
   * <p><b>The typed date becomes a floor.</b> A pad meets a given node once per sidereal day and
   * the rest of the day costs kilometres per second, so "launch on the 4th at 12:00" can only mean
   * "on the 4th at 12:00 or as soon after as the geometry allows". This is what gives the wizard's
   * launch-date field a meaning it did not have.
   *
   * <p>Run here, on the render thread, because it is closed form — some ninety evaluations of an
   * angle between two vectors. Nothing propagates.
   *
   * @param spec the mission being scheduled
   * @param requested the date read from the wizard
   * @return the date to schedule
   */
  private static AbsoluteDate scheduledDateFor(MissionSpec spec, AbsoluteDate requested) {
    if (!(spec instanceof MissionSpec.EarthOrbit earthOrbit) || !earthOrbit.hasTargetRaan()) {
      return requested;
    }
    Optional<LaunchWindow> window = EarthLaunchWindowPlanner.nextOpportunity(earthOrbit, requested);
    if (window.isEmpty()) {
      // A sidereal day always holds an alignment, so this is a broken configuration rather than an
      // unlucky one. The mission is still created, at the date it asked for.
      logger.warn(
          "No launch window found for RAAN {}° within a day of {}; keeping the requested date",
          earthOrbit.targetRaan(),
          requested);
      return requested;
    }
    logger.info(
        "Launch window for RAAN {}°: {} (requested {}, slot {} at {} m/s)",
        earthOrbit.targetRaan(),
        window.get().date(),
        requested,
        window.get().duration(),
        Math.round(window.get().best().deltaV()));
    return window.get().date();
  }

  /**
   * Re-specifies an existing mission from the wizard values it was reopened with. The entry keeps
   * its identity, color and place in the roster; its composed mission, optimization result and
   * ephemeris are replaced, so the mission goes back to {@code DRAFT} and must be recomputed.
   *
   * <p>The mission type comes from the entry, never from the values: the wizard locks it in edit
   * mode, and {@link MissionEntry#applySpec(MissionSpec)} refuses a spec that would change it.
   */
  private void updateMission(EventBus.UiNavigationEvent.UpdateMission request) {
    MissionContext missionContext = context.missionContext();
    MissionEntry entry = missionContext.findMission(request.missionId()).orElse(null);
    if (entry == null) {
      // Deleted while the wizard was open.
      logger.warn(
          "Mission update ignored: mission [{}] no longer exists", request.missionId().shortForm());
      return;
    }
    MissionSpec currentSpec = entry.spec().orElse(null);
    if (currentSpec == null) {
      logger.warn("Mission update ignored: mission [{}] carries no spec", entry.id().shortForm());
      return;
    }

    Map<String, Object> values = request.values();
    String requestedName = String.valueOf(values.get(FormField.MISSION_NAME.key()));
    // Same cosmetic rule as at creation, except the mission being edited does not count as holding
    // its own name — keeping it must not turn "Artemis" into "Artemis (2)".
    String name = availableName(missionContext, requestedName, entry.id());
    if (!name.equals(requestedName)) {
      logger.warn("Mission name '{}' is already in use, keeping '{}' instead", requestedName, name);
      values = new HashMap<>(values);
      values.put(FormField.MISSION_NAME.key(), name);
    }

    Object rawDate = values.get(FormField.LAUNCH_DATE.key());
    Optional<AbsoluteDate> missionDate = TimeConverter.parseUtcDate(String.valueOf(rawDate));
    if (missionDate.isEmpty()) {
      logger.error("Mission update failed for '{}': unusable launch date '{}'", name, rawDate);
      return;
    }
    try {
      MissionSpec spec = MissionFactory.specFromWizardValues(values, currentSpec.type());
      if (!entry.applySpec(spec)) {
        // applySpec already logged the composition failure and marked the mission FAILED; the
        // scheduled date is left alone so the entry keeps describing the mission it still flies.
        return;
      }
      entry.setScheduledDate(scheduledDateFor(spec, missionDate.get()));
      logger.info("Mission '{}' updated [{}]", name, entry.id().shortForm());
    } catch (RuntimeException e) {
      // Same net as at creation: a bad wizard value must not crash the render loop. The mission is
      // simply left as it was.
      logger.error("Mission update failed for '{}': {}", name, e.getMessage());
    }
  }

  /**
   * Returns {@code requested} if no mission carries it, otherwise the first free {@code "requested
   * (n)"} variant. Purely cosmetic: uniqueness of the name is advisory, mission identity is the
   * {@code MissionId} minted by the entry.
   *
   * @param excluded a mission whose own name does not count as taken (the one being edited), or
   *     {@code null} when every registered mission counts
   */
  private static String availableName(
      MissionContext missionContext, String requested, MissionId excluded) {
    if (isNameFree(missionContext, requested, excluded)) {
      return requested;
    }
    for (int suffix = 2; ; suffix++) {
      String candidate = requested + " (" + suffix + ")";
      if (isNameFree(missionContext, candidate, excluded)) {
        return candidate;
      }
    }
  }

  private static boolean isNameFree(
      MissionContext missionContext, String candidate, MissionId excluded) {
    if (!missionContext.isNameInUse(candidate)) {
      return true;
    }
    if (excluded == null) {
      return false;
    }
    // Taken — but possibly only by the mission we are editing, which is free to keep its name.
    return missionContext.getMissions().stream()
        .filter(other -> !other.id().equals(excluded))
        .noneMatch(other -> other.mission().getName().equals(candidate));
  }

  /**
   * Opens the wizard, blank or prefilled on an existing mission.
   *
   * @param missionId the mission to edit, or {@code null} to create a new one
   */
  private void openWizard(MissionId missionId) {
    if (widget != null) return;

    MissionEntry edited = null;
    if (missionId != null) {
      edited = context.missionContext().findMission(missionId).orElse(null);
      MissionSpec spec = edited == null ? null : edited.spec().orElse(null);
      if (spec == null) {
        // Race: deleted between the click and the poll. The management window is still open behind
        // this, so there is nothing to reopen — it was never closed.
        logger.warn("Edit ignored: mission [{}] is not editable", missionId.shortForm());
        return;
      }
      // Every wizard step reads the mission type from the context, so it must designate the edited
      // mission before the widget builds them.
      context.missionContext().setSelectedMissionType(spec.type());
    }

    Map<String, Object> initialValues = edited == null ? null : WizardPrefill.fromEntry(edited);
    MissionId editedId = edited == null ? null : edited.id();

    editing = editedId != null;
    widget = new MissionWizardWidget(context, initialValues);
    widget.setOnCancel(this::closeWizard);
    widget.setOnSubmit(values -> submit(editedId, values));
    widget.attachTo(context.guiGraph().getModalNode());
    logger.info(
        "Mission Wizard opened{}", editedId == null ? "" : " on [" + editedId.shortForm() + "]");
  }

  /**
   * Hands the aggregated wizard values to the create or the update path, then closes the wizard.
   */
  private void submit(MissionId editedMissionId, Map<String, Object> values) {
    EventBus bus = context.eventBus();
    if (editedMissionId == null) {
      logger.info("Mission Wizard CREATE_MISSION values = {}", values);
      bus.publishUiNavigation(new EventBus.UiNavigationEvent.CreateMission(values));
    } else {
      logger.info(
          "Mission Wizard UPDATE_MISSION [{}] values = {}", editedMissionId.shortForm(), values);
      bus.publishUiNavigation(
          new EventBus.UiNavigationEvent.UpdateMission(editedMissionId, values));
    }
    bus.publishUiNavigation(new EventBus.UiNavigationEvent.OpenMissionManagement());
    closeWizard();
  }

  private void closeWizard() {
    // No reachable path closes the wizard while the discard question is up — the dialog's backdrop
    // shields every control that could — but that invariant lives in two other files. Draining it
    // here makes it hold by construction, and the confirm path already orders it this way.
    closeDiscardDialog();
    if (widget != null) {
      widget.close();
      widget = null;
      editing = false;
      logger.info("Mission Wizard closed");
    }
  }
}
