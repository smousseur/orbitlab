package com.smousseur.orbitlab.ui.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jme3.math.ColorRGBA;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MissionColorPaletteTest {

  @Test
  void picksFirstPaletteColorWhenNoneInUse() {
    ColorRGBA picked = MissionColorPalette.pickFree(List.of());
    assertSame(MissionColorPalette.PALETTE.get(0), picked);
  }

  @Test
  void skipsColorsAlreadyInUse() {
    ColorRGBA first = MissionColorPalette.PALETTE.get(0);
    ColorRGBA second = MissionColorPalette.PALETTE.get(1);

    ColorRGBA picked = MissionColorPalette.pickFree(List.of(first));
    assertSame(second, picked, "pickFree should skip the in-use color and return the next one");

    ColorRGBA pickedAfterTwo = MissionColorPalette.pickFree(List.of(first, second));
    assertSame(MissionColorPalette.PALETTE.get(2), pickedAfterTwo);
  }

  @Test
  void usesComponentwiseEqualityToDetectInUseColors() {
    // A fresh ColorRGBA instance with the same components must still be detected as used.
    ColorRGBA cloneOfFirst =
        new ColorRGBA(
            MissionColorPalette.PALETTE.get(0).r,
            MissionColorPalette.PALETTE.get(0).g,
            MissionColorPalette.PALETTE.get(0).b,
            MissionColorPalette.PALETTE.get(0).a);
    ColorRGBA picked = MissionColorPalette.pickFree(List.of(cloneOfFirst));
    assertSame(MissionColorPalette.PALETTE.get(1), picked);
  }

  @Test
  void ignoresNullEntriesInInUseCollection() {
    List<ColorRGBA> in = new ArrayList<>();
    in.add(null);
    in.add(MissionColorPalette.PALETTE.get(0));
    ColorRGBA picked = MissionColorPalette.pickFree(in);
    assertSame(MissionColorPalette.PALETTE.get(1), picked);
  }

  @Test
  void recyclesRoundRobinWhenAllColorsInUse() {
    List<ColorRGBA> all = new ArrayList<>(MissionColorPalette.PALETTE);

    // 3 successive picks when palette is saturated — each one must be a palette color and the
    // cursor must advance monotonically (no infinite loop, no stale identity).
    Set<ColorRGBA> paletteSet = new HashSet<>(MissionColorPalette.PALETTE);
    ColorRGBA a = MissionColorPalette.pickFree(all);
    ColorRGBA b = MissionColorPalette.pickFree(all);
    ColorRGBA c = MissionColorPalette.pickFree(all);

    assertNotNull(a);
    assertNotNull(b);
    assertNotNull(c);
    // Each pick must be one of the palette colors.
    org.junit.jupiter.api.Assertions.assertTrue(paletteSet.contains(a));
    org.junit.jupiter.api.Assertions.assertTrue(paletteSet.contains(b));
    org.junit.jupiter.api.Assertions.assertTrue(paletteSet.contains(c));
    // Round-robin advances: 3 consecutive picks at indices i, i+1, i+2 (mod 8).
    int ia = MissionColorPalette.PALETTE.indexOf(a);
    int ib = MissionColorPalette.PALETTE.indexOf(b);
    int ic = MissionColorPalette.PALETTE.indexOf(c);
    assertEquals((ia + 1) % MissionColorPalette.PALETTE.size(), ib);
    assertEquals((ia + 2) % MissionColorPalette.PALETTE.size(), ic);
  }
}
