package com.smousseur.orbitlab.simulation.mission.ephemeris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.runtime.StageChainRunner;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import java.util.ArrayList;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * Sampling and horizon behaviour at chain level (spec {@code
 * docs/mission-horizon/01-horizon-explicite.md} §5 and §8).
 *
 * <p>Deliberately built on inert phases rather than on a real mission: what is under test is the
 * <em>wiring</em> — which step each phase is recorded at, what bounds a phase that configured no
 * cutoff, and whether the horizon can reach anything before the trailing coast. None of that needs
 * a thrust model, and not needing one keeps these tests in the seconds rather than the minutes.
 */
class MissionHorizonSamplingTest {

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  /**
   * A phase that declares itself propulsive and ends after a fixed duration, without actually
   * burning anything. {@code sampleStepSeconds} keys on {@link MissionStage#isPropulsive()}, so
   * this is exactly the input the rule reads — and the trajectory stays a clean coast, which makes
   * the sample counts predictable.
   */
  private static final class InertBurnStage extends MissionStage {
    private final double durationSeconds;

    InertBurnStage(String name, double durationSeconds) {
      super(name);
      this.durationSeconds = durationSeconds;
    }

    @Override
    public void configure(NumericalPropagator propagator, Mission mission) {
      configuredEndDate = mission.getCurrentState().getDate().shiftedBy(durationSeconds);
    }
  }

