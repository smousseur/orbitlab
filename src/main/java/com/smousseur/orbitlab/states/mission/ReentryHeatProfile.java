package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.models.earth.atmosphere.Atmosphere;
import org.orekit.time.AbsoluteDate;

/**
 * How hot each sample of a trajectory re-enters, as a bounded intensity a renderer can read at any
 * date.
 *
 * <p>Built from the <b>raw</b> ephemeris — the one the mission computation returns — and never from
 * the display points a {@code MissionRenderer} derives from it, which re-seat, ground-correct and
 * rotate positions and may replace a velocity with a heading. The flux is a function of the flown
 * state, so only the flown state can give it.
 *
 * <p>The quantity is the Sutton-Graves stagnation-point heat flux {@code k √(ρ / Rn) v_rel³}, with
 * one nominal nose radius for every object: an indicator of how strongly a shock layer glows, not a
 * luminance and not a per-object thermal model. It is evaluated only where it can matter — on an
 * Earth arc, descending ({@code r·v < 0}), below {@link #DOMAIN_TOP_M} — and is zero elsewhere
 * without the atmosphere being asked, which keeps an empirical model away from altitudes it is not
 * built for. The intensity maps the flux onto {@code [0, 1]} on a logarithmic scale between a
 * threshold, below which nothing glows, and a ceiling, above which everything saturates.
 *
 * <p>Each sample also keeps the <b>axis</b> a shock layer would face: the direction of the
 * air-relative velocity, from the very wind the flux was computed with, where the flux is computed;
 * the inertial velocity's direction elsewhere, where nothing glows and the axis is never drawn. And
 * it keeps the matching <b>speed</b>: relative to the air where the axis is, and inertial
 * elsewhere. With each sample's <b>altitude</b>, also kept, it shapes and tints the plasma around a
 * hot object.
 *
 * <p>Immutable: everything is computed once, in the constructor, so a profile may be built off the
 * render thread and read from it. The atmosphere is only read, and the shared Orekit instance is
 * already read concurrently by the optimizer. A failure of that atmosphere on a sample gives it a
 * zero flux instead of failing the profile: this is a display object, and a display object must
 * never fail the rendering. Failures are counted and logged once.
 */
public final class ReentryHeatProfile {

  private static final Logger logger = LogManager.getLogger(ReentryHeatProfile.class);

  /**
   * Sutton-Graves constant for Earth air, in kg^0.5/m: the textbook value of the stagnation-point
   * correlation {@code q = k √(ρ / Rn) v³} for an N2/O2 atmosphere.
   */
  private static final double SUTTON_GRAVES_K = 1.7415e-4;

  /** Nominal nose radius the flux is quoted for (m), the same for every object. */
  private static final double NOSE_RADIUS_M = 1.0;

  /** Altitude (m) at and above which the flux is taken as zero. */
  private static final double DOMAIN_TOP_M = 120_000.0;

  /**
   * Speed (m/s) below which a velocity gives no direction, the threshold {@code StackSeat} uses for
   * the same reason. The launch-pad sample is the case in point: it falls in the domain by rounding
   * and moves with the air, so its air-relative velocity is zero and could not be normalized.
   */
  private static final double MIN_DIRECTION_SPEED = 1e-3;

  /**
   * Norm below which the blend of two unit axes counts as cancelled: they were opposed, and their
   * mean has no direction left to normalize.
   */
  private static final double MIN_BLEND_NORM = 1e-9;

  /**
   * One sample of the profile, aligned with the ephemeris point it was computed from.
   *
   * @param date the sample date
   * @param fluxWm2 the stagnation heat flux, in W/m²; zero outside the domain and where the
   *     atmosphere failed
   * @param intensity the bounded intensity derived from the flux, in {@code [0, 1]}
   */
  public record HeatSample(AbsoluteDate date, double fluxWm2, double intensity) {}

  private final AbsoluteDate[] dates;

  /** Seconds from the first date, searched by bisection in {@link #intensityAt}. */
  private final double[] offsets;

  private final double[] intensities;

  /** Each sample's unit axis, as three coordinate arrays rather than one object per point. */
  private final double[] axisX;

  private final double[] axisY;
  private final double[] axisZ;

  /** Each sample's speed (m/s), relative to the air wherever its axis is. */
  private final double[] speeds;

