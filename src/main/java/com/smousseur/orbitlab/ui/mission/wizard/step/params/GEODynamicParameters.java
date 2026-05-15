package com.smousseur.orbitlab.ui.mission.wizard.step.params;

import com.simsilica.lemur.Container;

import java.util.Map;

public class GEODynamicParameters extends DynamicParameters {
  public GEODynamicParameters() {
    this.container = createContainer();
  }

  @Override
  protected Container createContainer() {
    return new Container();
  }

  @Override
  public void update(float tpf) {}

  @Override
  public Map<String, Object> getDynamicValues() {
    return Map.of();
  }
}
