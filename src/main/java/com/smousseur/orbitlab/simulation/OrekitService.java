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

  // ── Integrator max-step sizing (late-ignition invariant, spec 06 I6 / bilan 08 §3.1) ──
  //
  // A burn igniting mid-propagation restarts the integrator with the coast-sized step; if that
  // trial step can drive the mass negative, Orekit throws DURING the trial evaluation — before
  // step-size control or any event detector (burn cutoff, depletion guard) can react. The max step
  // must therefore stay below mass(ignition)/massFlow for every burn that can ignite mid-flight.

  /** Integrator max step for a burn-free (coast) propagation: nothing can ignite, so step large. */
  public static final double COAST_MAX_STEP = 300.0;

  /**
   * Conservative max step for a propagation that may ignite an unspecified burn: the historical
   * invariant sized on the worst realistic mid-flight ignition (Falcon Heavy upper stage, ~16 t at
   * ~287 kg/s → 56 s), kept as the no-argument factory default so callers that add burns after
   * creation stay safe.
   */
  public static final double SAFE_MAX_STEP = 30.0;

  private static final double MAX_STEP_SAFETY_FACTOR = 1.5;

  /**
   * Ignition parameters of a burn that can start mid-propagation, used to size the integrator max
   * step via {@link #burnLimitedMaxStep}.
   *
   * @param thrustNewton engine thrust (N)
   * @param ispSeconds specific impulse (s)
   * @param massAtIgnitionKg spacecraft mass when the burn ignites (kg)
   */
  public record BurnSpec(double thrustNewton, double ispSeconds, double massAtIgnitionKg) {
    /** Time to consume the whole ignition mass at constant flow — the invariant's bound (s). */
    double burnToZeroSeconds() {
      double massFlow = thrustNewton / (ispSeconds * Constants.G0_STANDARD_GRAVITY);
      return massAtIgnitionKg / massFlow;
    }
  }

  /**
   * Largest integrator max step that keeps the late-ignition invariant for the given burns.
   *
   * <p>With no burns it returns {@link #COAST_MAX_STEP} (nothing can ignite). With burns it never
   * exceeds the proven {@link #SAFE_MAX_STEP} cap — so the calibrated Falcon Heavy stepping is
   * unchanged — and tightens below it only when a burn is violent enough that a full {@code
   * SAFE_MAX_STEP} trial step at ignition would consume more than {@code 1/1.5} of its mass. That
   * auto-tightening is what keeps a lighter I7 load safe: once the mass at ignition drops below
   * about {@code SAFE_MAX_STEP × massFlow}, the hardcoded cap would let a single coast-sized trial
   * step drive the mass negative and crash the trial evaluation (spec 06 I6, bilan 08 §3.1).
   *
   * @param burns the burns that may ignite during the propagation
   * @return the largest safe integrator max step in seconds
   */
  public static double burnLimitedMaxStep(BurnSpec... burns) {
    double limit = burns.length == 0 ? COAST_MAX_STEP : SAFE_MAX_STEP;
    for (BurnSpec burn : burns) {
      limit = Math.min(limit, burn.burnToZeroSeconds() / MAX_STEP_SAFETY_FACTOR);
    }
    return limit;
  }

  /**
   * Creates a simple numerical propagator using only Newtonian two-body attraction, with the
   * conservative {@link #SAFE_MAX_STEP} max step.
   *
   * <p>Suitable for quick, low-fidelity orbit propagations where gravitational perturbations are
   * not needed.
   *
   * @return a new numerical propagator with Newtonian gravity only
   */
  public NumericalPropagator createSimplePropagator() {
    return createSimplePropagator(SAFE_MAX_STEP);
  }

  /**
   * Creates a simple Newtonian propagator with an explicit integrator max step. Size {@code maxStep}
   * with {@link #burnLimitedMaxStep} from the burns the caller will configure, or use {@link
   * #COAST_MAX_STEP} for a burn-free coast.
   *
   * @param maxStep integrator maximum step in seconds (must satisfy the late-ignition invariant)
   * @return a new numerical propagator with Newtonian gravity only
   */
  public NumericalPropagator createSimplePropagator(double maxStep) {
    double minStep = 0.001;
    double absTol = 1e-8;
    double relTol = 1e-10;

    DormandPrince853Integrator integrator =
        new DormandPrince853Integrator(minStep, maxStep, absTol, relTol);

    NumericalPropagator propagator = new NumericalPropagator(integrator);
    propagator.addForceModel(new NewtonianAttraction(Constants.WGS84_EARTH_MU));
    return propagator;
  }

  /**
   * Creates a numerical propagator tuned for optimization loops, with the conservative {@link
   * #SAFE_MAX_STEP} max step.
   *
   * <p>Uses a low-degree (8x8) spherical harmonics gravity model, which is fast enough for
   * iterative trajectory optimization while remaining faithful to the runtime propagator.
   *
   * @return a new numerical propagator with 8x8 gravity field
   */
  public NumericalPropagator createOptimizationPropagator() {
    return createOptimizationPropagator(SAFE_MAX_STEP);
  }

  /**
   * Creates an optimization-fidelity (8×8 gravity) propagator with an explicit integrator max step.
   * Size {@code maxStep} with {@link #burnLimitedMaxStep} from the burns the caller will configure,
   * or use {@link #COAST_MAX_STEP} for a burn-free coast.
   *
   * @param maxStep integrator maximum step in seconds (must satisfy the late-ignition invariant)
   * @return a new numerical propagator with 8x8 gravity field
   */
  public NumericalPropagator createOptimizationPropagator(double maxStep) {
    double minStep = 0.001;
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
