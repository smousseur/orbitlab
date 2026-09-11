package com.smousseur.orbitlab.simulation.mission.detector;

import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.orekit.forces.drag.DragForce;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;

/**
 * Fail-fast re-entry guard (spec {@code docs/mission-stages/03-garde-rentree.md}). Arms a {@link
 * ReentryDetector} that stops a propagation whose trajectory has sunk irrecoverably below the
 * Earth's surface. Under drag it also arms a second, shallower and descent-gated stop the deep
 * floor cannot serve (PHY-2 / L1, {@link #armDragStop}).
 *
 * <p><b>The defect class it closes.</b> Nothing in the mission phase chain used to stop a numerical
 * propagation that re-enters. The integrator follows the trajectory under the surface, the adaptive
 * step collapses as {@code r → 0}, and the propagation never returns. Observed on 2026-08-04 on the
 * I7 GEO multi-stage sweep at {@code λ(S1) = 0.3}: the depletion guard stopped the gravity turn on
 * the stack's dry mass, the parking insertion planned two burns it could not fly, and the phase
 * went on to propagate 2 666 s of pure ballistics from 36 km at 7 603 m/s. The evaluation hung for
 * four hours instead of reporting an infeasible λ in seconds. {@code
 * AnalyticParkingInsertionStage.requireDeliverable} closed that one instance; this guard closes the
 * class — any phase can produce a re-entry, and this stops all of them.
 *
 * <p><b>It only has to stop.</b> The verdict machinery downstream is already in place: an early
 * STOP shows up as {@code StageChainRunner.StageRun.shortfallSeconds() > 0}, which makes {@code
 * MissionEphemerisGenerator} mark the trajectory incomplete, which {@code MissionLoadEvaluator}
 * reads as infeasible.
 */
public final class ReentryGuard {
  private static final Logger logger = LogManager.getLogger(ReentryGuard.class);

  /**
   * Spherical altitude (m, relative to the WGS84 <em>equatorial</em> radius) below which a
   * trajectory is declared re-entered. Deliberately deep, for two independent reasons.
   *
   * <p><b>1. A floor at 0 m would fire on the launch pad.</b> The switching function is spherical
   * and referenced to the equatorial radius, while the Earth is 21.4 km oblate ({@code Re − Rp = 6
   * 378 137 − 6 356 752}). The spherical altitude of a launch site is therefore structurally
   * negative before liftoff — −0.16 km at Kourou, −4.9 km at Canaveral, −11.1 km at Baikonur, −17.0
   * km at Plesetsk. The trap is not the ascent climbing away from a naive floor; it is that the pad
   * already sits underneath it. At −50 km every terrestrial launch site clears the floor by at
   * least 33 km, so ascent phases need no exclusion, no radial-velocity test and no activation date
   * — {@code g} is positive and increasing from {@code t = 0}.
   *
   * <p><b>2. The depth costs nothing.</b> The point is not to date the re-entry, it is to stop
   * before the step control collapses — and that happens at {@code r → 0}, 6 328 km further down.
   * Falling from 0 to −50 km at 7.6 km/s costs a few seconds of propagation; the observed
   * alternative was 2 666 s and then forever.
   */
  public static final double SUBSURFACE_FLOOR = -50_000.0;

  /**
   * Spherical altitude (m) below which a <em>descending</em> trajectory is declared re-entered when
   * drag is mounted (PHY-2 / L1, spec {@code docs/atmosphere/08-conception-L1-PHY-2.md} §3.1).
   *
   * <p><b>Why it can be this shallow when {@link #SUBSURFACE_FLOOR} could not.</b> Under drag the
   * integrator's step control collapses <em>above</em> the deepest launch pad — measured at −9 to
   * −30 km ({@code docs/bugs.md} BUG-10) against pads as deep as −17 km (Plesetsk) — so a single
   * unconditional spherical floor cannot be both above the collapse and below every pad. The drag
   * stop breaks the tie with a radial-velocity gate: the handler stops only a <em>descending</em>
   * crossing (see {@link #armDragStop}), so a climbing ascent passes through this floor untouched
   * and only a genuine re-entry is caught, 9 km above where the integrator would otherwise cede. A
   * valid orbit whose perigee sits at or above the reference sphere never crosses it.
   */
  public static final double DRAG_REENTRY_FLOOR = 0.0;

  private ReentryGuard() {}

