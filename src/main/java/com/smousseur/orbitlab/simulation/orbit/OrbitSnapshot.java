package com.smousseur.orbitlab.simulation.orbit;

import com.smousseur.orbitlab.core.SolarSystemBody;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

import java.util.Objects;

/**
 * An immutable snapshot of a celestial body's computed orbit positions at a specific point in time.
 *
 * <p>Contains a sequence of heliocentric position vectors centered on a reference date,
 * along with the orbital period and sampling step used to generate them. Each snapshot
 * carries a version number for change detection.
 *
 * @param body the celestial body this snapshot represents
 * @param centerDate the center date around which positions are computed
 * @param periodSeconds the orbital period of the body in seconds
 * @param stepSeconds the time interval between consecutive position samples in seconds
 * @param positions the array of heliocentric position vectors in meters
 * @param version a monotonically increasing version number for change detection
 */
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
