package com.smousseur.orbitlab.simulation.mission.ephemeris;

import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import java.util.ArrayList;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.PVCoordinates;

/**
 * Turns {@link JettisonEvent}s into {@link DebrisTrack}s. A block of {@code multiplicity} exemplars
 * becomes that many tracks, each with its share of the mass and section, its own {@link
 * SeparationImpulse}, and its own fall. Runs only on the replay pass, never in the optimizer loop.
 *
 * <p>Each fall is flown by {@link ReentryFall} — the recipe a disposed payload falls with too —
 * bounded by the mission horizon, so that a debris that stays in orbit is drawn for as long as the
 * mission is.
 */
public final class DebrisGenerator {

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
            ReentryFall.fly(
                initialState, exemplarAero, atmosphere, horizonSeconds, DEBRIS_STAGE_NAME);
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
}
