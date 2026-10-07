package com.smousseur.orbitlab.ui.mission.wizard.step.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.ui.mission.wizard.step.planning.ZoomScale.CaptionSpan;
import com.smousseur.orbitlab.ui.mission.wizard.step.planning.ZoomScale.ZoomCaptions;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The zoom pane's scale — the part of {@link LaunchWindowTimeline} that can be exercised without an
 * initialised {@code AssetManager}.
 *
 * <p>The assertions are on <b>caption collisions</b> and not on the ratios the ladder was built
 * from. A ratio is an intermediate result; what the reader of the pane sees is whether {@code
 * opens} sits on top of the pane-start time, and that is what broke. Asserting the collision keeps
 * the test true if a label's width, the ladder or the gap is ever changed — the ratios would have
 * to be rewritten, the property would not.
 */
class ZoomScaleTest {

  /** The slot measured at 51.6&deg;, which the fixed &plusmn; 5 min pane already held. */
  private static final double SLOT_51_6_S = 232.0;

  /** The slot of a due-east launch from Kourou, i = 5.23&deg;: the configuration that broke. */
  private static final double SLOT_KOUROU_S = 1960.0;

  /**
   * What was left of a lunar slot once its optimum, 2026-10-10T00:42:39, had been picked and
   * written back as the floor: the slot then opens on its optimum and closes 3 h 11 min 31 s later.
   * The reported capture showed {@code opens} drawn over the optimum's caption.
   */
  private static final double REPORTED_REMAINDER_S = 11491.0;

  /** Where a bound's caption sits when it is centred on the pane, in the optimum's place. */
  private static final float CENTRED_BOUND_LEFT =
      (LaunchWindowTimeline.TRACK_W - ZoomScale.BOUND_LABEL_W) / 2f;

  @ParameterizedTest
  @ValueSource(
      doubles = {
        10.0,
        11.0,
        13.9,
        14.0,
        30.0,
        60.0,
        120.0,
        SLOT_51_6_S,
        240.0,
        375.0,
        600.0,
        900.0,
        SLOT_KOUROU_S,
        2400.0,
        3600.0,
        5400.0,
        7200.0,
        10800.0
      })
  @DisplayName("no two captions of the strip overlap, at any slot width")
  void captionsNeverOverlap(double slotSeconds) {
    assertNoOverlap(slotSeconds);
  }

  @Test
  @DisplayName("no two captions overlap anywhere between a ten-second and a three-hour slot")
  void captionsNeverOverlapAcrossTheSweep() {
    for (double slotSeconds = 10.0; slotSeconds <= 10800.0; slotSeconds += 1.0) {
      assertNoOverlap(slotSeconds);
    }
  }

  @Test
  @DisplayName("the reported Kourou slot of 32 min 40 s no longer collides")
  void kourouSlotFits() {
    assertNoOverlap(SLOT_KOUROU_S);
    assertEquals(1800.0, ZoomScale.halfSpanSeconds(SLOT_KOUROU_S));
  }

  @Test
  @DisplayName("the measured 51.6 degree case keeps the half-span it was drawn at")
  void measuredCaseKeepsItsScale() {
    assertEquals(300.0, ZoomScale.halfSpanSeconds(SLOT_51_6_S));
    assertEquals("+/- 5 min", ZoomScale.formatHalfSpan(ZoomScale.halfSpanSeconds(SLOT_51_6_S)));
  }

  @Test
  @DisplayName("every rung transition lands inside the two bounds")
  void ladderTransitionsStayInsideTheBounds() {
    double widest = ZoomScale.MAX_HALF_SPAN_RATIO / ZoomScale.MIN_HALF_SPAN_RATIO;
    double[] rungs = ZoomScale.rungs();
    for (int i = 0; i < rungs.length - 1; i++) {
      double step = rungs[i + 1] / rungs[i];
      assertTrue(
          step <= widest,
          "rung " + rungs[i] + " -> " + rungs[i + 1] + " steps by " + step + " > " + widest);
    }
  }

  @Test
  @DisplayName("an off-centre optimum keeps its far bound off the pane's edge")
  void offCentreOptimumKeepsItsFarBoundInside() {
    double halfSpan = ZoomScale.halfSpanSeconds(2.0 * 1000.0);
    ZoomCaptions captions = ZoomScale.captions(-10.0, 1000.0, halfSpan);
    assertClear("closes", captions.closes(), "pane end", captions.paneEnd());
  }

  @Test
  @DisplayName("a slot that opens on its optimum names that instant once")
  void aSlotOpeningOnItsOptimumNamesItOnce() {
    double halfSpan = ZoomScale.halfSpanSeconds(2.0 * REPORTED_REMAINDER_S);
    ZoomCaptions captions = ZoomScale.captions(0.0, REPORTED_REMAINDER_S, halfSpan);

    assertFalse(captions.hasOptimum(), "the opening is the optimum: one caption says both");
    assertEquals(CENTRED_BOUND_LEFT, captions.opens().left(), "opens takes the optimum's place");
    assertDrawnClear(captions);
  }

