package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.engine.events.ScenarioBrowserMode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The scenario browser's rules, exercised without Lemur or JME the way {@link AppMenuModelTest}
 * does for the menu. Everything the window draws about selection, validity and overwriting is
 * decided here, which is why none of it needs a render loop to be checked.
 */
class ScenarioBrowserModelTest {

  private static final ScenarioBrowserModel.Entry ALPHA =
      new ScenarioBrowserModel.Entry("alpha", "2026-08-21T14:32:10Z", 3);
  private static final ScenarioBrowserModel.Entry BRAVO =
      new ScenarioBrowserModel.Entry("bravo", "2026-08-20T09:00:00Z", 1);

  private static ScenarioBrowserModel model(ScenarioBrowserMode mode) {
    return new ScenarioBrowserModel(mode, List.of(ALPHA, BRAVO));
  }

  // --- selection -----------------------------------------------------------

  @Test
  void selectingARowHighlightsIt() {
    ScenarioBrowserModel model = model(ScenarioBrowserMode.OPEN);

    model.select("bravo");

    assertEquals("bravo", model.selectedName().orElseThrow());
  }

  @Test
  void selectingTheSameRowTwiceClearsTheSelection() {
    ScenarioBrowserModel model = model(ScenarioBrowserMode.OPEN);

    model.select("alpha");
    model.select("alpha");

    assertTrue(model.selectedName().isEmpty());
  }

  /** A row that is not listed cannot be selected: the window would have nothing to highlight. */
  @Test
  void selectingAnUnlistedRowChangesNothing() {
    ScenarioBrowserModel model = model(ScenarioBrowserMode.OPEN);
    model.select("alpha");

    model.select("charlie");

    assertEquals("alpha", model.selectedName().orElseThrow());
  }

  // --- open mode -----------------------------------------------------------

  @Test
  void openModeNeedsASelection() {
    ScenarioBrowserModel model = model(ScenarioBrowserMode.OPEN);

    assertFalse(model.isConfirmEnabled(), "nothing selected, nothing to open");
    model.select("alpha");
    assertTrue(model.isConfirmEnabled());
    assertEquals("alpha", model.targetName().orElseThrow());
  }

  /** Opening never overwrites anything, whatever is selected. */
  @Test
  void openModeNeverOverwrites() {
    ScenarioBrowserModel model = model(ScenarioBrowserMode.OPEN);
    model.select("alpha");

    assertFalse(model.wouldOverwrite());
  }

  /** In open mode a click is a selection and nothing else — there is no field to fill. */
  @Test
  void openModeLeavesTheNameAlone() {
    ScenarioBrowserModel model = model(ScenarioBrowserMode.OPEN);

    model.select("alpha");

    assertEquals("", model.typedName());
  }

  // --- save mode -----------------------------------------------------------

  @Test
  void saveModeNeedsAName() {
    ScenarioBrowserModel model = model(ScenarioBrowserMode.SAVE);

    assertFalse(model.isConfirmEnabled(), "an empty name saves nothing");
    model.setTypedName("charlie");
    assertTrue(model.isConfirmEnabled());
    assertEquals("charlie", model.targetName().orElseThrow());
  }

  /**
   * The predicate is the store's own. A button offering what the store then refuses is worse than a
   * greyed-out one.
   */
  @Test
  void saveModeRefusesANameTheStoreWouldRefuse() {
    ScenarioBrowserModel model = model(ScenarioBrowserMode.SAVE);

    for (String name : List.of("../escape", "with/slash", "dots.in.name", "  ", "")) {
      model.setTypedName(name);
      assertFalse(model.isConfirmEnabled(), "name: " + name);
      assertTrue(model.targetName().isEmpty(), "name: " + name);
    }

    model.setTypedName("LEO 400 - run_2");
    assertTrue(model.isConfirmEnabled(), "letters, digits, spaces, dash and underscore are fine");
  }

  @Test
  void saveModeReportsAnOverwrite() {
    ScenarioBrowserModel model = model(ScenarioBrowserMode.SAVE);

    model.setTypedName("charlie");
    assertFalse(model.wouldOverwrite(), "a fresh name overwrites nothing");

    model.setTypedName("alpha");
    assertTrue(model.wouldOverwrite());
  }

  /** Clicking a row is how overwriting is made one gesture rather than a re-typing exercise. */
  @Test
  void saveModeFillsTheNameFromTheClickedRow() {
    ScenarioBrowserModel model = model(ScenarioBrowserMode.SAVE);

    model.select("bravo");

    assertEquals("bravo", model.typedName());
    assertEquals("bravo", model.targetName().orElseThrow());
    assertTrue(model.wouldOverwrite());
  }

  /** The highlighted row must never contradict the field above it. */
  @Test
  void typingAwayFromTheSelectedRowDropsTheSelection() {
    ScenarioBrowserModel model = model(ScenarioBrowserMode.SAVE);
    model.select("alpha");

    model.setTypedName("alpha 2");

    assertTrue(model.selectedName().isEmpty());
    assertEquals("alpha 2", model.typedName());
    assertFalse(model.wouldOverwrite());
  }

  @Test
  void aNullNameReadsAsEmptyRatherThanAsAbsent() {
    ScenarioBrowserModel model = model(ScenarioBrowserMode.SAVE);

    model.setTypedName(null);

    assertEquals("", model.typedName());
    assertFalse(model.isConfirmEnabled());
  }

  // --- listing -------------------------------------------------------------

  @Test
  void anEmptyFolderHasNothingToConfirm() {
    ScenarioBrowserModel open = new ScenarioBrowserModel(ScenarioBrowserMode.OPEN, List.of());

    assertTrue(open.isEmpty());
    assertFalse(open.isConfirmEnabled());

    ScenarioBrowserModel save = new ScenarioBrowserModel(ScenarioBrowserMode.SAVE, List.of());
    save.setTypedName("first");

    assertTrue(save.isEmpty());
    assertTrue(save.isConfirmEnabled(), "the first save has no list to pick from");
    assertFalse(save.wouldOverwrite());
  }
}
