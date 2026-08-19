package com.smousseur.orbitlab.simulation.mission.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/**
 * MIS-2 — the numerics of {@link LaunchWindowSolver}, on merit functions whose minima are known in
 * closed form.
 *
 * <p><b>Synthetic problems and not the Moon, deliberately.</b> A test written on a lunar ephemeris
 * asserts whatever the ephemeris happens to say, so it can only be recorded from a previous run —
 * it proves the solver reproduces yesterday. A parabola has an exact vertex and exact threshold
 * crossings, so every number below is <em>derived</em> and the test can fail for a real reason.
 * Nothing here propagates, nothing here reads {@code orekit-data}.
 *
 * <p><b>One implementation detail leaks into the tolerances, and it is a property rather than a
 * defect.</b> The solver memoises on a key of {@code round(offset / precision)} and evaluates at
 * {@code key · precision}: every evaluation is therefore <em>snapped</em> to the precision grid,
 * and the function the golden section actually minimises is a staircase. The optimum is located to
 * within one precision cell, not to within a rounding error, and the assertions say so.
 */
class LaunchWindowSolverTest {

  private static final AbsoluteDate T0 = AbsoluteDate.J2000_EPOCH;

  private static double offsetOf(AbsoluteDate date) {
    return date.durationFrom(T0);
  }

  // ── fixtures ──────────────────────────────────────────────────────────────

  /** Records every epoch the solver asks about, so cost and caching can both be asserted. */
  private abstract static class RecordingProblem implements LaunchWindowProblem {
    final List<AbsoluteDate> evaluated = new ArrayList<>();
    int confirmations;
    private final Duration coarseStep;

    RecordingProblem(Duration coarseStep) {
      this.coarseStep = coarseStep;
    }

    @Override
    public String name() {
      return getClass().getSimpleName();
    }

    @Override
    public Duration coarseStep() {
      return coarseStep;
    }

    @Override
    public final LaunchWindowCandidate evaluate(AbsoluteDate epoch) {
      evaluated.add(epoch);
      return at(epoch, offsetOf(epoch));
    }

    abstract LaunchWindowCandidate at(AbsoluteDate epoch, double offsetSeconds);
  }

  /**
   * A single parabolic dip: {@code floor + rise · ((t − vertex)/halfWidth)²}.
   *
   * <p>Chosen because both the optimum and the two threshold crossings have a closed form, so the
   * edge assertions compare against arithmetic rather than against the solver's own output.
   */
  private static class SingleDip extends RecordingProblem {
    static final double VERTEX = 7_200.0;
    static final double FLOOR = 3_000.0;
    static final double RISE = 100.0;
    static final double HALF_WIDTH = 1_800.0;

    SingleDip() {
      super(Duration.ofMinutes(10));
    }

    @Override
    LaunchWindowCandidate at(AbsoluteDate epoch, double offsetSeconds) {
      return LaunchWindowCandidate.of(epoch, cost(offsetSeconds));
    }

    static double cost(double offsetSeconds) {
      double normalised = (offsetSeconds - VERTEX) / HALF_WIDTH;
      return FLOOR + RISE * normalised * normalised;
    }

    /** Where the cost crosses {@code budget}, exactly. */
    static double crossing(double budget, int sign) {
      return VERTEX + sign * HALF_WIDTH * Math.sqrt((budget - FLOOR) / RISE);
    }
  }

  /** Two dips of different depths, as the minimum of two parabolas — no plateau, no ties. */
  private static class TwoDips extends RecordingProblem {
    static final double SHALLOW_VERTEX = 10_800.0;
    static final double DEEP_VERTEX = 32_400.0;

    TwoDips() {
      super(Duration.ofMinutes(15));
    }

    @Override
    LaunchWindowCandidate at(AbsoluteDate epoch, double offsetSeconds) {
      double shallow = quadratic(offsetSeconds, SHALLOW_VERTEX, 3_200.0);
      double deep = quadratic(offsetSeconds, DEEP_VERTEX, 3_000.0);
      return LaunchWindowCandidate.of(epoch, Math.min(shallow, deep));
    }

