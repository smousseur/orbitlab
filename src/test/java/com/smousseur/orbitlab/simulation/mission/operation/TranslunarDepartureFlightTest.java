package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan.Departure;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * MIS-4 / L1 §4.2 — the one flown case of the lot: an injection aimed and bisected from a parking
 * plane that is <b>misaligned</b> with the Moon.
 *
 * <p><b>It exists because the risk L1 introduces has never been flown.</b> Until this lot {@code
 * TranslunarInjectionPlan.solve} only ever worked at zero misalignment, because {@code
 * parkingState} built the parking orbit from the very plane the aim was laid in. The geometry test
 * next door proves the injection <em>point</em> resolves in any imposed plane; nothing proved the
 * bisection on the perilune still converges once the transfer leaves that plane, and L0 set the
 * discipline of measuring that rather than asserting it.
 *
 * <p><b>Five degrees, and the figure is not arbitrary.</b> It is what the launch window of L2 will
 * meet <em>while converging</em>, not at its optimum: L0 §5 measured Canaveral's floor at 0.146°
 * over the 18.6-year cycle, but a shot taken off-window is misaligned by a great deal more.
 *
 * <p><b>The ΔV and the misalignment are logged, not asserted.</b> What the misalignment costs is
 * the relief the launch-window criterion of L2 is judged on, and pinning a number here would be
 * pinning L2's answer before L2 has asked the question. This test asserts what L1 owns: that the
 * aim still converges, and that the perilune it converges to is the one asked for.
 *
 * <p><b>Contrainte de méthode</b> (découpage §3): this case flies a four-day transfer some thirty
 * times over, so it costs a few seconds, and it is the user who runs it.
 */
class TranslunarDepartureFlightTest {
  private static final Logger logger = LogManager.getLogger(TranslunarDepartureFlightTest.class);

  /** The misalignment imposed on the parking plane (rad). */
  private static final double TILT = FastMath.toRadians(5.0);

  /** Perilune altitude aimed for (m) — the target the demo flight flies. */
  private static final double TARGET_PERILUNE = LunarTransferMission.DEFAULT_PERILUNE_ALTITUDE;

  /**
   * The band on the perilune the aim converges to (m). The bisection stops at 1 km on the flown
   * perilune, so this is the same order the demo flight is pinned at rather than a looser one
   * bought for a misaligned plane.
   */
  private static final double PERILUNE_BAND = 10_000.0;

  /** Mass at injection (kg) — the figure L0 measured its own table at. */
  private static final double INJECTION_MASS = 1_700.0;

  private static AbsoluteDate epoch;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 3, 31, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  @DisplayName("The aim still converges from a parking plane misaligned with the Moon")
  void theAimConvergesFromAMisalignedPlane() {
    SpacecraftState onOrbit = tiltedParking(TILT);
    Departure departure = TranslunarInjectionPlan.departureFrom(onOrbit);

    // The parking state as it will be at the injection point. L4 will coast to it; here it is
    // rebuilt from the direction the departure hands over, which is exactly what L2 does too.
    SpacecraftState atInjection =
        circularParking(
            normalOf(onOrbit), departure.injectionDirection(), departure.injectionDate());

    double exhaustVelocity =
        PropulsionSystem.getSpacecraftPropulsion().isp() * Constants.G0_STANDARD_GRAVITY;
    FlightContext context =
        new FlightContext(
            GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN));

    long startedAt = System.nanoTime();
    TranslunarInjectionPlan plan =
        TranslunarInjectionPlan.solve(atInjection, TARGET_PERILUNE, exhaustVelocity, context);
    double wallSeconds = (System.nanoTime() - startedAt) / 1.0e9;

    // ── logged, not asserted (spec §4.2) ────────────────────────────────────
    logger.info(
        "Departure from a {}° tilted plane: misalignment {}°, parking coast {} s, dv {} m/s,"
            + " aim offset {} km, plan perilune {} km, solved in {} s",
        String.format(Locale.ROOT, "%.1f", FastMath.toDegrees(TILT)),
        String.format(Locale.ROOT, "%.3f", FastMath.toDegrees(departure.planeMisalignment())),
        String.format(Locale.ROOT, "%.1f", departure.coastDuration()),
        String.format(Locale.ROOT, "%.1f", plan.deltaV().getNorm()),
        String.format(Locale.ROOT, "%.0f", plan.aimOffset() / 1000.0),
        String.format(Locale.ROOT, "%.1f", plan.perileneAltitude() / 1000.0),
        String.format(Locale.ROOT, "%.1f", wallSeconds));

