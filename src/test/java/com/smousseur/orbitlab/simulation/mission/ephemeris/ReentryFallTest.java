package com.smousseur.orbitlab.simulation.mission.ephemeris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.disposal.DeorbitSequence;
import com.smousseur.orbitlab.simulation.mission.disposal.DeorbitTail;
import com.smousseur.orbitlab.simulation.mission.disposal.DisposalFixtures;
import com.smousseur.orbitlab.simulation.mission.stage.StageNames;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;

/**
 * The re-entry recipe on its own: from where a disposal tail ends, the payload falls to the
 * geodetic ground; from an orbit that cannot decay in time, the fall stops on its bound.
 */
class ReentryFallTest {

  /** The altitude below which the renderer reads a last sample as landed. */
  private static final double LANDED_ALTITUDE_METERS = 1000.0;

  private static final PayloadModel MODEL = Payloads.EARTH_OBSERVATION_SAT;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  void theFallFromATailEndReachesTheGround() {
    double dry = MODEL.defaultDryMass();
    double reserve =
        PropellantBudget.disposalReserveFor(
            MODEL, dry, 400_000.0, DeorbitTail.REENTRY_PERIGEE_ALTITUDE_M);
    Mission mission = DisposalFixtures.payloadMission(MODEL.toSpacecraft(dry, 0, reserve));
    SpacecraftState horizon = DisposalFixtures.orbitState(400_000.0, 400_000.0, 0.0, dry + reserve);
    DeorbitSequence sequence = new DeorbitTail().plan(horizon, mission);
    SpacecraftState start = sequence.finalState();
    double bound = start.getOrbit().getKeplerianPeriod();

    MissionEphemeris fall =
        ReentryFall.fly(
            start, MODEL.aerodynamics(), AtmosphereModel.NRLMSISE, bound, StageNames.REENTRY);

    assertTrue(fall.isComplete());
    assertTrue(
        fall.lastPoint().altitudeMeters() < LANDED_ALTITUDE_METERS,
        "last sample at " + fall.lastPoint().altitudeMeters() + " m");
    assertTrue(fall.endDate().durationFrom(start.getDate()) < bound, "landed within the bound");
    for (MissionEphemerisPoint point : fall.allPoints()) {
      assertEquals(StageNames.REENTRY, point.stageName());
      assertFalse(point.propulsive());
      assertEquals(start.getMass(), point.mass(), 0.0);
    }
  }

  @Test
  void aFallThatCannotLandStopsOnItsBound() {
    double mass = MODEL.defaultDryMass();
    SpacecraftState start = DisposalFixtures.orbitState(400_000.0, 400_000.0, 0.0, mass);
    double bound = start.getOrbit().getKeplerianPeriod();

    MissionEphemeris fall =
        ReentryFall.fly(
            start, MODEL.aerodynamics(), AtmosphereModel.NRLMSISE, bound, StageNames.REENTRY);

    assertTrue(fall.isComplete(), "reaching the bound is not a truncation");
    assertEquals(0.0, fall.endDate().durationFrom(start.getDate().shiftedBy(bound)), 1.0e-6);
    assertTrue(
        fall.lastPoint().altitudeMeters() > LANDED_ALTITUDE_METERS,
        "still in orbit at " + fall.lastPoint().altitudeMeters() + " m");
  }
}
