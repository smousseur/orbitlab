package com.smousseur.orbitlab.ui.mission.wizard;

import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.Styles;
import com.smousseur.orbitlab.ui.UiKit;

public final class MissionWizardStyles {

  public static final String STYLE = "mission-wizard";

  // =================================================================
  //  WIZARD LAYOUT CONSTANTS
  // =================================================================

  /** Inner content width: WINDOW_WIDTH (880) - 2 * content horizontal inset (32). */
  public static final float WIZARD_CONTENT_WIDTH = 816f;

  /** Inner content height: WINDOW_HEIGHT (660) - HEADER_HEIGHT (120) - FOOTER_HEIGHT (72). */
  public static final float WIZARD_CONTENT_HEIGHT = 468f;

  // =================================================================
  //  WIZARD COLOUR PALETTE (from wizard.zip design tokens)
  // =================================================================
  /** blue-bright (#38bdf8). */
  public static final ColorRGBA WIZARD_ACCENT_BRIGHT = new ColorRGBA(0.220f, 0.741f, 0.973f, 1.0f);

  /** cyan (#22d3ee). */
  public static final ColorRGBA WIZARD_CYAN = new ColorRGBA(0.133f, 0.827f, 0.933f, 1.0f);

  // --- Borders ---
  /** border (#1a3a5c). */
  public static final ColorRGBA WIZARD_BORDER = new ColorRGBA(0.102f, 0.227f, 0.361f, 0.70f);

  // --- Text ---
  /** text-hi (#e2f0ff). */
  public static final ColorRGBA WIZARD_TEXT_PRIMARY = new ColorRGBA(0.886f, 0.941f, 1.0f, 1.0f);

  /** text-mid (#7eb5d6). */
  public static final ColorRGBA WIZARD_TEXT_SECONDARY = new ColorRGBA(0.494f, 0.710f, 0.839f, 1.0f);

  /** text-lo (#3d6585). */
  public static final ColorRGBA WIZARD_TEXT_LO = new ColorRGBA(0.239f, 0.396f, 0.522f, 1.0f);

  // --- Semantic ---
  /** success (#10b981). */
  public static final ColorRGBA WIZARD_SUCCESS = new ColorRGBA(0.063f, 0.725f, 0.506f, 1.0f);

  /** warning (#f59e0b). */
  public static final ColorRGBA WIZARD_WARNING = new ColorRGBA(0.961f, 0.620f, 0.043f, 1.0f);

  /** danger (#ef4444). */
  public static final ColorRGBA WIZARD_DANGER = new ColorRGBA(0.937f, 0.267f, 0.267f, 1.0f);

  // --- Backdrop ---
  public static final ColorRGBA WIZARD_BACKDROP = new ColorRGBA(0f, 0f, 0f, 0.60f);

  private MissionWizardStyles() {}

  // =================================================================
  //  BACKGROUND FACTORIES (v2 texture atlas)
  // =================================================================

  /** 9-slice shell background (34×34 texture, 16-pixel corners). */
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

  /** 9-slice input / textfield background (18×18 texture, 8-pixel corners). */
  public static TbtQuadBackgroundComponent inputBg() {
    return UiKit.wizardBg9("input", 8);
  }

  /** 9-slice input background with accent focus ring. */
  public static TbtQuadBackgroundComponent inputFocusBg() {
    return UiKit.wizardBg9("input-focus", 8);
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
    l.set("color", WIZARD_TEXT_PRIMARY);
    l.set("font", UiKit.sora(13));

    Attributes b = styles.getSelector("button", STYLE);
    b.set("background", UiKit.wizardBg9("btn-ghost", 8));
    b.set("color", WIZARD_TEXT_PRIMARY);
    b.set("font", UiKit.sora(13));
    b.set("insets", new Insets3f(10, 22, 10, 22));

    Attributes tf = styles.getSelector("textField", STYLE);
    tf.set("background", inputBg());
    tf.set("color", WIZARD_TEXT_PRIMARY);
    tf.set("font", UiKit.ibmPlexMono(11));
    tf.set("insets", new Insets3f(8, 12, 8, 12));

    Attributes s = styles.getSelector("slider", STYLE);
    s.set("background", UiKit.wizardFlat("slider-track"));

    Attributes sThumb = styles.getSelector("slider.thumb.button", STYLE);
    sThumb.set("text", "");
    sThumb.set("background", UiKit.wizardFlat("slider-thumb"));
    sThumb.set("color", WIZARD_TEXT_PRIMARY);
    sThumb.set("font", UiKit.sora(1));
    sThumb.set("insets", new Insets3f(0, 0, 0, 0));
  }
}
