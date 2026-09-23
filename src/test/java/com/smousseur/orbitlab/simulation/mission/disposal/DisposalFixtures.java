package com.smousseur.orbitlab.simulation.mission.disposal;

import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import java.util.List;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * A payload flying alone, at the epoch and in the plane every disposal measurement was taken at.
 *
 * <p>The payload is the only vehicle of the stack, so whatever mass a state carries resolves to it
 * and its depletion floor is its dry mass — the situation at the end of a mission once the upper
 * stage has been dropped, without having to fly the ascent to get there.
 */
public final class DisposalFixtures {

  /** The plane of every disposal measurement: the ISS inclination. */
  public static final double INCLINATION_DEG = 51.6;

  private DisposalFixtures() {}

  /**
   * The epoch of every disposal measurement. A method rather than a constant: the UTC scale cannot
   * be built before Orekit's data is loaded.
   *
   * @return 2026-01-01T12:00:00Z
   */
  public static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  /**
   * A mission whose only vehicle is {@code payload}, flown under the production atmosphere.
   *
   * @param payload the payload, which is the whole stack
   * @return the mission, with no stage of its own
   */
  public static Mission payloadMission(Spacecraft payload) {
    Mission mission =
        new Mission("disposal fixture", new VehicleStack(List.of(payload)), List.of(), null) {
          @Override
          public SpacecraftState getInitialState(AbsoluteDate initialDate) {
            return null;
          }
        };
    mission.setAtmosphere(AtmosphereModel.NRLMSISE);
    return mission;
  }

  /**
   * The same mission as {@link #payloadMission}, carrying a disposal tail — as an Earth-orbit
   * mission does when its payload carries a reserve.
   *
   * @param payload the payload, which is the whole stack
   * @return the mission, with no stage of its own and a deorbit tail
   */
  public static Mission payloadMissionWithTail(Spacecraft payload) {
    Mission mission =
        new Mission("disposal fixture", new VehicleStack(List.of(payload)), List.of(), null) {
          {
            setDisposalTail(new DeorbitTail());
          }

          @Override
          public SpacecraftState getInitialState(AbsoluteDate initialDate) {
            return null;
          }
        };
    mission.setAtmosphere(AtmosphereModel.NRLMSISE);
    return mission;
  }

  /**
   * A state on a {@code perigeeAltitude} × {@code apogeeAltitude} orbit, both measured above the
   * equatorial radius, at the given true anomaly.
   *
   * @param perigeeAltitude the perigee altitude (m)
   * @param apogeeAltitude the apogee altitude (m)
   * @param trueAnomalyDeg the true anomaly (°), 0 at the perigee
   * @param mass the spacecraft mass (kg)
   * @return the state, in the Earth context's inertial frame
   */
  public static SpacecraftState orbitState(
      double perigeeAltitude, double apogeeAltitude, double trueAnomalyDeg, double mass) {
    GravitationalContext earth = GravitationalContext.earth();
    double rp = earth.equatorialRadius() + perigeeAltitude;
    double ra = earth.equatorialRadius() + apogeeAltitude;
    return new SpacecraftState(
            new KeplerianOrbit(
                0.5 * (rp + ra),
                (ra - rp) / (ra + rp),
                FastMath.toRadians(INCLINATION_DEG),
                0.0,
                0.0,
                FastMath.toRadians(trueAnomalyDeg),
                PositionAngleType.TRUE,
                earth.inertialFrame(),
                epoch(),
                earth.mu()))
        .withMass(mass);
  }

  /**
   * The osculating Keplerian orbit of {@code state} about the Earth.
   *
   * @param state the state
   * @return its osculating orbit
   */
  public static KeplerianOrbit keplerian(SpacecraftState state) {
    return new KeplerianOrbit(
        state.getPVCoordinates(),
        state.getFrame(),
        state.getDate(),
        GravitationalContext.earth().mu());
  }

  /**
   * The osculating perigee altitude, spherical above the equatorial radius — the convention the
   * disposal target is written in.
   *
   * @param state the state
   * @return the perigee altitude (m)
   */
  public static double perigeeAltitude(SpacecraftState state) {
    KeplerianOrbit orbit = keplerian(state);
    return orbit.getA() * (1.0 - orbit.getE()) - GravitationalContext.earth().equatorialRadius();
  }
}
