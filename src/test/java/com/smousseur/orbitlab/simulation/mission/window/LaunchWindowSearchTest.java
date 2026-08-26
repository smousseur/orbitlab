package com.smousseur.orbitlab.simulation.mission.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/**
 * MIS-2 — the search criteria, and the four ways of asking for a search that cannot mean anything.
 *
 * <p>Each guard is tested for its <em>message</em> as well as its type, because these are
 * configuration mistakes surfacing in a wizard: "the sweep step must fit in the span" is actionable
 * where a bare {@code IllegalArgumentException} is not.
 */
class LaunchWindowSearchTest {

  private static final AbsoluteDate T0 = AbsoluteDate.J2000_EPOCH;

  /** A problem that exists only to declare a sampling scale. */
  private static LaunchWindowProblem problemSampledEvery(Duration step) {
    return new LaunchWindowProblem() {
      @Override
      public String name() {
        return "fixture";
      }

      @Override
      public LaunchWindowCandidate evaluate(AbsoluteDate epoch) {
        return LaunchWindowCandidate.of(epoch, 1.0);
      }

      @Override
      public Duration coarseStep() {
        return step;
      }

      /** Unused by these guards: set equal to the step for a self-consistent fixture. */
      @Override
      public Duration recurrence() {
        return step;
      }
    };
  }

  private static LaunchWindowProblem fakeProblem(Duration recurrence, Duration coarseStep) {
    return new LaunchWindowProblem() {
      @Override
      public String name() {
        return "fake";
      }

      @Override
      public LaunchWindowCandidate evaluate(AbsoluteDate epoch) {
        return LaunchWindowCandidate.of(epoch, 0.0);
      }

      @Override
      public Duration coarseStep() {
        return coarseStep;
      }

      @Override
      public Duration recurrence() {
        return recurrence;
      }
    };
  }

  private static LaunchWindowSearch valid() {
    return new LaunchWindowSearch(
        T0, Duration.ofDays(60), Duration.ofHours(6), Duration.ofMinutes(36), 3200.0, 1);
  }

  @Test
  @DisplayName("The seconds accessors convert the durations the solver arithmetic runs on")
  void secondsAccessorsConvert() {
    LaunchWindowSearch search = valid();

    assertEquals(60.0 * 86_400.0, search.spanSeconds());
    assertEquals(21_600.0, search.stepSeconds());
    assertEquals(2_160.0, search.precisionSeconds());
  }

  @Test
  @DisplayName("A zero or negative span is refused")
  void theSpanMustBePositive() {
    OrbitlabException zero =
        assertThrows(
            OrbitlabException.class,
            () ->
                new LaunchWindowSearch(
                    T0, Duration.ZERO, Duration.ofHours(6), Duration.ofMinutes(36), 3200.0, 1));
    assertTrue(zero.getMessage().contains("span"));

    assertThrows(
        OrbitlabException.class,
        () ->
            new LaunchWindowSearch(
                T0, Duration.ofDays(-1), Duration.ofHours(6), Duration.ofMinutes(36), 3200.0, 1));
  }

  @Test
  @DisplayName("A sweep step larger than the span is refused")
  void theStepMustFitInTheSpan() {
    // Not pedantry: a step past the span leaves a single grid sample, whose two neighbours are both
    // read as infinitely expensive — so it always "brackets a minimum" and the search reports a
    // window wherever it was told to start looking.
    OrbitlabException thrown =
        assertThrows(
            OrbitlabException.class,
            () ->
                new LaunchWindowSearch(
                    T0,
                    Duration.ofHours(1),
                    Duration.ofHours(6),
                    Duration.ofMinutes(1),
                    3200.0,
                    1));
    assertTrue(thrown.getMessage().contains("step"));
  }

