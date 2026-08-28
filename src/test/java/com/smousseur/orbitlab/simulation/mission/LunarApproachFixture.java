package com.smousseur.orbitlab.simulation.mission;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.ArcTransition;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.gravity.SphereOfInfluence;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import java.util.List;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * The fabricated selenocentric approach MIS-5 / L4 is tested on (spec {@code
 * docs/lunar-orbit/06-conception-L4.md} §6) — an inbound hyperbola started at the lunar sphere of
 * influence, carrying the catalogue orbiter.
 *
 * <p><b>The fixture must be flown perturbed, and that is a condition of validity rather than a
 * detail.</b> Under lunar point-mass gravity alone the Keplerian closed form is exact — 100.000000
 * km at the date it predicts, with a radial velocity of 2.3e-8 m/s — so a test written on an
 * unperturbed approach would pass against an implementation that reads the perilune off the
 * hyperbolic anomaly, which is wrong by hundreds of seconds on the real thing (spec §1.2 pt 1).
 * {@code LunarInsertionPlanTest} keeps one case whose only job is to assert that this fixture still
 * has that property.
 *
 * <p>Shared by three test classes across two packages, which is why it is public and why the
 * constants live here rather than three times over.
 */
public final class LunarApproachFixture {

  /**
   * The epoch every measurement of the lot was taken at.
   *
   * <p><b>Lazily resolved, deliberately</b>, for the reason {@code GravitationalContext.earth()}
   * gives: the UTC scale is built by Orekit, so a {@code static final} field would resolve it at
   * class-initialisation time — before the {@code OrekitService.get().initialize()} the test
   * classes call in {@code @BeforeAll}.
   *
   * @return the epoch
   */
  public static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 3, 31, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  /**
   * Hyperbolic excess speed at the Moon (m/s) — the closed-form value MIS-5 / L3 §1.2 pt 1 derived
   * from the same Hohmann {@code PropellantBudget} sizes the transfer with, and which L0 measured
   * the flown chain at 825.8 – 872.5.
   */
  public static final double EXCESS_SPEED = 828.74;

  /**
   * Keplerian perilune altitude of the fixture (m).
   *
   * <p><b>Bisected once, off line, so the perturbed flight reaches {@link
   * #FLOWN_PERILUNE_ALTITUDE}</b>: the Earth's tide over the 17.8 h approach moves the perilune by
   * hundreds of kilometres, so the Keplerian value and the flown one differ by a factor of 7.7
   * here. This is exactly what a real translunar aim solves for, and reproducing that search inside
   * the tests would cost seventeen propagations to arrive at this number.
   */
  public static final double KEPLERIAN_PERILUNE_ALTITUDE = 767_364.0;

  /** What {@link #KEPLERIAN_PERILUNE_ALTITUDE} delivers when flown perturbed (m). */
  public static final double FLOWN_PERILUNE_ALTITUDE = 100_000.0;

  /** Inclination of the fixture (deg), in the band L0 measured the flown chain delivers. */
  public static final double INCLINATION_DEG = 150.0;

  /** Right ascension of the ascending node of the fixture (deg). */
  public static final double RAAN_DEG = 20.0;

  /** Argument of perilune of the fixture (deg). */
  public static final double ARGUMENT_OF_PERILUNE_DEG = 174.0;

  private static final double ORBITER_DRY_MASS = 2_000.0;

  /**
   * Insertion propellant load (kg), as {@code PropellantBudget.loadsForLunarOrbit} sizes it for a
   * 400 km parking orbit and a 100 km lunar orbit.
   */
  private static final double ORBITER_PROPELLANT_LOAD = 657.7;

  private LunarApproachFixture() {}

  /** The lunar arc a mission built by {@link #missionWith} crosses into. */
  public static GravitationalContext lunarContext() {
    return ArcTransition.across(missionContext(), SolarSystemBody.MOON);
  }

  /** The environment the approach and the insertion are flown in. */
  public static FlightContext lunarFlightContext() {
    return new FlightContext(lunarContext());
  }

  /** The catalogue orbiter, loaded for the insertion, as the payload the S2 has just released. */
  public static Vehicle orbiter() {
    return Payloads.LUNAR_ORBITER.toSpacecraft(ORBITER_DRY_MASS, ORBITER_PROPELLANT_LOAD);
  }

  /** The fixture at the sphere, in the lunar frame — what the approach coast converts to. */
  public static SpacecraftState selenocentric() {
    return selenocentric(KEPLERIAN_PERILUNE_ALTITUDE, RAAN_DEG);
  }

  /**
   * The fixture at the sphere with a chosen Keplerian perilune and node, for the cases that need an
   * impacting approach or a second orientation.
   *
   * @param keplerianPeriluneAltitude the perilune altitude of the osculating hyperbola (m)
   * @param raanDeg the right ascension of the ascending node (deg)
   * @return the state at the sphere, in the lunar frame
   */
  public static SpacecraftState selenocentric(double keplerianPeriluneAltitude, double raanDeg) {
    GravitationalContext moon = lunarContext();
    double mu = moon.mu();
    double perilune = moon.equatorialRadius() + keplerianPeriluneAltitude;
    double semiMajorAxis = -mu / (EXCESS_SPEED * EXCESS_SPEED);
    double eccentricity = 1.0 - perilune / semiMajorAxis;
    double sphereRadius = SphereOfInfluence.of(SolarSystemBody.MOON).radiusAt(epoch());
    double cosTrueAnomaly =
        (semiMajorAxis * (1.0 - eccentricity * eccentricity) / sphereRadius - 1.0) / eccentricity;
    // Inbound branch: the negative root, so the state is closing on the Moon rather than leaving
    // it.
    double trueAnomaly = -FastMath.acos(FastMath.max(-1.0, FastMath.min(1.0, cosTrueAnomaly)));
    return new SpacecraftState(
            new CartesianOrbit(
                new KeplerianOrbit(
                    semiMajorAxis,
                    eccentricity,
                    FastMath.toRadians(INCLINATION_DEG),
                    FastMath.toRadians(raanDeg),
                    FastMath.toRadians(ARGUMENT_OF_PERILUNE_DEG),
                    trueAnomaly,
                    PositionAngleType.TRUE,
                    moon.inertialFrame(),
                    epoch(),
                    mu)))
        .withMass(orbiter().getMass());
  }

  /**
   * The same state expressed geocentrically — what {@code TranslunarCoastStage} hands the approach
   * on <b>both</b> passes, since it returns the Earth side of the boundary unconverted.
   *
   * @return the state at the sphere, in GCRF
   */
  public static SpacecraftState geocentric() {
    return ArcTransition.convert(selenocentric(), GravitationalContext.earth());
  }

  /**
   * The Earth arc a lunar mission flies before the crossing, as {@code LunarFlybyMission} declares
   * it.
   */
  public static GravitationalContext missionContext() {
    return GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN);
  }

  /**
   * A mission carrying the orbiter and the given chain, declaring the Earth arc a lunar mission
   * flies before the crossing.
   *
   * @param stages the chain to fly
   * @return the mission
   */
  public static Mission missionWith(List<MissionStage> stages) {
    return new Mission("lunar insertion test", orbiter(), stages, null) {
      @Override
      public SpacecraftState getInitialState(AbsoluteDate initialDate) {
        return null;
      }

      @Override
      public GravitationalContext gravitationalContext() {
        return missionContext();
      }
    };
  }

  /** Whether {@code orekit-data.zip} is on the classpath, which every case here needs. */
  public static boolean orekitDataAvailable() {
    return OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null;
  }
}
