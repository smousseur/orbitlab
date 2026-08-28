package com.smousseur.orbitlab.simulation.mission.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.gravity.SoiCrossingDetector;
import com.smousseur.orbitlab.simulation.gravity.SphereOfInfluence;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.runtime.StageChainRunner;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assumptions;
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
 * MIS-5 / L1 §6.2 — the two shapes of the translunar coast, on a synthetic approach.
 *
 * <p>Twin of {@link ParkingCoastStageTest}, and for the same reason: the stage walk and the
 * ephemeris pass fly the same phase through two different entry points, and nothing but a test
 * keeps them saying the same thing. There the risk was a coast collapsing to zero duration; here it
 * is two passes stopping at two different places on the same boundary.
 *
 * <p><b>The one-argument constructor is asserted, not assumed.</b> MIS-4's flight is expected to be
 * unchanged to the digit, and the mechanism that keeps it so is that its coast declares nothing and
 * still advances the stage walk by nothing. That is one assertion, and it costs a second — cheaper
 * than re-flying seven days to find out (spec {@code docs/lunar-orbit/03-conception-L1.md} §6.2).
 */
class TranslunarCoastStageTest {
  private static final Logger logger = LogManager.getLogger(TranslunarCoastStageTest.class);

  /** Bound on the approach coast: far past the crossing the fixture reaches. */
  private static final double BOUND_SECONDS = 2.0 * 86_400.0;

  /**
   * How far apart the two passes may stop and still be read as the same stop (s).
   *
   * <p>The definition of {@code StageLegRunner.BOUNDARY_STOP_TOLERANCE}, which is package-private
   * there: twice the detector's own date convergence, and the only quantity that can bound the gap
   * between two independently interpolated readings of one root (PHY-4 / L6 §12).
   */
  private static final double BOUNDARY_STOP_TOLERANCE =
      2.0 * SoiCrossingDetector.DATE_CONVERGENCE_SECONDS;

  private static AbsoluteDate epoch;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 3, 4, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  @DisplayName("The open-ended coast declares nothing and advances the stage walk by nothing")
  void theTerminalCoastIsUntouched() {
    TranslunarCoastStage coast = new TranslunarCoastStage("Coasting");
    Mission mission = missionWith(List.of(coast));
    SpacecraftState start = closingOnTheMoon();

    assertFalse(
        coast.soiCrossingEndsStage(mission),
        "MIS-4's coast must be cut into legs at the sphere, not ended by it");

    SpacecraftState flown = coast.propagateStandalone(start, mission);
    assertEquals(
        0.0,
        flown.getDate().durationFrom(start.getDate()),
        0.0,
        "if this ever fails, MIS-4's restitution horizon has moved and its flight is 3 d short");
  }

  @Test
  @DisplayName("The bounded coast stops at the sphere, and both passes stop at the same instant")
  void bothPassesStopOnTheSphere() {
    SpacecraftState start = closingOnTheMoon();

    TranslunarCoastStage walkCoast = new TranslunarCoastStage("Translunar coast", BOUND_SECONDS);
    Mission walkMission = missionWith(List.of(walkCoast));
    walkMission.setCurrentState(start);
    SpacecraftState walkExit = walkCoast.propagateStandalone(start, walkMission);

    TranslunarCoastStage chainCoast = new TranslunarCoastStage("Translunar coast", BOUND_SECONDS);
    Mission chainMission = missionWith(List.of(chainCoast));
    SpacecraftState chainExit =
        StageChainRunner.sampling(null, 0.0, null).run(List.of(chainCoast), start, chainMission);

    double flownHours = walkExit.getDate().durationFrom(start.getDate()) / 3600.0;
    double dateGap = chainExit.getDate().durationFrom(walkExit.getDate());
    double positionGap = Vector3D.distance(walkExit.getPosition(), chainExit.getPosition());
    logger.info(
        "Both passes stopped {} h in; date gap {} s, position gap {} m",
        String.format(Locale.ROOT, "%.3f", flownHours),
        String.format(Locale.ROOT, "%.3e", dateGap),
        String.format(Locale.ROOT, "%.3e", positionGap));

    // Teeth: a coast that stopped at its bound, or at once, would make the agreement meaningless.
    assertTrue(
        flownHours > 1.0 && walkExit.getDate().durationFrom(start.getDate()) < 0.5 * BOUND_SECONDS,
        "the fixture must cross well inside its bound, got " + flownHours + " h");

    Vector3D toMoon =
        OrekitService.get()
            .body(SolarSystemBody.MOON)
            .getPosition(walkExit.getDate(), walkExit.getFrame());
    assertEquals(
        SphereOfInfluence.of(SolarSystemBody.MOON).radiusAt(walkExit.getDate()),
        walkExit.getPosition().subtract(toMoon).getNorm(),
        1_000.0,
        "the stage walk must stop ON the sphere, not at its bound");

    assertEquals(
        0.0,
        dateGap,
        BOUNDARY_STOP_TOLERANCE,
        "the two passes must read the same crossing, got " + dateGap + " s apart");
    // The position gap is logged rather than bounded by a guessed number: same integrator settings,
    // same detector, same field, so it is the date gap carried at transfer speed and nothing else.
  }

  /**
   * A state 100 000 km short of the Moon and closing on it, with the Moon's own velocity plus a
   * relative approach — the shape {@code SoiRoundTripFlightTest} settled on, because a shot aimed
   * at where the Moon is now misses the one that has moved during the crossing.
   */
  private static SpacecraftState closingOnTheMoon() {
    Frame gcrf = OrekitService.get().gcrf();
    TimeStampedPVCoordinates moon =
        OrekitService.get().body(SolarSystemBody.MOON).getPVCoordinates(epoch, gcrf);
    Vector3D toMoon = moon.getPosition().normalize();
    Vector3D position = moon.getPosition().subtract(toMoon.scalarMultiply(100_000_000.0));
    Vector3D transverse = Vector3D.crossProduct(toMoon, Vector3D.PLUS_K).normalize();
    Vector3D velocity =
        moon.getVelocity().add(toMoon.scalarMultiply(700.0)).add(transverse.scalarMultiply(200.0));
    return new SpacecraftState(
        new CartesianOrbit(
            new TimeStampedPVCoordinates(epoch, position, velocity),
            gcrf,
            Constants.WGS84_EARTH_MU),
        1000.0);
  }

  /** Earth-centred with the Moon and the Sun, which is what the crossing is derived from. */
  private static Mission missionWith(List<MissionStage> stages) {
    return new Mission("translunar coast test", null, stages, null) {
      @Override
      public SpacecraftState getInitialState(AbsoluteDate initialDate) {
        return null;
      }

      @Override
      public GravitationalContext gravitationalContext() {
        return GravitationalContext.earth()
            .withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN);
      }
    };
  }
}
