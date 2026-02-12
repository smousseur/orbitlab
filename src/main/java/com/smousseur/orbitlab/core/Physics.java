package com.smousseur.orbitlab.core;

import org.hipparchus.util.FastMath;

public final class Physics {
  private Physics() {}

  /** Inverse de la transformation delta : physical = tanh(x) × π/2 → x = atanh(physical / (π/2)) */
  public static double inverseScaledTanh(double delta) {
    double normalized = delta / (FastMath.PI / 2.0);
    normalized = FastMath.max(-0.999, FastMath.min(0.999, normalized));
    return 0.5 * FastMath.log((1.0 + normalized) / (1.0 - normalized)); // atanh
  }

  public static double sigmoid(double x) {
    return 1.0 / (1.0 + FastMath.exp(-x));
  }

  public static double inverseSigmoid(double val, double lo, double hi) {
    double s = (val - lo) / (hi - lo);
    s = FastMath.max(1e-6, FastMath.min(1.0 - 1e-6, s));
    return FastMath.log(s / (1.0 - s));
  }
}
