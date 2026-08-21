package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.ui.menu.AppMenuItem;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure logic of the top-left application menu. No Lemur, no JME: the model is
 * exercised directly, the way {@link MissionDisplayPanelRulesTest} does for the display panel.
 */
class AppMenuModelTest {

  private static final String PANEL = "missionPanel";
  private static final String MANAGE = "manageMissions";
  private static final String NEW = "newMission";
  private static final String OPEN_SCENARIO = "openScenario";
  private static final String SAVE_SCENARIO = "saveScenario";
  private static final String QUIT = "quit";

  /** An id the menu will never carry, for the cases that exercise an unknown entry. */
  private static final String UNKNOWN = "noSuchEntry";

  private static AppMenuModel newModel() {
    return new AppMenuModel(
        List.of(
            AppMenuItem.toggle(PANEL, "Mission panel", "missions/icon-action-view"),
            AppMenuItem.toggle(MANAGE, "Mission management", "missions/icon-action-manage"),
            AppMenuItem.action(NEW, "New mission...", "wizard/icon-plus").withSeparatorBefore(),
            AppMenuItem.action(OPEN_SCENARIO, "Open scenario...", "scenario/icon-open")
                .withSeparatorBefore(),
            AppMenuItem.action(SAVE_SCENARIO, "Save scenario...", "scenario/icon-save"),
            AppMenuItem.action(QUIT, "Quit", "wizard/icon-close-red").withSeparatorBefore()));
  }

  @Test
  void menuStartsClosedAndToggleFlipsIt() {
    AppMenuModel model = newModel();

    assertFalse(model.isOpen());
    model.toggle();
    assertTrue(model.isOpen());
    model.toggle();
    assertFalse(model.isOpen());
  }

  @Test
  void closeOnAnAlreadyClosedMenuIsANoOp() {
    AppMenuModel model = newModel();

    model.close();
    assertFalse(model.isOpen());

    model.toggle();
    model.close();
    model.close();
    assertFalse(model.isOpen());
  }

  @Test
  void selectingAnEnabledEntryClosesTheMenuAndReturnsItsId() {
    AppMenuModel model = newModel();
    model.toggle();

    assertEquals(Optional.of(MANAGE), model.select(MANAGE));
    assertFalse(model.isOpen());
  }

  @Test
  void selectingADisabledEntryReturnsNothingAndLeavesTheMenuOpen() {
    AppMenuModel model = newModel();
    model.setEnabled(NEW, false);
    model.toggle();

    assertEquals(Optional.empty(), model.select(NEW));
    assertTrue(model.isOpen());
  }

  @Test
  void selectingAnUnknownEntryReturnsNothingAndLeavesTheMenuOpen() {
    AppMenuModel model = newModel();
    model.toggle();

    assertEquals(Optional.empty(), model.select(UNKNOWN));
    assertTrue(model.isOpen());
  }

  @Test
  void everyEntryIsEnabledUntilDisabled() {
    AppMenuModel model = newModel();

    assertTrue(model.isEnabled(PANEL));
    assertTrue(model.isEnabled(MANAGE));
    assertTrue(model.isEnabled(NEW));
    assertFalse(model.isEnabled(UNKNOWN));

    model.setEnabled(NEW, false);
    assertFalse(model.isEnabled(NEW));
    model.setEnabled(NEW, true);
    assertTrue(model.isEnabled(NEW));
  }

  @Test
  void theMissionPanelCheckFollowsTheStatePushedIn() {
    AppMenuModel model = newModel();

    assertFalse(model.isChecked(PANEL));
    model.setChecked(PANEL, true);
    assertTrue(model.isChecked(PANEL));
    model.setChecked(PANEL, false);
    assertFalse(model.isChecked(PANEL));
  }

  @Test
  void onlyToggleEntriesCarryACheck() {
    AppMenuModel model = newModel();

    assertThrows(IllegalArgumentException.class, () -> model.setChecked(NEW, true));
    assertThrows(IllegalArgumentException.class, () -> model.setChecked(UNKNOWN, true));
    assertFalse(model.isChecked(NEW));
  }

  @Test
  void itemsKeepTheirDeclarationOrder() {
    AppMenuModel model = newModel();

    assertEquals(
        List.of(PANEL, MANAGE, NEW, OPEN_SCENARIO, SAVE_SCENARIO, QUIT),
        model.items().stream().map(AppMenuItem::id).toList());
  }

  @Test
  void quitIsAPlainActionThatClosesTheMenu() {
    AppMenuModel model = newModel();
    model.toggle();

    assertEquals(Optional.of(QUIT), model.select(QUIT));
    assertFalse(model.isOpen());
    assertThrows(IllegalArgumentException.class, () -> model.setChecked(QUIT, true));
  }

  @Test
  void bothSurfaceEntriesCarryTheirOwnCheck() {
    AppMenuModel model = newModel();

    model.setChecked(PANEL, true);
    model.setChecked(MANAGE, true);
    assertTrue(model.isChecked(PANEL));
    assertTrue(model.isChecked(MANAGE));

    model.setChecked(PANEL, false);
    assertFalse(model.isChecked(PANEL));
    assertTrue(model.isChecked(MANAGE));
  }

  /**
   * The two scenario entries are plain actions: they open a window that is built and destroyed on
   * each use, so there is no lasting state for a check mark to reflect — unlike the panel and the
   * management window above them.
   */
  @Test
  void theTwoScenarioEntriesAreCheckLessActions() {
    AppMenuModel model = newModel();
    model.toggle();

    assertEquals(Optional.of(OPEN_SCENARIO), model.select(OPEN_SCENARIO));
    assertFalse(model.isOpen());

    model.toggle();
    assertEquals(Optional.of(SAVE_SCENARIO), model.select(SAVE_SCENARIO));
    assertFalse(model.isOpen());

    assertThrows(IllegalArgumentException.class, () -> model.setChecked(OPEN_SCENARIO, true));
    assertThrows(IllegalArgumentException.class, () -> model.setChecked(SAVE_SCENARIO, true));
  }

  /** Both are separated from the mission entries above and from Quit below. */
  @Test
  void theScenarioBlockStandsOnItsOwn() {
    AppMenuModel model = newModel();
    List<AppMenuItem> items = model.items();

    assertTrue(items.get(3).separatorBefore(), "a rule opens the scenario block");
    assertFalse(items.get(4).separatorBefore(), "the two entries stay together");
    assertTrue(items.get(5).separatorBefore(), "and Quit stays apart");
  }

  @Test
  void duplicateIdsAreRejected() {
    List<AppMenuItem> duplicated =
        List.of(
            AppMenuItem.action(MANAGE, "Manage missions...", "missions/icon-action-manage"),
            AppMenuItem.action(MANAGE, "Manage again...", "missions/icon-action-manage"));

    assertThrows(IllegalArgumentException.class, () -> new AppMenuModel(duplicated));
  }
}
