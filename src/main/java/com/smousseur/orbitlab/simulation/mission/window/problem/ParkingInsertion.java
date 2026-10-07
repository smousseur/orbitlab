package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.ParkingAscentMission;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionLoadEvaluator;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentSequence;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import java.util.Locale;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * Where a lunar mission's ascent really leaves it in parking — measured by flying that ascent once,
 * and carried from the lift-off it was flown at to any other.
 *
 * <p><b>Why a flight and not a formula.</b> The launch window prices and confirms the injection
 * from the parking state. Posed at the pad at the instant of lift-off, that state made the window
 * inject on a passage the vehicle cannot reach: the chain is in parking about an hour after
 * lift-off, and takes the first passage after that. The ascent alone ({@link ParkingAscentMission})
 * flown by the optimizer, budget and seed of the production compute lands, at the same date and
 * loads, on the insertion that compute flies — to the kilogram, in some fifty seconds.
 *
 * <p><b>Carried through the Earth-fixed frame.</b> The ascent turns with the pad and changes little
 * from one lift-off to the next, so its insertion expressed in ITRF, brought back to GCRF at
 * another lift-off plus the same delay, is the insertion an ascent flown there reaches: measured
 * from Canaveral over five days, to 0.002° of plane and 0.4 s of injection.
 *
 * @param earthFixed the insertion position and velocity, in ITRF
 * @param mecoDelay the time from lift-off to the end of the gravity turn (s)
 * @param insertionDelay the time from lift-off to the end of the parking insertion (s)
 * @param downrange the angle from the pad at lift-off to the insertion point, in the flown plane
 *     and in the direction of motion (rad, in {@code [0, 2π)})
 * @param mass the mass in parking (kg)
 */
