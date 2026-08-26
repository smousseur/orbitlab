package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.engine.events.ScenarioBrowserMode;
import com.smousseur.orbitlab.simulation.mission.scenario.ScenarioStore;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure logic of the scenario browser: which row is selected, what name is typed, whether the
 * confirm button may be pressed and whether pressing it would overwrite something.
 *
 * <p>Knows nothing of Lemur or JME, so it is unit-tested outside the render loop the way {@link
 * AppMenuModel} is. That is the whole reason it exists: the window that draws it is placement and
 * skinning, and placement is not where a rule about overwriting a file should live.
 *
 * <p>The mode is fixed at construction. The browser is built and destroyed on each opening, and the
 * two menu entries are the only thing that decides which mode it opens in — so a mode that could
 * change under the model would be a state nothing ever puts to use.
 */
public final class ScenarioBrowserModel {

  /**
   * One line of the list: what the browser can say about a scenario without opening it.
   *
   * @param name the file name, without extension — also the identity of the row
   * @param savedAt when it was written, as the file carries it, or {@code null} if it says nothing
   * @param missionCount how many missions it holds
   */
  public record Entry(String name, String savedAt, int missionCount) {
    public Entry {
      Objects.requireNonNull(name, "name");
    }
  }

  private final ScenarioBrowserMode mode;
  private final List<Entry> entries;

  private String selectedName;
  private String typedName = "";

  /**
   * @param mode which mode the browser opened in
   * @param entries the scenarios on disk, in the order they are listed
   */
  public ScenarioBrowserModel(ScenarioBrowserMode mode, List<Entry> entries) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
  }

  public ScenarioBrowserMode mode() {
    return mode;
  }

  public List<Entry> entries() {
    return entries;
  }

  /**
   * @return {@code true} when the folder holds nothing to list
   */
  public boolean isEmpty() {
    return entries.isEmpty();
  }

  /**
   * @return the selected row, or empty when nothing is selected
   */
  public Optional<String> selectedName() {
    return Optional.ofNullable(selectedName);
  }

  /**
   * Selects a row, or clears the selection when the row is already selected.
   *
   * <p>In {@link ScenarioBrowserMode#SAVE} the click also fills the name field, which is what makes
   * overwriting an existing scenario one gesture rather than a re-typing exercise. The field stays
   * editable afterwards: the row is a suggestion, not a lock.
   *
   * @param name the row clicked, or {@code null} to clear
   */
  public void select(String name) {
    if (name == null || name.equals(selectedName)) {
      selectedName = null;
      return;
    }
    if (!contains(name)) {
      return;
    }
    selectedName = name;
    if (mode == ScenarioBrowserMode.SAVE) {
      typedName = name;
    }
  }

  /**
   * @return what the name field holds; always {@code ""} rather than {@code null}
   */
  public String typedName() {
    return typedName;
  }

  /**
   * Records what the user typed. A name that stops matching the selected row drops the selection,
   * so the highlighted line never contradicts the field above it.
   *
   * @param typedName the field's content, {@code null} read as empty
   */
  public void setTypedName(String typedName) {
    this.typedName = typedName == null ? "" : typedName;
    if (selectedName != null && !selectedName.equals(this.typedName)) {
      selectedName = null;
    }
  }

  /**
   * The name the confirm button would act on: the typed one when saving, the selected row when
   * opening.
   *
   * @return the target name, or empty when there is nothing to act on
   */
  public Optional<String> targetName() {
    return mode == ScenarioBrowserMode.SAVE
        ? Optional.of(typedName).filter(ScenarioStore::isValidName)
        : selectedName();
  }

  /**
   * Whether the confirm button may be pressed.
   *
   * <p>Opening needs a row; saving needs a name the store will accept. The predicate is {@link
   * ScenarioStore#isValidName(String)} itself rather than a copy of it — a button that offers what
   * the store then refuses is worse than a greyed-out one.
   *
   * @return {@code true} when confirming would do something
   */
  public boolean isConfirmEnabled() {
    return targetName().isPresent();
  }

  /**
   * Whether confirming would overwrite a scenario already on disk — the question the save mode puts
   * behind a confirmation.
   *
   * @return {@code true} in save mode when the target name is already listed
   */
  public boolean wouldOverwrite() {
    return mode == ScenarioBrowserMode.SAVE && targetName().filter(this::contains).isPresent();
  }

  private boolean contains(String name) {
    return entries.stream().anyMatch(entry -> entry.name().equals(name));
  }
}