    private static double quadratic(double offset, double vertex, double floor) {
      double normalised = (offset - vertex) / 1_800.0;
      return floor + 200.0 * normalised * normalised;
    }
  }

  /**
   * A narrow notch on a gently rising baseline: 600 s wide, 1 500 m/s deep, centred off every round
   * hour so a coarse sweep cannot stumble onto it by luck.
   */
  private static final class NarrowNotch extends RecordingProblem {
    static final double CENTRE = 19_500.0;
    static final double HALF_WIDTH = 300.0;

    NarrowNotch() {
      super(Duration.ofMinutes(2));
    }

    @Override
    LaunchWindowCandidate at(AbsoluteDate epoch, double offsetSeconds) {
      double normalised = (offsetSeconds - CENTRE) / HALF_WIDTH;
      double dip = Math.max(0.0, 1.0 - normalised * normalised);
      // The baseline slopes, so no two samples tie: a flat baseline would make every grid point
      // "cheaper than or equal to both neighbours" and manufacture a window per sample.
      return LaunchWindowCandidate.of(epoch, 2_000.0 + 1.0e-3 * offsetSeconds - 1_500.0 * dip);
    }
  }

  /** Refuses everything before three hours, then the usual parabolic dip at six. */
  private static final class RefusesTheFirstThreeHours extends RecordingProblem {
    static final double VERTEX = 21_600.0;

    RefusesTheFirstThreeHours() {
      super(Duration.ofMinutes(30));
    }

    @Override
    LaunchWindowCandidate at(AbsoluteDate epoch, double offsetSeconds) {
      if (offsetSeconds < 10_800.0) {
        return LaunchWindowCandidate.refused(epoch, "the pad is not configured before T+3h");
      }
      double normalised = (offsetSeconds - VERTEX) / 3_600.0;
      return LaunchWindowCandidate.of(epoch, 3_000.0 + 300.0 * normalised * normalised);
    }
  }

  /** Refuses every epoch it is shown. */
  private static final class RefusesEverything extends RecordingProblem {
    RefusesEverything() {
      super(Duration.ofMinutes(30));
    }

    @Override
    LaunchWindowCandidate at(AbsoluteDate epoch, double offsetSeconds) {
      return LaunchWindowCandidate.refused(epoch, "no geometry at any epoch");
    }
  }

  /**
   * The same parabola as {@link SingleDip}, without the recording — an immutable problem, so it can
   * be evaluated from several threads at once and the reentrancy test measures the solver rather
   * than an {@code ArrayList}.
   */
  private static final class SharedDip implements LaunchWindowProblem {
    @Override
    public String name() {
      return "SharedDip";
    }

    @Override
    public Duration coarseStep() {
      return Duration.ofMinutes(10);
    }

    @Override
    public LaunchWindowCandidate evaluate(AbsoluteDate epoch) {
      return LaunchWindowCandidate.of(epoch, SingleDip.cost(offsetOf(epoch)));
    }
  }

  /** Monotonically cheaper as time goes on: the slot is still open when the range ends. */
  private static final class StillFalling extends RecordingProblem {
    StillFalling() {
      super(Duration.ofMinutes(5));
    }

    @Override
    LaunchWindowCandidate at(AbsoluteDate epoch, double offsetSeconds) {
      return LaunchWindowCandidate.of(epoch, 3_000.0 - 0.01 * offsetSeconds);
    }
  }

  // ── the optimum ───────────────────────────────────────────────────────────

  @Test
  @DisplayName("The refined optimum is the true vertex, to within one precision cell")
  void goldenSectionFindsTheVertex() {
    SingleDip problem = new SingleDip();
    LaunchWindowSearch search = LaunchWindowSearch.over(T0, Duration.ofHours(4), problem, 3_050.0);

    List<LaunchWindow> windows = new LaunchWindowSolver(problem).solve(search);

    assertEquals(1, windows.size(), "one dip, one window");
    double best = offsetOf(windows.get(0).date());

    // The tolerance is two precision cells (2 × 60 s) and not a rounding error, for the reason the
    // class javadoc gives: the staircase created by the evaluation cache ties the two golden probes
    // near the vertex, and the search settles one cell off. The cost is what says it did not settle
    // anywhere else — 3 000.11 m/s is the parabola 60 s from its floor.
    assertEquals(SingleDip.VERTEX, best, 2.0 * search.precisionSeconds());
    assertTrue(
        windows.get(0).best().deltaV() < SingleDip.FLOOR + 1.0,
        "the optimum must sit at the bottom of the dip, got " + windows.get(0).best().deltaV());
  }