public record ParkingInsertion(
    PVCoordinates earthFixed,
    double mecoDelay,
    double insertionDelay,
    double downrange,
    double mass) {
  private static final Logger logger = LogManager.getLogger(ParkingInsertion.class);

  /** The launch azimuth, due east: the plane the window measures the ascent on. */
  private static final double DUE_EAST = FastMath.PI / 2;

  /**
   * @param earthFixed the insertion position and velocity, in ITRF
   * @param mecoDelay the time from lift-off to the end of the gravity turn (s)
   * @param insertionDelay the time from lift-off to the end of the parking insertion (s)
   * @param downrange the angle from the pad to the insertion point in the flown plane (rad)
   * @param mass the mass in parking (kg)
   */
  public ParkingInsertion {
    Objects.requireNonNull(earthFixed, "earthFixed");
  }

  /**
   * Flies the ascent of a lunar mission due east, alone, and measures where it leaves the vehicle.
   *
   * @param configuration the launcher model, propellant loads and payload of the mission
   * @param atmosphere the atmosphere the mission flies against
   * @param latitude the launch site latitude in degrees
   * @param longitude the launch site longitude in degrees
   * @param altitude the launch site altitude in meters
   * @param parkingAltitude the parking orbit altitude in meters
   * @param liftOff the lift-off date the ascent is flown at
   * @return the measured insertion
   * @throws OrbitlabException when the ascent does not reach its parking orbit: a stage of the
   *     ascent refused, or the force model failed on the way
   */
  public static ParkingInsertion fly(
      LaunchConfiguration configuration,
      AtmosphereModel atmosphere,
      double latitude,
      double longitude,
      double altitude,
      double parkingAltitude,
      AbsoluteDate liftOff) {
    ParkingAscentMission mission =
        new ParkingAscentMission(
            "Parking ascent",
            configuration,
            parkingAltitude,
            latitude,
            longitude,
            altitude,
            LaunchPlane.dueEast(latitude));
    mission.setAtmosphere(atmosphere);
    mission.setCurrentState(mission.getInitialState(liftOff));
    long started = System.nanoTime();
    MissionComputeResult result;
    try {
      result =
          new MissionOptimizer(
                  mission,
                  MissionLoadEvaluator.DEFAULT_OPTIMIZER_MAX_EVALUATIONS,
                  MissionPlanOptimizer.SEED)
              .optimize();
    } catch (OrbitlabException refused) {
      throw refused;
    } catch (RuntimeException failure) {
      // What the force model throws on a state it cannot evaluate — measured: NRLMSISE00 turning
      // infinite during the parking insertion of one Canaveral date. The ascent did not reach its
      // parking orbit all the same, and the caller dates nothing on it.
      throw new OrbitlabException(
          String.format(
              Locale.ROOT,
              "the ascent flown at %s does not reach its %.0f km parking orbit: %s",
              liftOff,
              parkingAltitude / 1000.0,
              failure.getMessage()),
          failure);
    }

    MissionEphemerisPoint cutOff = null;
    MissionEphemerisPoint insertion = null;
    for (MissionEphemerisPoint point : result.ephemeris().allPoints()) {
      if (AscentSequence.SECOND_BURN_NAME.equals(point.stageName())) {
        cutOff = point;
      } else if (ParkingAscentMission.INSERTION_NAME.equals(point.stageName())) {
        insertion = point;
      }
    }
    if (cutOff == null || insertion == null) {
      throw new OrbitlabException(
          String.format(
              Locale.ROOT,
              "the ascent does not reach its %.0f km parking orbit",
              parkingAltitude / 1000.0));
    }

    Vector3D position = insertion.position();
    Vector3D velocity = insertion.velocity();
    Vector3D normal = Vector3D.crossProduct(position, velocity).normalize();
    Vector3D pad = new LaunchSitePlane(latitude, longitude, altitude, DUE_EAST).positionAt(liftOff);
    Vector3D padInPlane = pad.subtract(normal.scalarMultiply(pad.dotProduct(normal))).normalize();
    Vector3D towards = position.normalize();
    double downrange =
        FastMath.atan2(
            Vector3D.crossProduct(padInPlane, towards).dotProduct(normal),
            padInPlane.dotProduct(towards));
    if (downrange < 0.0) {
      downrange += 2.0 * FastMath.PI;
    }
    ParkingInsertion measured =
        new ParkingInsertion(
            OrekitService.get()
                .gcrf()
                .getTransformTo(OrekitService.get().itrf(), insertion.time())
                .transformPVCoordinates(new PVCoordinates(position, velocity)),
            cutOff.time().durationFrom(liftOff),
            insertion.time().durationFrom(liftOff),
            downrange,
            insertion.mass());
    logger.info(
        "Parking ascent flown at {} in {} s: cut-off +{} s, insertion +{} s, {}° downrange, {} kg",
        liftOff,
        String.format(Locale.ROOT, "%.1f", (System.nanoTime() - started) / 1e9),
        String.format(Locale.ROOT, "%.1f", measured.mecoDelay()),
        String.format(Locale.ROOT, "%.1f", measured.insertionDelay()),
        String.format(Locale.ROOT, "%.2f", FastMath.toDegrees(downrange)),
        String.format(Locale.ROOT, "%.1f", measured.mass()));
    return measured;
  }

  /**
   * The insertion as a lift-off at {@code liftOff} reaches it: the measured state put back in GCRF
   * at {@code liftOff} plus the insertion delay.
   *
   * <p><b>Rebuilt from position and velocity alone.</b> The frame transformation leaves the state
   * an acceleration that is not Keplerian, and Orekit then shifts it by a quadratic expansion —
   * absurd over a parking coast, where it was measured thousands of kilometres off.
   *
   * @param liftOff the lift-off date
   * @return the parking state at insertion, in GCRF, with the measured mass
   */
  public SpacecraftState carriedTo(AbsoluteDate liftOff) {
    AbsoluteDate date = liftOff.shiftedBy(insertionDelay);
    PVCoordinates carried =
        OrekitService.get()
            .itrf()
            .getTransformTo(OrekitService.get().gcrf(), date)
            .transformPVCoordinates(earthFixed);
    return new SpacecraftState(
            new CartesianOrbit(
                new TimeStampedPVCoordinates(date, carried.getPosition(), carried.getVelocity()),
                OrekitService.get().gcrf(),
                Constants.WGS84_EARTH_MU))
        .withMass(mass);
  }

  /**
   * The insertion rebuilt on a plane the ascent was not flown on — a free-azimuth lift-off — from
   * the scalars measured due east: a circular orbit of {@code radius} entered {@link
   * #insertionDelay} after {@code liftOff}, {@link #downrange} past the pad in the direction of
   * motion, its plane turned by the nodal regression since the cut-off.
   *
   * <p>Coarser than {@link #carriedTo}: measured from Canaveral due east, the same scalars posed on
   * the theoretical plane put the injection within 6 s and the misalignment within 0.04° to 0.06°
   * of the chain, where the carried insertion is within 0.4 s and 0.002°.
   *
   * @param liftOff the lift-off date
   * @param pad the pad's inertial position at lift-off, in GCRF
   * @param normal the unit normal of the plane the ascent is predicted to fly, which holds the pad
   * @param radius the parking radius (m)
   * @return the parking state at insertion, in GCRF, with the measured mass
   */
  SpacecraftState onPlane(AbsoluteDate liftOff, Vector3D pad, Vector3D normal, double radius) {
    Rotation sinceCutOff = nodalRegression(radius, normal, insertionDelay - mecoDelay);
    Vector3D direction =
        sinceCutOff.applyTo(
            new Rotation(normal, downrange, RotationConvention.VECTOR_OPERATOR)
                .applyTo(pad.normalize()));
    Vector3D velocity =
        Vector3D.crossProduct(sinceCutOff.applyTo(normal), direction)
            .scalarMultiply(FastMath.sqrt(Constants.WGS84_EARTH_MU / radius));
    return new SpacecraftState(
            new CartesianOrbit(
                new TimeStampedPVCoordinates(
                    liftOff.shiftedBy(insertionDelay), direction.scalarMultiply(radius), velocity),
                OrekitService.get().gcrf(),
                Constants.WGS84_EARTH_MU))
        .withMass(mass);
  }

  /**
   * The secular J2 regression of the node of a circular orbit over {@code seconds}: a turn about
   * the polar axis at {@code (3/2)·n·C20·(Re/a)²·cos i} — westward on a prograde orbit, {@code C20}
   * being negative. Over a parking coast it leaves the misalignment within 0.027° of the chain's at
   * the injection, measured from Canaveral over five days.
   *
   * @param radius the orbit radius (m)
   * @param normal the unit normal of the orbit
   * @param seconds the time over which the node regresses (s)
   * @return the rotation that turns a vector of the orbit with its node
   */
  static Rotation nodalRegression(double radius, Vector3D normal, double seconds) {
    double meanMotion = FastMath.sqrt(Constants.WGS84_EARTH_MU / (radius * radius * radius));
    double ratio = Constants.WGS84_EARTH_EQUATORIAL_RADIUS / radius;
    double rate = 1.5 * meanMotion * Constants.WGS84_EARTH_C20 * ratio * ratio * normal.getZ();
    return new Rotation(Vector3D.PLUS_K, rate * seconds, RotationConvention.VECTOR_OPERATOR);
  }
}
