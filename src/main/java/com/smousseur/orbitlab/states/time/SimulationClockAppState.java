package com.smousseur.orbitlab.states.time;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.smousseur.orbitlab.app.SimulationClock;
import com.smousseur.orbitlab.app.SimulationContext;
import java.util.Objects;
import org.orekit.time.AbsoluteDate;

/**
 * JME AppState that: - drives the SimulationClock with the JME frame time (tpf) - binds basic
 * keyboard controls (space, +/- , arrows)
 *
 * <p>Keeps SimulationClock free of any JME dependencies.
 */
public final class SimulationClockAppState extends BaseAppState implements ActionListener {

  private static final String ACTION_TOGGLE_PLAY = "clock.togglePlay";
  private static final String ACTION_SPEED_UP = "clock.speedUp";
  private static final String ACTION_SPEED_DOWN = "clock.speedDown";
  private static final String ACTION_SPEED_RESET = "clock.speedReset";
  private static final String ACTION_SEEK_FORWARD = "clock.seekForward";
  private static final String ACTION_SEEK_BACKWARD = "clock.seekBackward";

  private final SimulationClock clock;

  private InputManager inputManager;

  // Simple tuning knobs
  private double speedFactor = 2.0; // multiply/divide speed by this factor
  private double seekStepSeconds = 60.0; // seek +/- N seconds

  public SimulationClockAppState(SimulationContext context) {
    this.clock = Objects.requireNonNull(context.clock(), "clock");
  }

  public SimulationClock clock() {
    return clock;
  }

  public void setSpeedFactor(double speedFactor) {
    if (!Double.isFinite(speedFactor) || speedFactor <= 1.0) {
      throw new IllegalArgumentException("speedFactor must be finite and > 1.0");
    }
    this.speedFactor = speedFactor;
  }

  public void setSeekStepSeconds(double seekStepSeconds) {
    if (!Double.isFinite(seekStepSeconds) || seekStepSeconds <= 0.0) {
      throw new IllegalArgumentException("seekStepSeconds must be finite and > 0");
    }
    this.seekStepSeconds = seekStepSeconds;
  }

  @Override
  protected void initialize(Application app) {
    inputManager = app.getInputManager();
    registerInputs();
  }

  @Override
  public void update(float tpf) {
    // Drives the clock from the JME time step
    clock.update(tpf);
  }

  @Override
  protected void cleanup(Application app) {
    unregisterInputs();
    inputManager = null;
  }

  @Override
  protected void onEnable() {
    // Nothing special for now
  }

  @Override
  protected void onDisable() {
    // Nothing special for now
  }

  private void registerInputs() {
    // Space: play/pause
    inputManager.addMapping(ACTION_TOGGLE_PLAY, new KeyTrigger(KeyInput.KEY_SPACE));

    // Speed: +/- (main keyboard)
    inputManager.addMapping(ACTION_SPEED_UP, new KeyTrigger(KeyInput.KEY_EQUALS));
    inputManager.addMapping(ACTION_SPEED_DOWN, new KeyTrigger(KeyInput.KEY_MINUS));

    // Speed: numpad +/-
    inputManager.addMapping(ACTION_SPEED_UP, new KeyTrigger(KeyInput.KEY_ADD));
    inputManager.addMapping(ACTION_SPEED_DOWN, new KeyTrigger(KeyInput.KEY_SUBTRACT));

    // Reset speed
    inputManager.addMapping(ACTION_SPEED_RESET, new KeyTrigger(KeyInput.KEY_BACK));

    // Seek
    inputManager.addMapping(ACTION_SEEK_FORWARD, new KeyTrigger(KeyInput.KEY_RIGHT));
    inputManager.addMapping(ACTION_SEEK_BACKWARD, new KeyTrigger(KeyInput.KEY_LEFT));

    inputManager.addListener(
        this,
        ACTION_TOGGLE_PLAY,
        ACTION_SPEED_UP,
        ACTION_SPEED_DOWN,
        ACTION_SPEED_RESET,
        ACTION_SEEK_FORWARD,
        ACTION_SEEK_BACKWARD);
  }

  private void unregisterInputs() {
    if (inputManager == null) {
      return;
    }
    inputManager.removeListener(this);

    // Safe even if already removed
    inputManager.deleteMapping(ACTION_TOGGLE_PLAY);
    inputManager.deleteMapping(ACTION_SPEED_UP);
    inputManager.deleteMapping(ACTION_SPEED_DOWN);
    inputManager.deleteMapping(ACTION_SPEED_RESET);
    inputManager.deleteMapping(ACTION_SEEK_FORWARD);
    inputManager.deleteMapping(ACTION_SEEK_BACKWARD);
  }

  @Override
  public void onAction(String name, boolean isPressed, float tpf) {
    if (!isPressed) {
      return; // act on key down only
    }

    switch (name) {
      case ACTION_TOGGLE_PLAY -> clock.setPlaying(!clock.isPlaying());

      case ACTION_SPEED_UP -> {
        double current = clock.speed();
        double next = (current == 0.0) ? 1.0 : current * speedFactor;
        clock.setSpeed(next);
      }

      case ACTION_SPEED_DOWN -> {
        double current = clock.speed();
        double next = current / speedFactor;
        clock.setSpeed(next);
      }

      case ACTION_SPEED_RESET -> clock.setSpeed(1.0);

      case ACTION_SEEK_FORWARD -> seekBy(+seekStepSeconds);

      case ACTION_SEEK_BACKWARD -> seekBy(-seekStepSeconds);

      default -> {
        // ignore
      }
    }
  }

  private void seekBy(double deltaSeconds) {
    AbsoluteDate now = clock.now();
    clock.seek(now.shiftedBy(deltaSeconds));
  }
}