    // The closed-form cost of the same departure, for the record: it is the Lambert term L2 will
    // rank its epochs on, and this is the first place the two can be read side by side.
    logger.info(
        "Closed-form injection cost at the same departure: {} m/s (aimed at the lunar centre),"
            + " against {} m/s at zero misalignment",
        String.format(
            Locale.ROOT,
            "%.1f",
            TranslunarInjectionPlan.keplerianInjectionDeltaV(atInjection, departure.arrivalDate())),
        String.format(
            Locale.ROOT,
            "%.1f",
            TranslunarInjectionPlan.keplerianInjectionDeltaV(epoch, INJECTION_MASS)));

    // ── what L1 owns ────────────────────────────────────────────────────────
    assertEquals(
        TARGET_PERILUNE,
        plan.perileneAltitude(),
        PERILUNE_BAND,
        "the aim must converge to the perilune it was given, misaligned plane or not");
    assertTrue(plan.deltaV().getNorm() > 0.0, "an injection that costs nothing did not happen");
    // The same instant, not the same object: the departure reaches it as t₀ + coast + ToF and the
    // plan as (t₀ + coast) + ToF, which differ in the last attoseconds of Orekit's date arithmetic.
    assertEquals(
        0.0,
        plan.arrivalDate().durationFrom(departure.arrivalDate()),
        1.0e-6,
        "the plan must aim at the arrival date the departure resolved");
  }

  /**
   * A parking plane misaligned with the Moon by {@code tilt}, at an arbitrary phase.
   *
   * <p>Built by rotating the demo's own normal by {@code tilt} about {@code ĥ × ûM}, which gives
   * {@code ĥ' · ûM = sin(tilt)} — the misalignment is imposed rather than searched for (spec §4.1).
   * The phase is deliberately <em>not</em> the injection point: the departure has to resolve a real
   * coast, which is half of what this case flies.
   */
  private static SpacecraftState tiltedParking(double tilt) {
    SpacecraftState demo = TranslunarInjectionPlan.parkingState(epoch, INJECTION_MASS);
    Vector3D normal = normalOf(demo);
    Vector3D arrival =
        moonPosition(epoch.shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS)).normalize();
    Vector3D axis = Vector3D.crossProduct(normal, arrival).normalize();
    Vector3D tilted = new Rotation(axis, tilt, RotationConvention.VECTOR_OPERATOR).applyTo(normal);
    Vector3D elsewhere =
        new Rotation(tilted, FastMath.toRadians(120.0), RotationConvention.VECTOR_OPERATOR)
            .applyTo(demo.getPosition());
    return circularParking(tilted, elsewhere, epoch);
  }

  /** A circular parking orbit at the plan's altitude, in the plane {@code normal} is normal to. */
  private static SpacecraftState circularParking(
      Vector3D normal, Vector3D towards, AbsoluteDate date) {
    Vector3D unitNormal = normal.normalize();
    Vector3D direction =
        towards.subtract(unitNormal.scalarMultiply(towards.dotProduct(unitNormal))).normalize();
    double radius =
        Constants.WGS84_EARTH_EQUATORIAL_RADIUS + TranslunarInjectionPlan.PARKING_ALTITUDE;
    Vector3D velocity =
        Vector3D.crossProduct(unitNormal, direction)
            .scalarMultiply(FastMath.sqrt(Constants.WGS84_EARTH_MU / radius));
    return new SpacecraftState(
            new CartesianOrbit(
                new TimeStampedPVCoordinates(date, direction.scalarMultiply(radius), velocity),
                OrekitService.get().gcrf(),
                Constants.WGS84_EARTH_MU))
        .withMass(INJECTION_MASS);
  }

  private static Vector3D normalOf(SpacecraftState state) {
    return Vector3D.crossProduct(state.getPosition(), state.getPVCoordinates().getVelocity())
        .normalize();
  }

  private static Vector3D moonPosition(AbsoluteDate date) {
    return OrekitService.get()
        .body(SolarSystemBody.MOON)
        .getPosition(date, OrekitService.get().gcrf());
  }
}
