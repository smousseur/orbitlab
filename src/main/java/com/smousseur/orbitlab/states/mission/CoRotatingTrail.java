package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import java.util.Arrays;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.FramesFactory;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * The low part of a trajectory ribbon drawn as seen from the ground while its object is in the air:
 * each low vertex carried with the Earth, from the date it was flown to the current one.
 *
 * <p>The ribbon is inertial, and near the ground the inertial path is mostly the Earth's rotation —
 * 463 m/s east at Kourou — so it trails flat behind a launcher whose nose follows the air. Seen
 * from the ground, the same path rises from the pad, tangent to that nose, since the nose follows
 * the very velocity relative to the turning Earth. But a ribbon seen from the ground and an
 * inertial one only meet at the present instant: a vertex flown at {@code t} sits, from the ground,
 * the Earth's rotation since {@code t} away from its inertial place — 84 km at the pad three
 * minutes after launch, 1 670 km an hour later. Hence the rule: the low part follows the ground
 * while the object itself is in the air, eases back to its inertial place while the object climbs
 * through the air's ceiling, and the ribbon is the inertial one above, so it never tears.
 *
 * <p>The two weights are the drawn axis's ({@link DrawnFlightAxis#inertialWeight}): the object's,
 * so the ribbon leaves the ground in step with the nose, and each vertex's own, so the ribbon stays
 * continuous along its length. A vertex is drawn at {@code r + g·(1 − w)·(r_ground − r)}, with
 * {@code g} the object's ground following and {@code w} the vertex's weight.
 *
 * <p>Only the object's current passage through the air is carried: the vertices flown since it last
 * stood above the inertial floor. An object back in the air long after leaving it — a payload
 * deorbited days after its launch — would otherwise drag its whole ascent across the globe by every
 * turn the Earth made since, 11 000 km off and a segment stretched over 1 000 km on a Falcon Heavy
 * deorbited three days after launch. The vertex that closes the previous passage is above the floor
 * and never moves, so the ribbon stays continuous where the carried part begins.
 *
 * <p>Earth-fixed coordinates are the ones the drawn axis reads its wind from: the terrestrial frame
 * of the Earth ellipsoid. Render-only: the polyline and the samples behind it are untouched.
 */
final class CoRotatingTrail {

  /** Above this distance from the geocentre no vertex can be below the inertial floor. */
  private static final double LOW_RADIUS_M =
      Constants.WGS84_EARTH_EQUATORIAL_RADIUS + DrawnFlightAxis.INERTIAL_FLOOR_M;

  private final TrajectoryPolyline trail;

  /** For each vertex, its slot among the low ones, or −1 when it is never carried. */
  private final int[] slotOf;

  /** Each low vertex, in the terrestrial frame at the date it was flown. */
  private final Vector3D[] terrestrial;

  /** Each low vertex's share of the ground: {@code 1 − w} of its own altitude. */
  private final double[] groundShare;

  /**
   * For each vertex, the first vertex of the passage through the air it belongs to: the one after
   * the last vertex, up to it, that is out of the air.
   */
  private final int[] passageStart;

  private final org.orekit.frames.Frame terrestrialFrame;
  private final org.orekit.frames.Frame inertialFrame;

