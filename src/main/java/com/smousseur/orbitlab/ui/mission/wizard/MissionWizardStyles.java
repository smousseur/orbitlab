package com.smousseur.orbitlab.ui.mission.wizard;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.font.BitmapFont;
import com.jme3.math.ColorRGBA;
import com.jme3.texture.Texture;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.Styles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MissionWizardStyles {
  private static final Logger logger = LogManager.getLogger(MissionWizardStyles.class);

  public static final String STYLE = "mission-wizard";

  // =================================================================
  //  WIZARD COLOUR PALETTE
  // =================================================================

  // --- Backgrounds ---
  public static final ColorRGBA WIZARD_BG_DEEP =
      new ColorRGBA(0.043f, 0.078f, 0.125f, 0.95f);
  public static final ColorRGBA WIZARD_BG_CARD =
      new ColorRGBA(0.059f, 0.118f, 0.180f, 0.85f);
  public static final ColorRGBA WIZARD_BG_CARD_HOVER =
      new ColorRGBA(0.078f, 0.157f, 0.220f, 0.90f);
  public static final ColorRGBA WIZARD_SELECTED =
      new ColorRGBA(0.051f, 0.227f, 0.361f, 0.90f);

  // --- Accents ---
  public static final ColorRGBA WIZARD_ACCENT =
      new ColorRGBA(0.00f, 0.831f, 1.00f, 1.0f);
  public static final ColorRGBA WIZARD_ACCENT_DIM =
      new ColorRGBA(0.102f, 0.561f, 0.686f, 0.80f);

  // --- Borders ---
  public static final ColorRGBA WIZARD_BORDER =
      new ColorRGBA(0.110f, 0.204f, 0.282f, 0.70f);
  public static final ColorRGBA WIZARD_BORDER_GLOW =
      new ColorRGBA(0.00f, 0.659f, 0.80f, 0.60f);

  // --- Text ---
  public static final ColorRGBA WIZARD_TEXT_PRIMARY =
      new ColorRGBA(0.91f, 0.957f, 0.973f, 1.0f);
  public static final ColorRGBA WIZARD_TEXT_SECONDARY =
      new ColorRGBA(0.353f, 0.545f, 0.627f, 1.0f);
  public static final ColorRGBA WIZARD_TEXT_ACCENT = WIZARD_ACCENT;
  public static final ColorRGBA WIZARD_TEXT_DISABLED =
      new ColorRGBA(0.180f, 0.290f, 0.361f, 0.60f);

  // --- Semantic ---
  public static final ColorRGBA WIZARD_SUCCESS =
      new ColorRGBA(0.00f, 0.910f, 0.471f, 1.0f);
  public static final ColorRGBA WIZARD_WARNING =
      new ColorRGBA(1.00f, 0.722f, 0.188f, 1.0f);
  public static final ColorRGBA WIZARD_DANGER =
      new ColorRGBA(1.00f, 0.302f, 0.416f, 1.0f);
  public static final ColorRGBA WIZARD_INFO = WIZARD_ACCENT;

  // --- Backdrop ---
  public static final ColorRGBA WIZARD_BACKDROP =
      new ColorRGBA(0f, 0f, 0f, 0.60f);

  // =================================================================
  //  FONT HELPERS
  // =================================================================

  private static final String GRADIENT_TEXTURE =
      "com/simsilica/lemur/icons/bordered-gradient.png";
  private static final String FONT_RAJDHANI = "fonts/rajdhani-semibold-%d.fnt";
  private static final String FONT_MONO = "fonts/share-tech-mono-%d.fnt";

  private static Texture gradientTex;
  private static AssetManager assetManager;

  private MissionWizardStyles() {}

  public static BitmapFont rajdhani(int size) {
    return loadFontSafe(String.format(FONT_RAJDHANI, size));
  }

  public static BitmapFont mono(int size) {
    return loadFontSafe(String.format(FONT_MONO, size));
  }

  private static BitmapFont loadFontSafe(String path) {
    try {
      return assetManager.loadFont(path);
    } catch (AssetNotFoundException e) {
      logger.debug("Font not found: {}, using Lemur default", path);
      return GuiGlobals.getInstance().loadFont("Interface/Fonts/Default.fnt");
    }
  }

  // =================================================================
  //  GRADIENT BACKGROUND FACTORY
  // =================================================================

  public static TbtQuadBackgroundComponent createGradient(ColorRGBA color) {
    TbtQuadBackgroundComponent bg =
        TbtQuadBackgroundComponent.create(gradientTex, 1, 1, 1, 126, 126, 1f, false);
    bg.setColor(color);
    return bg;
  }

  // =================================================================
  //  STYLE REGISTRATION
  // =================================================================

  public static void init(AssetManager am) {
    assetManager = am;
    gradientTex = am.loadTexture(GRADIENT_TEXTURE);

    Styles styles = GuiGlobals.getInstance().getStyles();
    styles.applyStyles(STYLE, "glass");

    Attributes c = styles.getSelector("container", STYLE);
    c.set("background", createGradient(WIZARD_BG_DEEP));
    c.set("insets", new Insets3f(0, 0, 0, 0));

    Attributes l = styles.getSelector("label", STYLE);
    l.set("color", WIZARD_TEXT_PRIMARY);
    l.set("fontSize", 14);

    Attributes b = styles.getSelector("button", STYLE);
    b.set("background", createGradient(WIZARD_BG_CARD));
    b.set("color", WIZARD_TEXT_PRIMARY);
    b.set("fontSize", 14);
    b.set("insets", new Insets3f(6, 10, 6, 10));

    Attributes tf = styles.getSelector("textField", STYLE);
    tf.set("background", createGradient(WIZARD_BG_CARD));
    tf.set("color", WIZARD_TEXT_PRIMARY);
    tf.set("fontSize", 14);
    tf.set("insets", new Insets3f(6, 8, 6, 8));

    Attributes s = styles.getSelector("slider", STYLE);
    s.set("background", createGradient(WIZARD_BORDER));
  }
}
