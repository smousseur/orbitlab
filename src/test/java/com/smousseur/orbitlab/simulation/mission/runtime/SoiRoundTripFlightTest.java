package com.smousseur.orbitlab.simulation.mission.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.gravity.SphereOfInfluence;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.ConstantThrustStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
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
 * PHY-4 / L4 §7.4 — the test of the lot. A trajectory that flies Earth → Moon → Earth through the
 * leg runner, against the same trajectory flown end to end in one geocentric propagation with the
 * same forces.
 *
 * <p><b>The two agree only because both sides declare the opposite body</b> (spec {@code
 * docs/multi-corps/06-conception-L4.md} §4.2). Drop the Sun on one side and they part company by
 * kilometres — measured 7 249 m over six hours, which is the solar tide on the Earth-Moon system
 * (2·µ_S·d/D³ ≈ 3.0e-5 m/s², 7.0 km in ½at²) and not a defect. That contrast is logged rather than
 * asserted, in the shape L2 §5.2 gave its four propagations: separating the contributions is what
 * makes a half-wrong wiring visible where a single tolerance would swallow it.
 */
class SoiRoundTripFlightTest {
  private static final Logger logger = LogManager.getLogger(SoiRoundTripFlightTest.class);

  private static final double COAST_SECONDS = 3.0 * 86_400.0;

  private static AbsoluteDate epoch;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 3, 4, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  /** A minimal mission: the coast reads nothing else off it. */
  private static final class BallisticMission extends Mission {
    private final SpacecraftState initial;

    private BallisticMission(SpacecraftState initial, List<MissionStage> stages) {
      super("SOI round trip", null, stages, null);
      this.initial = initial;
    }

    @Override
    public SpacecraftState getInitialState(AbsoluteDate initialDate) {
      return initial;
    }
  }

  /** A coast that flies the Earth perturbed by Moon and Sun, and may cross the lunar SOI. */
  private static class CrossingCoast extends CoastingStage {
    CrossingCoast(String name, double seconds) {
      super(name, seconds);
    }

    @Override
    public GravitationalContext gravitationalContext(Mission mission) {
      return GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN);
    }

