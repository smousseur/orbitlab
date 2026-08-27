package com.smousseur.orbitlab.simulation.mission.maneuver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan.Departure;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.hipparchus.util.MathUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * PHY-4 / L6 §7.1 — the geometry of the translunar injection, with no propagation at all.
 *
 * <p>Everything asserted here is closed form: the transfer plane, the parking orbit derived from
 * it, and the Lambert seed. What the flight costs is in {@code LunarFlybyFlightTest}; this class
 * runs in milliseconds and is what says <em>why</em> the flight can work before anything is flown.
 *
 * <p><b>MIS-4 / L1 §4.1 extends it rather than opening a class of its own</b> ({@code
 * docs/lunar-flyby/03-conception-L1.md}): the departure from an <em>imposed</em> plane is the same
 * kind of statement — closed form, milliseconds — about the same geometry. The five tests above the
 * L1 block are the ones the lot must leave untouched, and they are.
 */
class TranslunarInjectionPlanTest {
  private static final Logger logger = LogManager.getLogger(TranslunarInjectionPlanTest.class);

  /**
   * The parking planes L1 §4.1 imposes: the three launch-site latitudes the lunar chain can fly
   * from (L0 §5), each with a right ascension of the ascending node and an argument of latitude of
   * its own, plus one deliberately arbitrary plane that answers to no site at all.
   */
  private static final List<ImposedPlane> IMPOSED_PLANES =
      List.of(
          new ImposedPlane("Canaveral", 28.562, 40.0, 0.0),
          new ImposedPlane("Baikonur", 45.965, 210.0, 95.0),
          new ImposedPlane("Kourou", 5.236, 300.0, 190.0),
          new ImposedPlane("arbitrary", 63.4, 117.0, 285.0));

  /** Mass carried by the closed-form fixtures (kg). Nothing here burns, so it only has to exist. */
  private static final double FIXTURE_MASS = 1_000.0;

  /**
   * How far past a full revolution a coast may run (s) — see {@link #assertFirstPassage}. Derived,
   * not recorded: the injection point drifts with the Moon at 0.549 °/h, so it gains 0.81° over the
   * 5 291 s parking revolution, which is 11.9 s of parking phase. Twenty leaves room for an imposed
   * orbit that is not perfectly circular.
   */
  private static final double INJECTION_POINT_DRIFT_SECONDS = 20.0;

  private static AbsoluteDate epoch;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 3, 31, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  @DisplayName(
      "The parking plane contains the Moon's direction at arrival, at the declared inclination")
  void parkingPlaneContainsTheMoonAtArrival() {
    SpacecraftState parking = TranslunarInjectionPlan.parkingState(epoch, 1_000.0);
    AbsoluteDate arrival = epoch.shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS);
    Vector3D moonDirection = moonPosition(arrival).normalize();

    Vector3D normal =
        Vector3D.crossProduct(parking.getPosition(), parking.getPVCoordinates().getVelocity())
            .normalize();

    // The plane contains the arrival direction: that is the whole reason no launch window is
    // needed,
    // and it is what keeps the transfer in-plane and the delta-v at its Hohmann value.
    assertEquals(
        0.0,
        normal.dotProduct(moonDirection),
        1.0e-12,
        "the parking plane must contain the Moon's direction at arrival");

