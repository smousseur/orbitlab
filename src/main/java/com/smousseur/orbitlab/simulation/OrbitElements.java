package com.smousseur.orbitlab.simulation;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.UnnormalizedSphericalHarmonicsProvider;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.conversion.osc2mean.EcksteinHechlerTheory;
import org.orekit.propagation.conversion.osc2mean.FixedPointConverter;
import org.orekit.utils.Constants;

/**
 * The elements of an orbit as OrbitLab reports them. A <em>reporting</em> quantity only: nothing
 * here feeds a propagation, a cost function or a targeting decision.
 *
 * <p><b>Altitude convention.</b> Apsides are spherical-equatorial, {@code a(1±e) −
 * WGS84_EARTH_EQUATORIAL_RADIUS} — the convention already in place at every call site. It is not
 * geodetic: at 5.23° inclination the difference is ~180 m (spec orbit-reporting/01 section 1.1).
 * Keeping it identical is what makes the osculating and mean lines comparable side by side.
 *
 * <p><b>Limit.</b> Apsides only mean something on a bound orbit. On a hyperbolic trajectory Orekit
 * returns {@code a < 0} and {@code e > 1}: {@code a(1+e)} then produces a large negative number
 * that is not an apogee, with nothing to signal it. Callers only report insertion or end-of-mission
 * states, which are bound.
 *
 * @param semiMajorAxis semi-major axis (m)
 * @param eccentricity eccentricity
 * @param inclination inclination (rad)
 * @param perigeeAltitude perigee altitude (m)
 * @param apogeeAltitude apogee altitude (m)
 */
public record OrbitElements(
    double semiMajorAxis,
    double eccentricity,
    double inclination,
    double perigeeAltitude,
    double apogeeAltitude) {

  private static final double RE = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  private static final Logger logger = LogManager.getLogger(OrbitElements.class);

  /**
   * Degree of the zonal field. Eckstein-Hechler needs the C60 term: at degree 5 it throws "no term
   * (6, 0) in a 5x0 spherical harmonics decomposition".
   */
  private static final int ZONAL_DEGREE = 6;

  /** Fixed-point settings. Measured: the conversion converges in 5 iterations. */
  private static final double CONVERGENCE_THRESHOLD = 1.0e-12;

  private static final int MAX_ITERATIONS = 500;

  /** No damping: measured useless here, it would only slow convergence down. */
  private static final double DAMPING = 1.0;

  /**
   * The zonal provider, built lazily. Deliberately <b>not</b> a static holder: its initialisation
   * needs {@code orekit-data.zip}, and a holder that fails leaves the class permanently unusable
   * ({@code NoClassDefFoundError} on every subsequent access). Here the failure is caught by {@link
   * #mean(Orbit)} and costs nothing but a log line. Benign race: two threads may build two
   * equivalent providers.
   */
  private static volatile UnnormalizedSphericalHarmonicsProvider zonalProvider;

  /** The osculating elements of {@code orbit}, read as they are. */
  public static OrbitElements osculating(Orbit orbit) {
    Objects.requireNonNull(orbit, "orbit");
    return elementsOf(orbit);
  }

  /**
   * The <b>mean</b> elements of {@code orbit} — the orbit stripped of its short-period terms, that
   * is, the mission orbit rather than the instantaneous snapshot.
   *
   * <p>Eckstein-Hechler, not Brouwer-Lyddane: measured against a theory-free referee (equinoctial
   * averaging of the osculating elements over one period under the 8x8 field), EH lands within ~200
   * m across the whole useful eccentricity range, where BL either diverges or, worse, returns a
   * mean perigee that varies by 8 216 m depending on the sampling anomaly (spec orbit-reporting/01
   * section 3.2.1).
   *
   * <p><b>Residual.</b> The conversion removes ~97% of the short-period oscillation, not 100%:
   * measured 2026-08-05, the mean perigee of one and the same orbit still varies by ~625 m at 400
   * km depending on the sampling instant (571 m at 600 km, 482 m at 1000 km). That is
   * Eckstein-Hechler's own modelling residual, not a convergence defect. Report it to the
   * kilometre, not to the metre.
   *
   * <p>Returns {@code Optional.empty()} rather than throwing: a fixed point may fail to converge,
   * and <b>no mission must ever fail because a report could not be computed</b> (spec
   * orbit-reporting/01 section 3.4).
   */
  public static Optional<OrbitElements> mean(Orbit orbit) {
    Objects.requireNonNull(orbit, "orbit");
    try {
      UnnormalizedSphericalHarmonicsProvider provider = zonalProvider();
      // Rebuilt on the provider's mu: mixing it with WGS84_EARTH_MU shifts the elements by about a
      // metre, which would read as J2 (spec orbit-reporting/01 section 3.3).
      KeplerianOrbit rebased =
          new KeplerianOrbit(
              orbit.getPVCoordinates(), orbit.getFrame(), orbit.getDate(), provider.getMu());
      // Fresh on every call: the converter carries an iteration counter, hence state.
      FixedPointConverter converter =
          new FixedPointConverter(
              new EcksteinHechlerTheory(provider), CONVERGENCE_THRESHOLD, MAX_ITERATIONS, DAMPING);
      return Optional.of(elementsOf(converter.convertToMean(rebased)));
    } catch (RuntimeException e) {
      logger.debug("Mean orbit unavailable ({}): {}", e.getClass().getSimpleName(), e.getMessage());
      return Optional.empty();
    }
  }

  private static UnnormalizedSphericalHarmonicsProvider zonalProvider() {
    UnnormalizedSphericalHarmonicsProvider local = zonalProvider;
    if (local == null) {
      local = GravityFieldFactory.getUnnormalizedProvider(ZONAL_DEGREE, 0);
      zonalProvider = local;
    }
    return local;
  }

  /** Shared apside formula: the same lines for the osculating and for the mean orbit. */
  private static OrbitElements elementsOf(Orbit orbit) {
    double a = orbit.getA();
    double e = orbit.getE();
    return new OrbitElements(a, e, orbit.getI(), a * (1.0 - e) - RE, a * (1.0 + e) - RE);
  }

  /** Compact log line, shared by every reporting site. */
  public String format() {
    return String.format(
        Locale.ROOT,
        "%.0f x %.0f m (e=%.3e, i=%.4f deg)",
        perigeeAltitude,
        apogeeAltitude,
        eccentricity,
        FastMath.toDegrees(inclination));
  }
}
