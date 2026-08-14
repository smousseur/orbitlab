package com.smousseur.orbitlab.ui.menu;

import java.util.Objects;

/**
 * One entry of the top-left application menu.
 *
 * <p>The icon is mandatory: every entry sits in the same icon gutter so the labels stay aligned
 * whatever a later entry adds (see {@code docs/menu/01-menu-applicatif.md} §3). {@code iconName} is
 * a texture path relative to {@code interface/}, without the {@code .png} extension — for instance
 * {@code "missions/icon-action-view"}.
 *
 * @param id stable identifier the menu reports back to its owner on selection
 * @param label text shown in the entry, in English like the rest of the UI
 * @param iconName icon texture path relative to {@code interface/}, without extension
 * @param kind whether the entry runs an action or reflects a boolean state with a check mark
 * @param separatorBefore whether a 1-pixel rule is drawn above the entry
 */
public record AppMenuItem(
    String id, String label, String iconName, Kind kind, boolean separatorBefore) {

  /** Entry behaviour: a plain action, or a toggle carrying a check mark. */
  public enum Kind {
    ACTION,
    TOGGLE
  }

  public AppMenuItem {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(iconName, "iconName");
    Objects.requireNonNull(kind, "kind");
  }

  /** Builds an action entry with no separator above it. */
  public static AppMenuItem action(String id, String label, String iconName) {
    return new AppMenuItem(id, label, iconName, Kind.ACTION, false);
  }

  /** Builds a toggle entry (check mark) with no separator above it. */
  public static AppMenuItem toggle(String id, String label, String iconName) {
    return new AppMenuItem(id, label, iconName, Kind.TOGGLE, false);
  }

  /** Returns the same entry, preceded by a separator. */
  public AppMenuItem withSeparatorBefore() {
    return new AppMenuItem(id, label, iconName, kind, true);
  }

  public boolean isToggle() {
    return kind == Kind.TOGGLE;
  }
}
