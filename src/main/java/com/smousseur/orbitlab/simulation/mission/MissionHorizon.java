package com.smousseur.orbitlab.simulation.mission;

import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * How far past insertion a mission is sampled and displayed — the mission's <b>restitution
 * horizon</b> (spec {@code specs/mission-horizon/01-horizon-explicite.md}).
 *
 * <p><b>Not the optimization horizon.</b> This decides only how much of the trailing coast is flown
 * for the ephemeris, and it cannot move an optimizer baseline — that is structural, not merely
 * intended. The optimize pass walks the chain through {@code StageChainRunner.plain()}, whose final
 * coast is zero, and the trailing {@code CoastingStage} does not override {@code
 * propagateStandalone}, so it is a no-op there. Lengthening or shortening this horizon therefore
 * changes no trajectory, only how much of it is recorded.
 *
 * <p>The horizon is carried by {@code MissionSpec} — the user's intent, which must survive a
 * recomposition — and copied onto the {@link Mission} by {@code MissionComposer}. The ephemeris
 * generator never sees it: it receives the resolved number of seconds, because a generator has no
 * business knowing the intent.
 */
public sealed interface MissionHorizon
    permits MissionHorizon.Revolutions, MissionHorizon.FixedDuration, MissionHorizon.TrailingCoast {

  /**
   * Shared logger. Public by necessity — an interface field cannot be private — but not part of the
   * contract: nothing outside this file is expected to use it.
   */
  Logger logger = LogManager.getLogger(MissionHorizon.class);

  /** Seconds in a day, for the fallback, the cap and the {@link #describe()} forms. */
  double SECONDS_PER_DAY = 86_400.0;

  /**
   * Hard cap on the trailing coast. Beyond a month the answer is an out-of-memory ephemeris
   * (roadmap {@code MIS-9}), not a bigger array: at the 60 s coast sampling step, 30 days already
   * cost ~43 000 points (~7 MB) per mission.
   */
  double MAX_COAST_SECONDS = 30.0 * SECONDS_PER_DAY;

  /**
   * Coast applied when a {@link Revolutions} horizon cannot be resolved because the insertion orbit
   * has no Keplerian period. Matches the order of magnitude of the derived defaults, so an
   * unresolvable horizon degrades to a plausible one rather than to nothing.
   */
  double UNRESOLVED_FALLBACK_SECONDS = 3.0 * SECONDS_PER_DAY;

  /** Derived default for a LEO insertion: 48 revolutions ≈ 3.2 days at 550 km. */
  int DEFAULT_LEO_REVOLUTIONS = 48;

  /** Derived default for a GEO insertion: 3 revolutions = 3.0 days. */
  int DEFAULT_GEO_REVOLUTIONS = 3;

  /**
   * The horizon a mission of this type gets when the user did not set one. Both defaults land on ~3
   * days: long enough to watch the orbital plane precess, and already the right order of magnitude
   * for the lunar and rendezvous profiles that will follow.
   *
   * @param type the mission type
   * @return the derived default horizon
   */
  static MissionHorizon defaultFor(MissionType type) {
    return switch (type) {
      case LEO -> new Revolutions(DEFAULT_LEO_REVOLUTIONS);
      case GEO -> new Revolutions(DEFAULT_GEO_REVOLUTIONS);
    };
  }

  /**
   * Resolves this horizon into the number of seconds the trailing coast must be flown for.
   *
   * <p><b>Never throws.</b> This is called from inside {@code MissionOptimizer.optimize()}, where
   * {@code MissionLoadEvaluator} turns any escaping {@code RuntimeException} into "lambda
   * infeasible" — an exception here would silently move the propellant load retained by the sizing
   * sweep. Same boundary discipline as {@code AchievedOrbit.of}.
   *
   * <p>The result is always within {@code [0, MAX_COAST_SECONDS]}.
   *
   * @param launchDate T_start, the date the mission lifts off
   * @param insertionState the state at the end of the last flown stage, before the trailing coast
   * @return the trailing coast duration in seconds
   */
  double finalCoastSeconds(AbsoluteDate launchDate, SpacecraftState insertionState);

  /**
   * A short human-readable form, for logs and for the wizard's helper line.
   *
   * @return the horizon described in one fragment, e.g. {@code "48 revolutions"}
   */
  String describe();

  /**
   * N revolutions of the achieved orbit, counted from insertion. The natural unit for an orbit
   * insertion: it says what it shows, and it tracks the orbit actually reached rather than the one
   * that was aimed at.
   *
   * @param count the number of revolutions, at least 1
   */
  record Revolutions(int count) implements MissionHorizon {

    public Revolutions {
      if (count < 1) {
        throw new IllegalArgumentException("Revolutions must be >= 1, got " + count);
      }
    }

    @Override
    public double finalCoastSeconds(AbsoluteDate launchDate, SpacecraftState insertionState) {
      double period = keplerianPeriodOf(insertionState);
      if (period <= 0.0) {
        logger.warn(
            "Insertion orbit has no Keplerian period (unbound or unreadable); falling back to a"
                + " {}-day coast instead of {} revolutions",
            UNRESOLVED_FALLBACK_SECONDS / SECONDS_PER_DAY,
            count);
        return UNRESOLVED_FALLBACK_SECONDS;
      }
      return capped(count * period);
    }

    /**
     * The Keplerian period at {@code state}, or {@code 0} when the state carries no bound orbit.
     * Built from the PV coordinates rather than read off {@code state.getOrbit()}, which throws on
     * a state propagated as absolute PVA.
     */
    private static double keplerianPeriodOf(SpacecraftState state) {
      try {
        KeplerianOrbit orbit =
            new KeplerianOrbit(
                state.getPVCoordinates(),
                state.getFrame(),
                state.getDate(),
                Constants.WGS84_EARTH_MU);
        // A hyperbolic trajectory has a < 0 and e > 1, where getKeplerianPeriod() is meaningless:
        // the caller must fall back rather than fly a NaN-length coast.
        if (orbit.getA() <= 0.0 || orbit.getE() >= 1.0) {
          return 0.0;
        }
        double period = orbit.getKeplerianPeriod();
        return Double.isFinite(period) && period > 0.0 ? period : 0.0;
      } catch (RuntimeException e) {
        logger.debug(
            "Keplerian period unavailable ({}): {}", e.getClass().getSimpleName(), e.getMessage());
        return 0.0;
      }
    }

    @Override
    public String describe() {
      return count + (count == 1 ? " revolution" : " revolutions");
    }
  }

  /**
   * A fixed <em>total</em> mission duration counted from launch — what the wizard's "mission
   * duration" field writes. The trailing coast is what remains once the ascent has been subtracted,
   * which is why this record cannot be resolved without the insertion date.
   *
   * @param seconds the total mission duration in seconds, finite and strictly positive
   */
  record FixedDuration(double seconds) implements MissionHorizon {

    public FixedDuration {
      if (!(seconds > 0.0) || !Double.isFinite(seconds)) {
        throw new IllegalArgumentException("Duration must be finite and > 0, got " + seconds);
      }
    }

    @Override
    public double finalCoastSeconds(AbsoluteDate launchDate, SpacecraftState insertionState) {
      double ascent = insertionState.getDate().durationFrom(launchDate);
      double coast = seconds - ascent;
      if (coast < 0.0) {
        // The user asked for less than the ascent takes. Clamping to zero hands them the ascent,
        // which is honest and complete, rather than raising on a background thread.
        logger.warn(
            "Requested mission duration ({} s) is shorter than the ascent ({} s); the trajectory"
                + " stops at insertion",
            String.format(Locale.ROOT, "%.0f", seconds),
            String.format(Locale.ROOT, "%.0f", ascent));
        return 0.0;
      }
      return capped(coast);
    }

    @Override
    public String describe() {
      return String.format(Locale.ROOT, "%.2f day(s)", seconds / SECONDS_PER_DAY);
    }
  }

  /**
   * A trailing coast of a fixed length, counted from insertion — the primitive {@code
   * StageChainRunner} already implements, and the exact semantics of the {@code
   * DEFAULT_COAST_DURATION_SECONDS} constant this work replaces.
   *
   * <p>It exists so that {@link Mission}'s default horizon can be <em>provably</em> the legacy
   * behaviour. Expressing that default as a {@link FixedDuration} would have been subtly different:
   * a total duration subtracts the ascent, shortening the coast by ~600 s and moving what {@code
   * GravityTurnFloorProbeTest} measures. Being independent of the insertion date, this case is also
   * immune to whatever {@code mission.getCurrentState()} happens to hold on a direct {@code
   * generate()} call.
   *
   * <p>The wizard never writes this case; it is the compatibility default and a convenience for
   * tests that want an exact coast.
   *
   * @param seconds the trailing coast in seconds, finite and non-negative
   */
  record TrailingCoast(double seconds) implements MissionHorizon {

    public TrailingCoast {
      if (seconds < 0.0 || !Double.isFinite(seconds)) {
        throw new IllegalArgumentException(
            "Trailing coast must be finite and >= 0, got " + seconds);
      }
    }

    @Override
    public double finalCoastSeconds(AbsoluteDate launchDate, SpacecraftState insertionState) {
      return capped(seconds);
    }

    @Override
    public String describe() {
      return String.format(Locale.ROOT, "%.2f day(s) past insertion", seconds / SECONDS_PER_DAY);
    }
  }

  /** Applies the hard cap shared by every case, logging when it bites. */
  private static double capped(double coastSeconds) {
    if (coastSeconds > MAX_COAST_SECONDS) {
      logger.warn(
          "Final coast capped from {} to {} days",
          String.format(Locale.ROOT, "%.1f", coastSeconds / SECONDS_PER_DAY),
          String.format(Locale.ROOT, "%.1f", MAX_COAST_SECONDS / SECONDS_PER_DAY));
      return MAX_COAST_SECONDS;
    }
    return coastSeconds;
  }
}
