package com.smousseur.orbitlab.simulation.mission.bench;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.flight.DragContext;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import java.util.Locale;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.AltitudeDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.PVCoordinates;

/**
 * PHY-5 / L0 measurement §5.2 — the debris-reentry integration-step sweep. NOT a gate: it changes
 * no {@code src/main} and asserts nothing. It answers the L1 go/no-go — does a jettisoned launcher
 * piece cost so much to propagate under drag that K debris (K ≤ 6) cannot be replayed for display,
 * and does a coarse integrator tolerance tame it?
 *
 * <p>Run with {@code -Dorbitlab.probe=true --tests '*Phy5ReentryStepSweepTest*'}. The states are
 * <b>representative</b>, not extracted from a live optimize — the step regime is a property of the
 * ballistic coefficient and the atmosphere traversed, robust to the exact entry state. Four regimes
 * a debris can enter: the three a dense launcher piece actually enters with the catalog frontal
 * aerodynamics (Falcon Heavy booster, 10.5 m² / Cd 0.4, 22 t → BC ~5 200 kg/m²), plus one low-BC
 * object to bracket the worst case (a tumbling or light piece the catalog does not model).
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class Phy5ReentryStepSweepTest {

  /** One Falcon Heavy side booster, from {@code Launchers.FALCON_HEAVY}: 10.5 m², Cd 0.4, 22 t. */
  private static final AerodynamicProperties BOOSTER_AERO = new AerodynamicProperties(10.5, 0.4);

  private static final double BOOSTER_DRY_MASS = 22_000.0;

  /** A low-ballistic-coefficient object: light and broad, as a tumbling piece would present. */
  private static final AerodynamicProperties LOW_BC_AERO = new AerodynamicProperties(50.0, 1.5);

  private static final double LOW_BC_MASS = 2_000.0;

  /** The tolerance sweep: the old tight pair, today's optimize pair, and two looser candidates. */
  private static final double[][] TOLERANCES = {
    {1e-8, 1e-10}, {1e-5, 1e-7}, {1e-3, 1e-5}, {1e-1, 1e-3}
  };

  /** Safety cap: abort a run past this many steps rather than let an unbounded decay hang. */
  private static final long STEP_CAP = 1_000_000L;

  private OrekitService service;
  private GravitationalContext earth;
  private OneAxisEllipsoid ellipsoid;
  private AbsoluteDate epoch;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void reentryStepSweep() {
    service = OrekitService.get();
    earth = GravitationalContext.earth();
    ellipsoid = service.getEarthEllipsoid();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());

    // The three regimes a jettisoned launcher piece actually enters with the catalog aerodynamics,
    // then a low-BC bracket. Horizon caps an object that would not reach the floor in the display
    // window (D5.3); the floor STOPs the suborbital ones earlier.
    runScenario(
        "Booster suborbital (70 km, 2.3 km/s)",
        BOOSTER_AERO,
        BOOSTER_DRY_MASS,
        70_000.0,
        new Vector3D(150.0, 2_300.0, 0.0),
        3_000.0);
    runScenario(
        "Core suborbital (150 km, 5.5 km/s)",
        BOOSTER_AERO,
        BOOSTER_DRY_MASS,
        150_000.0,
        new Vector3D(300.0, 5_500.0, 0.0),
        3_000.0);
    runScenario(
        "Dense debris decaying (130 km circular)",
        BOOSTER_AERO,
        BOOSTER_DRY_MASS,
        130_000.0,
        new Vector3D(0.0, 7_826.0, 0.0),
        14_400.0);
    runScenario(
        "Low-BC debris decaying (130 km circular)",
        LOW_BC_AERO,
        LOW_BC_MASS,
        130_000.0,
        new Vector3D(0.0, 7_826.0, 0.0),
        14_400.0);
    // The unbounded case D5.3 exists to prevent: the dense debris propagated toward the floor over
    // a 2-day decay instead of capped at the display horizon.
    runScenario(
        "Dense debris to floor (2-day horizon)",
        BOOSTER_AERO,
        BOOSTER_DRY_MASS,
        130_000.0,
        new Vector3D(0.0, 7_826.0, 0.0),
        172_800.0);
  }

  private void runScenario(
      String name,
      AerodynamicProperties aero,
      double mass,
      double altitude,
      Vector3D velocity,
      double horizonSeconds) {
    FlightContext context =
        new FlightContext(earth, new DragContext(aero, AtmosphereModel.NRLMSISE));
    Vector3D position = new Vector3D(6_378_137.0 + altitude, 0.0, 0.0);
    SpacecraftState initialState =
        new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(position, velocity), service.gcrf(), epoch, earth.mu()),
            mass);

    double bc = mass / (aero.dragCoefficient() * aero.crossSection());
    System.out.printf(
        Locale.ROOT,
        "%nPHY-5 / L0 §5.2 — %s | %.1f m² Cd %.1f, %.0f kg, BC %.0f kg/m², horizon %.0f s%n",
        name,
        aero.crossSection(),
        aero.dragCoefficient(),
        mass,
        bc,
        horizonSeconds);
    System.out.printf(
        Locale.ROOT,
        "%-16s %-14s %-14s %-12s %s%n",
        "absTol/relTol",
        "steps",
        "wall(ms)",
        "end(s)",
        "outcome");
    for (double[] tol : TOLERANCES) {
      runOne(context, initialState, horizonSeconds, tol[0], tol[1]);
    }
  }

  private void runOne(
      FlightContext context,
      SpacecraftState initialState,
      double horizonSeconds,
      double absTol,
      double relTol) {
    System.setProperty(OrekitService.OPT_ABS_TOL_PROPERTY, Double.toString(absTol));
    System.setProperty(OrekitService.OPT_REL_TOL_PROPERTY, Double.toString(relTol));
    try {
      NumericalPropagator propagator =
          service.createOptimizationPropagator(context, OrekitService.COAST_MAX_STEP);
      propagator.setInitialState(initialState);

      long[] steps = {0L};
      propagator
          .getMultiplexer()
          .add(
              interpolator -> {
                steps[0]++;
                if (steps[0] > STEP_CAP) {
                  throw new IllegalStateException("step cap " + STEP_CAP + " reached");
                }
              });

      // Geodetic-0 floor (D5): stop before the integrator hits invalid sub-surface altitude.
      propagator.addEventDetector(
          new AltitudeDetector(0.0, ellipsoid)
              .withMaxCheck(30.0)
              .withThreshold(1.0)
              .withHandler((s, detector, increasing) -> Action.STOP));

      long t0 = System.nanoTime();
      String outcome;
      double endSeconds;
      try {
        SpacecraftState end = propagator.propagate(epoch, epoch.shiftedBy(horizonSeconds));
        endSeconds = end.getDate().durationFrom(epoch);
        double endAltitude =
            ellipsoid
                .transform(
                    end.getPosition(ellipsoid.getBodyFrame()),
                    ellipsoid.getBodyFrame(),
                    end.getDate())
                .getAltitude();
        outcome =
            endSeconds < horizonSeconds - 1.0
                ? String.format(Locale.ROOT, "floor at %.0f m", endAltitude)
                : String.format(Locale.ROOT, "horizon, still %.0f km", endAltitude / 1000.0);
      } catch (RuntimeException e) {
        endSeconds = Double.NaN;
        outcome = "THREW: " + e.getClass().getSimpleName();
      }
      long wallMs = (System.nanoTime() - t0) / 1_000_000L;

      System.out.printf(
          Locale.ROOT,
          "%-16s %-14d %-14d %-12s %s%n",
          absTol + "/" + relTol,
          steps[0],
          wallMs,
          Double.isNaN(endSeconds) ? "-" : String.format(Locale.ROOT, "%.1f", endSeconds),
          outcome);
    } finally {
      System.clearProperty(OrekitService.OPT_ABS_TOL_PROPERTY);
      System.clearProperty(OrekitService.OPT_REL_TOL_PROPERTY);
    }
  }
}
