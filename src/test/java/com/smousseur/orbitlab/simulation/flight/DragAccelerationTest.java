package com.smousseur.orbitlab.simulation.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.orekit.forces.ForceModel;
import org.orekit.forces.drag.DragForce;
import org.orekit.frames.Frame;
import org.orekit.models.earth.atmosphere.Atmosphere;
import org.orekit.models.earth.atmosphere.HarrisPriester;
import org.orekit.models.earth.atmosphere.NRLMSISE00;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * <b>PHY-1 / L2 — the drag force is the drag force</b> (spec {@code
 * docs/atmosphere/05-conception-L2.md} §3.1).
 *
 * <p>The acceleration the mounted {@link DragForce} contributes at an imposed state, against {@code
 * 0.5·ρ·v_rel²·Cd·S/m} written out by hand. The force is pulled back out of a propagator built by
 * the <em>factory</em>, never by a direct {@code new DragForce(…)}: what is tested is the wiring,
 * not Orekit.
 *
 * <p><b>What the assertion is worth.</b> It does not prove the density model — L0 §1 confronted
 * both models to published tables for that. It proves the four things that can be wrong here: the
 * surface and the coefficient of the hardware actually in the flow, the sign, and above all <b>that
 * the velocity used is relative to a rotating atmosphere</b> rather than inertial. That last one is
 * invisible in an orbit result and is the reason this fixture exists.
 *
 * <p><b>Two states, because one would catch half the trap.</b> Both sit on the equator at 250 km,
 * so both see the same density and the same {@code |ω × r|} of about 483 m/s against a circular
 * speed of 7 755 m/s. They differ only in where the velocity points:
 *
 * <ul>
 *   <li><b>equatorial prograde</b>, flying due east — measured: an inertial velocity would
 *       over-read the <em>magnitude</em> by 13.7 % and get the direction exactly right;
 *   <li><b>polar at the node</b>, flying due north — measured: an inertial velocity would be 0.4 %
 *       off in magnitude and 3.57° off in direction.
 * </ul>
 *
 * <p>A fixture with only the first would pass on a force that got the direction wrong; one with
 * only the second would pass on a force that ignored co-rotation altogether. Every run logs what
 * the inertial-velocity variant would have given, in the shape {@code ThirdBodyPerturbationTest}
 * logs the linearised tide it never asserts.
 */
class DragAccelerationTest {
  private static final Logger logger = LogManager.getLogger(DragAccelerationTest.class);

  /** The geometry of the L0 measurement table, so the densities are the ones written down there. */
  private static final double ALTITUDE = 250_000.0;

  /** The catalogued Earth-observation payload: 9.0 m², Cd 2.2, and 10 t gives B = 505 kg/m². */
  private static final AerodynamicProperties AERO = Payloads.EARTH_OBSERVATION_SAT.aerodynamics();

  private static final double MASS = 10_000.0;

  /**
   * Relative tolerance on the acceleration vector.
   *
   * <p><b>Set from the measurement, and the measurement is zero.</b> The observed relative error is
   * exactly {@code 0.000000e+00} on both states and both models — the hand-written expression and
   * Orekit's turn out to do the same algebra in the same order, so the two vectors agree to the
   * last bit. Every run logs the error rather than trusting that. This line is left non-zero as
   * room for another epoch or a reordered expression, not as a margin: the failure it exists to
   * catch, an inertial velocity in place of the relative one, is 13.7 % off — fourteen orders of
   * magnitude above it.
   */
  private static final double RELATIVE_TOLERANCE = 1.0e-14;

