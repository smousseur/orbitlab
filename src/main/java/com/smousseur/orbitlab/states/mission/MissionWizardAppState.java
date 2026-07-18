package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardWidget;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;

import java.util.Map;

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
    String name = String.valueOf(values.get("MISSION_NAME"));
    if (missionContext.findMission(name).isPresent()) {
      logger.warn("Mission '{}' already exists, creation ignored", name);
      return;
    }
    TimeScale utc = TimeScalesFactory.getUTC();
    AbsoluteDate missionDate = new AbsoluteDate(values.get("LAUNCH_DATE").toString(), utc);
    try {
      Mission mission =
          MissionFactory.fromWizardValues(values, missionContext.getSelectedMissionType());
      MissionEntry missionEntry = new MissionEntry(mission);
      missionEntry.setScheduledDate(missionDate);
      missionContext.addMission(missionEntry);
    } catch (RuntimeException e) {
      // A bad wizard value must not crash the render loop; the mission is simply not created.
      logger.error("Mission creation failed for '{}': {}", name, e.getMessage());
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
