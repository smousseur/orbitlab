package com.smousseur.orbitlab.app.view;

import com.smousseur.orbitlab.core.SolarSystemBody;

public class FocusView {
  private ViewMode mode = ViewMode.SOLAR;
  private SolarSystemBody body = SolarSystemBody.SUN;

  public void reset() {
    this.mode = ViewMode.SOLAR;
    this.body = SolarSystemBody.SUN;
  }

  public void viewPlanet(SolarSystemBody body) {
    this.mode = ViewMode.PLANET;
    this.body = body;
  }

  public ViewMode getMode() {
    return mode;
  }

  public void setMode(ViewMode mode) {
    this.mode = mode;
  }

  public SolarSystemBody getBody() {
    return body;
  }

  public void setBody(SolarSystemBody body) {
    this.body = body;
  }
}