  @Test
  @DisplayName("A precision equal to or coarser than the step is refused")
  void thePrecisionMustBeFinerThanTheStep() {
    OrbitlabException equal =
        assertThrows(
            OrbitlabException.class,
            () ->
                new LaunchWindowSearch(
                    T0, Duration.ofDays(60), Duration.ofHours(6), Duration.ofHours(6), 3200.0, 1));
    assertTrue(equal.getMessage().contains("precision"));

    assertThrows(
        OrbitlabException.class,
        () ->
            new LaunchWindowSearch(
                T0, Duration.ofDays(60), Duration.ofHours(6), Duration.ofHours(12), 3200.0, 1));
  }

  @Test
  @DisplayName("A non-positive or NaN delta-v budget is refused")
  void theBudgetMustBePositive() {
    assertThrows(
        OrbitlabException.class,
        () ->
            new LaunchWindowSearch(
                T0, Duration.ofDays(60), Duration.ofHours(6), Duration.ofMinutes(36), 0.0, 1));
    // NaN passes `x < 0` and `x <= 0`; the guard is written `!(x > 0)` for exactly this, and a NaN
    // budget would make every comparison in the edge search false — a window that never closes.
    assertThrows(
        OrbitlabException.class,
        () ->
            new LaunchWindowSearch(
                T0,
                Duration.ofDays(60),
                Duration.ofHours(6),
                Duration.ofMinutes(36),
                Double.NaN,
                1));
  }

  @Test
  @DisplayName("Asking for fewer than one window is refused")
  void atLeastOneWindowMustBeAskedFor() {
    assertThrows(
        OrbitlabException.class,
        () ->
            new LaunchWindowSearch(
                T0, Duration.ofDays(60), Duration.ofHours(6), Duration.ofMinutes(36), 3200.0, 0));
  }

  @Test
  @DisplayName("over(...) adopts the problem's sampling scale rather than imposing one")
  void overTakesItsStepFromTheProblem() {
    LaunchWindowSearch search =
        LaunchWindowSearch.over(
            T0, Duration.ofDays(60), problemSampledEvery(Duration.ofHours(6)), 3200.0);

    assertEquals(Duration.ofHours(6), search.step(), "the step is the problem's, not a constant");
    assertEquals(Duration.ofMinutes(36), search.precision(), "a tenth of the step");
    assertEquals(1, search.maxWindows());
    assertEquals(3200.0, search.maxDeltaV());
  }

  @Test
  @DisplayName("over(...) refuses a span shorter than the problem's own sampling scale")
  void overRefusesASpanTooShortToSampleTheProblem() {
    // Searching two hours of a criterion that only resolves at six is not a narrow search, it is an
    // unanswerable one. Better here than as an empty result the caller reads as "no window".
    assertThrows(
        OrbitlabException.class,
        () ->
            LaunchWindowSearch.over(
                T0, Duration.ofHours(2), problemSampledEvery(Duration.ofHours(6)), 3200.0));
  }

  @Test
  @DisplayName("withMaxWindows changes the count and nothing else")
  void withMaxWindowsPreservesTheRest() {
    LaunchWindowSearch search = valid().withMaxWindows(5);

    assertEquals(5, search.maxWindows());
    assertEquals(valid().start(), search.start());
    assertEquals(valid().span(), search.span());
    assertEquals(valid().step(), search.step());
    assertEquals(valid().precision(), search.precision());
    assertEquals(valid().maxDeltaV(), search.maxDeltaV());
    assertEquals(valid().margin(), search.margin());
  }

  @Test
  @DisplayName("The form without a margin is bound by the absolute budget alone")
  void theFormWithoutAMarginIsBoundByTheBudgetAlone() {
    // An infinite margin is what "no relative cap" means arithmetically: min(budget, best + inf)
    // is the budget, so the solver needs no special case and the older callers keep their meaning.
    assertEquals(Double.POSITIVE_INFINITY, valid().margin());
  }

