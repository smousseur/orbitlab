package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smousseur.orbitlab.engine.Easing;
import com.smousseur.orbitlab.engine.RetrogradeFlipConfig;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * The flip schedule: when the primary is turned around for a retrograde burn, and by how much, as a
 * pure function of the date.
 */
class RetrogradeFlipsTest {

  private static final double X = 300.0;
  private static final double HALF_TURN = Math.PI / 2.0;
  private static final double TOLERANCE = 1e-9;
  private static final RetrogradeFlipConfig CONFIG =
      new RetrogradeFlipConfig(X, Easing.SMOOTHSTEP, -0.5);

  private static AbsoluteDate epoch;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  private static AbsoluteDate at(double seconds) {
    return epoch.shiftedBy(seconds);
  }

  private static BurnTimeline.Burn burn(double start, double end, double cosTheta) {
    return new BurnTimeline.Burn(
        at(start), at(end), 1, StageRole.UPPER, 10.0, 0.0, cosTheta, 1.0, 1.0);
  }

  private static RetrogradeFlips flips(List<BurnTimeline.Burn> burns, List<Double> barriers) {
    List<AbsoluteDate> dates = new ArrayList<>();
    for (double barrier : barriers) {
      dates.add(at(barrier));
    }
    return RetrogradeFlips.of(burns, dates, at(0.0), at(100_000.0), CONFIG);
  }

  private static double angle(RetrogradeFlips flips, double seconds) {
    return flips.angleAt(at(seconds));
  }

  @Test
  void aBurnAboveTheThresholdNeverTurns() {
    RetrogradeFlips flips =
        flips(List.of(burn(10_000.0, 10_100.0, 1.0), burn(20_000.0, 20_050.0, -0.0)), List.of());
    for (double t = 0.0; t <= 30_000.0; t += 50.0) {
      assertEquals(0.0, angle(flips, t), TOLERANCE, "at " + t);
    }
  }

  @Test
  void aRetrogradeBurnIsFlownTailFirst() {
    RetrogradeFlips flips = flips(List.of(burn(10_000.0, 10_400.0, -0.9971)), List.of());
    for (double t = 10_000.0; t < 10_400.0; t += 25.0) {
      assertEquals(Math.PI, angle(flips, t), TOLERANCE, "at " + t);
    }
  }

  @Test
  void theTurnTakesFlipSecondsEachWay() {
    RetrogradeFlips flips = flips(List.of(burn(10_000.0, 10_400.0, -1.0)), List.of());
    assertEquals(0.0, angle(flips, 10_000.0 - X - 1.0), TOLERANCE);
    assertEquals(0.0, angle(flips, 10_000.0 - X), TOLERANCE);
    assertEquals(HALF_TURN, angle(flips, 10_000.0 - X / 2.0), TOLERANCE);
    assertEquals(HALF_TURN, angle(flips, 10_400.0 + X / 2.0), TOLERANCE);
    assertEquals(0.0, angle(flips, 10_400.0 + X), TOLERANCE);
    assertEquals(0.0, angle(flips, 10_400.0 + X + 1.0), TOLERANCE);
  }

  @Test
  void aPrecedingBurnSqueezesTheTurnAndNeverTurnsInsideIt() {
    RetrogradeFlips flips =
        flips(List.of(burn(9_000.0, 9_900.0, 1.0), burn(10_000.0, 10_400.0, -1.0)), List.of());
    for (double t = 9_000.0; t < 9_900.0; t += 10.0) {
      assertEquals(0.0, angle(flips, t), TOLERANCE, "inside the prograde burn at " + t);
    }
    assertEquals(0.0, angle(flips, 9_900.0), TOLERANCE);
    assertEquals(HALF_TURN, angle(flips, 9_950.0), TOLERANCE);
    assertEquals(Math.PI, angle(flips, 10_000.0), TOLERANCE);
  }

  @Test
  void aFollowingBurnSqueezesTheReturn() {
    RetrogradeFlips flips =
        flips(List.of(burn(10_000.0, 10_400.0, -1.0), burn(10_500.0, 11_000.0, 1.0)), List.of());
    assertEquals(HALF_TURN, angle(flips, 10_450.0), TOLERANCE);
    for (double t = 10_500.0; t < 11_000.0; t += 10.0) {
      assertEquals(0.0, angle(flips, t), TOLERANCE, "inside the prograde burn at " + t);
    }
  }

