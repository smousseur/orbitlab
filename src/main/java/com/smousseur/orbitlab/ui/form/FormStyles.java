package com.smousseur.orbitlab.ui.form;

import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.core.GuiComponent;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.Styles;
import com.smousseur.orbitlab.ui.UiKit;

/**
 * Shared Lemur styles, colour palette and background factories for form-style modal windows (used
 * by the mission wizard and the mission panel).
 */
public final class FormStyles {

  public static final String STYLE = "form";

  // =================================================================
  //  FORM LAYOUT CONSTANTS
  // =================================================================

  /** Inner content width: WINDOW_WIDTH (880) - 2 * content horizontal inset (32). */
  public static final float CONTENT_WIDTH = 816f;

  /** Inner content height: WINDOW_HEIGHT (660) - HEADER_HEIGHT (120) - FOOTER_HEIGHT (72). */
  public static final float CONTENT_HEIGHT = 468f;

  /**
   * Horizontal inset of a form button. Public because it is also where such a button's skin
   * <i>starts</i>: the {@code form} style draws backgrounds inside the insets, so the visible left
   * edge of the application menu's chip stands this far in from the element's own origin. The
   * breadcrumb reads it to line its segments up with that edge without measuring pixels.
   */
  public static final float BUTTON_INSET_X = 22f;

  /** Vertical inset of a form button. */
  public static final float BUTTON_INSET_Y = 10f;

  // =================================================================
  //  FORM COLOUR PALETTE
  // =================================================================
  /** blue-bright (#38bdf8). */
  public static final ColorRGBA ACCENT_BRIGHT = new ColorRGBA(0.220f, 0.741f, 0.973f, 1.0f);

  /** cyan (#22d3ee). */
  public static final ColorRGBA CYAN = new ColorRGBA(0.133f, 0.827f, 0.933f, 1.0f);

  /** border (#1a3a5c). */
  public static final ColorRGBA BORDER = new ColorRGBA(0.102f, 0.227f, 0.361f, 0.70f);

  /** text-hi (#e2f0ff). */
  public static final ColorRGBA TEXT_PRIMARY = new ColorRGBA(0.886f, 0.941f, 1.0f, 1.0f);

  /** text-mid (#7eb5d6). */
  public static final ColorRGBA TEXT_SECONDARY = new ColorRGBA(0.494f, 0.710f, 0.839f, 1.0f);

  /** text-lo (#3d6585). */
  public static final ColorRGBA TEXT_LO = new ColorRGBA(0.239f, 0.396f, 0.522f, 1.0f);

  /** success (#10b981). */
  public static final ColorRGBA SUCCESS = new ColorRGBA(0.063f, 0.725f, 0.506f, 1.0f);

  /** warning (#f59e0b). */
  public static final ColorRGBA WARNING = new ColorRGBA(0.961f, 0.620f, 0.043f, 1.0f);

  /** danger (#ef4444). */
  public static final ColorRGBA DANGER = new ColorRGBA(0.937f, 0.267f, 0.267f, 1.0f);

  /** Modal backdrop tint (black @ 60% alpha). */
  public static final ColorRGBA BACKDROP = new ColorRGBA(0f, 0f, 0f, 0.60f);

  private FormStyles() {}

  // =================================================================
  //  BACKGROUND FACTORIES (wizard texture atlas)
  // =================================================================

  /** 9-slice shell background (34x34 texture, 16-pixel corners). */
  public static TbtQuadBackgroundComponent shellBg() {
    return UiKit.wizardBg9("wizard-shell", 16);
  }

  /** Flat header strip. */
  public static QuadBackgroundComponent headerBg() {
    return UiKit.wizardFlat("header-bg");
  }

  /** Flat footer strip. */
  public static QuadBackgroundComponent footerBg() {
    return UiKit.wizardFlat("footer-bg");
  }

  /** 9-slice input / textfield background (18x18 texture, 8-pixel corners). */
  public static TbtQuadBackgroundComponent inputBg() {
    return UiKit.wizardBg9("input", 8);
  }

  /** 9-slice input background with accent focus ring. */
  public static TbtQuadBackgroundComponent inputFocusBg() {
    return UiKit.wizardBg9("input-focus", 8);
  }

