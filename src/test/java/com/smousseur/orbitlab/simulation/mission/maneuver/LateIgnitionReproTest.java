package com.smousseur.orbitlab.simulation.mission.maneuver;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
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

/**
 * Regression tests for the late-ignition crash (spec 06 I6): a burn igniting long after the
 * propagation start restarts the integrator with a coast-sized step; if that trial step can drive
 * the mass negative, Orekit throws during the trial evaluation — before step-size control or any
 * event detector (burn cutoff, depletion guard) can react. The propagator factories therefore cap
 * the max step below mass(ignition)/massFlow of the strongest mid-propagation burn.
 */
class LateIgnitionReproTest {

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static SpacecraftState postGravityTurnState(AbsoluteDate date) {
    Frame gcrf = OrekitService.get().gcrf();
    // Post-GT hand-off from the failing mission log: alt ~30 km, vTan 7928.1, vRad 292.6.
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 29_988;
    return new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(292.6, 7_928.1, 0)),
                gcrf,
                date,
                Constants.WGS84_EARTH_MU))
        .withMass(16_238.103);
  }

  /**
   * Exact reproduction of the transfer-stage replay that died with "spacecraft mass is not
   * positive: -2,483 kg": burn 1 at t1 ≈ 1 585 s, circularization burn at ~4 315 s, depletion
   * guard and 1 s sampler, propagated far past the burns.
   */
  @Test
  void transferReplayIngredients_burnStopsOnSchedule() {
    AbsoluteDate date = new AbsoluteDate(2026, 1, 1, 12, 2, 41.09, TimeScalesFactory.getUTC());
    SpacecraftState state = postGravityTurnState(date);

    NumericalPropagator propagator = OrekitService.get().createOptimizationPropagator();
    propagator.setInitialState(state);
    propagator.addForceModel(
        new ConstantThrustManeuver(
            date.shiftedBy(1_585.7415985515743), 2.879144822549304, 981_000, 348,
            Vector3D.PLUS_I));
    propagator.addForceModel(
        new ConstantThrustManeuver(
            date.shiftedBy(4_315.152), 0.3767549809418964, 981_000, 348, Vector3D.PLUS_I));
    DepletionGuard.arm(propagator, 14_000.0, "repro");
    propagator.getMultiplexer().add(1.0, s -> {});

    SpacecraftState finalState = propagator.propagate(date.shiftedBy(4_315.53));

    // Both burns together consume ~936 kg; anything beyond means a cutoff was missed.
    assertEquals(16_238.103 - 936.0, finalState.getMass(), 50.0, "burns must stop on schedule");
  }

  /** Minimal form of the same hazard: one short burn after a long coast. */
  @Test
  void shortBurnAfterLongCoast_survivesIgnition() {
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
            .withMass(16_238.0);

    NumericalPropagator propagator = OrekitService.get().createOptimizationPropagator();
    propagator.setInitialState(state);
    propagator.addForceModel(
        new ConstantThrustManeuver(date.shiftedBy(1_585.7), 2.88, 981_000, 348, Vector3D.PLUS_I));

    SpacecraftState finalState = propagator.propagate(date.shiftedBy(4_000.0));

    assertEquals(16_238.0 - 287.4 * 2.88, finalState.getMass(), 20.0, "burn must consume ~828 kg");
  }
}