    @Override
    public Set<SolarSystemBody> soiTransitions(Mission mission) {
      return Set.of(SolarSystemBody.MOON);
    }
  }

  /**
   * A state {@code offsetFromMoon} short of the Moon, closing on it.
   *
   * <p><b>The velocity is the Moon's plus a relative approach</b>, not an absolute aim. The Moon
   * covers some 130 000 km in the day and a half a geocentric shot takes to climb out there, so a
   * trajectory aimed at where it is now simply misses — which is what the first version of this
   * fixture did, and what a real translunar injection avoids by arriving with a velocity close to
   * the Moon's own.
   *
   * @param offsetFromMoon how far short of the Moon to start, on the Earth-Moon line
   * @param closingSpeed the approach speed relative to the Moon
   * @param transverseSpeed the transverse component, which sets how far the flyby passes
   */
  private static SpacecraftState aimedAtTheMoon(
      double offsetFromMoon, double closingSpeed, double transverseSpeed) {
    Frame gcrf = OrekitService.get().gcrf();
    TimeStampedPVCoordinates moon =
        OrekitService.get().body(SolarSystemBody.MOON).getPVCoordinates(epoch, gcrf);
    Vector3D toMoon = moon.getPosition().normalize();
    Vector3D position = moon.getPosition().subtract(toMoon.scalarMultiply(offsetFromMoon));
    Vector3D transverse = Vector3D.crossProduct(toMoon, Vector3D.PLUS_K).normalize();
    Vector3D velocity =
        moon.getVelocity()
            .add(toMoon.scalarMultiply(closingSpeed))
            .add(transverse.scalarMultiply(transverseSpeed));
    return new SpacecraftState(
        new CartesianOrbit(
            new TimeStampedPVCoordinates(epoch, position, velocity),
            gcrf,
            Constants.WGS84_EARTH_MU),
        1000.0);
  }

  /** The fixture the crossing tests fly: enters the lunar sphere, swings by, leaves again. */
  private static SpacecraftState flybyFixture() {
    return aimedAtTheMoon(100_000_000.0, 700.0, 200.0);
  }

  /** Every sample the runner produced, with the context it was flown in. */
  private record Sample(AbsoluteDate date, FlightContext context, Vector3D position) {}

  private static List<Sample> flyThroughTheLegRunner(MissionStage coast, SpacecraftState start) {
    List<Sample> samples = new ArrayList<>();
    Mission mission = new BallisticMission(start, List.of(coast));
    StageChainRunner.sampling(
            (stage, context, state) ->
                samples.add(new Sample(state.getDate(), context, state.getPosition())),
            0.0,
            null)
        .run(List.of(coast), start, mission);
    return samples;
  }

  /** The same trajectory, in one geocentric propagation, with the same force model. */
  private static SpacecraftState flyStraightThrough(SpacecraftState start, double seconds) {
    NumericalPropagator propagator =
        OrekitService.get()
            .createOptimizationPropagator(
                new FlightContext(
                    GravitationalContext.earth()
                        .withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN)),
                OrekitService.COAST_MAX_STEP);
    propagator.setInitialState(start);
    return propagator.propagate(start.getDate().shiftedBy(seconds));
  }

  @Test
  @DisplayName(
      "Earth to Moon to Earth: three arcs, and the same trajectory as a single geocentric flight")
  void roundTripMatchesTheSingleFrameFlight() {
    SpacecraftState start = flybyFixture();
    CrossingCoast coast = new CrossingCoast("lunar flyby", COAST_SECONDS);

    List<Sample> samples = flyThroughTheLegRunner(coast, start);

    List<SolarSystemBody> arcBodies = new ArrayList<>();
    for (Sample sample : samples) {
      if (arcBodies.isEmpty()
          || arcBodies.get(arcBodies.size() - 1) != sample.context().gravity().body()) {
        arcBodies.add(sample.context().gravity().body());
      }
    }
    logger.info("Arcs flown, in order: {}", arcBodies);

    assertEquals(
        List.of(SolarSystemBody.EARTH, SolarSystemBody.MOON, SolarSystemBody.EARTH),
        arcBodies,
        "the trajectory enters the lunar sphere and leaves it again");

    // Both comparisons propagate the reference to the SAME date as the sample being compared, and
    // that is not pedantry: the first version of this test compared the last sample against a
    // reference flown for the nominal coast duration, 3.6 s further on, and read the resulting
    // 4 032 m as physics. At transfer speed 3.6 s IS 3.6 km. Measured properly, the two flights
    // agree to about ten metres either side of the flyby.
    Sample exit = lastSampleOfArc(samples, SolarSystemBody.MOON);
    SpacecraftState reference =
        flyStraightThrough(start, exit.date().durationFrom(start.getDate()));

    double gapAtExit = inGcrf(exit).subtract(reference.getPosition()).getNorm();
    logger.info(
        "At the outbound crossing ({} h in): |dPos| against the single-frame flight = {} m",
        Math.round(exit.date().durationFrom(start.getDate()) / 3600.0),
        String.format(java.util.Locale.ROOT, "%.3f", gapAtExit));

    Sample last = samples.get(samples.size() - 1);
    SpacecraftState referenceAtEnd =
        flyStraightThrough(start, last.date().durationFrom(start.getDate()));
    double gapAtEnd = inGcrf(last).subtract(referenceAtEnd.getPosition()).getNorm();
    logger.info(
        "At the end of the {} day coast, after the flyby: |dPos| = {} m",
        (int) (COAST_SECONDS / 86_400.0),
        String.format(java.util.Locale.ROOT, "%.3f", gapAtEnd));

    // Measured 9.55 m and 10.37 m on 2026-08-17. Fifty metres leaves five times the observed
    // divergence, which is room for another epoch and not for a wrong wiring: dropping the Sun on
    // one side alone costs kilometres (see the class javadoc), and dropping the opposite body
    // entirely costs far more.
    assertTrue(
        gapAtExit < 50.0,
        "the two flights must describe the same trajectory across the switch, got "
            + gapAtExit
            + " m");
    assertTrue(gapAtEnd < 50.0, "and still afterwards, got " + gapAtEnd + " m");
  }

  @Test
  @DisplayName("A near-tangential encounter produces one clean pair of crossings")
  void grazingTrajectoryIsBounded() {
    // Skimming the sphere: placed a kilometre inside it, moving tangentially at close to the local
    // circular speed, so the boundary is left almost along itself rather than crossed head-on — the
    // shallowest crossing geometry the detector has to resolve.
    Frame gcrf = OrekitService.get().gcrf();
    TimeStampedPVCoordinates moon =
        OrekitService.get().body(SolarSystemBody.MOON).getPVCoordinates(epoch, gcrf);
    SphereOfInfluence soi = SphereOfInfluence.of(SolarSystemBody.MOON);
    Vector3D toMoon = moon.getPosition().normalize();
    Vector3D tangential = Vector3D.crossProduct(toMoon, Vector3D.PLUS_K).normalize();

    Vector3D position =
        moon.getPosition().subtract(toMoon.scalarMultiply(soi.radiusAt(epoch) - 1_000.0));
    Vector3D velocity = moon.getVelocity().add(tangential.scalarMultiply(300.0));
    SpacecraftState start =
        new SpacecraftState(
            new CartesianOrbit(
                new TimeStampedPVCoordinates(epoch, position, velocity),
                gcrf,
                Constants.WGS84_EARTH_MU),
            1000.0);

    List<Sample> samples =
        flyThroughTheLegRunner(new CrossingCoast("grazing", 2.0 * 86_400.0), start);

    List<Sample> boundaries = new ArrayList<>();
    for (int i = 1; i < samples.size(); i++) {
      if (samples.get(i - 1).context().gravity().body()
          != samples.get(i).context().gravity().body()) {
        boundaries.add(samples.get(i));
      }
    }
    logger.info(
        "Grazing fixture: {} arc changes over two days, dead band {} km",
        boundaries.size(),
        Math.round(
            soi.radiusAt(epoch)
                * com.smousseur.orbitlab.simulation.gravity.SoiCrossingDetector.EXIT_DEAD_BAND
                / 1000.0));

    // The cap is a net; what must hold is that it is never approached. Measured: one arc change,
    // so the shallow geometry resolves cleanly rather than producing a chain.
    assertTrue(
        boundaries.size() < StageLegRunner.MAX_LEGS_PER_STAGE - 1,
        "the leg cap must stay a net, got " + boundaries.size() + " arc changes");

    // And no leg may be of zero length: that is precisely what chatter looks like.
    for (int i = 1; i < boundaries.size(); i++) {
      double dwell = boundaries.get(i).date().durationFrom(boundaries.get(i - 1).date());
      assertTrue(dwell > 1.0, "a leg lasted " + dwell + " s, which is chatter");
    }
  }

  /** The last sample flown in the given arc body's context. */
  private static Sample lastSampleOfArc(List<Sample> samples, SolarSystemBody body) {
    for (int i = samples.size() - 1; i >= 0; i--) {
      if (samples.get(i).context().gravity().body() == body) {
        return samples.get(i);
      }
    }
    throw new IllegalStateException("no sample flown around " + body);
  }

  private static Vector3D inGcrf(Sample sample) {
    return sample
        .context()
        .gravity()
        .inertialFrame()
        .getTransformTo(OrekitService.get().gcrf(), sample.date())
        .transformPosition(sample.position());
  }

  @Test
  @DisplayName("The boundary is one instant written twice, once per frame")
  void theBoundaryIsSampledOnBothSides() {
    SpacecraftState start = flybyFixture();
    List<Sample> samples = flyThroughTheLegRunner(new CrossingCoast("flyby", COAST_SECONDS), start);

    int boundaries = 0;
    for (int i = 1; i < samples.size(); i++) {
      Sample previous = samples.get(i - 1);
      Sample current = samples.get(i);
      if (previous.context().gravity().body() == current.context().gravity().body()) {
        continue;
      }
      boundaries++;
      assertEquals(
          0.0,
          current.date().durationFrom(previous.date()),
          1.0e-6,
          "the two samples either side of an arc change are the same instant");

      // And they describe the same place: the whole point of writing it twice.
      Vector3D previousInGcrf =
          previous
              .context()
              .gravity()
              .inertialFrame()
              .getTransformTo(OrekitService.get().gcrf(), previous.date())
              .transformPosition(previous.position());
      Vector3D currentInGcrf =
          current
              .context()
              .gravity()
              .inertialFrame()
              .getTransformTo(OrekitService.get().gcrf(), current.date())
              .transformPosition(current.position());
      assertEquals(
          0.0,
          previousInGcrf.subtract(currentInGcrf).getNorm(),
          1.0e-6,
          "and the same place, brought into a common frame");
    }

    assertEquals(2, boundaries, "one crossing in, one crossing out");

    // The anti-chatter property, asserted where it is actually at risk. A leg that has just
    // switched starts exactly ON the sphere, where g is zero to the root finder's precision; the
    // exit dead band is what stops the re-armed detector from firing again immediately (spec L4
    // §4.4). Without it the lunar arc would last milliseconds instead of hours.
    Sample firstLunar = firstSampleOfArc(samples, SolarSystemBody.MOON);
    Sample lastLunar = lastSampleOfArc(samples, SolarSystemBody.MOON);
    double dwell = lastLunar.date().durationFrom(firstLunar.date());
    logger.info("Time spent inside the lunar sphere: {} h", Math.round(dwell / 3600.0));
    assertTrue(dwell > 3600.0, "the lunar arc must last hours, not restart at once; got " + dwell);
  }

  /** The first sample flown in the given arc body's context. */
  private static Sample firstSampleOfArc(List<Sample> samples, SolarSystemBody body) {
    for (Sample sample : samples) {
      if (sample.context().gravity().body() == body) {
        return sample;
      }
    }
    throw new IllegalStateException("no sample flown around " + body);
  }

  @Test
  @DisplayName("The crossing happens on the Laplace sphere, not somewhere near it")
  void crossingsSitOnTheSphere() {
    SpacecraftState start = flybyFixture();
    List<Sample> samples = flyThroughTheLegRunner(new CrossingCoast("flyby", COAST_SECONDS), start);
    SphereOfInfluence soi = SphereOfInfluence.of(SolarSystemBody.MOON);

    for (int i = 1; i < samples.size(); i++) {
      Sample previous = samples.get(i - 1);
      Sample current = samples.get(i);
      if (previous.context().gravity().body() == current.context().gravity().body()) {
        continue;
      }
      Vector3D moon =
          OrekitService.get()
              .body(SolarSystemBody.MOON)
              .getPosition(previous.date(), previous.context().gravity().inertialFrame());
      double distance = previous.position().subtract(moon).getNorm();
      double radius = soi.radiusAt(previous.date());

      logger.info(
          "Crossing {} -> {} at {} km from the Moon, sphere at {} km",
          previous.context().gravity().body(),
          current.context().gravity().body(),
          Math.round(distance / 1000.0),
          Math.round(radius / 1000.0));

      // Entering is decided at R, leaving at R(1+eps): both are within the dead band of the sphere.
      assertEquals(radius, distance, radius * SoiDeadBandTolerance.VALUE);
    }
  }

  @Test
  @DisplayName("A propulsive stage may not declare a transition, and says so")
  void propulsiveStagesAreRefused() {
    SpacecraftState start = flybyFixture();

    MissionStage burn =
        new ConstantThrustStage("burn", 10.0) {
          @Override
          public Set<SolarSystemBody> soiTransitions(Mission mission) {
            return Set.of(SolarSystemBody.MOON);
          }
        };

    Mission mission = new BallisticMission(start, List.of(burn));
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                new StageLegRunner(
                        null,
                        true,
                        (stage, entry) ->
                            new StageLegRunner.EndDate(entry.getDate().shiftedBy(10.0), true))
                    .fly(burn, start, mission));

    assertTrue(thrown.getMessage().contains("propulsive"));
  }

  @Test
  @DisplayName("A stage declaring nothing flies exactly one leg, in the context it declared")
  void withoutTransitionsThereIsOneLeg() {
    SpacecraftState start = flybyFixture();
    CoastingStage plainCoast = new CoastingStage("plain", 3_600.0);
    Mission mission = new BallisticMission(start, List.of(plainCoast));
    mission.setCurrentState(start);

    StageLegRunner.StageFlight flight =
        new StageLegRunner(
                null,
                true,
                (stage, entry) ->
                    new StageLegRunner.EndDate(entry.getDate().shiftedBy(3_600.0), true))
            .fly(plainCoast, start, mission);

    assertEquals(1, flight.legs().size());
    assertSame(
        GravitationalContext.earth(),
        flight.lastLeg().context().gravity(),
        "the leg carries the shared instance, not a copy of it");
    assertFalse(flight.lastLeg().context().hasDrag(), "no mission asks for an atmosphere yet");
    assertSame(
        start, flight.legs().get(0).entryState(), "no conversion when the frame is the same");
    assertEquals(null, flight.lastLeg().crossedBoundary());
    assertNotEquals(start.getDate(), flight.lastLeg().exitState().getDate());
  }

  /** The tolerance the crossing assertions use, named so the number is not a bare literal. */
  private static final class SoiDeadBandTolerance {
    /** The exit dead band, which is the widest either crossing can legitimately sit from R(t). */
    private static final double VALUE =
        com.smousseur.orbitlab.simulation.gravity.SoiCrossingDetector.EXIT_DEAD_BAND * 2.0;
  }
}