  /**
   * Drops the default margin of a modal shell background so the window's own insets are the only
   * padding. No-op on background types that carry no margin.
   */
  public static void clearMargin(GuiComponent background) {
    if (background instanceof TbtQuadBackgroundComponent quad) {
      quad.setMargin(0f, 0f);
    } else if (background instanceof QuadBackgroundComponent quad) {
      quad.setMargin(0f, 0f);
    }
  }

  // =================================================================
  //  STYLE REGISTRATION
  // =================================================================

  public static void init(AssetManager assetManager) {
    Styles styles = GuiGlobals.getInstance().getStyles();

    Attributes c = styles.getSelector("container", STYLE);
    c.set("background", null);
    c.set("insets", new Insets3f(0, 0, 0, 0));

    Attributes l = styles.getSelector("label", STYLE);
    l.set("color", TEXT_PRIMARY);
    l.set("font", UiKit.sora(13));

    Attributes b = styles.getSelector("button", STYLE);
    b.set("background", UiKit.wizardBg9("btn-ghost", 8));
    b.set("color", TEXT_PRIMARY);
    b.set("font", UiKit.sora(13));
    b.set("insets", new Insets3f(BUTTON_INSET_Y, BUTTON_INSET_X, BUTTON_INSET_Y, BUTTON_INSET_X));

    // Top-left application menu. The title button needs nothing of its own: it is a plain
    // Button(FormStyles.STYLE) and takes btn-ghost, Sora 13 and TEXT_PRIMARY from the selector
    // above. Only the dropdown and its entries are a look the form style did not already carry —
    // "s'il lui faut une autre allure, c'est un sélecteur de plus", never an override at
    // construction (docs/menu/01-menu-applicatif.md §6.1).
    // Title chip: the shape of any form button — same insets, same font, same text colour, all
    // inherited from the "button" selector above — except that the HUD's one permanently visible
    // entry point is never a ghost. It wears the hover skin at rest, and answers the mouse with its
    // label alone: a chip that appeared and disappeared under the cursor would read as a widget
    // that comes and goes.
    Attributes menuTitle = styles.getSelector("menu.title.button", STYLE);
    menuTitle.set("background", UiKit.wizardBg9("btn-ghost-hover", 8));
    menuTitle.set("highlightColor", ACCENT_BRIGHT);

    Attributes menu = styles.getSelector("menu", STYLE);
    // The shell's 9-slice carries a 16-pixel margin of its own, which would push the entries that
    // far in from both edges and leave a selection floating in the middle of the dropdown. The
    // padding around the list is the selector's insets, and nothing else.
    TbtQuadBackgroundComponent menuBg = shellBg();
    clearMargin(menuBg);
    menu.set("background", menuBg);
    menu.set("insets", new Insets3f(8, 6, 8, 6));

    Attributes menuItem = styles.getSelector("menu.item", STYLE);
    // The hover skin is laid at rest too, fully transparent. An entry with no background has no
    // geometry, and a row with no geometry cannot be picked: the mouse would only find the letters
    // of its label, so the highlight would come and go over the text and nowhere else.
    TbtQuadBackgroundComponent menuItemBg = UiKit.textureBg("row-hover", 8);
    menuItemBg.setMargin(0f, 0f);
    menuItemBg.setColor(new ColorRGBA(1f, 1f, 1f, 0f));
    menuItem.set("background", menuItemBg);
    menuItem.set("color", TEXT_SECONDARY);
    menuItem.set("font", UiKit.sora(12));
    menuItem.set("insets", new Insets3f(7, 12, 7, 12));

    Attributes tf = styles.getSelector("textField", STYLE);
    tf.set("background", inputBg());
    tf.set("color", TEXT_PRIMARY);
    tf.set("font", UiKit.ibmPlexMono(11));
    tf.set("insets", new Insets3f(8, 12, 8, 12));

    Attributes s = styles.getSelector("slider", STYLE);
    s.set("background", UiKit.wizardFlat("slider-track"));

    Attributes sThumb = styles.getSelector("slider.thumb.button", STYLE);
    sThumb.set("text", "");
    sThumb.set("background", UiKit.wizardFlat("slider-thumb"));
    sThumb.set("color", TEXT_PRIMARY);
    sThumb.set("font", UiKit.sora(1));
    sThumb.set("insets", new Insets3f(0, 0, 0, 0));
  }
}