  @Test
  void twoRetrogradesCloseTogetherStayTurnedBetween() {
    RetrogradeFlips flips =
        flips(List.of(burn(10_000.0, 10_400.0, -1.0), burn(10_600.0, 11_000.0, -1.0)), List.of());
    for (double t = 10_000.0; t < 11_000.0; t += 10.0) {
      assertEquals(Math.PI, angle(flips, t), TOLERANCE, "at " + t);
    }
    assertEquals(HALF_TURN, angle(flips, 11_000.0 + X / 2.0), TOLERANCE);
  }

  @Test
  void twoRetrogradesFarApartTurnBackBetween() {
    RetrogradeFlips flips =
        flips(List.of(burn(10_000.0, 10_400.0, -1.0), burn(11_400.0, 11_800.0, -1.0)), List.of());
    assertEquals(0.0, angle(flips, 10_900.0), TOLERANCE);
    assertEquals(HALF_TURN, angle(flips, 10_550.0), TOLERANCE);
    assertEquals(HALF_TURN, angle(flips, 11_250.0), TOLERANCE);
  }

  @Test
  void aBarrierBoundsTheTurnLikeABurn() {
    RetrogradeFlips flips = flips(List.of(burn(10_000.0, 10_400.0, -1.0)), List.of(10_600.0));
    assertEquals(HALF_TURN, angle(flips, 10_500.0), TOLERANCE);
    assertEquals(0.0, angle(flips, 10_600.0), TOLERANCE);
    assertEquals(0.0, angle(flips, 10_650.0), TOLERANCE);
  }

  @Test
  void aBarrierBetweenTwoRetrogradesKeepsThemApart() {
    RetrogradeFlips flips =
        flips(
            List.of(burn(10_000.0, 10_400.0, -1.0), burn(10_600.0, 11_000.0, -1.0)),
            List.of(10_500.0));
    assertEquals(0.0, angle(flips, 10_500.0), TOLERANCE);
    assertEquals(HALF_TURN, angle(flips, 10_450.0), TOLERANCE);
    assertEquals(HALF_TURN, angle(flips, 10_550.0), TOLERANCE);
  }

  @Test
  void theEphemerisBoundsTheTurn() {
    List<BurnTimeline.Burn> burns = List.of(burn(100.0, 400.0, -1.0));
    RetrogradeFlips flips = RetrogradeFlips.of(burns, List.of(), at(0.0), at(500.0), CONFIG);
    assertEquals(HALF_TURN, angle(flips, 50.0), TOLERANCE);
    assertEquals(HALF_TURN, angle(flips, 450.0), TOLERANCE);
    assertEquals(0.0, angle(flips, 500.0), TOLERANCE);
    assertEquals(0.0, angle(flips, 600.0), TOLERANCE);
  }

  @Test
  void theAngleIsAFunctionOfTheDateAlone() {
    RetrogradeFlips flips =
        flips(
            List.of(
                burn(9_000.0, 9_900.0, 1.0),
                burn(10_000.0, 10_400.0, -1.0),
                burn(10_600.0, 11_000.0, -1.0)),
            List.of(12_000.0));
    List<Double> dates = new ArrayList<>();
    for (double t = 8_000.0; t <= 13_000.0; t += 7.0) {
      dates.add(t);
    }
    List<Double> forward = new ArrayList<>();
    for (double t : dates) {
      forward.add(angle(flips, t));
    }
    List<Integer> order = new ArrayList<>();
    for (int i = 0; i < dates.size(); i++) {
      order.add(i);
    }
    Collections.shuffle(order, new Random(42));
    for (int i : order) {
      assertEquals(forward.get(i), angle(flips, dates.get(i)), 0.0, "at " + dates.get(i));
    }
  }

  @Test
  void nothingTurnsWithoutBurns() {
    assertEquals(0.0, RetrogradeFlips.NONE.angleAt(at(1_000.0)), 0.0);
  }
}
