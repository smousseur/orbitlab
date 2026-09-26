package com.smousseur.orbitlab.simulation.mission.operation;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds a concrete {@link Mission} from a {@link MissionSpec} and an {@link OptimizationType},
 * resolving the stage decomposition that the spec left open.
 *
 * <p>This is the seam that decouples <em>what</em> the mission is (the spec, frozen by the wizard)
 * from <em>how</em> it is flown (the stages, a function of the mode). The optimization toggle can
 * therefore recompose the mission after the wizard closes — swapping analytic stages for their
 * CMA-ES counterparts — instead of committing to one composition at creation time.
 *
 * <p>Two independent axes drive the modes; this composer owns only the first:
 *
 * <ul>
 *   <li><b>Stage composition</b> (here): {@code FAST} yields the closed-form analytic profile;
 *       {@code BALANCED}/{@code PRECISE} yield the CMA-ES optimized transfer where one exists.
 *   <li><b>Load handling</b> (in {@code MissionPlanOptimizer}): fixed vs. minimized propellant
 *       loads, selecting the concrete {@code MissionPlanner}. Orthogonal to the stage choice.
 * </ul>
 */
public final class MissionComposer {

  /**
   * LEO targets whose perigee and apogee differ by less than this (meters) are treated as circular
   * and flown with the two-burn circular transfer rather than the single-burn elliptic one.
   */
  private static final double CIRCULAR_TOLERANCE_M = 1_000.0;

  /**
   * Highest target apogee (meters) the direct chain is composed for — the conventional ceiling of
   * low Earth orbit.
   *
   * <p><b>Why a ceiling and not a coast.</b> The direct chain has the gravity turn place the apogee
   * at the target itself, and then one transfer burn shape the orbit there. That is an <em>ascent
   * reach</em> question, not a coast question, and it is the reason a MEO is not a taller LEO: no
   * ascent puts an apogee at 20 200 km. A coast test cannot express it, and on a circular target
   * would not even have anything to measure — perigee and apogee coincide, so the transfer duration
   * is zero however high the orbit sits.
   *
   * <p>The coast has its own row in the rule, one step down: it decides whether the parking chain
   * this ceiling routes to can actually be flown, and by which stage.
   */
  private static final double DIRECT_CHAIN_APOGEE_CEILING_M = 2_000_000.0;

  /**
   * Parking altitude the high-orbit chain injects from, when the target's own perigee is not itself
   * a usable parking orbit — the altitude {@code GEOMission} has always parked at.
   */
  private static final double DEFAULT_PARKING_ALTITUDE_M = 400_000.0;

  private MissionComposer() {}

  /**
   * Whether a target apogee is beyond the ascent's reach and must therefore be flown through a
   * parking orbit.
   *
   * <p>Exposed so {@code MissionFactory} sizes the propellant for the chain that will actually be
   * composed. Those two decisions have to agree: a mission budgeted for one direct ascent and then
   * flown as parking + injection + circularization would be short of an entire apogee burn.
   *
   * @param apogeeAltitude the target apogee altitude in meters
   * @return {@code true} when the target needs the parking chain
   */
  public static boolean needsParkingOrbit(double apogeeAltitude) {
    return apogeeAltitude > DIRECT_CHAIN_APOGEE_CEILING_M;
  }

  /**
   * The altitude the high-orbit chain injects from: the target's own perigee when it is a usable
   * parking orbit, the default otherwise.
   *
   * @param perigeeAltitude the target perigee altitude in meters
   * @return the parking altitude in meters
   */
  public static double parkingAltitudeFor(double perigeeAltitude) {
    return Math.min(perigeeAltitude, DEFAULT_PARKING_ALTITUDE_M);
  }

