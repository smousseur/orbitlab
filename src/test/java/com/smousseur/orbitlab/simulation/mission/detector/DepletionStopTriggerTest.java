package com.smousseur.orbitlab.simulation.mission.detector;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.forces.maneuvers.Maneuver;
import org.orekit.forces.maneuvers.propulsion.BasicConstantThrustPropulsionModel;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

class DepletionStopTriggerTest {

  /** 100 kN at 300 s Isp → mass flow ≈ 34 kg/s → 500 kg depleted in ~14.7 s. */
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

  @Test
  void thrustStopsAtDepletion_propagationContinues() {
    AbsoluteDate date = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    Frame gcrf = OrekitService.get().gcrf();
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 400_000;
    double v = Math.sqrt(Constants.WGS84_EARTH_MU / r);
    SpacecraftState state =
        new SpacecraftState(
                new CartesianOrbit(
                    new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(0, v, 0)),
                    gcrf,
                    date,
                    Constants.WGS84_EARTH_MU))
            .withMass(FLOOR + 500);

    NumericalPropagator propagator = OrekitService.get().createSimplePropagator();
    propagator.setInitialState(state);
    // No date window: the engine ignites at t+1 ms and burns until flame-out.
    propagator.addForceModel(
        new Maneuver(
            null,
            new DepletionStopTrigger(date.shiftedBy(1.0e-3), FLOOR),
            new BasicConstantThrustPropulsionModel(THRUST, ISP, Vector3D.PLUS_I, "test-burn")));

    SpacecraftState finalState = propagator.propagate(date.shiftedBy(60.0));

    // Unlike the DepletionGuard, the trigger only cuts the thrust: the propagation must reach
    // its target date with the mass frozen at the depletion floor (~14.7 s analytic flame-out).
    assertEquals(60.0, finalState.getDate().durationFrom(date), 1e-6, "propagation must complete");
    assertEquals(FLOOR, finalState.getMass(), 1.0, "mass must freeze at the depletion floor");
  }
}
