package com.smousseur.orbitlab.simulation;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.orekit.bodies.CelestialBody;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.ZipJarCrawler;
import org.orekit.forces.ForceModel;
import org.orekit.forces.gravity.HolmesFeatherstoneAttractionModel;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.NormalizedSphericalHarmonicsProvider;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;

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

  public Frame itrf() {
    return FramesFactory.getITRF(IERSConventions.IERS_2010, true);
  }

  public Frame gcrf() {
    return FramesFactory.getGCRF();
  }

  public NumericalPropagator getDefaultPropagator() {
    double[] absTol = {1.0, 1.0, 1.0, 1e-3, 1e-3, 1e-3, 1e-2};
    double[] relTol = {1e-8, 1e-8, 1e-8, 1e-8, 1e-8, 1e-8, 1e-8};

    NumericalPropagator propagator =
        new NumericalPropagator(new DormandPrince853Integrator(0.1, 300.0, absTol, relTol));
    propagator.setOrbitType(OrbitType.CARTESIAN);
    propagator.setMu(Constants.WGS84_EARTH_MU);

    // GRAVITÉ ! Sans ça le satellite tombe en ligne droite
    propagator.addForceModel(getGravityModel());

    return propagator;
  }

  public ForceModel getGravityModel() {
    NormalizedSphericalHarmonicsProvider normalizedProvider =
        GravityFieldFactory.getNormalizedProvider(50, 50);
    return new HolmesFeatherstoneAttractionModel(itrf(), normalizedProvider);
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

  public OneAxisEllipsoid getEarthEllipsoid() {
    return new OneAxisEllipsoid(
        Constants.WGS84_EARTH_EQUATORIAL_RADIUS, Constants.WGS84_EARTH_FLATTENING, itrf());
  }

  private static class Holder {
    private static final OrekitService INSTANCE = new OrekitService();
  }

  public static OrekitService get() {
    return Holder.INSTANCE;
  }
}
