package com.smousseur.orbitlab.simulation.ephemeris;

import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

import java.util.Objects;

/**
 * An immutable snapshot of a celestial body's state at a specific point in time.
 *
 * <p>Contains the body's position/velocity coordinates and rotation, both expressed in the
 * ICRF (International Celestial Reference Frame).
 *
 * @param date the epoch at which this sample was taken
 * @param pvIcrf the position and velocity coordinates in the ICRF frame
 * @param rotationIcrf the body's orientation (rotation from ICRF to body-fixed frame)
 */
public record BodySample(
    AbsoluteDate date,
    PVCoordinates pvIcrf,
    Rotation rotationIcrf
) {
  public BodySample {
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(pvIcrf, "pvIcrf");
    Objects.requireNonNull(rotationIcrf, "rotationIcrf");
  }
}
