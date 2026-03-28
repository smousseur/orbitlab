package com.smousseur.orbitlab.ui;

import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.ui.clock.TimelineStyles;
import com.smousseur.orbitlab.ui.mission.MissionPanelStyles;
import com.smousseur.orbitlab.ui.telemetry.TelemetryStyles;

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
  // HUD layout constants
  // -------------------------------------------------------------------------

  /** Margin in pixels between the screen edge and any HUD widget. */
  public static final float HUD_MARGIN_PX = 16f;

  // -------------------------------------------------------------------------

  private AppStyles() {}

  /**
   * Initializes all widget styles in one pass.
   *
   * @param assetManager the JME3 asset manager forwarded to each style initializer
   */
  public static void init(AssetManager assetManager) {
    TimelineStyles.init(assetManager);
    TelemetryStyles.init(assetManager);
    MissionPanelStyles.init(assetManager);
  }
}
