package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * The burns of one trajectory, read off its raw ephemeris: when each one fires, which stage flies
 * it, and the figures that classify it.
 *
 * <p>The render layer knows no thrust. A sample carries a mass and a stage-wide {@code propulsive}
 * flag, and that flag stays true through the coasts the analytic stages fly between their burns — a
 * whole half orbit inside a Hohmann transfer. What actually fires is read from the mass instead: a
 * burn is a maximal run of consecutive sample pairs, inside a propulsive stage, over which time
 * passes and the mass drops. Three things end it:
 *
 * <ul>
 *   <li>a pair over which time passes and the mass does not — a coast, however short: the booster
 *       separation stage lasts one millisecond between the shared burn and the core's solo one, and
 *       it ends the first of them;
 *   <li>a pair over which the mass drops without time passing — a jettison;
 *   <li>the end of the ephemeris.
 * </ul>
 *
 * <p>A pair over which neither changes is bookkeeping, not flight: every stage writes its final
 * state, and the next stage its first sample, at the same date. It is passed over, so a burn that
 * runs on through a stage boundary stays one burn. Since the active stage only ever changes at a
 * jettison, <b>a burn is flown by a single active stage</b> — one specific impulse, one set of
 * nozzles.
 *
 * <p>The mass flow, normalised by the active stage's nominal flow {@code thrust / (Isp·g0)}, serves
 * two purposes and is not an intensity. Measured on the production missions, it is 1.0000 inside
 * every burn — the throttled Falcon Heavy core included, since its throttle lives in the parallel
 * block's aggregate propulsion — because every engine of the model fires at constant thrust. What
 * the ratio does carry is where a burn ignites and cuts off inside its sampling step, and the proof
 * that a run of falling mass is a burn at nominal flow.
 */
public final class BurnTimeline {

  /**
   * One burn.
   *
   * <p>Its bounds are placed inside the sampling step from the flow of its edge pairs: an engine
   * that ignites partway through a step loses mass for only part of it, and at nominal flow the
   * ratio of the two is the fraction of the step it ran. A burn that fits within a single pair is
   * centred in it, its position inside the step being unknown.
   *
   * @param start when the engine ignites
   * @param end when it cuts off
   * @param stageIndex the stack index of the active stage
   * @param role the active stage's role, or {@code null} on a stack that declares none
   * @param deltaV the velocity the burn delivers, {@code Isp·g0·ln(m₀/m₁)} (m/s)
   * @param deltaEnergy the change of specific orbital energy over the burn (J/kg)
   * @param cosTheta the energy-weighted mean cosine of the angle between thrust and velocity: +1
   *     prograde, −1 retrograde, about 0 for a thrust across the velocity
   * @param minInteriorFlowRatio the lowest flow ratio over the pairs strictly inside the burn, or
   *     {@code NaN} for a burn of one or two pairs, which has none
   * @param maxInteriorFlowRatio the highest such ratio, or {@code NaN} likewise
   */
  public record Burn(
      AbsoluteDate start,
      AbsoluteDate end,
      int stageIndex,
      StageRole role,
      double deltaV,
      double deltaEnergy,
      double cosTheta,
      double minInteriorFlowRatio,
      double maxInteriorFlowRatio) {

    /**
     * Whether the burn has pairs strictly inside it, and hence an interior flow range.
     *
     * @return {@code false} for a burn of one or two pairs
     */
    public boolean hasInteriorFlow() {
      return !Double.isNaN(minInteriorFlowRatio);
    }
  }

  private final List<Burn> burns;

  private BurnTimeline(List<Burn> burns) {
    this.burns = burns;
  }

  /**
   * Reads the burns of a trajectory.
   *
   * @param ephemeris the raw ephemeris the mission flew — never a display one, whose positions and
   *     velocities the renderer rewrites
   * @param vehicle the vehicle that flew it, read for the active stage at each mass
   * @return the burns, in flight order
   */
  public static BurnTimeline of(MissionEphemeris ephemeris, Vehicle vehicle) {
    Objects.requireNonNull(ephemeris, "ephemeris");
    Objects.requireNonNull(vehicle, "vehicle");
    List<MissionEphemerisPoint> points = ephemeris.allPoints();
    List<Burn> burns = new ArrayList<>();
    BurnBuilder current = null;
    for (int k = 0; k + 1 < points.size(); k++) {
      MissionEphemerisPoint a = points.get(k);
      MissionEphemerisPoint b = points.get(k + 1);
      double dt = b.time().durationFrom(a.time());
      double massLoss = a.mass() - b.mass();
      boolean timePasses = dt > 0.0;
      if (timePasses && a.propulsive() && massLoss > 0.0) {
        if (current == null) {
          current = new BurnBuilder(vehicle.resolveActiveStage(a.mass()));
        }
        current.add(a, b, dt, massLoss);
      } else if ((timePasses || massLoss > 0.0) && current != null) {
        burns.add(current.build());
        current = null;
      }
    }
    if (current != null) {
      burns.add(current.build());
    }
    return new BurnTimeline(List.copyOf(burns));
  }

