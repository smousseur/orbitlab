package com.smousseur.orbitlab.simulation.mission.ephemeris;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.Objects;
import org.orekit.propagation.SpacecraftState;

/**
 * One separation, as the replay captures it for {@link DebrisGenerator} to fly. It describes the
 * jettison as a whole — the
 * aggregate mass and section of a multi-exemplar block — and the {@code DebrisGenerator} splits it
 * into {@code multiplicity} drawn objects.
 *
 * <p>Position, velocity and date are those of the pre-jettison state (continuous across the
 * separation, since only the mass changes), carried by {@code state}. The {@code aero} and {@code
 * jettisonedMass} are the block's aggregate values, divided per exemplar downstream.
 *
 * @param state the pre-jettison state (position, velocity, date)
 * @param aero the jettisoned block's aggregate aerodynamics
 * @param jettisonedMass the total mass shed by this separation (kg)
 * @param multiplicity how many identical exemplars this separation drops (1 for a single piece)
 * @param role the role of the jettisoned piece (booster, core, upper stage)
 */
public record JettisonEvent(
    SpacecraftState state,
    AerodynamicProperties aero,
    double jettisonedMass,
    int multiplicity,
    StageRole role) {
  public JettisonEvent {
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(aero, "aero");
    Objects.requireNonNull(role, "role");
    if (multiplicity < 1) {
      throw new IllegalArgumentException("multiplicity must be at least 1: " + multiplicity);
    }
  }
}
