package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import java.util.Locale;
import java.util.Objects;
import org.hipparchus.util.FastMath;
import org.orekit.frames.Frame;

/**
 * The orbital plane a launch is asked to reach, and the azimuth that reaches it (spec {@code
 * docs/earth-orbit/01-mission-terre-parametrable.md} §3.1).
 *
 * <p><b>The user gives an inclination, not an azimuth.</b> The azimuth is a means: it depends on the
 * site latitude, two of them reach the same plane ({@link NodeBranch}), and getting it wrong is
 * silent. The inclination is the intention. This record therefore owns the derivation, and is the
 * <em>only</em> place in the code where {@code asin(cos i / cos φ)} is written.
 *
 * <p><b>Reachability is checked here, not discovered in flight</b> (spec §8). A site at latitude
 * {@code φ} reaches inclinations in {@code [φ, 180° − φ]} and nothing else, short of a plane change
 * no launcher in the catalog can pay for. {@link #requireReachableFrom} refuses the rest with the
 * reachable bound named, rather than clamping it into a mission that quietly flies something other
 * than what was asked.
 *
 * @param targetInclination the target orbit inclination, in <b>radians</b>, in {@code [0, π]}
 * @param nodeBranch which of the two azimuths reaching that inclination the launch flies
 */
public record LaunchPlane(double targetInclination, NodeBranch nodeBranch) {

  /**
   * How far the target inclination must sit from the site latitude before the ascent is flown with
   * a <em>commanded</em> plane rather than the historical free one (see {@link #commands}).
   *
   * <p>Sized on what the ascent can be held to, not on numerical noise: {@code T1} accepts 1° of
   * inclination error at MECO, so a target within a hundredth of that of the free plane is a target
   * the free plane already meets.
   */
  private static final double COMMANDED_PLANE_THRESHOLD_RAD = FastMath.toRadians(0.01);

  /** Below this, {@code cos φ} is too small for the azimuth to be defined (spec §8). */
  private static final double POLE_COSINE_EPSILON = 1.0e-10;

  public LaunchPlane {
    Objects.requireNonNull(nodeBranch, "nodeBranch");
    if (!Double.isFinite(targetInclination)
        || targetInclination < 0.0
        || targetInclination > FastMath.PI) {
      throw new OrbitlabException(
          String.format(
              Locale.ROOT,
              "Target inclination %.4f° is outside [0°, 180°]",
              FastMath.toDegrees(targetInclination)));
    }
  }

  /**
   * The plane a due-east launch reaches for free: inclination equal to the site latitude, on the
   * ascending branch. This is what every mission in the catalog has always flown, and what {@code
   * EarthOrbitMission} falls back to when no plane is asked for.
   *
   * @param launchLatitudeDeg the launch site latitude in degrees
   * @return the free plane of that site
   */
  public static LaunchPlane dueEast(double launchLatitudeDeg) {
    return new LaunchPlane(FastMath.toRadians(FastMath.abs(launchLatitudeDeg)), NodeBranch.ASCENDING);
  }

  /**
   * A plane explicitly targeted at an inclination given in degrees, on the ascending branch.
   *
   * @param inclinationDeg the target inclination in degrees
   * @return the plane
   */
  public static LaunchPlane ofDegrees(double inclinationDeg) {
    return ofDegrees(inclinationDeg, NodeBranch.ASCENDING);
  }

  /**
   * A plane explicitly targeted at an inclination given in degrees.
   *
   * @param inclinationDeg the target inclination in degrees
   * @param branch which of the two azimuths to fly
   * @return the plane
   */
  public static LaunchPlane ofDegrees(double inclinationDeg, NodeBranch branch) {
    return new LaunchPlane(FastMath.toRadians(inclinationDeg), branch);
  }

  /**
   * The frame the target inclination — and the achieved inclination it is compared against — are
   * expressed in (spec §3.4).
   *
   * <p><b>Both readings must come from the same frame, and this method is what guarantees it.</b>
   * GCRF is the frame the states are propagated in, so today's achieved inclinations are GCRF
   * inclinations by construction; declaring the target's frame here rather than leaving it implicit
   * is what makes the pair comparable. The cost of the choice is that GCRF's equator is J2000's, not
   * the date's: a launch geometry held rigorously constant sees its GCRF inclination move by up to
   * 0.29° peak-to-peak with the launch date alone, which is invisible for a LEO or a polar target
   * and worth ~1.8 % of nodal precession for a sun-synchronous one.
   *
   * @return the inclination reference frame
   */
  public static Frame inclinationFrame() {
    return OrekitService.get().gcrf();
  }

