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
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.AltitudeDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

/**
 * Turns {@link JettisonEvent}s into {@link DebrisTrack}s (PHY-5, spec {@code
 * docs/multi-objets/04-conception-L2.md} §2.3). A block of {@code multiplicity} exemplars becomes
 * that many tracks, each with its share of the mass and section, its own {@link SeparationImpulse},
 * and its own fall. Runs only on the replay pass, never in the optimizer loop.
 *
 * <p>The propagator is the production one, drag included (L0 §5.2 measured the reentry cost bounded
 * and the tolerance nearly irrelevant, so no display-only propagator is worth its code). The
 * trajectory is bounded by two of D5's stops — a geodetic-0 floor and the mission horizon — and
 * never by the broken {@code ReentryGuard}.
 *
 * <p>Two safeguards were added after {@code docs/bugs.md} BUG-27, when the geodetic-0 floor alone
 * proved insufficient for a booster that separates while climbing: {@link
 * #REENTRY_MAX_STEP_SECONDS} caps the integrator step so no single step can leap past the floor
 * into the altitudes where {@code NRLMSISE00} throws, and {@link #propagate} truncates gracefully
 * on any propagation failure — a display-only trail must never fail the mission computation.
 */
public final class DebrisGenerator {

  private static final Logger logger = LogManager.getLogger(DebrisGenerator.class);

  /** The fixed sampling step of a debris trail (s). A jettisoned booster falls in ~160-360 s. */
  private static final double SAMPLE_STEP_SECONDS = 2.0;

  /**
   * Integrator max step for a debris propagation (s), well below {@link
   * OrekitService#COAST_MAX_STEP} on purpose. A booster jettisoned while still <em>climbing</em> —
   * measured on an Ariane 64 ascent at ~63 km, ~3.4 km/s, +21° flight-path angle — lets the
   * adaptive step grow unchecked in the thin air it climbs through; a single coast-sized step then
   * evaluates the atmosphere far out of its altitude range, which {@code NRLMSISE00} answers with
   * an "Infinite value" throw <em>inside</em> the force model, before the geodetic-0 floor detector
   * can stop the fall ({@code docs/bugs.md} BUG-27). This is the regime the L0 §5.2 sweep missed:
   * every state it flew was descending, so the step never grew before the atmosphere thickened. A
   * 15 s cap keeps a step from spanning the atmospheric traversal — the sweep measured 300 s
   * throwing, 60 s the failure edge, and ≤30 s reaching the ground cleanly — and costs nothing on
   * an orbital debris, whose wall time is sampling bound and flat across caps.
   */
  private static final double REENTRY_MAX_STEP_SECONDS = 15.0;

  /**
   * The stage name every debris sample carries, so a renderer can shade the trail as non-thrust.
   */
  private static final String DEBRIS_STAGE_NAME = "Debris";

  /**
   * Propagates each jettisoned object under drag until it reaches the ground or the horizon.
   *
   * @param events the separation events to fly, in order
   * @param atmosphere the atmosphere the mission flew against; {@code NONE} means the debris fly
   *     ballistically (no drag)
   * @param horizonSeconds how long to propagate a debris that does not reach the floor
   * @return the tracks, one per jettisoned exemplar
   */
  public List<DebrisTrack> generate(
      List<JettisonEvent> events, AtmosphereModel atmosphere, double horizonSeconds) {
    List<DebrisTrack> tracks = new ArrayList<>();
    for (JettisonEvent event : events) {
      int multiplicity = event.multiplicity();
      double exemplarMass = event.jettisonedMass() / multiplicity;
      AerodynamicProperties exemplarAero =
          new AerodynamicProperties(
              event.aero().crossSection() / multiplicity, event.aero().dragCoefficient());
      for (int index = 1; index <= multiplicity; index++) {
        SpacecraftState initialState =
            exemplarInitialState(event.state(), exemplarMass, index, multiplicity);
        MissionEphemeris ephemeris =
            propagate(initialState, exemplarAero, atmosphere, horizonSeconds);
        tracks.add(new DebrisTrack(ephemeris, event.role(), index));
      }
    }
    return List.copyOf(tracks);
  }

  /**
   * The initial state of one exemplar: the pre-jettison position and date, its share of the mass,
   * and the separation velocity plus its own {@link SeparationImpulse}.
   */
  private static SpacecraftState exemplarInitialState(
      SpacecraftState preJettison, double mass, int index, int multiplicity) {
    PVCoordinates pv = preJettison.getPVCoordinates();
    Vector3D velocity = pv.getVelocity();
    Vector3D position = pv.getPosition();
    Vector3D kicked = velocity.add(SeparationImpulse.of(velocity, position, index, multiplicity));
    return new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(position, kicked),
                preJettison.getFrame(),
                preJettison.getDate(),
                GravitationalContext.earth().mu()))
        .withMass(mass);
  }

  private MissionEphemeris propagate(
      SpacecraftState initialState,
      AerodynamicProperties aero,
      AtmosphereModel atmosphere,
      double horizonSeconds) {
    OrekitService service = OrekitService.get();
    DragContext drag =
        atmosphere == AtmosphereModel.NONE ? null : new DragContext(aero, atmosphere);
    FlightContext context = new FlightContext(GravitationalContext.earth(), drag);
    OneAxisEllipsoid ellipsoid = service.getEarthEllipsoid();

    NumericalPropagator propagator =
        service.createOptimizationPropagator(context, REENTRY_MAX_STEP_SECONDS);
    propagator.setInitialState(initialState);

    List<MissionEphemerisPoint> points = new ArrayList<>();
    propagator
        .getMultiplexer()
        .add(SAMPLE_STEP_SECONDS, state -> points.add(sampleOf(state, ellipsoid)));
    propagator.addEventDetector(
        new AltitudeDetector(0.0, ellipsoid)
            .withMaxCheck(30.0)
            .withThreshold(1.0)
            .withHandler((s, detector, increasing) -> Action.STOP));

    AbsoluteDate start = initialState.getDate();
    boolean complete = true;
    try {
      SpacecraftState end = propagator.propagate(start, start.shiftedBy(horizonSeconds));
      // The fixed-step handler stops short of the final flown state (the floor is hit between
      // steps), so the last sample is added explicitly.
      if (points.isEmpty() || !points.getLast().time().equals(end.getDate())) {
        points.add(sampleOf(end, ellipsoid));
      }
    } catch (RuntimeException e) {
      // Defence in depth: a debris trail is display-only, so a numerical failure in its propagation
      // must never fail the mission computation — which is exactly what happened before, an
      // NRLMSISE00 re-entry throw climbing all the way to MissionOrchestratorAppState and losing an
      // otherwise-complete mission. The samples gathered up to the break are kept and the trail is
      // flagged incomplete, the same graceful truncation MissionEphemerisGenerator applies to a
      // stage that throws. REENTRY_MAX_STEP_SECONDS makes this unreachable for a measured launcher
      // debris; this catch guards the states it did not measure.
      complete = false;
      logger.warn(
          "Debris propagation stopped early ({}); drawing the {} sample(s) gathered so far",
          e.getMessage(),
          points.size());
    }

    // A fall shorter than one sample still owes a drawable two-point trajectory.
    if (points.size() < 2) {
      points.addFirst(sampleOf(initialState, ellipsoid));
    }
    return new MissionEphemeris(points, complete);
  }

  private static MissionEphemerisPoint sampleOf(SpacecraftState state, OneAxisEllipsoid ellipsoid) {
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
        DEBRIS_STAGE_NAME,
        false,
        state.getMass(),
        altitude,
        TrajectoryArc.forBody(SolarSystemBody.EARTH));
  }
}
