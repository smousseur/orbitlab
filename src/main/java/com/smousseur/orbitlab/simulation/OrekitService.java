package com.smousseur.orbitlab.simulation;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.flight.DragContext;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hipparchus.CalculusFieldElement;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.orekit.bodies.CelestialBody;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.ZipJarCrawler;
import org.orekit.forces.ForceModel;
import org.orekit.forces.drag.DragForce;
import org.orekit.forces.drag.IsotropicDrag;
import org.orekit.forces.gravity.HolmesFeatherstoneAttractionModel;
import org.orekit.forces.gravity.NewtonianAttraction;
import org.orekit.forces.gravity.ThirdBodyAttraction;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.NormalizedSphericalHarmonicsProvider;
import org.orekit.frames.FieldTransform;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.Transform;
import org.orekit.frames.TransformProvider;
import org.orekit.models.earth.atmosphere.Atmosphere;
import org.orekit.models.earth.atmosphere.HarrisPriester;
import org.orekit.models.earth.atmosphere.NRLMSISE00;
import org.orekit.models.earth.atmosphere.NRLMSISE00InputParameters;
import org.orekit.models.earth.atmosphere.data.CssiSpaceWeatherData;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;
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

  /** Gravity providers, cached per central body — created once, reused for every propagator. */
  private final Map<SolarSystemBody, ForceModel> gravityModels = new ConcurrentHashMap<>();

  /**
   * Third-body attractions, cached per perturbing body. Same contract as {@link #gravityModels}.
   */
  private final Map<SolarSystemBody, ForceModel> thirdBodyModels = new ConcurrentHashMap<>();

  /**
   * Body-centred frames with ICRF axes, cached per body. Same contract as {@link #gravityModels}.
   */
  private final Map<SolarSystemBody, Frame> bodyCentredFrames = new ConcurrentHashMap<>();

  /** Atmospheres, cached per (model, central body). Same contract as {@link #gravityModels}. */
  private final Map<AtmosphereKey, Atmosphere> atmospheres = new ConcurrentHashMap<>();

  /**
   * The identity of a cached atmosphere. An {@link AtmosphereModel} alone does not name one: an
   * {@code Atmosphere} is built against a body shape, so the same model around two bodies is two
   * objects — which is exactly what lets a {@link DragContext} carry only the enum and stay correct
   * across a sphere-of-influence crossing (spec {@code docs/atmosphere/04-conception-L1.md} §1.2).
   */
  private record AtmosphereKey(AtmosphereModel model, SolarSystemBody body) {}

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
   * Pays, on the calling thread, the one-off cost of building the terrestrial frame chain.
   *
   * <p><b>Why this method exists.</b> {@link #initialize()} ends on {@link
   * FramesFactory#getICRF()}, which is celestial and cheap; the <em>terrestrial</em> chain is
   * neither. The first {@link #itrf()} makes Orekit load and index the Earth-orientation parameters
   * that ITRF hangs on, and that happens exactly once per JVM — on whichever thread happens to ask
   * first. Measured here, first call each:
   *
   * <ul>
   *   <li>{@link #initialize()} — 784 ms;
   *   <li><b>{@link #itrf()} — 8 483 ms</b>, re-measured at 8 199 ms through this method;
   *   <li>{@link #gcrf()} afterwards — 0 ms;
   *   <li>{@link #getEarthEllipsoid()} afterwards — 2.8 ms.
   * </ul>
   *
   * <p><b>Which thread pays is the whole point.</b> The three users of {@link #itrf()} — the Earth
   * gravity field, an Earth mission, the launch window criterion — all reach it lazily, so the bill
   * lands wherever the first of them runs. In this application that has always been the render
   * thread: before the launch window feature it was an Earth mission at creation, and the feature
   * only moved the same eight and a half seconds earlier, into the wizard. Eight seconds on the
   * render thread is a frozen window, so the application starts this off it (see {@code
   * OrbitLabApplication.startFrameWarmUp}). Nothing here is an optimisation of the arithmetic:
   * every path warmed works without it, merely slowly, once.
   *
   * <p><b>Synchronous, and deliberately not spawned.</b> This method does not start a thread and is
   * not called from {@link #initialize()}: tests initialize Orekit in {@code @BeforeAll} and share
   * a JVM, where a background thread touching these caches would deepen {@code BUG-7} (cross-test
   * contamination) rather than help. The caller chooses the thread.
   */
  public void warmUpFrames() {
    itrf();
    getEarthEllipsoid();
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
   * The inertial frame centred on the given body, with <b>ICRF axes</b>: a pure translation of GCRF
   * to that body's centre. This is the frame a trajectory arc around that body is expressed in
   * (PHY-4 / L4, spec {@code docs/multi-corps/06-conception-L4.md} §2.2).
   *
   * <p><b>Deliberately not {@code CelestialBody.getInertiallyOrientedFrame()}</b>, although that is
   * Orekit's own selenocentric inertial frame. Measured: its axes are the IAU lunar pole, 22.08°
   * from GCRF on 2026-03-01, and drifting (22.08° → 22.43° → 22.94° over two years). Three
   * consequences decided against it:
   *
   * <ul>
   *   <li>{@code JmeVectorAdapter.toJmeBodyRelativePosition} applies {@code icrfToJme}
   *       unconditionally and documents its input as ICRF axes: a lunar arc in pole axes would be
   *       drawn 22° askew, silently;
   *   <li>a pure translation makes the arc conversion <b>exact</b> — round trip measured at 0 m,
   *       against 2.4e-7 m through Orekit's frame;
   *   <li>and Orekit's frame declares {@code rotationRate = 0} while its axes move with the date,
   *       so a propagation integrated there and converted back lands 2 974 m away after 6 h, where
   *       this frame lands 0.246 m away. The 3 km are bookkeeping, not physics.
   * </ul>
   *
   * <p>The Earth resolves to {@link #gcrf()}, which <em>is</em> the geocentric realisation of ICRF:
   * one method serves both bodies and the Earth arc keeps the very same frame instance, which is
   * what keeps the L1 gate's strict equality achievable. That branch does not re-enter the map —
   * the {@code computeIfAbsent} warning of spec L1 §7 applies and must not be "tidied" away.
   *
   * @param body the body to centre on
   * @return the body-centred, ICRF-oriented inertial frame
   */
  public Frame bodyCentredIcrfFrame(SolarSystemBody body) {
    if (body == SolarSystemBody.EARTH) {
      return gcrf();
    }
    return bodyCentredFrames.computeIfAbsent(
        body, b -> new Frame(gcrf(), new BodyCentredTranslation(body(b)), b + "/ICRF", true));
  }

  /**
   * Translates GCRF to a celestial body's centre without touching the axes. Velocity and
   * acceleration of the origin travel with the transform, which is what makes the frame usable for
   * propagation: the origin's acceleration is accounted for by the indirect term of {@link
   * ThirdBodyAttraction}, exactly as it is in Orekit's own body-centred frames.
   */
  private record BodyCentredTranslation(CelestialBody body) implements TransformProvider {

    @Override
    public Transform getTransform(AbsoluteDate date) {
      return new Transform(date, body.getPVCoordinates(date, FramesFactory.getGCRF()).negate());
    }

    @Override
    public <T extends CalculusFieldElement<T>> FieldTransform<T> getTransform(
        FieldAbsoluteDate<T> date) {
      return new FieldTransform<>(
          date, body.getPVCoordinates(date, FramesFactory.getGCRF()).negate());
    }
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
   * Creates a point-mass-gravity propagator around the given central body: a {@link
   * NewtonianAttraction} for the central term, plus whatever else the context declares.
   *
   * <p><b>It mounts the drag too</b>, although it has no caller in {@code src/main} — only tests
   * use it. Letting it ignore a {@link DragContext} would re-open the one failure mode this lot is
   * built to exclude: drag asked for and not flown (spec {@code
   * docs/atmosphere/04-conception-L1.md} §3.4).
   *
   * @param context the flight context: central body, third bodies, and any atmosphere
   * @param maxStep integrator maximum step in seconds (must satisfy the late-ignition invariant)
   * @return a new numerical propagator with point-mass gravity
   */
  public NumericalPropagator createTestPropagator(FlightContext context, double maxStep) {
    double minStep = 0.001;
    double absTol = 1e-8;
    double relTol = 1e-10;

    DormandPrince853Integrator integrator =
        new DormandPrince853Integrator(minStep, maxStep, absTol, relTol);

    NumericalPropagator propagator = new NumericalPropagator(integrator);
    propagator.addForceModel(new NewtonianAttraction(context.gravity().mu()));
    addPerturbers(propagator, context.gravity());
    addDrag(propagator, context);
    return propagator;
  }

  /**
   * Creates an optimization-fidelity (8×8 gravity) propagator around the given central body.
   *
   * <p><b>The order of the three calls below is load-bearing</b> — {@code setOrbitType}, then
   * {@code setMu}, then {@code addForceModel}. Orekit is not indifferent to it everywhere, and a
   * tidier-looking permutation would cost the L0 baseline (spec {@code
   * docs/multi-corps/03-conception-L1.md} §7). L2 extends that by one line: the central field is
   * added first, then the perturbers in the canonical order of the context's {@code EnumSet}.
   *
   * <p><b>A body with no harmonic field gets none</b>, and its central term is still whole: {@code
   * setMu} mounts a {@link NewtonianAttraction} of its own when no attraction model is present, and
   * that term is what the missing non-central field would have complemented. Before PHY-4 / L4 this
   * call was unconditional, so a lunar context mounted the <b>Earth's</b> 8×8 field expressed in
   * ITRF and propagated without complaint (spec {@code docs/multi-corps/06-conception-L4.md}
   * §1.2-D) — the one defect of that lot able to produce a plausible, wrong trajectory.
   *
   * <p><b>The drag comes last</b>, after the central field and the third bodies. An absent {@link
   * DragContext} adds nothing at all — not a zero force — so a drag-off propagator has exactly the
   * force list it had before PHY-1, in the same order, and the lot's non-regression is a property
   * of the type rather than a measurement (spec {@code docs/atmosphere/04-conception-L1.md} §1.1).
   *
   * @param context the flight context: central body, third bodies, and any atmosphere
   * @param maxStep integrator maximum step in seconds (must satisfy the late-ignition invariant)
   * @return a new numerical propagator with the body's gravity field
   */
  public NumericalPropagator createOptimizationPropagator(FlightContext context, double maxStep) {
    double minStep = 0.001;
    double absTol = 1e-8;
    double relTol = 1e-10;

    DormandPrince853Integrator integrator =
        new DormandPrince853Integrator(minStep, maxStep, absTol, relTol);

    GravitationalContext gravity = context.gravity();
    NumericalPropagator propagator = new NumericalPropagator(integrator);
    propagator.setOrbitType(OrbitType.CARTESIAN);
    propagator.setMu(gravity.mu());
    ForceModel nonCentralField = nonCentralField(gravity.body());
    if (nonCentralField != null) {
      propagator.addForceModel(nonCentralField);
    }
    addPerturbers(propagator, gravity);
    addDrag(propagator, context);
    return propagator;
  }

  /**
   * Adds one {@link ThirdBodyAttraction} per body the context declares as a perturber, in the
   * canonical order the context guarantees.
   *
   * <p>An empty perturber set adds nothing at all — not an identity force, not a zero term. That is
   * what makes L2's non-regression structural rather than measured: an unperturbed propagation has
   * the very same force list, in the very same order, as before the lot (spec {@code
   * docs/multi-corps/04-conception-L2.md} §4.1).
   *
   * @param propagator the propagator being built
   * @param context the gravitational context whose perturbers are to be mounted
   */
  private void addPerturbers(NumericalPropagator propagator, GravitationalContext context) {
    for (SolarSystemBody perturber : context.perturbers()) {
      propagator.addForceModel(getThirdBodyModel(perturber));
    }
  }

  /**
   * Adds the {@link DragForce} the context asks for — and nothing when it asks for none.
   *
   * <p><b>Two ways to mount nothing, and both are correct.</b> A context with no {@link
   * DragContext} is a mission flown in vacuum. A context that carries one around a body with no
   * atmosphere — a lunar arc whose drag context crossed the boundary with it — resolves to a {@code
   * null} atmosphere and mounts nothing either. That second case is what makes the aerodynamic half
   * portable across a sphere-of-influence crossing instead of a datum to be dropped and mourned
   * (spec {@code docs/atmosphere/04-conception-L1.md} §1.2).
   *
   * @param propagator the propagator being built
   * @param context the flight context whose drag is to be mounted
   */
  private void addDrag(NumericalPropagator propagator, FlightContext context) {
    if (!context.hasDrag()) {
      return;
    }
    DragContext drag = context.drag();
    Atmosphere atmosphere = atmosphereFor(drag.model(), context.gravity());
    if (atmosphere == null) {
      return;
    }
    propagator.addForceModel(
        new DragForce(
            atmosphere,
            new IsotropicDrag(drag.aero().crossSection(), drag.aero().dragCoefficient())));
  }

  /**
   * The atmosphere of the given model around the given central body, resolved once and shared by
   * every propagator, or {@code null} when that body has no atmosphere.
   *
   * <p><b>Only the Earth has one here</b>, exactly as only the Earth has a harmonic field: the
   * {@code null} is the honest answer for the Moon, and it is the single line that lets a drag
   * context travel unchanged across an SOI boundary. Same shape as {@link
   * #nonCentralField(SolarSystemBody)}, deliberately.
   *
   * <p><b>The shared instance is an invariant, not an optimisation</b>, for the same reason the
   * gravity models are: CMA-ES evaluates candidates in parallel, and {@code computeIfAbsent}
   * guarantees one instance per key rather than merely a coherent value. It matters more here —
   * {@link NRLMSISE00} is driven by a space-weather file that would otherwise be parsed once per
   * propagator.
   *
   * <p>The Earth test sits <em>outside</em> the mapping function, as it does for the gravity field:
   * a mapping function must not touch the map it is computing into.
   *
   * @param model the atmosphere model asked for; never {@link AtmosphereModel#NONE}, which {@link
   *     DragContext} refuses to hold
   * @param gravity the gravitational context the atmosphere is built against
   * @return the shared atmosphere, or {@code null} when the central body has none
   */
  private Atmosphere atmosphereFor(AtmosphereModel model, GravitationalContext gravity) {
    if (gravity.body() != SolarSystemBody.EARTH) {
      return null;
    }
    return atmospheres.computeIfAbsent(
        new AtmosphereKey(model, gravity.body()),
        key ->
            switch (key.model()) {
              case HARRIS_PRIESTER ->
                  new HarrisPriester(body(SolarSystemBody.SUN), gravity.shape());
              case NRLMSISE ->
                  new NRLMSISE00(spaceWeather(), body(SolarSystemBody.SUN), gravity.shape());
              case NONE ->
                  throw new IllegalStateException(
                      "NONE never reaches here: DragContext refuses to hold it");
            });
  }

  /**
   * The solar and geomagnetic indices {@link NRLMSISE00} is driven by, read from the CSSI space
   * weather file carried by {@code orekit-data.zip}.
   *
   * @return the input parameters for NRLMSISE-00
   */
  private NRLMSISE00InputParameters spaceWeather() {
    return new CssiSpaceWeatherData(CssiSpaceWeatherData.DEFAULT_SUPPORTED_NAMES);
  }

  /**
   * The non-central part of the given body's gravity potential — its 8×8 harmonic field — resolved
   * once and shared by every propagator, or {@code null} when that body has no field at all.
   *
   * <p><b>Only the Earth has one here, and that is a property of the data, not a shortcut.</b>
   * {@code orekit-data.zip} carries exactly one potential file, {@code Potential/eigen-6s.gfc},
   * which is terrestrial. Asking {@link GravityFieldFactory} for a lunar field would silently hand
   * back that same Earth model expressed in ITRF — measured in spec {@code
   * docs/multi-corps/06-conception-L4.md} §1.2-D, where it propagated a lunar arc without raising
   * anything. The {@code null} is therefore the honest answer, and {@link
   * #createOptimizationPropagator} is the single caller that reads it.
   *
   * <p><b>The shared instance is an invariant, not an optimisation</b> (spec {@code
   * docs/multi-corps/03-conception-L1.md} §3.3): it is what makes bit-for-bit equality with the L0
   * baseline achievable, and therefore what lets the gate demand a zero tolerance. {@code
   * computeIfAbsent} is atomic and evaluates the mapping function at most once per key, which is
   * exactly that guarantee — do not replace it with an explicit lock or a pre-warm.
   *
   * <p>The mapping function must not modify the map, so the Earth test sits <em>outside</em> it: a
   * resolution that fell back to another body from inside would deadlock (spec L1 §7).
   *
   * @param body the central body whose non-central field is requested
   * @return the shared force model for that body, or {@code null} if it has no harmonic field
   */
  private ForceModel nonCentralField(SolarSystemBody body) {
    if (body != SolarSystemBody.EARTH) {
      return null;
    }
    return gravityModels.computeIfAbsent(
        body,
        b -> {
          NormalizedSphericalHarmonicsProvider provider =
              GravityFieldFactory.getNormalizedProvider(8, 8);
          return new HolmesFeatherstoneAttractionModel(itrf(), provider);
        });
  }

  /**
   * The third-body attraction of the given body, resolved once and shared by every propagator.
   *
   * <p>Cached for the same reason as {@link #getGravityModel}, not for speed: CMA-ES explorations
   * run in parallel ({@code CMAESTrajectoryOptimizer:314}), and {@code computeIfAbsent} guarantees
   * a single instance per body rather than merely a coherent value.
   *
   * <p><b>A constraint this hands to L4:</b> {@link ThirdBodyAttraction} expresses the perturbing
   * body's position in the propagation frame and assumes that frame is centred on the central body.
   * Every propagation here is Earth-centred GCRF, so it holds today. A lunar arc still propagated
   * in GCRF would get the indirect term wrong — and wrong silently, since the magnitude would stay
   * plausible (spec {@code docs/multi-corps/04-conception-L2.md} §7).
   *
   * @param body the perturbing body
   * @return the shared third-body attraction force model for that body
   */
  private ForceModel getThirdBodyModel(SolarSystemBody body) {
    return thirdBodyModels.computeIfAbsent(body, b -> new ThirdBodyAttraction(body(b)));
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
