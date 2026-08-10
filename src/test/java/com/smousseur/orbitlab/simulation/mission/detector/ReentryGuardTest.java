package com.smousseur.orbitlab.simulation.mission.detector;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.runtime.StageChainRunner;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * Closes the defect class of spec {@code docs/mission-stages/03-garde-rentree.md}: a propagation
 * whose trajectory re-enters used to run forever — the integrator follows it under the surface and
 * the adaptive step collapses as {@code r → 0}.
 *
 * <p>Every propagation here is wrapped in a wall-clock timeout on purpose: without the guard the
 * failure mode is a <em>hang</em>, and a hanging test tells you nothing. The timeout turns it back
 * into a test failure.
 */
class ReentryGuardTest {

  /**
   * Wall-clock budget for a guarded propagation. Generous — the point is to bound, not to profile.
   */
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static SpacecraftState stateAt(Vector3D position, Vector3D velocity, double mass) {
    return new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(position, velocity),
                OrekitService.get().gcrf(),
                AbsoluteDate.J2000_EPOCH,
                Constants.WGS84_EARTH_MU))
        .withMass(mass);
  }

  /**
   * The state observed on the I7 GEO multi-stage sweep at {@code λ(S1) = 0.3}: 36 km altitude at 7
   * 603 m/s horizontal. Sub-circular at that radius, so the entry point is the apogee and the
   * perigee sits ~800 km <em>inside</em> the Earth — a re-entry, and the trajectory the parking
   * insertion flew for 2 666 s before the evaluation hung for four hours.
   */
  private static SpacecraftState reentringState() {
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 36_000.0;
    return stateAt(new Vector3D(r, 0, 0), new Vector3D(0, 7_603.0, 0), 6_000.0);
  }

  private static SpacecraftState circularStateAt(double altitude, double mass) {
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + altitude;
    double v = FastMath.sqrt(Constants.WGS84_EARTH_MU / r);
    return stateAt(new Vector3D(r, 0, 0), new Vector3D(0, v, 0), mass);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 1. The guard stops a re-entry
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void reentringCoast_stopsInsteadOfHanging() {
    SpacecraftState entry = reentringState();
    double requestedDuration = 2_666.0; // the coast the parking insertion actually scheduled

    SpacecraftState end =
        assertTimeoutPreemptively(
            TIMEOUT,
            () -> {
              NumericalPropagator propagator =
                  OrekitService.get().createOptimizationPropagator(OrekitService.COAST_MAX_STEP);
              propagator.setInitialState(entry);
              ReentryGuard.armQuiet(propagator);
              return propagator.propagate(entry.getDate().shiftedBy(requestedDuration));
            },
            "the guard must stop the propagation; a timeout here means it ran under the surface");

    double flown = end.getDate().durationFrom(entry.getDate());
    assertTrue(flown > 0, "the guard must not fire at the entry state, which is 36 km up");
    assertTrue(
        flown < requestedDuration,
        () -> "the propagation must stop short of its cutoff, flew the full " + flown + " s");

    double altitude =
        end.getPVCoordinates().getPosition().getNorm() - Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
    assertEquals(
        ReentryGuard.SUBSURFACE_FLOOR,
        altitude,
        1_000.0,
        "it must stop on the floor, not somewhere else");
  }

  /**
   * The end-to-end contract: a re-entering phase flown through {@link StageChainRunner} comes back
   * flagged <em>truncated</em>, which is what {@code MissionEphemerisGenerator} turns into {@code
   * complete=false} and {@code MissionLoadEvaluator} reads as infeasible. Not an exception — a
   * clean shortfall.
   */
  @Test
  void stageChainRunner_flagsTheReentringStageTruncated() {
    SpacecraftState entry = reentringState();
    MissionStage coast = new CoastingStage("Re-entering coast", 2_666.0);
    Mission mission = missionWith(List.of(coast));
    mission.setCurrentState(entry);

    List<StageChainRunner.StageRun> runs = new ArrayList<>();
    StageChainRunner runner = StageChainRunner.sampling(null, 0.0, run -> runs.add(run));

    assertTimeoutPreemptively(
        TIMEOUT,
        () -> runner.run(List.of(coast), entry, mission),
        "the runner must arm the guard on every phase it flies");

    assertEquals(1, runs.size());
    StageChainRunner.StageRun run = runs.getFirst();
    assertFalse(run.propagationFailed(), "a re-entry is a truncation, not a propagation failure");
    assertTrue(
        run.shortfallSeconds() > 0,
        () -> "the phase must be reported short of its cutoff, got " + run.shortfallSeconds());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 2. The guard is inactive on everything nominal
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * The non-regression proof at detector level: a detector whose {@code g} never changes sign does
   * not touch the integration — event detection runs by interpolation over already-accepted steps,
   * and only a detected event truncates one. So a guarded nominal propagation must land on the
   * exact same state as an unguarded one, not merely a close one.
   */
  @Test
  void nominalCircularOrbit_neverTriggers() {
    SpacecraftState entry = circularStateAt(400_000.0, 6_000.0);
    AbsoluteDate endDate = entry.getDate().shiftedBy(3_600.0);

    NumericalPropagator bare =
        OrekitService.get().createOptimizationPropagator(OrekitService.COAST_MAX_STEP);
    bare.setInitialState(entry);
    SpacecraftState reference = bare.propagate(endDate);

    NumericalPropagator guarded =
        OrekitService.get().createOptimizationPropagator(OrekitService.COAST_MAX_STEP);
    guarded.setInitialState(entry);
    ReentryGuard.armQuiet(guarded);
    SpacecraftState actual = guarded.propagate(endDate);

    assertEquals(0.0, actual.getDate().durationFrom(endDate), 0.0, "the full hour must be flown");
    assertEquals(
        0.0,
        Vector3D.distance(
            actual.getPVCoordinates().getPosition(), reference.getPVCoordinates().getPosition()),
        0.0,
        "arming the guard must not move a single metre of a nominal trajectory");
  }

  /**
   * The test that locks {@link ReentryGuard#SUBSURFACE_FLOOR}. The switching function is spherical
   * and referenced to the WGS84 <em>equatorial</em> radius, while the Earth is 21.4 km oblate — so
   * the spherical altitude of a launch pad is structurally negative, down to −17 km at Plesetsk. A
   * floor at 0 m would not "fire at ignition": it would already be breached before liftoff.
   */
  @Test
  void launchPadLatitudes_clearTheFloorBeforeLiftoff() {
    ReentryDetector detector = new ReentryDetector();

    for (double latitudeDeg : new double[] {0.0, 5.2, 28.5, 34.7, 45.9, 62.9, 90.0}) {
      SpacecraftState onThePad = padState(latitudeDeg);
      double g = detector.g(onThePad);

      double sphericalAltitude =
          onThePad.getPVCoordinates().getPosition().getNorm()
              - Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
      assertTrue(
          g > 0,
          () ->
              String.format(
                  "the guard must be inactive on the pad at %.1f° (spherical altitude %.1f km, "
                      + "floor %.1f km) — g = %.1f km",
                  latitudeDeg,
                  sphericalAltitude / 1000.0,
                  ReentryGuard.SUBSURFACE_FLOOR / 1000.0,
                  g / 1000.0));
      // The pole is the worst case at −21.4 km; anything less than ~28 km of margin there would
      // mean the floor is too shallow to be latitude-proof.
      assertTrue(
          g > 25_000.0,
          () ->
              String.format(
                  "margin at %.1f° is only %.1f km — too thin to be a safe floor",
                  latitudeDeg, g / 1000.0));
    }
  }

  /** A ballistic climb from the worst-case pad: the guard stays silent through the whole ascent. */
  @Test
  void verticalClimbFromTheWorstPad_neverTriggers() {
    SpacecraftState onThePad = padState(62.9); // Plesetsk, −17.0 km spherical
    Vector3D up = onThePad.getPVCoordinates().getPosition().normalize().scalarMultiply(500.0);
    SpacecraftState climbing = stateAt(onThePad.getPVCoordinates().getPosition(), up, 500_000.0);
    AbsoluteDate endDate = climbing.getDate().shiftedBy(60.0);

    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(OrekitService.SAFE_MAX_STEP);
    propagator.setInitialState(climbing);
    ReentryGuard.armQuiet(propagator);
    SpacecraftState end = propagator.propagate(endDate);

    assertEquals(
        0.0,
        end.getDate().durationFrom(endDate),
        0.0,
        "the guard must not truncate a climb away from a high-latitude pad");
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Helpers
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * A state sitting on the WGS84 ellipsoid at the given latitude — a launch pad at sea level. Built
   * through the real ellipsoid rather than a hand-rolled radius so the oblateness the floor has to
   * absorb is the actual one. Expressed in GCRF: only {@code |r|} matters to the detector, and that
   * is invariant under the ITRF→GCRF rotation.
   */
  private static SpacecraftState padState(double latitudeDeg) {
    Vector3D onEllipsoid =
        OrekitService.get()
            .getEarthEllipsoid()
            .transform(new GeodeticPoint(FastMath.toRadians(latitudeDeg), 0.0, 0.0));
    // A pad is not in orbit; give it enough tangential speed that CartesianOrbit stays well-defined
    // while leaving the radius — the only thing the detector reads — untouched.
    Vector3D tangential =
        Vector3D.crossProduct(Vector3D.PLUS_K, onEllipsoid).getNorm() > 1.0
            ? Vector3D.crossProduct(Vector3D.PLUS_K, onEllipsoid).normalize()
            : Vector3D.PLUS_I;
    return stateAt(onEllipsoid, tangential.scalarMultiply(7_000.0), 500_000.0);
  }

  private static Mission missionWith(List<MissionStage> stages) {
    VehicleStack stack =
        new VehicleStack(
            List.of(
                new LaunchVehicle(4_000, 107_500, 12_000, new PropulsionSystem(348, 981_000)),
                new Spacecraft(2_000, 2_000, 0, new PropulsionSystem(320, 400))));
    return new Mission("reentry guard test", stack, stages, null) {
      @Override
      public SpacecraftState getInitialState(AbsoluteDate initialDate) {
        return null;
      }
    };
  }
}
