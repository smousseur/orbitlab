package com.smousseur.orbitlab.ui.mission;

import com.jme3.math.ColorRGBA;
import java.util.Collection;
import java.util.List;

/**
 * Cyclic palette of 8 distinct trajectory colors used to identify missions across the Display
 * Panel, the management modal and the 3D scene. {@link ColorRGBA} does not provide a stable
 * componentwise {@code equals}, so reference- and value-based comparisons go through
 * {@link #containsColor}.
 */
public final class MissionColorPalette {

  public static final List<ColorRGBA> PALETTE =
      List.of(
          new ColorRGBA(0.30f, 0.65f, 0.90f, 1f),
          new ColorRGBA(0.85f, 0.35f, 0.75f, 1f),
          new ColorRGBA(0.55f, 0.85f, 0.30f, 1f),
          new ColorRGBA(0.95f, 0.60f, 0.25f, 1f),
          new ColorRGBA(0.95f, 0.85f, 0.30f, 1f),
          new ColorRGBA(0.65f, 0.45f, 0.95f, 1f),
          new ColorRGBA(0.95f, 0.55f, 0.55f, 1f),
          new ColorRGBA(0.25f, 0.80f, 0.75f, 1f));

  private static int recycleCursor = 0;

  private MissionColorPalette() {}

  /**
   * Returns the first palette color not present in {@code inUse}; if all eight are already taken,
   * recycles in round-robin order from the start of the palette.
   *
   * @param inUse colors currently assigned to other missions (may contain nulls, which are ignored)
   * @return a palette color
   */
  public static synchronized ColorRGBA pickFree(Collection<ColorRGBA> inUse) {
    for (ColorRGBA c : PALETTE) {
      if (!containsColor(inUse, c)) return c;
    }
    ColorRGBA c = PALETTE.get(recycleCursor % PALETTE.size());
    recycleCursor++;
    return c;
  }

  private static boolean containsColor(Collection<ColorRGBA> list, ColorRGBA c) {
    for (ColorRGBA x : list) {
      if (x == null) continue;
      if (x.r == c.r && x.g == c.g && x.b == c.b && x.a == c.a) return true;
    }
    return false;
  }
}
