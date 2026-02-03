package com.smousseur.orbitlab.states.time;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.ui.clock.TimelineWidget;

public final class TimelineWidgetAppState extends BaseAppState {

  private final ApplicationContext context;
  private TimelineWidget widget;

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
