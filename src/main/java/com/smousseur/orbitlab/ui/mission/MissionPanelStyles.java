package com.smousseur.orbitlab.ui.mission;

import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.texture.Texture;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.Styles;
import com.smousseur.orbitlab.ui.AppStyles;

/**
 * Defines and registers Lemur GUI styles for the mission panel widget using the Ice Blue palette
 * with Glass-style gradient backgrounds.
 *
 * <p>Uses the Lemur built-in {@code bordered-gradient.png} texture via {@link
 * TbtQuadBackgroundComponent} to produce gradient/bevel effects, tinted with Ice Blue colours.
 */
public final class MissionPanelStyles {

  /** The Lemur style identifier used to apply mission panel visual styling. */
  public static final String STYLE = "mission-panel";

  private static final String GRADIENT_TEXTURE =
      "com/simsilica/lemur/icons/bordered-gradient.png";

  /** The gradient texture loaded during init, reusable by the widget for programmatic backgrounds. */
  private static Texture gradientTex;

  private MissionPanelStyles() {}

  /**
   * Creates a {@link TbtQuadBackgroundComponent} with the gradient texture tinted to the given
   * colour. Callers can use this to create action-button backgrounds with specific colours.
   *
   * @param color the tint colour
   * @return a new gradient background component
   */
  public static TbtQuadBackgroundComponent createGradient(ColorRGBA color) {
    TbtQuadBackgroundComponent bg =
        TbtQuadBackgroundComponent.create(gradientTex, 1, 1, 1, 126, 126, 1f, false);
    bg.setColor(color);
    return bg;
  }

  /**
   * Initializes and registers the mission panel styles with the global Lemur style system.
   *
   * @param assetManager the JME3 asset manager used to load the gradient texture
   */
  public static void init(AssetManager assetManager) {
    gradientTex = assetManager.loadTexture(GRADIENT_TEXTURE);

    Styles styles = GuiGlobals.getInstance().getStyles();
    styles.applyStyles(STYLE, "glass");

    // Container: blue gradient + padding
    Attributes containerAttrs = styles.getSelector("container", STYLE);
    containerAttrs.set("background", createGradient(AppStyles.ICE_PANEL_BG));
    containerAttrs.set("insets", new Insets3f(4, 6, 4, 6));

    // Label: Ice Blue primary text
    Attributes labelAttrs = styles.getSelector("label", STYLE);
    labelAttrs.set("color", AppStyles.ICE_TEXT_PRIMARY);
    labelAttrs.set("fontSize", 14);

    // Button: lighter gradient + padding + text colour
    Attributes buttonAttrs = styles.getSelector("button", STYLE);
    buttonAttrs.set("background", createGradient(AppStyles.ICE_PANEL_BG_LIGHT));
    buttonAttrs.set("color", AppStyles.ICE_TEXT_PRIMARY);
    buttonAttrs.set("fontSize", 14);
    buttonAttrs.set("insets", new Insets3f(4, 8, 4, 8));
  }
}
