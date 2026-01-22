package com.smousseur.orbitlab.simulation.source;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import org.orekit.bodies.CelestialBody;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

import java.util.Objects;

public final class OrekitPvSource implements PvSource {

  @Override
  public PVCoordinates pvIcrf(SolarSystemBody body, AbsoluteDate date) {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(date, "date");

    OrekitService orekit = OrekitService.get();
    Frame icrf = orekit.icrf();
    CelestialBody cb = orekit.body(body);
    return cb.getPVCoordinates(date, icrf);
  }
}
