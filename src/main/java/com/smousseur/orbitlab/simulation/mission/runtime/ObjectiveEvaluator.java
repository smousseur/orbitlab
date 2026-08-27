package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.objective.FlybyObjective;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import java.util.List;

/**
 * Scores a flown ephemeris against the objective it was flown for, whatever kind of objective that
 * is (spec {@code docs/lunar-flyby/05-conception-L3.md} §3).
 *
 * <p><b>The switch below is the only place in the repository where {@code MissionObjective} being
 * sealed does any work.</b> The two other sites that read an objective's type do it with an {@code
 * instanceof} and a fallback branch, so adding a member to the hierarchy breaks no compilation unit
 * — the compiler has nowhere to complain. Being exhaustive and having no {@code default}, this
 * switch is where a third objective kind will be pointed out.
 *
 * <p><b>It lives beside {@link MissionLoadEvaluator} rather than in the objective package</b>
 * because the insertion branch delegates to it, and {@code runtime} already depends on {@code
 * objective}: placing this class the other way round and delegating back would make a package
 * cycle, and moving {@code objectiveMet} across would relocate a public API and its ten test call
 * sites for no gain.
 */
public final class ObjectiveEvaluator {

  private ObjectiveEvaluator() {}

  /**
   * Whether the flown trajectory satisfies its objective.
   *
   * @param ephemeris the computed mission ephemeris
   * @param objective the objective to score against
   * @param insertionToleranceRatio the ± band used by the <em>insertion</em> branch only, as a
   *     fraction of each target altitude. A flyby carries its own absolute band ({@link
   *     FlybyObjective#toleranceMeters()}) and ignores this value — the parameter is named for the
   *     branch it serves so that a reader does not have to work out why.
   * @return {@code true} when the flight met the objective
   * @throws IllegalArgumentException when a flyby objective names the body the flight starts at
   */
  public static boolean met(
      MissionEphemeris ephemeris, MissionObjective objective, double insertionToleranceRatio) {
    return switch (objective) {
      case OrbitInsertionObjective insertion ->
          MissionLoadEvaluator.objectiveMet(ephemeris, insertion, insertionToleranceRatio);
      case FlybyObjective flyby -> flybyMet(ephemeris, flyby);
    };
  }

  /**
   * The minimum altitude over every sample recorded on the target body's arc, compared to the aimed
   * closest approach within the objective's own band. The maximum is never read (see {@link
   * FlybyObjective}).
   *
   * <p><b>A minimum reached at the very last sample of the arc is refused</b> (§3.4): see the guard
   * below for why a truncated flight cannot be told from a completed one by the minimum alone.
   *
   * <p><b>Points are selected by body alone</b> — not by stage name, and not by the arc's rank in
   * the flown sequence. A round trip flies {@code [EARTH, MOON, EARTH]} and the arc to measure is
   * the middle one, so selecting by rank would have to pick a rank, and the answer would depend on
   * the mission horizon. Selecting by stage name would replicate the {@code "Coasting"} string
   * coupling {@link MissionLoadEvaluator} carries, for a measurement that has no reason to fall
   * during a terminal coast: on a round trip closest approach happens mid-flight.
   */
  private static boolean flybyMet(MissionEphemeris ephemeris, FlybyObjective flyby) {
    // MissionEphemeris refuses to exist with fewer than two points, so there is a first one.
    List<MissionEphemerisPoint> points = ephemeris.allPoints();
    if (points.get(0).arc().body() == flyby.body()) {
      // A flyby is scored on an arc the flight did not start in. Without this, a flyby objective on
      // the launch body would read the pad as its closest approach and report a miss by the whole
      // target altitude, with nothing saying why. It is thrown rather than returned false because a
      // malformed objective that merely returns false travels through the feasibility AND and comes
      // back out as "no feasible propellant load", sending its author looking for propellant. The
      // condition depends on the objective and the first sample only, never on the propellant
      // scaling, so it fires on the first evaluation of a search rather than half way through one.
      throw new IllegalArgumentException(
          "flyby objective targets "
              + flyby.body()
              + ", which is the body the flight starts at — a flyby is scored on an arc the"
              + " trajectory arrives in");
    }

    double closestApproach = Double.POSITIVE_INFINITY;
    double lastOnTheArc = Double.NaN;
    for (MissionEphemerisPoint point : points) {
      if (point.arc().body() == flyby.body()) {
        closestApproach = Math.min(closestApproach, point.altitudeMeters());
        lastOnTheArc = point.altitudeMeters();
      }
    }
    if (!Double.isFinite(closestApproach)) {
      return false; // the flight never reached the body
    }
    if (closestApproach <= 0.0) {
      return false; // an impact is not a met objective, whatever band was declared
    }
    if (lastOnTheArc <= closestApproach) {
      // The approach was still descending where the recording stops: closest approach was never
      // passed, so this number is not "how close it came" but "where the flight ran out of
      // horizon". Selecting the minimum over the arc cannot tell the two apart on its own, and a
      // truncated flyby yields a perfectly plausible minimum — which is how a horizon too short,
      // or an accidental capture, would otherwise pass silently (MIS-4 / L4 §3.4).
      //
      // It is false and not a throw, by the rule this class already follows: a truncated flight is
      // a fact of flight, like a body never reached or an impact; the throw stays for an objective
      // that is malformed. Written as "at or below" rather than "is the last index" so that a
      // plateau at the minimum refuses too.
      return false;
    }
    return Math.abs(closestApproach - flyby.closestApproachAltitude()) <= flyby.toleranceMeters();
  }
}