  @Test
  @DisplayName("A non-positive or NaN margin is refused, an infinite one is not")
  void theMarginMustBePositive() {
    assertThrows(OrbitlabException.class, () -> valid().withMargin(0.0));
    assertThrows(OrbitlabException.class, () -> valid().withMargin(-10.0));
    assertThrows(OrbitlabException.class, () -> valid().withMargin(Double.NaN));
    assertEquals(Double.POSITIVE_INFINITY, valid().withMargin(Double.POSITIVE_INFINITY).margin());
  }

  @Test
  @DisplayName("withMargin changes the margin and nothing else")
  void withMarginPreservesTheRest() {
    LaunchWindowSearch search = valid().withMargin(50.0);

    assertEquals(50.0, search.margin());
    assertEquals(valid().start(), search.start());
    assertEquals(valid().span(), search.span());
    assertEquals(valid().step(), search.step());
    assertEquals(valid().precision(), search.precision());
    assertEquals(valid().maxDeltaV(), search.maxDeltaV());
    assertEquals(valid().maxWindows(), search.maxWindows());
  }

  @Test
  @DisplayName("The horizon comes from the problem's recurrence, never from the caller")
  void theHorizonScalesWithTheProblemsRecurrence() {
    LaunchWindowSearch shortLived =
        LaunchWindowSearch.forOpportunities(
            AbsoluteDate.J2000_EPOCH,
            fakeProblem(Duration.ofMinutes(10), Duration.ofMinutes(1)),
            3,
            Double.POSITIVE_INFINITY,
            50.0);
    LaunchWindowSearch longLived =
        LaunchWindowSearch.forOpportunities(
            AbsoluteDate.J2000_EPOCH,
            fakeProblem(Duration.ofHours(10), Duration.ofHours(1)),
            3,
            Double.POSITIVE_INFINITY,
            50.0);

    // Three recurrences plus a twelfth: 3 * 600 + 50, and 3 * 36000 + 3000.
    assertEquals(Duration.ofSeconds(1850), shortLived.span());
    assertEquals(Duration.ofSeconds(111_000), longLived.span());
    assertEquals(Duration.ofMinutes(1), shortLived.step());
    assertEquals(Duration.ofSeconds(6), shortLived.precision());
    // One more than asked for, so the cost-ordered truncation cannot decide the chronology.
    assertEquals(4, shortLived.maxWindows());
  }

  @Test
  @DisplayName("Every opportunity the span holds is offered, the soonest included")
  void theSoonestOpportunitySurvivesTheCostOrderedCut() {
    Duration recurrence = Duration.ofHours(1);
    double phase = 60.0;
    // A periodic term places one optimum per hour; a slight downward drift makes each successive
    // optimum a little cheaper than the last, so a cost-ordered cut to three would drop the first.
    LaunchWindowProblem drifting =
        new LaunchWindowProblem() {
          @Override
          public String name() {
            return "drifting";
          }

          @Override
          public LaunchWindowCandidate evaluate(AbsoluteDate epoch) {
            double t = epoch.durationFrom(AbsoluteDate.J2000_EPOCH);
            double periodic = 1.0 - Math.cos(2.0 * Math.PI * (t - phase) / recurrence.toSeconds());
            return LaunchWindowCandidate.of(epoch, periodic + 0.1 - 0.001 * t / 3600.0);
          }

          @Override
          public Duration coarseStep() {
            return Duration.ofMinutes(1);
          }

          @Override
          public Duration recurrence() {
            return recurrence;
          }
        };

    LaunchWindowSearch search =
        LaunchWindowSearch.forOpportunities(
            AbsoluteDate.J2000_EPOCH, drifting, 3, Double.POSITIVE_INFINITY, 1.0);
    List<LaunchWindow> windows = new LaunchWindowSolver(drifting).solve(search);

    // 3 h 5 min of span over an hourly criterion phased at t = 60 s: four optima fit.
    assertEquals(4, windows.size());
    double soonest =
        windows.stream()
            .mapToDouble(window -> window.date().durationFrom(AbsoluteDate.J2000_EPOCH))
            .min()
            .orElseThrow();
    assertEquals(phase, soonest, 10.0);
  }
}
