package com.smousseur.orbitlab.simulation.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * PHY-4 / L4 §7.2 — the crossing is detected at the right radius, from either side.
 *
 * <p><b>What is asserted is the geometry at the detected date, never the date itself.</b> Pinning a
 * literal date would only restate the implementation; asserting that {@code |r − r_Moon| = d·k}
 * holds where the detector stopped is an independent statement about the same event.
 */
class SoiCrossingDetectorTest {
  private static final Logger logger = LogManager.getLogger(SoiCrossingDetectorTest.class);

  /**
   * Resolved in {@code @BeforeAll} and not as a {@code static final}: {@code
   * TimeScalesFactory.getUTC()} needs the data archive, which a class initialiser would demand
   * before {@code OrekitService.initialize()} has run. Same trap as spec L1 §2.1.
   */
  private static AbsoluteDate epoch;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 3, 4, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  /** A state on the Earth-Moon line, {@code offsetFromMoon} short of the Moon, closing on it. */
  private static SpacecraftState approaching(double offsetFromMoon, double closingSpeed) {
    Frame gcrf = OrekitService.get().gcrf();
    Vector3D moon = OrekitService.get().body(SolarSystemBody.MOON).getPosition(epoch, gcrf);
    Vector3D toMoon = moon.normalize();
    Vector3D position = moon.subtract(toMoon.scalarMultiply(offsetFromMoon));
    // A little out-of-line velocity so the trajectory is not a degenerate radial fall.
    Vector3D velocity = toMoon.scalarMultiply(closingSpeed).add(new Vector3D(0.0, 0.0, 150.0));
    return new SpacecraftState(
        new CartesianOrbit(
            new TimeStampedPVCoordinates(epoch, position, velocity),
            gcrf,
            Constants.WGS84_EARTH_MU),
        1000.0);
  }

  @Test
  @DisplayName("Entering: the detector stops where |r - r_Moon| equals the Laplace radius")
  void entryIsDetectedOnTheSphere() {
    SphereOfInfluence soi = SphereOfInfluence.of(SolarSystemBody.MOON);
    SpacecraftState start = approaching(80_000_000.0, 1_000.0);

    AtomicReference<SpacecraftState> crossing = new AtomicReference<>();
    NumericalPropagator propagator =
        OrekitService.get()
            .createOptimizationPropagator(
                new FlightContext(
                    GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON)),
                OrekitService.COAST_MAX_STEP);
    propagator.setInitialState(start);
    propagator.addEventDetector(
        new SoiCrossingDetector(soi, 1.0)
            .withHandler(
                (state, detector, increasing) -> {
                  crossing.set(state);
                  return Action.STOP;
                }));

    SpacecraftState end = propagator.propagate(epoch.shiftedBy(6.0 * 86_400.0));

    assertNotNull(crossing.get(), "the trajectory must reach the sphere within six days");
    SpacecraftState atCrossing = crossing.get();

    Vector3D moonAtCrossing =
        OrekitService.get()
            .body(SolarSystemBody.MOON)
            .getPosition(atCrossing.getDate(), atCrossing.getFrame());
    double distanceToMoon = atCrossing.getPosition().subtract(moonAtCrossing).getNorm();
    double expected = soi.separationAt(atCrossing.getDate()) * soi.laplaceFactor();

    logger.info(
        "SOI entry at {} : |r-Moon| = {} km, Laplace radius = {} km",
        atCrossing.getDate(),
        Math.round(distanceToMoon / 1000.0),
        Math.round(expected / 1000.0));

    // The geometric condition, not the date. One metre is well above the millimetre the 1 ms
    // convergence brackets to at transfer speed, and well below anything a wrong radius would give.
    assertEquals(expected, distanceToMoon, 1.0);