  @Test
  @DisplayName("A coarse sweep that steps over a dip reports no window at all")
  void aSweepCoarserThanTheFeatureFindsNothing() {
    // The executable form of why coarseStep() belongs to the problem (spec §5.a). The notch is
    // 600 s wide; sampled hourly it is invisible, and the failure is silent — an empty list reads
    // exactly like "there is genuinely no window". This test is what stops that from being a
    // surprise found in production.
    NarrowNotch problem = new NarrowNotch();

    List<LaunchWindow> missed =
        new LaunchWindowSolver(problem)
            .solve(
                new LaunchWindowSearch(
                    T0,
                    Duration.ofHours(10),
                    Duration.ofHours(1),
                    Duration.ofMinutes(6),
                    900.0,
                    1));
    assertTrue(missed.isEmpty(), "an hourly sweep cannot see a ten-minute notch");

    List<LaunchWindow> found =
        new LaunchWindowSolver(problem)
            .solve(LaunchWindowSearch.over(T0, Duration.ofHours(10), problem, 900.0));

    assertEquals(1, found.size(), "sampled at the problem's own scale, the notch is there");
    double best = offsetOf(found.get(0).date());
    assertTrue(
        Math.abs(best - NarrowNotch.CENTRE) < NarrowNotch.HALF_WIDTH,
        "the optimum must land inside the notch, got T+" + best + " s");
    assertTrue(found.get(0).best().deltaV() < 700.0, "and at the notch's depth");
  }

  // ── the edges ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "The edges bracket the acceptance threshold: inside is affordable, one cell out is not")
  void theEdgesBracketTheBudget() {
    SingleDip problem = new SingleDip();
    double budget = 3_050.0;
    LaunchWindowSearch search = LaunchWindowSearch.over(T0, Duration.ofHours(4), problem, budget);

    LaunchWindow window = new LaunchWindowSolver(problem).solve(search).get(0);
    double opening = offsetOf(window.opening());
    double closing = offsetOf(window.closing());
    double precision = search.precisionSeconds();

    // Against the closed form, not against a recorded run: cost = budget at vertex ± hw·√(Δ/rise).
    assertEquals(SingleDip.crossing(budget, -1), opening, precision, "opening edge");
    assertEquals(SingleDip.crossing(budget, +1), closing, precision, "closing edge");

    // And the property that makes them edges rather than two numbers near a third.
    assertTrue(SingleDip.cost(opening) <= budget, "the opening must be affordable");
    assertTrue(SingleDip.cost(closing) <= budget, "the closing must be affordable");
    assertTrue(SingleDip.cost(opening - precision) > budget, "one cell earlier must not be");
    assertTrue(SingleDip.cost(closing + precision) > budget, "one cell later must not be");

    assertTrue(opening < offsetOf(window.date()), "the optimum sits inside its own slot");
    assertTrue(offsetOf(window.date()) < closing);
  }

  @Test
  @DisplayName(
      "A slot still open at the end of the range is reported at the bound, not closed early")
  void anOpenEndedSlotIsCutAtTheSearchBound() {
    StillFalling problem = new StillFalling();
    LaunchWindowSearch search =
        new LaunchWindowSearch(
            T0, Duration.ofHours(1), Duration.ofMinutes(5), Duration.ofSeconds(30), 2_980.0, 1);

    LaunchWindow window = new LaunchWindowSolver(problem).solve(search).get(0);

    // Reporting the bound rather than manufacturing a closing time is the honest answer: the slot
    // does not close inside what was searched, and the caller can widen the span.
    assertEquals(
        search.spanSeconds(), offsetOf(window.closing()), 1.0e-6, "closing is the search bound");
    // 3000 − 0.01·t = 2980 at t = 2000 s.
    assertEquals(2_000.0, offsetOf(window.opening()), 2.0 * search.precisionSeconds());

    // Pinned because it is surprising rather than because it is ideal: the edge walk probes one
    // stride past the bound before deciding, so a problem must be defined slightly beyond the range
    // it was asked about. A problem that throws there would fail a search that never claimed to
    // look there.
    assertTrue(
        problem.evaluated.stream().anyMatch(d -> offsetOf(d) > search.spanSeconds()),
        "the solver evaluates one stride past the range");
  }

