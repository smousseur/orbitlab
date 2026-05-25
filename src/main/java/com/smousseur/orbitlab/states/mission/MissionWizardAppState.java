package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.operation.GEOMission;
import com.smousseur.orbitlab.simulation.mission.operation.LEOMission;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.vehicle.LauncherType;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
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
    double latitude = Double.parseDouble(values.get("LAUNCH_SITE_LAT").toString());
    double longitude = Double.parseDouble(values.get("LAUNCH_SITE_LONG").toString());
    double altitude = Double.parseDouble(values.get("LAUNCH_SITE_ALT").toString());
    TimeScale utc = TimeScalesFactory.getUTC();
    AbsoluteDate missionDate = new AbsoluteDate(values.get("LAUNCH_DATE").toString(), utc);
    LauncherType launcherType = LauncherType.valueOf(values.get("LAUNCHER_TYPE").toString());
    double payloadMass = Double.parseDouble(values.get("PAYLOAD_MASS").toString());
    Spacecraft payload =
        new Spacecraft(payloadMass, 0, PropulsionSystem.getSpacecraftPropulsion());
    VehicleStack vehicle = Mission.buildVehicle(launcherType.toVehicleStack(), payload);
    if (missionContext.getSelectedMissionType() == MissionType.LEO) {
      double perigeeKm = Double.parseDouble(values.get("LEO_PERIGEE_ALT").toString());
      double apogeeKm = Double.parseDouble(values.get("LEO_APOGEE_ALT").toString());
      double perigeeAlt = Math.min(perigeeKm, apogeeKm) * 1000.0;
      double apogeeAlt = Math.max(perigeeKm, apogeeKm) * 1000.0;
      LEOMission mission =
          new LEOMission(name, perigeeAlt, apogeeAlt, latitude, longitude, altitude, vehicle);
      MissionEntry missionEntry = new MissionEntry(mission);
      missionEntry.setScheduledDate(missionDate);
      missionEntry.setModelPath(launcherType.modelPath());
      missionContext.addMission(missionEntry);
    } else if (missionContext.getSelectedMissionType() == MissionType.GEO) {
      double parkingKm = Double.parseDouble(values.get("GTO_PARKING_ALT").toString());
      GEOMission mission =
          new GEOMission(
              name,
              parkingKm * 1000.0,
              GEOMission.GEO_ALTITUDE,
              latitude,
              longitude,
              altitude,
              0.0,
              vehicle);
      MissionEntry missionEntry = new MissionEntry(mission);
      missionEntry.setScheduledDate(missionDate);
      missionEntry.setModelPath(launcherType.modelPath());
      missionContext.addMission(missionEntry);
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
