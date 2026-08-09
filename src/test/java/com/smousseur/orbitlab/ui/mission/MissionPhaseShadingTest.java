package com.smousseur.orbitlab.ui.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.simulation.mission.ephemeris.PhaseRun;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The phase shading rule (spec {@code specs/graphics-effects/mission-phase-encoding.md} §5.2).
 *
 * <p>The load-bearing property is the last one: modulating a mission's own colour must never bring
 * it close enough to another mission's to make the two traces confusable. The palette identifies
 * missions; the phase encoding is only allowed to borrow room that identification does not need.
 */
class MissionPhaseShadingTest {

  /**
   * Two adjacent phases must differ by at least this much in RGB, or the encoding says nothing where
   * it is read most — along the ascent, which is a run of consecutive burns.
   *
   * <p>This is the guarantee the first version of this class lacked. It ranked only the coasts, so a
   * LEO — which has exactly one real coast — came out in two flat tones and the whole ascent in one
   * of them.
   */
  private static final float MIN_ADJACENT_CONTRAST = 0.10f;

  /**
   * Floor between two different missions at the same position in their sequences. Deliberately far
   * below {@link #MIN_ADJACENT_CONTRAST}: muting is a contraction toward a shared anchor, so
   * legibility within a mission is bought with separation between missions. What protects identity
   * is not this number but {@link #theFinalRunKeepsTheFullPaletteSeparation()}.
   */
  private static final float MIN_CROSS_MISSION_SEPARATION = 0.05f;

  private static final ColorRGBA MISSION = MissionColorPalette.PALETTE.get(0);

  /** Runs of ten vertices each — long enough to be drawable, so every one takes a rank. */
  private static List<PhaseRun> runs(boolean... propulsive) {
    List<PhaseRun> out = new ArrayList<>();
    for (int i = 0; i < propulsive.length; i++) {
      out.add(new PhaseRun("S" + i, propulsive[i], i * 10, 10));
    }
    return out;
  }

  private static float distance(ColorRGBA a, ColorRGBA b) {
    float dr = a.r - b.r;
    float dg = a.g - b.g;
    float db = a.b - b.b;
    return (float) Math.sqrt(dr * dr + dg * dg + db * db);
  }

  private static float luminance(ColorRGBA c) {
    return 0.2126f * c.r + 0.7152f * c.g + 0.0722f * c.b;
  }

  @Test
  void theLastRunIsExactlyTheMissionColour() {
    ColorRGBA[] shades = MissionPhaseShading.shade(MISSION, runs(true, false, true, false));

    ColorRGBA last = shades[3];
    assertEquals(MISSION.r, last.r, 0f, "the dominant segment must carry the palette colour");
    assertEquals(MISSION.g, last.g, 0f);
    assertEquals(MISSION.b, last.b, 0f);
  }

  @Test
  void aSingleRunMissionKeepsTheMissionColour() {
    ColorRGBA[] shades = MissionPhaseShading.shade(MISSION, runs(false));

    assertEquals(MISSION.r, shades[0].r, 0f);
    assertEquals(MISSION.g, shades[0].g, 0f);
    assertEquals(MISSION.b, shades[0].b, 0f);
  }

  /**
   * The guarantee the first version lacked, on the profile that exposed it: a LEO ascent, which is
   * consecutive burns with a single coast at the end.
   */
  @Test
  void adjacentPhasesAlwaysDifferOnALeoAscent() {
    ColorRGBA[] shades =
        MissionPhaseShading.shade(MISSION, runs(true, true, true, true, true, false));

    for (int i = 1; i < shades.length; i++) {
      float d = distance(shades[i - 1], shades[i]);
      int index = i;
      assertTrue(
          d >= MIN_ADJACENT_CONTRAST,
          () -> "phases " + (index - 1) + " and " + index + " are only " + d + " apart");
    }
  }

