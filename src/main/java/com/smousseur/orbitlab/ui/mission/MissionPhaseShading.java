package com.smousseur.orbitlab.ui.mission;

import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.simulation.mission.ephemeris.PhaseRun;
import java.util.List;
import java.util.Objects;

/**
 * Turns a mission's phase runs into per-run colours, all derived from that mission's single palette
 * entry (spec {@code specs/graphics-effects/mission-phase-encoding.md} §5.2).
 *
 * <p><b>The modulation only ever climbs toward the palette colour.</b> The final run — the longest
 * segment of any mission, around two thirds of the drawn line — is exactly {@link
 * MissionColorPalette}'s colour; earlier runs are muted. A mission therefore always ends up looking
 * like its palette entry, and it is the dominant segment that does it, which is what keeps two
 * missions on screen distinguishable while their phases are being read.
 *
 * <p><b>Every run is ranked, not just the coasts.</b> An earlier version ranked only the coasts and
 * gave every propulsive run one shared shade. On a LEO that produced exactly two colours in the
 * whole flight — because only {@code CoastingStage} and {@code StageSeparationStage} are
 * non-propulsive, so a LEO has a single real coast — and the ascent, which is the part of the
 * flight one actually watches, was four or five consecutive burns in one flat tone. Ranking every
 * run is what makes two consecutive phases differ at all.
 *
 * <p><b>A fixed step counted backwards from the final orbit.</b> Not a ramp spread over the run
 * count: that makes the step shrink as a mission gains phases, so a GEO with eleven runs would
 * dissolve into invisibility precisely because it has more to say. Counting back from the end in
 * fixed increments gives a contrast between neighbours that does not depend on the mission's
 * length, and keeps the last run on the palette colour exactly. The levels are walked as a triangle
 * (see {@link #MUTING_LEVELS}), so two runs six apart share a tone; they are far apart on screen,
 * and that is the price of a guarantee that adjacent ones never do.
 *
 * <p><b>Rank, not name.</b> There is no {@code stageName → colour} table to maintain. Stage names
 * are free-form per-mission strings; a table keyed on them would need extending for every new
 * mission type and would fail silently on a typo.
 *
 * <p><b>What this costs between missions.</b> Muting is a contraction toward a shared anchor, so it
 * brings every mission closer to every other one; a wider step buys legibility within a mission and
 * spends separation between them. The trade taken here is deliberate: the <em>final</em> run of any
 * two missions stays at the palette's own full separation, while their most-muted runs — short,
 * early segments read in the context of a whole trace — may come as close as 0.05.
 */
public final class MissionPhaseShading {

  /** Dark neutral the muted runs interpolate toward. */
  private static final ColorRGBA ANCHOR_MUTED = new ColorRGBA(0.12f, 0.13f, 0.16f, 1f);

  /**
   * Muting added per step back from the final orbit. Sized so two adjacent runs differ by ~0.16 in
   * RGB even when both are burns and the whitening contracts the gap.
   */
  private static final float MUTING_STEP = 0.22f;

  /**
   * Muting levels, {@code 0 … MUTING_LEVELS − 1}, bounding how dark an early phase can get.
   *
   * <p>Walked as a <b>triangle</b> — up to the darkest then back down — rather than a sawtooth that
   * wraps. Both guarantee that adjacent runs differ by exactly one step, but a sawtooth also puts a
   * jump from the nominal colour straight to the darkest one in the middle of the sequence: on a
   * real LEO that lands between the two gravity turns, and it reads as a rendering fault rather
   * than as information.
   */
  private static final int MUTING_LEVELS = 4;

  /**
   * How far a propulsive run is lifted toward white on top of its rank. Kept modest: the lift
   * contracts the rank difference between two adjacent burns by {@code 1 − BURN_LIFT}, which is the
   * ascent's worst case.
   */
  private static final float BURN_LIFT = 0.20f;

  private MissionPhaseShading() {}

  /**
   * One colour per run, in the same order as {@code runs}.
   *
   * @param missionColor the mission's palette entry
   * @param runs the mission's phase runs, in flight order
   * @return the per-run colours; the array is freshly allocated and owned by the caller
   */
  public static ColorRGBA[] shade(ColorRGBA missionColor, List<PhaseRun> runs) {
    Objects.requireNonNull(missionColor, "missionColor");
    Objects.requireNonNull(runs, "runs");

    int drawable = 0;
    for (PhaseRun run : runs) {
      if (run.isDrawable()) {
        drawable++;
      }
    }

    ColorRGBA[] shades = new ColorRGBA[runs.size()];
    int rank = 0;
    for (int i = 0; i < runs.size(); i++) {
      PhaseRun run = runs.get(i);
      // Counted backwards: the last drawable run sits at 0 and therefore on the palette colour.
      int fromEnd = Math.max(0, drawable - 1 - rank);
      float weight = MUTING_STEP * triangleLevel(fromEnd);
      ColorRGBA base = lerp(missionColor, ANCHOR_MUTED, weight);
      shades[i] = run.propulsive() ? lerp(base, ColorRGBA.White, BURN_LIFT) : base;
      // A run too short to draw a segment takes the tone of its neighbourhood without consuming a
      // rank — an instantaneous separation must not shift every phase after it by one step.
      if (run.isDrawable()) {
        rank++;
      }
    }
    return shades;
  }

  /**
   * Triangle wave over {@code 0 … MUTING_LEVELS − 1}: 0, 1, 2, 3, 2, 1, 0, 1, … Consecutive inputs
   * always map to levels one apart, which is the adjacency guarantee, and the walk never jumps.
   */
  private static int triangleLevel(int fromEnd) {
    int period = 2 * (MUTING_LEVELS - 1);
    int phase = fromEnd % period;
    return phase < MUTING_LEVELS ? phase : period - phase;
  }

  private static ColorRGBA lerp(ColorRGBA from, ColorRGBA to, float t) {
    return new ColorRGBA(
        from.r + (to.r - from.r) * t,
        from.g + (to.g - from.g) * t,
        from.b + (to.b - from.b) * t,
        1f);
  }
}