  /**
   * Composes the mission for the given spec and optimization mode.
   *
   * @param spec the mission description (targets, vehicle, site)
   * @param mode the optimization mode driving the stage composition
   * @return the built mission, ready to hand to a {@code MissionPlanner}
   */
  public static Mission compose(MissionSpec spec, OptimizationType mode) {
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(mode, "mode");
    requireDisposalPropulsion(spec);
    requireFlyableDisposal(spec);
    Mission mission =
        switch (spec) {
          case MissionSpec.EarthOrbit earthOrbit -> composeEarthOrbit(earthOrbit, mode);
          case MissionSpec.Geo geo -> composeGeo(geo);
          case MissionSpec.Lunar lunar -> composeLunar(lunar);
          case MissionSpec.LunarOrbit lunarOrbit -> composeLunarOrbit(lunarOrbit);
        };
    // This composer is the ONLY writer of a mission's restitution horizon. Carrying it on the spec
    // and applying it here,
    // rather than threading it through the constructor chains, is what makes it survive a mode
    // toggle or a wizard edit: both replace the Mission, neither replaces the spec.
    mission.setHorizon(spec.horizon());
    // Same rule, same single writer, for the atmosphere choice.
    mission.setAtmosphere(spec.atmosphere());
    return mission;
  }

  /**
   * PHY-10's invariant, enforced once for every construction path. A mission that leaves its
   * payload in a stable orbit must fly one that carries propulsion, so it can dispose of itself at
   * end of life; an inert payload is refused here rather than propagated into an orbit it can never
   * leave.
   *
   * <p>Expressed on {@code propulsion() != null} — the engine's presence, not its tank — because
   * the disposal reserve lives outside {@code propellantCapacity} and is what the engine burns. It
   * does <b>not</b> subsume the GEO / lunar-orbit burn checks, which refuse a payload whose engine
   * is present but too weak for the mission's own delegated burn; this one refuses the absence of
   * an engine, and fires first for a truly inert payload.
   */
  private static void requireDisposalPropulsion(MissionSpec spec) {
    if (spec.type().deliversToStableOrbit()
        && spec.configuration().payload().propulsion() == null) {
      throw new OrbitlabException(
          String.format(
              Locale.ROOT,
              "A %s mission delivers its payload to a stable orbit, where it must be able to dispose"
                  + " of itself at end of life — but %s carries no propulsion. Fly a payload with an"
                  + " engine of its own.",
              spec.type().displayName(),
              payloadLabel(spec)));
    }
  }

  /**
   * The payload name a refusal message names it by: the catalog id when the spec remembers one, a
   * generic placeholder otherwise.
   */
  private static String payloadLabel(MissionSpec spec) {
    return spec.configuration().hasPayloadId() ? spec.configuration().payloadId() : "the payload";
  }

