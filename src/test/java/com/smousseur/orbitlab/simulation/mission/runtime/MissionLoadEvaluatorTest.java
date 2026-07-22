package com.smousseur.orbitlab.simulation.mission.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagePropellant;
import java.util.ArrayList;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/**
 * Fast unit test of the I7 feasibility predicate (spec 09 §6 task 2) — the {@code objectiveMet} and
 * {@code residualSufficient} decisions — exercised on synthetic ephemerides and performance reports,
 * with no propagation.
 */
class MissionLoadEvaluatorTest {

  private static final double TOL = MissionLoadEvaluator.DEFAULT_OBJECTIVE_TOLERANCE_RATIO; // 0.07

  /** Builds a two-plus-point ephemeris from (stageName, altitude) pairs; positions are dummy. */
  private static MissionEphemeris ephemerisOf(List<String> stages, List<Double> altitudes) {
    List<MissionEphemerisPoint> points = new ArrayList<>();
    AbsoluteDate t = AbsoluteDate.J2000_EPOCH;
    for (int i = 0; i < stages.size(); i++) {
      points.add(
          new MissionEphemerisPoint(
              t.shiftedBy(i), Vector3D.ZERO, Vector3D.ZERO, stages.get(i), 1_000.0, altitudes.get(i)));
    }
    return new MissionEphemeris(points);
  }

  private static MissionEphemeris coastEphemeris(double... coastAltitudes) {
    List<String> stages = new ArrayList<>();
    List<Double> alts = new ArrayList<>();
    // A couple of non-terminal points that must be ignored by the measurement.
    stages.add("Gravity turn");
    alts.add(50_000.0);
    stages.add("Transfert");
    alts.add(120_000.0);
    for (double a : coastAltitudes) {
      stages.add("Coasting");
      alts.add(a);
    }
    return ephemerisOf(stages, alts);
  }

  // ── objectiveMet ────────────────────────────────────────────────────────

  @Test
  void objectiveMet_circularOnTarget_true() {
    OrbitInsertionObjective target =
        OrbitInsertionObjective.circular(SolarSystemBody.EARTH, 400_000, 0.0);
    MissionEphemeris ephemeris = coastEphemeris(399_000, 401_000);
    assertTrue(MissionLoadEvaluator.objectiveMet(ephemeris, target, TOL));
  }

  @Test
  void objectiveMet_apogeeAtToleranceEdge_true_butBeyond_false() {
    OrbitInsertionObjective target =
        OrbitInsertionObjective.circular(SolarSystemBody.EARTH, 400_000, 0.0);
    // Max altitude exactly +7 %: within tolerance.
    assertTrue(
        MissionLoadEvaluator.objectiveMet(coastEphemeris(400_000, 428_000), target, TOL));
    // Max altitude +8 %: out of tolerance.
    assertFalse(
        MissionLoadEvaluator.objectiveMet(coastEphemeris(400_000, 432_000), target, TOL));
  }

  @Test
  void objectiveMet_perigeeTooLow_false() {
    OrbitInsertionObjective target =
        OrbitInsertionObjective.circular(SolarSystemBody.EARTH, 400_000, 0.0);
    // Min altitude −10 % (360 km) breaks the perigee band even though the apogee is fine.
    assertFalse(
        MissionLoadEvaluator.objectiveMet(coastEphemeris(360_000, 401_000), target, TOL));
  }

  @Test
  void objectiveMet_ellipticTarget_usesPerigeeAndApogeeSeparately() {
    OrbitInsertionObjective target =
        new OrbitInsertionObjective(SolarSystemBody.EARTH, 300_000, 600_000, 0.0);
    assertTrue(
        MissionLoadEvaluator.objectiveMet(coastEphemeris(295_000, 610_000), target, TOL));
    // Apogee undershoots by more than 7 %.
    assertFalse(
        MissionLoadEvaluator.objectiveMet(coastEphemeris(295_000, 550_000), target, TOL));
  }