  /**
   * The launch azimuth reaching this plane from a site at the given latitude, measured
   * <b>clockwise from north</b> — 90° is due east.
   *
   * <p>This is spherical trigonometry on a non-rotating Earth: {@code sin A = cos i / cos φ}. It
   * takes no account of the Earth's rotation, which biases the true inertial heading; that bias is
   * absorbed by the commanded-plane attitude (spec §4), whose job is precisely to reach the plane
   * whatever the entrainment does to the initial heading.
   *
   * @param launchLatitude the launch site latitude in <b>radians</b>
   * @return the launch azimuth in radians, clockwise from north
   * @throws OrbitlabException if the site is too close to a pole, or the plane unreachable from it
   */
  public double launchAzimuth(double launchLatitude) {
    double cosLat = FastMath.cos(launchLatitude);
    if (FastMath.abs(cosLat) < POLE_COSINE_EPSILON) {
      throw new OrbitlabException(
          "Launch latitude too close to a pole, azimuth is undefined: "
              + FastMath.toDegrees(launchLatitude)
              + "°");
    }
    double sinAzimuth = FastMath.cos(targetInclination) / cosLat;
    if (FastMath.abs(sinAzimuth) > 1.0) {
      throw unreachable(FastMath.toDegrees(launchLatitude));
    }
    // The two extremes are written out rather than left to asin, so that the free due-east plane
    // (i = φ, hence sin A = 1 exactly) yields the very same double the pre-MIS-7 code returned as a
    // literal. A one-ulp difference there would move every calibrated trajectory in the catalog.
    double ascending;
    if (sinAzimuth == 1.0) {
      ascending = FastMath.PI / 2;
    } else if (sinAzimuth == -1.0) {
      ascending = -FastMath.PI / 2;
    } else {
      ascending = FastMath.asin(sinAzimuth);
    }
    return nodeBranch == NodeBranch.ASCENDING ? ascending : FastMath.PI - ascending;
  }

  /**
   * Whether this plane must be <em>flown to</em> rather than merely fallen into.
   *
   * <p>The distinction is the non-regression seam of spec §4.2: the commanded-plane attitude aims at
   * the target plane's prograde direction where the historical one follows the current tangential
   * velocity, and the two are not numerically identical even when the planes coincide. A target that
   * is already the site's free plane therefore keeps the historical path bit-for-bit, and the
   * calibrated due-east trajectories cannot drift.
   *
   * @param launchLatitude the launch site latitude in radians
   * @return {@code true} when the target plane differs from the site's free due-east plane
   */
  public boolean commands(double launchLatitude) {
    return FastMath.abs(targetInclination - FastMath.abs(launchLatitude))
        > COMMANDED_PLANE_THRESHOLD_RAD;
  }

  /**
   * Refuses a plane the site cannot reach (spec §8), naming the reachable bound.
   *
   * @param launchLatitudeDeg the launch site latitude in degrees
   * @return this plane, when it is reachable
   * @throws OrbitlabException if the inclination is below {@code |φ|} or above {@code 180° − |φ|}
   */
  public LaunchPlane requireReachableFrom(double launchLatitudeDeg) {
    double minimum = FastMath.abs(launchLatitudeDeg);
    double inclinationDeg = FastMath.toDegrees(targetInclination);
    if (inclinationDeg < minimum || inclinationDeg > 180.0 - minimum) {
      throw unreachable(launchLatitudeDeg);
    }
    return this;
  }

  /**
   * The target inclination in degrees, for messages and for the UI.
   *
   * @return the target inclination in degrees
   */
  public double targetInclinationDeg() {
    return FastMath.toDegrees(targetInclination);
  }

  private OrbitlabException unreachable(double launchLatitudeDeg) {
    double minimum = FastMath.abs(launchLatitudeDeg);
    return new OrbitlabException(
        String.format(
            Locale.ROOT,
            "Inclination %.3f° is unreachable from latitude %.3f°: without a plane change the site"
                + " reaches [%.3f°, %.3f°], the minimum being the latitude itself",
            FastMath.toDegrees(targetInclination),
            launchLatitudeDeg,
            minimum,
            180.0 - minimum));
  }
}