  /** Each sample's altitude (m), as the flown state carries it. */
  private final double[] altitudes;

  private final List<HeatSample> samples;
  private final int atmosphereFailures;

  /** The date of the first sample whose intensity is above zero; null when none is. */
  private final AbsoluteDate firstHeat;

  /**
   * Computes the profile of a sequence of ephemeris points.
   *
   * @param points the raw ephemeris points, sorted by date
   * @param air the atmosphere to read the density and the wind from; {@code null} gives an empty
   *     profile, whose intensity is zero everywhere
   * @param thresholdWm2 the flux at and below which the intensity is zero, in W/m²; strictly
   *     positive
   * @param ceilingWm2 the flux at and above which the intensity is one, in W/m²; strictly above the
   *     threshold
   * @throws IllegalArgumentException if the threshold is not positive or the ceiling not above it
   */
  public ReentryHeatProfile(
      List<MissionEphemerisPoint> points, Atmosphere air, double thresholdWm2, double ceilingWm2) {
    Objects.requireNonNull(points, "points");
    if (!(thresholdWm2 > 0.0)) {
      throw new IllegalArgumentException("threshold must be positive, got " + thresholdWm2);
    }
    if (!(ceilingWm2 > thresholdWm2)) {
      throw new IllegalArgumentException(
          "ceiling must be above the threshold " + thresholdWm2 + ", got " + ceilingWm2);
    }
    int n = air == null ? 0 : points.size();
    dates = new AbsoluteDate[n];
    offsets = new double[n];
    intensities = new double[n];
    axisX = new double[n];
    axisY = new double[n];
    axisZ = new double[n];
    speeds = new double[n];
    altitudes = new double[n];
    List<HeatSample> built = new ArrayList<>(n);
    AbsoluteDate firstGlowing = null;
    int inDomain = 0;
    int failures = 0;
    for (int i = 0; i < n; i++) {
      MissionEphemerisPoint point = points.get(i);
      double flux = 0.0;
      Vector3D axis = directionOf(point.velocity());
      double speed = point.velocity().getNorm();
      if (inDomain(point)) {
        inDomain++;
        AirState state = airStateOf(point, air);
        flux = state.fluxWm2();
        if (Double.isFinite(flux)) {
          speed = state.relativeVelocity().getNorm();
          Vector3D relative = directionOf(state.relativeVelocity());
          if (!relative.equals(Vector3D.ZERO)) {
            axis = relative;
          }
        } else {
          failures++;
          flux = 0.0;
        }
      }
      dates[i] = point.time();
      offsets[i] = point.time().durationFrom(dates[0]);
      intensities[i] = intensityOf(flux, thresholdWm2, ceilingWm2);
      if (firstGlowing == null && intensities[i] > 0.0) {
        firstGlowing = point.time();
      }
      axisX[i] = axis.getX();
      axisY[i] = axis.getY();
      axisZ[i] = axis.getZ();
      speeds[i] = speed;
      altitudes[i] = point.altitudeMeters();
      built.add(new HeatSample(point.time(), flux, intensities[i]));
    }
    samples = List.copyOf(built);
    atmosphereFailures = failures;
    firstHeat = firstGlowing;
    if (failures > 0) {
      logger.warn(
          "Re-entry heat profile: the atmosphere failed on {} of the {} sample(s) in the heating"
              + " domain; their flux is taken as zero",
          failures,
          inDomain);
    }
  }

  /**
   * The profile of a mission's ephemeris, read against the shared atmosphere of the given model.
   *
   * @param ephemeris the raw mission ephemeris
   * @param model the atmosphere the mission is flown against; {@link AtmosphereModel#NONE} gives an
   *     empty profile
   * @param thresholdWm2 the flux at and below which the intensity is zero, in W/m²
   * @param ceilingWm2 the flux at and above which the intensity is one, in W/m²
   * @return the profile
   * @throws IllegalArgumentException if the threshold is not positive or the ceiling not above it
   */
  public static ReentryHeatProfile forMission(
      MissionEphemeris ephemeris, AtmosphereModel model, double thresholdWm2, double ceilingWm2) {
    Objects.requireNonNull(ephemeris, "ephemeris");
    Objects.requireNonNull(model, "model");
    if (model == AtmosphereModel.NONE) {
      return new ReentryHeatProfile(List.of(), null, thresholdWm2, ceilingWm2);
    }
    Atmosphere air = OrekitService.get().atmosphere(model, GravitationalContext.earth());
    return new ReentryHeatProfile(ephemeris.allPoints(), air, thresholdWm2, ceilingWm2);
  }

