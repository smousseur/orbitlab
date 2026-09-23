package com.smousseur.orbitlab.simulation.mission.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.disposal.DisposalFixtures;
import com.smousseur.orbitlab.simulation.mission.runtime.StageChainRunner;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * The deorbit burn on its own: a tracking retrograde burn on the payload's engine, ending on its
 * own date — or earlier, on the target perigee.
 */
class DeorbitBurnStageTest {

  private static final double TARGET_PERIGEE_ALTITUDE = 50_000.0;

  private static final double ORBIT_ALTITUDE = 400_000.0;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  void burnsRetrogradeInPlaneUntilItsOwnEndDate() {
    PayloadModel model = Payloads.EARTH_OBSERVATION_SAT;
    double dry = model.defaultDryMass();
    double reserve = 500.0;
    Mission mission = DisposalFixtures.payloadMission(model.toSpacecraft(dry, 0, reserve));
    SpacecraftState entry =
        DisposalFixtures.orbitState(ORBIT_ALTITUDE, ORBIT_ALTITUDE, 0.0, dry + reserve);
    double duration = 600.0;
    DeorbitBurnStage burn =
        new DeorbitBurnStage(StageNames.DEORBIT_BURN, duration, TARGET_PERIGEE_ALTITUDE);

    SpacecraftState end = StageChainRunner.plain().run(List.of(burn), entry, mission);
    SpacecraftState coasted =
        StageChainRunner.plain()
            .run(List.of(new CoastingStage("Reference coast", duration)), entry, mission);

    AbsoluteDate scheduledEnd = entry.getDate().shiftedBy(duration);
    assertTrue(burn.isPropulsive());
    assertEquals(scheduledEnd, burn.getConfiguredEndDate());
    assertEquals(0.0, end.getDate().durationFrom(scheduledEnd), 1.0e-9);

    // The engine lights one millisecond after the stage entry, so the last millisecond of the
    // scheduled duration is never burnt.
    PropulsionSystem engine = model.propulsion();
    double massFlow = engine.thrust() / (engine.isp() * Constants.G0_STANDARD_GRAVITY);
    assertEquals(entry.getMass() - (duration - 1.0e-3) * massFlow, end.getMass(), 1.0e-3);

    // Measured against the same flight without thrust, so that drag and J2 cancel out: the burn
    // takes energy away and leaves the plane where it was.
    KeplerianOrbit burnt = DisposalFixtures.keplerian(end);
    KeplerianOrbit ballistic = DisposalFixtures.keplerian(coasted);
    assertTrue(
        burnt.getA() < ballistic.getA() - 10_000.0,
        "a retrograde burn lowers the semi-major axis: "
            + burnt.getA()
            + " vs "
            + ballistic.getA());
    assertEquals(ballistic.getI(), burnt.getI(), 1.0e-5, "a tracking burn stays in plane");
  }

  @Test
  void cutsOffOnTheTargetPerigee() {
    PayloadModel model = Payloads.GEO_SAT;
    double dry = model.defaultDryMass();
    // Sized for a 0 km perigee, so a single burn of the whole reserve goes well past 50 km.
    double reserve = PropellantBudget.disposalReserveFor(model, dry, ORBIT_ALTITUDE, 0.0);
    PropulsionSystem engine = model.propulsion();
    double duration = reserve / (engine.thrust() / (engine.isp() * Constants.G0_STANDARD_GRAVITY));
    Mission mission = DisposalFixtures.payloadMission(model.toSpacecraft(dry, 0, reserve));
    SpacecraftState entry =
        DisposalFixtures.orbitState(ORBIT_ALTITUDE, ORBIT_ALTITUDE, 0.0, dry + reserve);
    DeorbitBurnStage burn =
        new DeorbitBurnStage(StageNames.DEORBIT_BURN, duration, TARGET_PERIGEE_ALTITUDE);

    SpacecraftState end = StageChainRunner.plain().run(List.of(burn), entry, mission);

    assertTrue(
        end.getDate().durationFrom(entry.getDate()) < duration - 1.0,
        "the cutoff ends the burn before its scheduled duration");
    double perigee = DisposalFixtures.perigeeAltitude(end);
    assertTrue(perigee <= TARGET_PERIGEE_ALTITUDE, "perigee " + perigee + " m above the target");
    assertTrue(
        perigee > TARGET_PERIGEE_ALTITUDE - 1_000.0,
        "perigee " + perigee + " m: the cutoff overshot the target");
  }
}
