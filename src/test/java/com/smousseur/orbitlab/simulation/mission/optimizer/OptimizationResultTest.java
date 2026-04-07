package com.smousseur.orbitlab.simulation.mission.optimizer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OptimizationResultTest {

  @Test
  void fourArgConstructor_storesCopy_mutatingOriginalDoesNotAffectResult() {
    double[] vars = {1.0, 2.0, 3.0};
    OptimizationResult result = new OptimizationResult(vars, 0.5, null, 100);

    vars[0] = 999.0; // mutate original after construction

    assertEquals(1.0, result.bestVariables()[0], 1e-12,
        "Constructor should have cloned the array; mutation of original must not affect result");
  }

  @Test
  void bestVariables_returnsDefensiveCopy_mutatingReturnedArrayDoesNotAffectResult() {
    OptimizationResult result = new OptimizationResult(new double[]{1.0, 2.0}, 0.5, null, 10);

    double[] copy1 = result.bestVariables();
    copy1[0] = 999.0; // mutate the returned clone

    assertEquals(1.0, result.bestVariables()[0], 1e-12,
        "bestVariables() should return a fresh clone each time");
  }

  @Test
  void fourArgConstructor_stageEntryState_isNull() {
    OptimizationResult result = new OptimizationResult(new double[]{0.0}, 1.0, null, 5);
    assertNull(result.stageEntryState());
  }

  @Test
  void fourArgConstructor_fieldsMatchInputs() {
    double[] vars = {4.0, 5.0};
    OptimizationResult result = new OptimizationResult(vars, 3.14, null, 42);

    assertArrayEquals(vars, result.bestVariables(), 1e-12);
    assertEquals(3.14, result.bestCost(), 1e-12);
    assertEquals(42, result.evaluations());
  }

  @Test
  void consecutiveCalls_bestVariables_returnIndependentCopies() {
    OptimizationResult result = new OptimizationResult(new double[]{7.0, 8.0}, 0.0, null, 1);

    double[] a = result.bestVariables();
    double[] b = result.bestVariables();

    assertNotSame(a, b, "Each call to bestVariables() must return a distinct array instance");
    assertArrayEquals(a, b, 1e-12);
  }
}
