package com.smousseur.orbitlab.simulation;

import com.smousseur.orbitlab.core.SolarSystemBody;
import org.orekit.bodies.CelestialBody;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.ZipJarCrawler;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public final class OrekitService {
  private final AtomicBoolean initialized = new AtomicBoolean(false);

  private OrekitService() {
    DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
    manager.addProvider(new ZipJarCrawler(OrekitService.class.getClassLoader(), "orekit-data.zip"));
  }

  public void initialize() {
    if (!initialized.compareAndSet(false, true)) {
      return;
    }
    FramesFactory.getICRF();
  }

  public Frame icrf() {
    return FramesFactory.getICRF();
  }

  public CelestialBody body(SolarSystemBody body) {
    return switch (body) {
      case SUN -> CelestialBodyFactory.getSun();
      case MERCURY -> CelestialBodyFactory.getMercury();
      case VENUS -> CelestialBodyFactory.getVenus();
      case EARTH -> CelestialBodyFactory.getEarth();
      case MARS -> CelestialBodyFactory.getMars();
      case JUPITER -> CelestialBodyFactory.getJupiter();
      case SATURN -> CelestialBodyFactory.getSaturn();
      case URANUS -> CelestialBodyFactory.getUranus();
      case NEPTUNE -> CelestialBodyFactory.getNeptune();
      case PLUTO -> CelestialBodyFactory.getPluto();
    };
  }

  private static class Holder {
    private static final OrekitService INSTANCE = new OrekitService();
  }

  public static OrekitService get() {
    return Holder.INSTANCE;
  }
}
