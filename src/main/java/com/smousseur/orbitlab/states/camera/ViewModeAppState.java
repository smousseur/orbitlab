package com.smousseur.orbitlab.states.camera;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.app.view.ViewMode;
import com.smousseur.orbitlab.core.SolarSystemBody;

import java.util.Objects;

public final class ViewModeAppState extends BaseAppState implements ActionListener {

  private static final String ACTION_TOGGLE_VIEW_MODE = "viewMode.toggle";
  private final ApplicationContext context;

  private InputManager inputManager;

  public ViewModeAppState(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  @Override
  protected void initialize(Application app) {
    this.inputManager = app.getInputManager();
    inputManager.addMapping(ACTION_TOGGLE_VIEW_MODE, new KeyTrigger(KeyInput.KEY_V));
    inputManager.addListener(this, ACTION_TOGGLE_VIEW_MODE);

    applyMode(context.focusView());
  }

  @Override
  protected void cleanup(Application app) {
    if (inputManager != null) {
      inputManager.removeListener(this);
      inputManager.deleteMapping(ACTION_TOGGLE_VIEW_MODE);
      inputManager = null;
    }
  }

  @Override
  protected void onEnable() {
    // nothing
  }

  @Override
  protected void onDisable() {
    // nothing
  }

  @Override
  public void onAction(String name, boolean isPressed, float tpf) {
    if (!ACTION_TOGGLE_VIEW_MODE.equals(name) || !isPressed) {
      return;
    }

    FocusView view = context.focusView();
    ViewMode mode = view.getMode();
    mode = (mode == ViewMode.SOLAR) ? ViewMode.PLANET : ViewMode.SOLAR;
    view.setMode(mode);
    applyMode(view);
  }

  private void applyMode(FocusView view) {
    switch (view.getMode()) {
      case SOLAR -> view.reset();
      case PLANET -> view.viewPlanet(SolarSystemBody.EARTH);
    }
  }
}