  /**
   * The intensity at a date: linear between the two samples that bracket it, that of the sample
   * itself on a sample date — the first one when several share it — and zero outside the ephemeris.
   * A pure function of the date, so playing forward and rewinding read the same values.
   *
   * @param date the date to read
   * @return the intensity, in {@code [0, 1]}
   */
  public double intensityAt(AbsoluteDate date) {
    Bracket bracket = bracketOf(date);
    if (bracket == null) {
      return 0.0;
    }
    int lower = bracket.lower();
    return intensities[lower] + bracket.tau() * (intensities[bracket.upper()] - intensities[lower]);
  }

  /**
   * The unit axis at a date: that of the sample itself on a sample date — the first one when
   * several share it — and, between two samples, the normalized linear blend of theirs. Where the
   * two axes cancel out the blend has no direction, and the earlier sample's axis is kept. A pure
   * function of the date, like {@link #intensityAt}.
   *
   * <p>Meaningful only where the {@link #intensityAt intensity} is positive: there, at least one of
   * the two samples was heating, and its axis is the air-relative velocity's direction.
   *
   * @param date the date to read
   * @return the unit axis, in the frame of the ephemeris arcs; {@link Vector3D#ZERO} outside the
   *     ephemeris and for an empty profile
   */
  public Vector3D axisAt(AbsoluteDate date) {
    Bracket bracket = bracketOf(date);
    if (bracket == null) {
      return Vector3D.ZERO;
    }
    Vector3D lower = axisOf(bracket.lower());
    double tau = bracket.tau();
    if (tau == 0.0) {
      return lower;
    }
    Vector3D blend = new Vector3D(1.0 - tau, lower, tau, axisOf(bracket.upper()));
    return blend.getNorm() < MIN_BLEND_NORM ? lower : blend.normalize();
  }

  /**
   * The speed at a date, on the rule of {@link #intensityAt}: linear between the two samples that
   * bracket it, that of the sample itself on a sample date — the first one when several share it —
   * and zero outside the ephemeris.
   *
   * <p>Meaningful only where the {@link #intensityAt intensity} is positive: there, at least one of
   * the two samples was heating, and its speed is the one relative to the air.
   *
   * @param date the date to read
   * @return the speed, in m/s; zero outside the ephemeris and for an empty profile
   */
  public double speedAt(AbsoluteDate date) {
    Bracket bracket = bracketOf(date);
    if (bracket == null) {
      return 0.0;
    }
    int lower = bracket.lower();
    return speeds[lower] + bracket.tau() * (speeds[bracket.upper()] - speeds[lower]);
  }

  /**
   * The altitude at a date, on the rule of {@link #speedAt}: linear between the two samples that
   * bracket it, that of the sample itself on a sample date — the first one when several share it —
   * and zero outside the ephemeris. Kept for every sample, in the heating domain or not: it is the
   * altitude the flown state carries, not something read off the atmosphere.
   *
   * @param date the date to read
   * @return the altitude, in metres; zero outside the ephemeris and for an empty profile
   */
  public double altitudeAt(AbsoluteDate date) {
    Bracket bracket = bracketOf(date);
    if (bracket == null) {
      return 0.0;
    }
    int lower = bracket.lower();
    return altitudes[lower] + bracket.tau() * (altitudes[bracket.upper()] - altitudes[lower]);
  }

  /**
   * The date of the first sample that glows — the first whose intensity is above zero.
   *
   * <p>The origin an object's turbulence clock can count from while it glows. A primary's ephemeris
   * starts at the launch and its payload falls after the mission's horizon, more than a day later:
   * counted from the launch, the seconds would reach where a {@code float}, which the shader takes
   * them as, steps by a frame or more.
   *
   * @return the date of the first glowing sample, or empty for a profile that never glows
   */
  public Optional<AbsoluteDate> firstHeatDate() {
    return Optional.ofNullable(firstHeat);
  }

