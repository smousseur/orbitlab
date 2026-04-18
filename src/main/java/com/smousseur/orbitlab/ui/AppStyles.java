package com.smousseur.orbitlab.ui;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.font.BitmapFont;
import com.jme3.math.ColorRGBA;
import com.simsilica.lemur.GuiGlobals;
import com.smousseur.orbitlab.ui.clock.TimelineStyles;
import com.smousseur.orbitlab.ui.mission.MissionPanelStyles;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.telemetry.TelemetryStyles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Central registry for OrbitLab's Lemur GUI visual tokens and style initialization.
 *
 * <p>All shared HUD colours and layout constants are defined here so every widget stays
 * visually consistent. Individual style classes ({@link TimelineStyles}, {@link TelemetryStyles})
 * reference these tokens instead of repeating raw literals.
 *
 * <p>Call {@link #init(AssetManager)} once from {@code OrbitLabApplication.simpleInitApp()}
 * after {@code GuiGlobals.initialize()} and {@code BaseStyles.loadGlassStyle()}.
 */
public final class AppStyles {

  private static final Logger logger = LogManager.getLogger(AppStyles.class);

  // -------------------------------------------------------------------------
  // HUD colour palette
  // -------------------------------------------------------------------------

  /** Semi-transparent dark overlay used as the background for all HUD panels. */
  public static final ColorRGBA HUD_BACKGROUND = new ColorRGBA(0f, 0f, 0f, 0.25f);

  /** Barely-visible track colour for HUD sliders. */
  public static final ColorRGBA HUD_SLIDER_TRACK = new ColorRGBA(0.3f, 0.3f, 0.3f, 0.05f);

  /** Accent colour for slider thumb buttons. */
  public static final ColorRGBA HUD_SLIDER_THUMB = new ColorRGBA(0.6f, 0.8f, 0.8f, 0f);

  // -------------------------------------------------------------------------
  // Timeline ("Capsule console") palette — sampled from design handoff
  // -------------------------------------------------------------------------

  /** Timeline cyan accent (#5ee0f5). */
  public static final ColorRGBA TL_CYAN =
      new ColorRGBA(0.369f, 0.878f, 0.961f, 1.0f);

  /** Timeline cyan accent with softened alpha. */
  public static final ColorRGBA TL_CYAN_SOFT =
      new ColorRGBA(0.369f, 0.878f, 0.961f, 0.85f);

  /** Primary timeline text colour (near-white with blue tint). */
  public static final ColorRGBA TL_TEXT_MAIN =
      new ColorRGBA(0.863f, 0.941f, 0.980f, 0.95f);

  /** Dimmed timeline text colour. */
  public static final ColorRGBA TL_TEXT_DIM =
      new ColorRGBA(0.667f, 0.804f, 0.882f, 0.70f);

  /** Muted timeline text colour used for subtle labels like "UTC". */
  public static final ColorRGBA TL_TEXT_MUTED =
      new ColorRGBA(0.549f, 0.706f, 0.804f, 0.50f);

  /** Amber accent for warning-style states (SEEK, burn markers). */
  public static final ColorRGBA TL_AMBER =
      new ColorRGBA(0.910f, 0.647f, 0.278f, 1.0f);

  /** Rose accent for destructive / critical states. */
  public static final ColorRGBA TL_ROSE =
      new ColorRGBA(0.831f, 0.353f, 0.420f, 1.0f);

  // -------------------------------------------------------------------------
  // Ice Blue palette — modern, transparent, blue-toned
  // -------------------------------------------------------------------------

  /** Main panel background — deep blue, fairly transparent. */
  public static final ColorRGBA ICE_PANEL_BG = new ColorRGBA(0.08f, 0.12f, 0.20f, 0.75f);

  /** Lighter panel / row background. */
  public static final ColorRGBA ICE_PANEL_BG_LIGHT = new ColorRGBA(0.12f, 0.18f, 0.28f, 0.60f);

  /** Selected row highlight. */
  public static final ColorRGBA ICE_ROW_SELECTED = new ColorRGBA(0.20f, 0.45f, 0.70f, 0.80f);

  /** Primary accent — buttons, titles. */
  public static final ColorRGBA ICE_ACCENT = new ColorRGBA(0.30f, 0.65f, 0.90f, 1.0f);

  /** Danger action (delete). */
  public static final ColorRGBA ICE_DANGER = new ColorRGBA(0.90f, 0.35f, 0.35f, 1.0f);

  /** Success action (launch). */
  public static final ColorRGBA ICE_SUCCESS = new ColorRGBA(0.30f, 0.80f, 0.55f, 1.0f);

  /** Warning action (optimize). */
  public static final ColorRGBA ICE_WARNING = new ColorRGBA(0.95f, 0.75f, 0.30f, 1.0f);

  /** Primary text colour — near-white with blue tint. */
  public static final ColorRGBA ICE_TEXT_PRIMARY = new ColorRGBA(0.90f, 0.94f, 0.98f, 1.0f);

  /** Secondary text — muted blue-grey. */
  public static final ColorRGBA ICE_TEXT_SECONDARY = new ColorRGBA(0.55f, 0.65f, 0.75f, 1.0f);

  /** Thin separator / border colour. */
  public static final ColorRGBA ICE_BORDER = new ColorRGBA(0.30f, 0.40f, 0.55f, 0.40f);

  // -------------------------------------------------------------------------
  // HUD layout constants
  // -------------------------------------------------------------------------

  /** Margin in pixels between the screen edge and any HUD widget. */
  public static final float HUD_MARGIN_PX = 16f;

  // -------------------------------------------------------------------------

  private AppStyles() {}

  /**
   * Loads a BitmapFont from the given classpath path, falling back to Lemur's default font when
   * the resource is missing. Shared helper for widgets that embed bitmap fonts generated from
   * {@code rajdhani-semibold} or {@code share-tech-mono}.
   *
   * @param assetManager the JME3 asset manager
   * @param path the font resource path (e.g. {@code "fonts/rajdhani-semibold-14.fnt"})
   * @return the loaded font, or Lemur's default if the asset is not present
   */
  public static BitmapFont loadFontSafe(AssetManager assetManager, String path) {
    try {
      return assetManager.loadFont(path);
    } catch (AssetNotFoundException e) {
      logger.debug("Font not found: {}, using Lemur default", path);
      return GuiGlobals.getInstance().loadFont("Interface/Fonts/Default.fnt");
    }
  }

  /**
   * Initializes all widget styles in one pass.
   *
   * @param assetManager the JME3 asset manager forwarded to each style initializer
   */
  public static void init(AssetManager assetManager) {
    TimelineStyles.init(assetManager);
    TelemetryStyles.init(assetManager);
    MissionPanelStyles.init(assetManager);
    MissionWizardStyles.init(assetManager);
  }
}
