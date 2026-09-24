package com.smousseur.orbitlab.simulation.mission.ephemeris;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.flight.DragContext;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.AltitudeDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * The fall of an unpowered object to the ground: the recipe a jettisoned debris is flown with, and
 * the one a payload is flown with once its disposal tail has brought it down. Display only — it
 * runs after the mission is computed, never in the optimizer loop.
 *
 * <p>The propagator is the production one, drag included. The trajectory is bounded by a
 * <b>geodetic</b>-0 floor and by a duration the caller chooses, and deliberately not by {@code
 * ReentryGuard}: that guard's drag stop sits on a sphere of equatorial radius, which a fall ending
 * away from the equator crosses kilometres above the ground — measured 1.1 to 4.2 km for falls
 * ending at 13° to 26° of latitude.
 *
 * <p>Two safeguards keep a fall from failing the computation it decorates. {@link
 * #REENTRY_MAX_STEP_SECONDS} caps the integrator step, so that no single step can leap past the
 * floor into altitudes where {@code NRLMSISE00} throws; and {@link #fly} truncates gracefully on
 * any propagation failure.
 */
public final class ReentryFall {

  private static final Logger logger = LogManager.getLogger(ReentryFall.class);

  /**
   * The fixed sampling step of a fall (s). A jettisoned booster falls in ~160-360 s and a disposed
   * payload in 30 to 51 minutes; at this step a straight segment between two samples strays from
   * the payload's trajectory by 34 to 41 m at most, where 10 s strays by a kilometre and the 60 s
   * of a coast by 23 to 34 km in the final plunge.
   */
  private static final double SAMPLE_STEP_SECONDS = 2.0;

  /**
   * Integrator max step for a fall (s), well below {@link OrekitService#COAST_MAX_STEP} on purpose.
   * A booster jettisoned while still <em>climbing</em> — measured on an Ariane 64 ascent at ~63 km,
   * ~3.4 km/s, +21° flight-path angle — lets the adaptive step grow unchecked in the thin air it
   * climbs through; a single coast-sized step then evaluates the atmosphere far out of its altitude
   * range, which {@code NRLMSISE00} answers with an "Infinite value" throw <em>inside</em> the
   * force model, before the geodetic-0 floor can stop the fall. A sweep of descending states alone
   * never showed it, since their step never grew before the atmosphere thickened. A 15 s cap keeps
   * a step from spanning the atmospheric traversal — the sweep measured 300 s throwing, 60 s the
   * failure edge, and ≤30 s reaching the ground cleanly — and costs nothing on an orbital fall,
   * whose wall time is sampling bound and flat across caps.
   */
  private static final double REENTRY_MAX_STEP_SECONDS = 15.0;

  private ReentryFall() {}

  /**
   * Flies an unpowered object from {@code start} until it reaches the geodetic ground or {@code
   * boundSeconds} have passed.
   *
   * @param start the state the fall begins from
   * @param aero the object's aerodynamics, or {@code null} when it declares none — an object that
   *     declares none does not drag
   * @param atmosphere the atmosphere to fall through; {@code NONE} means a ballistic fall
   * @param boundSeconds how long to fly an object that does not reach the ground
   * @param stageName the stage name every sample carries
   * @return the fall's samples, at a fixed step and closed on the final flown state; flagged
   *     incomplete when the propagation failed before the ground or the bound
   */
  public static MissionEphemeris fly(
      SpacecraftState start,
      AerodynamicProperties aero,
      AtmosphereModel atmosphere,
      double boundSeconds,
      String stageName) {
    OrekitService service = OrekitService.get();
    DragContext drag =
        atmosphere == AtmosphereModel.NONE || aero == null
            ? null
            : new DragContext(aero, atmosphere);
    FlightContext context = new FlightContext(GravitationalContext.earth(), drag);
    OneAxisEllipsoid ellipsoid = service.getEarthEllipsoid();

    NumericalPropagator propagator =
        service.createOptimizationPropagator(context, REENTRY_MAX_STEP_SECONDS);
    propagator.setInitialState(start);

    List<MissionEphemerisPoint> points = new ArrayList<>();
    propagator
        .getMultiplexer()
        .add(SAMPLE_STEP_SECONDS, state -> points.add(sampleOf(state, ellipsoid, stageName)));
    propagator.addEventDetector(
        new AltitudeDetector(0.0, ellipsoid)
            .withMaxCheck(30.0)
            .withThreshold(1.0)
            .withHandler((s, detector, increasing) -> Action.STOP));

    AbsoluteDate startDate = start.getDate();
    boolean complete = true;
    try {
      SpacecraftState end = propagator.propagate(startDate, startDate.shiftedBy(boundSeconds));
      // The fixed-step handler stops short of the final flown state (the floor is hit between
      // steps), so the last sample is added explicitly.
      if (points.isEmpty() || !points.getLast().time().equals(end.getDate())) {
        points.add(sampleOf(end, ellipsoid, stageName));
      }
    } catch (RuntimeException e) {
      // A fall is display-only, so a numerical failure in its propagation must never fail the
      // mission computation — an NRLMSISE00 re-entry throw once climbed all the way to the
      // orchestrator and lost an otherwise-complete mission. The samples gathered up to the break
      // are kept and the fall is flagged incomplete, the same graceful truncation
      // MissionEphemerisGenerator applies to a stage that throws. REENTRY_MAX_STEP_SECONDS makes
      // this unreachable for every measured fall; this catch guards the states nobody measured.
      complete = false;
      logger.warn(
          "[{}] Fall propagation stopped early ({}); keeping the {} sample(s) gathered so far",
          stageName,
          e.getMessage(),
          points.size());
    }

    // A fall shorter than one sample still owes a drawable two-point trajectory.
    if (points.size() < 2) {
      points.addFirst(sampleOf(start, ellipsoid, stageName));
    }
    return new MissionEphemeris(points, complete);
  }

  private static MissionEphemerisPoint sampleOf(
      SpacecraftState state, OneAxisEllipsoid ellipsoid, String stageName) {
    Vector3D position = state.getPosition();
    Vector3D velocity = state.getPVCoordinates().getVelocity();
    double altitude =
        ellipsoid
            .transform(
                state.getPosition(ellipsoid.getBodyFrame()),
                ellipsoid.getBodyFrame(),
                state.getDate())
            .getAltitude();
    return new MissionEphemerisPoint(
        state.getDate(),
        position,
        velocity,
        stageName,
        false,
        state.getMass(),
        altitude,
        TrajectoryArc.forBody(SolarSystemBody.EARTH));
  }
}
