package com.smousseur.orbitlab.ui.clock;

import com.jme3.asset.AssetManager;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.Styles;
import com.smousseur.orbitlab.ui.AppStyles;

/**
 * Defines and registers Lemur GUI styles for the timeline widget.
 *
 * <p>Builds a custom "timeline" style based on the Glass theme, overriding container
 * transparency, slider background, and thumb button appearance to suit the HUD overlay.
 * Visual tokens (colours, margins) are sourced from {@link AppStyles}.
 */
public final class TimelineStyles {

  /** The Lemur style identifier used to apply timeline-specific visual styling. */
  public static final String STYLE = "timeline";

  private TimelineStyles() {}

  /**
   * Initializes and registers the timeline styles with the global Lemur style system.
   *
   * @param assetManager the JME3 asset manager (reserved for future texture loading)
   */
  public static void init(AssetManager assetManager) {
    Styles styles = GuiGlobals.getInstance().getStyles();
    styles.applyStyles(STYLE, "glass");

    styles
        .getSelector("container", STYLE)
        .set("background", new QuadBackgroundComponent(AppStyles.HUD_BACKGROUND));
    styles
        .getSelector("slider", STYLE)
        .set("background", new QuadBackgroundComponent(AppStyles.HUD_SLIDER_TRACK));

    Attributes sliderButton = styles.getSelector("slider.thumb.button", STYLE);
    sliderButton.set("background", new QuadBackgroundComponent(AppStyles.HUD_SLIDER_THUMB));
    sliderButton.set("text", "[]");
  }
}
