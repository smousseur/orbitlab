package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.frames.TopocentricFrame;
import org.orekit.time.AbsoluteDate;

/**
 * A ground site and the orbital plane it reaches at an instant — the geometry every launch-window
 * problem raised on a pad starts from.
 *
 * <p><b>It exists because two problems ask the same question</b>: {@link EarthLaunchWindowProblem}
 * compares the plane the pad reaches against a target plane, and {@link LunarLaunchWindowProblem}
 * takes that plane as the one it must depart in (MIS-4 / L2, spec {@code
 * docs/lunar-flyby/04-conception-L2.md} §2.6). Two classes answering it separately would be two
 * places to be right about a frame chain and a topocentric basis.
 *
 * <p><b>The basis itself is not written here.</b> The {@code (north, east)} convention stays in
 * {@link Physics#localHorizontalDirection}, its single site; what this class owns is the pad's
 * inertial position and the plane raised on it.
 */
class LaunchSitePlane {

  private final TopocentricFrame pad;
  private final double azimuth;

  /**
   * @param latitude the site latitude in degrees
   * @param longitude the site longitude in degrees
   * @param altitude the site altitude in meters
   * @param azimuth the launch azimuth in radians, clockwise from north
   */
  LaunchSitePlane(double latitude, double longitude, double altitude, double azimuth) {
    this.pad =
        new TopocentricFrame(
            OrekitService.get().getEarthEllipsoid(),
            new GeodeticPoint(
                FastMath.toRadians(latitude), FastMath.toRadians(longitude), altitude),
            "Launch Pad");
    this.azimuth = azimuth;
  }

  /**
   * @param epoch the instant the Earth's rotation is read at
   * @return the pad's inertial position at that instant, in GCRF (m)
   */
  Vector3D positionAt(AbsoluteDate epoch) {
    return OrekitService.get()
        .itrf()
        .getTransformTo(OrekitService.get().gcrf(), epoch)
        .transformPosition(pad.getCartesianPoint());
  }

  /**
   * The unit normal of the plane the site reaches at {@code epoch}, in GCRF.
   *
   * @param epoch the instant the Earth's rotation is read at
   * @return the unit angular momentum of the reachable plane
   */
  Vector3D normalAt(AbsoluteDate epoch) {
    return normalOn(positionAt(epoch));
  }

  /**
   * The same normal, from a position already resolved — for a caller that needs both, the position
   * being where it puts the parking orbit's phase.
   *
   * @param position the pad's inertial position
   * @return the unit angular momentum of the reachable plane
   */
  Vector3D normalOn(Vector3D position) {
    return Vector3D.crossProduct(position, Physics.localHorizontalDirection(position, azimuth))
        .normalize();
  }
}
