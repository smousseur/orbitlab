package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardWidget;
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

  public MissionWizardAppState(ApplicationContext context) {
    this.context = context;
  }

  public boolean isWizardVisible() {
    return widget != null && widget.isVisible();
  }

  @Override
  protected void initialize(Application app) {}

  @Override
  protected void cleanup(Application app) {
    closeWizard();
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}

  @Override
  public void update(float tpf) {
    EventBus bus = context.eventBus();
    if (bus.pollOpenWizard() != null) {
      openWizard();
    }
    EventBus.UiNavigationEvent.CreateMission create = bus.pollCreateMission();
    if (create != null) {
      createMission(create);
    }
    if (widget != null) {
      widget.update(tpf, getApplication().getCamera());
    }
  }

  private void createMission(EventBus.UiNavigationEvent.CreateMission createMission) {
    MissionContext missionContext = context.missionContext();
    Map<String, Object> values = createMission.values();
    String requestedName = String.valueOf(values.get(FormField.MISSION_NAME.key()));

    // Names are labels, not keys — a duplicate no longer costs the user their mission. It is
    // suffixed so the list stays readable, and the mission is created either way.
    String name = availableName(missionContext, requestedName);
    if (!name.equals(requestedName)) {
      logger.warn("Mission name '{}' is already in use, creating '{}' instead", requestedName, name);
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
      missionEntry.setScheduledDate(missionDate.get());
      missionContext.addMission(missionEntry);
      logger.info("Mission '{}' created [{}]", name, missionEntry.id().shortForm());
    } catch (RuntimeException e) {
      // A bad wizard value must not crash the render loop; the mission is simply not created.
      logger.error("Mission creation failed for '{}': {}", name, e.getMessage());
    }
  }

  /**
   * Returns {@code requested} if no mission carries it, otherwise the first free {@code "requested
   * (n)"} variant. Purely cosmetic: uniqueness of the name is advisory, mission identity is the
   * {@code MissionId} minted by the entry.
   */
  private static String availableName(MissionContext missionContext, String requested) {
    if (!missionContext.isNameInUse(requested)) {
      return requested;
    }
    for (int suffix = 2; ; suffix++) {
      String candidate = requested + " (" + suffix + ")";
      if (!missionContext.isNameInUse(candidate)) {
        return candidate;
      }
    }
  }

  private void openWizard() {
    if (widget != null) return;
    widget = new MissionWizardWidget(context);
    widget.setOnCancel(this::closeWizard);
    widget.setOnCreate(
        values -> {
          logger.info("Mission Wizard CREATE_MISSION values = {}", values);
          context
              .eventBus()
              .publishUiNavigation(new EventBus.UiNavigationEvent.CreateMission(values));
          closeWizard();
          context
              .eventBus()
              .publishUiNavigation(new EventBus.UiNavigationEvent.OpenMissionManagement());
        });
    widget.attachTo(context.guiGraph().getModalNode());
    logger.info("Mission Wizard opened");
  }

  private void closeWizard() {
    if (widget != null) {
      widget.close();
      widget = null;
      logger.info("Mission Wizard closed");
    }
  }
}
