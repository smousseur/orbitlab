package com.smousseur.orbitlab.simulation.source;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.ephemeris.BodySample;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.orekit.bodies.CelestialBody;
import org.orekit.frames.Frame;
import org.orekit.frames.Transform;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

import java.util.Objects;

public class OrekitEphemerisSource implements EphemerisSource {

  @Override
  public BodySample sampleIcrf(SolarSystemBody body, AbsoluteDate date) {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(date, "date");

    OrekitService orekitService = OrekitService.get();
    Frame icrf = orekitService.icrf();
    CelestialBody celestialBody = orekitService.body(body);

    PVCoordinates pvIcrf = celestialBody.getPVCoordinates(date, icrf);

    Frame bodyFixed = celestialBody.getBodyOrientedFrame();
    Transform icrfToBodyFixed = icrf.getTransformTo(bodyFixed, date);
    Rotation rotationIcrf = icrfToBodyFixed.getRotation(); // convention: ICRF -> bodyFixed

    return new BodySample(date, pvIcrf, rotationIcrf);
  }
}