  // ── refusals are data ─────────────────────────────────────────────────────

  @Test
  @DisplayName("Refused epochs are walked past, not thrown on, and the sweep completes behind them")
  void refusalsDoNotStopTheSweep() {
    RefusesTheFirstThreeHours problem = new RefusesTheFirstThreeHours();
    LaunchWindowSearch search = LaunchWindowSearch.over(T0, Duration.ofHours(12), problem, 3_100.0);

    List<LaunchWindow> windows = new LaunchWindowSolver(problem).solve(search);

    assertEquals(1, windows.size(), "the dip past the refused region is still found");
    assertEquals(
        RefusesTheFirstThreeHours.VERTEX,
        offsetOf(windows.get(0).date()),
        2.0 * search.precisionSeconds());
    assertTrue(
        offsetOf(windows.get(0).opening()) >= 10_800.0,
        "and no part of the slot reaches into the refused region");

    // The refused samples were evaluated and skipped rather than aborting the traversal: the last
    // grid point was still reached.
    assertTrue(
        problem.evaluated.stream().anyMatch(d -> offsetOf(d) >= search.spanSeconds() - 1.0),
        "the sweep ran to the end of the range");
  }

  @Test
  @DisplayName("A range in which every epoch is refused yields no window, and no exception")
  void anEntirelyRefusedRangeIsEmpty() {
    RefusesEverything problem = new RefusesEverything();

    List<LaunchWindow> windows =
        new LaunchWindowSolver(problem)
            .solve(LaunchWindowSearch.over(T0, Duration.ofHours(12), problem, 3_100.0));

    assertTrue(windows.isEmpty(), "an infinite cost cannot bracket a minimum");
  }

  // ── ordering and capping ──────────────────────────────────────────────────

  @Test
  @DisplayName("Windows come back cheapest first, and maxWindows keeps the cheapest ones")
  void windowsAreOrderedByCostAndCapped() {
    TwoDips problem = new TwoDips();
    LaunchWindowSearch search =
        new LaunchWindowSearch(
            T0, Duration.ofHours(12), Duration.ofMinutes(15), Duration.ofMinutes(1), 3_300.0, 5);

    List<LaunchWindow> windows = new LaunchWindowSolver(problem).solve(search);

    assertEquals(2, windows.size(), "two dips fit the budget");
    assertTrue(
        windows.get(0).best().deltaV() < windows.get(1).best().deltaV(),
        "cheapest first — the order the wizard offers them in");
    assertEquals(
        TwoDips.DEEP_VERTEX,
        offsetOf(windows.get(0).date()),
        2.0 * search.precisionSeconds(),
        "the deeper dip leads");
    assertEquals(
        TwoDips.SHALLOW_VERTEX, offsetOf(windows.get(1).date()), 2.0 * search.precisionSeconds());

    // The cap keeps the cheapest, not the earliest: a mission scheduled from the first slot in
    // chronological order would spend 200 m/s more for nothing.
    List<LaunchWindow> capped =
        new LaunchWindowSolver(new TwoDips()).solve(search.withMaxWindows(1));
    assertEquals(1, capped.size());
    assertEquals(
        TwoDips.DEEP_VERTEX, offsetOf(capped.get(0).date()), 2.0 * search.precisionSeconds());
  }

