package com.smousseur.orbitlab.simulation.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrbitElements;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.runtime.StageChainRunner;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * <b>PHY-1 / L2 — the drag-on path, flown</b> (spec {@code docs/atmosphere/05-conception-L2.md}
 * §3.2 and §3.3).
 *
 * <p>A parking orbit coasted for a day through {@code StageChainRunner}, that is through the very
 * path a mission flies: {@code MissionStage.flightContext} resolves the mission's atmosphere choice
 * against the aerodynamics of the active stage, {@code StageLegRunner} builds the propagator from
 * the resulting {@link FlightContext}, and {@code ReentryGuard} is armed on the leg exactly as in
 * production. Only the initial state is imposed.
 *
 * <p><b>Why the fixture starts in orbit instead of recomposing an existing profile</b> (spec §1.1).
 * Every terrestrial mission starts on the launch pad, so giving {@code LEO-400} an atmosphere would
 * mean flying an ascent from 0 km: Harris-Priester throws below 100 km, and the regime below 200 km
 * costs up to 982 497 integration steps for a single day (L0 §2.2). Both belong to PHY-2, which owns
 * the altitude bound. This fixture never goes below 200 km.
 *
 * <p><b>Two things had to be measured before this fixture could assert anything</b>, and both were
 * found by sweeping the coast duration against the analytic expression — with ρ sampled on an
 * unperturbed Keplerian orbit, which is the second of the two mistakes:
 *
 * <pre>
 *   duration   osculating gap   mean gap    analytic   osc/analytic   mean/analytic
 *     1 rev         36.01 m     36.03 m     30.71 m       1.173           1.173
 *      3 h          72.82 m     72.62 m     61.94 m       1.176           1.172
 *      6 h         147.41 m    145.56 m    123.87 m       1.190           1.175
 *     12 h         305.76 m    291.54 m    247.73 m       1.234           1.177
 *     24 h         665.90 m    584.46 m    495.33 m       1.344           1.180
 * </pre>
 *
 * <p><b>a. The decay is read on mean elements, not osculating ones.</b> The osculating ratio drifts
 * with duration while the mean ratio does not, and that signature names the cause: the drag-on
 * flight runs ahead of its drag-off twin — tens of kilometres of along-track drift after a day — so
 * the two sample the J2 short-period term of {@code a} at different arguments of latitude. That term
 * has a 6 km amplitude here, ten times the signal, and it inflated the osculating difference by 14 %
 * at 24 h while leaving a single revolution untouched. {@link OrbitElements#mean(Orbit)} removes it.
 *
 * <p><b>b. ρ is averaged along the trajectory actually flown</b>, sampled on the drag-free twin,
 * rather than along an unperturbed Keplerian orbit. A Keplerian sample reads 17 % low, near-uniformly
 * over the sweep, because the real orbit's altitude history under J2 is not the circle it started on
 * and the density scale height here is only about 40 km. With both corrections the analytic
 * expression lands within 1 % of the flown decay at every duration of the sweep.
 *
 * <p>The drag-off run is therefore not decoration: it is the zero of the measurement, and the path
 * the density is read along.
 */
class OrbitalDecayFlightTest {
  private static final Logger logger = LogManager.getLogger(OrbitalDecayFlightTest.class);

  /** The geometry of the L0 measurement table (§2.2), so the numbers are comparable to it. */
  private static final double PARKING_ALTITUDE = 250_000.0;

  private static final double HIGH_ALTITUDE = 800_000.0;
  private static final double INCLINATION = FastMath.toRadians(51.6);
  private static final double DURATION = 86_400.0;

  /** The catalogued Earth-observation payload: 9.0 m², Cd 2.2, 10 t dry — B = 505 kg/m². */
  private static final AerodynamicProperties AERO = Payloads.EARTH_OBSERVATION_SAT.aerodynamics();

  private static final double MASS = 10_000.0;

  private static final AtmosphereModel[] MODELS = {
    AtmosphereModel.HARRIS_PRIESTER, AtmosphereModel.NRLMSISE
  };

  /**
   * Half-width of the band the flown decay must land in, around the analytic secular rate.
   *
   * <p><b>The physical width of the target, not the precision of the fixture.</b> Measured here, the
   * flown decay sits 0.9 % over the analytic expression on Harris-Priester and 1.0 % over it on
   * NRLMSISE-00, and stays within 1 % at every duration of the sweep in the class javadoc. What the
   * band leaves room for is a first-order secular formula applied to a real J2 orbit, at another
   * epoch and another catalogue cross-section — not room for a wrong wiring, which cannot land
   * inside a quarter.
   *
   * <p>A tighter band would not catch more of what this lot is about: an ignored co-rotation weighs
   * 7.6 % here, and it is {@code DragAccelerationTest} that pins it, to the bit.
   */
  private static final double BAND = 0.25;

  /**
   * Floor on the 800 km drag-on / drag-off gap, in metres of semi-major axis.
   *
   * <p><b>Why a lower bound at all</b> (spec §1.2): the découpage's "under 0.1 %" is a net four
   * orders of magnitude wider than the signal, so on its own it also passes when no drag was mounted
   * whatsoever — the one failure mode L1 was built to exclude. Measured here: 0.281 m of decay over
   * 24 h against an analytic 0.282 m. The floor is a third of that.
   */
  private static final double HIGH_ALTITUDE_FLOOR_M = 0.09;

  /** The découpage's net for a model applied at the wrong scale. */
  private static final double HIGH_ALTITUDE_CEILING = 1.0e-3;

  private static AbsoluteDate epoch;

  /** One flight per (altitude, model): the tests share them rather than re-flying the same coast. */
  private static final Map<String, Double> DRAG_ON = new HashMap<>();

  private static final Map<Double, DragFreeFlight> DRAG_FREE = new HashMap<>();

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 3, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  // ════════════════════════════════════════════════════════════════════════
  // Measure 2 — the decay at 250 km, against the analytic secular rate
  // ════════════════════════════════════════════════════════════════════════

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = AtmosphereModel.class,
      names = {"HARRIS_PRIESTER", "NRLMSISE"})
  @DisplayName("A 250 km parking decays at the analytic secular rate, within a quarter")
  void parkingDecay_landsInTheAnalyticBand(AtmosphereModel model) {
    double flown = decayOver(PARKING_ALTITUDE, model);
    double expected = analyticDecay(PARKING_ALTITUDE, model);
    double ratio = flown / expected;

    logger.info("L2 decay — 250 km, 24 h, i = 51.6 deg, {}:", model);
    logger.info(
        "  mean density along the flown path = {} kg/m^3",
        scientific(dragFree(PARKING_ALTITUDE).meanDensity().get(model)));
    logger.info("  analytic (co-rotation included)   = {} m", fixed(expected, 1));
    logger.info("  flown through StageChainRunner    = {} m", fixed(flown, 1));
    logger.info("  ratio = {}", fixed(ratio, 3));

    assertTrue(flown > 0.0, "the orbit must lose energy, got " + flown + " m");
    assertEquals(
        1.0,
        ratio,
        BAND,
        () ->
            "the flown decay must match the analytic secular rate within "
                + (int) (100 * BAND)
                + "%: analytic "
                + expected
                + " m, flown "
                + flown
                + " m");
  }

  // ════════════════════════════════════════════════════════════════════════
  // Measure 3 — the high-altitude sanity, at two bounds
  // ════════════════════════════════════════════════════════════════════════

  @Test
  @DisplayName("At 800 km the same mission barely notices the atmosphere — but does notice it")
  void highAltitude_isNegligibleYetPresent() {
    double gap = decayOver(HIGH_ALTITUDE, AtmosphereModel.HARRIS_PRIESTER);
    double relative = gap / (Constants.WGS84_EARTH_EQUATORIAL_RADIUS + HIGH_ALTITUDE);

    logger.info("L2 sanity — 800 km, 24 h, Harris-Priester (the denser of the two up here):");
    logger.info(
        "  drag-on vs drag-off gap = {} m ({} of the semi-major axis)",
        fixed(gap, 3),
        scientific(relative));
    logger.info(
        "  analytic secular decay  = {} m",
        fixed(analyticDecay(HIGH_ALTITUDE, AtmosphereModel.HARRIS_PRIESTER), 3));

    assertTrue(
        relative < HIGH_ALTITUDE_CEILING,
        "drag at 800 km must stay negligible, got " + relative + " of a");
    assertTrue(
        gap > HIGH_ALTITUDE_FLOOR_M,
        "and must nonetheless be there — a zero gap means no DragForce was mounted at all, got "
            + gap
            + " m");
  }

  // ════════════════════════════════════════════════════════════════════════
  // Recorded, never asserted — an input to PHY-2 (spec §4.1)
  // ════════════════════════════════════════════════════════════════════════

  @Test
  @DisplayName("The Harris-Priester / NRLMSISE-00 gap, recorded for PHY-2")
  void modelGap_isRecorded() {
    double harrisPriester = decayOver(PARKING_ALTITUDE, AtmosphereModel.HARRIS_PRIESTER);
    double nrlmsise = decayOver(PARKING_ALTITUDE, AtmosphereModel.NRLMSISE);

    logger.info("L2 model gap — 250 km, 24 h, B = {} kg/m^2:", Math.round(ballisticCoefficient()));
    logger.info("  Harris-Priester = {} m", fixed(harrisPriester, 1));
    logger.info("  NRLMSISE-00     = {} m", fixed(nrlmsise, 1));
    logger.info(
        "  NRLMSISE-00 is {} % more severe (L0 measured 22 % at B = 455 and at B = 101)",
        fixed(100.0 * (nrlmsise / harrisPriester - 1.0), 1));
  }

  // ════════════════════════════════════════════════════════════════════════
  // Fixtures
  // ════════════════════════════════════════════════════════════════════════

  /** How much mean semi-major axis the drag took, against the drag-free twin at the same date. */
  private static double decayOver(double altitude, AtmosphereModel model) {
    return dragFree(altitude).meanSemiMajorAxis() - dragOn(altitude, model);
  }

  private static double dragOn(double altitude, AtmosphereModel model) {
    return DRAG_ON.computeIfAbsent(
        altitude + "/" + model, ignored -> meanSemiMajorAxis(fly(altitude, model, null)));
  }

  /**
   * The zero of the measurement: the same coast with no atmosphere at all, sampled so the densities
   * the analytic expression needs are read along the path the orbit really flies.
   *
   * <p>Sampling does not change the flight — the step handler interpolates inside the integrator's
   * own steps — so this is the same trajectory a {@code plain()} run produces.
   */
  private static DragFreeFlight dragFree(double altitude) {
    return DRAG_FREE.computeIfAbsent(
        altitude,
        key -> {
          Frame gcrf = OrekitService.get().gcrf();
          Map<AtmosphereModel, Double> totals = new EnumMap<>(AtmosphereModel.class);
          int[] samples = {0};
          SpacecraftState end =
              fly(
                  key,
                  AtmosphereModel.NONE,
                  (stage, context, state) -> {
                    samples[0]++;
                    for (AtmosphereModel model : MODELS) {
                      totals.merge(
                          model,
                          AtmosphereProbe.of(model)
                              .getDensity(state.getDate(), state.getPosition(gcrf), gcrf),
                          Double::sum);
                    }
                  });
          Map<AtmosphereModel, Double> means = new EnumMap<>(AtmosphereModel.class);
          totals.forEach((model, total) -> means.put(model, total / samples[0]));
          return new DragFreeFlight(meanSemiMajorAxis(end), Map.copyOf(means));
        });
  }

  /** One coast, flown by the production chain runner. */
  private static SpacecraftState fly(
      double altitude, AtmosphereModel model, StageChainRunner.StepSampler sampler) {
    SpacecraftState start = circularState(altitude);
    MissionStage coast = new CoastingStage("Coasting", DURATION);
    Mission mission = new ParkingMission(start, payload(), List.of(coast));
    mission.setAtmosphere(model);
    mission.setCurrentState(start);

    long startedAt = System.currentTimeMillis();
    StageChainRunner runner =
        sampler == null ? StageChainRunner.plain() : StageChainRunner.sampling(sampler, 0.0, null);
    SpacecraftState end = runner.run(List.of(coast), start, mission);
    logger.info(
        "  flown {} km / {} in {} ms",
        (int) (altitude / 1000.0),
        model,
        System.currentTimeMillis() - startedAt);

    assertEquals(
        DURATION,
        end.getDate().durationFrom(start.getDate()),
        1.0,
        "the coast must reach its cutoff; a short flight means the guard or the integrator gave up");
    return end;
  }

  /**
   * The secular decay of a circular orbit over the coast: {@code 2π·ρ·a²/B} per revolution, with the
   * co-rotation of the atmosphere taken out of the relative speed.
   *
   * <p>ρ is the mean along the drag-free flight — see the class javadoc for why a Keplerian sample
   * reads 17 % low.
   */
  private static double analyticDecay(double altitude, AtmosphereModel model) {
    Orbit start = circularState(altitude).getOrbit();
    double a = start.getA();
    double density = dragFree(altitude).meanDensity().get(model);

    double circularSpeed = FastMath.sqrt(Constants.WGS84_EARTH_MU / a);
    double airSpeed = Constants.WGS84_EARTH_ANGULAR_VELOCITY * a * FastMath.cos(INCLINATION);
    double corotation = FastMath.pow(1.0 - airSpeed / circularSpeed, 2);

    double perRevolution = 2.0 * FastMath.PI * density * a * a / ballisticCoefficient();
    return perRevolution * (DURATION / start.getKeplerianPeriod()) * corotation;
  }

  private static double meanSemiMajorAxis(SpacecraftState state) {
    return OrbitElements.mean(state.getOrbit())
        .orElseThrow(() -> new AssertionError("the mean elements must converge"))
        .semiMajorAxis();
  }

  private static double ballisticCoefficient() {
    return MASS / (AERO.dragCoefficient() * AERO.crossSection());
  }

  /** Circular, at the requested altitude, crossing the equator northbound at the epoch. */
  private static SpacecraftState circularState(double altitude) {
    Frame gcrf = OrekitService.get().gcrf();
    double radius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + altitude;
    Vector3D position = new Vector3D(radius, 0.0, 0.0);
    double speed = FastMath.sqrt(Constants.WGS84_EARTH_MU / radius);
    Vector3D velocity =
        new Vector3D(0.0, speed * FastMath.cos(INCLINATION), speed * FastMath.sin(INCLINATION));
    return new SpacecraftState(
        new CartesianOrbit(
            new TimeStampedPVCoordinates(epoch, position, velocity),
            gcrf,
            Constants.WGS84_EARTH_MU),
        MASS);
  }

  /** The payload of the catalog, instantiated at the mass its ballistic coefficient assumes. */
  private static Vehicle payload() {
    Spacecraft spacecraft = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(MASS, 0.0);
    assertEquals(AERO, spacecraft.aerodynamics(), "the catalog must carry the aerodynamics");
    return spacecraft;
  }

  /** What one drag-free coast yields: the zero of the decay, and the densities along its path. */
  private record DragFreeFlight(
      double meanSemiMajorAxis, Map<AtmosphereModel, Double> meanDensity) {}

  /**
   * A mission that is already in orbit. Everything else about it is real: the vehicle comes from the
   * catalog, the stage is a production {@code CoastingStage}, and the atmosphere is read off the
   * mission exactly as {@code MissionStage.flightContext} reads it in flight.
   */
  private static final class ParkingMission extends Mission {
    private final SpacecraftState initial;

    private ParkingMission(SpacecraftState initial, Vehicle vehicle, List<MissionStage> stages) {
      super("PHY-1 L2 parking", vehicle, stages, null);
      this.initial = initial;
    }

    @Override
    public SpacecraftState getInitialState(AbsoluteDate initialDate) {
      return initial;
    }
  }

  private static String fixed(double value, int decimals) {
    return String.format(Locale.ROOT, "%." + decimals + "f", value);
  }

  private static String scientific(double value) {
    return String.format(Locale.ROOT, "%.4e", value);
  }
}
