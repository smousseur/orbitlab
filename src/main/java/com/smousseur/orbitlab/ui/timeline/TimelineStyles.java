package com.smousseur.orbitlab.ui.timeline;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.font.BitmapFont;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.Styles;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.UiKit;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Registers the Lemur style for the Capsule timeline and exposes texture/font helpers for {@link
 * TimelineWidget}.
 *
 * <p>Textures follow the layout shipped in {@code src/main/resources/interface/timeline/} as
 * produced by the Orbitlab handoff texture pack; missing textures fall back to no background
 * silently so development environments without the {@code resources/} folder keep running.
 */
public final class TimelineStyles {

  private static final Logger logger = LogManager.getLogger(TimelineStyles.class);

  /** The Lemur style identifier used to apply timeline-specific visual styling. */
  public static final String STYLE = "timeline";

  /**
   * Element id of the mission timeline's toggle. Registered as a selector so the button is built
   * with {@code new Button(text, new ElementId(TRIGGER_ELEMENT), STYLE)} and carries no visual
   * override of its own — the rule {@code UI-4} left behind (spec §11).
   */
  public static final String TRIGGER_ELEMENT = "timeline.trigger.button";

  private static final String TEX_PREFIX = "interface/timeline/";

  /** Rounded corner inset (in pixels) for the capsule 9-slice texture (54×54, R=26). */
  private static final int CAPSULE_INSET = 26;

  private static final String BACKGROUND_ATTR = "background";
  private static final String INSETS_ATTR = "insets";
  private static final String COLOR_ATTR = "color";
  private static final String FONT_ATTR = "font";
  private static final String FONT_SIZE_ATTR = "fontSize";

  private static AssetManager assetManager;
  private static final Map<String, Texture2D> textureCache = new HashMap<>();

  private TimelineStyles() {}

  /**
   * Returns the {@code share-tech-mono} bitmap font at the given pixel size, or Lemur's default if
   * the asset is missing.
   *
   * @param size pixel size (must match one of the bundled sizes: 10, 12, 14)
   * @return the bitmap font (never {@code null})
   */
  public static BitmapFont mono(int size) {
    return UiKit.mono(size);
  }

  /**
   * Returns the {@code rajdhani-semibold} bitmap font at the given pixel size, or Lemur's default
   * if the asset is missing.
   *
   * @param size pixel size (must match one of the bundled sizes: 10, 12, 14, 16, 18, 20)
   * @return the bitmap font (never {@code null})
   */
  public static BitmapFont rajdhani(int size) {
    return UiKit.rajdhani(size);
  }

  /**
   * Loads a timeline texture by its short name (e.g. {@code "capsule.png"}) with cached filter
   * settings tuned for UI rendering. Returns {@code null} if the asset is missing.
   *
   * @param name file name relative to {@code interface/timeline/}
   * @return the cached texture, or {@code null} if absent
   */
  public static Texture2D tex(String name) {
    return textureCache.computeIfAbsent(name, TimelineStyles::loadTexSafe);
  }

  /**
   * Builds a 9-slice capsule background from {@code capsule.png}, or {@code null} if the texture is
   * missing.
   *
   * @return a new background component or {@code null}
   */
  public static TbtQuadBackgroundComponent capsuleBackground() {
    Texture2D capsule = tex("capsule.png");
    if (capsule == null) {
      return null;
    }
    return TbtQuadBackgroundComponent.create(
        capsule, 1f, CAPSULE_INSET, CAPSULE_INSET, CAPSULE_INSET, CAPSULE_INSET, 1f, false);
  }

  /**
   * Builds a 9-slice button background from the given texture name (expects a small 8×8
   * rounded-corner asset like {@code btn-hover.png} or {@code btn-active.png}).
   *
   * @param name texture file name
   * @return a new background component or {@code null} if the texture is missing
   */
  public static TbtQuadBackgroundComponent buttonBackground(String name) {
    Texture2D t = tex(name);
    if (t == null) {
      return null;
    }
    return TbtQuadBackgroundComponent.create(t, 1f, 3, 3, 3, 3, 1f, false);
  }

  /**
   * The toggle's skin in either state. A Lemur selector cannot express "checked", so the on state
   * is a transition the widget applies — the same shape {@code AppMenu.ItemView} uses for hover and
   * check — while both skins still come from this style rather than from literals at the call site.
   *
   * @param active whether the mission timeline is open
   * @return a new background component, or {@code null} when the texture is missing
   */
  public static TbtQuadBackgroundComponent triggerBackground(boolean active) {
    return buttonBackground(active ? "btn-active.png" : "btn-hover.png");
  }

  /**
   * Initializes and registers the timeline styles with the global Lemur style system.
   *
   * @param am the JME3 asset manager, used to load textures and fonts
   */
  public static void init(AssetManager am) {
    assetManager = am;
    textureCache.clear();

    Styles styles = GuiGlobals.getInstance().getStyles();
    // styles.applyStyles(STYLE, "glass");

    TbtQuadBackgroundComponent capsule = capsuleBackground();

    Attributes container = styles.getSelector("container", STYLE);
    if (capsule != null) {
      container.set(BACKGROUND_ATTR, capsule);
    }
    container.set(INSETS_ATTR, new Insets3f(0, 0, 0, 0));

    BitmapFont defaultLabel = rajdhani(12);
    Attributes label = styles.getSelector("label", STYLE);
    label.set(COLOR_ATTR, AppStyles.TL_TEXT_MAIN);
    label.set(FONT_ATTR, defaultLabel);
    label.set(FONT_SIZE_ATTR, 12f);

    // Inline date editing: the field carries no background of its own — the box behind it is drawn
    // by ClockDisplay so that the read-only label and the field share the exact same text origin.
    Attributes textField = styles.getSelector("textField", STYLE);
    textField.set(COLOR_ATTR, AppStyles.TL_CYAN);
    textField.set(FONT_ATTR, UiKit.mono(12));
    textField.set(FONT_SIZE_ATTR, 12f);
    textField.set(INSETS_ATTR, new Insets3f(0, 0, 0, 0));
    textField.set(BACKGROUND_ATTR, null);
    textField.set("singleLine", true);

    Attributes button = styles.getSelector("button", STYLE);
    button.set(COLOR_ATTR, AppStyles.TL_TEXT_DIM);
    button.set(FONT_ATTR, defaultLabel);
    button.set(FONT_SIZE_ATTR, 12f);
    button.set(INSETS_ATTR, new Insets3f(0, 0, 0, 0));
    button.set(BACKGROUND_ATTR, null);

    Attributes trigger = styles.getSelector(TRIGGER_ELEMENT, STYLE);
    trigger.set(COLOR_ATTR, AppStyles.TL_TEXT_DIM);
    trigger.set("highlightColor", AppStyles.TL_CYAN);
    trigger.set(FONT_ATTR, UiKit.mono(10));
    trigger.set(FONT_SIZE_ATTR, 10f);
    trigger.set(INSETS_ATTR, new Insets3f(0, 0, 0, 0));
    trigger.set("textHAlignment", HAlignment.Center);
    trigger.set("textVAlignment", VAlignment.Center);
    trigger.set(BACKGROUND_ATTR, triggerBackground(false));
  }

  private static Texture2D loadTexSafe(String name) {
    String path = TEX_PREFIX + name;
    try {
      Texture2D t = (Texture2D) assetManager.loadTexture(path);
      t.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
      t.setMagFilter(Texture.MagFilter.Bilinear);
      return t;
    } catch (AssetNotFoundException e) {
      logger.debug("Timeline texture not found: {}", path);
      return null;
    }
  }
}
