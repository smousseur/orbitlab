package com.smousseur.orbitlab.states.time;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.ui.timeline.TimelineWidget;

/**
 * Application state that manages the timeline GUI widget displayed at the bottom of the screen.
 *
 * <p>Creates and lays out a {@link TimelineWidget} during initialization, updates it each frame to
 * reflect the current simulation time and playback state, and properly closes the widget on cleanup
 * to release GUI resources.
 */
public final class TimelineWidgetAppState extends BaseAppState {

  private final ApplicationContext context;
  private TimelineWidget widget;

  /**
   * Creates a new timeline widget state.
   *
   * @param context the application context providing clock and GUI graph information
   */
  public TimelineWidgetAppState(ApplicationContext context) {
    this.context = context;
  }

  @Override
  protected void initialize(Application app) {
    widget = new TimelineWidget(context);
    widget.layoutBottomCenter(app.getCamera().getWidth());
  }

  @Override
  public void update(float tpf) {
    widget.update(tpf);
  }

  @Override
  protected void cleanup(Application app) {
    if (widget != null) {
      widget.close();
      widget = null;
    }
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
