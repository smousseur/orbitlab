package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.LEOMission;
import com.smousseur.orbitlab.simulation.mission.MissionContext;
import com.smousseur.orbitlab.simulation.mission.MissionEntry;
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
    EventBus.UiNavigationEvent nav = context.eventBus().pollUiNavigation();

    if (nav != null) {
      switch (nav) {
        case EventBus.UiNavigationEvent.OpenMissionWizard open -> openWizard();
        case EventBus.UiNavigationEvent.CreateMission mission -> createMission(mission);
      }
    }
    ;
    if (widget != null) {
      widget.update(tpf, getApplication().getCamera());
    }
  }

  private void createMission(EventBus.UiNavigationEvent.CreateMission createMission) {
    MissionContext missionContext = context.missionContext();
    Map<String, Object> values = createMission.values();
    String name = String.valueOf(values.get("MISSION_NAME"));
    double targetAlt = Double.parseDouble(values.get("LEO_TARGET_ALT").toString());
    double latitude = Double.parseDouble(values.get("LAUNCH_SITE_LAT").toString());
    double longitude = Double.parseDouble(values.get("LAUNCH_SITE_LONG").toString());
    double altitude = Double.parseDouble(values.get("LAUNCH_SITE_ALT").toString());
    TimeScale utc = TimeScalesFactory.getUTC();
    AbsoluteDate missionDate = new AbsoluteDate(values.get("LAUNCH_DATE").toString(), utc);
    LEOMission mission = new LEOMission(name, targetAlt * 1000, latitude, longitude, altitude);
    MissionEntry missionEntry = new MissionEntry(mission);
    missionEntry.setScheduledDate(missionDate);
    missionContext.addMission(missionEntry);
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
