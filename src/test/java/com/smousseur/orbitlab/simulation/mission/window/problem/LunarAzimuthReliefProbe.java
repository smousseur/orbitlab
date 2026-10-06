package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan.Departure;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import java.lang.reflect.Method;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;

/** Probe: the lunar window criterion with a free launch azimuth, swept over a lunation. */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
class LunarAzimuthReliefProbe {
  private static final Logger logger = LogManager.getLogger(LunarAzimuthReliefProbe.class);
  private static final double PARKING = 400_000.0;
  private static final double RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + PARKING;

  private Method ascent;

  @Test
  void relief() throws Exception {
    OrekitService.get().initialize();
    ascent =
        PropellantBudget.class.getDeclaredMethod(
            "ascentDeltaV", double.class, double.class, double.class);
    ascent.setAccessible(true);
    sweep("Kourou", 5.236, -52.775, 0.0);
    sweep("Canaveral", 28.562, -80.577, 3.0);
  }

  private void sweep(String name, double lat, double lon, double alt) throws Exception {
    double dueEastAscent = (double) ascent.invoke(null, PARKING, lat, FastMath.PI / 2);
    logger.info(
        String.format(Locale.ROOT, "AZIMUTH %s due-east ascent %.0f m/s", name, dueEastAscent));
    AbsoluteDate start = new AbsoluteDate(2026, 10, 6, 0, 0, 0.0, TimeScalesFactory.getUTC());
    StringBuilder table = new StringBuilder();
    double worstDayExtra = 0.0;
    double sumDayExtra = 0.0;
    for (int day = 0; day < 28; day++) {
      Best freeBest = null;
      Best eastBest = null;
      for (int h = 0; h < 24; h++) {
        AbsoluteDate epoch = start.shiftedBy((day * 24 + h) * 3_600.0);
        for (int a2 = 0; a2 <= 360; a2++) {
          double azimuth = FastMath.toRadians(a2 * 0.5);
          Best b = evaluate(lat, lon, alt, azimuth, epoch);
          if (freeBest == null || b.total < freeBest.total) {
            freeBest = b;
          }
          if (a2 == 180 && (eastBest == null || b.total < eastBest.total)) {
            eastBest = b;
          }
        }
      }
      double extra = freeBest.ascent - dueEastAscent;
      worstDayExtra = FastMath.max(worstDayExtra, extra);
      sumDayExtra += extra;
      table.append(
          String.format(
              Locale.ROOT,
              "%n  d%02d east: tli %5.0f (β%+5.1f°) total %5.0f | free: A %5.1f° i %4.1f° β%+4.2f°"
                  + " tli %5.0f ascent %+4.0f vs east, total %5.0f (gain %+5.0f)",
              day,
              eastBest.tli,
              eastBest.beta,
              eastBest.total,
              freeBest.azimuthDeg,
              freeBest.inclinationDeg,
              freeBest.beta,
              freeBest.tli,
              extra,
              freeBest.total,
              eastBest.total - freeBest.total));
    }
    logger.info("AZIMUTH {} (hourly epochs, azimuth step 0.5°, best of each day):{}", name, table);
    logger.info(
        String.format(
            Locale.ROOT,
            "AZIMUTH %s ascent surcharge of the free optimum vs due east: mean %.0f, worst %.0f m/s",
            name,
            sumDayExtra / 28,
            worstDayExtra));
  }

  private Best evaluate(double lat, double lon, double alt, double azimuth, AbsoluteDate epoch)
      throws Exception {
    LaunchSitePlane site = new LaunchSitePlane(lat, lon, alt, azimuth);
    Vector3D position = site.positionAt(epoch);
    Vector3D normal = site.normalOn(position);
    Departure departure = TranslunarInjectionPlan.departureFrom(circular(normal, position, epoch));
    SpacecraftState injection =
        circular(normal, departure.injectionDirection(), departure.injectionDate());
    double tli =
        TranslunarInjectionPlan.keplerianInjectionDeltaV(injection, departure.arrivalDate());
    double ascentDv = (double) ascent.invoke(null, PARKING, lat, azimuth);
    return new Best(
        FastMath.toDegrees(azimuth),
        FastMath.toDegrees(FastMath.acos(normal.getZ())),
        FastMath.toDegrees(departure.planeMisalignment()),
        tli,
        ascentDv,
        tli + ascentDv);
  }

  private static SpacecraftState circular(Vector3D normal, Vector3D towards, AbsoluteDate date) {
    Vector3D direction =
        towards.subtract(normal.scalarMultiply(towards.dotProduct(normal))).normalize();
    Vector3D velocity =
        Vector3D.crossProduct(normal, direction)
            .scalarMultiply(FastMath.sqrt(Constants.WGS84_EARTH_MU / RADIUS));
    return new SpacecraftState(
            new CartesianOrbit(
                new TimeStampedPVCoordinates(date, direction.scalarMultiply(RADIUS), velocity),
                OrekitService.get().gcrf(),
                Constants.WGS84_EARTH_MU))
        .withMass(1_000.0);
  }

  private record Best(
      double azimuthDeg,
      double inclinationDeg,
      double beta,
      double tli,
      double ascent,
      double total) {}
}
