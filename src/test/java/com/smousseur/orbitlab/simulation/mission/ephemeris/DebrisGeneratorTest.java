package com.smousseur.orbitlab.simulation.mission.ephemeris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
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

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  private static SpacecraftState boosterSeparationState() {
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    // ~70 km altitude, ~2.3 km/s downrange with a shallow climb — a suborbital booster.
    Vector3D position = new Vector3D(6_448_137.0, 0.0, 0.0);
    Vector3D velocity = new Vector3D(150.0, 2_300.0, 0.0);
    return new SpacecraftState(
        new CartesianOrbit(
            new PVCoordinates(position, velocity),
            OrekitService.get().gcrf(),
            epoch,
            GravitationalContext.earth().mu()),
        22_000.0);
  }

  @Test
  void producesOneDebrisEphemerisStartingAtTheJettison() {
    SpacecraftState booster = boosterSeparationState();
    JettisonEvent event = new JettisonEvent(booster, new AerodynamicProperties(10.5, 0.4));

    List<DebrisTrack> tracks =
        new DebrisGenerator().generate(List.of(event), AtmosphereModel.NRLMSISE, 3_000.0);

    assertEquals(1, tracks.size(), "one event yields one track");
    MissionEphemeris ephemeris = tracks.get(0).ephemeris();
    assertEquals(
        booster.getDate(), ephemeris.startDate(), "the debris trajectory starts at the jettison");
    assertTrue(ephemeris.size() >= 2, "a drawable trajectory needs at least two samples");
  }

  @Test
  void theSuborbitalBoosterReentersToTheFloorWithinTheHorizon() {
    JettisonEvent event =
        new JettisonEvent(boosterSeparationState(), new AerodynamicProperties(10.5, 0.4));

    List<DebrisTrack> tracks =
        new DebrisGenerator().generate(List.of(event), AtmosphereModel.NRLMSISE, 3_000.0);

    MissionEphemeris ephemeris = tracks.get(0).ephemeris();
    assertTrue(
        ephemeris.lastPoint().altitudeMeters() < 1_000.0,
        () ->
            "the booster should reach the ground floor, ended at "
                + ephemeris.lastPoint().altitudeMeters()
                + " m");
  }
}
