package com.smousseur.orbitlab.simulation.mission.maneuver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.mission.LunarApproachFixture;
import com.smousseur.orbitlab.simulation.mission.maneuver.LunarInsertionPlan.Arrival;
import com.smousseur.orbitlab.simulation.mission.maneuver.LunarInsertionPlan.Burn;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;

/**
 * MIS-5 / L4 §6.1 — the shared arithmetic of the lunar insertion, on the fabricated approach of
 * {@link LunarApproachFixture}.
 *
 * <p>Five cases, and one of them tests the fixture rather than the code: {@link
 * #theFixtureHasTeeth()}. Without it the other four would stay green against an implementation that
 * reads the perilune off the hyperbolic anomaly, which is the one defect of this lot that produces
 * passing tests over wrong code.
 */
class LunarInsertionPlanTest {
  private static final Logger logger = LogManager.getLogger(LunarInsertionPlanTest.class);

  /** The band L4 §5 retains, after correcting the closing criterion the découpage wrote. */
  private static final double APSIDE_BAND = 500.0;

  private static final double ECCENTRICITY_BAND = 5.0e-4;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        LunarApproachFixture.orekitDataAvailable(), "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  @DisplayName("The arrival is the perilune the approach actually flies to")
  void theArrivalIsTheFlownPerilune() {
    SpacecraftState approach = LunarApproachFixture.selenocentric();
    FlightContext context = LunarApproachFixture.lunarFlightContext();

    Arrival arrival = LunarInsertionPlan.arrivalFrom(approach, context);

    double reference = ternarySearchForPerilune(approach, context);
    double detected = arrival.atPerilune().getDate().durationFrom(approach.getDate());
    logger.info(
        "Arrival detected at {} s, ternary reference {} s, perilune {} km",
        String.format(Locale.ROOT, "%.4f", detected),
        String.format(Locale.ROOT, "%.4f", reference),
        String.format(Locale.ROOT, "%.3f", arrival.periluneAltitude() / 1000.0));

    assertEquals(reference, detected, 1.0e-2, "the detector must find the flown perilune");
    assertEquals(
        LunarApproachFixture.FLOWN_PERILUNE_ALTITUDE,
        arrival.periluneAltitude(),
        1_000.0,
        "the fixture is bisected to fly at 100 km");
  }

  /**
   * The fixture's own property, asserted rather than assumed: the flown perilune is nowhere near
   * the Keplerian one. An unperturbed approach would put the two within a metre and a millisecond,
   * and every other case here would then pass against a closed-form implementation.
   */
  @Test
  @DisplayName("The fixture has teeth: the flown perilune is far from the Keplerian one")
  void theFixtureHasTeeth() {
    SpacecraftState approach = LunarApproachFixture.selenocentric();
    FlightContext context = LunarApproachFixture.lunarFlightContext();
    KeplerianOrbit osculating = new KeplerianOrbit(approach.getOrbit());

    double closedForm = -osculating.getMeanAnomaly() / osculating.getKeplerianMeanMotion();
    double keplerianAltitude =
        osculating.getA() * (1.0 - osculating.getE()) - context.gravity().equatorialRadius();

    Arrival arrival = LunarInsertionPlan.arrivalFrom(approach, context);
    double flown = arrival.atPerilune().getDate().durationFrom(approach.getDate());

    logger.info(
        "Keplerian says {} km at {} s; flown is {} km at {} s",
        String.format(Locale.ROOT, "%.1f", keplerianAltitude / 1000.0),
        String.format(Locale.ROOT, "%.1f", closedForm),
        String.format(Locale.ROOT, "%.1f", arrival.periluneAltitude() / 1000.0),
        String.format(Locale.ROOT, "%.1f", flown));

    assertTrue(
        FastMath.abs(keplerianAltitude - arrival.periluneAltitude()) > 500_000.0,
        "the fixture must be flown perturbed, or it cannot tell a detected perilune from a computed"
            + " one; altitudes were "
            + keplerianAltitude
            + " and "
            + arrival.periluneAltitude());
    assertTrue(
        FastMath.abs(closedForm - flown) > 300.0,
        "same, on the date: the closed form and the flown perilune were "
            + (closedForm - flown)
            + " s apart");
  }

