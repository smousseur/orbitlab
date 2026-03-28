package com.smousseur.orbitlab.ui.mission;

import com.jme3.asset.AssetManager;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.style.Styles;
import com.smousseur.orbitlab.ui.AppStyles;

/**
 * Defines and registers Lemur GUI styles for the mission panel widget.
 *
 * <p>Creates a "mission-panel" style derived from the Glass base style.
 * Visual tokens (colours, margins) are sourced from {@link AppStyles}.
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
        .set("background", new QuadBackgroundComponent(AppStyles.HUD_BACKGROUND));
  }
}
