package com.smousseur.orbitlab.simulation.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.forces.ForceModel;
import org.orekit.forces.gravity.HolmesFeatherstoneAttractionModel;
import org.orekit.forces.gravity.NewtonianAttraction;
import org.orekit.forces.gravity.ThirdBodyAttraction;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * <b>PHY-4 / L2 — the third-body wiring, closed exactly</b> (spec {@code
 * docs/multi-corps/04-conception-L2.md} §5.1).
 *
 * <p><b>What it guards.</b> L2 lets a stage declare perturbing bodies in its {@link
 * GravitationalContext}, and the propagator factories mount one {@link ThirdBodyAttraction} per
 * declared body. This fixture pulls the mounted force back out of a propagator built by the
 * <em>factory</em> — never by a direct {@code new ThirdBodyAttraction(...)} — and evaluates it at an
 * imposed geometry. What is tested is therefore the wiring, not Orekit.
 *
 * <p><b>The geometry is imposed by moving the spacecraft, not the Moon.</b> At a fixed epoch the
 * real lunar position is read in GCRF and the spacecraft is placed at geostationary radius along
 * that direction, then along the opposite one. The distance {@code d} is whatever the day gives —
 * the Moon wanders between 363 000 and 405 000 km — so the expected value is computed from the
 * actual position rather than copied from the spec.
 *
 * <p><b>What the assertion is worth, and what it is not.</b> The expected acceleration is written
 * out by hand as {@code µ·[(r_b−r)/|r_b−r|³ − r_b/|r_b|³]}, using the µ carried by the force's own
 * parameter driver. That does not prove the constant. It proves the four things that can actually be
 * wrong: the right body, the right frame, the right sign, and above all <b>the presence of the
 * indirect term</b> {@code −r_b/|r_b|³}. That term is what makes an attraction a tidal perturbation;
 * omitting it — by wiring a {@link NewtonianAttraction} by inadvertence — would give an acceleration
 * about a hundred times too large and pointing somewhere else entirely.
 *
 * <p><b>On the spec's 7.3 × 10⁻⁶ m/s².</b> The découpage quotes the <em>linearised</em> tide
 * {@code 2·µ_L·r/d³}. At geostationary radius {@code r/d ≈ 0.11}, and the linearisation is 19 % low
 * on the near side and 14 % high on the far side (spec L2 §1.1-A). It is logged here as an order of
 * magnitude and never asserted: the exact expression is pinned instead, which is stricter, not
 * looser.
 */
class ThirdBodyPerturbationTest {
  private static final Logger logger = LogManager.getLogger(ThirdBodyPerturbationTest.class);

  /** Geostationary radius, the spec's reference geometry (m). */
  private static final double GEO_RADIUS = 42_164_000.0;

  /**
   * Relative tolerance on the acceleration vector. The hand-written expression and Orekit's do the
   * same algebra in a different order, so the last bits differ; this is float equality, not a
   * negotiated margin.
   *
   * <p><b>Set from the measurement</b>, as spec L1 §5.3 requires of every literal here: the observed
   * relative error is 4.9 × 10⁻¹⁶ on the near side and 2.6 × 10⁻¹⁶ on the far side, and every run
   * logs it. The value below leaves twenty times that, which is room for a different lunar geometry
   * on another epoch, not room for a wrong formula — an omitted indirect term is off by a factor of
   * about a hundred.
   */
  private static final double RELATIVE_TOLERANCE = 1.0e-14;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  // ════════════════════════════════════════════════════════════════════════
  // The force list — the non-regression of "empty by default"
  // ════════════════════════════════════════════════════════════════════════

