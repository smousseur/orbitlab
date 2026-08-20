package com.smousseur.orbitlab.simulation.flight;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import java.util.Objects;

/**
 * The aerodynamic half of a {@link FlightContext}: what the vehicle presents to the flow, and which
 * atmosphere it is presented to.
 *
 * <p>Introduced by PHY-1 / L1 (spec {@code docs/atmosphere/04-conception-L1.md} §2). Together with
 * {@link com.smousseur.orbitlab.simulation.gravity.GravitationalContext} it is exactly the force
 * list of a propagator: this pair is what Orekit's {@code DragForce(Atmosphere, DragSensitive)}
 * asks for, in the same two parts.
 *
 * <p><b>It holds the model, not a built {@code Atmosphere}</b>, and that is what makes it portable
 * across a sphere-of-influence boundary: an atmosphere is built against a body shape, so an
 * already-built one would be a <em>terrestrial</em> atmosphere and applying it around the Moon
 * would be silently wrong. The resolution happens at propagator construction, where the central
 * body is known (spec §1.2).
 *
 * @param aero the frontal area and drag coefficient of the hardware actually flying
 * @param model the atmosphere the drag is computed against; never {@link AtmosphereModel#NONE}
 */
public record DragContext(AerodynamicProperties aero, AtmosphereModel model) {

  /**
   * Validates both halves, and <b>rejects {@link AtmosphereModel#NONE}</b>.
   *
   * <p>Same shape, and same reason, as {@code GravitationalContext} rejecting the central body from
   * its own perturber set: "no drag" must have exactly <em>one</em> representation — a null {@link
   * FlightContext#drag()} — instead of two that some later reader would have to keep in agreement.
   * Accepting {@code NONE} here would mean a context that exists, carries a cross-section, and
   * mounts nothing; every site reading it would then have to test both.
   */
  public DragContext {
    Objects.requireNonNull(aero, "aero");
    Objects.requireNonNull(model, "model");
    if (model == AtmosphereModel.NONE) {
      throw new IllegalArgumentException(
          "NONE is the absence of a DragContext, not a DragContext");
    }
  }
}
