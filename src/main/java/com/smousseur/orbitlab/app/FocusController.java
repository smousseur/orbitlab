package com.smousseur.orbitlab.app;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.mission.FollowedObject;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.states.camera.CameraTransitionAppState;
import java.util.Objects;

/**
 * The single entry point for selecting one object of a mission to follow — its primary or a debris.
 * A selection points <em>both</em> the camera and the telemetry at that object, in one gesture: the
 * unified rule of SEL-1 / L2 (spec {@code docs/selection-objets/04-conception-L2.md} §2.1).
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

  private SolarSystemBody parentBodyOf(FollowedObject object) {
    MissionContext missions = context.missionContext();
    return missions
        .ephemerisOf(object)
        .map(ephemeris -> ephemeris.displayPointAt(context.clock().now()).arc().body())
        .orElseGet(() -> context.focusView().getBody());
  }
}