  @Test
  @DisplayName("The ignition lead is half a burn, and the perilune re-read from it agrees")
  void theIgnitionLeadIsHalfABurnBeforeThePerilune() {
    SpacecraftState approach = LunarApproachFixture.selenocentric();
    FlightContext context = LunarApproachFixture.lunarFlightContext();
    ActiveStageInfo active = active(approach);

    Arrival arrival = LunarInsertionPlan.arrivalFrom(approach, context);
    double lead = LunarInsertionPlan.ignitionLead(arrival, active);

    SpacecraftState atIgnition =
        coast(approach, context).propagate(arrival.atPerilune().getDate().shiftedBy(-lead));
    Arrival reread = LunarInsertionPlan.arrivalFrom(atIgnition, context);
    double remaining = reread.atPerilune().getDate().durationFrom(atIgnition.getDate());

    Burn burn = LunarInsertionPlan.insert(atIgnition, active, context);
    logger.info(
        "Lead {} s for a {} s burn; perilune re-read {} s ahead of ignition",
        String.format(Locale.ROOT, "%.4f", lead),
        String.format(Locale.ROOT, "%.2f", burn.duration()),
        String.format(Locale.ROOT, "%.4f", remaining));

    assertEquals(
        lead, remaining, 1.0e-3, "the perilune re-read from ignition must be the lead away");
    assertEquals(
        0.5 * burn.duration(),
        lead,
        1.0,
        "the lead is half a burn, up to the surcharge the calibration adds");
  }

  @Test
  @DisplayName("The insertion circularises the perilune it reaches, and costs the finite loss")
  void theInsertionCircularisesWithinTheBand() {
    SpacecraftState approach = LunarApproachFixture.selenocentric();
    FlightContext context = LunarApproachFixture.lunarFlightContext();
    ActiveStageInfo active = active(approach);

    Arrival arrival = LunarInsertionPlan.arrivalFrom(approach, context);
    SpacecraftState atIgnition =
        coast(approach, context)
            .propagate(
                arrival
                    .atPerilune()
                    .getDate()
                    .shiftedBy(-LunarInsertionPlan.ignitionLead(arrival, active)));

    Burn burn = LunarInsertionPlan.insert(atIgnition, active, context);
    KeplerianOrbit achieved = flyAndRead(atIgnition, burn, active, context);
    double radius = context.gravity().equatorialRadius() + burn.periluneAltitude();
    double perilune = achieved.getA() * (1.0 - achieved.getE());
    double apolune = achieved.getA() * (1.0 + achieved.getE());

    logger.info(
        "Insertion: commanded {} m/s for {} m/s impulsive (+{}), achieved {} x {} km, e = {}",
        String.format(Locale.ROOT, "%.3f", burn.commandedDeltaV()),
        String.format(Locale.ROOT, "%.3f", burn.impulsiveDeltaV()),
        String.format(Locale.ROOT, "%.3f", burn.commandedDeltaV() - burn.impulsiveDeltaV()),
        String.format(
            Locale.ROOT, "%.3f", (perilune - context.gravity().equatorialRadius()) / 1000.0),
        String.format(
            Locale.ROOT, "%.3f", (apolune - context.gravity().equatorialRadius()) / 1000.0),
        String.format(Locale.ROOT, "%.2e", achieved.getE()));

    assertEquals(radius, perilune, APSIDE_BAND, "perilune outside the L4 §5 band");
    assertEquals(radius, apolune, APSIDE_BAND, "apolune outside the L4 §5 band");
    assertTrue(achieved.getE() < ECCENTRICITY_BAND, "eccentricity was " + achieved.getE());

    double surcharge = burn.commandedDeltaV() - burn.impulsiveDeltaV();
    assertTrue(
        surcharge > 1.0 && surcharge < 10.0,
        "the finite burn must cost more than the impulse and stay a correction, got " + surcharge);
  }

