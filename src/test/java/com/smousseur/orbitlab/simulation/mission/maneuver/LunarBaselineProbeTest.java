package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.gravity.SphereOfInfluence;
import com.smousseur.orbitlab.simulation.mission.MissionHorizon;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.LunarTransferMission;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * MIS-4 / L0 — the throwaway probe of the baseline (spec {@code docs/lunar-flyby/01-decoupage.md}
 * §4, lot L0). It changes no behaviour and asserts almost nothing: it <b>prints numbers</b> that
 * the following lots are designed against, and it is meant to be deleted once {@code
 * 02-baseline-L0.md} has recorded them.
 *
 * <p>Four of the five measures of the lot live here; the fifth — how many Earth-hardcoded sites a
 * ground-launched lunar flight traverses — is a reading of the call graph and produces no runtime
 * number, so it is recorded in the document directly.
 *
 * <ul>
 *   <li>{@link #aimAcrossParkingAltitudes()} — measure 1, does the aim converge from a parking
 *       orbit other than the 185 km it was calibrated at;
 *   <li>{@link #sphereExitAcrossALunation()} — measure 3, does the vehicle leave the lunar sphere
 *       and when, over five epochs of one lunation;
 *   <li>{@link #lunarDeclinationAgainstSiteLatitudes()} — measure 5, whether a parking plane
 *       inclined at the launch site's latitude can contain the Moon at all, and what the residual
 *       misalignment costs when it cannot;
 *   <li>measure 4, the wall time, falls out of the first two: each row of measure 1 times one
 *       {@code solve()}, each row of measure 3 times a whole flight.
 * </ul>
 *
 * <p>Opt-in, like the other read-only probes of the repository:
 *
 * <pre>{@code
 * gradlew cleanTest test --tests "*LunarBaselineProbeTest" -Dorbitlab.probe=true
 * }</pre>
 *
 * <p>{@code cleanTest} is not decorative: re-running the same {@code --tests} filter after a green
 * run executes nothing at all, and a probe that silently reports the previous run's numbers is
 * worse than no probe.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class LunarBaselineProbeTest {
  private static final Logger logger = LogManager.getLogger(LunarBaselineProbeTest.class);

  /** The epoch {@code LunarTransferFlightTest} is pinned on, so every number here is comparable. */
  private static AbsoluteDate epoch;

  /**
   * Vehicle mass at injection (kg) — {@code LunarTransferMission}'s 500 kg dry plus its 1 200 kg of
   * propellant, whose fields are private. Duplicated here rather than exposed: the probe is deleted
   * at the end of the lot and must not leave a widened API behind it.
   */
  private static final double INJECTION_MASS = 1_700.0;

  /** Parking altitudes swept by measure 1 (m). 185 km is the calibration point. */
  private static final double[] PARKING_ALTITUDES = {185_000.0, 250_000.0, 300_000.0, 400_000.0};

  /**
   * Days after {@link #epoch} sampled by measure 3 — the spacing {@code LunarTransferFlightTest}
   * already uses to cover a lunation.
   */
  private static final int[] PROBE_DAYS = {0, 6, 12, 18, 24};

  /**
   * Horizon flown by measure 3 (s). L6 measured entry at 3.25 d and perilune at 4.0 d and then
   * <em>extrapolated</em> "no exit before 5.5 d"; ten days leaves that extrapolation room to be
   * wrong in either direction, and distinguishes a late exit from no exit at all.
   */
  private static final double PROBE_HORIZON_SECONDS = 10.0 * 86_400.0;

  /** No stage of the demo mission is optimizable, so CMA-ES never runs on this budget. */
  private static final int MAX_EVALUATIONS = 1;

  /** Launch sites of the wizard, as {@code StepLaunchSite} declares them. */
  private record Site(String name, double latitudeDeg) {}

  private static final List<Site> SITES =
      List.of(
          new Site("Kourou", 5.236),
          new Site("Canaveral", 28.562),
          new Site("Tanegashima", 30.400),
          new Site("Vandenberg", 34.632),
          new Site("Baikonur", 45.965));

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 3, 31, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  // ── Measure 1 — the aim against the parking altitude ──────────────────────

  /**
   * Solves the injection from parking orbits of increasing altitude, all other things equal.
   *
   * <p><b>What it decides.</b> {@code TranslunarInjectionPlan} is calibrated at {@code
   * PARKING_ALTITUDE = 185 km} and every constant it carries — the four-day time of flight, the
   * 170° transfer angle, the bracket the bisection starts from — was tuned there. If the aim
   * converges to the same perilune from 400 km, the lunar chain can take its parking altitude from
   * wherever the mission spec puts it; if it does not, the chain owns a constant of its own and the
   * découpage says so in writing rather than discovering it in flight.
   */
  @Test
  @DisplayName("L0 measure 1 — the aim, from parking altitudes other than the calibrated 185 km")
  void aimAcrossParkingAltitudes() {
    double exhaustVelocity =
        PropulsionSystem.getSpacecraftPropulsion().isp() * Constants.G0_STANDARD_GRAVITY;
    FlightContext context =
        new FlightContext(
            GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN));

    logger.info("── L0 measure 1: the aim against the parking altitude ──");
    logger.info("epoch {}, target perilune {} km", epoch, 100);
    logger.info(
        "  alt(km) |   outcome |   dv(m/s) | plan perilune(km) | kepler miss(km) | wall(s)");

    for (double altitude : PARKING_ALTITUDES) {
      SpacecraftState parking = parkingAt(altitude, epoch, INJECTION_MASS);
      long startedAt = System.nanoTime();
      try {
        TranslunarInjectionPlan plan =
            TranslunarInjectionPlan.solve(
                parking, LunarTransferMission.DEFAULT_PERILUNE_ALTITUDE, exhaustVelocity, context);
        double wall = (System.nanoTime() - startedAt) / 1.0e9;
        logger.info(
            String.format(
                Locale.ROOT,
                "  %7.0f | converged | %9.1f | %17.1f | %15.1f | %7.1f",
                altitude / 1000.0,
                plan.deltaV().getNorm(),
                plan.perileneAltitude() / 1000.0,
                plan.keplerianMissMeters() / 1000.0,
                wall));
      } catch (OrbitlabException refusal) {
        double wall = (System.nanoTime() - startedAt) / 1.0e9;
        logger.info(
            String.format(
                Locale.ROOT,
                "  %7.0f |   REFUSED | %48s | %7.1f",
                altitude / 1000.0,
                refusal.getMessage(),
                wall));
      }
    }
  }

  // ── Measure 3 — the exit from the lunar sphere ────────────────────────────

  /**
   * Flies the demo mission to a ten-day horizon at five epochs of one lunation, and reports where
   * every arc begins and ends.
   *
   * <p><b>What it decides.</b> Three separate things rest on the answer. The horizon of {@code L4},
   * which must cover the exit for <em>any</em> launch date and not only for the one it was
   * calibrated on. The half of the boundary dead band {@code ε} that has never been flown — L6
   * crossed inbound only. And the shape of {@code FlybyObjective} in {@code L3}: if the flight ends
   * back on the Earth arc, {@code MissionLoadEvaluator.objectiveMet} — which measures the
   * <em>final</em> coast arc — never looks at the lunar arc at all, and the objective has to select
   * its arc by body rather than by position in the sequence.
   *
   * <p>Each row also times one whole flight, which is measure 4: two {@code solve()} calls, one on
   * the optimizer's stage walk and one on the ephemeris pass, since the injection stage has no
   * replay branch.
   */
  @Test
  @DisplayName("L0 measure 3 — does the vehicle leave the lunar sphere, and when")
  void sphereExitAcrossALunation() {
    SphereOfInfluence lunarSphere = SphereOfInfluence.of(SolarSystemBody.MOON);
    logger.info(
        "── L0 measure 3: the exit from the lunar sphere, horizon {} d ──",
        PROBE_HORIZON_SECONDS / 86_400.0);
    logger.info(
        "lunar sphere radius at epoch: {} km",
        String.format(Locale.ROOT, "%.0f", lunarSphere.radiusAt(epoch) / 1000.0));

    for (int day : PROBE_DAYS) {
      AbsoluteDate date = epoch.shiftedBy(day * 86_400.0);
      LunarTransferMission mission = new LunarTransferMission("L0 probe day " + day);
      mission.setHorizon(new MissionHorizon.FixedDuration(PROBE_HORIZON_SECONDS));

      long startedAt = System.nanoTime();
      MissionComputeResult result;
      try {
        mission.setCurrentState(mission.getInitialState(date));
        result = new MissionOptimizer(mission, MAX_EVALUATIONS, 42L).optimize();
      } catch (OrbitlabException refusal) {
        logger.info(
            "day {}: REFUSED after {} s — {}",
            day,
            String.format(Locale.ROOT, "%.1f", (System.nanoTime() - startedAt) / 1.0e9),
            refusal.getMessage());
        continue;
      }
      double wall = (System.nanoTime() - startedAt) / 1.0e9;

      MissionEphemeris ephemeris = result.ephemeris();
      if (ephemeris == null) {
        logger.info(
            "day {}: no ephemeris produced after {} s",
            day,
            String.format(Locale.ROOT, "%.1f", wall));
        continue;
      }
      reportArcs(day, date, ephemeris, wall);
    }
  }

  /** Prints one flight's arc sequence, its perilune, its trail budget and its wall time. */
  private static void reportArcs(
      int day, AbsoluteDate launch, MissionEphemeris ephemeris, double wallSeconds) {
    List<MissionEphemerisPoint> points = ephemeris.allPoints();
    List<String> arcs = new ArrayList<>();
    SolarSystemBody previousBody = null;
    for (MissionEphemerisPoint point : points) {
      if (point.arc().body() != previousBody) {
        arcs.add(
            String.format(
                Locale.ROOT,
                "%s@%.2fd",
                point.arc().body(),
                point.time().durationFrom(launch) / 86_400.0));
        previousBody = point.arc().body();
      }
    }

    double perilune = Double.POSITIVE_INFINITY;
    double lunarMax = Double.NEGATIVE_INFINITY;
    for (MissionEphemerisPoint point : points) {
      if (point.arc().body() == SolarSystemBody.MOON) {
        perilune = FastMath.min(perilune, point.altitudeMeters());
        lunarMax = FastMath.max(lunarMax, point.altitudeMeters());
      }
    }

    MissionEphemerisPoint last = points.get(points.size() - 1);
    logger.info(
        "day {}: arcs {} | flown {} d | perilune {} km | max on lunar arc {} km | last arc {}"
            + " | {} points, {} trail vertices | complete={} | wall {} s",
        day,
        arcs,
        String.format(Locale.ROOT, "%.2f", last.time().durationFrom(launch) / 86_400.0),
        String.format(Locale.ROOT, "%.1f", perilune / 1000.0),
        String.format(Locale.ROOT, "%.0f", lunarMax / 1000.0),
        last.arc().body(),
        points.size(),
        ephemeris.displayTrail().size(),
        ephemeris.isComplete(),
        String.format(Locale.ROOT, "%.1f", wallSeconds));
  }

  // ── Measure 5 — the lunar declination against the site latitudes ──────────

  /**
   * Sweeps the Moon's geocentric declination and compares it, site by site, to the inclination a
   * due-east ascent reaches from that site.
   *
   * <p><b>What it decides.</b> A parking plane of inclination {@code i} contains directions of
   * declination up to {@code ±i} and no further, so a plane that has to contain the Moon at arrival
   * needs {@code i >= |δ|} — which is the guard {@link TranslunarInjectionPlan#transferPlaneNormal}
   * enforces, and the reason its inclination constant is 30° rather than a launch latitude. The
   * chain {@code L4} composes takes {@code i = φ} of the site instead, and Canaveral's 28.562° sits
   * inside the declination's own range. When it does not fit, the shortfall {@code |δ| − i} is the
   * minimum angle between the reachable plane and the Moon's direction, and it is paid as a plane
   * change at parking speed — the same shape of floor {@code EarthLaunchWindowProblem} already
   * carries, and a term {@code L2}'s criterion has to know about.
   *
   * <p>No propagation: this is an ephemeris lookup and spherical trigonometry, and it costs
   * seconds.
   */
  @Test
  @DisplayName("L0 measure 5 — can a plane inclined at the launch latitude contain the Moon")
  void lunarDeclinationAgainstSiteLatitudes() {
    double parkingSpeed =
        FastMath.sqrt(
            Constants.WGS84_EARTH_MU
                / (Constants.WGS84_EARTH_EQUATORIAL_RADIUS
                    + TranslunarInjectionPlan.PARKING_ALTITUDE));

    logger.info("── L0 measure 5: lunar declination against the site latitudes ──");
    logger.info(
        "plane-change speed at {} km parking: {} m/s",
        TranslunarInjectionPlan.PARKING_ALTITUDE / 1000.0,
        String.format(Locale.ROOT, "%.1f", parkingSpeed));

    reportDeclinationBand("2026 only", epoch.shiftedBy(-89 * 86_400.0), 365, 0.25, parkingSpeed);
    reportDeclinationBand("one Metonic-scale cycle (18.6 y)", epoch, 19 * 365, 0.25, parkingSpeed);
    reportDuty("one lunation from the pinned epoch", epoch, 27.32, 1.0 / 144.0);
  }

  /**
   * Prints the extreme declination over a span and, per site, the misalignment it leaves.
   *
   * @param label how the span is named in the document
   * @param from the first sample
   * @param days the span length in days
   * @param stepDays the sampling step in days
   * @param parkingSpeed the speed a plane change is paid at (m/s)
   */
  private static void reportDeclinationBand(
      String label, AbsoluteDate from, int days, double stepDays, double parkingSpeed) {
    double maxAbsDeclination = 0.0;
    AbsoluteDate at = null;
    for (double t = 0.0; t <= days; t += stepDays) {
      AbsoluteDate date = from.shiftedBy(t * 86_400.0);
      double declination = FastMath.abs(declinationDeg(date));
      if (declination > maxAbsDeclination) {
        maxAbsDeclination = declination;
        at = date;
      }
    }

    logger.info(
        "{}: max |declination| = {}° on {}",
        label,
        String.format(Locale.ROOT, "%.3f", maxAbsDeclination),
        at);
    for (Site site : SITES) {
      double shortfall = FastMath.max(0.0, maxAbsDeclination - site.latitudeDeg());
      double cost = 2.0 * parkingSpeed * FastMath.sin(0.5 * FastMath.toRadians(shortfall));
      logger.info(
          String.format(
              Locale.ROOT,
              "    %-12s i=%6.3f° | worst misalignment %6.3f° | plane change %8.1f m/s",
              site.name(),
              site.latitudeDeg(),
              shortfall,
              cost));
    }
  }

  /**
   * Prints, per site, how much of a span the site's plane can contain the Moon at all, and how long
   * the longest uninterrupted opportunity lasts.
   *
   * <p>This is the number the découpage's §6 pt 3 rests on when it says Kourou "only contains the
   * Moon a few hours a month".
   */
  private static void reportDuty(String label, AbsoluteDate from, double days, double stepDays) {
    logger.info(
        "{} ({} d, {} min step):",
        label,
        days,
        String.format(Locale.ROOT, "%.0f", stepDays * 1440.0));
    for (Site site : SITES) {
      int inside = 0;
      int total = 0;
      int run = 0;
      int longestRun = 0;
      for (double t = 0.0; t <= days; t += stepDays) {
        total++;
        double declination = FastMath.abs(declinationDeg(from.shiftedBy(t * 86_400.0)));
        if (declination <= site.latitudeDeg()) {
          inside++;
          run++;
          longestRun = FastMath.max(longestRun, run);
        } else {
          run = 0;
        }
      }
      logger.info(
          String.format(
              Locale.ROOT,
              "    %-12s i=%6.3f° | reachable %5.1f %% of the span | longest opportunity %6.2f h",
              site.name(),
              site.latitudeDeg(),
              100.0 * inside / total,
              longestRun * stepDays * 24.0));
    }
  }

  /** The Moon's geocentric declination in GCRF, in degrees. */
  private static double declinationDeg(AbsoluteDate date) {
    Vector3D moon =
        OrekitService.get()
            .body(SolarSystemBody.MOON)
            .getPosition(date, OrekitService.get().gcrf());
    return FastMath.toDegrees(FastMath.asin(moon.getZ() / moon.getNorm()));
  }

  // ── The parking orbit, at an altitude the production method does not take ──

  /**
   * {@code TranslunarInjectionPlan.parkingState} with the altitude opened up.
   *
   * <p>The body is copied rather than called because the production method hardcodes {@code
   * PARKING_ALTITUDE}, and measure 1 exists precisely to vary it. Widening the production signature
   * for a probe that is to be deleted would be the wrong direction: {@code L1} will decide what
   * that API becomes, from a parking orbit it is <em>given</em> rather than one it builds.
   *
   * @param altitude the circular parking altitude (m)
   * @param injectionDate the date the impulse is applied
   * @param mass the spacecraft mass at injection (kg)
   * @return the parking state, in GCRF
   */
  private static SpacecraftState parkingAt(
      double altitude, AbsoluteDate injectionDate, double mass) {
    AbsoluteDate arrival = injectionDate.shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS);
    Vector3D moonDirection =
        OrekitService.get()
            .body(SolarSystemBody.MOON)
            .getPosition(arrival, OrekitService.get().gcrf())
            .normalize();
    Vector3D normal = TranslunarInjectionPlan.transferPlaneNormal(moonDirection);
    Vector3D injectionDirection =
        new Rotation(
                normal, -TranslunarInjectionPlan.TRANSFER_ANGLE, RotationConvention.VECTOR_OPERATOR)
            .applyTo(moonDirection);

    double radius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + altitude;
    Vector3D position = injectionDirection.scalarMultiply(radius);
    Vector3D velocity =
        Vector3D.crossProduct(normal, injectionDirection)
            .scalarMultiply(FastMath.sqrt(Constants.WGS84_EARTH_MU / radius));

    return new SpacecraftState(
            new CartesianOrbit(
                new TimeStampedPVCoordinates(injectionDate, position, velocity),
                OrekitService.get().gcrf(),
                Constants.WGS84_EARTH_MU))
        .withMass(mass);
  }
}