  private static AbsoluteDate epoch;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 3, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = AtmosphereModel.class,
      names = {"HARRIS_PRIESTER", "NRLMSISE"})
  void equatorialPrograde_pinsTheMagnitude(AtmosphereModel model) {
    assertAnalyticDrag(model, Vector3D.PLUS_J, "equatorial prograde");
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = AtmosphereModel.class,
      names = {"HARRIS_PRIESTER", "NRLMSISE"})
  void polarAtTheNode_pinsTheDirection(AtmosphereModel model) {
    assertAnalyticDrag(model, Vector3D.PLUS_K, "polar at the node");
  }

  // ════════════════════════════════════════════════════════════════════════
  // Fixtures
  // ════════════════════════════════════════════════════════════════════════

  private void assertAnalyticDrag(AtmosphereModel model, Vector3D flightDirection, String label) {
    Frame gcrf = OrekitService.get().gcrf();
    double radius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + ALTITUDE;
    Vector3D position = new Vector3D(radius, 0.0, 0.0);
    double circularSpeed = FastMath.sqrt(Constants.WGS84_EARTH_MU / radius);
    Vector3D velocity = flightDirection.scalarMultiply(circularSpeed);
    SpacecraftState state = stateAt(position, velocity, gcrf);

    DragForce drag = mountedDrag(model);
    Atmosphere atmosphere = AtmosphereProbe.behind(drag);
    assertInstanceOf(
        expectedClass(model), atmosphere, "the enum must resolve to the model it names");

    Vector3D relative = velocity.subtract(atmosphereVelocity(position, gcrf));
    double density = atmosphere.getDensity(epoch, position, gcrf);
    Vector3D expected = dragAcceleration(density, relative);
    Vector3D inertial = dragAcceleration(density, velocity);

    Vector3D actual = drag.acceleration(state, drag.getParameters(epoch));
    double error = Vector3D.distance(actual, expected) / expected.getNorm();

    logger.info("L2 drag — {}, {} at {} km:", label, model, (int) (ALTITUDE / 1000.0));
    logger.info("  rho            = {} kg/m^3", format(density));
    logger.info(
        "  |v_rel|        = {} m/s (inertial {} m/s, atmosphere {} m/s)",
        round(relative.getNorm()),
        round(circularSpeed),
        round(atmosphereVelocity(position, gcrf).getNorm()));
    logger.info("  expected |a|   = {} m/s^2", format(expected.getNorm()));
    logger.info(
        "  inertial |a|   = {} m/s^2 ({} % off, {} deg apart — NOT asserted)",
        format(inertial.getNorm()),
        String.format(
            Locale.ROOT, "%+.1f", 100.0 * (inertial.getNorm() / expected.getNorm() - 1.0)),
        String.format(Locale.ROOT, "%.2f", FastMath.toDegrees(Vector3D.angle(inertial, expected))));
    logger.info("  relative error against Orekit = {}", format(error));

    assertEquals(
        0.0,
        error,
        RELATIVE_TOLERANCE,
        () ->
            "the mounted drag must be 0.5*rho*v_rel^2*Cd*S/m against the ROTATING atmosphere; "
                + "expected "
                + expected
                + " but got "
                + actual);
  }

  /** {@code −½·ρ·|v|·v·Cd·S/m} — the drag opposing a flow at velocity {@code v}. */
  private static Vector3D dragAcceleration(double density, Vector3D flow) {
    double coefficient =
        -0.5 * density * flow.getNorm() * AERO.dragCoefficient() * AERO.crossSection() / MASS;
    return flow.scalarMultiply(coefficient);
  }

  /**
   * The velocity of the co-rotating air at {@code position}, derived from the ITRF → GCRF transform
   * rather than from an {@code ω × r} written by hand: the transform carries precession, nutation
   * and the true rotation rate of the day, which a constant ω does not.
   *
   * <p>It is also <b>not</b> read off the atmosphere object, which is what the force itself uses —
   * that would make the expected value depend on the very code under test.
   */
  private static Vector3D atmosphereVelocity(Vector3D position, Frame frame) {
    Frame itrf = OrekitService.get().itrf();
    Vector3D fixed = frame.getTransformTo(itrf, epoch).transformPosition(position);
    return itrf.getTransformTo(frame, epoch)
        .transformPVCoordinates(new PVCoordinates(fixed, Vector3D.ZERO))
        .getVelocity();
  }

  private static DragForce mountedDrag(AtmosphereModel model) {
    List<ForceModel> forces =
        OrekitService.get()
            .createOptimizationPropagator(
                FlightContext.earth().withDrag(new DragContext(AERO, model)),
                OrekitService.COAST_MAX_STEP)
            .getAllForceModels();
    return forces.stream()
        .filter(DragForce.class::isInstance)
        .map(DragForce.class::cast)
        .findFirst()
        .orElseThrow(() -> new AssertionError("no DragForce mounted for " + model));
  }

  private static Class<? extends Atmosphere> expectedClass(AtmosphereModel model) {
    return model == AtmosphereModel.HARRIS_PRIESTER ? HarrisPriester.class : NRLMSISE00.class;
  }

  private static SpacecraftState stateAt(Vector3D position, Vector3D velocity, Frame frame) {
    return new SpacecraftState(
        new CartesianOrbit(
            new TimeStampedPVCoordinates(epoch, position, velocity),
            frame,
            Constants.WGS84_EARTH_MU),
        MASS);
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.6e", value);
  }

  private static long round(double value) {
    return Math.round(value);
  }
}
