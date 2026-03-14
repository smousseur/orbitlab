package com.smousseur.orbitlab.states.camera;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.smousseur.orbitlab.app.ApplicationContext;
import java.util.Objects;

/**
 * Application state that handles toggling the camera view mode via keyboard input.
 *
 * <p>Listens for the 'R' key press and resets the focus view, cycling through available
 * view modes (e.g., solar system view, planet view). The view mode determines which
 * celestial body the camera orbits around and how the floating origin is applied.
 */
public final class ViewModeAppState extends BaseAppState implements ActionListener {

  private static final String ACTION_TOGGLE_VIEW_MODE = "viewMode.toggle";
  private final ApplicationContext context;

  private InputManager inputManager;

  /**
   * Creates a new view mode state.
   *
   * @param context the application context providing focus view management
   */
  public ViewModeAppState(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  @Override
  protected void initialize(Application app) {
    this.inputManager = app.getInputManager();
    inputManager.addMapping(ACTION_TOGGLE_VIEW_MODE, new KeyTrigger(KeyInput.KEY_R));
    inputManager.addListener(this, ACTION_TOGGLE_VIEW_MODE);

    context.focusView().reset();
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
    context.focusView().reset();
  }
}