    // The STOP is what ended the propagation — but not to the bit. Measured: the state Orekit
    // hands the handler and the state propagate() returns are 51 ps apart, because the returned
    // one is re-interpolated at the located root. The leg loop must therefore convert the RETURNED
    // state, which is also the one StageChainRunner has always threaded on.
    assertEquals(
        0.0,
        end.getDate().durationFrom(atCrossing.getDate()),
        1.0e-9,
        "the STOP is what ended the propagation");
    assertTrue(
        end.getDate().durationFrom(epoch) > 0.0, "the crossing is ahead of the start, not at it");
  }

  @Test
  @DisplayName("Leaving: the same class works from inside, where the Moon is the origin")
  void exitIsDetectedFromTheLunarFrame() {
    SphereOfInfluence soi = SphereOfInfluence.of(SolarSystemBody.MOON);
    Frame moonFrame = GravitationalContext.moon().inertialFrame();

    // Just inside the sphere, moving out along the Earth-Moon line.
    Vector3D awayFromEarth =
        OrekitService.get()
            .body(SolarSystemBody.MOON)
            .getPosition(epoch, OrekitService.get().gcrf())
            .normalize()
            .negate();
    double radius = soi.radiusAt(epoch);
    Vector3D position = awayFromEarth.scalarMultiply(radius - 5_000_000.0);
    Vector3D velocity = awayFromEarth.scalarMultiply(900.0);

    SpacecraftState start =
        new SpacecraftState(
            new CartesianOrbit(
                new TimeStampedPVCoordinates(epoch, position, velocity),
                moonFrame,
                GravitationalContext.moon().mu()),
            1000.0);

    AtomicReference<SpacecraftState> crossing = new AtomicReference<>();
    NumericalPropagator propagator =
        OrekitService.get()
            .createOptimizationPropagator(
                new FlightContext(
                    GravitationalContext.moon().withPerturbers(SolarSystemBody.EARTH)),
                OrekitService.COAST_MAX_STEP);
    propagator.setInitialState(start);
    propagator.addEventDetector(
        new SoiCrossingDetector(soi, 1.0 + SoiCrossingDetector.EXIT_DEAD_BAND)
            .withHandler(
                (state, detector, increasing) -> {
                  crossing.set(state);
                  return Action.STOP;
                }));

    propagator.propagate(epoch.shiftedBy(2.0 * 86_400.0));

    assertNotNull(crossing.get(), "the trajectory must leave the sphere within two days");
    SpacecraftState atCrossing = crossing.get();

    // In the lunar frame the Moon is the origin, so the distance is the position norm itself —
    // the same expression as the entry case, which is the point being proved.
    double distanceToMoon = atCrossing.getPosition().getNorm();
    double expected =
        soi.separationAt(atCrossing.getDate())
            * soi.laplaceFactor()
            * (1.0 + SoiCrossingDetector.EXIT_DEAD_BAND);

    logger.info(
        "SOI exit at {} : |r| = {} km, threshold with dead band = {} km",
        atCrossing.getDate(),
        Math.round(distanceToMoon / 1000.0),
        Math.round(expected / 1000.0));

    assertEquals(expected, distanceToMoon, 1.0);
  }

  @Test
  @DisplayName("The dead band is a real margin, and it sits above the root-finder's noise")
  void deadBandIsMeasurableButSmall() {
    SphereOfInfluence soi = SphereOfInfluence.of(SolarSystemBody.MOON);
    double margin = soi.radiusAt(epoch) * SoiCrossingDetector.EXIT_DEAD_BAND;

    assertTrue(margin > 5_000.0, "far above the metre the 1 ms convergence brackets to");
    assertTrue(margin < 30_000.0, "and far below anything that would misdate the exit");
  }

  @Test
  @DisplayName("The handler survives withHandler: create() carries both fields")
  void fieldsSurviveTheHandlerRebuild() {
    SphereOfInfluence soi = SphereOfInfluence.of(SolarSystemBody.MOON);
    SoiCrossingDetector rebuilt =
        new SoiCrossingDetector(soi, 1.0).withHandler((s, d, increasing) -> Action.STOP);

    // A field dropped in create() would show up as a wrong trajectory, never as an error — the trap
    // ReentryDetector documents. Reading the sphere back is the cheapest guard against it.
    assertEquals(soi, rebuilt.sphere());

    SpacecraftState onTheLine = approaching(80_000_000.0, 1_000.0);
    assertEquals(new SoiCrossingDetector(soi, 1.0).g(onTheLine), rebuilt.g(onTheLine));
  }

  @Test
  @DisplayName("g is positive outside the sphere and negative inside")
  void switchingFunctionSign() {
    SphereOfInfluence soi = SphereOfInfluence.of(SolarSystemBody.MOON);
    SoiCrossingDetector detector = new SoiCrossingDetector(soi, 1.0);

    assertTrue(detector.g(approaching(80_000_000.0, 1_000.0)) > 0.0, "outside");
    assertTrue(detector.g(approaching(40_000_000.0, 1_000.0)) < 0.0, "inside");
  }
}