  @Test
  @DisplayName("The returned list is immutable")
  void theResultIsImmutable() {
    TwoDips problem = new TwoDips();
    List<LaunchWindow> windows =
        new LaunchWindowSolver(problem)
            .solve(
                new LaunchWindowSearch(
                    T0,
                    Duration.ofHours(12),
                    Duration.ofMinutes(15),
                    Duration.ofMinutes(1),
                    3_300.0,
                    5));

    assertThrows(UnsupportedOperationException.class, () -> windows.remove(0));
  }

  // ── the acceptance threshold ──────────────────────────────────────────────

  @Test
  @DisplayName("A margin cuts the same slot an absolute budget of the same value would")
  void aMarginCutsTheSlotAnEquivalentBudgetWould() {
    // The equivalence is the specification: 50 m/s over a 3 000 m/s floor is the 3 050 m/s budget,
    // said without knowing the floor. Asserting one against the other rather than against numbers
    // is what makes this a test of the anchoring and not of the parabola.
    SingleDip absolute = new SingleDip();
    LaunchWindow byBudget =
        new LaunchWindowSolver(absolute)
            .solve(LaunchWindowSearch.over(T0, Duration.ofHours(4), absolute, 3_050.0))
            .get(0);

    SingleDip relative = new SingleDip();
    LaunchWindowSearch search =
        LaunchWindowSearch.over(T0, Duration.ofHours(4), relative, 1.0e9).withMargin(50.0);
    LaunchWindow byMargin = new LaunchWindowSolver(relative).solve(search).get(0);

    assertEquals(
        offsetOf(byBudget.opening()),
        offsetOf(byMargin.opening()),
        search.precisionSeconds(),
        "the opening must not depend on which of the two ways the threshold was said");
    assertEquals(
        offsetOf(byBudget.closing()), offsetOf(byMargin.closing()), search.precisionSeconds());
    assertEquals(offsetOf(byBudget.date()), offsetOf(byMargin.date()), search.precisionSeconds());
  }

  @Test
  @DisplayName("Budget and margin both bite: the lower of the two cuts the slot")
  void theLowerOfTheTwoThresholdsCutsTheSlot() {
    SingleDip problem = new SingleDip();
    LaunchWindowSearch search =
        LaunchWindowSearch.over(T0, Duration.ofHours(4), problem, 3_020.0).withMargin(50.0);

    LaunchWindow window = new LaunchWindowSolver(problem).solve(search).get(0);

    // Budget 3 020 against a floor of 3 000 + margin 50 = 3 050: the budget wins.
    assertEquals(
        SingleDip.crossing(3_020.0, -1),
        offsetOf(window.opening()),
        2.0 * search.precisionSeconds());
    assertEquals(
        SingleDip.crossing(3_020.0, +1),
        offsetOf(window.closing()),
        2.0 * search.precisionSeconds());

    SingleDip other = new SingleDip();
    LaunchWindow byMargin =
        new LaunchWindowSolver(other)
            .solve(LaunchWindowSearch.over(T0, Duration.ofHours(4), other, 3_050.0).withMargin(10.0))
            .get(0);

    // And the other way round: budget 3 050 against 3 000 + 10, the margin wins.
    assertEquals(
        SingleDip.crossing(3_010.0, -1),
        offsetOf(byMargin.opening()),
        2.0 * search.precisionSeconds());
  }

  @Test
  @DisplayName("The margin is anchored on the screening cost, not on the confirmed one")
  void theMarginIsAnchoredOnTheScreeningTier() {
    // The translunar shape: confirmation comes back 40 m/s cheaper than the screen. Anchoring the
    // margin on it would move the threshold down by 40 while the edge walk still reads screening
    // costs, and the slot would collapse to almost nothing. Both tiers must be compared to their
    // own kind.
    SingleDip confirming =
        new SingleDip() {
          @Override
          public LaunchWindowCandidate confirm(LaunchWindowCandidate candidate) {
            return LaunchWindowCandidate.of(candidate.epoch(), candidate.deltaV() - 40.0);
          }
        };
    LaunchWindowSearch search =
        LaunchWindowSearch.over(T0, Duration.ofHours(4), confirming, 1.0e9).withMargin(50.0);

    LaunchWindow window = new LaunchWindowSolver(confirming).solve(search).get(0);

    assertEquals(
        SingleDip.crossing(3_050.0, -1),
        offsetOf(window.opening()),
        2.0 * search.precisionSeconds(),
        "the threshold is the screened floor + 50, not the confirmed floor + 50");
    assertEquals(
        SingleDip.crossing(3_050.0, +1),
        offsetOf(window.closing()),
        2.0 * search.precisionSeconds());
  }

