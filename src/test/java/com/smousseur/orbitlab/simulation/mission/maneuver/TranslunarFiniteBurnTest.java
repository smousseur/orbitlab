package com.smousseur.orbitlab.simulation.mission.maneuver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.operation.LunarFlybyMission;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.attitudes.FrameAlignedProvider;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

/**
 * MIS-4 / L6 §6.3 — the finite burn on its own, on the two upper-stage profiles the découpage sizes
 * the lot by.
 *
 * <p><b>It exists because no lunar flight of the repository flies an Ariane 62</b> (spec §1.3): the
 * four of them build a Falcon Heavy, whose 3° of arc is the one case where the lot barely earns its
 * keep. The 19° profile that justifies L6 is exercised here, at the level of the burn alone —
 * seconds rather than a second four-day flight, and it asks exactly what L6 delivers rather than
 * what a lunar Ariane would additionally need sizing (§6.3, alternatives).
 *
 * <p><b>The two profiles fly the same 22.7 t at injection</b>, which is the mass the découpage's
 * duration table is implicitly written at (§1.4). Flown on 2026-08-27, that mass reproduces the
 * tabulated durations and arcs, and <b>refutes the loss column</b>:
 *
 * <table>
 *   <caption>Measured against the découpage's estimate</caption>
 *   <tr><th>Profile</th><th>Duration</th><th>Arc</th><th>Surcharge</th><th>Announced</th></tr>
 *   <tr><td>Falcon Heavy S2</td><td>47.9 s</td><td>3.3°</td><td>+2.18 m/s</td><td>~0.4</td></tr>
 *   <tr><td>Ariane 62 ULPM</td><td>288.5 s</td><td>19.6°</td><td>+23.5 m/s</td><td>~14</td></tr>
 * </table>
 *
 * <p><b>Each case asserts two things, and the second is the one L6 nearly shipped without.</b> The
 * energy reached has to be the impulse's, which is what the inner knob is for; and the perilune the
 * plan reports has to be the target, which only holds because the aim bisection is evaluated on the
 * finite departure. Aiming with the impulse and flying the burn passed the first assertion and
 * missed the Moon by 3 451 km.
 *
 * <p><b>The {@code 1 − sinc(θ/2)} estimate is a lower bound, not the loss.</b> It accounts only for
 * the thrust direction sweeping the arc; what a burn also pays is the time the vehicle spends off
 * the impulsive trajectory while thrusting. Measured, that doubles the ULPM figure and quintuples
 * the Falcon Heavy one. The bands below are set from these measurements and not from the estimate:
 * their job is to catch a regression of the calibration, so they are wide enough that the residual
 * of a converged secant cannot reach an edge.
 */
class TranslunarFiniteBurnTest {
  private static final Logger logger = LogManager.getLogger(TranslunarFiniteBurnTest.class);

  /** The epoch every closed-form lunar case of the repository is measured at. */
  private static AbsoluteDate epoch;

  /**
   * Mass at injection (kg). The value the découpage's table is written at: it is what turns {@code
   * dt = m₀c/F·(1 − e^(−Δv/c))} into the tabulated 47 s at 348 s / 981 kN and 275 s at 457 s / 180
   * kN.
   */
  private static final double INJECTION_MASS = 22_700.0;

  /** Dry mass of the synthetic upper stage (kg), low enough that neither profile hits the floor. */
  private static final double DRY_MASS = 5_000.0;

  /** Verification band on the energy delivered, in m/s equivalent — the calibration's own 0.01. */
  private static final double ENERGY_BAND_METERS_PER_SECOND = 0.05;

  /**
   * Band on the perilune the aim converges to (m). Twice the 1 km the bisection stops at: the aim
   * is now evaluated on the finite departure, so this is what says the outer knob converged on the
   * trajectory the burn really flies rather than on the impulse it is calibrated against.
   */
  private static final double AIM_BAND_METERS = 2_000.0;

  @BeforeAll
  static void setUp() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 3, 31, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  @DisplayName("A Falcon Heavy upper stage delivers the impulsive energy over a 3° arc")
  void falconHeavy_deliversTheImpulsiveEnergy() {
    assertProfile("Falcon Heavy S2", new PropulsionSystem(348, 981_000.0), 0.5, 5.0);
  }

  @Test
  @DisplayName("An Ariane 62 ULPM delivers the same energy over a 19° arc, and pays for it")
  void ariane62Ulpm_deliversTheImpulsiveEnergyOverAMuchLongerArc() {
    assertProfile("Ariane 62 ULPM", new PropulsionSystem(457, 180_000.0), 12.0, 40.0);
  }

  /**
   * Calibrates the burn on a profile, then <b>re-flies it independently</b> and checks that the
   * energy reached is the impulsive one. Re-flying rather than trusting the residual the
   * calibration logs is the point: the assertion has to be able to fail when the secant leaves its
   * bracket, and that is what triggers the vector remedy of §4.4.
   */
  private void assertProfile(
      String label, PropulsionSystem propulsion, double minSurcharge, double maxSurcharge) {
    SpacecraftState parking = TranslunarInjectionPlan.parkingState(epoch, INJECTION_MASS);
    ActiveStageInfo active =
        new LaunchVehicle(DRY_MASS, INJECTION_MASS - DRY_MASS, propulsion)
            .resolveActiveStage(INJECTION_MASS);
    FlightContext context =
        new FlightContext(
            GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN));

