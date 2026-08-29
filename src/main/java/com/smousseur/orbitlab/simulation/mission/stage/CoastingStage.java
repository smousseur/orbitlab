package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.gravity.ArcTransition;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.events.NodeDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * A mission stage representing an unpowered coasting phase with an optional maximum duration. If a
 * maximum time is specified, the stage transitions to the next stage when that time elapses. If no
 * maximum time is set, the stage coasts indefinitely until an external event triggers a transition.
 */
public class CoastingStage extends MissionStage {
  private final Double maxTime;
  private final boolean stopAtNode;

  /**
   * The body this coast flies around when it is not the mission's own, or {@code null} when it is
   * (MIS-5 / L5, spec {@code docs/lunar-orbit/07-conception-L5.md} §3.1).
   *
   * <p>Nullable rather than a second class: a lunar terminal coast declaring its arc would have
   * been the fourth copy of an override L4 §3.2 already refused to centralise at two, and the
   * parameter is generic where a {@code LunarCoastStage} would not be — MIS-11's return leg coasts
   * around the Earth after the same kind of crossing.
   */
  private final SolarSystemBody arcBody;

  /**
   * Creates a coasting stage with an optional maximum duration.
   *
   * @param name the human-readable name of this stage
   * @param maxTime the maximum coasting duration in seconds, or {@code null} for unlimited coasting
   */
  public CoastingStage(String name, Double maxTime) {
    this(name, maxTime, null);
  }

  /**
   * Creates a coasting stage flown around a body other than the mission's own.
   *
   * <p>The arc is <b>derived</b> from the mission's context by {@link ArcTransition#across}, not
   * written out: that method already says what crossing a sphere of influence does to a context —
   * which body becomes central, and that the one left behind stays a perturber — and repeating the
   * answer here would be a second place to keep in step.
   *
   * <p><b>Declaring it is not optional on a stage that follows a crossing.</b> {@code
   * StageLegRunner.fly} converts its entry state into the context the stage declares, and {@code
   * ArcTransition.convert} compares frames by reference: a selenocentric state handed to a stage
   * that declares the mission's terrestrial context is really transformed back to GCRF, and the
   * mission ends up measured against the Earth from 380 000 km away.
   *
   * @param name the human-readable name of this stage
   * @param maxTime the maximum coasting duration in seconds, or {@code null} for unlimited coasting
   * @param arcBody the body this coast flies around, or {@code null} for the mission's own
   */
  public CoastingStage(String name, Double maxTime, SolarSystemBody arcBody) {
    super(name);
    this.maxTime = maxTime;
    this.stopAtNode = false;
    this.arcBody = arcBody;
  }

  public CoastingStage(String name, boolean stopAtNode) {
    super(name);
    this.maxTime = null;
    this.stopAtNode = stopAtNode;
    this.arcBody = null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The mission's own context unless an arc body was declared, in which case the crossing rule
   * derives it. A coast built through either of the historical constructors declares none and
   * returns the inherited expression itself, so nothing existing changes by identity rather than by
   * tolerance.
   */
  @Override
  public GravitationalContext gravitationalContext(Mission mission) {
    return arcBody == null
        ? super.gravitationalContext(mission)
        : ArcTransition.across(mission.gravitationalContext(), arcBody);
  }

  @Override
  public boolean isPropulsive() {
    return false;
  }

  /**
   * The date this coast is configured to end on, counted from {@code entry} — or {@code null} when
   * it has no maximum duration.
   *
   * <p><b>One arithmetic, read by both passes</b> (MIS-5 / L1, spec {@code
   * docs/lunar-orbit/03-conception-L1.md} §5.3): {@link #configure} anchors it on the state the
   * chain runner has just published, and a subclass overriding {@code propagateStandalone} anchors
   * it on the state the stage walk hands it. The two are the same state, so the two passes stop on
   * the same date — a subclass writing {@code shiftedBy(maxTime)} itself would put that agreement
   * at the mercy of two copies of one expression. Same reasoning as {@code ParkingCoastStage}'s
   * absolute {@code ignitionDate}.
   *
   * @param entry the state this coast starts from
   * @return the cutoff date, or {@code null} when the coast has no maximum duration
   */
  protected AbsoluteDate cutoffFrom(SpacecraftState entry) {
    return maxTime == null ? null : entry.getDate().shiftedBy(maxTime);
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    if (stopAtNode) {
      // The node is measured in the frame this stage flies in, not in GCRF. PHY-4 / L1 §3.4
      // announced three NodeDetector sites switched to the context and only two were; this is the
      // third, and it is the stage a sphere-of-influence crossing actually happens in. For an Earth
      // stage it is the very same frame instance, which is what lets the L1 gate prove the change
      // moved nothing (spec docs/multi-corps/06-conception-L4.md §3.7).
      propagator.addEventDetector(
          new NodeDetector(gravitationalContext(mission).inertialFrame())
              .withHandler(
                  (s, detector, increasing) -> {
                    mission.transitionToNextStage(s);
                    return Action.STOP;
                  }));
    } else if (maxTime != null) {
      AbsoluteDate t = cutoffFrom(mission.getCurrentState());
      this.configuredEndDate = t;
      propagator.addEventDetector(
          new DateDetector(t)
              .withHandler(
                  (s, detector, increasing) -> {
                    mission.transitionToNextStage(s);
                    return Action.STOP;
                  }));
    }
  }
}