  /**
   * A payload that carries a disposal reserve must fly on a mission that can actually burn it, or
   * the reserve rides to orbit as dead mass forever. Checked in order, each against a different
   * owner's rule:
   *
   * <ol>
   *   <li>only an {@link MissionSpec.EarthOrbit} composes into the direct chain that attaches
   *       {@code DeorbitTail} — a GEO or lunar chain carries no tail at all;
   *   <li>{@link #needsParkingOrbit(double)} routes the target through the parking chain ({@link
   *       GEOMission}), whose chain carries no tail either, direct-chain spec or not;
   *   <li>{@link PropellantBudget#isReentryRegime(double)} on the <b>apogee</b>: above it, that
   *       orbit's end of life is a graveyard re-orbit, not a reentry, so the reserve was sized for
   *       a burn this mission was never going to fly. (2) and (3) share today's 2 000 km number,
   *       but they are the ascent-reach ceiling and the IADC regime boundary, two different owners'
   *       rules that happen to coincide rather than one rule checked twice;
   *   <li>{@link EarthOrbitMission#dropsUpperStage(Spacecraft)}: without a nominal load of its own,
   *       the payload never gets the upper stage dropped and stays attached to it to the end, so
   *       the tail would burn the upper stage's engine instead of the payload's own reserve.
   * </ol>
   *
   * <p>A payload with no reserve carries none of this and goes through untouched.
   */
  private static void requireFlyableDisposal(MissionSpec spec) {
    if (!spec.configuration().payload().hasDisposalReserve()) {
      return;
    }
    if (!(spec instanceof MissionSpec.EarthOrbit earthOrbit)) {
      throw new OrbitlabException(
          String.format(
              Locale.ROOT,
              "The payload carries a disposal reserve, but only a %s mission can deorbit its"
                  + " payload at end of mission; a %s mission cannot. Fly the payload on a %s"
                  + " mission, or fly it without deorbiting it at end of mission.",
              MissionType.LEO.displayName(),
              spec.type().displayName(),
              MissionType.LEO.displayName()));
    }
    double apogeeAltitude = earthOrbit.apogeeAltitude();
    if (needsParkingOrbit(apogeeAltitude)) {
      throw new OrbitlabException(
          String.format(
              Locale.ROOT,
              "The payload carries a disposal reserve, but the target apogee %.0f km is reached"
                  + " through a parking orbit, from which the payload is not deorbited. Fly a"
                  + " lower target that the ascent reaches on its own, or fly the payload without"
                  + " deorbiting it at end of mission.",
              apogeeAltitude / 1000.0));
    }
    if (!PropellantBudget.isReentryRegime(apogeeAltitude)) {
      throw new OrbitlabException(
          String.format(
              Locale.ROOT,
              "The payload carries a disposal reserve, but the target apogee %.0f km is above the"
                  + " reentry regime: that orbit's end of life is a graveyard re-orbit, not a"
                  + " reentry. Fly a lower target within the reentry regime, or fly the payload"
                  + " without deorbiting it at end of mission.",
              apogeeAltitude / 1000.0));
    }
    if (!EarthOrbitMission.dropsUpperStage(earthOrbit.configuration().payload())) {
      throw new OrbitlabException(
          String.format(
              Locale.ROOT,
              "The payload carries a disposal reserve, but %s carries no nominal propellant load"
                  + " of its own to fly a final trim, so the upper stage is never dropped and the"
                  + " deorbit burn would be flown by the upper stage instead of the payload. Fly a"
                  + " payload that can fly its own final trim, or fly the payload without"
                  + " deorbiting it at end of mission.",
              payloadLabel(earthOrbit)));
    }
  }

