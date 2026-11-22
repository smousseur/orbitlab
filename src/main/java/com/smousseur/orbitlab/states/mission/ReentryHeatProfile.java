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
  private final List<HeatSample> samples;
  private final int atmosphereFailures;

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
    List<HeatSample> built = new ArrayList<>(n);
    int inDomain = 0;
    int failures = 0;
    for (int i = 0; i < n; i++) {
      MissionEphemerisPoint point = points.get(i);
      double flux = 0.0;
      if (inDomain(point)) {
        inDomain++;
        flux = fluxOrNaN(point, air);
        if (!Double.isFinite(flux)) {
          failures++;
          flux = 0.0;
        }
      }
      dates[i] = point.time();
      offsets[i] = point.time().durationFrom(dates[0]);
      intensities[i] = intensityOf(flux, thresholdWm2, ceilingWm2);
      built.add(new HeatSample(point.time(), flux, intensities[i]));
    }
    samples = List.copyOf(built);
    atmosphereFailures = failures;
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
    int n = dates.length;
    if (n == 0) {
      return 0.0;
    }
    double offset = date.durationFrom(dates[0]);
    if (offset < 0.0 || offset > offsets[n - 1]) {
      return 0.0;
    }
    int found = Arrays.binarySearch(offsets, offset);
    if (found >= 0) {
      int first = found;
      while (first > 0 && offsets[first - 1] == offsets[first]) {
        first--;
      }
      return intensities[first];
    }
    int upper = -found - 1;
    int lower = upper - 1;
    double tau = (offset - offsets[lower]) / (offsets[upper] - offsets[lower]);
    return intensities[lower] + tau * (intensities[upper] - intensities[lower]);
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

  private static double fluxOrNaN(MissionEphemerisPoint point, Atmosphere air) {
    try {
      double density = air.getDensity(point.time(), point.position(), point.arc().frame());
      Vector3D wind = air.getVelocity(point.time(), point.position(), point.arc().frame());
      double vRel = point.velocity().subtract(wind).getNorm();
      return SUTTON_GRAVES_K * Math.sqrt(density / NOSE_RADIUS_M) * vRel * vRel * vRel;
    } catch (RuntimeException e) {
      // An empirical model can throw on a state it does not cover (NRLMSISE00 answers far outside
      // its altitude range with an "Infinite value" throw); the caller turns any such failure into
      // a counted zero flux rather than letting it escape a display object.
      return Double.NaN;
    }
  }
}
