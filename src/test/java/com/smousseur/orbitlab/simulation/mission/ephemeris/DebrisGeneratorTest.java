package com.smousseur.orbitlab.simulation.mission.ephemeris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.PVCoordinates;

class DebrisGeneratorTest {

  /** One Falcon Heavy side booster: 10.5 m² Cd 0.4, 22 t dry. */
  private static final double UNIT_SECTION = 10.5;

  private static final double UNIT_DRY_MASS = 22_000.0;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  /** A booster block of {@code m} exemplars at a ~70 km, ~2.3 km/s suborbital separation state. */
  private static JettisonEvent boosterBlockEvent(int m) {
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    Vector3D position = new Vector3D(6_448_137.0, 0.0, 0.0);
    Vector3D velocity = new Vector3D(150.0, 2_300.0, 0.0);
    SpacecraftState state =
        new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(position, velocity),
                OrekitService.get().gcrf(),
                epoch,
                GravitationalContext.earth().mu()),
            m * UNIT_DRY_MASS);
    return new JettisonEvent(
        state,
        new AerodynamicProperties(m * UNIT_SECTION, 0.4),
        m * UNIT_DRY_MASS,
        m,
        StageRole.BOOSTER);
  }

  @Test
  void splitsABlockIntoOneTrackPerExemplar() {
    List<DebrisTrack> tracks =
        new DebrisGenerator()
            .generate(List.of(boosterBlockEvent(2)), AtmosphereModel.NRLMSISE, 3_000.0);

    assertEquals(2, tracks.size(), "a block of two yields two tracks");
    assertEquals(StageRole.BOOSTER, tracks.get(0).role());
    assertEquals(1, tracks.get(0).exemplarIndex());
    assertEquals(2, tracks.get(1).exemplarIndex());
  }

  @Test
  void eachExemplarCarriesItsShareOfTheMass() {
    List<DebrisTrack> tracks =
        new DebrisGenerator()
            .generate(List.of(boosterBlockEvent(2)), AtmosphereModel.NRLMSISE, 3_000.0);

    assertEquals(
        UNIT_DRY_MASS,
        tracks.get(0).ephemeris().firstPoint().mass(),
        1.0,
        "the 44 t block splits into two 22 t exemplars");
  }

  @Test
  void aSingleSuborbitalPieceReentersToTheFloor() {
    List<DebrisTrack> tracks =
        new DebrisGenerator()
            .generate(List.of(boosterBlockEvent(1)), AtmosphereModel.NRLMSISE, 3_000.0);

    assertEquals(1, tracks.size());
    MissionEphemeris ephemeris = tracks.get(0).ephemeris();
    assertTrue(ephemeris.size() >= 2, "a drawable trajectory needs at least two samples");
    assertTrue(
        ephemeris.lastPoint().altitudeMeters() < 1_000.0,
        () ->
            "the piece should reach the ground, ended at "
                + ephemeris.lastPoint().altitudeMeters());
  }
}
