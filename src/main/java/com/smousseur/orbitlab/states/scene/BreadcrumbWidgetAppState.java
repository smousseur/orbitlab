package com.smousseur.orbitlab.states.scene;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.app.view.ViewMode;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.states.camera.CameraTransitionAppState;
import com.smousseur.orbitlab.ui.breadcrumb.BreadcrumbWidget;
import java.util.Objects;

/**
 * Owns the breadcrumb band at the top of the HUD: keeps it in step with the focus, and turns a
 * click on a segment into a camera request.
 *
 * <p>The focus is polled rather than listened to. {@link FocusView} publishes nothing, and the pair
 * that decides what the breadcrumb shows — mode and body — is two field reads a frame. The mission
 * being followed is deliberately <em>not</em> part of the key: it changes nothing on screen, since
 * missions have no segment, and the mode change that comes with it is enough to catch the one thing
 * that does change, whether the last segment is clickable ({@code docs/navigation/01-breadcrumb.md}
 * §4.5).
 */
public final class BreadcrumbWidgetAppState extends BaseAppState {

  private final ApplicationContext context;

  private BreadcrumbWidget widget;
  private ViewMode lastMode;
  private SolarSystemBody lastBody;

  /**
   * Creates the breadcrumb state.
   *
   * @param context the application context, read for the focus view, the GUI graph and the camera
   *     transition state
   */
  public BreadcrumbWidgetAppState(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  @Override
  protected void initialize(Application app) {
    widget = new BreadcrumbWidget(this::selectSolar, this::selectBody);
    widget.attachTo(context.guiGraph().getBreadcrumbNode());
    widget.layoutTopBand(app.getCamera().getWidth(), app.getCamera().getHeight());
    syncFocus();
  }

  @Override
  public void update(float tpf) {
    syncFocus();
  }

  @Override
  protected void cleanup(Application app) {
    if (widget != null) {
      widget.close();
      widget = null;
    }
    lastMode = null;
    lastBody = null;
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}

  /** Rebuilds the chain when, and only when, the focus key has changed. */
  private void syncFocus() {
    FocusView focusView = context.focusView();
    ViewMode mode = focusView.getMode();
    SolarSystemBody body = focusView.getBody();
    if (mode == lastMode && body == lastBody) {
      return;
    }
    lastMode = mode;
    lastBody = body;
    widget.setFocus(mode, body);
  }

  /**
   * Goes back out to the whole-system view. Routed through the transition state rather than calling
   * {@code FocusView.reset()} directly, so the segment is strictly the {@code R} key: same
   * animation, and the same guards against a request arriving mid-transition or for the view
   * already in effect.
   */
  private void selectSolar() {
    CameraTransitionAppState transition = context.cameraTransition();
    if (transition != null) {
      transition.requestSolar();
    }
  }

  /** Focuses a body, exactly as clicking its billboard in the 3D view does. */
  private void selectBody(SolarSystemBody body) {
    CameraTransitionAppState transition = context.cameraTransition();
    if (transition != null) {
      transition.requestPlanet(body);
    }
  }
}
