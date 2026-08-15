package com.smousseur.orbitlab.ui;

/**
 * Depth scale of the GUI bucket, and the single place that holds it.
 *
 * <p>Every GUI surface shares one depth space. {@code GuiGraph} attaches its nodes to one frame and
 * translates none of them, so a node's position in the graph decides nothing: the bucket sorts on
 * world {@code z}, in rendering as in picking, and the higher {@code z} wins both.
 *
 * <p>The scale also orders dismissal. {@code HudSurfaces} ranks registered surfaces by their layer,
 * so the surface {@code ESC} sends away is by construction the one drawn in front. One ordering,
 * two uses — see {@code docs/ui/01-surfaces-et-modalite.md} §6.1 and §6.2.
 */
public final class UiLayers {

  /**
   * Permanent HUD: breadcrumb band, timeline, telemetry, planet billboards. The time capsule stacks
   * its own components up to a local {@code z} of 5; the mission timeline posed above it goes to 10
   * (its hover tooltip), so that tooltip ties with {@link #PANEL} in world {@code z}.
   *
   * <p>The tie is never observable: the mission display panel is anchored top-left under the
   * application menu, while the mission timeline is a 600 px band at bottom-centre, so the two
   * surfaces do not overlap. Should a future HUD surface reach into that band, it is this scale —
   * not the widget's internal stack — that has to be revisited.
   */
  public static final float HUD = 0f;

  /** Anchored, toggleable panel: the mission display panel. */
  public static final float PANEL = 10f;

  /** Non-modal draggable window: the mission management window. */
  public static final float WINDOW = 20f;

  /**
   * Full-screen click catcher of the open application menu. Deliberately below the title button, so
   * clicking the title while the menu is open toggles it instead of being swallowed.
   */
  public static final float MENU_CATCHER = 40f;

  /** The menu's title button, permanently on screen. */
  public static final float MENU_TITLE = 50f;

  /** The menu's dropdown. */
  public static final float MENU_DROPDOWN = 60f;

  /** Backdrop of a modal surface, and the surface itself one unit in front. */
  public static final float MODAL_BACKDROP = 100f;

  public static final float MODAL = 101f;

  /** A blocking dialog shields whatever modal opened it, hence a second pair above. */
  public static final float DIALOG_BACKDROP = 200f;

  public static final float DIALOG = 201f;

  private UiLayers() {}
}
