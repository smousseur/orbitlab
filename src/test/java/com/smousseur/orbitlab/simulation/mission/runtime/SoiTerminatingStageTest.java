package com.smousseur.orbitlab.simulation.mission.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.gravity.SphereOfInfluence;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisGenerator;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * MIS-5 / L1 §6.1 — the test of the lot, on a synthetic inbound crossing. A trajectory closing on
 * the Moon, flown by a coast that declares the lunar sphere <b>ends</b> it.
 *
 * <p><b>Its own fixture rather than cases added to {@link SoiRoundTripFlightTest}</b>, whose
 * subject is an Earth → Moon → Earth round trip and whose javadoc claims to be the test of PHY-4 /
 * L4. This one wants a single entry and never leaves; sharing a class would blur what each is
 * pinning.
 *
 * <p><b>The case that carries the lot is {@link #theFlagDropsOnlyOnTheBoundary()}.</b> Clearing
 * {@code endDateIsStageCutoff} is what lets a coast stop three days short of its bound and still be
 * read as complete — and the same clearing, done for the stage as a whole rather than for the
 * boundary stop alone, would blind it to every other truncation. That distinction is the only thing
 * standing between this lot and a defect surfacing as "no feasible propellant load" on a mission
 * with nothing to do with the Moon (spec {@code docs/lunar-orbit/03-conception-L1.md} §9).
 */
class SoiTerminatingStageTest {
  private static final Logger logger = LogManager.getLogger(SoiTerminatingStageTest.class);

  /** Bound on the coast: far past the crossing, so stopping early is unmistakable. */
  private static final double BOUND_SECONDS = 2.0 * 86_400.0;

  /** Where the early-stop case is cut, well short of the sphere. */
  private static final double EARLY_STOP_SECONDS = 3_600.0;

  private static AbsoluteDate epoch;

  @BeforeAll
  static void initOrekit() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 3, 4, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  /** A minimal mission: the coast reads nothing else off it. */
  private static final class BallisticMission extends Mission {
    private final SpacecraftState initial;

    private BallisticMission(SpacecraftState initial, List<MissionStage> stages) {
      super("SOI terminating stage", null, stages, null);
      this.initial = initial;
    }

    @Override
    public SpacecraftState getInitialState(AbsoluteDate initialDate) {
      return initial;
    }
  }

  /** A bounded coast that watches the lunar sphere and ends at it. */
  private static class TerminatingCoast extends CoastingStage {
    TerminatingCoast(String name) {
      super(name, BOUND_SECONDS);
    }

    @Override
    public GravitationalContext gravitationalContext(Mission mission) {
      return GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN);
    }

    @Override
    public Set<SolarSystemBody> soiTransitions(Mission mission) {
      return Set.of(SolarSystemBody.MOON);
    }

    @Override
    public boolean soiCrossingEndsStage(Mission mission) {
      return true;
    }
  }

  /**
   * A state {@code offsetFromMoon} short of the Moon, closing on it — the inbound half of {@link
   * SoiRoundTripFlightTest}'s fixture, and aimed the same way: the velocity is the Moon's plus a
   * relative approach, because the Moon moves far enough during the crossing that a shot at where
   * it is now simply misses.
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

  private static StageLegRunner.StageFlight flyOneStage(MissionStage stage, SpacecraftState start) {
    Mission mission = new BallisticMission(start, List.of(stage));
    mission.setCurrentState(start);
    // The resolver runs AFTER configure(), which is why reading the stage's own cutoff here is the
    // same thing StageChainRunner does rather than a shortcut around it.
    return new StageLegRunner(
            null, true, (s, entry) -> new StageLegRunner.EndDate(s.getConfiguredEndDate(), true))
        .fly(stage, start, mission);
  }

  private static MissionEphemeris flyThroughTheGenerator(
      MissionStage stage, SpacecraftState start) {
    Mission mission = new BallisticMission(start, List.of(stage));
    return new MissionEphemerisGenerator().generate(mission, start, 0.0);
  }

  @Test
  @DisplayName("A crossing that ends the stage leaves one leg, stopped on the sphere")
  void oneLegStoppedOnTheSphere() {
    SpacecraftState start = closingOnTheMoon();
    StageLegRunner.StageFlight flight = flyOneStage(new TerminatingCoast("approach"), start);

    assertEquals(1, flight.legs().size(), "the crossing ends the stage, it does not cut it");
    assertEquals(
        SolarSystemBody.MOON,
        flight.lastLeg().crossedBoundary(),
        "the leg must record which sphere ended it");
    assertEquals(
        SolarSystemBody.EARTH,
        flight.lastLeg().context().gravity().body(),
        "the stage ends on the OUTGOING side of the boundary, unconverted");

    SpacecraftState exit = flight.lastLeg().exitState();
    Vector3D toMoon =
        OrekitService.get().body(SolarSystemBody.MOON).getPosition(exit.getDate(), exit.getFrame());
    double distance = exit.getPosition().subtract(toMoon).getNorm();
    double radius = SphereOfInfluence.of(SolarSystemBody.MOON).radiusAt(exit.getDate());
    logger.info(
        "Stopped {} h in, {} m from the sphere of influence",
        Math.round(exit.getDate().durationFrom(start.getDate()) / 3600.0),
        String.format(java.util.Locale.ROOT, "%.1f", distance - radius));

    // Entering is decided at R(t) exactly — the dead band applies to leaving only — so the stop
    // sits
    // on the sphere to the root finder's precision, which is a metre at transfer speed.
    assertEquals(radius, distance, 1_000.0, "the stage must stop ON the sphere");
    assertNotEquals(
        start.getDate(), exit.getDate(), "the fixture must actually fly before it crosses");
  }

  @Test
  @DisplayName("Stopping on the boundary is not stopping short: the cutoff flag drops")
  void theCutoffFlagDrops() {
    SpacecraftState start = closingOnTheMoon();
    StageLegRunner.StageFlight flight = flyOneStage(new TerminatingCoast("approach"), start);

    assertFalse(
        flight.endDateIsStageCutoff(),
        "the boundary is where the stage meant to stop, so the bound is no longer its cutoff");

    MissionEphemeris ephemeris = flyThroughTheGenerator(new TerminatingCoast("approach"), start);
    double flown = ephemeris.lastPoint().time().durationFrom(start.getDate());
    logger.info(
        "Flown {} h of a {} h bound, complete={}",
        Math.round(flown / 3600.0),
        Math.round(BOUND_SECONDS / 3600.0),
        ephemeris.isComplete());

    assertTrue(
        flown < 0.5 * BOUND_SECONDS,
        "the fixture must stop well short of its bound for this to mean anything, got " + flown);
    assertTrue(
        ephemeris.isComplete(),
        "a coast that stopped where it declared it would must not read as truncated");
  }

  @Test
  @DisplayName("The flag drops only on the boundary — any other early stop is still a truncation")
  void theFlagDropsOnlyOnTheBoundary() {
    SpacecraftState start = closingOnTheMoon();

    // The same coast, cut an hour in by a STOP of its own. Nothing about the boundary declaration
    // changes; what changes is that the boundary is not what stopped it.
    MissionStage cutShort =
        new TerminatingCoast("approach, cut short") {
          @Override
          public void configure(NumericalPropagator propagator, Mission mission) {
            super.configure(propagator, mission);
            propagator.addEventDetector(
                new DateDetector(mission.getCurrentState().getDate().shiftedBy(EARLY_STOP_SECONDS))
                    .withHandler((state, detector, increasing) -> Action.STOP));
          }
        };

    StageLegRunner.StageFlight flight = flyOneStage(cutShort, start);
    assertEquals(1, flight.legs().size());
    assertEquals(
        null,
        flight.lastLeg().crossedBoundary(),
        "the boundary is not what stopped it — the fixture is wrong if this fails");
    assertTrue(
        flight.endDateIsStageCutoff(),
        "an early stop from another cause must stay visible as a shortfall");

    MissionEphemeris ephemeris = flyThroughTheGenerator(cutShort, start);
    assertFalse(
        ephemeris.isComplete(),
        "a coast truncated by something other than its boundary is still truncated");
  }

  @Test
  @DisplayName("The boundary instant is written once, not twice, on the side that was flown")
  void theBoundaryIsNotWrittenTwice() {
    MissionEphemeris ephemeris =
        flyThroughTheGenerator(new TerminatingCoast("approach"), closingOnTheMoon());

    List<MissionEphemerisPoint> points = ephemeris.allPoints();
    for (int i = 1; i < points.size(); i++) {
      assertNotEquals(
          0.0,
          points.get(i).time().durationFrom(points.get(i - 1).time()),
          "two samples share an instant at index " + i + "; the outgoing sample was not skipped");
    }
    for (MissionEphemerisPoint point : points) {
      assertEquals(
          SolarSystemBody.EARTH,
          point.arc().body(),
          "the stage ends at the boundary, so no point is ever flown around the Moon");
    }
  }

  @Test
  @DisplayName("Ending at a boundary without a cutoff is refused, not netted at 7200 s")
  void endingAtABoundaryWithoutACutoffIsRefused() {
    SpacecraftState start = closingOnTheMoon();

    // Open-ended: CoastingStage configures no date, so the resolver would fall into the safety net.
    MissionStage unbounded =
        new CoastingStage("unbounded approach", (Double) null) {
          @Override
          public GravitationalContext gravitationalContext(Mission mission) {
            return GravitationalContext.earth()
                .withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN);
          }

          @Override
          public Set<SolarSystemBody> soiTransitions(Mission mission) {
            return Set.of(SolarSystemBody.MOON);
          }

          @Override
          public boolean soiCrossingEndsStage(Mission mission) {
            return true;
          }
        };

    Mission mission = new BallisticMission(start, List.of(unbounded));
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                StageChainRunner.sampling(null, 0.0, null).run(List.of(unbounded), start, mission));

    assertTrue(thrown.getMessage().contains("unbounded approach"), thrown.getMessage());
    assertTrue(thrown.getMessage().contains("no end date"), thrown.getMessage());
  }

  @Test
  @DisplayName("Ending at a boundary while watching none is refused")
  void endingAtABoundaryWithoutOneIsRefused() {
    SpacecraftState start = closingOnTheMoon();

    MissionStage watchesNothing =
        new CoastingStage("watches nothing", BOUND_SECONDS) {
          @Override
          public boolean soiCrossingEndsStage(Mission mission) {
            return true;
          }
        };

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> flyOneStage(watchesNothing, start));

    assertTrue(thrown.getMessage().contains("watches no boundary"), thrown.getMessage());
  }
}
