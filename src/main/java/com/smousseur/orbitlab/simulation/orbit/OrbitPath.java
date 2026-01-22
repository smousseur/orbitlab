package com.smousseur.orbitlab.simulation.orbit;

import com.smousseur.orbitlab.core.SolarSystemBody;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

import java.util.List;
import java.util.Objects;

public record OrbitPath(
    SolarSystemBody body,
    AbsoluteDate start,
    AbsoluteDate end,
    double stepSeconds,
    List<Vector3D> positionsHelioMeters) {
  public OrbitPath {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if (!Double.isFinite(stepSeconds) || stepSeconds <= 0.0) {
      throw new IllegalArgumentException("stepSeconds must be finite and > 0");
    }
    Objects.requireNonNull(positionsHelioMeters, "positionsHelioMeters");
  }
}
