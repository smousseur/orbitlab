package com.smousseur.orbitlab.simulation.mission.detector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.AbstractDetector;
import org.orekit.propagation.events.EventDetectionSettings;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.events.handlers.EventHandler;
import org.orekit.time.AbsoluteDate;

/**
 * Detects and records the crossing of the Kármán line — the conventional 100 km edge of the
 * atmosphere. Mass sibling of {@link ReentryDetector}: same shape, same spherical switching
 * function, a {@code CONTINUE} action so the propagation is <em>marked</em> and never stopped.
 *
 * <p>Delivered by PHY-3 as the brick {@code MIS-10} consumes — the re-entry needs an entry mark
 * before it needs a termination. It is armed nowhere in production in this lot: the consumer that
 * reads {@link #firstDescendingCrossing()} is the deorbit mission, not this one. A unit test flies
 * a trajectory through 100 km to prove it records the crossing and its direction.
 *
 * <p>The direction is carried on each crossing because a full flight meets 100 km twice — climbing
 * out on ascent, coming back down on re-entry — and only the descending one marks an atmospheric
 * entry. This is the distinction {@link ReentryGuard} draws with radial velocity; here it falls out
 * of the event's own {@code increasing} flag for free.
 */
public class AtmosphericInterfaceDetector extends AbstractDetector<AtmosphericInterfaceDetector> {

  /** The Kármán line: the conventional altitude (m) at which space is said to begin. */
  public static final double KARMAN_ALTITUDE = 100_000.0;

  /**
   * One crossing of the interface.
   *
   * @param date when the trajectory crossed the Kármán line
   * @param descending {@code true} when the crossing was downward — an atmospheric entry — and
   *     {@code false} when upward, on the way out during ascent
   */
  public record InterfaceCrossing(AbsoluteDate date, boolean descending) {}

  /** Reference sphere radius the switching function is measured against (m). */
  private final double equatorialRadius;

  /**
   * The crossings recorded so far, shared <b>by reference</b> across every {@link #create} rebuild
   * so the record survives a {@code withMaxCheck} or a {@code withHandler}: {@link
   * AbstractDetector} rebuilds the detector as soon as one of those is called, and the instance the
   * propagator ends up calling is not the one the caller built.
   */
  private final List<InterfaceCrossing> crossings;

  /**
   * Creates an interface detector for the given reference shape.
   *
   * <p>Checked every 10 s with a 1 s date convergence, exactly like {@link ReentryDetector}: the
   * altitude is monotonic through 100 km on both an ascent and a re-entry, so the interval only has
   * to catch a single sign change, and the caller acts on the fact of the crossing rather than on a
   * sub-second epoch.
   *
   * @param equatorialRadius the reference sphere radius (m), from the stage's gravitational context
   */
  public AtmosphericInterfaceDetector(double equatorialRadius) {
    this(equatorialRadius, new ArrayList<>());
  }

  private AtmosphericInterfaceDetector(double equatorialRadius, List<InterfaceCrossing> crossings) {
    super(10.0, 1.0, DEFAULT_MAX_ITER, new CrossingRecorder(crossings));
    this.equatorialRadius = equatorialRadius;
    this.crossings = crossings;
  }

  /**
   * Copy constructor used by {@link #create}. The radius and the crossings list must travel through
   * here for the same reason {@link ReentryDetector}'s radius does: a value left out of the copy is
   * silently lost on the first {@code with*} call, and here that would be a detector that stops
   * recording the moment a consumer reconfigures it.
   */
  private AtmosphericInterfaceDetector(
      EventDetectionSettings settings,
      EventHandler handler,
      double equatorialRadius,
      List<InterfaceCrossing> crossings) {
    super(settings, handler);
    this.equatorialRadius = equatorialRadius;
    this.crossings = crossings;
  }

  @Override
  protected AtmosphericInterfaceDetector create(
      EventDetectionSettings detectionSettings, EventHandler newHandler) {
    return new AtmosphericInterfaceDetector(
        detectionSettings, newHandler, equatorialRadius, crossings);
  }

  /**
   * Switching function: positive above the Kármán line, negative below it. Each root (g=0) is a
   * crossing of the interface.
   */
  @Override
  public double g(SpacecraftState state) {
    double sphericalAltitude = state.getPVCoordinates().getPosition().getNorm() - equatorialRadius;
    return sphericalAltitude - KARMAN_ALTITUDE;
  }

  /**
   * The interface crossings recorded during propagation, in the order they occurred.
   *
   * @return an immutable copy of the recorded crossings
   */
  public List<InterfaceCrossing> crossings() {
    return List.copyOf(crossings);
  }

  /**
   * The epoch of the first downward crossing — the atmospheric entry {@code MIS-10} marks its
   * re-entry from.
   *
   * @return the first descending crossing's date, or empty when the trajectory never entered
   */
  public Optional<AbsoluteDate> firstDescendingCrossing() {
    return crossings.stream()
        .filter(InterfaceCrossing::descending)
        .map(InterfaceCrossing::date)
        .findFirst();
  }

  /**
   * Records each interface crossing and lets the propagation continue. Kept as the default handler
   * so the recording is intrinsic; a consumer that needs a different action — a re-entry that stops
   * on the line — attaches its own with {@code withHandler} and takes the recording over from here.
   */
  private static final class CrossingRecorder implements EventHandler {
    private final List<InterfaceCrossing> crossings;

    private CrossingRecorder(List<InterfaceCrossing> crossings) {
      this.crossings = crossings;
    }

    @Override
    public Action eventOccurred(SpacecraftState state, EventDetector detector, boolean increasing) {
      // Increasing g is altitude climbing through the line — an ascent crossing; a re-entry is the
      // decreasing one, which is the crossing MIS-10 cares about.
      crossings.add(new InterfaceCrossing(state.getDate(), !increasing));
      return Action.CONTINUE;
    }
  }
}