    // And it is inclined at the declared value, measured off the flown state rather than read back
    // off the constant that built it.
    double inclination = Vector3D.angle(normal, Vector3D.PLUS_K);
    assertEquals(
        TranslunarInjectionPlan.PARKING_INCLINATION,
        inclination,
        1.0e-9,
        "the parking inclination must be the declared one");
  }

  @Test
  @DisplayName(
      "The injection point sits the declared transfer angle short of the arrival direction")
  void injectionPointSitsTheTransferAngleShortOfTheMoon() {
    SpacecraftState parking = TranslunarInjectionPlan.parkingState(epoch, 1_000.0);
    AbsoluteDate arrival = epoch.shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS);

    double travelled = Vector3D.angle(parking.getPosition(), moonPosition(arrival));
    assertEquals(
        TranslunarInjectionPlan.TRANSFER_ANGLE,
        travelled,
        1.0e-9,
        "the transfer must span the declared angle");

    // Clear of the half-revolution degeneracy, which is the reason the angle is not 180 degrees.
    assertTrue(
        FastMath.PI - travelled > FastMath.toRadians(1.0),
        "the transfer must stay clear of the 180 degree Lambert singularity");

    // The parking orbit is circular at the declared altitude, prograde in its plane.
    double radius =
        Constants.WGS84_EARTH_EQUATORIAL_RADIUS + TranslunarInjectionPlan.PARKING_ALTITUDE;
    assertEquals(radius, parking.getPosition().getNorm(), 1.0);
    assertEquals(
        FastMath.sqrt(Constants.WGS84_EARTH_MU / radius),
        parking.getPVCoordinates().getVelocity().getNorm(),
        1.0e-6);
    assertEquals(
        0.0,
        parking.getPosition().dotProduct(parking.getPVCoordinates().getVelocity())
            / (radius * 7_800.0),
        1.0e-12,
        "a circular orbit has no radial velocity");
  }

  @Test
  @DisplayName(
      "The Lambert seed costs what vis-viva says a near-Hohmann translunar injection costs")
  void lambertSeedCostsTheViscVivaDeltaV() {
    SpacecraftState parking = TranslunarInjectionPlan.parkingState(epoch, 1_000.0);
    AbsoluteDate arrival = epoch.shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS);

    // Aim at a hundred-kilometre perilune, the target the flight test flies.
    double offset = 1_737_400.0 + 100_000.0;
    Vector3D seed =
        TranslunarInjectionPlan.keplerianSeedVelocity(
            parking,
            TranslunarInjectionPlan.boundaryConditions(
                parking, arrival, TranslunarInjectionPlan.aimPointFor(parking, arrival, offset)));
    double deltaV = seed.subtract(parking.getPVCoordinates().getVelocity()).getNorm();

    // The reference is derived here and not recorded from a previous run: vis-viva on an ellipse
    // from
    // the parking radius to the Moon's distance. Asserting against the implementation's own output
    // would only prove it reproduces yesterday (the discipline L4 §7.2 set).
    double mu = Constants.WGS84_EARTH_MU;
    double rp = parking.getPosition().getNorm();
    double ra = moonPosition(arrival).getNorm();
    double semiMajorAxis = 0.5 * (rp + ra);
    double expected = FastMath.sqrt(mu * (2.0 / rp - 1.0 / semiMajorAxis)) - FastMath.sqrt(mu / rp);

    logger.info(
        "Lambert seed delta-v {} m/s against the vis-viva reference {} m/s", deltaV, expected);

    // The band covers the transfer being 170 degrees rather than 180 and four days rather than the
    // Hohmann 5.02, both of which cost a little more than the reference ellipse.
    assertEquals(expected, deltaV, 150.0);
  }

  @Test
  @DisplayName("A lunar declination beyond the parking inclination is refused, not approximated")
  void aDeclinationBeyondTheInclinationThrows() {
    // Fabricated, because the real Moon never gets there: measured [-28.40, +28.32] degrees over
    // 400
    // days from 2026-03-01, and 28.6 degrees is the ceiling of the whole 18.6-year cycle. The guard
    // exists BECAUSE the margin to 30 degrees is 1.4 degrees, not because the case is reachable.
    double declination = FastMath.toRadians(35.0);
    Vector3D beyond = new Vector3D(FastMath.cos(declination), 0.0, FastMath.sin(declination));

    OrbitlabException thrown =
        assertThrows(
            OrbitlabException.class, () -> TranslunarInjectionPlan.transferPlaneNormal(beyond));
    assertTrue(thrown.getMessage().contains("declination"));

    // And just inside the inclination it resolves, so the guard is a boundary and not a blanket.
    double inside = TranslunarInjectionPlan.PARKING_INCLINATION - FastMath.toRadians(0.5);
    Vector3D within = new Vector3D(FastMath.cos(inside), 0.0, FastMath.sin(inside));
    Vector3D normal = TranslunarInjectionPlan.transferPlaneNormal(within);
    assertEquals(0.0, normal.dotProduct(within), 1.0e-12);
    assertEquals(
        TranslunarInjectionPlan.PARKING_INCLINATION,
        Vector3D.angle(normal, Vector3D.PLUS_K),
        1.0e-9);
  }

  @Test
  @DisplayName("The geometry resolves at every epoch of a lunar month, with no window search")
  void theGeometryResolvesAtEveryEpoch() {
    // The property decision 4 of the design buys: the parking orbit is built to fit the Moon, so
    // there is nothing to wait for. Twenty-eight daily epochs cover a full declination cycle.
    for (int day = 0; day < 28; day++) {
      AbsoluteDate date = epoch.shiftedBy(day * 86_400.0);
      SpacecraftState parking = TranslunarInjectionPlan.parkingState(date, 1_000.0);
      Vector3D normal =
          Vector3D.crossProduct(parking.getPosition(), parking.getPVCoordinates().getVelocity())
              .normalize();
      Vector3D moonDirection =
          moonPosition(date.shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS)).normalize();
      assertEquals(
          0.0, normal.dotProduct(moonDirection), 1.0e-12, "day " + day + " left the plane behind");
    }
  }

  // ── MIS-4 / L1 §4.1 — the departure from an imposed plane ──────────────────

  @Test
  @DisplayName(
      "The injection point lies in the imposed plane, the transfer angle short of the Moon")
  void theInjectionPointLiesInTheImposedPlane() {
    for (ImposedPlane plane : IMPOSED_PLANES) {
      SpacecraftState parking = plane.parkingAt(epoch);
      Vector3D normal = normalOf(parking);
      Departure departure = TranslunarInjectionPlan.departureFrom(parking);
      Vector3D injection = departure.injectionDirection();

      assertEquals(
          1.0, injection.getNorm(), 1.0e-12, plane.name() + ": a direction is a unit vector");
      // Assertion 1. The whole property of the lot: the injection point is in the plane the
      // spacecraft was given, not in one derived from where the Moon happens to be.
      assertEquals(
          0.0,
          injection.dotProduct(normal),
          1.0e-12,
          plane.name() + ": the injection point must lie in the imposed plane");

      // Assertion 2. And it is the transfer angle behind the arrival direction laid flat in that
      // plane — measured as an oriented angle, so a mirrored injection point cannot pass for it.
      Vector3D inPlaneArrival = inPlane(moonDirection(departure.arrivalDate()), normal);
      assertEquals(
          TranslunarInjectionPlan.TRANSFER_ANGLE,
          orientedAngle(injection, inPlaneArrival, normal),
          1.0e-9,
          plane.name() + ": the injection point must sit the transfer angle short of the Moon");
    }
  }

  @Test
  @DisplayName("The parking coast reaches the injection point, and never buys a second revolution")
  void theCoastReachesTheInjectionPointWithinOneRevolution() {
    for (ImposedPlane plane : IMPOSED_PLANES) {
      SpacecraftState parking = plane.parkingAt(epoch);
      Departure departure = TranslunarInjectionPlan.departureFrom(parking);
      KeplerianOrbit keplerian = keplerianFrom(parking);

      logger.info(
          "Imposed plane {}: coast {} s, misalignment {}°",
          plane.name(),
          String.format(Locale.ROOT, "%.1f", departure.coastDuration()),
          String.format(Locale.ROOT, "%.3f", FastMath.toDegrees(departure.planeMisalignment())));

      // Assertion 3. The fixed point is internally consistent: the Keplerian position the coast
      // lands on IS the injection point. This is what the whole closed form claims, and the only
      // assertion that would catch a mean-motion or a sign error in it.
      assertEquals(
          0.0,
          Vector3D.angle(
              keplerian.shiftedBy(departure.coastDuration()).getPosition(),
              departure.injectionDirection()),
          1.0e-9,
          plane.name() + ": the coast must land on the injection point");

      assertFirstPassage(plane.name(), departure, keplerian.getKeplerianPeriod());

      assertEquals(
          departure.coastDuration(),
          departure.injectionDate().durationFrom(epoch),
          1.0e-9,
          plane.name() + ": the injection date is the coast");
      assertEquals(
          TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS,
          departure.arrivalDate().durationFrom(departure.injectionDate()),
          1.0e-9,
          plane.name() + ": the arrival date is one time of flight after injection");
    }

    // The ceiling itself, which none of the four planes above comes near. A state sitting just PAST
    // its injection point has to wait almost a whole revolution for the next one — the wrap of the
    // first pass selects the first passage, not the nearest one — and this is the only case where
    // the coast approaches its bound.
    SpacecraftState justMissed = tiltedFromTheDemo(FastMath.toRadians(5.0), epoch);
    Departure departure = TranslunarInjectionPlan.departureFrom(justMissed);
    double period = keplerianFrom(justMissed).getKeplerianPeriod();
    logger.info(
        "Just past the injection point: coast {} s against a {} s period",
        String.format(Locale.ROOT, "%.1f", departure.coastDuration()),
        String.format(Locale.ROOT, "%.1f", period));
    assertTrue(
        departure.coastDuration() > 0.9 * period,
        "the fixture must actually exercise the wrap, got " + departure.coastDuration() + " s");
    assertFirstPassage("just past the injection point", departure, period);
  }

  /**
   * Assertion 4 — the coast is the first passage through the injection point.
   *
   * <p><b>The ceiling is one revolution plus the injection point's own drift, and not one
   * revolution</b>, which is where L1 §4.1 is a hair too strict: while the spacecraft chases the
   * injection point, that point moves forward with the Moon at 0.549 °/h, so a departure taken just
   * past it lands 0.81° — <b>11.9 s</b> at this altitude — beyond a full revolution. Measured at
   * 10.5 s. The substance holds: what L6 needs is that the parking coast fits inside an upper
   * stage's restart window (7 200 s on the Falcon Heavy S2), and 5 302 s does.
   */
  private static void assertFirstPassage(String label, Departure departure, double period) {
    assertTrue(
        departure.coastDuration() >= 0.0,
        label + ": a coast cannot run backwards, got " + departure.coastDuration() + " s");
    assertTrue(
        departure.coastDuration() < period + INJECTION_POINT_DRIFT_SECONDS,
        label
            + ": the coast must be the first passage, got "
            + departure.coastDuration()
            + " s against a "
            + period
            + " s period");
  }

  @Test
  @DisplayName("The reported misalignment is the arrival direction's angle above the parking plane")
  void theMisalignmentIsTheAngleAboveThePlane() {
    // Assertion 6, on every imposed plane: the sine form the projection drops out of, cross-checked
    // against the angle between the two vectors — a different computation of the same quantity, and
    // the reason L2 can take this term as given rather than writing the trigonometry a second time.
    for (ImposedPlane plane : IMPOSED_PLANES) {
      SpacecraftState parking = plane.parkingAt(epoch);
      Departure departure = TranslunarInjectionPlan.departureFrom(parking);
      Vector3D arrival = moonDirection(departure.arrivalDate());

      assertEquals(
          MathUtils.SEMI_PI - Vector3D.angle(normalOf(parking), arrival),
          departure.planeMisalignment(),
          1.0e-12,
          plane.name() + ": the misalignment must be the angle above the plane");
    }

    // And it is signed, and it can be imposed exactly: rotating the demo's normal by γ about
    // ĥ × ûM gives ĥ' = ĥ·cos γ + ûM·sin γ, hence ĥ'·ûM = sin γ. The test dictates its
    // misalignment instead of searching for one.
    for (double degrees : new double[] {5.0, -5.0, 20.0}) {
      double gamma = FastMath.toRadians(degrees);
      Departure departure = TranslunarInjectionPlan.departureFrom(tiltedFromTheDemo(gamma, epoch));
      logger.info(
          "Tilt γ = {}° imposes a misalignment of {}°, coast {} s",
          degrees,
          String.format(Locale.ROOT, "%.3f", FastMath.toDegrees(departure.planeMisalignment())),
          String.format(Locale.ROOT, "%.1f", departure.coastDuration()));

      // The band is one degree and it is not slack: the fixture is exact at the demo's arrival
      // date, while the misalignment is read at the arrival date the coast leads to, up to one
      // parking revolution later — 1.47 h during which the Moon moves 0.81°.
      assertEquals(
          gamma,
          departure.planeMisalignment(),
          FastMath.toRadians(1.0),
          "the tilt fixture must impose the misalignment it names, sign included");
    }
  }

  @Test
  @DisplayName("On the fabricated plane the departure is the demo itself: no coast, same point")
  void atZeroMisalignmentTheDepartureIsTheDemo() {
    // Assertion 5, and it is the one that says L1 did not move the demo. parkingState already puts
    // the spacecraft at the injection point of a plane that contains the Moon, so the fixed point
    // has nothing to do and must say so — not resolve to a whole revolution, which is what a naive
    // wrap into [0, 2π) does with a rounding sign.
    SpacecraftState demo = TranslunarInjectionPlan.parkingState(epoch, FIXTURE_MASS);
    Departure departure = TranslunarInjectionPlan.departureFrom(demo);

    assertEquals(0.0, departure.coastDuration(), "the demo departs from where it already is");
    assertEquals(
        0.0,
        departure.planeMisalignment(),
        1.0e-9,
        "the fabricated plane contains the arrival direction by construction");
    assertEquals(
        0.0,
        Vector3D.angle(departure.injectionDirection(), demo.getPosition()),
        1.0e-9,
        "the injection point is the parking state's own position");
    assertEquals(
        epoch.shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS),
        departure.arrivalDate(),
        "the arrival date is the one parkingState was built against");
  }

  @Test
  @DisplayName(
      "A non-Keplerian acceleration on the parking state does not move the injection point")
  void aNonKeplerianAccelerationDoesNotMoveTheCoast() {
    // Assertion 7, and without it the regression is invisible. A state coming out of a numerical
    // propagator carries a non-Keplerian acceleration, and Orekit's Orbit.shiftedBy then adds a
    // quadratic dt² term meant for small offsets: over a parking revolution that term displaces the
    // position by hundreds of kilometres. The fixed point must rebuild its orbit from position and
    // velocity alone, and this is what proves it does — the failure it guards against does not
    // throw, it silently shifts the answer.
    ImposedPlane plane = IMPOSED_PLANES.get(0);
    SpacecraftState clean = plane.parkingAt(epoch);
    SpacecraftState perturbed = withFabricatedAcceleration(clean);

    assertTrue(
        perturbed.getOrbit().hasNonKeplerianAcceleration(),
        "the fixture must actually carry the acceleration whose effect is being ruled out");
    assertFalse(
        clean.getOrbit().hasNonKeplerianAcceleration(), "and the reference must not carry one");

    Departure fromClean = TranslunarInjectionPlan.departureFrom(clean);
    Departure fromPerturbed = TranslunarInjectionPlan.departureFrom(perturbed);

    assertEquals(
        fromClean.coastDuration(),
        fromPerturbed.coastDuration(),
        1.0e-6,
        "the coast must not depend on an acceleration the parking state happens to carry");
    assertEquals(
        0.0,
        Vector3D.angle(fromClean.injectionDirection(), fromPerturbed.injectionDirection()),
        1.0e-12,
        "nor must the injection point");
  }

  /** A parking plane the departure is given rather than allowed to choose. */
  private record ImposedPlane(
      String name,
      double inclinationDegrees,
      double raanDegrees,
      double argumentOfLatitudeDegrees) {

    /** The circular parking state this plane carries at {@code date}, at the declared phase. */
    SpacecraftState parkingAt(AbsoluteDate date) {
      double inclination = FastMath.toRadians(inclinationDegrees);
      double raan = FastMath.toRadians(raanDegrees);
      Vector3D normal =
          new Vector3D(
              FastMath.sin(inclination) * FastMath.sin(raan),
              -FastMath.sin(inclination) * FastMath.cos(raan),
              FastMath.cos(inclination));
      Vector3D node = new Vector3D(FastMath.cos(raan), FastMath.sin(raan), 0.0);
      double argumentOfLatitude = FastMath.toRadians(argumentOfLatitudeDegrees);
      Vector3D direction =
          new Rotation(normal, argumentOfLatitude, RotationConvention.VECTOR_OPERATOR)
              .applyTo(node);
      return circularParking(normal, direction, date);
    }
  }

  /**
   * The demo's plane tilted by {@code gamma} about {@code ĥ × ûM}, which imposes a misalignment of
   * exactly {@code gamma} at the demo's arrival date (see {@link
   * #theMisalignmentIsTheAngleAboveThePlane}).
   */
  private static SpacecraftState tiltedFromTheDemo(double gamma, AbsoluteDate date) {
    SpacecraftState demo = TranslunarInjectionPlan.parkingState(date, FIXTURE_MASS);
    Vector3D normal = normalOf(demo);
    Vector3D arrival =
        moonDirection(date.shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS));
    Vector3D axis = Vector3D.crossProduct(normal, arrival).normalize();
    Vector3D tilted = new Rotation(axis, gamma, RotationConvention.VECTOR_OPERATOR).applyTo(normal);
    return circularParking(tilted, demo.getPosition(), date);
  }

  /** A circular parking orbit at the plan's altitude, in the plane {@code normal} is normal to. */
  private static SpacecraftState circularParking(
      Vector3D normal, Vector3D towards, AbsoluteDate date) {
    Vector3D unitNormal = normal.normalize();
    Vector3D direction = inPlane(towards, unitNormal);
    double radius =
        Constants.WGS84_EARTH_EQUATORIAL_RADIUS + TranslunarInjectionPlan.PARKING_ALTITUDE;
    Vector3D position = direction.scalarMultiply(radius);
    Vector3D velocity =
        Vector3D.crossProduct(unitNormal, direction)
            .scalarMultiply(FastMath.sqrt(Constants.WGS84_EARTH_MU / radius));
    return new SpacecraftState(
            new CartesianOrbit(
                new TimeStampedPVCoordinates(date, position, velocity),
                OrekitService.get().gcrf(),
                Constants.WGS84_EARTH_MU))
        .withMass(FIXTURE_MASS);
  }

  /**
   * The same state, carrying a fabricated non-Keplerian acceleration — the Keplerian one plus a
   * cross-track term of J2's order of magnitude at this altitude, which is far above the {@code mu
   * · 1e-9} threshold Orekit sets the flag on.
   */
  private static SpacecraftState withFabricatedAcceleration(SpacecraftState state) {
    Vector3D position = state.getPosition();
    Vector3D velocity = state.getPVCoordinates().getVelocity();
    double radius = position.getNorm();
    Vector3D keplerian =
        position.scalarMultiply(-Constants.WGS84_EARTH_MU / (radius * radius * radius));
    Vector3D acceleration = keplerian.add(normalOf(state).scalarMultiply(0.017));
    return new SpacecraftState(
            new CartesianOrbit(
                new TimeStampedPVCoordinates(state.getDate(), position, velocity, acceleration),
                state.getFrame(),
                state.getOrbit().getMu()))
        .withMass(state.getMass());
  }

  /** The orbit a departure reads the phase off: rebuilt from position and velocity alone. */
  private static KeplerianOrbit keplerianFrom(SpacecraftState state) {
    return new KeplerianOrbit(
        new PVCoordinates(state.getPosition(), state.getPVCoordinates().getVelocity()),
        state.getFrame(),
        state.getDate(),
        state.getOrbit().getMu());
  }

  private static Vector3D normalOf(SpacecraftState state) {
    return Vector3D.crossProduct(state.getPosition(), state.getPVCoordinates().getVelocity())
        .normalize();
  }

  /** {@code towards} laid flat in the plane {@code normal} is normal to, normalized. */
  private static Vector3D inPlane(Vector3D towards, Vector3D normal) {
    return towards.subtract(normal.scalarMultiply(towards.dotProduct(normal))).normalize();
  }

  /** The angle from {@code from} to {@code to} measured about {@code axis}, in {@code (−π, π]}. */
  private static double orientedAngle(Vector3D from, Vector3D to, Vector3D axis) {
    return FastMath.atan2(Vector3D.crossProduct(from, to).dotProduct(axis), from.dotProduct(to));
  }

  private static Vector3D moonDirection(AbsoluteDate date) {
    return moonPosition(date).normalize();
  }

  private static Vector3D moonPosition(AbsoluteDate date) {
    Frame gcrf = OrekitService.get().gcrf();
    return OrekitService.get().body(SolarSystemBody.MOON).getPosition(date, gcrf);
  }
}