  @Test
  @DisplayName("The four refusals")
  void theFourRefusals() {
    FlightContext context = LunarApproachFixture.lunarFlightContext();
    ActiveStageInfo active = active(LunarApproachFixture.selenocentric());

    // 1. The perilune is behind: the fixture, flown past its own periapsis.
    SpacecraftState outbound =
        coast(LunarApproachFixture.selenocentric(), context)
            .propagate(LunarApproachFixture.epoch().shiftedBy(70_000.0));
    assertThrows(
        OrbitlabException.class,
        () -> LunarInsertionPlan.arrivalFrom(outbound, context),
        "an approach past its perilune has nothing to insert into");

    // 2. No perilune at all: the guard stops the search first. Measured, the flown perilune tracks
    // the Keplerian one about 1:1 with a 667 km offset here, so a 600 km Keplerian aim impacts
    // while the 767 km one of the fixture flies at 100 km.
    SpacecraftState impacting =
        LunarApproachFixture.selenocentric(600_000.0, LunarApproachFixture.RAAN_DEG);
    assertThrows(
        OrbitlabException.class,
        () -> LunarInsertionPlan.arrivalFrom(impacting, context),
        "a hyperbola that meets the surface must be refused");

    // 3. A perilune below the surface, in the band the guard's -50 km floor lets through:
    // measured, Keplerian 630 to 670 km. Distinct from case 2, and the whole point of judging the
    // flown perilune rather than the Keplerian one.
    SpacecraftState grazing =
        LunarApproachFixture.selenocentric(650_000.0, LunarApproachFixture.RAAN_DEG);
    OrbitlabException refusal =
        assertThrows(
            OrbitlabException.class, () -> LunarInsertionPlan.arrivalFrom(grazing, context));
    logger.info("Sub-surface refusal: {}", refusal.getMessage());

    // 4. The depletion floor, judged on the commanded ΔV: the same approach on a tank that cannot
    // pay for it.
    SpacecraftState approach = LunarApproachFixture.selenocentric();
    Spacecraft starved =
        new Spacecraft(2_600.0, 120.0, 120.0, Payloads.LUNAR_ORBITER.akmPropulsion());
    ActiveStageInfo poor = starved.resolveActiveStage(approach.getMass());
    Arrival arrival = LunarInsertionPlan.arrivalFrom(approach, context);
    SpacecraftState atIgnition =
        coast(approach, context)
            .propagate(
                arrival
                    .atPerilune()
                    .getDate()
                    .shiftedBy(-LunarInsertionPlan.ignitionLead(arrival, active)));
    assertThrows(
        OrbitlabException.class,
        () -> LunarInsertionPlan.insert(atIgnition, poor, context),
        "a burn that would take the stage below its depletion floor must be refused");
  }

  private static ActiveStageInfo active(SpacecraftState state) {
    return LunarApproachFixture.orbiter().resolveActiveStage(state.getMass());
  }

  private static NumericalPropagator coast(SpacecraftState from, FlightContext context) {
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, OrekitService.COAST_MAX_STEP);
    propagator.setInitialState(from);
    return propagator;
  }

  private static KeplerianOrbit flyAndRead(
      SpacecraftState ignitionState, Burn burn, ActiveStageInfo active, FlightContext context) {
    PropulsionSystem propulsion = active.propulsion();
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, OrekitService.SAFE_MAX_STEP);
    propagator.setInitialState(ignitionState);
    LunarInsertionPlan.addBurn(
        propagator, ignitionState, burn.direction(), burn.duration(), propulsion);
    SpacecraftState end = propagator.propagate(LunarInsertionPlan.cutoffDate(ignitionState, burn));
    return new KeplerianOrbit(
        end.getPVCoordinates(), end.getFrame(), end.getDate(), context.gravity().mu());
  }

  /** An independent reading of the flown perilune: a ternary search on the radius. */
  private static double ternarySearchForPerilune(SpacecraftState from, FlightContext context) {
    KeplerianOrbit osculating = new KeplerianOrbit(from.getOrbit());
    double estimate = -osculating.getMeanAnomaly() / osculating.getKeplerianMeanMotion();
    NumericalPropagator propagator = coast(from, context);
    double low = estimate - 1_500.0;
    double high = estimate + 1_500.0;
    for (int i = 0; i < 80 && high - low > 1.0e-4; i++) {
      double first = low + (high - low) / 3.0;
      double second = high - (high - low) / 3.0;
      double atFirst =
          propagator.propagate(from.getDate().shiftedBy(first)).getPosition().getNorm();
      double atSecond =
          propagator.propagate(from.getDate().shiftedBy(second)).getPosition().getNorm();
      if (atFirst < atSecond) {
        high = second;
      } else {
        low = first;
      }
    }
    return 0.5 * (low + high);
  }
}
