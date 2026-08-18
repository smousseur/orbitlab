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
  private static final class TwoDips extends RecordingProblem {
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
