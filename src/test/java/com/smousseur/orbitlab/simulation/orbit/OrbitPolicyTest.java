package com.smousseur.orbitlab.simulation.orbit;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

class OrbitPolicyTest {

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void stepSeconds_validInputs_shouldComputeCorrectStep() {
    assertEquals(100.0, OrbitPolicy.stepSeconds(1000.0, 10), 1e-9);
    assertEquals(50.0, OrbitPolicy.stepSeconds(100.0, 2), 1e-9);
    assertEquals(1.0, OrbitPolicy.stepSeconds(100.0, 100), 1e-9);
  }

  @Test
  void stepSeconds_invalidPeriod_shouldThrowException() {
    assertThrows(IllegalArgumentException.class, () -> OrbitPolicy.stepSeconds(-100.0, 10));
    assertThrows(IllegalArgumentException.class, () -> OrbitPolicy.stepSeconds(0.0, 10));
    assertThrows(IllegalArgumentException.class, () -> OrbitPolicy.stepSeconds(Double.NaN, 10));
    assertThrows(
        IllegalArgumentException.class,
        () -> OrbitPolicy.stepSeconds(Double.POSITIVE_INFINITY, 10));
  }

  @Test
  void stepSeconds_invalidNPoints_shouldThrowException() {
    assertThrows(IllegalArgumentException.class, () -> OrbitPolicy.stepSeconds(1000.0, 1));
    assertThrows(IllegalArgumentException.class, () -> OrbitPolicy.stepSeconds(1000.0, 0));
    assertThrows(IllegalArgumentException.class, () -> OrbitPolicy.stepSeconds(1000.0, -5));
  }

  @Test
  void comfortMarginSeconds_validInputs_shouldClampCorrectly() {
    // raw = 10 * 5 = 50, min = 10, max = 1000 * 0.1 = 100 → result = 50
    double margin = OrbitPolicy.comfortMarginSeconds(1000.0, 5.0, 10, 10.0, 0.1);
    assertEquals(50.0, margin, 1e-9);

    // raw = 5 * 10 = 50, min = 60, max = 1000 * 0.2 = 200 → result = 60 (clamped to min)
    margin = OrbitPolicy.comfortMarginSeconds(1000.0, 10.0, 5, 60.0, 0.2);
    assertEquals(60.0, margin, 1e-9);

    // raw = 100 * 10 = 1000, min = 10, max = 1000 * 0.5 = 500 → result = 500 (clamped to max)
    margin = OrbitPolicy.comfortMarginSeconds(1000.0, 10.0, 100, 10.0, 0.5);
    assertEquals(500.0, margin, 1e-9);

    // raw = 0 * 10 = 0, min = 20, max = 1000 * 0.3 = 300 → result = 20 (clamped to min)
    margin = OrbitPolicy.comfortMarginSeconds(1000.0, 10.0, 0, 20.0, 0.3);
    assertEquals(20.0, margin, 1e-9);
  }

  @Test
  void comfortMarginSeconds_invalidInputs_shouldThrowException() {
    // Invalid periodSeconds
    assertThrows(
        IllegalArgumentException.class,
        () -> OrbitPolicy.comfortMarginSeconds(-1000.0, 5.0, 10, 10.0, 0.1));
    assertThrows(
        IllegalArgumentException.class,
        () -> OrbitPolicy.comfortMarginSeconds(Double.NaN, 5.0, 10, 10.0, 0.1));

    // Invalid stepSeconds
    assertThrows(
        IllegalArgumentException.class,
        () -> OrbitPolicy.comfortMarginSeconds(1000.0, -5.0, 10, 10.0, 0.1));
    assertThrows(
        IllegalArgumentException.class,
        () -> OrbitPolicy.comfortMarginSeconds(1000.0, Double.NaN, 10, 10.0, 0.1));

    // Invalid marginPoints
    assertThrows(
        IllegalArgumentException.class,
        () -> OrbitPolicy.comfortMarginSeconds(1000.0, 5.0, -1, 10.0, 0.1));

    // Invalid mMinSeconds
    assertThrows(
        IllegalArgumentException.class,
        () -> OrbitPolicy.comfortMarginSeconds(1000.0, 5.0, 10, -10.0, 0.1));
    assertThrows(
        IllegalArgumentException.class,
        () -> OrbitPolicy.comfortMarginSeconds(1000.0, 5.0, 10, Double.NaN, 0.1));

    // Invalid mMaxFraction
    assertThrows(
        IllegalArgumentException.class,
        () -> OrbitPolicy.comfortMarginSeconds(1000.0, 5.0, 10, 10.0, 0.0));
    assertThrows(
        IllegalArgumentException.class,
        () -> OrbitPolicy.comfortMarginSeconds(1000.0, 5.0, 10, 10.0, 1.5));
    assertThrows(
        IllegalArgumentException.class,
        () -> OrbitPolicy.comfortMarginSeconds(1000.0, 5.0, 10, 10.0, -0.1));
  }

