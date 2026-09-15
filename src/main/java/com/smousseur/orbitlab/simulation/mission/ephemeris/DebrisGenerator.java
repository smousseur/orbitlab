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
 */
public final class DebrisGenerator {

  /** The fixed sampling step of a debris trail (s). A jettisoned booster falls in ~160-360 s. */
  private static final double SAMPLE_STEP_SECONDS = 2.0;

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
            GravitationalContext.earth().mu()),
        mass);
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
        service.createOptimizationPropagator(context, OrekitService.COAST_MAX_STEP);
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
    SpacecraftState end = propagator.propagate(start, start.shiftedBy(horizonSeconds));

    // The fixed-step handler stops short of the final flown state (the floor is hit between steps),
    // so the last sample is added explicitly; and a fall shorter than one step still owes a
    // drawable
    // two-point trajectory.
    if (points.isEmpty() || !points.get(points.size() - 1).time().equals(end.getDate())) {
      points.add(sampleOf(end, ellipsoid));
    }
    if (points.size() < 2) {
      points.add(0, sampleOf(initialState, ellipsoid));
    }
    return new MissionEphemeris(points, true);
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
