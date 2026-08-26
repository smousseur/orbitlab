package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.ui.menu.AppMenuItem;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure logic of the top-left application menu: open state, check marks and per-entry availability.
 * Knows nothing of Lemur or JME so it can be unit-tested outside the render loop, the way {@link
 * MissionDisplayPanelRules} is for the display panel.
 *
 * <p>The model does not own the truth behind a check mark — the widget the entry commands does. The
 * owning app state pushes that state in through {@link #setChecked(String, boolean)}; the model
 * only remembers it so the view has a single place to read from.
 */
public final class AppMenuModel {

  private final Map<String, AppMenuItem> itemsById = new LinkedHashMap<>();
  private final List<AppMenuItem> items;
  private final Set<String> checked = new HashSet<>();
  private final Set<String> disabled = new HashSet<>();

  private boolean open;

  /**
   * @param items the entries, in display order; ids must be unique
   */
  public AppMenuModel(List<AppMenuItem> items) {
    Objects.requireNonNull(items, "items");
    for (AppMenuItem item : items) {
      if (itemsById.put(item.id(), item) != null) {
        throw new IllegalArgumentException("Duplicate menu item id: " + item.id());
      }
    }
    this.items = List.copyOf(items);
  }

  /** The entries, in display order. */
  public List<AppMenuItem> items() {
    return items;
  }

  public boolean isOpen() {
    return open;
  }

  /** Opens a closed menu, closes an open one. */
  public void toggle() {
    open = !open;
  }

  /** Closes the menu; a no-op when it is already closed. */
  public void close() {
    open = false;
  }

  public boolean isChecked(String itemId) {
    return checked.contains(itemId);
  }

  /**
   * Mirrors into the model the state of the surface a toggle entry commands.
   *
   * @throws IllegalArgumentException if {@code itemId} is unknown or is not a toggle entry
   */
  public void setChecked(String itemId, boolean value) {
    AppMenuItem item = requireItem(itemId);
    if (!item.isToggle()) {
      throw new IllegalArgumentException("Not a toggle menu item: " + itemId);
    }
    if (value) {
      checked.add(itemId);
    } else {
      checked.remove(itemId);
    }
  }

  public boolean isEnabled(String itemId) {
    return itemsById.containsKey(itemId) && !disabled.contains(itemId);
  }

  /**
   * Enables or disables an entry. A disabled entry is shown greyed out and swallows its click
   * without closing the menu.
   *
   * @throws IllegalArgumentException if {@code itemId} is unknown
   */
  public void setEnabled(String itemId, boolean value) {
    requireItem(itemId);
    if (value) {
      disabled.remove(itemId);
    } else {
      disabled.add(itemId);
    }
  }

  /**
   * Records a click on an entry.
   *
   * @param itemId the clicked entry
   * @return the id of the entry to run, closing the menu; empty — and the menu left open — when the
   *     entry is unknown or disabled
   */
  public Optional<String> select(String itemId) {
    if (!isEnabled(itemId)) {
      return Optional.empty();
    }
    close();
    return Optional.of(itemId);
  }

  private AppMenuItem requireItem(String itemId) {
    AppMenuItem item = itemsById.get(itemId);
    if (item == null) {
      throw new IllegalArgumentException("Unknown menu item: " + itemId);
    }
    return item;
  }
}