  /**
   * The composition rule of spec §6.1 — a readable decision rather than a new {@code MissionType}.
   *
   * <table>
   *   <caption>Which chain an Earth-orbit target is flown with</caption>
   *   <tr><th>Condition</th><th>Chain</th></tr>
   *   <tr><td>Target apogee within the ascent's reach</td>
   *       <td>ascent + direct transfer (the historical chain)</td></tr>
   *   <tr><td>Otherwise, with a long-coast upper stage or a payload carrying the apogee ΔV</td>
   *       <td>parking + injection + circularization (the GEO chain)</td></tr>
   *   <tr><td>Otherwise</td>
   *       <td>explicit refusal, naming the stage and the duration it is short of</td></tr>
   * </table>
   *
   * <p>This is what makes "P2 is parameter entry" true: the wizard does not have to know which
   * chain a target needs, and cannot offer one the vehicle cannot fly.
   */
  private static Mission composeEarthOrbit(MissionSpec.EarthOrbit spec, OptimizationType mode) {
    LaunchPlane plane = spec.launchPlane();
    if (spec.apogeeAltitude() > DIRECT_CHAIN_APOGEE_CEILING_M) {
      return composeHighOrbit(spec, plane);
    }
    if (mode == OptimizationType.FAST) {
      // Analytic Hohmann transfer — the historical default. Kept byte-for-byte identical to the
      // pre-toggle path (non-regression baseline).
      return new EarthOrbitMission(
          spec.name(),
          spec.configuration(),
          spec.perigeeAltitude(),
          spec.apogeeAltitude(),
          plane,
          spec.latitude(),
          spec.longitude(),
          spec.altitude());
    }
    // CMA-ES optimized transfer, and the two shapes take DIFFERENT stages — read the direction
    // carefully, this comment used to state it backwards:
    //
    //  - circular  -> TransfertTwoManeuverStage, TWO burns (burn 1 optimized on 4 variables, the
    //    circularization at the next apoapsis resolved deterministically). It only supports
    //    circular targets, which is exactly why the tolerance above exists;
    //  - elliptic  -> TransfertManeuverStage, a SINGLE burn shaping the whole ellipse, graded on
    //    apogee, perigee and eccentricity in one aggregate cost.
    //
    // See OptimizationType for what each path measurably buys — and for the open question on the
    // elliptic one, which misses the apogee by 16 km where the analytic profile hits it to 562 m.
    boolean circular =
        Math.abs(spec.apogeeAltitude() - spec.perigeeAltitude()) < CIRCULAR_TOLERANCE_M;
    return circular
        ? EarthOrbitMission.circularWithOptimizedTransfer(
            spec.name(),
            spec.configuration(),
            spec.perigeeAltitude(),
            plane,
            spec.latitude(),
            spec.longitude(),
            spec.altitude())
        : EarthOrbitMission.ellipticWithOptimizedTransfer(
            spec.name(),
            spec.configuration(),
            spec.perigeeAltitude(),
            spec.apogeeAltitude(),
            plane,
            spec.latitude(),
            spec.longitude(),
            spec.altitude());
  }

  /**
   * The second and third rows of §6.1: a target beyond the ascent's reach is flown as the GEO
   * profile does it — park low, inject, coast to apogee, circularize there — or refused.
   *
   * <p><b>The coast is the whole question.</b> Between the injection burn and the circularization
   * the vehicle is shut down for half a transfer period: 2 h 58 for 400 km → 20 200 km. An Ariane
   * 62 upper stage declares 6 h and holds it; a Falcon Heavy one declares 2 h and does not. Where
   * the stage cannot, the burn can still be delegated to the payload's apogee kick motor, exactly
   * as the GEO profile already delegates it — which is why a Falcon Heavy reaches GEO at all, with
   * a 5 h 15 coast it could never survive itself.
   *
   * <p>When neither holds, the mission is refused here rather than propagated into a stage that
   * quietly outlives its own specification. The message names the stage and the duration it is
   * short of, so the answer ("fly Ariane 62, or take a payload with a kick motor") is in the
   * failure.
   *
   * <p>The optimization mode is not an argument, for the reason {@link #composeGeo} gives: both
   * CMA-ES transfer stages are written for the direct chain and have no counterpart here, so every
   * mode yields the analytic profile. The mode still differentiates these missions on the
   * load-handling axis, in {@code MissionPlanOptimizer}.
   */
  private static Mission composeHighOrbit(MissionSpec.EarthOrbit spec, LaunchPlane plane) {
    double parkingAltitude = parkingAltitudeFor(spec.perigeeAltitude());
    double transferCoast = Physics.hohmannTransferDuration(parkingAltitude, spec.apogeeAltitude());

    StageModel upperStage = spec.configuration().launcher().stages().getLast();
    boolean stageHoldsTheCoast = upperStage.capabilities().canCoastFor(transferCoast);

    // Asked as a quantity and not as a presence. While
    // GEO_SAT was the only propelled payload, "does it carry a tank" and "can it fly the apogee
    // burn" were the same question; a station-keeping thruster answers yes to the first and no to
    // the second, and would otherwise have been signed up for a 1 700 m/s circularization.
    double burnDeltaV =
        PropellantBudget.apogeeBurnDeltaV(parkingAltitude, spec.apogeeAltitude(), 0.0);
    double payloadDeltaV = PropellantBudget.payloadDeltaV(spec.configuration().payload());

    if (!stageHoldsTheCoast && payloadDeltaV < burnDeltaV) {
      throw new OrbitlabException(
          String.format(
              Locale.ROOT,
              "Target apogee %.0f km is out of the ascent's reach, so it needs a parking orbit and"
                  + " a %.2f h coast to apogee — but '%s' (%s) declares a maximum coast of %.2f h"
                  + " and the payload carries %.0f m/s where the apogee burn needs %.0f. Fly a"
                  + " launcher whose upper stage holds the coast, or a payload with a kick motor.",
              spec.apogeeAltitude() / 1000.0,
              transferCoast / 3600.0,
              upperStage.name(),
              spec.configuration().launcher().displayName(),
              upperStage.capabilities().maxCoastDuration() / 3600.0,
              payloadDeltaV,
              burnDeltaV));
    }

    // The GEO chain, parameterised. GEOMission is named for the orbit it was written for, not for
    // the only one it can fly: its target altitude and final inclination have always been
    // arguments, and a MEO is that chain with 20 200 km and 55° instead of 35 786 km and 0°.
    //
    // The plane is handed to the ASCENT, not just to the apogee burn — the one thing the GEO
    // profile does differently, because an equatorial plane is not reachable from a launch site and
    // a 55° one is. See the constructor's javadoc for what it cost to find that out.
    return new GEOMission(
        spec.name(),
        spec.configuration(),
        parkingAltitude,
        spec.apogeeAltitude(),
        plane,
        spec.latitude(),
        spec.longitude(),
        spec.altitude(),
        plane.targetInclinationDeg());
  }

