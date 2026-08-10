package com.smousseur.orbitlab.ui.mission;

import com.smousseur.orbitlab.simulation.OrbitElements;
import java.util.Locale;
import org.hipparchus.util.FastMath;

/**
 * Every string the mission detail view and the panel footer display.
 *
 * <p>Gathered here rather than inlined in the Lemur widgets for one practical reason: the widgets
 * need an {@code AssetManager} and a JME context, so nothing about them can be unit-tested, whereas
 * every decision that can actually be wrong — rounding, signs, units, the dash that stands for "not
 * applicable" — lives in this class and is covered.
 *
 * <p><b>ASCII only.</b> The bundled bitmap fonts carry glyphs 32-127: {@code deg} not the degree
 * sign, {@code x} not the multiplication sign, {@code ...} not the ellipsis character.
 */
public final class MissionResultText {

  /** What a value that does not apply looks like: a non-propulsive stage's ΔV, a missing reading. */
  public static final String NOT_APPLICABLE = "-";

  private static final double ONE_TONNE_KG = 1000.0;
  private static final long SECONDS_PER_MINUTE = 60L;
  private static final long SECONDS_PER_HOUR = 3600L;

  private MissionResultText() {}

  /**
   * The apsides of an orbit, e.g. {@code "400000 x 400114 m"}.
   *
   * @param elements the orbit elements
   * @return the formatted apsides
   */
  public static String formatAltitudes(OrbitElements elements) {
    return String.format(
        Locale.ROOT, "%.0f x %.0f m", elements.perigeeAltitude(), elements.apogeeAltitude());
  }

  /**
   * An inclination in degrees, e.g. {@code "i=51.6012 deg"}.
   *
   * @param radians the inclination in radians
   * @return the formatted inclination
   */
  public static String formatInclination(double radians) {
    return String.format(Locale.ROOT, "i=%.4f deg", FastMath.toDegrees(radians));
  }

  /**
   * The signed deviation of an achieved orbit from a target, e.g. {@code "miss +0 / +114 m
   * +0.0012 deg"}. Signs are always explicit: an unsigned deviation reads as a magnitude and hides
   * whether the orbit came in high or low.
   *
   * @param achieved the achieved elements, in whichever convention the caller is reporting
   * @param target the requested orbit
   * @return the formatted deviation
   */
  public static String formatMiss(OrbitElements achieved, MissionTargetOrbit target) {
    return String.format(
        Locale.ROOT,
        "miss %+.0f / %+.0f m  %+.4f deg",
        achieved.perigeeAltitude() - target.perigeeAltitude(),
        achieved.apogeeAltitude() - target.apogeeAltitude(),
        FastMath.toDegrees(achieved.inclination() - target.inclination()));
  }

  /**
   * A stage duration as {@code M:SS}, gaining an hour field past 3600 s.
   *
   * @param seconds the duration in seconds
   * @return the formatted duration, or {@code "-"} when the value is not a usable duration
   */
  public static String formatDuration(double seconds) {
    if (!Double.isFinite(seconds) || seconds < 0.0) {
      return NOT_APPLICABLE;
    }
    long total = FastMath.round(seconds);
    long hours = total / SECONDS_PER_HOUR;
    long minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE;
    long secs = total % SECONDS_PER_MINUTE;
    return hours > 0
        ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
        : String.format(Locale.ROOT, "%d:%02d", minutes, secs);
  }

  /**
   * A stage delta-v, rounded to the metre per second.
   *
   * @param metresPerSecond the delta-v
   * @return the formatted delta-v, or {@code "-"} for a non-propulsive stage
   */
  public static String formatDeltaV(double metresPerSecond) {
    if (!Double.isFinite(metresPerSecond) || metresPerSecond <= 0.0) {
      return NOT_APPLICABLE;
    }
    return String.format(Locale.ROOT, "%.0f m/s", metresPerSecond);
  }

  /**
   * A propellant mass, in tonnes past one tonne and in kilograms below.
   *
   * @param kilograms the mass
   * @return the formatted mass, or {@code "-"} when nothing was burnt
   */
  public static String formatPropellant(double kilograms) {
    if (!Double.isFinite(kilograms) || kilograms <= 0.0) {
      return NOT_APPLICABLE;
    }
    return kilograms >= ONE_TONNE_KG
        ? String.format(Locale.ROOT, "%.1f t", kilograms / ONE_TONNE_KG)
        : String.format(Locale.ROOT, "%.0f kg", kilograms);
  }

  /**
   * Clips a text to {@code maxChars} <em>including</em> the trailing marker, so the result never
   * exceeds the width the caller budgeted for it.
   *
   * @param text the text to clip
   * @param maxChars the maximum length of the result, marker included
   * @return the text, clipped and marked when it did not fit
   */
  public static String truncate(String text, int maxChars) {
    if (text.length() <= maxChars) {
      return text;
    }
    return text.substring(0, Math.max(0, maxChars - 3)) + "...";
  }
}
