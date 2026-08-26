package com.smousseur.orbitlab.simulation.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.ArcTransition;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The four types of PHY-1 / L1 (spec {@code docs/atmosphere/04-conception-L1.md} §2), and the one
 * property the lot exists to make true: everything is in place and nothing is switched on.
 */
class FlightContextTest {

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  @DisplayName("The Earth context has no drag, and shares the gravitational instance")
  void earth_hasNoDrag_andSharesTheGravitationalInstance() {
    FlightContext earth = FlightContext.earth();

    assertFalse(earth.hasDrag(), "nothing flies an atmosphere until PHY-2");
    assertNull(earth.drag());
    // The laziness of GravitationalContext.Holder is inherited, not duplicated: this is the very
    // instance every pre-PHY-1 site read, which is what the zero-tolerance baselines assert on.
    assertSame(GravitationalContext.earth(), earth.gravity());
    assertSame(GravitationalContext.moon(), FlightContext.moon().gravity());
  }

  @Test
  @DisplayName("A DragContext refuses NONE, so \"no drag\" has exactly one representation")
  void dragContext_refusesNone() {
    AerodynamicProperties aero = new AerodynamicProperties(10.0, 2.2);

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> new DragContext(aero, AtmosphereModel.NONE));
    assertTrue(thrown.getMessage().contains("NONE"));

    assertThrows(NullPointerException.class, () -> new DragContext(null, AtmosphereModel.NRLMSISE));
    assertThrows(NullPointerException.class, () -> new DragContext(aero, null));
  }

  @Test
  @DisplayName("The aerodynamic properties keep the surface first, as IsotropicDrag reads them")
  void aerodynamicProperties_holdTheSurfaceFirst() {
    AerodynamicProperties aero = new AerodynamicProperties(31.6, 0.4);

    assertEquals(31.6, aero.crossSection(), 1e-12);
    assertEquals(0.4, aero.dragCoefficient(), 1e-12);
    // A transposition would read as Cd 31.6 on a 0.4 m² section — a factor near 30 on the drag,
    // invisible to any assertion that does not name the two numbers apart.
    assertEquals(15_000 / (0.4 * 31.6), aero.ballisticCoefficient(15_000), 1e-9);

    assertThrows(IllegalArgumentException.class, () -> new AerodynamicProperties(0.0, 2.2));
    assertThrows(IllegalArgumentException.class, () -> new AerodynamicProperties(10.0, -1.0));
    assertThrows(IllegalArgumentException.class, () -> new AerodynamicProperties(Double.NaN, 2.2));
  }

  /**
   * The defect the découpage §3.6 conceded and left to MIS-5 — an aerodynamic half lost at a
   * sphere-of-influence crossing — does not exist under this composition. The context carries the
   * <em>model</em>, so it crosses untouched and is resolved against whichever body is being flown
   * around when the next propagator is built.
   */
  @Test
  @DisplayName("The aerodynamic half survives an Earth → Moon → Earth round trip")
  void dragSurvivesASphereOfInfluenceRoundTrip() {
    DragContext drag =
        new DragContext(new AerodynamicProperties(9.0, 2.2), AtmosphereModel.NRLMSISE);
    FlightContext outbound =
        new FlightContext(GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON), drag);

    FlightContext atTheMoon =
        outbound.withGravity(ArcTransition.across(outbound.gravity(), SolarSystemBody.MOON));
    assertEquals(SolarSystemBody.MOON, atTheMoon.gravity().body());
    assertSame(drag, atTheMoon.drag(), "the model crosses; only the body changes");

    FlightContext backHome =
        atTheMoon.withGravity(ArcTransition.across(atTheMoon.gravity(), SolarSystemBody.MOON));
    assertEquals(SolarSystemBody.EARTH, backHome.gravity().body());
    assertSame(drag, backHome.drag(), "and it is still there on the way back");
  }

  @Test
  @DisplayName("withDrag and the gravity-only constructor are each other's inverse")
  void withDrag_andTheGravityOnlyConstructor() {
    DragContext drag =
        new DragContext(new AerodynamicProperties(9.0, 2.2), AtmosphereModel.HARRIS_PRIESTER);

    FlightContext on = FlightContext.earth().withDrag(drag);
    assertTrue(on.hasDrag());
    assertSame(GravitationalContext.earth(), on.gravity());

    assertFalse(new FlightContext(on.gravity()).hasDrag());
    assertThrows(NullPointerException.class, () -> FlightContext.earth().withDrag(null));
    assertThrows(NullPointerException.class, () -> new FlightContext(null, drag));
  }
}
