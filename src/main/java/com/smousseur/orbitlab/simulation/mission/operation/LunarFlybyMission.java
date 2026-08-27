package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.objective.FlybyObjective;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticParkingInsertionStage;
import com.smousseur.orbitlab.simulation.mission.stage.ParkingCoastStage;
import com.smousseur.orbitlab.simulation.mission.stage.TranslunarCoastStage;
import com.smousseur.orbitlab.simulation.mission.stage.TranslunarInjectionStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentSequence;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.VerticalAscentStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import java.util.ArrayList;
import java.util.List;

/**
 * The lunar flyby of the product: a mission that lifts off from a pad, parks, coasts to its
 * injection point, leaves for the Moon and flies past it (MIS-4 / L4).
 *
 * <p><b>It is what {@code LunarTransferMission} is not.</b> The PHY-4 demo starts on a fabricated
 * parking orbit built around where the Moon will be, so it never launches and never waits for a
 * window. This one starts on the pad: the plane is the one the site reaches, the phase is whatever
 * the ascent delivers, and the departure point is found inside that plane rather than chosen to
 * suit the Moon.
 *
 * <p><b>The parking altitude is a parameter and it is 400 km</b> (spec {@code
 * docs/lunar-flyby/06-conception-L4.md} §2). Not for the cost — the injection is 54 m/s cheaper
 * from 400 km than from 185, and the ascent to it costs more than that back — but because 400 km is
 * the only parking altitude any ascent in this repository actually flies, while 185 km sits exactly
 * on the knee of {@code GravityTurnConstraints.getFpaWindowDeg}, the tightest edge of the CMA-ES
 * calibration and one never yet exercised.
 *
 * <p><b>No S2 jettison after the injection</b> (§3.3). A separation exists so {@code
 * resolveActiveStage} can hand the next burn to another engine; the injection is the last burn of
 * this chain and the payload is inert, so a separation would change no trajectory, add a stage that
 * knows how to refuse, and widen the gap between the stage walk and the flight — {@code
 * StageSeparationStage} does not override {@code propagateStandalone} either. The assumed price is
 * that the end-of-mission mass includes the spent stage.
 *
 * <p><b>The Moon and the Sun are declared at mission level</b>, as on the demo, and that is what
 * makes the crossing work at all: {@code ArcTransition} derives the selenocentric context
 * mechanically from this one, {@code earth().withPerturbers(MOON, SUN)} becoming {@code
 * moon().withPerturbers(EARTH, SUN)}. The ascent inherits the two perturbers as a consequence —
 * negligible over ten minutes of climb, and it moves no reference, the recalibrated ascent
 * baselines belonging to the LEO and GEO profiles.
 */
public class LunarFlybyMission extends EarthMission {

  /**
   * The ± band on the flown perilune (m), for this mission and for the PHY-4 demo that reads it
   * from here.
   *
   * <p>It lives on the mission of the product rather than on the demo because the demo is the class
   * meant to die at the end of the chantier: leaving the band there would take it away with her
   * (§4.1). It is not a component of {@code MissionSpec.Lunar} either — the width is dictated by
   * the measurement and not chosen by a caller, an order of magnitude above the ~0.9 km the 60 s
   * coast sampling can over-read closest approach by and the ~1 km the aim secant converges to.
   *
   * <p><b>Measured on the ground-launched chain, and the ground adds nothing.</b> The band was
   * written as provisional because a flight starting on the pad carries the dispersion of the
   * ascent and a date bias L2 could only estimate, either of which could have moved the perilune by
   * an unknown amount. Flown on 2026-08-27 by {@code LunarFlybyFlightTest}: <b>101.0 km against a
   * 100 km target</b>, one kilometre out, from a geometry whose β had drifted 0.664° from the one
   * the launch window planned. The reason the two are unrelated is that {@code
   * TranslunarInjectionPlan.solve} re-aims from the state the vehicle is really in at injection, so
   * the bisection absorbs the drift before it can reach closest approach — leaving only the ~1 km
   * that bisection converges to. Ten kilometres is therefore ten times the observed error rather
   * than a guess, and it is kept at that: the two terms it is built from — the ~0.9 km the 60 s
   * coast sampling can over-read by, and the ~1 km of the secant — are what set the floor, and
   * neither moved.
   */
  public static final double PERILUNE_TOLERANCE = 10_000.0;

