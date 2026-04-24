package com.smousseur.orbitlab.ui.telemetry;

import com.jme3.asset.AssetManager;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.style.Styles;
import com.smousseur.orbitlab.ui.AppStyles;

/**
 * Defines and registers Lemur GUI styles for the mission telemetry widget.
 *
 * <p>Creates a "telemetry" style derived from the Glass base style. Visual tokens (colours,
 * margins) are sourced from {@link AppStyles}.
 */
public final class TelemetryStyles {

  /** The Lemur style identifier used to apply telemetry-specific visual styling. */
  public static final String STYLE = "telemetry";

  private TelemetryStyles() {}

  /**
   * Initializes and registers the telemetry styles with the global Lemur style system.
   *
   * @param assetManager the JME3 asset manager (reserved for future texture loading)
   */
  public static void init(AssetManager assetManager) {
    Styles styles = GuiGlobals.getInstance().getStyles();
    // styles.applyStyles(STYLE, "glass");
    styles
        .getSelector("container", STYLE)
        .set("background", new QuadBackgroundComponent(AppStyles.HUD_BACKGROUND));
  }
}
