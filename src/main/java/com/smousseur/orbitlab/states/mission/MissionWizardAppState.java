package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardWidget;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MissionWizardAppState extends BaseAppState implements ActionListener {
  private static final Logger logger = LogManager.getLogger(MissionWizardAppState.class);
  private static final String ACTION_DEBUG_CYCLE = "wizard.debug.cycle";

  private final ApplicationContext context;
  private MissionWizardWidget widget;
  private InputManager inputManager;

  public MissionWizardAppState(ApplicationContext context) {
    this.context = context;
  }

  public boolean isWizardVisible() {
    return widget != null && widget.isVisible();
  }

  @Override
  protected void initialize(Application app) {
    this.inputManager = app.getInputManager();
    inputManager.addMapping(ACTION_DEBUG_CYCLE, new KeyTrigger(KeyInput.KEY_F8));
    inputManager.addListener(this, ACTION_DEBUG_CYCLE);
  }

  @Override
  protected void cleanup(Application app) {
    closeWizard();
    if (inputManager.hasMapping(ACTION_DEBUG_CYCLE)) {
      inputManager.deleteMapping(ACTION_DEBUG_CYCLE);
    }
    inputManager.removeListener(this);
    this.inputManager = null;
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}

  @Override
  public void update(float tpf) {
    EventBus.UiNavigation nav = context.eventBus().pollUiNavigation();
    if (nav == EventBus.UiNavigation.OPEN_MISSION_WIZARD) {
      openWizard();
    }
    if (widget != null) {
      widget.update(tpf, getApplication().getCamera());
    }
  }

  @Override
  public void onAction(String name, boolean isPressed, float tpf) {
    if (ACTION_DEBUG_CYCLE.equals(name) && isPressed && widget != null) {
      widget.cycleStep();
    }
  }

  private void openWizard() {
    if (widget != null) return;
    widget = new MissionWizardWidget(context);
    widget.setOnCancel(this::closeWizard);
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
