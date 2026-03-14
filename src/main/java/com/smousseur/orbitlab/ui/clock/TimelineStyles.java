package com.smousseur.orbitlab.ui.clock;

import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.Styles;

/**
 * Defines and registers Lemur GUI styles for the timeline widget.
 *
 * <p>Builds a custom "timeline" style based on the Glass theme, overriding container
 * transparency, slider background, and thumb button appearance to suit the HUD overlay.
 */
public final class TimelineStyles {

  /** The Lemur style identifier used to apply timeline-specific visual styling. */
  public static final String STYLE = "timeline";

  private TimelineStyles() {}

  /**
   * Initializes and registers the timeline styles with the global Lemur style system.
   *
   * <p>Creates a "timeline" style derived from the Glass base style and customizes
   * container backgrounds, slider tracks, and thumb buttons for the HUD timeline widget.
   *
   * @param assetManager the JME3 asset manager (reserved for future texture loading)
   */
  public static void init(AssetManager assetManager) {
    // assetManager pas encore utilisé ici (pas d'images), mais on le garde pour la suite.
    // (Tu pourras y loader des textures pour track/knob.)
    Styles styles = GuiGlobals.getInstance().getStyles();

    // Crée un style "timeline" basé sur le style "base" (Glass)
    styles.applyStyles(STYLE, "glass");

    // Rendre les containers un peu plus transparents (HUD)
    styles
        .getSelector("container", STYLE)
        .set("background", new QuadBackgroundComponent(new ColorRGBA(0f, 0f, 0f, 0.25f)));
    styles
        .getSelector("slider", STYLE)
        .set("background", new QuadBackgroundComponent(new ColorRGBA(0.3f, 0.3f, 0.3f, 0.05f)));
    // Thumb/knob (cible exacte du thème glass)
    Attributes sliderButton = styles.getSelector("slider.thumb.button", STYLE);
    sliderButton.set(
        "background", new QuadBackgroundComponent(new ColorRGBA(0.6f, 0.8f, 0.8f, 0f)));
    sliderButton.set("text", "[]");
    /*
        // Slider: track/knob provisoires (couleur/alpha) en attendant les PNG
        // Note: les noms exacts d'éléments ("slider", "thumb", etc.) dépendent du thème.
        // Si besoin, on affinera après un premier run.
        {
          Attributes slider = styles.getSelector(STYLE, "slider").get();
          slider.set("background", new QuadBackgroundComponent(new ColorRGBA(1f, 1f, 1f, 0.10f)));
        }
    */
  }
}
