package com.smousseur.orbitlab.simulation.orbit;

import com.smousseur.orbitlab.core.SolarSystemBody;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

import java.util.Objects;

public record OrbitSnapshot(
    SolarSystemBody body,
    AbsoluteDate centerDate,
    double periodSeconds,
    double stepSeconds,
    Vector3D[] positions,
    long version) {
  public OrbitSnapshot {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(centerDate, "centerDate");
    Objects.requireNonNull(positions, "positions");
    if (!Double.isFinite(periodSeconds) || periodSeconds <= 0.0) {
      throw new IllegalArgumentException("periodSeconds must be finite and > 0");
    }
    if (!Double.isFinite(stepSeconds) || stepSeconds <= 0.0) {
      throw new IllegalArgumentException("stepSeconds must be finite and > 0");
    }
    if (positions.length < 2) {
      throw new IllegalArgumentException("positions must contain at least 2 points");
    }
    if (version < 0) {
      throw new IllegalArgumentException("version must be >= 0");
    }
  }
}
