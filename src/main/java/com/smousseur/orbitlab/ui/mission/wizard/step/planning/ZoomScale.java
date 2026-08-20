package com.smousseur.orbitlab.ui.mission.wizard.step.planning;

/**
 * The scale of the zoom pane of {@link LaunchWindowTimeline}, and where the captions that read it
 * land on the track.
 *
 * <p><b>Why the scale cannot be a constant.</b> The slot the pane shows is {@code 2 * margin /
 * slope} with {@code slope = v * sin(i) * omega_earth}, so it grows as {@code 1 / sin i}: 3 min 48 s
 * at the 51.6&deg; that was measured, 6 min 15 s at 28.5&deg;, and 32 min 40 s for a due-east launch
 * from Kourou — which is the wizard's own default site. A fixed &plusmn; 5 min pane held the first
 * and was overrun by the third, and an overrun is not a cosmetic loss: the fraction is clamped to
 * [0, 1] and the caption's left edge to the track, so {@code opens} lands exactly on the pane-start
 * time and {@code closes} on the pane-end time. The half-span therefore has to come from the slot.
 *
 * <p><b>Why a ladder and not a multiple of the slot.</b> A pane sized to, say, 1.3 times the slot
 * every time would draw the same picture whatever the inclination, and the slot's width
 * <em>relative to the frame</em> would stop carrying any information. With rungs, a slot filling a
 * &plusmn; 1 min frame and one filling a &plusmn; 30 min frame read differently at a glance, and the
 * frame keeps saying something the three numbers of the readout do not.
 *
 * <p><b>The two bounds, and where they come from.</b> Both are collisions between captions of the
 * graduation strip, and both are symmetric, so stating them for {@code opens} covers {@code closes}.
 * Write {@code r} for the slot's half-width divided by the half-span, so the opening sits at
 * fraction {@code (1 - r) / 2} of the track.
 *
 * <ul>
 *   <li><b>Lower bound.</b> The {@code opens} caption is {@link #BOUND_LABEL_W} wide and centred on
 *       that fraction; its left edge must clear the pane-start caption, {@link #TIME_LABEL_W} wide
 *       at x = 0, by {@link #CAPTION_GAP}. That caps {@code r}, hence forces the half-span to be at
 *       least {@link #MIN_HALF_SPAN_RATIO} times the slot's half-width — 1.452 on the constants in
 *       force.
 *   <li><b>Upper bound.</b> The same caption's right edge must clear the optimum's caption, {@link
 *       #TIME_LABEL_W} wide and centred on the track, by the same gap. That floors {@code r}, hence
 *       forbids the half-span from exceeding {@link #MAX_HALF_SPAN_RATIO} times the half-width —
 *       4.322.
 * </ul>
 *
 * <p>The window between the two is a factor of 2.977, so a ladder whose consecutive rungs differ by
 * at most 2.5 always lands inside it when the smallest rung meeting the lower bound is picked. That
 * is the invariant {@code ZoomScaleTest} checks, alongside the collision-freedom the ratios exist to
 * produce.
 *
 * <p>Extracted from the widget so that property can be exercised at all: a Lemur container cannot be
 * built without an initialised {@code AssetManager}, and the arithmetic below is the whole of the
 * decision. The same split as {@code RaanEntry} and {@code RefusedPage}. The widget lays its
 * captions out from the spans this class returns rather than recomputing them, so the test drives
 * the code that draws.
 */
final class ZoomScale {

  /** The 8 characters of {@code HH:mm:ss}, plus air. */
  static final float TIME_LABEL_W = 10 * LaunchWindowTimeline.CAPTION_CHAR_W;

  /** The 15 characters of {@code closes HH:mm:ss}, plus air. */
  static final float BOUND_LABEL_W = 17 * LaunchWindowTimeline.CAPTION_CHAR_W;

  /** The track the strip is laid out over, and the frame the zoom pane fills. */
  private static final float TRACK_W = LaunchWindowTimeline.TRACK_W;

  /**
   * Clearance kept between two captions of the strip: one character of the face they are drawn in,
   * the narrowest gap that still reads as a gap on a monospaced font.
   */
  private static final float CAPTION_GAP = LaunchWindowTimeline.CAPTION_CHAR_W;

  /** Smallest half-span, in half-slots, that keeps {@code opens} clear of the pane-start caption. */
  static final double MIN_HALF_SPAN_RATIO =
      1.0 / (1.0 - 2.0 * (TIME_LABEL_W + CAPTION_GAP + BOUND_LABEL_W / 2f) / TRACK_W);

  /** Largest half-span, in half-slots, that keeps {@code opens} clear of the optimum's caption. */
  static final double MAX_HALF_SPAN_RATIO =
      TRACK_W / (TIME_LABEL_W + 2.0 * CAPTION_GAP + BOUND_LABEL_W);

