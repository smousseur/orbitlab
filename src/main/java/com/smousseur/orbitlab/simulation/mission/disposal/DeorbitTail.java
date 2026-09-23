package com.smousseur.orbitlab.simulation.mission.disposal;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.ArcTransition;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryGuard;
import com.smousseur.orbitlab.simulation.mission.runtime.StageChainRunner;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.stage.DeorbitBurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.StageNames;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.ode.events.Action;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.ApsideDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * The disposal tail of a payload that re-enters: a sequence of tracking retrograde burns, each
 * centred on an apogee, that brings the osculating perigee down to {@link
 * #REENTRY_PERIGEE_ALTITUDE_M}.
 *
 * <p><b>Outside the stage chain, by construction.</b> A mission carries its tail beside {@link
 * Mission#getStages()}, never in it: the optimize pass walks the stages, so it never sees the tail,
 * and neither the CMA-ES loop nor the zero-tolerance gates can be moved by it. The tail is planned
 * and flown once, after the mission itself has been computed, from the state the mission reached at
 * its horizon.
 *
 * <p><b>Planned by flying, then materialized.</b> The sequence is not solved in closed form: each
 * segment — the coast to the next apogee, then the burn — is flown, as the very stage that will be
 * flown afterwards, by {@link StageChainRunner#plain()}, and the state it ends on is where the next
 * segment is planned from. Flying the materialized stages again through a sampling runner therefore
 * ends on the planned state: the two flights are the same stages from the same states, the
 * agreement the optimize and restitution passes already rest on.
 *
 * <p><b>Why split burns, capped at a quarter of a revolution.</b> A finite burn loses efficiency
 * with the share of the orbit it sweeps: negligible up to about 12 %, heavy between 44 and 60 %,
 * and past a whole revolution it becomes a spiral. The reserve a 10 t payload carries to reach a 0
 * km perigee from 400 km, burnt in one go on its 400 N engine, sweeps 60 % of a revolution and
 * leaves the perigee at 66 km; split into burns of at most a quarter revolution, every reserve
 * measured reached the perigee it was sized for, on both payloads, from 400 to 1 800 km.
 */
public final class DeorbitTail {
  private static final Logger logger = LogManager.getLogger(DeorbitTail.class);

  /**
   * The perigee the tail brings the payload down to (m), spherical above the equatorial radius.
   * Every measured sequence reaching it — both re-entry-regime payloads, from 400 to 1 800 km, at
   * every arc cap — re-entered, the 25 % cap ones after 35 to 52 minutes of fall. It is also the
   * target a disposal reserve is sized for, so that sizing and cutoff read one number.
   */
  public static final double REENTRY_PERIGEE_ALTITUDE_M = 50_000.0;

  /**
   * Longest burn, as a share of the Keplerian period at the start of its coast. At 25 % every
   * measured configuration reached the target; a single burn sweeping 44 to 60 % of a revolution
   * did not. Nothing was measured between 25 and 44 %.
   */
  static final double ARC_CAP_FRACTION = 0.25;

  /**
   * Safety bound on the number of burns of one sequence: four times the most measured at a 25 %
   * cap, eight, for the Earth-observation payload at 1 800 km.
   */
  static final int MAX_BURNS = 32;

  /** Shortest coast before a burn (s): a burn whose centre is already past lights at once. */
  private static final double MIN_COAST_SECONDS = 1.0e-3;

  /**
   * How far past the planned cutoff the last burn is scheduled (s). When flown again, that burn's
   * last integration step is not the one the planning flight took, and a date landing a hair before
   * the crossing would leave the perigee above the target. Scheduled just past it, the burn is
   * ended by its own perigee cutoff every time; the overrun is far below the one-second shortfall a
   * trajectory is judged truncated at.
   */
  private static final double CUTOFF_OVERRUN_SECONDS = 0.1;

  /**
   * How far short of its own end a segment may stop and still count as having reached it (s). A
   * date-terminated propagation lands on its date to far under a millisecond.
   */
  private static final double END_TOLERANCE_SECONDS = 1.0e-3;

  /** An apsis closer than this to the search start is the one the state sits on, and is skipped. */
  private static final double APSIS_SKIP_SECONDS = 1.0;

  /** How many periods the apogee search flies before giving up. */
  private static final double APOGEE_SEARCH_PERIODS = 1.2;

  /**
   * Plans the sequence from the mission's horizon state.
   *
   * <p>Burn by burn: find the next apogee by a coast flight — a flight the re-entry guard cuts
   * short means the payload is falling already, and the sequence stops there; size the burn at the
   * arc cap or the propellant left above the depletion floor, whichever is shorter; centre it on
   * the apogee, lighting no earlier than the coast allows; fly it with its perigee cutoff. The
   * sequence stops on the target, on the last kilogram, on a fall, or on {@link #MAX_BURNS}.
   *
   * <p>Leaves {@code mission}'s current state wherever the last planning flight put it: the caller
   * owns that state.
   *
   * @param horizonState the state the mission reached at its horizon
   * @param mission the mission whose payload is disposed of
   * @return the planned sequence
   */
  public DeorbitSequence plan(SpacecraftState horizonState, Mission mission) {
    double targetRadius =
        mission.gravitationalContext().equatorialRadius() + REENTRY_PERIGEE_ALTITUDE_M;
    List<MissionStage> stages = new ArrayList<>();
    List<DeorbitSequence.Burn> burns = new ArrayList<>();
    SpacecraftState state = horizonState;

    while (burns.size() < MAX_BURNS) {
      ActiveStageInfo active = mission.getVehicle().resolveActiveStage(state.getMass());
      double propellant = state.getMass() - active.depletionFloor();
      if (propellant <= 0.0) {
        return finish(stages, burns, state, DeorbitSequence.End.PROPELLANT_SPENT);
      }

      AbsoluteDate apogee = nextApogee(state, mission);
      if (apogee == null) {
        return finish(stages, burns, state, DeorbitSequence.End.FELL_BEFORE_NEXT_BURN);
      }

      PropulsionSystem engine = active.propulsion();
      double massFlow = engine.thrust() / (engine.isp() * Constants.G0_STANDARD_GRAVITY);
      double propellantSeconds = propellant / massFlow;
      double capSeconds = ARC_CAP_FRACTION * state.getOrbit().getKeplerianPeriod();
      double plannedSeconds = Math.min(capSeconds, propellantSeconds);
      double coastSeconds =
          Math.max(
              apogee.shiftedBy(-plannedSeconds / 2.0).durationFrom(state.getDate()),
              MIN_COAST_SECONDS);
      CoastingStage coast = new CoastingStage(StageNames.DEORBIT_COAST, coastSeconds);
      stages.add(coast);
      SpacecraftState ignition = fly(coast, state, mission);
      if (ignition == state || stoppedShort(ignition, coast)) {
        return finish(stages, burns, ignition, DeorbitSequence.End.TRUNCATED);
      }

      burns.add(new DeorbitSequence.Burn(apogee, plannedSeconds));
      DeorbitBurnStage burn =
          new DeorbitBurnStage(StageNames.DEORBIT_BURN, plannedSeconds, REENTRY_PERIGEE_ALTITUDE_M);
      SpacecraftState burnt = fly(burn, ignition, mission);
      if (burnt == ignition) {
        stages.add(burn);
        return finish(stages, burns, burnt, DeorbitSequence.End.TRUNCATED);
      }

      if (perigeeRadius(burnt, mission) <= targetRadius) {
        double cutSeconds = burnt.getDate().durationFrom(ignition.getDate());
        if (cutSeconds + CUTOFF_OVERRUN_SECONDS < plannedSeconds) {
          burn =
              new DeorbitBurnStage(
                  StageNames.DEORBIT_BURN,
                  cutSeconds + CUTOFF_OVERRUN_SECONDS,
                  REENTRY_PERIGEE_ALTITUDE_M);
          burnt = fly(burn, ignition, mission);
        }
        stages.add(burn);
        return finish(stages, burns, burnt, DeorbitSequence.End.TARGET_REACHED);
      }

      stages.add(burn);
      if (stoppedShort(burnt, burn)) {
        return finish(stages, burns, burnt, DeorbitSequence.End.TRUNCATED);
      }
      if (propellantSeconds <= capSeconds) {
        return finish(stages, burns, burnt, DeorbitSequence.End.PROPELLANT_SPENT);
      }
      state = burnt;
    }
    return finish(stages, burns, state, DeorbitSequence.End.MAX_BURNS);
  }

  /**
   * Flies one stage alone, exactly as it will be flown in the sequence.
   *
   * @return the state it ended on — the very {@code entry} instance when its propagation failed,
   *     which is how {@link StageChainRunner#plain()} reports a failure
   */
  private static SpacecraftState fly(MissionStage stage, SpacecraftState entry, Mission mission) {
    return StageChainRunner.plain().run(List.of(stage), entry, mission);
  }

  private static boolean stoppedShort(SpacecraftState end, MissionStage stage) {
    return stage.getConfiguredEndDate().durationFrom(end.getDate()) > END_TOLERANCE_SECONDS;
  }

  /**
   * The next apogee after {@code state}, found by flying the coast it will be reached by: same
   * flight context, same integrator step, same re-entry guard.
   *
   * @return the apogee date, or {@code null} when the coast was stopped before reaching one
   */
  private static AbsoluteDate nextApogee(SpacecraftState state, Mission mission) {
    FlightContext context =
        new CoastingStage(StageNames.DEORBIT_COAST, null).flightContext(state, mission);
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, OrekitService.COAST_MAX_STEP);
    propagator.setInitialState(ArcTransition.convert(state, context.gravity()));
    ReentryGuard.armQuiet(propagator, context.gravity());
    AtomicReference<AbsoluteDate> apogee = new AtomicReference<>();
    propagator.addEventDetector(
        new ApsideDetector(state.getOrbit())
            .withHandler(
                (s, detector, increasing) -> {
                  if (increasing
                      || s.getDate().durationFrom(state.getDate()) <= APSIS_SKIP_SECONDS) {
                    return Action.CONTINUE;
                  }
                  apogee.set(s.getDate());
                  return Action.STOP;
                }));
    propagator.propagate(
        state.getDate().shiftedBy(APOGEE_SEARCH_PERIODS * state.getOrbit().getKeplerianPeriod()));
    return apogee.get();
  }

  private static double perigeeRadius(SpacecraftState state, Mission mission) {
    KeplerianOrbit orbit =
        new KeplerianOrbit(
            state.getPVCoordinates(),
            state.getFrame(),
            state.getDate(),
            mission.gravitationalContext().mu());
    return orbit.getA() * (1.0 - orbit.getE());
  }

  private static DeorbitSequence finish(
      List<MissionStage> stages,
      List<DeorbitSequence.Burn> burns,
      SpacecraftState finalState,
      DeorbitSequence.End end) {
    DeorbitSequence sequence = new DeorbitSequence(stages, burns, finalState, end);
    logger.info(
        "Deorbit sequence planned: {} burn(s), {} stage(s), ended {} at {}",
        burns.size(),
        stages.size(),
        end,
        finalState.getDate());
    return sequence;
  }
}
