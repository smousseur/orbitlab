package com.smousseur.orbitlab.simulation.mission.optimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.Logger;

/**
 * Stateless diagnostics helpers for inspecting CMA-ES results post-mortem.
 *
 * <p>Used by {@code MissionOptimizer} after each stage optimization to surface
 * pathologies (parameters saturated at their bounds) that would otherwise remain
 * opaque. See {@code specs/optimizer/03-robustness-roadmap.md} §0.1.
 */
public final class OptimizerDiagnostics {

  /** WARN if a parameter sits within this fraction of either bound. */
  public static final double SATURATION_THRESHOLD = 0.05;

  private OptimizerDiagnostics() {}

  /**
   * Saturation report for one parameter.
   *
   * @param index position in the parameter vector
   * @param value the optimized value
   * @param lower lower bound
   * @param upper upper bound
   * @param normalized {@code (value − lower) / (upper − lower)} in [0, 1] (clipped)
   * @param lowSat true when normalized {@literal <} {@link #SATURATION_THRESHOLD}
   * @param highSat true when normalized {@literal >} {@code 1 − SATURATION_THRESHOLD}
   */
  public record BoundFlag(
      int index,
      double value,
      double lower,
      double upper,
      double normalized,
      boolean lowSat,
      boolean highSat) {}

  /**
   * Computes saturation flags for every parameter in {@code best}.
   *
   * @param best the optimized parameter vector
   * @param lower lower bounds (same length as {@code best})
   * @param upper upper bounds (same length as {@code best})
   * @return one {@link BoundFlag} per parameter
   */
  public static List<BoundFlag> evaluateBounds(double[] best, double[] lower, double[] upper) {
    if (best.length != lower.length || best.length != upper.length) {
      throw new IllegalArgumentException(
          "best/lower/upper must have the same length: got "
              + best.length
              + "/"
              + lower.length
              + "/"
              + upper.length);
    }
    List<BoundFlag> flags = new ArrayList<>(best.length);
    for (int i = 0; i < best.length; i++) {
      double range = upper[i] - lower[i];
      double normalized = range > 0 ? (best[i] - lower[i]) / range : 0.5;
      double clipped = Math.max(0.0, Math.min(1.0, normalized));
      boolean low = clipped < SATURATION_THRESHOLD;
      boolean high = clipped > 1.0 - SATURATION_THRESHOLD;
      flags.add(new BoundFlag(i, best[i], lower[i], upper[i], clipped, low, high));
    }
    return flags;
  }

  /**
   * Builds a compact one-line report of all parameters' bound usage.
   *
   * <p>Example: {@code t1=12% | dt1=48% | α1=100%[HIGH-SAT] | β1=63%}.
   *
   * @param flags result of {@link #evaluateBounds}
   * @param paramNames human-readable parameter names (same length as {@code flags});
   *     pass {@code null} or shorter to fall back to {@code x0, x1, ...}
   * @return formatted string
   */
  public static String formatBoundReport(List<BoundFlag> flags, String[] paramNames) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < flags.size(); i++) {
      BoundFlag f = flags.get(i);
      String name = nameOf(paramNames, i);
      if (i > 0) sb.append(" | ");
      sb.append(
          String.format(Locale.ROOT, "%s=%.0f%%", name, f.normalized() * 100.0));
      if (f.lowSat()) sb.append("[LOW-SAT]");
      if (f.highSat()) sb.append("[HIGH-SAT]");
    }
    return sb.toString();
  }

  /**
   * Logs the bound report at INFO and emits a WARN per saturated parameter.
   *
   * @param logger destination logger
   * @param stageName stage being reported (for prefixing)
   * @param flags result of {@link #evaluateBounds}
   * @param paramNames human-readable parameter names
   */
  public static void logBoundReport(
      Logger logger, String stageName, List<BoundFlag> flags, String[] paramNames) {
    logger.info(
        "Stage '{}' bound usage: {}", stageName, formatBoundReport(flags, paramNames));
    for (int i = 0; i < flags.size(); i++) {
      BoundFlag f = flags.get(i);
      if (f.lowSat() || f.highSat()) {
        String name = nameOf(paramNames, i);
        logger.warn(
            "Stage '{}' parameter {} saturated ({}): value={} bounds=[{}, {}] norm={}",
            stageName,
            name,
            f.lowSat() ? "LOW" : "HIGH",
            f.value(),
            f.lower(),
            f.upper(),
            f.normalized());
      }
    }
  }

  /**
   * Returns a compact list of saturated parameter names (e.g. {@code "[α1, β1]"}).
   * Empty string when no saturation occurred.
   */
  public static String saturationSummary(List<BoundFlag> flags, String[] paramNames) {
    StringBuilder sb = new StringBuilder("[");
    boolean first = true;
    for (int i = 0; i < flags.size(); i++) {
      BoundFlag f = flags.get(i);
      if (f.lowSat() || f.highSat()) {
        if (!first) sb.append(", ");
        sb.append(nameOf(paramNames, i));
        sb.append(f.lowSat() ? "(LOW)" : "(HIGH)");
        first = false;
      }
    }
    sb.append("]");
    return sb.toString();
  }

  private static String nameOf(String[] paramNames, int i) {
    if (paramNames == null || i >= paramNames.length || paramNames[i] == null) {
      return "x" + i;
    }
    return paramNames[i];
  }
}
