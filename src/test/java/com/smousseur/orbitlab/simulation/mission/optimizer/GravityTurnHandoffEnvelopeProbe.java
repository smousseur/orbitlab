package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.operation.MissionComposer;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

/**
 * PHY-2 / L5 — what hand-off states a gravity turn can actually reach, per launcher, measured
 * rather than inferred.
 *
 * <p><b>The question this answers.</b> A drag-on ascent hands the analytic transfer a state, and
 * the transfer then coasts half an orbit to the apogee it aims at. That coast is flown by the upper
 * stage alone, whose ballistic coefficient collapsed at separation, so it only survives if the
 * hand-off is above the sensible atmosphere — while the apogee it carries must still sit in the
 * window {@link GravityTurnConstraints} sets. Those two demands can conflict, and whether they do
 * is a property of the <em>vehicle</em>, not of the cost function: an upper stage's thrust, its
 * cross-section and the time its staging completes all move the reachable set.
 *
 * <p><b>What it does.</b> Walks the production chain to the gravity turn exactly as {@code
 * MissionOptimizer} does, then, instead of optimizing, grades a regular grid over the problem's own
 * box and prints the hand-off each candidate produces — altitude, periapsis, apogee, tangential
 * velocity, flight path angle, cost, and the {@code Cd·A/m} the coasting stage carries. Each
 * profile is run drag-on and drag-off so the envelope reads as a difference.
 *
 * <p><b>The verdict lines are the point.</b> Per profile and environment it reports the highest
 * hand-off whose apogee still lands in the window — the single number that says whether a hand-off
 * floor is reachable on that vehicle, or whether it is a constant fitted to one of them.
 *
 * <p><b>It asserts nothing.</b> It is an instrument, like {@code AtmosphereProbe}: it prints tables
 * someone reads to choose. Gated on {@code orbitlab.probe} so it never runs in a normal suite.
 *
 * <p>Flies the spec's analytic loads — the composition {@code MeasuredLoadPlanner} flies on its
 * first pass, which is the pass that has to close.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class GravityTurnHandoffEnvelopeProbe {

  private static final Logger logger = LogManager.getLogger(GravityTurnHandoffEnvelopeProbe.class);

  /** Kourou, the site every PHY-2 profile was measured from. */
  private static final double LATITUDE_DEG = 5.23;

  private static final double LONGITUDE_DEG = -52.77;

  private static final int TRANSITION_STEPS = 14;
  private static final int EXPONENT_STEPS = 14;

  /** The profiles compared. The first is the one PHY-2 / L5 calibrated its floors on. */
  private static final List<Profile> PROFILES =
      List.of(
          new Profile("FALCON_HEAVY", "EARTH_OBS_SAT", 10_000.0, 400.0),
          new Profile("ARIANE_64", "EARTH_OBS_SAT", 10_000.0, 400.0));

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void handoffEnvelope_perLauncher_dragOnAgainstVacuum() {
    for (Profile profile : PROFILES) {
      MissionSpec dragOn =
          MissionFactory.specFromWizardValues(profile.wizardValues(), MissionType.LEO);
      sweep(profile.label() + " NRLMSISE", dragOn);
      sweep(profile.label() + " NONE", dragOn.withAtmosphere(AtmosphereModel.NONE));
    }
  }

  /** Grades the whole box and prints one line per candidate, then the verdict lines. */
  private void sweep(String label, MissionSpec spec) {
    Mission mission = MissionComposer.compose(spec, OptimizationType.FAST);
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    mission.setInitialDate(epoch);
    mission.setCurrentState(mission.getInitialState(epoch));

    GravityTurnEntry entry = advanceToGravityTurn(mission);
    TrajectoryProblem problem = entry.stage().buildProblem(mission);
    double[] lower = problem.getLowerBounds();
    double[] upper = problem.getUpperBounds();
    // The window the hand-off's apogee has to land in, read off the same constraints the cost
    // function grades against rather than restated here.
    GravityTurnConstraints constraints = GravityTurnConstraints.forTarget(targetAltitudeOf(spec));

    logger.info(
        "[L5 envelope] {} — box tT [{}, {}] s, exp [{}, {}] | apogee window [{}, {}] km",
        label,
        fmt(lower[0]),
        fmt(upper[0]),
        fmt(lower[1]),
        fmt(upper[1]),
        fmt(constraints.targetApogee() / 1000.0),
        fmt(constraints.maxApogee() / 1000.0));

    List<Candidate> inWindow = new ArrayList<>();
    Candidate highest = null;

    for (int i = 0; i < TRANSITION_STEPS; i++) {
      double transitionTime = lerp(lower[0], upper[0], i, TRANSITION_STEPS);
      for (int j = 0; j < EXPONENT_STEPS; j++) {
        double exponent = lerp(lower[1], upper[1], j, EXPONENT_STEPS);
        Candidate candidate = grade(problem, mission, entry, transitionTime, exponent);
        logger.info("[L5 envelope] {} | {}", label, candidate.line());
        if (candidate.failed()) {
          continue;
        }
        if (highest == null || candidate.altitude() > highest.altitude()) {
          highest = candidate;
        }
        if (candidate.apogee() >= constraints.targetApogee()
            && candidate.apogee() <= constraints.maxApogee()) {
          inWindow.add(candidate);
        }
      }
    }

    Candidate bestInWindow =
        inWindow.stream().max(Comparator.comparingDouble(Candidate::altitude)).orElse(null);

    logger.info(
        "[L5 verdict] {} — highest hand-off overall: {}",
        label,
        highest == null ? "none" : highest.line());
    logger.info(
        "[L5 verdict] {} — {} of {} candidates land the apogee in the window",
        label,
        inWindow.size(),
        TRANSITION_STEPS * EXPONENT_STEPS);
    logger.info(
        "[L5 verdict] {} — HIGHEST HAND-OFF WITH THE APOGEE IN THE WINDOW: {}",
        label,
        bestInWindow == null
            ? "NONE — the two demands are incompatible here"
            : bestInWindow.line());
  }

  /** One graded grid point. */
  private record Candidate(double altitude, double apogee, boolean failed, String line) {}

  private Candidate grade(
      TrajectoryProblem problem,
      Mission mission,
      GravityTurnEntry entry,
      double transitionTime,
      double exponent) {
    try {
      SpacecraftState handOff = problem.propagate(new double[] {transitionTime, exponent});
      double cost = problem.computeCost(handOff);
      // The ascent chain advances the shared mission as it flies (spec 01 §5.6), so every candidate
      // has to start from the state the stage actually entered on.
      mission.setCurrentState(entry.state());

      StageEndStateDiagnostic.EndState end = StageEndStateDiagnostic.from(handOff);
      KeplerianOrbit orbit =
          new KeplerianOrbit(
              handOff.getPVCoordinates(),
              handOff.getFrame(),
              handOff.getDate(),
              Constants.WGS84_EARTH_MU);
      double apogee = orbit.getA() * (1.0 + orbit.getE()) - Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
      return new Candidate(
          end.altitude(),
          apogee,
          false,
          String.format(
              Locale.ROOT,
              "tT=%7.1f s | exp=%5.3f | alt=%8.1f km | peri=%9.1f km | apo=%9.1f km"
                  + " | vTan=%7.1f m/s | FPA=%6.2f | cost=%12.4f | CdA/m=%.2e",
              transitionTime,
              exponent,
              end.altitude() / 1000.0,
              end.periapsis() / 1000.0,
              apogee / 1000.0,
              end.vTan(),
              end.fpaDeg(),
              cost,
              coastingBallisticTerm(handOff, mission)));
    } catch (RuntimeException e) {
      mission.setCurrentState(entry.state());
      return new Candidate(
          Double.NEGATIVE_INFINITY,
          Double.NEGATIVE_INFINITY,
          true,
          String.format(
              Locale.ROOT,
              "tT=%7.1f s | exp=%5.3f | REFUSED: %s",
              transitionTime,
              exponent,
              e.getMessage()));
    }
  }

  /**
   * {@code Cd·A/m} of the stage that will fly the coast, in m²/kg — the term the drag deceleration
   * is linear in, and the one that collapses at separation. Reported so two launchers can be
   * compared at equal altitude without recomputing a density.
   */
  private static double coastingBallisticTerm(SpacecraftState state, Mission mission) {
    ActiveStageInfo active = mission.getVehicle().resolveActiveStage(state.getMass());
    AerodynamicProperties aero = active.aerodynamics();
    return aero == null ? 0.0 : aero.crossSection() * aero.dragCoefficient() / state.getMass();
  }

  private static double targetAltitudeOf(MissionSpec spec) {
    return ((MissionSpec.EarthOrbit) spec).perigeeAltitude();
  }

  /** A wizard mission to sweep, named by what distinguishes it from the others. */
  private record Profile(String launcherId, String payloadId, double payloadMass, double targetKm) {

    String label() {
      return String.format(
          Locale.ROOT, "%s+%.0ft@%.0fkm", launcherId, payloadMass / 1000.0, targetKm);
    }

    Map<String, Object> wizardValues() {
      Map<String, Object> values = new HashMap<>();
      values.put("MISSION_NAME", label());
      values.put("LAUNCH_SITE_NAME", "Kourou - French Guiana");
      values.put("LAUNCH_SITE_LAT", LATITUDE_DEG);
      values.put("LAUNCH_SITE_LONG", LONGITUDE_DEG);
      values.put("LAUNCH_SITE_ALT", 0.0);
      values.put("LAUNCHER_TYPE", launcherId);
      values.put("PAYLOAD_TYPE", payloadId);
      values.put("PAYLOAD_MASS", payloadMass);
      values.put("LEO_PERIGEE_ALT", targetKm);
      values.put("LEO_APOGEE_ALT", targetKm);
      return values;
    }
  }

  /** The gravity-turn stage and the state the mission enters it on. */
  private record GravityTurnEntry(OptimizableMissionStage<?> stage, SpacecraftState state) {}

  /**
   * Flies the non-optimizable stages until the first optimizable one — the gravity turn — and stops
   * there, the way {@code MissionOptimizer} does before handing a problem to CMA-ES.
   */
  private GravityTurnEntry advanceToGravityTurn(Mission mission) {
    for (MissionStage stage : mission.getStages()) {
      if (stage instanceof OptimizableMissionStage<?> optimizable) {
        return new GravityTurnEntry(optimizable, mission.getCurrentState());
      }
      mission.setCurrentState(stage.propagateStandalone(mission.getCurrentState(), mission));
    }
    throw new AssertionError("the ascent chain carries no optimizable stage");
  }

  private static double lerp(double from, double to, int index, int steps) {
    return steps == 1 ? from : from + (to - from) * index / (steps - 1.0);
  }

  private static String fmt(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }
}
