package com.smousseur.orbitlab.simulation.mission.detector;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

class DepletionGuardTest {

  /** 100 kN at 300 s Isp → mass flow ≈ 34 kg/s. */
  private static final double THRUST = 100_000;

  private static final double ISP = 300;
  private static final double FLOOR = 10_000;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static SpacecraftState leoState(AbsoluteDate date, double mass) {
    Frame gcrf = OrekitService.get().gcrf();
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 400_000;
    double v = Math.sqrt(Constants.WGS84_EARTH_MU / r);
    return new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(0, v, 0)),
                gcrf,
                date,
                Constants.WGS84_EARTH_MU))
        .withMass(mass);
  }

  @Test
  void oversizedBurn_stoppedAtDepletionFloor() {
    AbsoluteDate date = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    // 500 kg of propellant above the floor, but a 60 s window would burn ~2 040 kg.
    SpacecraftState state = leoState(date, FLOOR + 500);

    NumericalPropagator propagator = OrekitService.get().createSimplePropagator();
    propagator.setInitialState(state);
    propagator.addForceModel(
        new ConstantThrustManeuver(date.shiftedBy(1.0e-3), 60.0, THRUST, ISP, Vector3D.PLUS_I));
    DepletionGuard.arm(propagator, FLOOR, "test");

    SpacecraftState finalState = propagator.propagate(date.shiftedBy(61.0));

    assertEquals(FLOOR, finalState.getMass(), 1.0, "propagation must stop at the depletion floor");
    assertTrue(
        finalState.getDate().durationFrom(date) < 20.0,
        "must stop well before the scheduled window end (~14.7 s depletion)");
  }

  @Test
  void nominalBurn_untouchedByGuard() {
    AbsoluteDate date = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    // 5 000 kg of propellant above the floor: the 60 s burn (~2 040 kg) fits comfortably.
    SpacecraftState state = leoState(date, FLOOR + 5_000);

    NumericalPropagator propagator = OrekitService.get().createSimplePropagator();
    propagator.setInitialState(state);
    propagator.addForceModel(
        new ConstantThrustManeuver(date.shiftedBy(1.0e-3), 60.0, THRUST, ISP, Vector3D.PLUS_I));
    DepletionGuard.arm(propagator, FLOOR, "test");

    SpacecraftState finalState = propagator.propagate(date.shiftedBy(61.0));

    assertEquals(61.0, finalState.getDate().durationFrom(date), 1e-6, "full window must run");
    assertTrue(
        finalState.getMass() > FLOOR + 2_900,
        "mass must reflect the nominal 2 040 kg consumption only");
  }
}
