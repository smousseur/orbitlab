package com.smousseur.orbitlab.ui.telemetry;

import com.jme3.asset.AssetManager;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.Styles;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;

/**
 * Defines and registers Lemur GUI styles for the mission telemetry widget.
 *
 * <p>Creates a "telemetry" style. The shell background (rounded dark-blue panel) is set explicitly
 * on the root container by {@link TelemetryWidget}; sub-containers using this style stay
 * transparent so layout dividers, rows and cells render cleanly.
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

    Attributes c = styles.getSelector("container", STYLE);
    c.set("background", null);
    c.set("insets", new Insets3f(0, 0, 0, 0));

    Attributes l = styles.getSelector("label", STYLE);
    l.set("color", FormStyles.TEXT_PRIMARY);
    l.set("font", UiKit.ibmPlexMono(11));
    l.set("insets", new Insets3f(0, 0, 0, 0));
  }
}
