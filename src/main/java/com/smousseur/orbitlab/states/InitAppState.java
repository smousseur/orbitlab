package com.smousseur.orbitlab.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.simulation.source.DatasetEphemerisSource;
import com.smousseur.orbitlab.simulation.source.EphemerisSourceRegistry;

import java.nio.file.Path;

public class InitAppState extends BaseAppState {
  private DatasetEphemerisSource datasetSource;

  @Override
  protected void initialize(Application app) {
    Path datasetDir = Path.of("dataset", "ephemeris");
    datasetSource = new DatasetEphemerisSource(datasetDir, /*chunksInCachePerBody*/ 32);
    EphemerisSourceRegistry.publish(datasetSource);
  }

  @Override
  protected void cleanup(Application app) {
    datasetSource.close();
    EphemerisSourceRegistry.clear(datasetSource);
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
