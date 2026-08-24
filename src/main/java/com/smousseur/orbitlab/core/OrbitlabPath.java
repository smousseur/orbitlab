package com.smousseur.orbitlab.core;

import java.nio.file.Path;

public final class OrbitlabPath {
  private static final String USER_HOME = System.getProperty("user.home");
  private static final String HOME_DIR = PropertiesService.get().getProperty("home.path");
  private static final String DATASET_DIR = PropertiesService.get().getProperty("dataset.path");
  private static final String EPHEMERIS_DIR = PropertiesService.get().getProperty("ephemeris.path");
  private static final String ORBIT_DIR = PropertiesService.get().getProperty("orbits.path");
  private static final String SCENARIOS_DIR = PropertiesService.get().getProperty("scenarios.path");
  private static final Path DATASET_PATH = Path.of(USER_HOME, HOME_DIR, DATASET_DIR);

  public static final Path EPHEMERIS_PATH = DATASET_PATH.resolve(EPHEMERIS_DIR);
  public static final Path ORBITS_PATH = DATASET_PATH.resolve(ORBIT_DIR);
  public static final Path SCENARIOS_PATH = Path.of(USER_HOME, HOME_DIR, SCENARIOS_DIR);
}