  /**
   * Arms the guard on the given propagator, logging a warning when it fires. Use on the ephemeris /
   * replay pass, where a re-entry means the trajectory being delivered is broken and the operator
   * should see why it came out truncated.
   *
   * <p><b>Why WARN and not ERROR</b>, unlike {@link DepletionGuard#arm}: that guard firing means
   * the upstream mass accounting is wrong, i.e. a bug. A re-entry does not — it is a physically
   * infeasible candidate, a legitimate verdict the feasibility machinery knows how to read. Logging
   * it at ERROR would drown the level that does mean "bug".
   *
   * @param propagator the propagator to guard
   * @param context short label for the log (stage or maneuver name)
   */
  public static void arm(
      NumericalPropagator propagator, String context, GravitationalContext body) {
    double radius = body.equatorialRadius();
    propagator.addEventDetector(
        new ReentryDetector(radius)
            .withHandler(
                (state, detector, increasing) -> {
                  logger.warn(
                      "[{}] Trajectory re-entered at {} ({} km below the reference sphere, floor "
                          + "{} km): stopping propagation, this phase is truncated",
                      context,
                      state.getDate(),
                      Math.round(
                          (radius - state.getPVCoordinates().getPosition().getNorm()) / 1000.0),
                      Math.round(-SUBSURFACE_FLOOR / 1000.0));
                  return Action.STOP;
                }));
    armDragStop(propagator, radius, context);
  }

  /**
   * Arms the guard without the log. Use on optimization propagations and on the plan-solving
   * propagations phases run internally, where infeasible candidates legitimately re-enter: the
   * truncation itself penalizes them through the cost function, and a line per candidate would
   * flood the output. Mirrors {@link DepletionGuard#armQuiet}.
   *
   * @param propagator the propagator to guard
   */
  public static void armQuiet(NumericalPropagator propagator, GravitationalContext body) {
    double radius = body.equatorialRadius();
    propagator.addEventDetector(
        new ReentryDetector(radius).withHandler((state, detector, increasing) -> Action.STOP));
    armDragStop(propagator, radius, null);
  }

  /**
   * Arms the drag-regime re-entry stop — but only when the propagator carries drag, so a drag-off
   * propagation is byte-identical to before this method existed. PHY-2 / L1 (spec {@code
   * docs/atmosphere/08-conception-L1-PHY-2.md} §3.1).
   *
   * <p><b>The drag is read from the force list already mounted, not passed in.</b> The propagator
   * is built with its {@link DragForce} (or none) before it is armed, so its own force models are
   * the true, un-driftable answer to "is this flown under drag?" — which is why {@code arm}/{@code
   * armQuiet} keep their gravity-only signatures and no call site has to thread a flight context.
   *
   * <p><b>The stop is gated on a descending radial velocity</b>, and that is what lets its floor
   * sit at {@link #DRAG_REENTRY_FLOOR} (0 km) rather than deep like {@link #SUBSURFACE_FLOOR}: a
   * climbing ascent crosses 0 km on the way up ({@code vRadial ≥ 0}) and is waved through, while a
   * re-entry crosses it coming down ({@code vRadial < 0}) and is stopped — above the depth at which
   * the integrator would otherwise cede under drag.
   *
   * @param propagator the propagator being armed
   * @param radius the reference sphere radius (m)
   * @param context the stage/maneuver label to log on a loud stop, or {@code null} to stop quietly
   */
  private static void armDragStop(NumericalPropagator propagator, double radius, String context) {
    if (!hasDrag(propagator)) {
      return;
    }
    propagator.addEventDetector(
        new ReentryDetector(radius, DRAG_REENTRY_FLOOR)
            .withHandler(
                (state, detector, increasing) -> {
                  if (!descending(state)) {
                    return Action.CONTINUE;
                  }
                  if (context != null) {
                    logger.warn(
                        "[{}] Trajectory re-entered under drag at {} ({} km below the reference "
                            + "sphere): stopping propagation, this phase is truncated",
                        context,
                        state.getDate(),
                        Math.round(
                            (radius - state.getPVCoordinates().getPosition().getNorm()) / 1000.0));
                  }
                  return Action.STOP;
                }));
  }

  /** Whether the propagator mounts a {@link DragForce}, i.e. is flown against an atmosphere. */
  private static boolean hasDrag(NumericalPropagator propagator) {
    return propagator.getAllForceModels().stream().anyMatch(DragForce.class::isInstance);
  }

  /** Whether the state's radial velocity points inward, i.e. the trajectory is falling. */
  private static boolean descending(SpacecraftState state) {
    Vector3D position = state.getPVCoordinates().getPosition();
    Vector3D velocity = state.getPVCoordinates().getVelocity();
    return Vector3D.dotProduct(velocity, position.normalize()) < 0.0;
  }
}
