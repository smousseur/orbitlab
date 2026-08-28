package com.smousseur.orbitlab.simulation.gravity;

import com.smousseur.orbitlab.simulation.OrekitService;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.AbstractDetector;
import org.orekit.propagation.events.EventDetectionSettings;
import org.orekit.propagation.events.handlers.ContinueOnEvent;
import org.orekit.propagation.events.handlers.EventHandler;

/**
 * Detects a trajectory crossing the boundary of a {@link SphereOfInfluence}. Shape sibling of
 * {@code ReentryDetector}: one scalar switching function, no state of its own, and what to do about
 * the crossing left to the handler the caller attaches.
 *
 * <p>Introduced by PHY-4 / L4 (spec {@code docs/multi-corps/06-conception-L4.md} §2.4).
 *
 * <p><b>One class serves both directions of crossing</b>, and that is a property of the switching
 * function rather than a convenience. The position of the sphere's body is evaluated in {@code
 * state.getFrame()}, so on an Earth arc it is the Moon's geocentric position while on a lunar arc
 * it is the origin itself — the same expression measures the same distance from either side.
 * Orekit's own {@code RelativeDistanceDetector} has this exact shape; it is not reused only because
 * its threshold is a constant and ours breathes with the Earth-Moon distance (spec L4 §3.2).
 *
 * <p><b>The dead band lives here, as a scale on the radius.</b> A STOP fires at {@code g = 0}, so
 * the next leg starts <em>on</em> the sphere and a detector re-armed on the same radius sees a sign
 * decided by rounding — it can re-fire at once and produce a chain of zero-length legs. Entering is
 * therefore decided at {@code R(t)} and leaving at {@code R(t)·(1 + ε)}: the trajectory must have
 * moved a margin clear before the reverse crossing counts (spec L4 §4.4).
 */
public class SoiCrossingDetector extends AbstractDetector<SoiCrossingDetector> {

  /**
   * Hysteresis margin on the radius when leaving a sphere of influence. 1.5e-4 is about 10 km at
   * the lunar sphere's 64 500 km — some ten seconds of dwell at transfer speed, far above the metre
   * the root finder brackets to, and far below anything that would misdate the exit noticeably.
   *
   * <p>The risk is asymmetric and this value errs on the safe side deliberately: too small
   * chatters, too large only dates the exit late, and a late exit costs bookkeeping rather than
   * physics because the opposite body perturbs on both sides anyway (spec L4 §4.2).
   */
  public static final double EXIT_DEAD_BAND = 1.5e-4;

  /**
   * Date convergence of the root localisation (s). The crossing date is therefore known to about a
   * millisecond, which is about a metre at transfer speed.
   *
   * <p><b>Public because a caller has to bound the gap between two dates with it</b> (PHY-4 / L6,
   * spec {@code docs/multi-corps/08-conception-L6.md} §12): the state handed to the handler and the
   * state {@code propagate()} returns are both taken at the localised root, but re-interpolated
   * independently, so they can differ by up to this threshold. Measured 51 ps on L4's synthetic
   * fixture and <b>524 µs</b> on the first real translunar flight — an amplitude the fixture could
   * not show, and which no constant unrelated to this threshold can bound.
   */
  public static final double DATE_CONVERGENCE_SECONDS = 1.0e-3;

  private final SphereOfInfluence soi;
  private final double radiusScale;

  /**
   * Creates a detector for one crossing direction of the given sphere.
   *
   * <p>Checked every 10 s like the other detectors of this codebase, with a 1 ms date convergence:
   * unlike the re-entry guard, the caller acts on <em>where</em> the crossing is and not merely on
   * the fact of it, and 1 ms is about a metre at transfer speed.
   *
   * @param soi the sphere whose boundary is watched
   * @param radiusScale 1.0 to detect entering, {@code 1 + EXIT_DEAD_BAND} to detect leaving
   */
  public SoiCrossingDetector(SphereOfInfluence soi, double radiusScale) {
    super(10.0, DATE_CONVERGENCE_SECONDS, DEFAULT_MAX_ITER, new ContinueOnEvent());
    this.soi = soi;
    this.radiusScale = radiusScale;
  }

  /**
   * The detector watching {@code soi}'s boundary from a trajectory flying in {@code from}, with the
   * direction rule already applied: entering is decided at {@code R(t)}, leaving at {@code R(t)·(1
   * + EXIT_DEAD_BAND)}.
   *
   * <p><b>The rule lives here because two callers need it</b> (MIS-5 / L1, spec {@code
   * docs/lunar-orbit/03-conception-L1.md} §5.4). It used to sit in {@code StageLegRunner}'s private
   * arming loop, which a stage cannot reach; a translunar coast that stops at the sphere has to arm
   * the very same detector on the optimize pass, and a rule written twice is a rule free to drift
   * on the only thing that decides where the two passes stop.
   *
   * <p>No handler is attached: what to do about the crossing stays the caller's, as it is for the
   * public constructor.
   *
   * @param soi the sphere whose boundary is watched
   * @param from the gravitational context the trajectory is being flown in
   * @return the detector for that direction of crossing
   */
  public static SoiCrossingDetector crossingFrom(SphereOfInfluence soi, GravitationalContext from) {
    double scale = from.body() == soi.body() ? 1.0 + EXIT_DEAD_BAND : 1.0;
    return new SoiCrossingDetector(soi, scale);
  }

  /**
   * Copy constructor used by {@link #create}.
   *
   * <p><b>Both fields must travel through here.</b> {@link AbstractDetector} rebuilds the detector
   * through {@link #create} as soon as a handler is attached with {@code withHandler}; a field left
   * out of the copy is silently lost on that first call, and the failure shows up as a wrong
   * trajectory rather than as an error. {@code ReentryDetector} documents the same trap.
   */
  private SoiCrossingDetector(
      EventDetectionSettings settings,
      EventHandler handler,
      SphereOfInfluence soi,
      double radiusScale) {
    super(settings, handler);
    this.soi = soi;
    this.radiusScale = radiusScale;
  }

  @Override
  protected SoiCrossingDetector create(
      EventDetectionSettings detectionSettings, EventHandler newHandler) {
    return new SoiCrossingDetector(detectionSettings, newHandler, soi, radiusScale);
  }

  /** The sphere this detector watches. */
  public SphereOfInfluence sphere() {
    return soi;
  }

  /**
   * Switching function: positive outside the sphere of influence, negative inside. The root is the
   * crossing.
   */
  @Override
  public double g(SpacecraftState state) {
    Vector3D toBody =
        OrekitService.get().body(soi.body()).getPosition(state.getDate(), state.getFrame());
    double distance = state.getPosition().subtract(toBody).getNorm();
    return distance - soi.radiusAt(state.getDate()) * radiusScale;
  }
}
