package com.smousseur.orbitlab.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class PropertiesService {
  private final Properties properties = new Properties();

  private PropertiesService() {
    load();
    overloadValues();
  }

  private void load() {
    try (InputStream is = getClass().getResourceAsStream("/application.properties")) {
      properties.load(is);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load application.properties", e);
    }
  }

  private void overloadValues() {
    for (String key : properties.stringPropertyNames()) {
      String override = System.getProperty(key);
      if (override != null) {
        properties.setProperty(key, override);
      }
    }
  }

  public String getProperty(String key) {
    return properties.getProperty(key);
  }

  private static class Holder {
    private static final PropertiesService INSTANCE = new PropertiesService();
  }

  /**
   * Returns the singleton instance of the PropertiesService service.
   *
   * @return the shared {@code PropertiesService} instance
   */
  public static PropertiesService get() {
    return PropertiesService.Holder.INSTANCE;
  }
}
