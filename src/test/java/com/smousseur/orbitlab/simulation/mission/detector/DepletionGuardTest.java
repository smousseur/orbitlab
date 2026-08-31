package com.smousseur.orbitlab.simulation.mission.detector;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

class DepletionGuardTest {

  /** 100 kN at 300 s Isp → mass flow ≈ 34 kg/s. */
  private static final double THRUST = 100_000;

  private static final double ISP = 300;
  private static final double FLOOR = 10_000;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static SpacecraftState leoState(AbsoluteDate date, double mass) {
    Frame gcrf = OrekitService.get().gcrf();
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 400_000;
    double v = Math.sqrt(Constants.WGS84_EARTH_MU / r);
    return new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(0, v, 0)),
                gcrf,
                date,
                Constants.WGS84_EARTH_MU))
        .withMass(mass);
  }

  @Test
  void oversizedBurn_stoppedAtDepletionFloor() {
    AbsoluteDate date = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    // 500 kg of propellant above the floor, but a 60 s window would burn ~2 040 kg.
    SpacecraftState state = leoState(date, FLOOR + 500);

    NumericalPropagator propagator =
        OrekitService.get()
            .createTestPropagator(FlightContext.earth(), OrekitService.SAFE_MAX_STEP);
    propagator.setInitialState(state);
    propagator.addForceModel(
        new ConstantThrustManeuver(date.shiftedBy(1.0e-3), 60.0, THRUST, ISP, Vector3D.PLUS_I));
    DepletionGuard.arm(propagator, FLOOR, "test");

    SpacecraftState finalState = propagator.propagate(date.shiftedBy(61.0));

    assertEquals(FLOOR, finalState.getMass(), 1.0, "propagation must stop at the depletion floor");
    assertTrue(
        finalState.getDate().durationFrom(date) < 20.0,
        "must stop well before the scheduled window end (~14.7 s depletion)");
  }

  @Test
  void nominalBurn_untouchedByGuard() {
    AbsoluteDate date = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    // 5 000 kg of propellant above the floor: the 60 s burn (~2 040 kg) fits comfortably.
    SpacecraftState state = leoState(date, FLOOR + 5_000);

    NumericalPropagator propagator =
        OrekitService.get()
            .createTestPropagator(FlightContext.earth(), OrekitService.SAFE_MAX_STEP);
    propagator.setInitialState(state);
    propagator.addForceModel(
        new ConstantThrustManeuver(date.shiftedBy(1.0e-3), 60.0, THRUST, ISP, Vector3D.PLUS_I));
    DepletionGuard.arm(propagator, FLOOR, "test");

    SpacecraftState finalState = propagator.propagate(date.shiftedBy(61.0));

    assertEquals(61.0, finalState.getDate().durationFrom(date), 1e-6, "full window must run");
    assertTrue(
        finalState.getMass() > FLOOR + 2_900,
        "mass must reflect the nominal 2 040 kg consumption only");
  }

  // ════════════════════════════════════════════════════════════════════════
  // What the guard says when it fires — docs/bugs.md BUG-15
  // ════════════════════════════════════════════════════════════════════════

  /**
   * A burn whose duration came out of {@link Physics#computeBurnDurationCapped} ends <em>on</em>
   * the floor: the clamp is {@code remainingFuel / massFlow}, so the detector fires at the burn's
   * own scheduled cutoff. That is an under-sized stage, which the propellant-load search reads as a
   * routine verdict — hence a warning, and a message that does not accuse the mass accounting.
   */
  @Test
  void cappedBurn_reachesTheFloorAtItsCutoff_andIsReportedAsAVerdict() {
    AbsoluteDate date = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    SpacecraftState state = leoState(date, FLOOR + 500);
    // 400 m/s is far more than 500 kg buys at this mass, so the cap binds and sets the window.
    double capped = Physics.computeBurnDurationCapped(400.0, state.getMass(), ISP, THRUST, 500.0);

    List<LogEvent> events =
        captureGuardLog(
            () -> {
              NumericalPropagator propagator = testPropagator(state);
              propagator.addForceModel(
                  new ConstantThrustManeuver(
                      date.shiftedBy(1.0e-3), capped, THRUST, ISP, Vector3D.PLUS_I));
              DepletionGuard.armCappedBurn(propagator, FLOOR, "test");

              SpacecraftState finalState = propagator.propagate(date.shiftedBy(capped + 10.0));

              assertEquals(FLOOR, finalState.getMass(), 1.0, "the burn must end on the floor");
              assertEquals(
                  capped,
                  finalState.getDate().durationFrom(date),
                  0.05,
                  "the floor must be reached at the scheduled cutoff, not before it");
            });

    assertEquals(1, events.size(), "the guard must report exactly once");
    assertEquals(Level.WARN, events.getFirst().getLevel(), "a capped burn is not an error");
    assertTrue(
        events.getFirst().getMessage().getFormattedMessage().contains("capped at the floor"),
        "the message must name the cap rather than accuse the mass accounting");
  }

  /**
   * The other half: a window nothing clamped cannot reach the floor unless the accounting is wrong.
   */
  @Test
  void overScheduledBurn_reachesTheFloorEarly_andIsStillAnError() {
    AbsoluteDate date = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    SpacecraftState state = leoState(date, FLOOR + 500);

    List<LogEvent> events =
        captureGuardLog(
            () -> {
              NumericalPropagator propagator = testPropagator(state);
              propagator.addForceModel(
                  new ConstantThrustManeuver(
                      date.shiftedBy(1.0e-3), 60.0, THRUST, ISP, Vector3D.PLUS_I));
              DepletionGuard.arm(propagator, FLOOR, "test");

              propagator.propagate(date.shiftedBy(61.0));
            });

    assertEquals(1, events.size(), "the guard must report exactly once");
    assertEquals(Level.ERROR, events.getFirst().getLevel(), "an unclamped window is an error");
  }

  private static NumericalPropagator testPropagator(SpacecraftState state) {
    NumericalPropagator propagator =
        OrekitService.get()
            .createTestPropagator(FlightContext.earth(), OrekitService.SAFE_MAX_STEP);
    propagator.setInitialState(state);
    return propagator;
  }

  /**
   * Runs {@code body} with an appender attached to the guard's logger, and returns what it logged.
   */
  private static List<LogEvent> captureGuardLog(Runnable body) {
    CapturingAppender appender = new CapturingAppender();
    org.apache.logging.log4j.core.Logger guardLogger =
        (org.apache.logging.log4j.core.Logger) LogManager.getLogger(DepletionGuard.class);
    appender.start();
    guardLogger.addAppender(appender);
    try {
      body.run();
    } finally {
      guardLogger.removeAppender(appender);
      appender.stop();
    }
    return appender.events;
  }

  private static final class CapturingAppender extends AbstractAppender {
    private final List<LogEvent> events = new ArrayList<>();

    private CapturingAppender() {
      super("depletion-guard-capture", null, null, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      events.add(event.toImmutable());
    }
  }
}
