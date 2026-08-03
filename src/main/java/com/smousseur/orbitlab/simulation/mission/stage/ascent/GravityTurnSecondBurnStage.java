package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * Second powered phase of the explicit ascent: the upper stage burning from the end of the
 * interstage coast to MECO. It computes nothing — every date and duration comes from the {@link
 * AscentPlan} the first burn published, which is what makes the three phases reproduce the
 * single-propagator ascent to the millisecond (spec {@code
 * specs/mission-stages/01-separations-implicites.md} §4.3).
 *
 * <p>Configuring it without a plan fails loudly rather than flying an invented schedule.
 */
public class GravityTurnSecondBurnStage extends GravityTurnBurnStage {

  /**
   * Creates the second burn phase of an ascent.
   *
   * @param name the human-readable phase name
   * @param planRef the reference the three ascent phases share
   * @param instrumentation the guards this phase arms (replay or optimize)
   */
  public GravityTurnSecondBurnStage(
      String name, AscentPlanRef planRef, AscentInstrumentation instrumentation) {
    super(name, planRef, instrumentation);
  }

  @Override
  protected void configureBurn(NumericalPropagator propagator, AscentPlan plan) {
    if (!(plan.burn2Duration() > 0)) {
      // Degenerate schedule (MECO exactly at staging completion): no second burn to fly. Orekit
      // rejects a zero-duration maneuver, so the phase simply coasts its zero-length window.
      return;
    }
    PropulsionSystem propulsion = plan.secondStage().propulsion();
    propagator.addForceModel(
        new ConstantThrustManeuver(
            plan.secondIgnitionDate(),
            plan.burn2Duration(),
            propulsion.thrust(),
            propulsion.isp(),
            Vector3D.PLUS_I));
  }

  @Override
  protected AbsoluteDate endDate(AscentPlan plan, SpacecraftState entryState) {
    AbsoluteDate meco = plan.mecoDate();
    // Never propagate backwards: a MECO before this phase's entry can only come from a schedule
    // the staging invariant already refuses, and integrating in reverse would turn a rejected
    // candidate into an unrelated trajectory rather than a penalized one.
    return meco.durationFrom(entryState.getDate()) > 0 ? meco : entryState.getDate();
  }
}