  @Test
  @DisplayName("a slot that closes on its optimum names that instant once")
  void aSlotClosingOnItsOptimumNamesItOnce() {
    double halfSpan = ZoomScale.halfSpanSeconds(2.0 * REPORTED_REMAINDER_S);
    ZoomCaptions captions = ZoomScale.captions(-REPORTED_REMAINDER_S, 0.0, halfSpan);

    assertFalse(captions.hasOptimum(), "the closing is the optimum: one caption says both");
    assertEquals(CENTRED_BOUND_LEFT, captions.closes().left(), "closes takes the optimum's place");
    assertDrawnClear(captions);
  }

  @Test
  @DisplayName("a leaning slot slides its near bound's caption clear of the optimum's")
  void aLeaningSlotSlidesItsNearBoundClear() {
    double halfSpan = ZoomScale.halfSpanSeconds(2.0 * REPORTED_REMAINDER_S);
    ZoomCaptions captions = ZoomScale.captions(-600.0, REPORTED_REMAINDER_S, halfSpan);

    assertTrue(captions.hasOptimum(), "ten minutes apart, the two instants keep a caption each");
    assertTrue(
        captions.opens().right() + LaunchWindowTimeline.CAPTION_CHAR_W <= captions.optimum().left(),
        "opens ends at "
            + captions.opens().right()
            + ", within a character of the optimum at "
            + captions.optimum().left());
    assertDrawnClear(captions);
  }

  @Test
  @DisplayName("no two drawn captions overlap however far the slot leans, either way")
  void captionsNeverOverlapHoweverTheSlotLeans() {
    for (double far : new double[] {SLOT_51_6_S / 2.0, SLOT_KOUROU_S / 2.0, REPORTED_REMAINDER_S}) {
      double halfSpan = ZoomScale.halfSpanSeconds(2.0 * far);
      for (double near = 0.0; near <= far; near += far / 500.0) {
        assertDrawnClear(ZoomScale.captions(-near, far, halfSpan));
        assertDrawnClear(ZoomScale.captions(-far, near, halfSpan));
      }
    }
  }

  @Test
  @DisplayName("a slot wider than the top rung is clamped rather than refused")
  void degenerateSlotTakesTheTopRung() {
    assertEquals(21600.0, ZoomScale.halfSpanSeconds(200_000.0));
  }

  @ParameterizedTest
  @ValueSource(doubles = {10.0, 30.0, 60.0, 300.0, 1800.0, 3600.0, 10800.0, 21600.0})
  @DisplayName("the note is ASCII and fits the width the heading gives it")
  void noteIsAsciiAndFits(double rung) {
    String note = ZoomScale.formatHalfSpan(rung);
    for (char c : note.toCharArray()) {
      assertTrue(c < 128, "non-ASCII character in " + note);
    }
    assertTrue(
        note.length() * LaunchWindowTimeline.CAPTION_CHAR_W <= LaunchWindowTimeline.SPAN_NOTE_W,
        note + " is wider than the note slot");
  }

  @Test
  @DisplayName("the rungs are named in the largest unit that divides them")
  void rungsAreNamedReadably() {
    assertEquals("+/- 30 s", ZoomScale.formatHalfSpan(30.0));
    assertEquals("+/- 1 min", ZoomScale.formatHalfSpan(60.0));
    assertEquals("+/- 30 min", ZoomScale.formatHalfSpan(1800.0));
    assertEquals("+/- 1 h", ZoomScale.formatHalfSpan(3600.0));
    assertEquals("+/- 6 h", ZoomScale.formatHalfSpan(21600.0));
  }

  private static void assertNoOverlap(double slotSeconds) {
    double halfSpan = ZoomScale.halfSpanSeconds(slotSeconds);
    assertOrdered(ZoomScale.captions(-slotSeconds / 2.0, slotSeconds / 2.0, halfSpan));
  }

  private static void assertOrdered(ZoomCaptions captions) {
    assertClear("pane start", captions.paneStart(), "opens", captions.opens());
    assertClear("opens", captions.opens(), "optimum", captions.optimum());
    assertClear("optimum", captions.optimum(), "closes", captions.closes());
    assertClear("closes", captions.closes(), "pane end", captions.paneEnd());
  }

  /** The captions the widget draws, left to right: the optimum's only when it has one. */
  private static void assertDrawnClear(ZoomCaptions captions) {
    List<String> names = new ArrayList<>(List.of("pane start", "opens"));
    List<CaptionSpan> spans = new ArrayList<>(List.of(captions.paneStart(), captions.opens()));
    if (captions.hasOptimum()) {
      names.add("optimum");
      spans.add(captions.optimum());
    }
    names.addAll(List.of("closes", "pane end"));
    spans.addAll(List.of(captions.closes(), captions.paneEnd()));
    for (int i = 0; i < spans.size() - 1; i++) {
      assertClear(names.get(i), spans.get(i), names.get(i + 1), spans.get(i + 1));
    }
  }

  private static void assertClear(
      String leftName, CaptionSpan left, String rightName, CaptionSpan right) {
    assertTrue(
        left.right() <= right.left(),
        leftName + " ends at " + left.right() + " but " + rightName + " starts at " + right.left());
  }
}
