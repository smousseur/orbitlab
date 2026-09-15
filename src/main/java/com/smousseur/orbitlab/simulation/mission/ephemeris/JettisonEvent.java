package com.smousseur.orbitlab.simulation.mission.ephemeris;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import java.util.Objects;
import org.orekit.propagation.SpacecraftState;

/**
 * The state a jettisoned object starts its autonomous flight from, emitted by the replay when a
 * separation fires (PHY-5 / L1, spec {@code docs/multi-objets/03-conception-L1.md} §2.2).
 *
 * <p>Position, velocity and date are those of the pre-jettison state — continuous across the
 * separation, since only the mass changes — carried by {@code initialState} with the mass set to
 * the mass actually shed. {@code aero} is the jettisoned piece's own frontal section, resolved from
 * the vehicle at the pre-jettison mass; it is what the {@link DebrisGenerator} mounts the drag
 * against.
 *
 * @param initialState the debris state at separation (pre-jettison position/velocity, shed mass)
 * @param aero the jettisoned piece's aerodynamics
 */
public record JettisonEvent(SpacecraftState initialState, AerodynamicProperties aero) {
  public JettisonEvent {
    Objects.requireNonNull(initialState, "initialState");
    Objects.requireNonNull(aero, "aero");
  }
}
