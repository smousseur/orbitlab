package com.smousseur.orbitlab.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.models.earth.atmosphere.Atmosphere;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * Checks {@code Physics.dynamicPressure} against the atmosphere the mission actually flies,
 * resolved through the PHY-3 seam {@link OrekitService#atmosphere}. The brick reads density and air
 * velocity from that one instance; the tests hold it to the physics that make {@code Q(t)} worth
 * plotting.
 */
class DynamicPressureTest {

  private static final double RE = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static final double MU = Constants.WGS84_EARTH_MU;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static Atmosphere atmosphere() {
    return OrekitService.get().atmosphere(AtmosphereModel.NRLMSISE, GravitationalContext.earth());
  }

  private static SpacecraftState stateAt(double altitude, Vector3D velocity) {
    Vector3D position = new Vector3D(RE + altitude, 0, 0);
    return new SpacecraftState(
        new CartesianOrbit(
            new PVCoordinates(position, velocity),
            OrekitService.get().gcrf(),
            AbsoluteDate.J2000_EPOCH,
            MU));
  }

  /** Denser air low in the ascent means more pressure than the same speed high up. */
  @Test
  void dynamicPressure_fallsWithAltitude() {
    Atmosphere atmosphere = atmosphere();
    Vector3D velocity = new Vector3D(0, 2_000.0, 0);
    double low = Physics.dynamicPressure(stateAt(20_000.0, velocity), atmosphere);
    double high = Physics.dynamicPressure(stateAt(300_000.0, velocity), atmosphere);

    assertTrue(low > 0.0, "there is dynamic pressure at 20 km");
    assertTrue(high >= 0.0, "dynamic pressure is never negative");
    assertTrue(low > high, () -> "low altitude must feel more pressure: " + low + " vs " + high);
  }

  /**
   * The wiring: {@code ½ ρ v_rel²}, with density and relative velocity read from the atmosphere.
   */
  @Test
  void dynamicPressure_matchesHalfRhoVrelSquared() {
    Atmosphere atmosphere = atmosphere();
    SpacecraftState state = stateAt(40_000.0, new Vector3D(0, 1_500.0, 0));
    Vector3D position = state.getPVCoordinates().getPosition();
    double rho = atmosphere.getDensity(state.getDate(), position, state.getFrame());
    Vector3D vRel =
        state
            .getPVCoordinates()
            .getVelocity()
            .subtract(atmosphere.getVelocity(state.getDate(), position, state.getFrame()));
    double expected = 0.5 * rho * vRel.getNormSq();

    assertEquals(expected, Physics.dynamicPressure(state, atmosphere), expected * 1e-12 + 1e-12);
  }

  /**
   * The pressure is on the velocity <b>relative to the moving air</b>: a body drifting with the
   * atmosphere feels none, however dense the air it sits in.
   */
  @Test
  void dynamicPressure_isZero_whenMovingWithTheAir() {
    Atmosphere atmosphere = atmosphere();
    Vector3D position = new Vector3D(RE + 30_000.0, 0, 0);
    Vector3D air =
        atmosphere.getVelocity(AbsoluteDate.J2000_EPOCH, position, OrekitService.get().gcrf());

    assertTrue(air.getNorm() > 100.0, "the co-rotating air moves several hundred m/s low down");
    assertEquals(
        0.0,
        Physics.dynamicPressure(stateAt(30_000.0, air), atmosphere),
        1e-9,
        "a body drifting with the air feels no dynamic pressure");
  }
}
