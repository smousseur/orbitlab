package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan.Departure;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowCandidate;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowProblem;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * The lunar problem, and the one that dates a launch: what a translunar injection costs when the
 * parking plane is <b>the one the pad reaches</b> rather than one built around the Moon — MIS-4 /
 * L2.
 *
 * <p><b>The criterion is the injection alone.</b> The ascent costs the same Δv at every hour of the
 * day, so the only thing the launch date decides is the geometry the transfer starts from: the pad
 * is carried round by the Earth, the plane it raises turns with it, and the Moon at arrival sits at
 * a signed angle β above that plane. What that misalignment costs is the Lambert term and nothing
 * else — L1 measured the naive plane change {@code 2·v·sin(β/2)} at a <em>third</em> of the real
 * price, because an arc that must span 170° between a point of the parking plane and an off-plane
 * target rotates the plane by {@code asin(sin β / sin 170°)}, not by β. Adding the two would
 * double-count the same physics and still understate it.
 *
 * <p><b>Two opportunities per sidereal day, and not one.</b> The Earth problem aims at a plane with
 * a fixed node, an equality of vectors met once per turn; this one aims at a <em>direction</em>,
 * and a plane contains a direction far more often than it coincides with another plane. With the
 * Moon at declination δ, {@code ĥ · û_M} vanishes iff {@code |tan δ| ≤ tan i}, twice per turn of
 * node, the two roots separated by {@code 180° − 2·|arcsin(cot i · tan δ)|}: half a day apart when
 * the Moon crosses the equator, some fifty minutes apart at the 2026 maximum seen from Canaveral —
 * where δ reaches 28.415° against i = 28.562° — and merging into a single soft minimum beyond.
 *
 * <p><b>Nothing refuses a site here.</b> A pad whose latitude is below the lunar declination — from
 * Kourou, 87.5% of a lunation — reaches no plane containing the Moon, but that is priced rather
 * than declared: the criterion stays finite and returns an optimum no budget accepts, so the search
 * yields no window on its own. The refusals that remain are the ones taken from a flown trajectory,
 * in {@link #confirm}: the perilune the aim converges to, and the depletion floor of the active
 * stage.
 *
 * <p><b>The parking orbit a confirming problem prices is the one the ascent really reaches.</b> The
 * vehicle is not in parking at lift-off: the chain inserts it some 3 650 s later, 226° down range,
 * and leaves from the first passage of the injection point <em>after</em> that insertion. Posed at
 * the pad at the instant of lift-off, the parking orbit passed its injection point before the
 * vehicle was there for 27 of the 50 windows of a lunation from Canaveral, and the window confirmed
 * a passage the chain could not fly: on 2026-10-08T02:52:04 it confirmed at β = 0 on a passage 918
 * s after lift-off, while the chain, left with the next one, read β = −0.83° at its insertion and
 * aimed no lower than a 257 km perilune for a 100 km target.
 *
 * <p>A confirming problem is therefore given a {@link ParkingInsertion} — the ascent flown once,
 * carried to every epoch through the Earth-fixed frame — and coasts it to the first passage after
 * the insertion, turning the plane with the secular J2 nodal regression on the way: 0.49° a
 * revolution, measured −0.4886° on a flown 400 km parking coast. The mass it confirms with is the
 * one the ascent left in parking.
 *
 * <p><b>The screening problem keeps the parking orbit posed at the pad</b>: it is the wizard's
 * timeline, which runs before a launcher is chosen and so has no ascent to fly. Its dates precede
 * the creation's — measured from Canaveral over five days, by 80 to 121 s on the morning windows
 * and 9 to 11 minutes on the night ones, always inside the same slot.
 *
 * <p><b>Where due east has nothing, the azimuth is freed</b> ({@link PlaneChoice#FREE_AZIMUTH}). At
 * any instant exactly one prograde plane holds both the pad and the Moon's direction at arrival, so
 * the misalignment can be nulled at every epoch and only the price of reaching that plane varies:
 * the ascent loses the part of the Earth's rotation the heading no longer banks, which the
 * criterion adds to the injection. The plane priced is the one the ascent is predicted to
 * <em>fly</em>, not the one it is commanded: the climb leaves the pad's entrainment out of the
 * commanded plane uncorrected, which turns the flown plane about the vertical by {@code atan(w /
 * v)} — 0.4° to 1.7° measured from Kourou, a β that cost up to 375 m/s of injection when left in.
 * The commanded azimuth is therefore the one whose <em>flown</em> plane contains the Moon.
 */
public class LunarLaunchWindowProblem implements LaunchWindowProblem {
  private static final Logger logger = LogManager.getLogger(LunarLaunchWindowProblem.class);

  /** Which plane the parking orbit of a lift-off at an epoch is posed in. */
  public enum PlaneChoice {
    /**
     * The plane a due-east launch reaches, {@code i = φ} — one plane per instant, wherever the Moon
     * is. What every lunar mission flew before the free azimuth, and still the first choice.
     */
    DUE_EAST,

    /**
     * The plane containing the Moon at arrival once flown, at the azimuth that commands it; the
     * criterion adds what that azimuth costs the ascent against due east.
     */
    FREE_AZIMUTH
  }

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
   * The launch azimuth, due east. At {@code i = φ}, {@code sin A = cos i / cos φ} is 1 exactly and
   * {@code LaunchPlane.launchAzimuth} returns {@code π/2} for both node branches — so a due-east
   * problem has no branch to choose and no plane to pass in.
   */
  private static final double DUE_EAST = FastMath.PI / 2;

  /**
   * Nominal mass a screening geometry is posed with (kg). {@link #evaluate} is mass-free — the
   * Keplerian injection ΔV and the departure geometry both are — but a {@code SpacecraftState}
   * needs a positive mass all the same.
   */
  private static final double SCREENING_MASS = 1_000.0;

  /**
   * Passes of the fixed point that closes the plane through the pad and the Moon on its own arrival
   * date. Two are enough for what it is used for — seeding the secant, whose own tolerance decides
   * the plane: the arrival only moves with the coast, by the Moon's 0.55°/h over at most a parking
   * revolution.
   */
  private static final int THROUGH_THE_MOON_PASSES = 2;

  /** The second azimuth the secant starts from, a milliradian past the first (rad). */
  private static final double SECANT_STEP = 1.0e-3;

  /** Misalignment the secant stops at (rad) — numerical zero, the criterion being smooth there. */
  private static final double SECANT_TOLERANCE = 1.0e-12;

  /** A bound on the secant, which converges in a handful of steps on a sinusoid's zero crossing. */
  private static final int SECANT_ITERATIONS = 20;

  /**
   * Below this, the Moon at arrival sits at the pad's zenith or nadir and no single plane is
   * defined by the two directions.
   */
  private static final double ALIGNED_SINE = 1.0e-9;

  private final LaunchSitePlane site;
  private final double latitude;
  private final double parkingAltitude;
  private final double parkingRadius;
  private final double parkingSpeed;
  private final double targetPerileneAltitude;
  private final Vehicle vehicle;

  /** The measured insertion a confirming problem prices from, {@code null} on a screening one. */
  private final ParkingInsertion insertion;

  private final boolean confirming;
  private final PlaneChoice planeChoice;
  private final String name;

  /**
   * The reason the latest confirmation refused its candidate. Mutable state on a problem that is
   * already not shareable — its {@link #confirm} flies a stage — and read once its search is over.
   */
  private String latestRefusal;

  /**
   * @param latitude the launch site latitude in degrees, which is also the inclination flown
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   * @param parkingAltitude the circular parking altitude the injection leaves from (m); a parameter
   *     and not a constant, L0 having measured the aim to converge identically from 185 to 400 km
   * @param targetPerileneAltitude the perilune altitude {@link #confirm} aims for (m)
   * @param vehicle the vehicle: it supplies the Isp of the active stage and the depletion floor
   *     that stage refuses below
   * @param insertion the parking insertion the mission's ascent was measured to reach, due east
   */
  public LunarLaunchWindowProblem(
      double latitude,
      double longitude,
      double altitude,
      double parkingAltitude,
      double targetPerileneAltitude,
      Vehicle vehicle,
      ParkingInsertion insertion) {
    this(
        latitude,
        longitude,
        altitude,
        parkingAltitude,
        targetPerileneAltitude,
        vehicle,
        insertion,
        PlaneChoice.DUE_EAST);
  }

  /**
   * The confirming problem on a chosen plane.
   *
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   * @param parkingAltitude the circular parking altitude the injection leaves from (m)
   * @param targetPerileneAltitude the perilune altitude {@link #confirm} aims for (m)
   * @param vehicle the vehicle
   * @param insertion the parking insertion the mission's ascent was measured to reach, due east
   * @param planeChoice which plane the parking orbit is posed in
   */
  public LunarLaunchWindowProblem(
      double latitude,
      double longitude,
      double altitude,
      double parkingAltitude,
      double targetPerileneAltitude,
      Vehicle vehicle,
      ParkingInsertion insertion,
      PlaneChoice planeChoice) {
    this(
        latitude,
        longitude,
        altitude,
        parkingAltitude,
        targetPerileneAltitude,
        Objects.requireNonNull(vehicle, "vehicle"),
        Objects.requireNonNull(insertion, "insertion"),
        true,
        planeChoice);
  }

  /**
   * The problem as the wizard's timeline poses it: <b>screening only</b>, with no vehicle and
   * therefore no verdict.
   *
   * <p><b>The contract read literally, not a workaround.</b> {@code vehicle} is documented "for
   * {@link #confirm} alone", and {@link LaunchWindowProblem#confirm} carries a no-op default the
   * interface calls "the honest answer for a problem whose evaluate is already the truth". The
   * parameters step runs before the launcher step, so no vehicle exists there — and confirming
   * would cost seven to ten seconds a candidate on the render thread, plus an ascent to measure the
   * parking orbit on, where {@link #evaluate} costs microseconds.
   *
   * @param latitude the launch site latitude in degrees, which is also the inclination flown
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   * @param parkingAltitude the circular parking altitude the injection leaves from (m)
   * @param targetPerileneAltitude the perilune altitude aimed for (m); it names the problem and
   *     waits for a confirming solve, {@link #evaluate} not reading it
   * @return the screening problem
   */
  public static LunarLaunchWindowProblem screening(
      double latitude,
      double longitude,
      double altitude,
      double parkingAltitude,
      double targetPerileneAltitude) {
    return screening(
        latitude,
        longitude,
        altitude,
        parkingAltitude,
        targetPerileneAltitude,
        PlaneChoice.DUE_EAST);
  }

  /**
   * The screening problem on a chosen plane — the timeline's, when due east offers nothing.
   *
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   * @param parkingAltitude the circular parking altitude the injection leaves from (m)
   * @param targetPerileneAltitude the perilune altitude aimed for (m)
   * @param planeChoice which plane the parking orbit is posed in
   * @return the screening problem
   */
  public static LunarLaunchWindowProblem screening(
      double latitude,
      double longitude,
      double altitude,
      double parkingAltitude,
      double targetPerileneAltitude,
      PlaneChoice planeChoice) {
    return new LunarLaunchWindowProblem(
        latitude,
        longitude,
        altitude,
        parkingAltitude,
        targetPerileneAltitude,
        null,
        null,
        false,
        planeChoice);
  }

  private LunarLaunchWindowProblem(
      double latitude,
      double longitude,
      double altitude,
      double parkingAltitude,
      double targetPerileneAltitude,
      Vehicle vehicle,
      ParkingInsertion insertion,
      boolean confirming,
      PlaneChoice planeChoice) {
    this.site = new LaunchSitePlane(latitude, longitude, altitude, DUE_EAST);
    this.latitude = latitude;
    this.parkingAltitude = parkingAltitude;
    this.parkingRadius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + parkingAltitude;
    this.parkingSpeed = FastMath.sqrt(Constants.WGS84_EARTH_MU / parkingRadius);
    this.targetPerileneAltitude = targetPerileneAltitude;
    this.vehicle = vehicle;
    this.insertion = insertion;
    this.confirming = confirming;
    this.planeChoice = Objects.requireNonNull(planeChoice, "planeChoice");
    this.name =
        String.format(
            Locale.ROOT,
            "Lunar launch (φ %.2f°, parking %.0f km%s)",
            latitude,
            parkingAltitude / 1000.0,
            planeChoice == PlaneChoice.FREE_AZIMUTH ? ", free azimuth" : "");
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
   * 170°·cos β)} moves <em>away</em> from the Lambert singularity as the geometry degrades. It is
   * caught all the same, because one bad sample must not abort a sweep.
   */
  @Override
  public LaunchWindowCandidate evaluate(AbsoluteDate epoch) {
    try {
      Injection injection = injectionAt(epoch);
      double deltaV =
          TranslunarInjectionPlan.keplerianInjectionDeltaV(
                  injection.state(), injection.arrivalDate())
              + ascentSurcharge(injection.azimuth());
      logger.debug(
          "[{}] {}: {} m/s at β = {}°, A = {}°",
          name(),
          epoch,
          String.format(Locale.ROOT, "%.1f", deltaV),
          String.format(Locale.ROOT, "%.3f", FastMath.toDegrees(injection.planeMisalignment())),
          String.format(Locale.ROOT, "%.3f", FastMath.toDegrees(injection.azimuth())));
      return LaunchWindowCandidate.of(epoch, deltaV);
    } catch (OrbitlabException refused) {
      return LaunchWindowCandidate.refused(epoch, refused.getMessage());
    }
  }

  /**
   * What reaching the plane at {@code azimuth} costs the ascent beyond due east (m/s), on the
   * budget's own model — the figure the loads are sized on once that plane is chosen. Zero on the
   * due-east problem, whose criterion is the injection alone.
   */
  private double ascentSurcharge(double azimuth) {
    if (planeChoice == PlaneChoice.DUE_EAST) {
      return 0.0;
    }
    return PropellantBudget.ascentDeltaV(parkingAltitude, latitude, azimuth)
        - PropellantBudget.ascentDeltaV(parkingAltitude, latitude, DUE_EAST);
  }

  /**
   * Flies the aim at a screened epoch: the perilune bisection of {@link
   * TranslunarInjectionPlan#solve}, then the depletion floor of the active stage, from the mass the
   * ascent left in parking. Some thirty four-day propagations, seven to ten seconds, which is why
   * it runs on the handful of refined candidates and not on the sweep.
   *
   * <p><b>Both verdicts come from {@link TranslunarInjectionPlan#inject}, which is also what {@code
   * TLIBurnStage} flies</b>. They were the same four lines written twice until L4, and holding them
   * together is what let L6 turn the injection into a finite burn without the window drifting
   * behind it: the price quoted here is the <em>commanded</em> ΔV, the one the mission really
   * burns.
   *
   * <p><b>It is a verdict on reachability and not only on cost</b>. A finite departure reaches
   * fewer perilunes than an impulsive one: on the flyby's own window an epoch the impulse aims at
   * 100 km bottoms out at 132 km once burnt, the whole aim map lifted above the target. This method
   * is what refuses such an epoch instead of handing the mission a date it cannot honour — and that
   * only means something if {@code vehicle} is the launcher that will fly. Screening on a
   * spacecraft kick motor prices a 75° burn nothing in the chain ever lights.
   */
  @Override
  public LaunchWindowCandidate confirm(LaunchWindowCandidate candidate) {
    if (!confirming) {
      // Screening mode: no vehicle, no verdict. Said explicitly because this class overrides the
      // interface's no-op default and would otherwise dereference a vehicle it was never given.
      return candidate;
    }
    AbsoluteDate epoch = candidate.epoch();
    try {
      Injection injection = injectionAt(epoch);
      TranslunarInjectionPlan.Burn burn =
          TranslunarInjectionPlan.inject(
              injection.state(),
              targetPerileneAltitude,
              vehicle.resolveActiveStage(injection.state().getMass()),
              flightContext());

      double deltaV = burn.commandedDeltaV() + ascentSurcharge(injection.azimuth());
      logger.info(
          "[{}] {} confirmed at {} m/s (screened at {} m/s) — β = {}°, A = {}°, perilune {} km",
          name(),
          epoch,
          FastMath.round(deltaV),
          FastMath.round(candidate.deltaV()),
          String.format(Locale.ROOT, "%.3f", FastMath.toDegrees(injection.planeMisalignment())),
          String.format(Locale.ROOT, "%.3f", FastMath.toDegrees(injection.azimuth())),
          String.format(Locale.ROOT, "%.1f", burn.plan().perileneAltitude() / 1000.0));
      return LaunchWindowCandidate.of(epoch, deltaV);
    } catch (OrbitlabException refused) {
      logger.info("[{}] {} refused: {}", name(), epoch, refused.getMessage());
      latestRefusal = refused.getMessage();
      return LaunchWindowCandidate.refused(epoch, refused.getMessage());
    } catch (RuntimeException failure) {
      // Anything the force model or Orekit throws on an extreme geometry. Withdrawing the epoch
      // keeps one bad candidate from aborting a whole search, but it is a fault and not a refusal,
      // so it is logged as one.
      logger.warn("[{}] {} could not be confirmed", name(), epoch, failure);
      latestRefusal = failure.getMessage();
      return LaunchWindowCandidate.refused(epoch, failure.getMessage());
    }
  }

  /**
   * Why the latest candidate this problem confirmed was refused — what a caller tells the user when
   * a search came back empty. One problem serves one search, so "latest" is that search's.
   *
   * @return the refusal, or empty when no confirmation was refused
   */
  public Optional<String> latestRefusal() {
    return Optional.ofNullable(latestRefusal);
  }

  /**
   * The plane the ascent is commanded to fly for a lift-off at {@code epoch}: the site's due-east
   * plane, or on the free-azimuth problem the plane of the azimuth {@link #injectionAt} solved for.
   * Recomputed from the date rather than carried by a candidate, the geometry being deterministic.
   *
   * @param epoch the lift-off date
   * @return the commanded plane
   */
  public LaunchPlane launchPlaneAt(AbsoluteDate epoch) {
    if (planeChoice == PlaneChoice.DUE_EAST) {
      return LaunchPlane.dueEast(latitude);
    }
    return LaunchPlane.fromAzimuth(injectionAt(epoch).azimuth(), FastMath.toRadians(latitude));
  }

  /**
   * The parking state at the injection point of a lift-off at {@code epoch}, and the geometry that
   * placed it there.
   *
   * <p>Exposed rather than private, with {@link #injectionAt}, because it is what a caller reads
   * the geometry <em>behind</em> a price with: β is not a term of the cost — L1 measured that the
   * Lambert term already carries all of it — but it is what explains one, and a reader who could
   * only see the number could not tell a right price from a plausible one.
   *
   * <p><b>Public since MIS-4 / L4</b>, where the closure flight reads the geometry this problem
   * <em>planned</em> in order to measure it against the one the chain actually flew — the two
   * biases §6 below chiffers without flying, three minutes of them, on a window measured at eleven.
   * That comparison is the only way those numbers stop being an estimate, and it cannot be made
   * from inside this package: the mission that flies is somewhere else entirely.
   *
   * @param state the parking state at the injection point, in GCRF: the measured insertion coasted
   *     there on a confirming problem, a circular orbit posed at the pad on a screening one
   * @param arrivalDate the date the Moon's centre is aimed at
   * @param planeMisalignment the signed angle of the arrival direction above the parking plane at
   *     the injection point (rad), positive towards the plane's normal
   * @param azimuth the launch azimuth the ascent is commanded to fly (rad, clockwise from north):
   *     due east, or on the free-azimuth problem the one whose flown plane is {@code state}'s
   */
  public record Injection(
      SpacecraftState state, AbsoluteDate arrivalDate, double planeMisalignment, double azimuth) {}

  /**
   * Resolves the whole geometry of a lift-off at {@code epoch}: the parking orbit, the coast to the
   * injection point, and the parking state as it is there.
   *
   * <p><b>On a confirming problem the parking orbit is the measured insertion</b>: due east,
   * carried to {@code epoch} through the Earth-fixed frame; at a free azimuth, rebuilt from the
   * same measurement on the plane that azimuth is predicted to fly. The coast is then the chain's
   * own rule — the first passage after the insertion — with the plane turning by the nodal
   * regression.
   *
   * <p><b>On a screening problem it is posed at the pad at the lift-off instant</b>, the phase
   * being the site's direction: the plane is raised as {@code position × horizontal}, so the pad
   * lies in it by construction.
   */
  public Injection injectionAt(AbsoluteDate epoch) {
    if (planeChoice == PlaneChoice.FREE_AZIMUTH) {
      return freeAzimuthInjectionAt(epoch);
    }
    if (insertion != null) {
      return coasted(insertion.carriedTo(epoch), insertion.mass(), DUE_EAST);
    }
    Vector3D position = site.positionAt(epoch);
    return posed(epoch, position, site.normalOn(position), DUE_EAST);
  }

  /**
   * The free-azimuth geometry: the commanded azimuth whose predicted flown plane contains the Moon
   * at arrival, found by a secant on that plane's misalignment from the azimuth of the plane
   * through the pad and the Moon.
   *
   * <p>The arrival date is part of the unknown — it is the end of the parking coast plus the time
   * of flight, and the coast depends on the plane — which is why every evaluation of the secant
   * re-reads the departure on the plane it is trying, rather than fixing the Moon once.
   */
  private Injection freeAzimuthInjectionAt(AbsoluteDate epoch) {
    Vector3D position = site.positionAt(epoch);
    Vector3D entrainment = site.velocityAt(epoch);
    DoubleFunction<Injection> atAzimuth =
        azimuth -> onPlane(epoch, position, flownNormal(position, entrainment, azimuth), azimuth);
    double previous = azimuthThroughTheMoon(position, epoch);
    double current = previous + SECANT_STEP;
    double previousMisalignment = atAzimuth.apply(previous).planeMisalignment();
    Injection injection = atAzimuth.apply(current);
    double currentMisalignment = injection.planeMisalignment();
    for (int i = 0;
        i < SECANT_ITERATIONS
            && FastMath.abs(currentMisalignment) > SECANT_TOLERANCE
            && currentMisalignment != previousMisalignment;
        i++) {
      double next =
          current
              - currentMisalignment
                  * (current - previous)
                  / (currentMisalignment - previousMisalignment);
      previous = current;
      previousMisalignment = currentMisalignment;
      current = next;
      injection = atAzimuth.apply(current);
      currentMisalignment = injection.planeMisalignment();
    }
    return injection;
  }

  /**
   * The injection of a lift-off at {@code epoch} into the plane {@code normal} holds: the measured
   * insertion rebuilt on that plane, with the mass the azimuth's surcharge leaves, on a confirming
   * problem; the parking orbit posed at the pad on a screening one.
   */
  private Injection onPlane(AbsoluteDate epoch, Vector3D pad, Vector3D normal, double azimuth) {
    if (insertion == null) {
      return posed(epoch, pad, normal, azimuth);
    }
    return coasted(
        insertion.onPlane(epoch, pad, normal, parkingRadius), massAfterSurcharge(azimuth), azimuth);
  }

  /**
   * The mass in parking at {@code azimuth}: the one measured due east, less the propellant the
   * azimuth's ascent surcharge burns on the active stage, by Tsiolkovsky. Some 22 kg for the 3.8
   * m/s of Kourou on 2026-10-06 at 08:00Z, some 300 kg for the 52 m/s of the dearest azimuth.
   */
  private double massAfterSurcharge(double azimuth) {
    double exhaustVelocity =
        vehicle.resolveActiveStage(insertion.mass()).propulsion().isp()
            * Constants.G0_STANDARD_GRAVITY;
    return insertion.mass() * FastMath.exp(-ascentSurcharge(azimuth) / exhaustVelocity);
  }

  /**
   * The parking orbit posed at the pad at {@code epoch}, in the plane {@code normal} holds, and its
   * first passage of the injection point — the screening geometry, which flies no ascent.
   */
  private Injection posed(AbsoluteDate epoch, Vector3D pad, Vector3D normal, double azimuth) {
    Departure departure = TranslunarInjectionPlan.departureFrom(circular(normal, pad, epoch));
    return new Injection(
        circular(normal, departure.injectionDirection(), departure.injectionDate()),
        departure.arrivalDate(),
        departure.planeMisalignment(),
        azimuth);
  }

  /**
   * The injection of a vehicle parked at {@code atInsertion}: the first passage of the injection
   * point after the insertion, reached on a Keplerian coast whose plane turns with the nodal
   * regression, and the misalignment read on the plane as it is there.
   *
   * <p>The coast is rebuilt from position and velocity alone, like {@code departureFrom}'s own, so
   * that it shifts by mean anomaly and not by Orekit's quadratic expansion.
   */
  private static Injection coasted(SpacecraftState atInsertion, double mass, double azimuth) {
    Departure departure = TranslunarInjectionPlan.departureFrom(atInsertion);
    PVCoordinates insertionPv =
        new PVCoordinates(atInsertion.getPosition(), atInsertion.getPVCoordinates().getVelocity());
    KeplerianOrbit parking =
        new KeplerianOrbit(
            insertionPv,
            OrekitService.get().gcrf(),
            atInsertion.getDate(),
            Constants.WGS84_EARTH_MU);
    Vector3D normal = insertionPv.getMomentum().normalize();
    Rotation regression =
        ParkingInsertion.nodalRegression(
            insertionPv.getPosition().getNorm(), normal, departure.coastDuration());
    PVCoordinates atPoint = parking.shiftedBy(departure.coastDuration()).getPVCoordinates();
    Vector3D arrival =
        OrekitService.get()
            .body(SolarSystemBody.MOON)
            .getPosition(departure.arrivalDate(), OrekitService.get().gcrf())
            .normalize();
    return new Injection(
        new SpacecraftState(
                new CartesianOrbit(
                    new TimeStampedPVCoordinates(
                        departure.injectionDate(),
                        regression.applyTo(atPoint.getPosition()),
                        regression.applyTo(atPoint.getVelocity())),
                    OrekitService.get().gcrf(),
                    Constants.WGS84_EARTH_MU))
            .withMass(mass),
        departure.arrivalDate(),
        TranslunarInjectionPlan.planeMisalignment(regression.applyTo(normal), arrival),
        azimuth);
  }

  /**
   * The azimuth of the prograde plane holding both the pad and the Moon at arrival, closed on the
   * arrival date by a short fixed point on the coast.
   */
  private double azimuthThroughTheMoon(Vector3D position, AbsoluteDate epoch) {
    Vector3D north = Physics.localHorizontalDirection(position, 0.0);
    Vector3D east = Physics.localHorizontalDirection(position, DUE_EAST);
    double coast = 0.0;
    double azimuth = DUE_EAST;
    for (int pass = 0; pass < THROUGH_THE_MOON_PASSES; pass++) {
      Vector3D moon =
          OrekitService.get()
              .body(SolarSystemBody.MOON)
              .getPosition(
                  epoch.shiftedBy(coast + TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS),
                  OrekitService.get().gcrf());
      Vector3D normal = Vector3D.crossProduct(position.normalize(), moon.normalize());
      if (normal.getNorm() < ALIGNED_SINE) {
        throw new OrbitlabException(
            "the Moon at arrival is at the pad's zenith or nadir: no single plane holds both");
      }
      normal = normal.getZ() < 0.0 ? normal.normalize().negate() : normal.normalize();
      Vector3D motion = Vector3D.crossProduct(normal, position);
      azimuth = FastMath.atan2(motion.dotProduct(east), motion.dotProduct(north));
      coast =
          TranslunarInjectionPlan.departureFrom(circular(normal, position, epoch)).coastDuration();
    }
    return azimuth;
  }

  /**
   * The plane the ascent is predicted to fly when commanded at {@code azimuth}: the commanded plane
   * turned about the vertical by {@code atan(w / v)}, where {@code w} is the pad's entrainment
   * velocity out of the commanded plane — which the climb leaves uncorrected — and {@code v} the
   * parking orbit's circular speed. Due east, {@code w} vanishes and so does the turn.
   *
   * <p>Measured against four uncompensated flights from Kourou, with {@code v} taken anywhere from
   * the 7 461 m/s of the Falcon Heavy's cut-off to 7 700 m/s, the model predicted the flown
   * misalignment to within 0.23°, where the residual it corrects is 0.4° to 1.7°. Two flights
   * commanded through it came out at −0.03° and −0.23°.
   */
  private Vector3D flownNormal(Vector3D position, Vector3D entrainment, double azimuth) {
    Vector3D commanded = site.normalOn(position, azimuth);
    double outOfPlane = entrainment.dotProduct(commanded);
    Vector3D along = Vector3D.crossProduct(commanded, position.normalize());
    return commanded
        .scalarMultiply(parkingSpeed)
        .subtract(along.scalarMultiply(outOfPlane))
        .normalize();
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
        .withMass(SCREENING_MASS);
  }

  /** The same environment the lunar mission declares: Earth-centred, Moon and Sun as perturbers. */
  private static FlightContext flightContext() {
    return new FlightContext(
        GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN));
  }
}
