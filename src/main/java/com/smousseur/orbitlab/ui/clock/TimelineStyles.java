package com.smousseur.orbitlab.ui.clock;

import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.Styles;

/**
 * Centralise les styles Lemur du widget Timeline.
 *
 * <p>Objectif: partir de GlassStyle (base) et surcharger uniquement ce qui est nécessaire. Quand tu
 * auras des PNG, tu remplaceras le QuadBackgroundComponent par un composant basé texture.
 */
public final class TimelineStyles {

  public static final String STYLE = "timeline";

  private TimelineStyles() {}

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
