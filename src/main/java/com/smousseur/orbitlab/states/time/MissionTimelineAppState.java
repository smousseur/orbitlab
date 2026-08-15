package com.smousseur.orbitlab.states.time;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.ui.timeline.TimelineWidget;
import com.smousseur.orbitlab.ui.timeline.mission.MissionTimelineTrigger;
import com.smousseur.orbitlab.ui.timeline.mission.MissionTimelineWidget;
import java.util.Optional;

/**
 * Owns the mission timeline: its visibility, the mission it follows, and when its content is
 * rebuilt (spec {@code docs/navigation/02-timeline-mission.md} §10 and §12).
 *
 * <p><b>The track follows the telemetry focus; it never drives it.</b> The focus rules live in
 * {@code MissionDisplayPanelRules} and are not duplicated here. One consequence is that no
 * retention code is needed: {@code MissionEntry.publish()} clears the ephemeris and drops the
 * mission back to {@code DRAFT} on a recomputation, R9 then disarms the focus, and the widget
 * simply disappears — it never holds a window whose ephemeris no longer exists.
 *
 * <p><b>The ephemeris is read once per identity change, not per frame.</b> Segments, colours,
 * markers and graduations are rebuilt when the {@link MissionId} or the {@link MissionEphemeris}
 * instance changes; per frame only the playhead and its gap label move. The ephemeris is handed out
 * as one shared immutable instance, so an identity check is both sufficient and cheap.
 *
 * <p>No {@code getState(Class)}: everything comes through {@link ApplicationContext}.
 */
public final class MissionTimelineAppState extends BaseAppState {

  private final ApplicationContext context;

  /**
   * Condition 1 of §10.1, held here rather than on {@code MissionEntry}: it is a session display
   * preference, so it survives a change of followed mission, and it stays true while conditions 2
   * to 5 are false — returning to a followed mission finds the track as it was left, without a
   * second click.
   */
  private boolean toggleEnabled;

  private MissionTimelineWidget widget;
  private MissionTimelineTrigger trigger;

  // Nullable identity of what the widget currently draws. Held as plain nullable fields because
  // Optional is a return type only in this repository.
  private MissionId boundMissionId;
  private MissionEphemeris boundEphemeris;

  /**
   * @param context the application context
   */
  public MissionTimelineAppState(ApplicationContext context) {
    this.context = context;
  }

  @Override
  protected void initialize(Application app) {
    widget = new MissionTimelineWidget(context);
    widget.layoutAboveCapsule(app.getCamera().getWidth(), capsuleTop());
    widget.setVisible(false);

    trigger = new MissionTimelineTrigger(context.guiGraph().getTimelineNode());
    trigger.layout(app.getCamera().getWidth(), widget.bandBottom());
    trigger.setOnClick(
        () -> {
          toggleEnabled = !toggleEnabled;
          trigger.setActive(toggleEnabled);
        });
    trigger.setActive(false);
  }

  @Override
  public void update(float tpf) {
    Optional<MissionEntry> available =
        MissionTimelineVisibility.availableMission(context.missionContext());
    trigger.setPresent(available.isPresent());

    if (available.isEmpty() || !toggleEnabled) {
      widget.setVisible(false);
      return;
    }

    MissionEntry entry = available.get();
    MissionEphemeris ephemeris = entry.getEphemeris().orElse(null);
    if (ephemeris == null) {
      // availableMission already guarantees this, but the entry is written from the optimizer
      // thread and could in principle be cleared between the two reads.
      widget.setVisible(false);
      return;
    }

    if (boundEphemeris != ephemeris || !entry.id().equals(boundMissionId)) {
      widget.bind(entry, ephemeris);
      boundMissionId = entry.id();
      boundEphemeris = ephemeris;
    }

    widget.setVisible(true);
    widget.updateNow(context.clock().now());
  }

  @Override
  protected void cleanup(Application app) {
    if (widget != null) {
      widget.close();
      widget = null;
    }
    if (trigger != null) {
      trigger.close();
      trigger = null;
    }
    boundMissionId = null;
    boundEphemeris = null;
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}

  /**
   * The y of the time capsule's top edge, read from the capsule itself rather than re-derived. It
   * is time, and time is at the bottom of this screen: the top-left is taken by the application
   * menu and the display panel, the top-right by telemetry, and a track is a wide object only the
   * bottom band can hold without covering the scene.
   */
  private static float capsuleTop() {
    return TimelineWidget.BOTTOM_MARGIN_PX + TimelineWidget.CAPSULE_HEIGHT;
  }
}