  /** And on a GEO, where a length-dependent ramp would have shrunk the step to nothing. */
  @Test
  void adjacentPhasesAlwaysDifferOnALongGeoSequence() {
    ColorRGBA[] shades =
        MissionPhaseShading.shade(
            MISSION,
            runs(true, true, true, false, true, true, false, true, true, true, false));

    for (int i = 1; i < shades.length; i++) {
      float d = distance(shades[i - 1], shades[i]);
      int index = i;
      assertTrue(
          d >= MIN_ADJACENT_CONTRAST,
          () -> "phases " + (index - 1) + " and " + index + " are only " + d + " apart");
    }
  }

  /** An instantaneous separation must not shift every phase after it by one step. */
  @Test
  void aRunTooShortToDrawDoesNotConsumeARank() {
    List<PhaseRun> withSeparation =
        List.of(
            new PhaseRun("Ascent", true, 0, 10),
            new PhaseRun("Separation", false, 10, 1),
            new PhaseRun("Gravity turn", true, 11, 10),
            new PhaseRun("Coasting", false, 21, 10));
    List<PhaseRun> without = runs(true, true, false);

    ColorRGBA[] a = MissionPhaseShading.shade(MISSION, withSeparation);
    ColorRGBA[] b = MissionPhaseShading.shade(MISSION, without);

    assertEquals(b[0].r, a[0].r, 1e-6f, "the ascent must be shaded the same either way");
    assertEquals(b[1].r, a[2].r, 1e-6f, "so must the gravity turn that follows the separation");
    assertEquals(b[2].r, a[3].r, 1e-6f, "and the final coast");
  }

  /** At equal rank the propulsive run is the brighter one. */
  @Test
  void aBurnReadsHotterThanACoastAtTheSameRank() {
    // Nine runs: index 0 and index 4 sit at the same point of the muting cycle.
    ColorRGBA[] shades =
        MissionPhaseShading.shade(
            MISSION, runs(false, false, false, false, true, false, false, false, false));

    assertTrue(
        luminance(shades[4]) > luminance(shades[0]),
        "the burn must read hotter than the coast sharing its rank");
  }

  /** Identity is carried by the dominant segment, and that one is never modulated. */
  @Test
  void theFinalRunKeepsTheFullPaletteSeparation() {
    List<PhaseRun> profile = runs(true, false, true, false, true, false);
    int n = MissionColorPalette.PALETTE.size();

    for (int i = 0; i < n; i++) {
      for (int j = i + 1; j < n; j++) {
        ColorRGBA[] a = MissionPhaseShading.shade(MissionColorPalette.PALETTE.get(i), profile);
        ColorRGBA[] b = MissionPhaseShading.shade(MissionColorPalette.PALETTE.get(j), profile);
        float d = distance(a[a.length - 1], b[b.length - 1]);
        int mi = i;
        int mj = j;
        assertTrue(
            d >= 0.2f,
            () -> "final runs of missions " + mi + " and " + mj + " are only " + d + " apart");
      }
    }
  }

  /**
   * Anti-confusion, at the comparison that is actually made on screen: the same position in two
   * different missions' sequences. Deliberately weaker than the previous all-pairs form, which was
   * what forced the muting down to the point of illegibility.
   */
  @Test
  void missionsStaySeparatedAtEqualRank() {
    List<PhaseRun> profile = runs(true, false, true, false, true, false);
    int n = MissionColorPalette.PALETTE.size();

    for (int i = 0; i < n; i++) {
      for (int j = i + 1; j < n; j++) {
        ColorRGBA[] a = MissionPhaseShading.shade(MissionColorPalette.PALETTE.get(i), profile);
        ColorRGBA[] b = MissionPhaseShading.shade(MissionColorPalette.PALETTE.get(j), profile);
        for (int r = 0; r < a.length; r++) {
          float d = distance(a[r], b[r]);
          int mi = i;
          int mj = j;
          int rr = r;
          assertTrue(
              d >= MIN_CROSS_MISSION_SEPARATION,
              () ->
                  "missions " + mi + " and " + mj + " at run " + rr + " are only " + d + " apart");
        }
      }
    }
  }

  @Test
  void mutingStaysVisibleAgainstTheMissionColour() {
    ColorRGBA[] shades = MissionPhaseShading.shade(MISSION, runs(false, false, false));

    assertTrue(
        distance(shades[0], MISSION) > 0.2f,
        "the earliest phase must be visibly more muted than the final orbit");
  }
}
