package com.smousseur.orbitlab.simulation.orbit;

import com.smousseur.orbitlab.core.SolarSystemBody;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

import java.util.List;
import java.util.Objects;

/**
 * An immutable sequence of heliocentric positions representing the orbital path of a celestial body
 * over a specific time range.
 *
 * <p>Positions are stored in meters relative to the Sun's position (heliocentric coordinates)
 * in the ICRF frame.
 *
 * @param body the celestial body whose orbit this path describes
 * @param start the start time of the orbital path
 * @param end the end time of the orbital path
 * @param stepSeconds the time interval between consecutive positions in seconds
 * @param positionsHelioMeters the list of heliocentric position vectors in meters
 */
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