  @Test
  @DisplayName("A refused optimum does not become the anchor: the margin follows the cheapest that flies")
  void theAnchorIsTheCheapestFlyableMinimum() {
    // The deep dip is refused by the full model. Anchoring the margin on it anyway would put the
    // threshold at 3 050 and price the shallow dip (3 200) out of its own search — "no window"
    // reported for a month that has one. The anchor is therefore the first minimum the
    // confirmation accepts, not the first one the sweep finds.
    TwoDips problem =
        new TwoDips() {
          @Override
          public LaunchWindowCandidate confirm(LaunchWindowCandidate candidate) {
            confirmations++;
            if (Math.abs(offsetOf(candidate.epoch()) - TwoDips.DEEP_VERTEX) < 1_800.0) {
              return LaunchWindowCandidate.refused(candidate.epoch(), "the pad is down that week");
            }
            return candidate;
          }
        };
    LaunchWindowSearch search =
        new LaunchWindowSearch(
            T0,
            Duration.ofHours(12),
            Duration.ofMinutes(15),
            Duration.ofMinutes(1),
            1.0e9,
            50.0,
            5);

    List<LaunchWindow> windows = new LaunchWindowSolver(problem).solve(search);

    assertEquals(1, windows.size(), "the shallow dip is still an opportunity");
    assertEquals(
        TwoDips.SHALLOW_VERTEX,
        offsetOf(windows.get(0).date()),
        2.0 * search.precisionSeconds());
    // 3 200 + 200·((t − 10 800)/1 800)² = 3 250 at t = 10 800 ± 900.
    assertEquals(
        TwoDips.SHALLOW_VERTEX - 900.0,
        offsetOf(windows.get(0).opening()),
        2.0 * search.precisionSeconds());
    assertEquals(
        TwoDips.SHALLOW_VERTEX + 900.0,
        offsetOf(windows.get(0).closing()),
        2.0 * search.precisionSeconds());
  }

  @Test
  @DisplayName("A minimum dearer than the threshold is never handed to the expensive tier")
  void aMinimumPastTheThresholdIsNotConfirmed() {
    // Where the saving is: the shallow dip costs 200 m/s more than the deep one, so a 50 m/s margin
    // rules it out on the screening alone. Confirming it would be paying the expensive tier for an
    // answer already known.
    TwoDips problem =
        new TwoDips() {
          @Override
          public LaunchWindowCandidate confirm(LaunchWindowCandidate candidate) {
            confirmations++;
            return candidate;
          }
        };
    LaunchWindowSearch search =
        new LaunchWindowSearch(
            T0,
            Duration.ofHours(12),
            Duration.ofMinutes(15),
            Duration.ofMinutes(1),
            1.0e9,
            50.0,
            5);

    List<LaunchWindow> windows = new LaunchWindowSolver(problem).solve(search);

    assertEquals(1, windows.size(), "only the deep dip is within 50 m/s of the best");
    assertEquals(
        TwoDips.DEEP_VERTEX, offsetOf(windows.get(0).date()), 2.0 * search.precisionSeconds());
    assertEquals(1, problem.confirmations, "the dearer minimum must not be confirmed");
  }

  // ── overlapping slots ─────────────────────────────────────────────────────

