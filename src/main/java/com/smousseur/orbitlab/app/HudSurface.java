package com.smousseur.orbitlab.app;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * One surface that {@code ESC} can send away: an open dropdown, a window, a modal, a dialog.
 *
 * <p>The record holds no state of its own. Whether the surface is on screen is asked of the
 * component that owns it, so the registry can never disagree with what the user sees.
 *
 * @param name identifier used in logs and tests
 * @param layer depth of the surface, taken from {@code UiLayers} — it is also its dismissal
 *     priority, so the surface sent away is the one drawn in front
 * @param openCheck tells whether the surface is currently on screen
 * @param dismissAction sends the surface away; run only when {@code openCheck} holds
 */
public record HudSurface(
    String name, float layer, BooleanSupplier openCheck, Runnable dismissAction) {

  /** Name of the mission management window's surface, read by the menu to draw its check mark. */
  public static final String MISSION_MANAGEMENT = "missionManagement";

  /** Name of the application menu's dropdown. Registered and read by its own app state only. */
  public static final String APP_MENU = "appMenu";

  /** Name of the quit confirmation. Registered and read by its own app state only. */
  public static final String QUIT_DIALOG = "quitDialog";

  /**
   * Name of the mission deletion confirmation, opened from the management window. Registered and
   * read by the window itself.
   */
  public static final String DELETE_DIALOG = "deleteDialog";

  /**
   * Name of the mission creation wizard. Registered and read by its own app state only; being a
   * modal, it stands between the management window and any dialog opened on top of it.
   */
  public static final String MISSION_WIZARD = "missionWizard";

  /**
   * Name of the wizard's discard confirmation, opened when the wizard is dismissed. Registered and
   * read by the wizard's own app state only.
   */
  public static final String DISCARD_DIALOG = "discardDialog";

  public HudSurface {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(openCheck, "openCheck");
    Objects.requireNonNull(dismissAction, "dismissAction");
  }

  public boolean isOpen() {
    return openCheck.getAsBoolean();
  }

  public void dismiss() {
    dismissAction.run();
  }
}