  /** Name of the terminal coast, the one every profile of the repository ends on. */
  private static final String FINAL_COAST_NAME = "Coasting";

  /**
   * Name of the parking coast. Public because it is the only handle a caller has on the phase: the
   * closure flight reads the injection geometry off the ephemeris samples this stage produced, and
   * a literal there would be a string coupling nothing keeps in step with this file.
   */
  public static final String PARKING_COAST_NAME = "Parking coast";

  private final double latitude;
  private final double longitude;
  private final double altitude;

  /**
   * Creates a lunar flyby flown from a ground site.
   *
   * <p>The ascent plane is the one a due-east launch reaches for free, {@code i = φ}, and there is
   * no choice to offer: at that inclination the two azimuths {@code LaunchPlane.launchAzimuth}
   * distinguishes merge, which is why L2's window problem takes no {@code LaunchPlane} either. An
   * adaptive inclination is a later lot.
   *
   * @param name the mission name
   * @param configuration the launcher model, propellant loads and payload
   * @param parkingAltitude the parking orbit altitude in meters
   * @param periluneAltitude the perilune altitude to fly past the Moon at, in meters
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   */
  public LunarFlybyMission(
      String name,
      LaunchConfiguration configuration,
      double parkingAltitude,
      double periluneAltitude,
      double latitude,
      double longitude,
      double altitude) {
    this(
        name,
        configuration.toVehicleStack(),
        configuration.ascentProfile(),
        parkingAltitude,
        periluneAltitude,
        latitude,
        longitude,
        altitude);
  }

  private LunarFlybyMission(
      String name,
      Vehicle vehicle,
      AscentProfile profile,
      double parkingAltitude,
      double periluneAltitude,
      double latitude,
      double longitude,
      double altitude) {
    super(
        name,
        vehicle,
        buildStages(profile, parkingAltitude, periluneAltitude, latitude),
        new FlybyObjective(SolarSystemBody.MOON, periluneAltitude, PERILUNE_TOLERANCE));
    this.latitude = latitude;
    this.longitude = longitude;
    this.altitude = altitude;
  }

  @Override
  protected double getLatitude() {
    return latitude;
  }

  @Override
  protected double getLongitude() {
    return longitude;
  }

  @Override
  protected double getAltitude() {
    return altitude;
  }

  /**
   * Earth-centred with the Moon and the Sun as perturbers — the context {@code ArcTransition}
   * derives the lunar one from when the coast crosses the sphere of influence.
   */
  @Override
  public GravitationalContext gravitationalContext() {
    return GravitationalContext.earth().withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN);
  }

  private static List<MissionStage> buildStages(
      AscentProfile profile, double parkingAltitude, double periluneAltitude, double latitude) {
    List<MissionStage> stages = new ArrayList<>();
    stages.add(new VerticalAscentStage("Vertical Ascent", profile.verticalAscentDuration()));
    stages.addAll(
        AscentSequence.gravityTurn(
            profile,
            GravityTurnConstraints.forTarget(parkingAltitude),
            LaunchPlane.dueEast(latitude),
            latitude));
    stages.addAll(
        List.of(
            new AnalyticParkingInsertionStage("Parking", parkingAltitude),
            new ParkingCoastStage(PARKING_COAST_NAME),
            new TranslunarInjectionStage("Translunar injection", periluneAltitude),
            new TranslunarCoastStage(FINAL_COAST_NAME)));
    return List.copyOf(stages);
  }
}