  /**
   * Finds the low vertices of {@code trail} and pins each to the ground under it at its date.
   *
   * @param trail the ribbon's polyline
   */
  CoRotatingTrail(TrajectoryPolyline trail) {
    this.trail = Objects.requireNonNull(trail, "trail");
    this.inertialFrame = FramesFactory.getGCRF();
    this.slotOf = new int[trail.size()];
    this.passageStart = new int[trail.size()];
    Arrays.fill(slotOf, -1);
    Vector3D[] pinned = new Vector3D[trail.size()];
    double[] shares = new double[trail.size()];
    org.orekit.frames.Frame terrestrialFrame = null;
    int count = 0;
    int start = 0;
    for (int i = 0; i < trail.size(); i++) {
      passageStart[i] = start;
      TrajectoryArc arc = trail.arcs().get(trail.arcOf(i)).arc();
      // The arc test comes first: a trail with no Earth arc has no Earth table to read from.
      if (arc.body() != SolarSystemBody.EARTH) {
        start = i + 1;
        continue;
      }
      Vector3D position = trail.positionAt(i, SolarSystemBody.EARTH);
      if (position.getNorm() >= LOW_RADIUS_M) {
        start = i + 1;
        continue;
      }
      if (terrestrialFrame == null) {
        terrestrialFrame = OrekitService.get().itrf();
      }
      AbsoluteDate date = trail.timeAt(i);
      Vector3D onGround =
          arc.frame().getTransformTo(terrestrialFrame, date).transformPosition(position);
      double share =
          1.0 - DrawnFlightAxis.inertialWeight(altitudeOf(onGround, terrestrialFrame, date));
      if (share > 0.0) {
        slotOf[i] = count;
        pinned[count] = onGround;
        shares[count] = share;
        count++;
      } else {
        start = i + 1;
      }
    }
    this.terrestrialFrame = terrestrialFrame;
    this.terrestrial = Arrays.copyOf(pinned, count);
    this.groundShare = Arrays.copyOf(shares, count);
  }

  /**
   * How much an object's ribbon follows the ground: 1 in the air, 0 above the inertial floor, the
   * complement of the drawn axis's weight in between. Only about the Earth: elsewhere there is no
   * air to follow.
   *
   * @param object the object's sample now
   * @return the ground following, in [0, 1]
   */
  static double groundFollowing(MissionEphemerisPoint object) {
    if (object.arc().body() != SolarSystemBody.EARTH) {
      return 0.0;
    }
    return 1.0 - DrawnFlightAxis.inertialWeight(object.altitudeMeters());
  }

  /**
   * The displacements of this frame, for the passage through the air the object is in at {@code
   * now}.
   *
   * @param now the current simulation date, the one the low vertices are carried to
   * @param groundFollowing the object's ground following, from {@link #groundFollowing}
   * @return the displacements, or {@code null} when nothing moves — no low vertex, or an object out
   *     of the air
   */
  Frame at(AbsoluteDate now, double groundFollowing) {
    if (terrestrial.length == 0 || groundFollowing <= 0.0) {
      return null;
    }
    Rotation toTerrestrial = inertialFrame.getTransformTo(terrestrialFrame, now).getRotation();
    return new Frame(toTerrestrial, groundFollowing, passageStart[trail.indexUpTo(now)]);
  }

  private static double altitudeOf(
      Vector3D onGround, org.orekit.frames.Frame terrestrialFrame, AbsoluteDate date) {
    OneAxisEllipsoid earth = OrekitService.get().getEarthEllipsoid();
    return earth.transform(onGround, terrestrialFrame, date).getAltitude();
  }

  /** The displacements of the low vertices at one date. */
  final class Frame {

    private final Rotation toTerrestrial;
    private final double groundFollowing;
    private final int firstCarried;

    private Frame(Rotation toTerrestrial, double groundFollowing, int firstCarried) {
      this.toTerrestrial = toTerrestrial;
      this.groundFollowing = groundFollowing;
      this.firstCarried = firstCarried;
    }

    /**
     * How far vertex {@code index} is moved from its inertial place. A vector, so it applies
     * unchanged in any render body's frame, all of them ICRF-oriented.
     *
     * @param index a vertex of the trail
     * @return the displacement in metres; {@link Vector3D#ZERO} for a vertex not carried — out of
     *     the air, or flown in an earlier passage through it
     */
    Vector3D displacementAt(int index) {
      int slot = slotOf[index];
      if (slot < 0 || index < firstCarried) {
        return Vector3D.ZERO;
      }
      Vector3D carried = toTerrestrial.applyInverseTo(terrestrial[slot]);
      Vector3D inertial = trail.positionAt(index, SolarSystemBody.EARTH);
      return carried.subtract(inertial).scalarMultiply(groundFollowing * groundShare[slot]);
    }
  }
}