  /**
   * The rungs the pane may be drawn at, in seconds: &plusmn; 10 s through &plusmn; 6 h, on readable
   * subdivisions of the second, the minute and the hour rather than on a pure 1-2-5 decade — a
   * &plusmn; 30 min pane is a scale a reader has a name for, a &plusmn; 500 s one is not.
   *
   * <p>The top rungs are not decoration. The slot degenerates as the inclination approaches the
   * launch site's latitude, where {@code sin i} stops growing the slope: it is about 2 h 51 min at
   * i = 1&deg;, and unbounded at the equator. The bottom rungs are reached by no Earth criterion —
   * the slot bottoms out near 182 s at a polar launch — but they cost nothing and they keep the
   * class honest for a criterion with a sharper slope.
   */
  private static final double[] RUNGS_S = {
    10.0, 20.0, 30.0, 60.0, 120.0, 300.0, 600.0, 1200.0, 1800.0, 3600.0, 7200.0, 10800.0, 21600.0
  };

  private ZoomScale() {}

  /**
   * @return the ladder, for the test that checks its transitions against the two bounds
   */
  static double[] rungs() {
    return RUNGS_S.clone();
  }

  /**
   * The smallest rung wide enough to show the slot without a caption collision.
   *
   * <p>A slot too wide for the top rung takes the top rung and is drawn clamped. That is a
   * near-equatorial degeneracy the criterion itself barely constrains, and the readout's figures
   * still tell the truth about it; refusing to draw would say less.
   *
   * @param slotSeconds the width the pane has to show, twice the greater distance from the optimum
   *     to a bound of the slot
   * @return the half-span of the pane, in seconds
   */
  static double halfSpanSeconds(double slotSeconds) {
    double needed = Math.max(slotSeconds, 0.0) / 2.0 * MIN_HALF_SPAN_RATIO;
    for (double rung : RUNGS_S) {
      if (rung >= needed) {
        return rung;
      }
    }
    return RUNGS_S[RUNGS_S.length - 1];
  }

  /**
   * The note drawn beside the {@code SELECTED WINDOW} heading.
   *
   * <p>ASCII only: the bundled {@code ibmplexmono-*} faces stop at glyph id 127, and a missing glyph
   * draws nothing and reports nothing, so {@code +/-} stands in for the sign.
   *
   * @param halfSpanSeconds a rung, as returned by {@link #halfSpanSeconds(double)}
   * @return the rung named in the largest unit it divides exactly
   */
  static String formatHalfSpan(double halfSpanSeconds) {
    long seconds = Math.round(halfSpanSeconds);
    if (seconds < 60L) {
      return "+/- " + seconds + " s";
    }
    if (seconds < 3600L) {
      return "+/- " + seconds / 60L + " min";
    }
    return "+/- " + seconds / 3600L + " h";
  }

  /**
   * @param offsetSeconds an instant, as a signed distance from the optimum
   * @param halfSpanSeconds the half-span of the pane
   * @return where that instant falls across the pane, 0 at its left edge and 1 at its right
   */
  static double fraction(double offsetSeconds, double halfSpanSeconds) {
    return (offsetSeconds + halfSpanSeconds) / (2.0 * halfSpanSeconds);
  }

  /**
   * Where the five captions of the graduation strip sit.
   *
   * <p>The two offsets are taken separately rather than as one width because the optimum is the
   * cheapest candidate of the slot and not its midpoint, so the two halves need not be equal.
   *
   * @param openOffsetSeconds the opening, as a signed distance from the optimum (negative)
   * @param closeOffsetSeconds the closing, as a signed distance from the optimum (positive)
   * @param halfSpanSeconds the half-span of the pane
   * @return the spans, left to right
   */
  static ZoomCaptions captions(
      double openOffsetSeconds, double closeOffsetSeconds, double halfSpanSeconds) {
    return new ZoomCaptions(
        new CaptionSpan(0f, TIME_LABEL_W),
        centred(BOUND_LABEL_W, fraction(openOffsetSeconds, halfSpanSeconds)),
        centred(TIME_LABEL_W, 0.5),
        centred(BOUND_LABEL_W, fraction(closeOffsetSeconds, halfSpanSeconds)),
        new CaptionSpan(TRACK_W - TIME_LABEL_W, TIME_LABEL_W));
  }

  private static CaptionSpan centred(float width, double fraction) {
    double bounded = Double.isNaN(fraction) ? 0.0 : Math.max(0.0, Math.min(1.0, fraction));
    float left = (float) (TRACK_W * bounded) - width / 2f;
    return new CaptionSpan(Math.max(0f, Math.min(TRACK_W - width, left)), width);
  }

  /**
   * A caption's horizontal footprint on the track.
   *
   * @param left distance from the track's left edge
   * @param width the caption's own width, which is what it is clipped to
   */
  record CaptionSpan(float left, float width) {

    /**
     * @return the first x the caption no longer covers
     */
    float right() {
      return left + width;
    }
  }

  /**
   * The graduation strip of the zoom pane, in drawing order.
   *
   * @param paneStart the instant at the pane's left edge
   * @param opens the slot's opening
   * @param optimum the cheapest instant of the slot, always at the centre
   * @param closes the slot's closing
   * @param paneEnd the instant at the pane's right edge
   */
  record ZoomCaptions(
      CaptionSpan paneStart,
      CaptionSpan opens,
      CaptionSpan optimum,
      CaptionSpan closes,
      CaptionSpan paneEnd) {}
}