  @Test
  @DisplayName("Two minima whose slots reach each other are reported as one window")
  void overlappingSlotsAreFusedIntoOne() {
    // At a 12 000 m/s threshold the composite of the two parabolas never leaves the budget between
    // the dips — it peaks at 10 300 where they cross — so the two edge walks run through each
    // other. That is one open slot with two local minima, not two slots, and offering it twice
    // would spend the maxWindows budget on the same interval.
    TwoDips problem = new TwoDips();
    LaunchWindowSearch search =
        new LaunchWindowSearch(
            T0,
            Duration.ofHours(24),
            Duration.ofMinutes(15),
            Duration.ofMinutes(1),
            12_000.0,
            5);

    List<LaunchWindow> windows = new LaunchWindowSolver(problem).solve(search);

    assertEquals(1, windows.size(), "one interval, even though two minima were bracketed");
    assertEquals(
        TwoDips.DEEP_VERTEX,
        offsetOf(windows.get(0).date()),
        2.0 * search.precisionSeconds(),
        "the surviving optimum is the cheaper of the two");
    assertEquals(0.0, offsetOf(windows.get(0).opening()), 1.0e-6, "open from the range start");
    // 3 000 + 200·((t − 32 400)/1 800)² = 12 000 at t = 32 400 + 1 800·√45.
    assertEquals(
        32_400.0 + 1_800.0 * Math.sqrt(45.0),
        offsetOf(windows.get(0).closing()),
        2.0 * search.precisionSeconds());
  }

  // ── the two tiers ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("confirm() runs once per bracketed minimum and its cost is the one reported")
  void confirmationRecostsTheWindow() {
    // The two-tier contract: the cheap criterion ranks and brackets, the expensive one has the last
    // word on what the window costs.
    SingleDip confirming =
        new SingleDip() {
          @Override
          public LaunchWindowCandidate confirm(LaunchWindowCandidate candidate) {
            confirmations++;
            return LaunchWindowCandidate.of(candidate.epoch(), candidate.deltaV() + 40.0);
          }
        };
    LaunchWindowSearch search =
        LaunchWindowSearch.over(T0, Duration.ofHours(4), confirming, 3_050.0);

    LaunchWindow window = new LaunchWindowSolver(confirming).solve(search).get(0);

    assertEquals(1, confirming.confirmations, "confirmed once, not once per sample");
    assertEquals(
        SingleDip.cost(offsetOf(window.date())) + 40.0,
        window.best().deltaV(),
        1.0e-9,
        "the reported cost is the confirmed one");
  }

  @Test
  @DisplayName("A confirmation that refuses, or that overruns the budget, withdraws the window")
  void aFailedConfirmationWithdrawsTheWindow() {
    SingleDip refusing =
        new SingleDip() {
          @Override
          public LaunchWindowCandidate confirm(LaunchWindowCandidate candidate) {
            confirmations++;
            return LaunchWindowCandidate.refused(candidate.epoch(), "the flown perilune is 135 km");
          }
        };
    assertTrue(
        new LaunchWindowSolver(refusing)
            .solve(LaunchWindowSearch.over(T0, Duration.ofHours(4), refusing, 3_050.0))
            .isEmpty(),
        "a screened epoch the full model refuses must not be offered");
    assertEquals(1, refusing.confirmations);

    SingleDip expensive =
        new SingleDip() {
          @Override
          public LaunchWindowCandidate confirm(LaunchWindowCandidate candidate) {
            return LaunchWindowCandidate.of(candidate.epoch(), candidate.deltaV() + 100.0);
          }
        };
    assertTrue(
        new LaunchWindowSolver(expensive)
            .solve(LaunchWindowSearch.over(T0, Duration.ofHours(4), expensive, 3_050.0))
            .isEmpty(),
        "and neither must one the full model prices out of the budget");
  }

  @Test
  @DisplayName("The edges are cut on the screening cost, not on the confirmed one")
  void theEdgesIgnoreTheConfirmationSurcharge() {
    // An asymmetry of the current design, pinned rather than praised. Only the optimum is confirmed
    // — confirming the edges would cost two more runs of the expensive tier per window — so a
    // surcharge shifts the reported cost without narrowing the slot. Worth knowing before a caller
    // reads the width as "how long the confirmed cost stays affordable".
    SingleDip plain = new SingleDip();
    SingleDip surcharged =
        new SingleDip() {
          @Override
          public LaunchWindowCandidate confirm(LaunchWindowCandidate candidate) {
            return LaunchWindowCandidate.of(candidate.epoch(), candidate.deltaV() + 40.0);
          }
        };

    LaunchWindow withoutSurcharge =
        new LaunchWindowSolver(plain)
            .solve(LaunchWindowSearch.over(T0, Duration.ofHours(4), plain, 3_050.0))
            .get(0);
    LaunchWindow withSurcharge =
        new LaunchWindowSolver(surcharged)
            .solve(LaunchWindowSearch.over(T0, Duration.ofHours(4), surcharged, 3_050.0))
            .get(0);

    assertEquals(withoutSurcharge.duration(), withSurcharge.duration());
    assertTrue(withSurcharge.best().deltaV() > withoutSurcharge.best().deltaV());
  }