  /**
   * Whether any point of an ephemeris lies in the heating domain — the only points a profile reads
   * the atmosphere on. An ephemeris with none heats nowhere, so its profile is all zeros: a
   * consumer can skip building it, which is what costs on a long orbital ephemeris.
   *
   * @param ephemeris the raw mission ephemeris
   * @return {@code true} if at least one point is on an Earth arc, descending, below the domain top
   */
  static boolean mayHeat(MissionEphemeris ephemeris) {
    for (MissionEphemerisPoint point : ephemeris.allPoints()) {
      if (inDomain(point)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The computed samples, one per ephemeris point and in the same order; empty for an empty
   * profile.
   *
   * @return the immutable list of samples
   */
  public List<HeatSample> samples() {
    return samples;
  }

  /**
   * How many samples of the domain got a zero flux because the atmosphere threw or returned a
   * non-finite density.
   *
   * @return the failure count
   */
  public int atmosphereFailures() {
    return atmosphereFailures;
  }

  /**
   * The intensity of a flux: zero at and below the threshold, one at and above the ceiling, and
   * {@code ln(flux / threshold) / ln(ceiling / threshold)} in between.
   */
  static double intensityOf(double fluxWm2, double thresholdWm2, double ceilingWm2) {
    if (fluxWm2 <= thresholdWm2) {
      return 0.0;
    }
    return Math.min(1.0, Math.log(fluxWm2 / thresholdWm2) / Math.log(ceilingWm2 / thresholdWm2));
  }

  private static boolean inDomain(MissionEphemerisPoint point) {
    return point.arc().body() == SolarSystemBody.EARTH
        && point.position().dotProduct(point.velocity()) < 0.0
        && point.altitudeMeters() < DOMAIN_TOP_M;
  }

  /**
   * The dates bracketing {@code date}: its own sample — the first of several sharing the date —
   * with a zero weight, or the two samples around it with the weight of the upper one.
   *
   * @return the bracket, or {@code null} outside the ephemeris and for an empty profile
   */
  private Bracket bracketOf(AbsoluteDate date) {
    int n = dates.length;
    if (n == 0) {
      return null;
    }
    double offset = date.durationFrom(dates[0]);
    if (offset < 0.0 || offset > offsets[n - 1]) {
      return null;
    }
    int found = Arrays.binarySearch(offsets, offset);
    if (found >= 0) {
      int first = found;
      while (first > 0 && offsets[first - 1] == offsets[first]) {
        first--;
      }
      return new Bracket(first, first, 0.0);
    }
    int upper = -found - 1;
    int lower = upper - 1;
    double tau = (offset - offsets[lower]) / (offsets[upper] - offsets[lower]);
    return new Bracket(lower, upper, tau);
  }

  private Vector3D axisOf(int index) {
    return new Vector3D(axisX[index], axisY[index], axisZ[index]);
  }

  /** {@code velocity}'s direction, or {@link Vector3D#ZERO} when it is too slow to have one. */
  private static Vector3D directionOf(Vector3D velocity) {
    return velocity.getNorm() < MIN_DIRECTION_SPEED ? Vector3D.ZERO : velocity.normalize();
  }

  private static AirState airStateOf(MissionEphemerisPoint point, Atmosphere air) {
    try {
      double density = air.getDensity(point.time(), point.position(), point.arc().frame());
      Vector3D wind = air.getVelocity(point.time(), point.position(), point.arc().frame());
      Vector3D relative = point.velocity().subtract(wind);
      double vRel = relative.getNorm();
      return new AirState(
          SUTTON_GRAVES_K * Math.sqrt(density / NOSE_RADIUS_M) * vRel * vRel * vRel, relative);
    } catch (RuntimeException e) {
      // An empirical model can throw on a state it does not cover (NRLMSISE00 answers far outside
      // its altitude range with an "Infinite value" throw); the caller turns any such failure into
      // a counted zero flux rather than letting it escape a display object.
      return new AirState(Double.NaN, Vector3D.ZERO);
    }
  }

  /**
   * What the atmosphere says of one heating sample.
   *
   * @param fluxWm2 the stagnation heat flux, NaN when the atmosphere failed
   * @param relativeVelocity the velocity relative to the air, in the arc's frame
   */
  private record AirState(double fluxWm2, Vector3D relativeVelocity) {}

  /**
   * The samples bracketing a date: {@code lower} and {@code upper} with {@code tau} the weight of
   * {@code upper}, or one sample twice with a zero weight on a sample date.
   */
  private record Bracket(int lower, int upper, double tau) {}
}
