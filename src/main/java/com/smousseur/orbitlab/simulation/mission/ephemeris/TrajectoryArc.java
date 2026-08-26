package com.smousseur.orbitlab.simulation.mission.ephemeris;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.time.AbsoluteDate;

/**
 * The frame one segment of a trajectory is expressed in: the body it is centred on, and the
 * inertial frame that centring is realised by.
 *
 * <p>Introduced by PHY-4 / L3 (spec {@code docs/multi-corps/05-conception-L3.md}) to make explicit
 * what {@code MissionEphemerisGenerator} already had in hand and dropped — a sample's positions are
 * expressed in {@code SpacecraftState.getFrame()}, and nothing recorded which one that was. Until
 * L4 produces a second arc, every point of every mission carries {@link #earth()}.
 *
 * <p><b>Narrower than {@link GravitationalContext} on purpose.</b> The two carry the same pair, and
 * reusing the existing record would have spared the derivation below. But <b>the equality of this
 * record is arc identity</b> — it is what {@link TrajectoryPolyline} partitions on — whereas a
 * {@code GravitationalContext}'s equality also covers {@code mu} and the perturbers, which are
 * propagation concerns. L6 will legitimately fly one lunar arc as a coast without perturbers
 * followed by a burn with them; partitioned by {@code equals}, that would fabricate an arc boundary
 * where there is none.
 *
 * @param body the body the positions of this arc are centred on
 * @param frame the inertial frame they are expressed in — GCRF for the Earth
 */
public record TrajectoryArc(SolarSystemBody body, Frame frame) {

  public TrajectoryArc {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(frame, "frame");
  }

  /**
   * The arc a stage's samples belong to, derived from what that stage declares. This is the one
   * place the pairing <em>central body ↔ frame the positions are actually in</em> is stated, which
   * matters because nothing else checks it: {@code OrekitService.createOptimizationPropagator}
   * never sets a propagation frame, so Orekit takes it from the initial orbit (spec L3 §1). An arc
   * built from the stage's own context is the closest a sample can get to saying what it flew in.
   *
   * @param context the gravitational context of the stage that produced the samples
   * @return the matching arc
   */
  public static TrajectoryArc of(GravitationalContext context) {
    Objects.requireNonNull(context, "context");
    return new TrajectoryArc(context.body(), context.inertialFrame());
  }

  /**
   * The Earth arc: GCRF, centred on the Earth. Every point of every mission carries it until L4.
   *
   * <p><b>Resolved from {@link FramesFactory} and not from {@code
   * GravitationalContext.earth()}</b>, although that would state the pairing once instead of twice.
   * Four of the five test classes that build ephemeris points never initialise {@code
   * OrekitService}, and the Earth context resolves ITRF and the WGS84 ellipsoid — both of which
   * need the data archive. GCRF does not: it is EME2000 plus a fixed frame bias. The price is a
   * second statement of the Earth pairing, and it is paid by a one-line test asserting the two
   * agree (spec L3 §2.1).
   *
   * <p>Holder pattern rather than a {@code static final} field, for the reason {@code
   * GravitationalContext} gives: the frame is built by Orekit, so resolution is deferred to first
   * read.
   *
   * @return the shared Earth arc
   */
  public static TrajectoryArc earth() {
    return Holder.EARTH;
  }

  /**
   * The arc a body's positions are expressed in, resolved from the body alone.
   *
   * <p>Needed because the render path knows which body it is drawing <em>about</em> — a {@link
   * SolarSystemBody} taken from the focus or from the sample — and has to turn that into the frame
   * the conversion targets. The frame is the very one {@code OrekitService.bodyCentredIcrfFrame}
   * hands the propagator, cached there, so an arc resolved here and an arc a stage declared are the
   * same frame instance and {@link #convertPosition} short-circuits between them.
   *
   * <p>The Earth branch goes through {@link #earth()} and therefore does <b>not</b> reach {@code
   * OrekitService}: the four test classes that build polylines without initialising it must keep
   * working (spec L3 §2.1). {@code FramesFactory.getGCRF()} is the same instance {@code
   * OrekitService.gcrf()} returns, so the two paths cannot diverge.
   *
   * @param body the body positions are centred on
   * @return the arc for that body
   */
  public static TrajectoryArc forBody(SolarSystemBody body) {
    Objects.requireNonNull(body, "body");
    return body == SolarSystemBody.EARTH
        ? earth()
        : new TrajectoryArc(body, OrekitService.get().bodyCentredIcrfFrame(body));
  }

  /**
   * A position of this arc, re-expressed in {@code target}'s frame at the given date.
   *
   * <p>Both frames are body-centred with ICRF axes, so this is a <b>pure translation</b> and the
   * conversion is exact — L4 §7.3 measured the round trip at 0 m, and its reason applies word for
   * word: a translation has nothing to round on the position. That is also why the two directions
   * compose back to the identity rather than to something merely close.
   *
   * <p>Frames are compared by <b>reference</b>, not by {@code equals}. Both come from the same
   * cache in {@code OrekitService}, so a same-body conversion returns the argument untouched
   * instead of pushing it through an identity {@code Transform} — which is what keeps a single-arc
   * trajectory bit-for-bit unconverted (spec {@code docs/multi-corps/07-conception-L5.md} §3.4, and
   * the same discipline as L4 §3.5).
   *
   * @param position a position of this arc, in metres
   * @param date the date that position was sampled at, which decides where the two centres are
   * @param target the arc to express it in
   * @return the position in {@code target}'s frame
   */
  public Vector3D convertPosition(Vector3D position, AbsoluteDate date, TrajectoryArc target) {
    Objects.requireNonNull(position, "position");
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(target, "target");
    if (frame == target.frame()) {
      return position;
    }
    return frame.getStaticTransformTo(target.frame(), date).transformPosition(position);
  }

  private static final class Holder {
    private static final TrajectoryArc EARTH =
        new TrajectoryArc(SolarSystemBody.EARTH, FramesFactory.getGCRF());
  }
}
