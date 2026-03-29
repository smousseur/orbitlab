package com.smousseur.orbitlab.ui.mission;

import com.jme3.asset.AssetManager;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.Styles;
import com.smousseur.orbitlab.ui.AppStyles;

/**
 * Defines and registers Lemur GUI styles for the mission panel widget using the Ice Blue palette.
 *
 * <p>Creates a "mission-panel" style derived from the Glass base style. Containers get the deep
 * blue translucent background, labels use the Ice Blue primary text colour, and buttons use the
 * lighter panel background.
 */
public final class MissionPanelStyles {

  /** The Lemur style identifier used to apply mission panel visual styling. */
  public static final String STYLE = "mission-panel";

  private MissionPanelStyles() {}

  /**
   * Initializes and registers the mission panel styles with the global Lemur style system.
   *
   * @param assetManager the JME3 asset manager (reserved for future texture loading)
   */
  public static void init(AssetManager assetManager) {
    Styles styles = GuiGlobals.getInstance().getStyles();
    styles.applyStyles(STYLE, "glass");

    styles
        .getSelector("container", STYLE)
        .set("background", new QuadBackgroundComponent(AppStyles.ICE_PANEL_BG));

    Attributes labelAttrs = styles.getSelector("label", STYLE);
    labelAttrs.set("color", AppStyles.ICE_TEXT_PRIMARY);

    Attributes buttonAttrs = styles.getSelector("button", STYLE);
    buttonAttrs.set("background", new QuadBackgroundComponent(AppStyles.ICE_PANEL_BG_LIGHT));
    buttonAttrs.set("color", AppStyles.ICE_TEXT_PRIMARY);
  }
}
