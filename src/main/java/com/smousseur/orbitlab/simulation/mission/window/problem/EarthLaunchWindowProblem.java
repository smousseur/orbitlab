package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowCandidate;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowProblem;
import java.time.Duration;
import java.util.Locale;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * The Earth problem, and the first one that has a launch window rather than a ranking: what it
 * would cost to reach a target orbital plane from a fixed ground site at a given instant.
 *
 * <p><b>The whole criterion is the RAAN.</b> A site at latitude φ launching at the azimuth {@link
 * LaunchPlane} derives reaches the target <em>inclination</em> at every instant of the day — that
 * is what the azimuth is for — and the plane it reaches differs from the target only by its right
 * ascension of the ascending node, which sweeps a full turn per sidereal day as the Earth carries
 * the pad round. With both inclinations equal the angle between the two planes follows {@code cos θ
 * = cos²i + sin²i·cos ΔΩ}, and the cost of the residual is the impulsive plane change {@code
 * 2·v·sin(θ/2)}.
 *
 * <p><b>What that shape implies, and it is the opposite of the translunar problem's.</b> Near
 * alignment {@code θ ≈ sin(i)·ΔΩ}, so the cost climbs at {@code v·sin(i)·ω⊕} — 0.438 m/s per second
 * of the clock for a 400 km orbit at 51.6°. A 50 m/s margin is therefore worth ±116 s: <b>a window
 * measured at 3 min 52 s, once per sidereal day</b>, against a cost measured at 12 020 m/s half a
 * day later, which is the full {@code 2·v·sin(i)}. This criterion has the relief the Earth-Moon one
 * lacks — 12 km/s of it against 14 m/s — and it is the one the wizard's launch date exists for.
 *
 * <p><b>Once per sidereal day and not twice</b>, because {@link
 * com.smousseur.orbitlab.simulation.mission.operation.NodeBranch} is fixed by the mission: the site
 * passes through the target plane twice a day, but the two crossings are served by the two
 * azimuths, and a mission flies one of them. Both branches are two searches, not one.
 *
 * <p><b>The cost floor is not zero, and that is physical rather than an artefact.</b> The azimuth
 * comes from the site's <em>geodetic</em> latitude, by spherical trigonometry on a non-rotating
 * Earth ({@code LaunchPlane.launchAzimuth}), while the plane is raised on the site's actual
 * inertial position, whose geocentric latitude is lower. The plane reached is therefore inclined a
 * fraction of a degree away from the target, and no instant of the day closes that gap: the cost at
 * the optimum is a floor, constant in time, <b>measured at 1.1 m/s from Kourou and 35.9 m/s from a
 * 45° pad</b> — nil at the equator, largest at mid-latitude, exactly as the difference between the
 * two latitude conventions is. It shifts the curve without moving its minimum, which is exactly why
 * the acceptance threshold of {@link
 * com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSearch#margin()} is relative: an
 * absolute budget would have to be told about this floor, and told again for every pad.
 *
 * <p><b>The target plane does not move, and a caller must know what that costs.</b> A RAAN handed
 * to this problem is a number, not an orbit: the plane it describes is fixed in {@link
 * LaunchPlane#inclinationFrame()} and stays where it was put. A plane that is really in orbit does
 * not — the secular J2 rate {@code dΩ/dt = −3/2·J2·(Re/p)²·n·cos i} regresses the node of a 400 km,
 * 51.6° orbit by <b>5.00°/day</b>. Over the 26 hours a planner searches, that is 5.4° of node,
 * hence 4.25° between the planes, hence some <b>570 m/s</b> — ten times the margin that defines the
 * window. So the field means <em>the plane as it will be at lift-off</em>, and a RAAN read off a
 * real target hours earlier is a snapshot that has already expired.
 *
 * <p>Following a target that precesses is a second implementation of this interface whose normal is
 * a function of the epoch rather than a constant — a handful of lines, and MIS-6's business: the
 * roadmap's dependency graph makes a TLE ephemeris source an input to the rendezvous, not to the
 * launch window.
 *
 * <p><b>Nothing here refuses, and nothing here confirms.</b> An inclination the site cannot reach
 * is refused at construction, where the caller still knows what it asked for, so every epoch is
 * flyable and only the price changes; and the closed form <em>is</em> the truth for a plane
 * alignment, so {@link #confirm} stays the default. This problem is the interface's easy case, and
 * it is worth noticing that it is also the one the product needs.
 */
public class EarthLaunchWindowProblem implements LaunchWindowProblem {

  /**
   * Sweep step. One alignment per sidereal day makes a smooth criterion an hourly sweep samples
   * twenty-four times per period — the minimum only has to be bracketed, not resolved.
   */
  private static final Duration COARSE_STEP = Duration.ofHours(1);

  /**
   * Refinement resolution. The window is minutes wide where the sweep step is an hour, and a launch
   * instant is a decision to the second; the tenth-of-a-step default would ask for six minutes and
   * quietly return a slot wider than the one it was looking for.
   */
  private static final Duration PRECISION = Duration.ofSeconds(1);

  /**
   * Recurrence. The pad meets a fixed plane once per <b>sidereal</b> day, the criterion following
   * the Earth's inertial rotation rather than the solar day. Derived from the rotation rate rather
   * than written out, so the 86 164 s of the measurement has a single source.
   */
  private static final Duration RECURRENCE =
      Duration.ofMillis(
          Math.round(2.0 * FastMath.PI / Constants.WGS84_EARTH_ANGULAR_VELOCITY * 1000.0));

  private final LaunchSitePlane site;
  private final Vector3D targetNormal;
  private final double orbitalVelocity;
  private final String name;

  /**
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   * @param plane the plane the ascent flies to: the target inclination and the node branch
   * @param targetRaan the right ascension of the target's ascending node, in <b>radians</b>, in the
   *     frame {@link LaunchPlane#inclinationFrame()} declares
   * @param semiMajorAxis the target orbit's semi-major axis in meters, which sets the speed the
   *     plane change is paid at
   * @throws com.smousseur.orbitlab.core.OrbitlabException if the site cannot reach the plane
   */
  public EarthLaunchWindowProblem(
      double latitude,
      double longitude,
      double altitude,
      LaunchPlane plane,
      double targetRaan,
      double semiMajorAxis) {
    plane.requireReachableFrom(latitude);
    this.site =
        new LaunchSitePlane(
            latitude, longitude, altitude, plane.launchAzimuth(FastMath.toRadians(latitude)));
    this.targetNormal = planeNormal(plane.targetInclination(), targetRaan);
    this.orbitalVelocity = FastMath.sqrt(Constants.WGS84_EARTH_MU / semiMajorAxis);
    this.name =
        String.format(
            Locale.ROOT,
            "Earth launch (i %.2f°, RAAN %.2f°)",
            plane.targetInclinationDeg(),
            FastMath.toDegrees(targetRaan));
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Duration coarseStep() {
    return COARSE_STEP;
  }

  @Override
  public Duration refinementPrecision() {
    return PRECISION;
  }

  @Override
  public Duration recurrence() {
    return RECURRENCE;
  }

  @Override
  public LaunchWindowCandidate evaluate(AbsoluteDate epoch) {
    double residual = Vector3D.angle(reachablePlaneNormal(epoch), targetNormal);
    return LaunchWindowCandidate.of(
        epoch, 2.0 * orbitalVelocity * FastMath.sin(0.5 * residual));
  }

  /**
   * The unit normal of the plane the site reaches at {@code epoch}, in GCRF.
   *
   * <p>Delegated to {@link LaunchSitePlane}, which the lunar problem raises its departure plane on
   * as well: the frame chain and the topocentric basis are asked for in one place rather than two
   * (MIS-4 / L2 §2.6). Kept here as a method because it is what the tests of this problem build
   * their target planes from — an alignment is defined as "the plane the pad reaches at that
   * instant" — so it is part of what they guard.
   */
  Vector3D reachablePlaneNormal(AbsoluteDate epoch) {
    return site.normalAt(epoch);
  }

  /**
   * The unit angular momentum of an orbit of given inclination and node — the standard {@code (sin
   * i·sin Ω, −sin i·cos Ω, cos i)}, written once here so the target and the reachable plane are
   * compared as two normals rather than as two pairs of angles.
   */
  static Vector3D planeNormal(double inclination, double raan) {
    double sinInclination = FastMath.sin(inclination);
    return new Vector3D(
        sinInclination * FastMath.sin(raan),
        -sinInclination * FastMath.cos(raan),
        FastMath.cos(inclination));
  }
}
