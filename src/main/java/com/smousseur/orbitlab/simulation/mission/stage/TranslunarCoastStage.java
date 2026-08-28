package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.gravity.SoiCrossingDetector;
import com.smousseur.orbitlab.simulation.gravity.SphereOfInfluence;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryGuard;
import java.util.Set;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;

/**
 * The ballistic coast of a translunar transfer: a {@link CoastingStage} that watches the lunar
 * sphere of influence, and that either changes central body at it or <b>ends</b> there.
 *
 * <p><b>The declaration cannot come from the mission.</b> L4 §3.1 deliberately refused to derive
 * {@code soiTransitions} from the perturber set — doing so would arm a detector on a GEO mission
 * that only wanted the lunar perturbation, and make {@code withPerturbers(MOON)} say two things at
 * once. So a stage has to carry it, and this is the first production stage in the repository to.
 *
 * <p><b>Two shapes, told apart by whether the coast is bounded</b> (MIS-5 / L1, spec {@code
 * docs/lunar-orbit/03-conception-L1.md} §5.1):
 *
 * <ul>
 *   <li>{@link #TranslunarCoastStage(String)} — MIS-4's <em>terminal</em> coast. Open-ended,
 *       bounded by the mission's restitution horizon because it is the last stage of the chain, and
 *       cut into legs at the sphere: the flyby goes out, past the Moon, and back, producing {@code
 *       [EARTH, MOON, EARTH]}.
 *   <li>{@link #TranslunarCoastStage(String, double)} — MIS-5's <em>approach</em> coast. Bounded,
 *       and therefore one that ends at the sphere so that the stages after it can fly
 *       selenocentrically.
 * </ul>
 *
 * <p>There is no boolean at the call site: a bound is exactly what a stage ending at a boundary
 * owes (it has to say how far it goes if the boundary never comes), so carrying one <em>is</em> the
 * declaration.
 *
 * <p><b>The bounded form overrides {@code propagateStandalone}, and the guard on it is
 * structural.</b> Without the guard MIS-4's coast would fly three days on the optimize pass too,
 * and the damage would land nowhere near the trajectory: {@code MissionOptimizer} reads {@code
 * getCurrentState()} after the stage walk to resolve the restitution horizon, so a coast that
 * advanced the walk by 3.07 d would shorten the recorded flight from 7 d to 3.95 d (spec §1.2 pt
 * 2).
 *
 * <p><b>The state it returns is on the Earth side of the boundary, unconverted</b> — exactly what
 * {@code StageLegRunner} returns, whose last leg carries the outgoing context. The two passes
 * therefore hand the next stage the same thing. On the ephemeris pass {@code ArcTransition.convert}
 * at the head of {@code fly} converts it; on the optimize pass nothing does, and the selenocentric
 * stage that follows has to convert it itself (spec §8 pt 1).
 */
public class TranslunarCoastStage extends CoastingStage {

  /** Whether crossing the lunar sphere ends this coast, rather than merely cutting it in legs. */
  private final boolean endsAtTheSphere;

  /**
   * Creates an open-ended translunar coast, whose duration comes from the mission's restitution
   * horizon because it is the last stage of the chain, and which is cut into legs at the sphere.
   *
   * @param name the human-readable name of this stage
   */
  public TranslunarCoastStage(String name) {
    super(name, null);
    this.endsAtTheSphere = false;
  }

  /**
   * Creates a bounded translunar coast, which ends at the lunar sphere of influence.
   *
   * @param name the human-readable name of this stage
   * @param boundSeconds how far the coast goes if the sphere is never reached, in seconds — a bound
   *     and not a duration: the coast is expected to end well before it
   */
  public TranslunarCoastStage(String name, double boundSeconds) {
    super(name, boundSeconds);
    this.endsAtTheSphere = true;
  }

  @Override
  public Set<SolarSystemBody> soiTransitions(Mission mission) {
    return Set.of(SolarSystemBody.MOON);
  }

  @Override
  public boolean soiCrossingEndsStage(Mission mission) {
    return endsAtTheSphere;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The open-ended form keeps the inherited no-op, which is what leaves MIS-4's stage walk — and
   * therefore its restitution horizon — untouched.
   *
   * <p>The bounded form flies to the sphere, at 8×8 gravity like the parking coast and the
   * injection before it: the two passes have to fly the same field, or the two crossing dates
   * cannot coincide.
   */
  @Override
  public SpacecraftState propagateStandalone(SpacecraftState currentState, Mission mission) {
    if (!endsAtTheSphere) {
      return super.propagateStandalone(currentState, mission);
    }

    SpacecraftState entry = enter(currentState, mission);
    FlightContext context = flightContext(entry, mission);
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, maxStepSeconds(entry, mission));
    propagator.setInitialState(entry);
    ReentryGuard.armQuiet(propagator, context.gravity());
    armTheSphere(propagator, context.gravity(), mission);
    return propagator.propagate(cutoffFrom(entry));
  }

  /**
   * Arms one STOP detector per declared boundary, through the same factory {@code StageLegRunner}
   * arms the ephemeris pass with — the direction rule is written once, in {@link
   * SoiCrossingDetector#crossingFrom} (spec §5.4).
   */
  private void armTheSphere(
      NumericalPropagator propagator, GravitationalContext context, Mission mission) {
    for (SolarSystemBody boundaryBody : soiTransitions(mission)) {
      propagator.addEventDetector(
          SoiCrossingDetector.crossingFrom(SphereOfInfluence.of(boundaryBody), context)
              .withHandler((state, detector, increasing) -> Action.STOP));
    }
  }
}
