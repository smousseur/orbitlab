package com.smousseur.orbitlab.ui;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.font.BitmapFont;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.IconComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import java.util.HashMap;
import java.util.Map;

import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Shared UI utilities: gradient backgrounds, font loaders, and layout helpers.
 *
 * <p>Call {@link #init(AssetManager)} once from {@link AppStyles#init(AssetManager)} before any
 * style class uses {@link #gradientBackground(ColorRGBA)}.
 */
public final class UiKit {

  private static final Logger logger = LogManager.getLogger(UiKit.class);

  private static final String GRADIENT_TEXTURE = "com/simsilica/lemur/icons/bordered-gradient.png";
  private static final String FONT_RAJDHANI = "fonts/rajdhani-semibold-%d.fnt";
  private static final String FONT_MONO = "fonts/share-tech-mono-%d.fnt";
  private static final String FONT_ORBITRON = "fonts/orbitron-semibold-%d.fnt";
  private static final String FONT_IBM_PLEX_MONO = "fonts/ibmplexmono-regular-%d.fnt";
  private static final String FONT_SORA = "fonts/sora-medium-%d.fnt";
  private static final String WIZARD_DIR = "interface/wizard/";

  private static AssetManager assetManager;
  private static Texture gradientTex;
  private static final Map<String, Texture2D> wizardTextureCache = new HashMap<>();

  private UiKit() {}

  /**
   * Loads the shared gradient texture and stores the asset manager for later calls.
   *
   * @param am the JME3 asset manager
   */
  public static void init(AssetManager am) {
    assetManager = am;
    gradientTex = am.loadTexture(GRADIENT_TEXTURE);
  }

  /**
   * Creates a 9-slice gradient background tinted to {@code color}.
   *
   * <p>Uses the Lemur built-in {@code bordered-gradient.png} texture loaded once during {@link
   * #init(AssetManager)}.
   *
   * @param color the tint colour
   * @return a new background component
   */
  public static TbtQuadBackgroundComponent gradientBackground(ColorRGBA color) {
    TbtQuadBackgroundComponent bg =
        TbtQuadBackgroundComponent.create(gradientTex, 1, 1, 1, 126, 126, 1f, false);
    bg.setColor(color);
    return bg;
  }

  /**
   * Loads {@code rajdhani-semibold} at the given pixel size, falling back to Lemur's default.
   *
   * @param size pixel size (must match a bundled variant: 10, 12, 14, 16, 18, 20)
   * @return the bitmap font, never {@code null}
   */
  public static BitmapFont rajdhani(int size) {
    return AppStyles.loadFontSafe(assetManager, String.format(FONT_RAJDHANI, size));
  }

  /**
   * Loads {@code share-tech-mono} at the given pixel size, falling back to Lemur's default.
   *
   * @param size pixel size (must match a bundled variant: 10, 12, 14)
   * @return the bitmap font, never {@code null}
   */
  public static BitmapFont mono(int size) {
    return AppStyles.loadFontSafe(assetManager, String.format(FONT_MONO, size));
  }

  /**
   * Returns a fixed-size transparent container used for padding in layouts.
   *
   * @param width preferred width in pixels
   * @param height preferred height in pixels
   * @return a new spacer container with no background
   */
  public static Container spacer(float width, float height) {
    Container s = new Container();
    s.setPreferredSize(new Vector3f(width, height, 0));
    s.setBackground(null);
    return s;
  }

  /** Returns a vertical gap spacer with zero width. */
  public static Container vSpacer(float height) {
    return spacer(0, height);
  }

  /** Returns a horizontal gap spacer with zero height. */
  public static Container hSpacer(float width) {
    return spacer(width, 0);
  }

  /**
   * Loads an icon texture into a fixed-size container. Falls back to an empty container when the
   * asset is missing.
   *
   * @param iconPath asset path
   * @param width preferred width in pixels
   * @param height preferred height in pixels
   * @return container with the icon background, or an empty container on missing asset
   */
  public static Container iconPlaceholder(String iconPath, float width, float height) {
    Container icon = new Container();
    icon.setPreferredSize(new Vector3f(width, height, 0));
    try {
      Texture tex = assetManager.loadTexture(iconPath);
      icon.setBackground(new QuadBackgroundComponent(tex));
    } catch (AssetNotFoundException e) {
      logger.debug("Icon not found: {}, using empty placeholder", iconPath);
      icon.setBackground(null);
    }
    return icon;
  }

  // =================================================================
  //  Wizard v2 texture / icon / font helpers
  // =================================================================

  /**
   * Loads a wizard v2 texture (bilinear, no mipmaps) from {@code interface/wizard/v2/<name>.png},
   * caching it for reuse. Returns {@code null} on a missing asset.
   */
  private static Texture2D loadWizardTexture(String name) {
    Texture2D cached = wizardTextureCache.get(name);
    if (cached != null) return cached;
    try {
      Texture2D tex = (Texture2D) assetManager.loadTexture(WIZARD_DIR + name + ".png");
      tex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
      tex.setMagFilter(Texture.MagFilter.Bilinear);
      wizardTextureCache.put(name, tex);
      return tex;
    } catch (AssetNotFoundException e) {
      logger.warn("Wizard texture not found: {}", name);
      return null;
    }
  }

  /**
   * Builds a 9-slice background from a wizard v2 texture. The texture is assumed to be square and
   * uses {@code border} pixels from every edge as the non-stretched corner region.
   *
   * @param name texture name without extension or folder
   * @param border inset (pixels) on every side of the texture
   * @return a 9-slice background, or a flat gradient fallback when the texture is missing
   */
  public static TbtQuadBackgroundComponent wizardBg9(String name, int border) {
    Texture2D tex = loadWizardTexture(name);
    if (tex == null) return gradientBackground(ColorRGBA.DarkGray);
    int w = tex.getImage().getWidth();
    int h = tex.getImage().getHeight();
    return TbtQuadBackgroundComponent.create(
        tex, 1f, border, border, w - border, h - border, 1f, false);
  }

  /** Builds a flat (non 9-slice) background from a wizard v2 texture. */
  public static QuadBackgroundComponent wizardFlat(String name) {
    Texture2D tex = loadWizardTexture(name);
    if (tex == null) return new QuadBackgroundComponent(ColorRGBA.DarkGray);
    return new QuadBackgroundComponent(tex);
  }

  /**
   * Returns a fixed-size container backed by a wizard v2 icon texture. Falls back to an empty
   * container on missing asset.
   */
  public static Container wizardIcon(String name, float width, float height) {
    return iconPlaceholder(WIZARD_DIR + name + ".png", width, height);
  }

  /** Builds a Lemur {@link IconComponent} pointing at a wizard v2 texture. */
  public static IconComponent wizardIconComponent(String name) {
    return new IconComponent(WIZARD_DIR + name + ".png");
  }

  /** Loads {@code orbitron-semibold} at the given pixel size, falling back to Lemur's default. */
  public static BitmapFont orbitron(int size) {
    return AppStyles.loadFontSafe(assetManager, String.format(FONT_ORBITRON, size));
  }

  /** Loads {@code ibmplexmono-regular} at the given pixel size, falling back to Lemur's default. */
  public static BitmapFont ibmPlexMono(int size) {
    return AppStyles.loadFontSafe(assetManager, String.format(FONT_IBM_PLEX_MONO, size));
  }

  /** Loads {@code sora-medium} at the given pixel size, falling back to Lemur's default. */
  public static BitmapFont sora(int size) {
    return AppStyles.loadFontSafe(assetManager, String.format(FONT_SORA, size));
  }

  public static Container fieldLabelRow(String text, String iconName) {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    row.addChild(UiKit.wizardIcon(iconName, 14f, 14f));
    row.addChild(UiKit.hSpacer(6f));
    Label label = row.addChild(new Label(text, MissionWizardStyles.STYLE));
    label.setFont(UiKit.ibmPlexMono(11));
    label.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
    return row;
  }
}
