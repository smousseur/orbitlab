package com.smousseur.orbitlab.simulation.ephemeris;

import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

import java.util.Objects;

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