  /**
   * An unperturbed context mounts exactly what it mounted before L2 — <b>no third body at all</b>.
   * Not an identity term, not a zero contribution: nothing. That is what makes L2's non-regression
   * structural rather than measured (spec §4.1).
   *
   * <p><b>The shape of the list, measured.</b> A {@code NumericalPropagator} always carries a
   * central {@link NewtonianAttraction} of its own, and returns it <em>last</em> whatever else was
   * added. The 8×8 factory therefore shows two forces, not one: {@link
   * HolmesFeatherstoneAttractionModel} supplies the non-central part of the field and that
   * Newtonian supplies the central term — no double counting, and a pairing that predates PHY-4.
   * The Newtonian factory shows one, because the explicit central attraction it adds replaces the
   * propagator's own instead of stacking on it.
   */
  @Test
  void anUnperturbedContext_mountsNoThirdBody() {
    List<ForceModel> optimization = forcesOf(GravitationalContext.earth());
    List<ForceModel> newtonian =
        OrekitService.get()
            .createTestPropagator(GravitationalContext.earth(), OrekitService.COAST_MAX_STEP)
            .getAllForceModels();

    assertEquals(List.of(), thirdBodyNames(optimization), "the 8x8 factory must mount no perturber");
    assertEquals(List.of(), thirdBodyNames(newtonian), "the Newtonian factory must mount none");

    assertEquals(2, optimization.size(), "non-central field, then the propagator's own central term");
    assertInstanceOf(HolmesFeatherstoneAttractionModel.class, optimization.get(0));
    assertInstanceOf(NewtonianAttraction.class, optimization.get(1));
    assertEquals(1, newtonian.size(), "the explicit central attraction replaces the propagator's");
    assertInstanceOf(NewtonianAttraction.class, newtonian.get(0));
  }

  /**
   * The perturbers are summed in the canonical ordinal order of the context's {@code EnumSet} —
   * which puts the Sun before the Moon.
   *
   * <p><b>The caller's argument order does not reach the propagator.</b> The order force models are
   * added is the order their accelerations are summed, so it decides the last bit; the {@code
   * EnumSet} of spec §2.1 exists precisely so that a numerical result cannot depend on how someone
   * happened to write a varargs list.
   */
  @Test
  void perturbersAreSummed_inCanonicalOrder() {
    List<String> moonFirst = mountedBodies(SolarSystemBody.MOON, SolarSystemBody.SUN);
    List<String> sunFirst = mountedBodies(SolarSystemBody.SUN, SolarSystemBody.MOON);

    assertEquals(
        List.of("Sun", "Moon"),
        moonFirst,
        "perturbers must be summed in ordinal order, Sun before Moon");
    assertEquals(moonFirst, sunFirst, "the caller's argument order must not reach the propagator");
  }

  /** Both factories honour the context: a context must mean the same thing everywhere (spec §3.2). */
  @Test
  void theNewtonianFactory_honoursPerturbersToo() {
    List<ForceModel> forces =
        OrekitService.get()
            .createTestPropagator(
                GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON),
                OrekitService.COAST_MAX_STEP)
            .getAllForceModels();