  @Test
  void isInComfort_withinMargin_shouldReturnTrue() {
    AbsoluteDate centerDate = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    OrbitSnapshot snapshot =
        new OrbitSnapshot(
            SolarSystemBody.EARTH,
            centerDate,
            86400.0,
            100.0,
            new Vector3D[] {Vector3D.ZERO, Vector3D.PLUS_I},
            1);

    // Exactly at center
    assertTrue(OrbitPolicy.isInComfort(snapshot, centerDate, 100.0));

    // Within margin (50 seconds before)
    AbsoluteDate before = centerDate.shiftedBy(-50.0);
    assertTrue(OrbitPolicy.isInComfort(snapshot, before, 100.0));

    // Within margin (50 seconds after)
    AbsoluteDate after = centerDate.shiftedBy(50.0);
    assertTrue(OrbitPolicy.isInComfort(snapshot, after, 100.0));

    // At exact boundary
    AbsoluteDate boundary = centerDate.shiftedBy(100.0);
    assertTrue(OrbitPolicy.isInComfort(snapshot, boundary, 100.0));
  }

  @Test
  void isInComfort_outsideMargin_shouldReturnFalse() {
    AbsoluteDate centerDate = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    OrbitSnapshot snapshot =
        new OrbitSnapshot(
            SolarSystemBody.EARTH,
            centerDate,
            86400.0,
            100.0,
            new Vector3D[] {Vector3D.ZERO, Vector3D.PLUS_I},
            1);

    // Outside margin (150 seconds before)
    AbsoluteDate before = centerDate.shiftedBy(-150.0);
    assertFalse(OrbitPolicy.isInComfort(snapshot, before, 100.0));

    // Outside margin (150 seconds after)
    AbsoluteDate after = centerDate.shiftedBy(150.0);
    assertFalse(OrbitPolicy.isInComfort(snapshot, after, 100.0));
  }

  @Test
  void isInComfort_nullSnapshot_shouldReturnFalse() {
    AbsoluteDate t = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    assertFalse(OrbitPolicy.isInComfort(null, t, 100.0));
  }

  @Test
  void isInComfort_nullDate_shouldThrowException() {
    AbsoluteDate centerDate = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    OrbitSnapshot snapshot =
        new OrbitSnapshot(
            SolarSystemBody.EARTH,
            centerDate,
            86400.0,
            100.0,
            new Vector3D[] {Vector3D.ZERO, Vector3D.PLUS_I},
            1);

    assertThrows(NullPointerException.class, () -> OrbitPolicy.isInComfort(snapshot, null, 100.0));
  }

  @Test
  void isInComfort_invalidMargin_shouldThrowException() {
    AbsoluteDate centerDate = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    AbsoluteDate t = centerDate.shiftedBy(50.0);
    OrbitSnapshot snapshot =
        new OrbitSnapshot(
            SolarSystemBody.EARTH,
            centerDate,
            86400.0,
            100.0,
            new Vector3D[] {Vector3D.ZERO, Vector3D.PLUS_I},
            1);

    assertThrows(IllegalArgumentException.class, () -> OrbitPolicy.isInComfort(snapshot, t, -10.0));
    assertThrows(
        IllegalArgumentException.class, () -> OrbitPolicy.isInComfort(snapshot, t, Double.NaN));
  }

  @Test
  void snapCenter_shouldSnapToNearestMultiple() {
    AbsoluteDate anchor = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    double snapStep = 100.0;

    // Exactly on grid
    AbsoluteDate t1 = anchor.shiftedBy(300.0);
    assertEquals(anchor.shiftedBy(300.0), OrbitPolicy.snapCenter(t1, anchor, snapStep));

    // Between grid points, closer to 300
    AbsoluteDate t2 = anchor.shiftedBy(270.0);
    assertEquals(anchor.shiftedBy(300.0), OrbitPolicy.snapCenter(t2, anchor, snapStep));

    // Between grid points, closer to 200
    AbsoluteDate t3 = anchor.shiftedBy(240.0);
    assertEquals(anchor.shiftedBy(200.0), OrbitPolicy.snapCenter(t3, anchor, snapStep));

    // Exactly at midpoint (rounds to nearest even)
    AbsoluteDate t4 = anchor.shiftedBy(250.0);
    assertEquals(anchor.shiftedBy(300.0), OrbitPolicy.snapCenter(t4, anchor, snapStep));

    // Negative offset
    AbsoluteDate t5 = anchor.shiftedBy(-150.0);
    assertEquals(anchor.shiftedBy(-100.0), OrbitPolicy.snapCenter(t5, anchor, snapStep));
  }

  @Test
  void snapCenter_atAnchor_shouldReturnAnchor() {
    AbsoluteDate anchor = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    assertEquals(anchor, OrbitPolicy.snapCenter(anchor, anchor, 100.0));
  }

  @Test
  void snapCenter_nullInputs_shouldThrowException() {
    AbsoluteDate anchor = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    AbsoluteDate t = anchor.shiftedBy(50.0);

    assertThrows(NullPointerException.class, () -> OrbitPolicy.snapCenter(null, anchor, 100.0));
    assertThrows(NullPointerException.class, () -> OrbitPolicy.snapCenter(t, null, 100.0));
  }

  @Test
  void snapCenter_invalidSnapStep_shouldThrowException() {
    AbsoluteDate anchor = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    AbsoluteDate t = anchor.shiftedBy(50.0);

    assertThrows(IllegalArgumentException.class, () -> OrbitPolicy.snapCenter(t, anchor, 0.0));
    assertThrows(IllegalArgumentException.class, () -> OrbitPolicy.snapCenter(t, anchor, -100.0));
    assertThrows(
        IllegalArgumentException.class, () -> OrbitPolicy.snapCenter(t, anchor, Double.NaN));
  }
}