  // ── the cache ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("No epoch is evaluated twice, and every evaluation lands on the precision grid")
  void evaluationsAreMemoisedOnThePrecisionGrid() {
    SingleDip problem = new SingleDip();
    LaunchWindowSearch search = LaunchWindowSearch.over(T0, Duration.ofHours(4), problem, 3_050.0);

    new LaunchWindowSolver(problem).solve(search);

    Set<AbsoluteDate> distinct = new HashSet<>(problem.evaluated);
    assertEquals(
        distinct.size(),
        problem.evaluated.size(),
        "the golden section and the two bisections revisit instants; the cache must absorb that");

    for (AbsoluteDate date : problem.evaluated) {
      double cells = offsetOf(date) / search.precisionSeconds();
      assertEquals(
          Math.round(cells),
          cells,
          1.0e-9,
          "every evaluation is snapped to the precision grid, at T+" + offsetOf(date) + " s");
    }
  }

  @Test
  @DisplayName("One solver serves concurrent searches without mixing them")
  void theSolverIsReentrant() throws Exception {
    // Two searches of different widths, interleaved across four threads on one solver. Each must
    // come back with the answer it gives alone: a per-search state held on the solver would not
    // throw here, it would quietly answer one search with the other's grid.
    SharedDip problem = new SharedDip();
    LaunchWindowSolver solver = new LaunchWindowSolver(problem);
    LaunchWindowSearch narrow = LaunchWindowSearch.over(T0, Duration.ofHours(4), problem, 3_050.0);
    LaunchWindowSearch wide =
        new LaunchWindowSearch(
            T0, Duration.ofHours(8), Duration.ofMinutes(10), Duration.ofMinutes(1), 3_200.0, 1);

    List<LaunchWindow> expectedNarrow = solver.solve(narrow);
    List<LaunchWindow> expectedWide = solver.solve(wide);
    assertFalse(
        expectedNarrow.equals(expectedWide), "the two searches must differ for this to prove much");

    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      List<Callable<List<LaunchWindow>>> jobs = new ArrayList<>();
      for (int i = 0; i < 40; i++) {
        LaunchWindowSearch search = i % 2 == 0 ? narrow : wide;
        jobs.add(() -> solver.solve(search));
      }
      List<Future<List<LaunchWindow>>> results = pool.invokeAll(jobs);
      for (int i = 0; i < results.size(); i++) {
        assertEquals(
            i % 2 == 0 ? expectedNarrow : expectedWide,
            results.get(i).get(),
            "concurrent search " + i + " came back with another search's answer");
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("A solver can be reused, and its cache does not survive between searches")
  void theSolverIsReusableAndItsCacheIsPerSearch() {
    SingleDip problem = new SingleDip();
    LaunchWindowSearch search = LaunchWindowSearch.over(T0, Duration.ofHours(4), problem, 3_050.0);
    LaunchWindowSolver solver = new LaunchWindowSolver(problem);

    List<LaunchWindow> first = solver.solve(search);
    int afterFirst = problem.evaluated.size();
    List<LaunchWindow> second = solver.solve(search);

    assertEquals(first, second, "the search is deterministic");
    assertEquals(
        2 * afterFirst,
        problem.evaluated.size(),
        "the cache is cleared per solve, so a re-run under a changed model is not served stale");

    // And the same solver answering a different search is not poisoned by the first one's grid.
    List<LaunchWindow> narrower =
        solver.solve(LaunchWindowSearch.over(T0, Duration.ofHours(3), problem, 3_050.0));
    assertFalse(narrower.isEmpty());
  }
}