  @Test
  void objectiveMet_noTerminalCoastSamples_false() {
    OrbitInsertionObjective target =
        OrbitInsertionObjective.circular(SolarSystemBody.EARTH, 400_000, 0.0);
    MissionEphemeris ephemeris =
        ephemerisOf(List.of("Gravity turn", "Transfert"), List.of(50_000.0, 120_000.0));
    assertFalse(MissionLoadEvaluator.objectiveMet(ephemeris, target, TOL));
  }

  // ── residualSufficient (floor vs the SIZED stage's own load) ──────────────

  /** Report for an FH-like stack: S1 burnt out, S2 sized, payload dry. */
  private static MissionPerformanceReport reportOf(
      double s2Loaded, double s2Residual, double payloadLoaded, double payloadResidual) {
    List<StagePropellant> perStage =
        List.of(
            new StagePropellant(0, 1_233_000.0, 0.0),
            new StagePropellant(1, s2Loaded, s2Residual),
            new StagePropellant(2, payloadLoaded, payloadResidual));
    double loaded = 1_233_000.0 + s2Loaded + payloadLoaded;
    return new MissionPerformanceReport(
        List.of(), 8_000.0, loaded, s2Residual + payloadResidual, perStage);
  }

  @Test
  void residualSufficient_marginAboveFloorOfSizedStageLoad_true() {
    // Heuristic-like point: 284 kg residual on a 2844 kg sized-stage load = 10 % ≥ 1 %.
    assertTrue(MissionLoadEvaluator.residualSufficient(reportOf(2_844.0, 284.0, 0.0, 0.0), 1, 0.01));
  }

  @Test
  void residualSufficient_flameOut_false() {
    // Knife-edge point: the sized stage is emptied (residual 0) → below any positive floor.
    assertFalse(MissionLoadEvaluator.residualSufficient(reportOf(1_040.0, 0.0, 0.0, 0.0), 1, 0.01));
  }

  @Test
  void residualSufficient_dividesBySizedStageNotWholeStack() {
    // 284 kg over the whole 1.24 M stack is 0.02 % (would fail a stack-wide 1 % floor), but over the
    // 2844 kg sized stage it is 10 % — the floor must use the sized-stage denominator.
    MissionPerformanceReport report = reportOf(2_844.0, 284.0, 0.0, 0.0);
    assertTrue(MissionLoadEvaluator.residualSufficient(report, 1, 0.01));
    assertFalse(MissionLoadEvaluator.residualSufficient(report, 0, 0.01)); // S1 burnt out
  }

  @Test
  void residualSufficient_ignoresPropellantOfStagesAboveTheSizedOne() {
    // bilan 10 §6: the sized stage is emptied but a loaded payload kick motor sits above it. The
    // stack-wide total (500 kg) would clear a 1 % floor on the 1040 kg sized load and wrongly
    // report the flame-out as feasible; the sized stage's own entry rejects it.
    MissionPerformanceReport report = reportOf(1_040.0, 0.0, 500.0, 500.0);
    assertTrue(report.totalPropellantResidual() >= 0.01 * 1_040.0); // the old, masking comparison
    assertFalse(MissionLoadEvaluator.residualSufficient(report, 1, 0.01));
    assertTrue(MissionLoadEvaluator.residualSufficient(report, 2, 0.01)); // the AKM is untouched
  }

  @Test
  void residualSufficient_noSizedStage_disablesFloor() {
    assertTrue(MissionLoadEvaluator.residualSufficient(reportOf(0.0, 0.0, 0.0, 0.0), -1, 0.01));
  }

  @Test
  void residualSufficient_reportWithoutPerStageSplit_fallsBackToStackTotal() {
    MissionPerformanceReport legacy =
        new MissionPerformanceReport(List.of(), 8_000.0, 1_235_844.0, 284.0);
    assertFalse(MissionLoadEvaluator.residualSufficient(legacy, 1, 0.01)); // 284 / 1.24 M < 1 %
  }
}
