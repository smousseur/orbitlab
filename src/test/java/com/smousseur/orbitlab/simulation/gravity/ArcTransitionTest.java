package com.smousseur.orbitlab.simulation.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import java.util.EnumSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * PHY-4 / L4 §7.3 — the boundary crossing: which context follows, and the state expressed in it.
 *
 * <p>The découpage asks for the millimetre and the µm/s on the continuity. Both selenocentric and
 * geocentric frames being ICRF-oriented, the transform is a pure translation and the round trip is
 * <b>exact</b> in position — so this class asks for exactly zero and says why (spec {@code
 * docs/multi-corps/06-conception-L4.md} §7.3).
 */
class ArcTransitionTest {
  private static final Logger logger = LogManager.getLogger(ArcTransitionTest.class);

  private static AbsoluteDate epoch;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 3, 4, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  /** A state on the Earth-Moon line, 66 000 km short of the Moon, in GCRF. */
  private static SpacecraftState nearTheBoundary() {
    Frame gcrf = OrekitService.get().gcrf();
    Vector3D moon = OrekitService.get().body(SolarSystemBody.MOON).getPosition(epoch, gcrf);
    Vector3D toMoon = moon.normalize();
    Vector3D position = moon.subtract(toMoon.scalarMultiply(66_000_000.0));
    Vector3D velocity = toMoon.scalarMultiply(1_000.0).add(new Vector3D(0.0, 0.0, 200.0));
    return new SpacecraftState(
        new CartesianOrbit(
            new TimeStampedPVCoordinates(epoch, position, velocity),
            gcrf,
            Constants.WGS84_EARTH_MU),
        1000.0);
  }

  @Test
  @DisplayName("The conversion round trip is exact in position")
  void roundTripIsExact() {
    SpacecraftState inGcrf = nearTheBoundary();
    GravitationalContext moon =
        GravitationalContext.moon().withPerturbers(SolarSystemBody.EARTH, SolarSystemBody.SUN);
    GravitationalContext earth =
        GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN);

    SpacecraftState inMoon = ArcTransition.convert(inGcrf, moon);
    SpacecraftState back = ArcTransition.convert(inMoon, earth);

    double dPos = back.getPosition().subtract(inGcrf.getPosition()).getNorm();
    double dVel =
        back.getPVCoordinates()
            .getVelocity()
            .subtract(inGcrf.getPVCoordinates().getVelocity())
            .getNorm();
    logger.info("Arc conversion round trip: dPos = {} m, dVel = {} m/s", dPos, dVel);

    // Exactly zero, and not a tolerance: a pure translation has nothing to round on the position.
    assertEquals(0.0, dPos, "the frames are ICRF-parallel, so this is a translation and its inverse");
    assertEquals(0.0, dVel, 1.0e-12);
    assertEquals(inGcrf.getMass(), back.getMass());
    assertEquals(0.0, back.getDate().durationFrom(inGcrf.getDate()));
  }

  @Test
  @DisplayName("Continuity: the two sides describe the same place in a common frame")
  void bothSidesCoincideInACommonFrame() {
    SpacecraftState outgoing = nearTheBoundary();
    GravitationalContext moon = GravitationalContext.moon();
    SpacecraftState incoming = ArcTransition.convert(outgoing, moon);

    // Brought back into GCRF by Orekit's own transform, independently of ArcTransition.
    Vector3D incomingInGcrf =
        incoming
            .getFrame()
            .getTransformTo(OrekitService.get().gcrf(), incoming.getDate())
            .transformPosition(incoming.getPosition());

    assertEquals(0.0, incomingInGcrf.subtract(outgoing.getPosition()).getNorm());
    assertEquals(moon.mu(), incoming.getOrbit().getMu(), "rebased on the new central body");
    assertSame(moon.inertialFrame(), incoming.getFrame());
  }

  @Test
  @DisplayName("Crossing inwards swaps the central body and keeps the Sun")
  void crossingInwardsDerivesTheLunarContext() {
    GravitationalContext earth =
        GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN);

    GravitationalContext lunar = ArcTransition.across(earth, SolarSystemBody.MOON);

    assertEquals(SolarSystemBody.MOON, lunar.body());
    assertEquals(
        EnumSet.of(SolarSystemBody.EARTH, SolarSystemBody.SUN),
        EnumSet.copyOf(lunar.perturbers()),
        "the body just left keeps perturbing, and the Sun crosses without being named");
    assertSame(GravitationalContext.moon().inertialFrame(), lunar.inertialFrame());
  }

  @Test
  @DisplayName("Crossing outwards gives the primary back, and the trip is symmetric")
  void crossingOutwardsReturnsToEarth() {
    GravitationalContext earth =
        GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN);

    GravitationalContext lunar = ArcTransition.across(earth, SolarSystemBody.MOON);
    GravitationalContext backToEarth = ArcTransition.across(lunar, SolarSystemBody.MOON);

    assertEquals(earth, backToEarth, "Earth -> Moon -> Earth returns the very same context");
  }

  @Test
  @DisplayName("An unperturbed arc still gets the body it just left")
  void unperturbedArcGainsTheOppositeBody() {
    GravitationalContext lunar =
        ArcTransition.across(GravitationalContext.earth(), SolarSystemBody.MOON);

    assertEquals(
        EnumSet.of(SolarSystemBody.EARTH),
        EnumSet.copyOf(lunar.perturbers()),
        "the rule is unconditional: leaving a body does not stop it pulling");
  }

  @Test
  @DisplayName("Keeping the new central body as its own perturber throws, and that is the point")
  void theNewCentralBodyIsRemovedNotTolerated() {
    // The removal in across() is what prevents this. Proving the guard exists proves the removal
    // is load-bearing rather than cosmetic (spec L2 §2.2).
    assertThrows(
        IllegalArgumentException.class,
        () -> GravitationalContext.moon().withPerturbers(SolarSystemBody.MOON));

    GravitationalContext lunar =
        ArcTransition.across(
            GravitationalContext.earth()
                .withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN),
            SolarSystemBody.MOON);
    assertTrue(!lunar.perturbers().contains(SolarSystemBody.MOON));
  }

  @Test
  @DisplayName("Converting to the frame already held is a no-op, by reference")
  void sameFrameIsNotConverted() {
    SpacecraftState inGcrf = nearTheBoundary();
    // Reference equality on the returned state: this is what keeps the L1 gate bit-identical when
    // the leg runner aligns an entry state that is already in the right frame (spec L4 §3.5).
    assertSame(inGcrf, ArcTransition.convert(inGcrf, GravitationalContext.earth()));
  }
}
