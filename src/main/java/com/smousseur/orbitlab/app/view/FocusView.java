package com.smousseur.orbitlab.app.view;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.EngineConfig;

/**
 * Mutable state holder that tracks the current camera focus: which view mode is active and which
 * solar system body the camera is centered on.
 *
 * <p>Used by camera and rendering states to determine how the scene should be displayed.
 */
public class FocusView {
  private ViewMode mode = ViewMode.SOLAR;
  private SolarSystemBody body = SolarSystemBody.SUN;
  private String focusedMission;
  private float cameraDistance;
  private final EngineConfig engineConfig;

  public FocusView(EngineConfig engineConfig) {
    this.engineConfig = engineConfig;
  }

  /**
   * Tests if the view is currently focused on the specified body in planet view mode.
   *
   * @param body the body
   * @return true if body is focused
   */
  public boolean isFocused(SolarSystemBody body) {
    return this.mode == ViewMode.PLANET && this.body == body;
  }

  /** Resets the focus to the default state: solar view centered on the Sun. */
  public void reset() {
    this.mode = ViewMode.SOLAR;
    this.body = SolarSystemBody.SUN;
    this.focusedMission = null;
    this.cameraDistance = engineConfig.orbitCamera().defaultDistance();
  }

  /**
   * Switches the focus to planet view mode, centering on the specified body.
   *
   * @param body the solar system body to focus on
   */
  public void viewPlanet(SolarSystemBody body) {
    this.mode = ViewMode.PLANET;
    this.body = body;
    this.focusedMission = null;
  }

  /**
   * Switches the focus to spacecraft view mode, centering on a mission's spacecraft. The parent
   * body is retained so the planet-scale render context (HUD markers, orbits, Earth-3D in the
   * near view) keeps working.
   *
   * @param missionName the unique name of the mission to follow
   * @param parentBody the body the mission is currently orbiting (e.g. Earth for LEO)
   */
  public void viewSpacecraft(String missionName, SolarSystemBody parentBody) {
    this.mode = ViewMode.SPACECRAFT;
    this.body = parentBody;
    this.focusedMission = missionName;
  }

  /**
   * Returns the name of the currently focused mission, if any.
   *
   * @return the focused mission name, or {@code null} when not in spacecraft mode
   */
  public String getFocusedMission() {
    return focusedMission;
  }

  /**
   * Returns the current view mode.
   *
   * @return the active view mode
   */
  public ViewMode getMode() {
    return mode;
  }

  /**
   * Sets the view mode.
   *
   * @param mode the view mode to activate
   */
  public void setMode(ViewMode mode) {
    this.mode = mode;
  }

  /**
   * Returns the solar system body that the view is currently focused on.
   *
   * @return the focused body
   */
  public SolarSystemBody getBody() {
    return body;
  }

  /**
   * Sets the solar system body to focus on.
   *
   * @param body the body to focus on
   */
  public void setBody(SolarSystemBody body) {
    this.body = body;
  }

  /**
   * Gets camera distance.
   *
   * @return the camera distance
   */
  public float getCameraDistance() {
    return cameraDistance;
  }

  /**
   * Sets camera distance.
   *
   * @param cameraDistance the camera distance
   */
  public void setCameraDistance(float cameraDistance) {
    this.cameraDistance = cameraDistance;
  }

  /**
   * Returns whether a satellite body should be visible given the current view mode and focus.
   * Satellites are only visible when the camera is focused on themselves or on their parent body.
   *
   * @param body the satellite body
   * @return true if the satellite should be visible
   */
  public boolean isSatelliteVisible(SolarSystemBody body) {
    return mode == ViewMode.PLANET && (this.body == body || this.body == body.parent());
  }
}