  private static SpacecraftState circularState(double altitude) {
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + altitude;
    double v = FastMath.sqrt(Constants.WGS84_EARTH_MU / r);
    return new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(0, v, 0)),
                OrekitService.get().gcrf(),
                AbsoluteDate.J2000_EPOCH,
                Constants.WGS84_EARTH_MU))
        .withMass(5_000.0);
  }

  private static Mission missionWith(List<MissionStage> stages) {
    VehicleStack stack =
        new VehicleStack(
            List.of(
                new LaunchVehicle(4_000, 107_500, 12_000, new PropulsionSystem(348, 981_000)),
                new Spacecraft(2_000, 2_000, 0, new PropulsionSystem(320, 400))));
    return new Mission("horizon sampling test", stack, stages, null) {
      @Override
      public SpacecraftState getInitialState(AbsoluteDate initialDate) {
        return null;
      }
    };
  }

  private static List<MissionEphemerisPoint> pointsOfStage(MissionEphemeris eph, String stageName) {
    return eph.allPoints().stream().filter(p -> stageName.equals(p.stageName())).toList();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // The sampling step follows the phase
  // ─────────────────────────────────────────────────────────────────────────

  /** The rule itself, read off the stages rather than off a generated ephemeris. */
  @Test
  void sampleStep_isOneSecondForABurnAndSixtyForACoast() {
    Mission mission = missionWith(List.of());
    SpacecraftState entry = circularState(400_000.0);

    assertEquals(1.0, new InertBurnStage("Burn", 10.0).sampleStepSeconds(entry, mission), 0.0);
    assertEquals(60.0, new CoastingStage("Coast", 10.0).sampleStepSeconds(entry, mission), 0.0);
  }

  /**
   * And the same rule as it actually lands in an ephemeris: a 120 s burn recorded second by second,
   * a 600 s coast recorded once a minute. This is the 60x that makes a multi-day horizon
   * affordable.
   */
  @Test
  void generatedEphemeris_recordsBurnsFinelyAndCoastsCoarsely() {
    MissionStage burn = new InertBurnStage("Burn", 120.0);
    MissionStage trailing = new CoastingStage("Trailing", (Double) null);
    Mission mission = missionWith(List.of(burn, trailing));
    SpacecraftState entry = circularState(400_000.0);
    mission.setCurrentState(entry);

    MissionEphemeris eph = new MissionEphemerisGenerator().generate(mission, entry, 600.0);

    List<MissionEphemerisPoint> burnPoints = pointsOfStage(eph, "Burn");
    List<MissionEphemerisPoint> coastPoints = pointsOfStage(eph, "Trailing");

    assertEquals(
        1.0,
        burnPoints.get(1).time().durationFrom(burnPoints.get(0).time()),
        1.0e-6,
        "a propulsive phase must be sampled every second");
    assertEquals(
        60.0,
        coastPoints.get(1).time().durationFrom(coastPoints.get(0).time()),
        1.0e-6,
        "a coast must be sampled every minute");

    assertTrue(
        burnPoints.size() > 100,
        () -> "120 s of burn at 1 s should give ~121 points, got " + burnPoints.size());
    assertTrue(
        coastPoints.size() < 20,
        () -> "600 s of coast at 60 s should give ~11 points, got " + coastPoints.size());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // The horizon only ever extends the tail
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * The invariant the whole item rests on: the horizon must not reach anything before the trailing
   * coast. It holds structurally — the optimize pass flies the chain through {@code
   * StageChainRunner.plain()}, whose final coast is zero, and the trailing {@code CoastingStage}
   * does not override {@code propagateStandalone} — and this pins the observable consequence.
   *
   * <p>Two different claims, deliberately asserted at two different strengths.
   *
   * <p><b>Everything before the trailing coast is identical to the bit.</b> Those phases are
   * propagated to their own configured cutoffs, which the horizon does not touch, so there is no
   * tolerance to grant and none is granted. This is the assertion that would catch the horizon
   * leaking into an optimized stage.
   *
   * <p><b>The trailing coast itself agrees to the millimetre, not exactly.</b> Measured: 1.3 µm on
   * the first shared samples. That is not the horizon perturbing the trajectory — it is the
   * adaptive integrator picking a different step sequence when handed a different target date,
   * which is expected and harmless for a phase that is, by construction, past everything the
   * optimizer produced.
   */
  @Test
  void horizon_extendsTheTailWithoutReachingAnythingBefore() {
    SpacecraftState entry = circularState(400_000.0);

    MissionEphemeris shortRun = generateWithCoast(entry, 120.0);
    MissionEphemeris longRun = generateWithCoast(entry, 3_600.0);

    assertTrue(
        longRun.size() > shortRun.size(), "a longer horizon must record strictly more points");
    assertTrue(
        longRun.endDate().durationFrom(shortRun.endDate()) > 3_000.0,
        "the extra time must land at the end");
    assertEquals(shortRun.isComplete(), longRun.isComplete());

    List<MissionEphemerisPoint> shortBurn = pointsOfStage(shortRun, "Burn");
    List<MissionEphemerisPoint> longBurn = pointsOfStage(longRun, "Burn");
    assertEquals(shortBurn.size(), longBurn.size(), "the burn must be recorded identically");
    for (int i = 0; i < shortBurn.size(); i++) {
      int index = i;
      assertEquals(
          0.0,
          Vector3D.distance(longBurn.get(i).position(), shortBurn.get(i).position()),
          0.0,
          () -> "burn point " + index + " moved: the horizon reached before the trailing coast");
    }

    // The short run's own stage-end sample lands where the long run has no reason to sample, so the
    // shared prefix stops one short of it.
    List<MissionEphemerisPoint> shortCoast = pointsOfStage(shortRun, "Trailing");
    List<MissionEphemerisPoint> longCoast = pointsOfStage(longRun, "Trailing");
    for (int i = 0; i < shortCoast.size() - 1; i++) {
      int index = i;
      assertEquals(
          0.0,
          longCoast.get(i).time().durationFrom(shortCoast.get(i).time()),
          0.0,
          () -> "coast sample " + index + " must be taken at the same instant");
      assertEquals(
          0.0,
          Vector3D.distance(longCoast.get(i).position(), shortCoast.get(i).position()),
          1.0e-3,
          () -> "coast sample " + index + " moved by more than integrator noise");
    }
  }

  private static MissionEphemeris generateWithCoast(SpacecraftState entry, double coastSeconds) {
    Mission mission =
        missionWith(
            List.of(
                new InertBurnStage("Burn", 60.0), new CoastingStage("Trailing", (Double) null)));
    mission.setCurrentState(entry);
    return new MissionEphemerisGenerator().generate(mission, entry, coastSeconds);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // The safety net
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * {@code FALLBACK_DURATION_SECONDS} is reachable, contrary to what its old comment implied: a
   * {@link CoastingStage} with neither a max time nor a node to stop on configures no end date. The
   * value is deliberately left where it was — this pins what it does so that moving it later is a
   * decision rather than an accident.
   */
  @Test
  void aStageWithoutACutoff_isBoundedBySafetyNet() {
    MissionStage unbounded = new CoastingStage("Unbounded", (Double) null);
    Mission mission = missionWith(List.of(unbounded));
    SpacecraftState entry = circularState(400_000.0);
    mission.setCurrentState(entry);

    List<StageChainRunner.StageRun> runs = new ArrayList<>();
    // lastStageCoastSeconds = 0, so the last stage falls through to its own cutoff — which it has
    // not configured — and lands on the net.
    StageChainRunner.sampling(null, 0.0, runs::add).run(List.of(unbounded), entry, mission);

    assertEquals(1, runs.size());
    StageChainRunner.StageRun run = runs.getFirst();
    assertEquals(
        StageChainRunner.FALLBACK_DURATION_SECONDS,
        run.endDate().durationFrom(entry.getDate()),
        1.0e-6);
    assertEquals(
        0.0,
        run.shortfallSeconds(),
        0.0,
        "the net is not the stage's own cutoff, so stopping there is not a shortfall");
  }
}