    assertEquals(List.of("Moon"), thirdBodyNames(forces));
    assertInstanceOf(
        NewtonianAttraction.class,
        forces.get(forces.size() - 1),
        "the central attraction is always summed last");
  }

  /**
   * One instance per body, shared by every propagator — the guarantee {@code computeIfAbsent} gives
   * and an explicit lock would not, with CMA-ES explorations running in parallel (spec §3.3).
   */
  @Test
  void theThirdBodyModel_isSharedPerBody() {
    GravitationalContext context =
        GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN);

    List<ForceModel> first = forcesOf(context);
    List<ForceModel> second = forcesOf(context);

    assertSame(first.get(1), second.get(1), "the Sun's attraction must be a single shared instance");
    assertSame(first.get(2), second.get(2), "the Moon's attraction must be a single shared instance");
  }

  /**
   * Declaring the central body as its own perturber is a caller bug, and the easiest one to commit
   * in L4 by copying an Earth context to adapt it to a lunar arc. It throws rather than being
   * politely dropped (spec §2.2).
   */
  @Test
  void theCentralBody_cannotPerturbItself() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> GravitationalContext.earth().withPerturbers(SolarSystemBody.EARTH));
    assertTrue(error.getMessage().contains("EARTH"), error.getMessage());
  }

  // ════════════════════════════════════════════════════════════════════════
  // The physics — the exact tidal difference
  // ════════════════════════════════════════════════════════════════════════

  /** The spacecraft on the Moon's side of the Earth: the strongest of the two configurations. */
  @Test
  void theAcceleration_isTheExactTidalDifference_onTheNearSide() {
    assertExactTidalDifference(+1.0, "near side");
  }

  /**
   * The spacecraft on the far side. Same expression, and it must still hold: the far-side
   * acceleration points <em>away</em> from the Moon, which no formula missing the indirect term can
   * reproduce.
   */
  @Test
  void theAcceleration_isTheExactTidalDifference_onTheFarSide() {
    assertExactTidalDifference(-1.0, "far side");
  }

  // ════════════════════════════════════════════════════════════════════════
  // Fixtures
  // ════════════════════════════════════════════════════════════════════════

  private void assertExactTidalDifference(double side, String label) {
    AbsoluteDate date = epoch();
    Frame frame = OrekitService.get().gcrf();

    ForceModel moon =
        forcesOf(GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON)).get(1);
    Vector3D moonPosition = OrekitService.get().body(SolarSystemBody.MOON).getPosition(date, frame);
    Vector3D spacecraft = moonPosition.normalize().scalarMultiply(side * GEO_RADIUS);

    double mu = moon.getParameters(date)[0];
    Vector3D actual = moon.acceleration(stateAt(spacecraft, frame, date), moon.getParameters(date));
    Vector3D expected = tidalDifference(mu, moonPosition, spacecraft);

    double distance = moonPosition.getNorm();
    double linearised = 2.0 * mu * GEO_RADIUS / (distance * distance * distance);
    double error = Vector3D.distance(actual, expected) / expected.getNorm();

    logger.info(
        "L2 third body — {} at r = {} km, Moon at d = {} km:", label, fmt(GEO_RADIUS / 1000.0, 0),
        fmt(distance / 1000.0, 0));
    logger.info("  exact |a|      = {} m/s^2", fmt(expected.getNorm(), 9));
    logger.info(
        "  linearised 2ur/d^3 = {} m/s^2 ({}% off — spec quotes this one)",
        fmt(linearised, 9), fmt(100.0 * (linearised / expected.getNorm() - 1.0), 1));
    logger.info("  relative error against Orekit = {}", String.format(Locale.ROOT, "%.3e", error));

    assertEquals(
        0.0,
        error,
        RELATIVE_TOLERANCE,
        () ->
            "the mounted force must be the exact third-body difference, indirect term included; "
                + "expected "
                + expected
                + " but got "
                + actual);
  }

  /**
   * The third-body perturbation of a body at {@code bodyPosition} on a spacecraft at {@code
   * spacecraft}, both central-body-centred: {@code µ·[(r_b−r)/|r_b−r|³ − r_b/|r_b|³]}. The second
   * term is the acceleration the body imparts to the <em>central body</em>, and subtracting it is
   * what turns an attraction into a perturbation.
   */
  private static Vector3D tidalDifference(double mu, Vector3D bodyPosition, Vector3D spacecraft) {
    Vector3D relative = bodyPosition.subtract(spacecraft);
    double relativeCube = FastMath.pow(relative.getNorm(), 3);
    double bodyCube = FastMath.pow(bodyPosition.getNorm(), 3);
    return relative
        .scalarMultiply(mu / relativeCube)
        .subtract(bodyPosition.scalarMultiply(mu / bodyCube));
  }

  private static List<ForceModel> forcesOf(GravitationalContext context) {
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, OrekitService.COAST_MAX_STEP);
    return propagator.getAllForceModels();
  }

  private static List<String> mountedBodies(SolarSystemBody... perturbers) {
    List<ForceModel> forces = forcesOf(GravitationalContext.earth().withPerturbers(perturbers));
    assertEquals(perturbers.length + 2, forces.size(), "non-central field, perturbers, central term");
    assertInstanceOf(HolmesFeatherstoneAttractionModel.class, forces.get(0));
    assertInstanceOf(NewtonianAttraction.class, forces.get(forces.size() - 1));
    return thirdBodyNames(forces);
  }

  /** The perturbing bodies actually mounted, in the order the propagator will sum them. */
  private static List<String> thirdBodyNames(List<ForceModel> forces) {
    return forces.stream()
        .filter(ThirdBodyAttraction.class::isInstance)
        .map(force -> ((ThirdBodyAttraction) force).getBodyName())
        .toList();
  }

  /**
   * A state at the given position. {@code ThirdBodyAttraction.dependsOnPositionOnly()} is true, so
   * the velocity only has to make a valid orbit — it does not enter the acceleration.
   */
  private static SpacecraftState stateAt(Vector3D position, Frame frame, AbsoluteDate date) {
    double circularSpeed = FastMath.sqrt(Constants.WGS84_EARTH_MU / position.getNorm());
    Vector3D velocity =
        Vector3D.crossProduct(Vector3D.PLUS_K, position).normalize().scalarMultiply(circularSpeed);
    return new SpacecraftState(
        new CartesianOrbit(
            new PVCoordinates(position, velocity), frame, date, Constants.WGS84_EARTH_MU));
  }

  private static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  private static String fmt(double value, int decimals) {
    return String.format(Locale.ROOT, "%." + decimals + "f", value);
  }
}
