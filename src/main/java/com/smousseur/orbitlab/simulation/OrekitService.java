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
import org.orekit.forces.gravity.NewtonianAttraction;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.NormalizedSphericalHarmonicsProvider;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;

/**
 * Singleton service providing access to the Orekit astrodynamics library.
 *
 * <p>Encapsulates Orekit initialization, reference frame retrieval, celestial body lookup, and
 * numerical propagator creation with different fidelity levels (simple, optimization, default).
 * Access the singleton instance via {@link #get()}.
 */
public final class OrekitService {
  private final AtomicBoolean initialized = new AtomicBoolean(false);

  /** Cached gravity providers — created once, reused for every propagator. */
  private volatile ForceModel fullGravityModel;

  private volatile ForceModel lightGravityModel;

  private OrekitService() {}

  /**
   * Eagerly initializes the Orekit data context and loads reference frames.
   *
   * <p>This method is idempotent; subsequent calls after the first are no-ops.
   */
  public void initialize() {
    if (!initialized.compareAndSet(false, true)) {
      return;
    }
    try {
      DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
      manager.addProvider(
          new ZipJarCrawler(OrekitService.class.getClassLoader(), "orekit-data.zip"));
      FramesFactory.getICRF();
    } catch (RuntimeException e) {
      initialized.set(false);
      throw e;
    }
  }

  /**
   * Returns the International Celestial Reference Frame (ICRF).
   *
   * @return the ICRF frame
   */
  public Frame icrf() {
    return FramesFactory.getICRF();
  }

  /**
   * Returns the International Terrestrial Reference Frame (ITRF) using IERS 2010 conventions.
   *
   * @return the ITRF frame
   */
  public Frame itrf() {
    return FramesFactory.getITRF(IERSConventions.IERS_2010, true);
  }

  /**
   * Returns the Geocentric Celestial Reference Frame (GCRF).
   *
   * @return the GCRF frame
   */
  public Frame gcrf() {
    return FramesFactory.getGCRF();
  }

  /**
   * Creates a simple numerical propagator using only Newtonian two-body attraction.
   *
   * <p>Suitable for quick, low-fidelity orbit propagations where gravitational perturbations are
   * not needed.
   *
   * @return a new numerical propagator with Newtonian gravity only
   */
  public NumericalPropagator createSimplePropagator() {
    double minStep = 0.001;
    // Same bound as createOptimizationPropagator: a burn igniting mid-propagation with a
    // coast-sized trial step can drive the mass negative and crash the trial evaluation.
    double maxStep = 30.0;
    double absTol = 1e-8;
    double relTol = 1e-10;

    DormandPrince853Integrator integrator =
        new DormandPrince853Integrator(minStep, maxStep, absTol, relTol);

    NumericalPropagator propagator = new NumericalPropagator(integrator);
    propagator.addForceModel(new NewtonianAttraction(Constants.WGS84_EARTH_MU));
    return propagator;
  }

  /**
   * Creates a numerical propagator tuned for optimization loops.
   *
   * <p>Uses a low-degree (8x8) spherical harmonics gravity model, which is fast enough for
   * iterative trajectory optimization while remaining faithful to the runtime propagator.
   *
   * @return a new numerical propagator with 8x8 gravity field
   */
  public NumericalPropagator createOptimizationPropagator() {
    double minStep = 0.001;
    // Max step must stay below mass(ignition)/massFlow for every burn that can ignite
    // mid-propagation: after an ignition event the integrator restarts with the coast-sized
    // step, and a trial step that drives the mass negative makes Orekit throw DURING the trial
    // evaluation — before step-size control or any event detection (cutoff, depletion guard)
    // can react. Worst realistic case: the upper stage igniting a transfer burn (~16 t at
    // ~287 kg/s → 56 s); 30 s keeps a 2× margin at ~3× the coast stepping cost.
    double maxStep = 30.0;
    double absTol = 1e-8;
    double relTol = 1e-10;

    DormandPrince853Integrator integrator =
        new DormandPrince853Integrator(minStep, maxStep, absTol, relTol);

    NumericalPropagator propagator = new NumericalPropagator(integrator);
    propagator.setOrbitType(OrbitType.CARTESIAN);
    propagator.setMu(Constants.WGS84_EARTH_MU);
    propagator.addForceModel(getLightGravityModel());
    return propagator;
  }

  private ForceModel getFullGravityModel() {
    if (fullGravityModel == null) {
      synchronized (this) {
        if (fullGravityModel == null) {
          NormalizedSphericalHarmonicsProvider provider =
              GravityFieldFactory.getNormalizedProvider(50, 50);
          fullGravityModel = new HolmesFeatherstoneAttractionModel(itrf(), provider);
        }
      }
    }
    return fullGravityModel;
  }

  private ForceModel getLightGravityModel() {
    if (lightGravityModel == null) {
      synchronized (this) {
        if (lightGravityModel == null) {
          NormalizedSphericalHarmonicsProvider provider =
              GravityFieldFactory.getNormalizedProvider(8, 8);
          lightGravityModel = new HolmesFeatherstoneAttractionModel(itrf(), provider);
        }
      }
    }
    return lightGravityModel;
  }

  /**
   * Returns the Orekit {@link CelestialBody} corresponding to the given solar system body.
   *
   * @param body the solar system body identifier
   * @return the Orekit celestial body instance
   */
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
      case MOON -> CelestialBodyFactory.getMoon();
    };
  }

  /**
   * Creates a WGS84 Earth ellipsoid model in the ITRF frame.
   *
   * @return a new Earth ellipsoid with WGS84 parameters
   */
  public OneAxisEllipsoid getEarthEllipsoid() {
    return new OneAxisEllipsoid(
        Constants.WGS84_EARTH_EQUATORIAL_RADIUS, Constants.WGS84_EARTH_FLATTENING, itrf());
  }

  private static class Holder {
    private static final OrekitService INSTANCE = new OrekitService();
  }

  /**
   * Returns the singleton instance of the Orekit service.
   *
   * @return the shared {@code OrekitService} instance
   */
  public static OrekitService get() {
    return Holder.INSTANCE;
  }
}
