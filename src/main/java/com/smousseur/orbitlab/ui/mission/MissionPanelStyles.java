package com.smousseur.orbitlab.ui.mission;

import com.jme3.asset.AssetManager;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.Styles;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.UiKit;

/**
 * Defines and registers Lemur GUI styles for the mission panel widget using the Ice Blue palette
 * with Glass-style gradient backgrounds.
 */
public final class MissionPanelStyles {

  /** The Lemur style identifier used to apply mission panel visual styling. */
  public static final String STYLE = "mission-panel";

  private MissionPanelStyles() {}

  /**
   * Initializes and registers the mission panel styles with the global Lemur style system.
   *
   * @param assetManager the JME3 asset manager (unused; gradient texture is managed by
   *     {@link UiKit})
   */
  public static void init(AssetManager assetManager) {
    Styles styles = GuiGlobals.getInstance().getStyles();
    styles.applyStyles(STYLE, "glass");

    Attributes containerAttrs = styles.getSelector("container", STYLE);
    containerAttrs.set("background", UiKit.gradientBackground(AppStyles.ICE_PANEL_BG));
    containerAttrs.set("insets", new Insets3f(4, 6, 4, 6));

    Attributes labelAttrs = styles.getSelector("label", STYLE);
    labelAttrs.set("color", AppStyles.ICE_TEXT_PRIMARY);
    labelAttrs.set("fontSize", 14);

    Attributes buttonAttrs = styles.getSelector("button", STYLE);
    buttonAttrs.set("background", UiKit.gradientBackground(AppStyles.ICE_PANEL_BG_LIGHT));
    buttonAttrs.set("color", AppStyles.ICE_TEXT_PRIMARY);
    buttonAttrs.set("fontSize", 14);
    buttonAttrs.set("insets", new Insets3f(4, 8, 4, 8));
  }
}