  /**
   * The lunar chain: ascent, parking insertion, parking coast to the injection point, translunar
   * injection, translunar coast.
   *
   * <p>The optimization mode is not an argument, for the same reason {@link #composeGeo} gives: no
   * stage of the lunar half of the chain has a CMA-ES counterpart, so every mode yields the same
   * composition. What the mission optimizes is its <em>ascent</em>, exactly like any Earth mission,
   * and that is unaffected by the mode. The mode still differentiates the mission on the
   * load-handling axis in {@code MissionPlanOptimizer}.
   */
  private static Mission composeLunar(MissionSpec.Lunar spec) {
    return new LunarFlybyMission(
        spec.name(),
        spec.configuration(),
        spec.parkingAltitude(),
        spec.periluneAltitude(),
        spec.latitude(),
        spec.longitude(),
        spec.altitude());
  }

  /**
   * The lunar orbit chain: the lunar chain above, plus an upper-stage jettison, a translunar coast
   * that <em>ends</em> at the sphere of influence, a selenocentric approach, the insertion burn,
   * and a terminal coast flown around the Moon.
   *
   * <p>The optimization mode is not an argument, for the reason {@link #composeLunar} gives: no
   * stage of the lunar half of the chain has a CMA-ES counterpart. What this mission optimizes is
   * its ascent, like any Earth mission.
   */
  private static Mission composeLunarOrbit(MissionSpec.LunarOrbit spec) {
    return new LunarOrbitMission(
        spec.name(),
        spec.configuration(),
        spec.parkingAltitude(),
        spec.orbitAltitude(),
        spec.latitude(),
        spec.longitude(),
        spec.altitude());
  }

  /**
   * GEO has a single (analytic) composition, which is why this one takes no {@code mode}: both
   * CMA-ES transfer stages are LEO-only, with no GEO equivalent yet. Every mode therefore yields
   * the analytic profile; the mode still differentiates GEO on the load-handling axis in
   * MissionPlanOptimizer.
   */
  private static Mission composeGeo(MissionSpec.Geo spec) {
    return new GEOMission(
        spec.name(),
        spec.configuration(),
        spec.parkingAltitude(),
        spec.targetAltitude(),
        spec.latitude(),
        spec.longitude(),
        spec.altitude(),
        spec.finalInclination());
  }
}
