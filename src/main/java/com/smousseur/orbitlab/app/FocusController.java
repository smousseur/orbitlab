package com.smousseur.orbitlab.app;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.mission.FollowedObject;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.states.camera.CameraTransitionAppState;
import java.util.Objects;

/**
 * The single entry point for selecting one object of a mission to follow — its primary or a debris.
 * A selection points <em>both</em> the camera and the telemetry at that object, in one gesture: the
 * unified selection rule.
 *
 * <p>Three sites call it — clicking the primary spacecraft, clicking a debris, and the telemetry's
 * return-to-primary segment — so the parent-body resolution and the pairing of the two focuses live
 * here rather than being duplicated. It reads its collaborators from the {@link ApplicationContext}
 * at call time, because the camera transition state is registered after boot.
 */
public final class FocusController {

  private final ApplicationContext context;

  /**
   * Creates the controller.
   *
   * @param context the application context, read for the camera transition, the mission context and
   *     the focus view
   */
  public FocusController(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  /**
   * Follows {@code object}: flies the camera to it and points the telemetry at it. The parent body
   * the camera frames it against is the one the object is orbiting right now, read from its own
   * ephemeris, falling back to the currently focused body while that ephemeris is unavailable (a
   * mission being recomputed).
   *
   * @param object the object to follow
   */
  public void select(FollowedObject object) {
    Objects.requireNonNull(object, "object");
    SolarSystemBody parentBody = parentBodyOf(object);
    CameraTransitionAppState transition = context.cameraTransition();
    if (transition != null) {
      transition.requestSpacecraft(object, parentBody);
    }
    context.missionContext().setTelemetryFocus(object);
  }

  /**
   * Hands the camera and telemetry back to the mission's primary when the followed object is a
   * debris that has lost its handle — the "show debris" toggle switched off, or the clock rewound
   * before the piece's jettison. Meant to be called once per frame.
   *
   * <p>It reads the <em>camera</em> focus, which only settles at the end of a transition, so the
   * fallback acts on a stable focus and its own {@link #select} is never swallowed by the "a
   * transition is already playing" guard. It resolves the piece's jettison from its ephemeris;
   * while that ephemeris is momentarily gone (a mission being recomputed) the rewind case cannot be
   * judged and is treated as "not before jettison", leaving that transient to the mission-level
   * focus rules. Re-invoking it on every frame of the fly-back is harmless: the camera request is
   * ignored while the return transition plays, and the telemetry write is idempotent.
   */
  public void returnToPrimaryIfDetached() {
    if (!(context.focusView().getFocusedObject() instanceof FollowedObject.Debris debris)) {
      return;
    }
    boolean debrisVisible = context.displaySettings().isDebrisVisible();
    boolean beforeJettison =
        context
            .missionContext()
            .ephemerisOf(debris)
            .map(ephemeris -> context.clock().now().compareTo(ephemeris.startDate()) < 0)
            .orElse(false);
    if (handleLost(debris, debrisVisible, beforeJettison)) {
      select(new FollowedObject.Primary(debris.mission()));
    }
  }

  private SolarSystemBody parentBodyOf(FollowedObject object) {
    MissionContext missions = context.missionContext();
    return missions
        .ephemerisOf(object)
        .map(ephemeris -> ephemeris.displayPointAt(context.clock().now()).arc().body())
        .orElseGet(() -> context.focusView().getBody());
  }

  /**
   * Whether a followed object has lost its on-screen handle, so the focus must fall back to the
   * primary. Only a debris can: its handle is its icon, up exactly while the "show debris" toggle
   * is on and the clock is at or past the piece's jettison. The return therefore fires on the two
   * cases the icon is gone — the toggle switched off, or the clock rewound before the jettison.
   * Because this reads neither the impact instant nor the end of the debris' life, it never fires
   * on impact: a landed piece still carries its icon and stays followed.
   *
   * @param followed the object the camera currently follows
   * @param debrisVisible whether the global "show debris" toggle is on
   * @param beforeJettison whether the clock is before the debris' jettison (its ephemeris start)
   * @return {@code true} when a followed debris no longer has a handle
   */
  static boolean handleLost(
      FollowedObject followed, boolean debrisVisible, boolean beforeJettison) {
    return followed instanceof FollowedObject.Debris && (!debrisVisible || beforeJettison);
  }
}