  /**
   * The burns, in flight order.
   *
   * @return an unmodifiable list
   */
  public List<Burn> burns() {
    return burns;
  }

  /**
   * The burn firing at a date, over its half-open span {@code [start, end)}.
   *
   * @param date the date to look up
   * @return the burn firing then, or empty between burns
   */
  public Optional<Burn> burnAt(AbsoluteDate date) {
    int low = 0;
    int high = burns.size() - 1;
    int lastStarted = -1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      if (burns.get(mid).start().compareTo(date) <= 0) {
        lastStarted = mid;
        low = mid + 1;
      } else {
        high = mid - 1;
      }
    }
    if (lastStarted < 0) {
      return Optional.empty();
    }
    Burn burn = burns.get(lastStarted);
    return date.compareTo(burn.end()) < 0 ? Optional.of(burn) : Optional.empty();
  }

  /**
   * The gravitational parameter of the body an arc is centred on. A burn never straddles two arcs —
   * a propulsive stage that declares a sphere-of-influence transition is refused — so one value
   * serves a whole burn.
   */
  private static double muOf(SolarSystemBody body) {
    return switch (body) {
      case EARTH -> GravitationalContext.earth().mu();
      case MOON -> GravitationalContext.moon().mu();
      default -> throw new IllegalArgumentException("no gravitational parameter for " + body);
    };
  }

  /** Specific orbital energy {@code v²/2 − μ/r} of a sample about the body of its arc. */
  private static double energy(MissionEphemerisPoint point, double mu) {
    return 0.5 * point.velocity().getNormSq() - mu / point.position().getNorm();
  }

  /** Accumulates the pairs of one burn. */
  private static final class BurnBuilder {

    private final ActiveStageInfo stage;
    private final double exhaustVelocity;
    private final double nominalFlow;

    private int pairs;
    private AbsoluteDate firstPairStart;
    private AbsoluteDate firstPairEnd;
    private double firstPairSeconds;
    private double firstPairRatio;
    private AbsoluteDate lastPairStart;
    private double lastPairSeconds;
    private double lastPairRatio;
    private double interiorMin = Double.POSITIVE_INFINITY;
    private double interiorMax = Double.NEGATIVE_INFINITY;
    private double deltaV;
    private double deltaEnergy;
    private double speedWeightedDeltaV;

    private BurnBuilder(ActiveStageInfo stage) {
      this.stage = stage;
      PropulsionSystem propulsion = stage.propulsion();
      this.exhaustVelocity = propulsion.isp() * Constants.G0_STANDARD_GRAVITY;
      this.nominalFlow = propulsion.thrust() / exhaustVelocity;
    }

    private void add(
        MissionEphemerisPoint a, MissionEphemerisPoint b, double seconds, double massLoss) {
      double ratio = massLoss / seconds / nominalFlow;
      if (pairs == 0) {
        firstPairStart = a.time();
        firstPairEnd = b.time();
        firstPairSeconds = seconds;
        firstPairRatio = ratio;
      } else if (pairs >= 2) {
        interiorMin = Math.min(interiorMin, lastPairRatio);
        interiorMax = Math.max(interiorMax, lastPairRatio);
      }
      lastPairStart = a.time();
      lastPairSeconds = seconds;
      lastPairRatio = ratio;
      pairs++;

      // The specific energy is conserved under point-mass gravity, so over a pair it changes by
      // the thrust's work, |v|·Δv·cos θ: summing both sides weights each pair by what it delivers.
      double mu = muOf(a.arc().body());
      double pairDeltaV = exhaustVelocity * Math.log(a.mass() / b.mass());
      deltaV += pairDeltaV;
      deltaEnergy += energy(b, mu) - energy(a, mu);
      speedWeightedDeltaV += 0.5 * (a.velocity().getNorm() + b.velocity().getNorm()) * pairDeltaV;
    }

    private Burn build() {
      AbsoluteDate start;
      AbsoluteDate end;
      if (pairs == 1) {
        double running = firedFraction(firstPairRatio) * firstPairSeconds;
        AbsoluteDate middle = firstPairStart.shiftedBy(0.5 * firstPairSeconds);
        start = middle.shiftedBy(-0.5 * running);
        end = middle.shiftedBy(0.5 * running);
      } else {
        start = firstPairEnd.shiftedBy(-firedFraction(firstPairRatio) * firstPairSeconds);
        end = lastPairStart.shiftedBy(firedFraction(lastPairRatio) * lastPairSeconds);
      }
      boolean hasInterior = pairs > 2;
      return new Burn(
          start,
          end,
          stage.stageIndex(),
          stage.role(),
          deltaV,
          deltaEnergy,
          deltaEnergy / speedWeightedDeltaV,
          hasInterior ? interiorMin : Double.NaN,
          hasInterior ? interiorMax : Double.NaN);
    }

    /**
     * The fraction of a step an edge pair's engine ran, capped at the whole step: a ratio a hair
     * above one is rounding, and must not push a bound outside the pair it was read from.
     */
    private static double firedFraction(double ratio) {
      return Math.min(ratio, 1.0);
    }
  }
}
