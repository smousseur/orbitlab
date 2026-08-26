package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan.Departure;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowCandidate;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowProblem;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * The lunar problem, and the one that dates a launch: what a translunar injection costs when the
 * parking plane is <b>the one the pad reaches</b> rather than one built around the Moon — MIS-4 /
 * L2 (spec {@code docs/lunar-flyby/04-conception-L2.md}).
 *
 * <p><b>The criterion is the injection alone.</b> The ascent costs the same Δv at every hour of the
 * day, so the only thing the launch date decides is the geometry the transfer starts from: the pad
 * is carried round by the Earth, the plane it raises turns with it, and the Moon at arrival sits at
 * a signed angle β above that plane. What that misalignment costs is the Lambert term and nothing
 * else — L1 measured the naive plane change {@code 2·v·sin(β/2)} at a <em>third</em> of the real
 * price, because an arc that must span 170° between a point of the parking plane and an off-plane
 * target rotates the plane by {@code asin(sin β / sin 170°)}, not by β (spec §2.3). Adding the two
 * would double-count the same physics and still understate it.
 *
 * <p><b>Two opportunities per sidereal day, and not one.</b> The Earth problem aims at a plane with
 * a fixed node, an equality of vectors met once per turn; this one aims at a <em>direction</em>,
 * and a plane contains a direction far more often than it coincides with another plane. With the
 * Moon at declination δ, {@code ĥ · û_M} vanishes iff {@code |tan δ| ≤ tan i}, twice per turn of
 * node, the two roots separated by {@code 180° − 2·|arcsin(cot i · tan δ)|}: half a day apart when
 * the Moon crosses the equator, some fifty minutes apart at the 2026 maximum seen from Canaveral —
 * where δ reaches 28.415° against i = 28.562° — and merging into a single soft minimum beyond (spec
 * §1.1).
 *
 * <p><b>Nothing refuses a site here.</b> A pad whose latitude is below the lunar declination — from
 * Kourou, 87.5% of a lunation — reaches no plane containing the Moon, but that is priced rather
 * than declared: the criterion stays finite and returns an optimum no budget accepts, so the search
 * yields no window on its own. The refusals that remain are the ones taken from a flown trajectory,
 * in {@link #confirm}: the perilune the aim converges to, and the depletion floor of the active
 * stage (spec §1.3).
 *
 * <p><b>What this criterion does not carry</b>, both biased the same way and both closable only by
 * flying the chain in L4: the ascent is outside the model, so the parking orbit is posed at the
 * launch instant on the site's own direction where the real insertion arrives some ten minutes and
 * twenty degrees later — <b>68 s</b> on the optimal date — and the plane is read at that same
 * instant without the ≤ 0.49° of nodal regression the parking coast accumulates, worth another
 * <b>115 s</b>. Some three minutes together, on a window measured at eleven (spec §6).
 */
public class LunarLaunchWindowProblem implements LaunchWindowProblem {
  private static final Logger logger = LogManager.getLogger(LunarLaunchWindowProblem.class);

  /**
   * Sweep step. The criterion is smooth at the hour and only its minimum has to be bracketed, which
   * twelve samples per half sidereal day do — the same reasoning, and the same number, as the Earth
   * problem's.
   */
  private static final Duration COARSE_STEP = Duration.ofHours(1);

  /**
   * Refinement resolution. The window is some eleven minutes wide where the sweep step is an hour,
   * so the tenth-of-a-step default would ask for six minutes — coarser than the thing it is looking
   * for, which is the silent kind of wrong.
   */
  private static final Duration PRECISION = Duration.ofSeconds(1);

  /**
   * Recurrence. Half a <b>sidereal</b> day, the two roots of the plane containing the Moon's
   * direction. Derived from the rotation rate rather than written out, so the 86 164 s has a single
   * source in this package.
   */
  private static final Duration RECURRENCE =
      Duration.ofMillis(Math.round(FastMath.PI / Constants.WGS84_EARTH_ANGULAR_VELOCITY * 1000.0));

  /**
   * The launch azimuth, due east. The chain this problem serves flies {@code i = φ}, where {@code
   * sin A = cos i / cos φ} is 1 exactly and {@code LaunchPlane.launchAzimuth} returns {@code π/2}
   * for both node branches — so there is no branch to choose and no plane to pass in (spec §1.2).
   */
  private static final double DUE_EAST = FastMath.PI / 2;

  private final LaunchSitePlane site;
  private final double parkingRadius;
  private final double targetPerileneAltitude;
  private final Vehicle vehicle;
  private final double massAtInjection;
  private final String name;

  /**
   * @param latitude the launch site latitude in degrees, which is also the inclination flown
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   * @param parkingAltitude the circular parking altitude the injection leaves from (m); a parameter
   *     and not a constant, L0 having measured the aim to converge identically from 185 to 400 km
   * @param targetPerileneAltitude the perilune altitude {@link #confirm} aims for (m)
   * @param vehicle the vehicle, for {@link #confirm} alone: it supplies the Isp of the active stage
   *     and the depletion floor that stage refuses below
   * @param massAtInjection the mass at injection (kg), given rather than derived — the ascent is
   *     outside this model, and it is {@code PropellantBudget} that will size it in L5
   */
  public LunarLaunchWindowProblem(
      double latitude,
      double longitude,
      double altitude,
      double parkingAltitude,
      double targetPerileneAltitude,
      Vehicle vehicle,
      double massAtInjection) {
    this.site = new LaunchSitePlane(latitude, longitude, altitude, DUE_EAST);
    this.parkingRadius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + parkingAltitude;
    this.targetPerileneAltitude = targetPerileneAltitude;
    this.vehicle = Objects.requireNonNull(vehicle, "vehicle");
    this.massAtInjection = massAtInjection;
    this.name =
        String.format(
            Locale.ROOT,
            "Lunar launch (φ %.2f°, parking %.0f km)",
            latitude,
            parkingAltitude / 1000.0);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Duration coarseStep() {
    return COARSE_STEP;
  }

  @Override
  public Duration refinementPrecision() {
    return PRECISION;
  }

  @Override
  public Duration recurrence() {
    return RECURRENCE;
  }

  /**
   * What leaving at {@code epoch} costs: one Lambert solve and a few ephemeris reads, no
   * propagation.
   *
   * <p>A refusal here is an accident rather than a regime — the projection L1 injects through is
   * defined at every misalignment a pad can produce, and the true transfer angle {@code acos(cos
   * 170°·cos β)} moves <em>away</em> from the Lambert singularity as the geometry degrades (spec
   * §1.4). It is caught all the same, because one bad sample must not abort a sweep.
   */
  @Override
  public LaunchWindowCandidate evaluate(AbsoluteDate epoch) {
    try {
      Injection injection = injectionAt(epoch);
      double deltaV =
          TranslunarInjectionPlan.keplerianInjectionDeltaV(
              injection.state(), injection.arrivalDate());
      logger.debug(
          "[{}] {}: {} m/s at β = {}°",
          name(),
          epoch,
          String.format(Locale.ROOT, "%.1f", deltaV),
          String.format(Locale.ROOT, "%.3f", FastMath.toDegrees(injection.planeMisalignment())));
      return LaunchWindowCandidate.of(epoch, deltaV);
    } catch (OrbitlabException refused) {
      return LaunchWindowCandidate.refused(epoch, refused.getMessage());
    }
  }

  /**
   * Flies the aim at a screened epoch: the perilune bisection of {@link
   * TranslunarInjectionPlan#solve}, then the depletion floor of the active stage. Some thirty
   * four-day propagations, about 4.5 seconds, which is why it runs on the handful of refined
   * candidates and not on the sweep.
   *
   * <p><b>These four lines are a copy of {@code TranslunarInjectionStage.enter}, and that is the
   * defect {@link LaunchWindowProblem#confirm} names in its own javadoc</b> — a re-implementation
   * can drift from the stage, and the drift would surface as a mission failing on the optimizer
   * thread after having been scheduled. It is unavoidable here: calling the stage takes a {@code
   * Mission}, and the lunar mission of the product is L4's. L4 hands the confirmation back to the
   * stage, as the translunar problem next door already does (spec §3.3).
   */
  @Override
  public LaunchWindowCandidate confirm(LaunchWindowCandidate candidate) {
    AbsoluteDate epoch = candidate.epoch();
    try {
      Injection injection = injectionAt(epoch);
      ActiveStageInfo active = vehicle.resolveActiveStage(massAtInjection);
      double exhaustVelocity = active.propulsion().isp() * Constants.G0_STANDARD_GRAVITY;

      TranslunarInjectionPlan plan =
          TranslunarInjectionPlan.solve(
              injection.state(), targetPerileneAltitude, exhaustVelocity, flightContext());
      SpacecraftState injected = plan.applyTo(injection.state(), exhaustVelocity);

      double floor = active.depletionFloor();
      if (injected.getMass() < floor) {
        String refusal =
            String.format(
                Locale.ROOT,
                "the %.0f m/s injection would leave %.0f kg, below the %.0f kg depletion floor of"
                    + " the active stage",
                plan.deltaV().getNorm(),
                injected.getMass(),
                floor);
        logger.info("[{}] {} refused: {}", name(), epoch, refusal);
        return LaunchWindowCandidate.refused(epoch, refusal);
      }

      double deltaV = plan.deltaV().getNorm();
      logger.info(
          "[{}] {} confirmed at {} m/s (screened at {} m/s) — β = {}°, perilune {} km",
          name(),
          epoch,
          FastMath.round(deltaV),
          FastMath.round(candidate.deltaV()),
          String.format(Locale.ROOT, "%.3f", FastMath.toDegrees(injection.planeMisalignment())),
          String.format(Locale.ROOT, "%.1f", plan.perileneAltitude() / 1000.0));
      return LaunchWindowCandidate.of(epoch, deltaV);
    } catch (OrbitlabException refused) {
      logger.info("[{}] {} refused: {}", name(), epoch, refused.getMessage());
      return LaunchWindowCandidate.refused(epoch, refused.getMessage());
    } catch (RuntimeException failure) {
      // Anything the force model or Orekit throws on an extreme geometry. Withdrawing the epoch
      // keeps one bad candidate from aborting a whole search, but it is a fault and not a refusal,
      // so it is logged as one.
      logger.warn("[{}] {} could not be confirmed", name(), epoch, failure);
      return LaunchWindowCandidate.refused(epoch, failure.getMessage());
    }
  }

  /**
   * The parking state at the injection point of a lift-off at {@code epoch}, and the geometry that
   * placed it there.
   *
   * <p>Package-private rather than private, with {@link #injectionAt}, because it is what the tests
   * of this package read the geometry <em>behind</em> a price with: β is not a term of the cost —
   * L1 measured that the Lambert term already carries all of it (spec §2.3) — but it is what
   * explains one, and a test that could only see the number could not tell a right price from a
   * plausible one.
   *
   * @param state the circular parking state at the injection point, in the plane the pad reaches
   * @param arrivalDate the date the Moon's centre is aimed at
   * @param planeMisalignment the signed angle of the arrival direction above the parking plane
   *     (rad), positive towards the plane's normal
   */
  record Injection(SpacecraftState state, AbsoluteDate arrivalDate, double planeMisalignment) {}

  /**
   * Resolves the whole geometry of a lift-off at {@code epoch}: the plane the pad reaches, the
   * parking orbit posed on the site's own direction, the coast to the injection point, and the
   * parking state as it is there.
   *
   * <p><b>The phase is the site's direction, and it is free rather than chosen.</b> The plane is
   * raised as {@code position × horizontal}, so the pad lies in it by construction; it is also the
   * physically right phase, a due-east launch at {@code i = φ} putting the site at the northernmost
   * point of the orbit.
   */
  Injection injectionAt(AbsoluteDate epoch) {
    Vector3D position = site.positionAt(epoch);
    Vector3D normal = site.normalOn(position);
    Departure departure = TranslunarInjectionPlan.departureFrom(circular(normal, position, epoch));
    return new Injection(
        circular(normal, departure.injectionDirection(), departure.injectionDate()),
        departure.arrivalDate(),
        departure.planeMisalignment());
  }

  /**
   * A circular parking orbit at {@link #parkingRadius}, in the plane {@code normal} is normal to,
   * phased at the projection of {@code towards} into that plane.
   */
  private SpacecraftState circular(Vector3D normal, Vector3D towards, AbsoluteDate date) {
    Vector3D direction =
        towards.subtract(normal.scalarMultiply(towards.dotProduct(normal))).normalize();
    Vector3D velocity =
        Vector3D.crossProduct(normal, direction)
            .scalarMultiply(FastMath.sqrt(Constants.WGS84_EARTH_MU / parkingRadius));
    return new SpacecraftState(
            new CartesianOrbit(
                new TimeStampedPVCoordinates(
                    date, direction.scalarMultiply(parkingRadius), velocity),
                OrekitService.get().gcrf(),
                Constants.WGS84_EARTH_MU))
        .withMass(massAtInjection);
  }

  /** The same environment the lunar mission declares: Earth-centred, Moon and Sun as perturbers. */
  private static FlightContext flightContext() {
    return new FlightContext(
        GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN));
  }
}