    // The ignition state a parking coast would have delivered, built here the way ParkingCoastStage
    // builds it: inject() is handed the state it really ignites at, never one it reconstructs.
    SpacecraftState ignitionState = ignitionState(parking, active, context);
    TranslunarInjectionPlan.Burn burn =
        TranslunarInjectionPlan.inject(
            ignitionState, parking, LunarFlybyMission.DEFAULT_PERILUNE_ALTITUDE, active, context);

    double impulsive = burn.plan().deltaV().getNorm();
    double surcharge = burn.commandedDeltaV() - impulsive;
    double arc = 360.0 * burn.duration() / parking.getOrbit().getKeplerianPeriod();

    SpacecraftState reference =
        burn.plan().applyTo(parking, propulsion.isp() * Constants.G0_STANDARD_GRAVITY);
    SpacecraftState flown = reflyBurn(ignitionState, burn, propulsion, context);
    double speed = reference.getPVCoordinates().getVelocity().getNorm();
    double energyGap = (energyOf(reference) - energyOf(flown)) / speed;

    logger.info(
        "{}: dt = {} s ({}° of arc), commanded {} m/s for {} m/s impulsive (+{}), mass {} -> {} kg,"
            + " energy gap {} m/s",
        label,
        String.format(Locale.ROOT, "%.1f", burn.duration()),
        String.format(Locale.ROOT, "%.1f", arc),
        String.format(Locale.ROOT, "%.1f", burn.commandedDeltaV()),
        String.format(Locale.ROOT, "%.1f", impulsive),
        String.format(Locale.ROOT, "%.2f", surcharge),
        String.format(Locale.ROOT, "%.0f", INJECTION_MASS),
        String.format(Locale.ROOT, "%.0f", burn.endMass()),
        String.format(Locale.ROOT, "%.4f", energyGap));

    assertEquals(
        0.0,
        energyGap,
        ENERGY_BAND_METERS_PER_SECOND,
        label + ": the finite burn must reach the specific energy of the impulse it replaces");
    assertEquals(
        LunarFlybyMission.DEFAULT_PERILUNE_ALTITUDE,
        burn.plan().perileneAltitude(),
        AIM_BAND_METERS,
        label
            + ": the aim converged against the burn, so the perilune the plan reports is the one"
            + " this burn flies");
    assertTrue(
        surcharge >= minSurcharge && surcharge <= maxSurcharge,
        () ->
            String.format(
                Locale.ROOT,
                "%s: the finite-burn surcharge is %.2f m/s, outside the [%.1f, %.1f] band this"
                    + " profile was measured in",
                label,
                surcharge,
                minSurcharge,
                maxSurcharge));
  }

  /**
   * The ignition point a parking coast stops at: one {@link TranslunarInjectionPlan#ignitionLead}
   * before the injection, which is what centres the burn on it.
   */
  private static SpacecraftState ignitionState(
      SpacecraftState parking, ActiveStageInfo active, FlightContext context) {
    double lead =
        TranslunarInjectionPlan.ignitionLead(
            parking, TranslunarInjectionPlan.departureFrom(parking), active);
    NumericalPropagator backwards =
        OrekitService.get().createOptimizationPropagator(context, OrekitService.SAFE_MAX_STEP);
    backwards.setInitialState(parking);
    return backwards.propagate(parking.getDate().shiftedBy(-lead));
  }

  /**
   * Flies the calibrated burn independently of the calibration, from the very state it was
   * calibrated to ignite at.
   *
   * <p>Verifying from a reconstructed ignition point measures the reconstruction and not the burn —
   * 0.31 m/s of it on the ULPM profile when this helper still assumed "half a burn early" (spec L6
   * §9.5).
   */
  private static SpacecraftState reflyBurn(
      SpacecraftState ignitionState,
      TranslunarInjectionPlan.Burn burn,
      PropulsionSystem propulsion,
      FlightContext context) {
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, OrekitService.SAFE_MAX_STEP);
    propagator.setInitialState(ignitionState);
    propagator.addForceModel(
        new ConstantThrustManeuver(
            ignitionState.getDate().shiftedBy(1.0e-3),
            burn.duration(),
            propulsion.thrust(),
            propulsion.isp(),
            new FrameAlignedProvider(
                new Rotation(burn.direction(), Vector3D.PLUS_I), ignitionState.getFrame()),
            Vector3D.PLUS_I));
    return propagator.propagate(ignitionState.getDate().shiftedBy(burn.duration() + 1.0e-3));
  }

  private static double energyOf(SpacecraftState state) {
    double speed = state.getPVCoordinates().getVelocity().getNorm();
    return 0.5 * speed * speed - state.getOrbit().getMu() / state.getPosition().getNorm();
  }
}
