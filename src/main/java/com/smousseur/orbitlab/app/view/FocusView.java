package com.smousseur.orbitlab.app.view;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.EngineConfig;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import java.util.Set;

/**
 * Mutable state holder that tracks the current camera focus: which view mode is active and which
 * solar system body the camera is centered on.
 *
 * <p>Used by camera and rendering states to determine how the scene should be displayed.
 */
public class FocusView {
  private ViewMode mode = ViewMode.SOLAR;
  private SolarSystemBody body = SolarSystemBody.SUN;
  private MissionId focusedMission;
  private float cameraDistance;
  private final EngineConfig engineConfig;

  /**
   * Where a camera transition is heading, while it is still playing; {@code null} the rest of the
   * time.
   *
   * <p>A transition holds {@link #mode} and {@link #body} on their source values until its very
   * last frame, because the floating origin centres the rendered frame on them and moving that
   * ground mid-interpolation would break it. But a scene drawn strictly from the source is wrong
   * for the whole 2.5 s: the camera is already most of the way to somewhere else, and everything
   * keyed on the focus — the far clip floor, satellite visibility — would only catch up on the
   * final frame, as a pop. These two fields let those rules answer for <em>either</em> end.
   *
   * <p>Not everything may take the union. Anything drawn in the near viewport is positioned
   * relative to the frame's centre and is only correct for the source — see {@link
   * #isMissionVisible}.
   */
  private ViewMode pendingMode;

  private SolarSystemBody pendingBody;

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

  /**
   * Declares where a camera transition is heading, so the visibility rules can account for the
   * destination while the focus itself is still on the source.
   *
   * @param mode the view mode the transition will end in
   * @param body the body it will be centred on
   */
  public void beginTransition(ViewMode mode, SolarSystemBody body) {
    this.pendingMode = mode;
    this.pendingBody = body;
  }

  /** Clears the pending destination. Called when a transition completes or is cancelled. */
  public void endTransition() {
    this.pendingMode = null;
    this.pendingBody = null;
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
   * body is retained so the planet-scale render context (HUD markers, orbits, Earth-3D in the near
   * view) keeps working.
   *
   * @param missionId the id of the mission to follow
   * @param parentBody the body the mission is currently orbiting (e.g. Earth for LEO)
   */
  public void viewSpacecraft(MissionId missionId, SolarSystemBody parentBody) {
    this.mode = ViewMode.SPACECRAFT;
    this.body = parentBody;
    this.focusedMission = missionId;
  }

  /**
   * Returns the id of the currently focused mission, if any.
   *
   * @return the focused mission id, or {@code null} when not in spacecraft mode
   */
  public MissionId getFocusedMission() {
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
   * <p>Spacecraft mode counts as looking at the parent body, not as a third case: {@link
   * #viewSpacecraft} deliberately keeps {@code body} pointing at the planet the mission orbits, so
   * following a satellite of Earth from Earth orbit is the same viewing situation as focusing Earth
   * itself. The clause read {@code mode == PLANET} alone until spacecraft mode was added and this
   * predicate was not revisited, which is what made the Moon disappear on entering a mission.
   *
   * <p>A transition's destination counts too. Flying in from the solar view towards the Earth, the
   * Moon would otherwise stay hidden for the whole approach and appear on the last frame; it is
   * drawn from its own far-frame position, so it is correct to show at any point along the way.
   *
   * @param body the satellite body
   * @return true if the satellite should be visible
   */
  public boolean isSatelliteVisible(SolarSystemBody body) {
    return isPlanetScale() && (orbits(this.body, body) || orbits(pendingBody, body));
  }

  private static boolean orbits(SolarSystemBody centre, SolarSystemBody satellite) {
    return centre != null && (centre == satellite || centre == satellite.parent());
  }

  /**
   * Returns whether a mission whose trajectory flies about {@code arcBodies} should be visible given
   * the current view mode and focus.
   *
   * <p>A mission can be drawn about any body one of its arcs is centred on: PHY-4 / L5 converts
   * every vertex into the frame of whichever of them is being looked at, so a lunar transfer is
   * legitimate viewed from the Earth it launched from as well as from the Moon it arrives at.
   * Focusing a body it never flies about — Mars, or the Earth for a mission around Mars — must hide
   * it: there is no table for that body and its coordinates mean nothing there.
   *
   * <p><b>Why not the objective's body</b>, which is what this took until L5. With the view body
   * following the arc, a lunar mission (objective {@code MOON}, first arc {@code EARTH}) would be
   * hidden for the whole of its terrestrial ascent and appear only at the sphere-of-influence
   * crossing. The old rule does not merely become incomplete, it becomes wrong (spec {@code
   * docs/multi-corps/07-conception-L5.md} §5.4).
   *
   * <p>Missions around the focused body stay visible in spacecraft mode too, so following one of
   * them does not hide its siblings.
   *
   * <p><b>Unlike {@link #isSatelliteVisible}, this deliberately ignores a transition's
   * destination.</b> A mission is drawn in the near viewport, whose origin is wherever the floating
   * origin has centred the frame — the transition's <em>source</em> for its whole duration. Showing
   * a mission bound for the Earth while the frame is still centred on the Sun would draw its
   * trajectory around the Sun. It has to wait for the focus to actually flip.
   *
   * @param arcBodies the bodies the mission's trajectory can be expressed about, from {@code
   *     TrajectoryPolyline.arcBodies()}
   * @return true if the mission should be shown
   */
  public boolean isMissionVisible(Set<SolarSystemBody> arcBodies) {
    return isPlanetScaleMode(mode) && arcBodies.contains(this.body);
  }

  /**
   * Whether the planet-scale scene is drawn at all — counting a transition's destination, so the
   * scene being flown into is already set up rather than snapping in on the final frame.
   *
   * @return true if either end of the current view is planet scale
   */
  public boolean isPlanetScale() {
    return isPlanetScaleMode(mode) || isPlanetScaleMode(pendingMode);
  }

  private static boolean isPlanetScaleMode(ViewMode mode) {
    return mode == ViewMode.PLANET || mode == ViewMode.SPACECRAFT;
  }
}
