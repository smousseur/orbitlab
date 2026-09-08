package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.simulation.mission.detector.DepletionStopTrigger;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.forces.maneuvers.Maneuver;
import org.orekit.forces.maneuvers.propulsion.BasicConstantThrustPropulsionModel;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * The powered phase a parallel-burn launcher inserts between its two jettisons: the core stage
 * burning alone, at the full thrust it recovers once the boosters are dropped (spec {@code
 * docs/etagement/03-conception-L1.md} §3.5).
 *
 * <p><b>Why it is a phase rather than a continuation.</b> The thrust changes at booster flame-out —
 * the block's aggregate gives way to the core's own — and a {@code ConstantThrustManeuver} does not
 * change thrust. The core-only stretch is therefore its own propagation, which is also what gives
 * the booster jettison somewhere to happen.
 *
 * <p>Structurally identical to {@link GravityTurnFirstBurnStage}'s burn: flame-out semantics on the
 * core's own depletion floor, a cutoff on a jettison date, and the pitch law anchored on the plan
 * rather than on the phase. It computes nothing: every date comes from the {@link AscentPlan} the
 * first burn published.
 */
public class GravityTurnCoreBurnStage extends GravityTurnBurnStage {

  /**
   * Creates the core-only burn phase of an ascent.
   *
   * @param name the human-readable phase name
   * @param planRef the reference the ascent phases share
   * @param instrumentation the guards this phase arms (replay or optimize)
   */
  public GravityTurnCoreBurnStage(
      String name, AscentPlanRef planRef, AscentInstrumentation instrumentation) {
    super(name, planRef, instrumentation);
  }

  @Override
  protected void configureBurn(NumericalPropagator propagator, AscentPlan plan) {
    PropulsionSystem propulsion = plan.coreStage().propulsion();
    propagator.addForceModel(
        new Maneuver(
            null,
            new DepletionStopTrigger(plan.coreIgnitionDate(), plan.coreStage().depletionFloor()),
            new BasicConstantThrustPropulsionModel(
                propulsion.thrust(), propulsion.isp(), Vector3D.PLUS_I, "GT-core")));
  }

  @Override
  protected AbsoluteDate endDate(AscentPlan plan, SpacecraftState entryState) {
    return plan.coreJettisonDate();
  }
}
