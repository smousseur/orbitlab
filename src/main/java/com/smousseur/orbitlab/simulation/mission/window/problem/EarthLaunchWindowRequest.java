package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import java.util.Objects;
import org.hipparchus.util.FastMath;
import org.orekit.utils.Constants;

/**
 * Everything a launch window needs to know about a mission, and nothing else (MIS-2).
 *
 * <p><b>Why not the {@link MissionSpec.EarthOrbit} itself.</b> A spec is only buildable through
 * {@code MissionFactory.specFromWizardValues}, which resolves the launcher and payload catalogs and
 * sizes the propellant analytically — and therefore throws. A plane alignment needs none of that:
 * six numbers, and a criterion that cannot fail. Handing the spec to a wizard widget would make the
 * timeline break for propulsion reasons that have nothing to do with it.
 *
 * <p><b>It is also a memoisation key.</b> The wizard recomputes on a polled loop and must not
 * search again while nothing has changed; a record's {@code equals} is that test, and {@link
 * LaunchPlane} being a record too, the equality is by value all the way down.
 *
 * <p><b>{@link #from} is the intended construction path.</b> The canonical constructor is for a
 * caller that genuinely only has raw coordinates to assemble; five positional {@code double}s in a
 * row is a transposition hazard nothing in the type system can reject, so reaching for the
 * constructor over a spec that already exists trades a compiler-checked read for a silent risk.
 *
 * @param latitude the launch site latitude in degrees
 * @param longitude the launch site longitude in degrees
 * @param altitude the launch site altitude in meters
 * @param plane the plane the ascent flies to
 * @param targetRaanDeg the target node, in <b>degrees</b> — the unit the wizard field carries
 * @param semiMajorAxis the target orbit's semi-major axis in meters
 */
public record EarthLaunchWindowRequest(
    double latitude,
    double longitude,
    double altitude,
    LaunchPlane plane,
    double targetRaanDeg,
    double semiMajorAxis)
    implements LaunchWindowRequest {

  public EarthLaunchWindowRequest {
    Objects.requireNonNull(plane, "plane");
  }

  /**
   * Reads a request off a configured mission.
   *
   * @param spec the mission being scheduled; it must name a target node
   * @return the request describing its window
   * @throws OrbitlabException if the mission names no target node
   */
  public static EarthLaunchWindowRequest from(MissionSpec.EarthOrbit spec) {
    if (!spec.hasTargetRaan()) {
      throw new OrbitlabException("the mission names no target node: " + spec.name());
    }
    return new EarthLaunchWindowRequest(
        spec.latitude(),
        spec.longitude(),
        spec.altitude(),
        spec.launchPlane(),
        spec.targetRaan(),
        Constants.WGS84_EARTH_EQUATORIAL_RADIUS
            + 0.5 * (spec.perigeeAltitude() + spec.apogeeAltitude()));
  }

  /**
   * @return the problem this request poses
   * @throws com.smousseur.orbitlab.core.OrbitlabException if the site cannot reach the plane
   */
  @Override
  public EarthLaunchWindowProblem toProblem() {
    return new EarthLaunchWindowProblem(
        latitude, longitude, altitude, plane, FastMath.toRadians(targetRaanDeg), semiMajorAxis);
  }
}
