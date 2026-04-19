package com.smousseur.orbitlab.ui;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.font.BitmapFont;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.texture.Texture;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
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

  private static AssetManager assetManager;
  private static Texture gradientTex;

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
   * <p>Uses the Lemur built-in {@code bordered-gradient.png} texture loaded once during
   * {@link #init(AssetManager)}.
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
   * @param width  preferred width in pixels
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
   * @param width    preferred width in pixels
   * @param height   preferred height in pixels
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
}
