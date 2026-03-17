package com.smousseur.orbitlab.simulation.orbit;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

class OrbitRuntimeSlotTest {

  private OrbitRuntimeSlot slot;

  @BeforeEach
  void setUp() {
    slot = new OrbitRuntimeSlot(SolarSystemBody.EARTH);
  }

  private static OrbitSnapshot earthSnapshot(
      AbsoluteDate centerDate, Vector3D[] positions, long version) {
    return new OrbitSnapshot(SolarSystemBody.EARTH, centerDate, 86400.0, 100.0, positions, version);
  }

  @Test
  void constructor_nullBody_shouldThrowException() {
    assertThrows(NullPointerException.class, () -> new OrbitRuntimeSlot(null));
  }

  @Test
  void body_shouldReturnCorrectBody() {
    assertEquals(SolarSystemBody.EARTH, slot.body());

    OrbitRuntimeSlot marsSlot = new OrbitRuntimeSlot(SolarSystemBody.MARS);
    assertEquals(SolarSystemBody.MARS, marsSlot.body());
  }

  @Test
  void snapshot_initiallyNull_shouldReturnNull() {
    assertNull(slot.snapshot());
  }

  @Test
  void publish_shouldUpdateSnapshot() {
    AbsoluteDate centerDate = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    OrbitSnapshot snapshot =
        earthSnapshot(centerDate, new Vector3D[] {Vector3D.ZERO, Vector3D.PLUS_I}, 1);

    slot.publish(snapshot);
    assertEquals(snapshot, slot.snapshot());
  }

  @Test
  void publish_multipleSnapshots_shouldUpdateToLatest() {
    AbsoluteDate centerDate = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    OrbitSnapshot snapshot1 =
        earthSnapshot(centerDate, new Vector3D[] {Vector3D.ZERO, Vector3D.PLUS_I}, 1);
    OrbitSnapshot snapshot2 =
        earthSnapshot(
            centerDate.shiftedBy(1000.0), new Vector3D[] {Vector3D.ZERO, Vector3D.PLUS_J}, 2);

    slot.publish(snapshot1);
    assertEquals(snapshot1, slot.snapshot());

    slot.publish(snapshot2);
    assertEquals(snapshot2, slot.snapshot());
  }

  @Test
  void publish_nullSnapshot_shouldThrowException() {
    assertThrows(NullPointerException.class, () -> slot.publish(null));
  }

  @Test
  void newVersion_shouldIncrementFromOne() {
    assertEquals(1L, slot.newVersion());
    assertEquals(2L, slot.newVersion());
    assertEquals(3L, slot.newVersion());
  }

  @Test
  void tryStartJob_initiallyTrue_shouldReturnTrue() {
    assertTrue(slot.tryStartJob());
  }

  @Test
  void tryStartJob_whenAlreadyInFlight_shouldReturnFalse() {
    assertTrue(slot.tryStartJob());
    assertFalse(slot.tryStartJob());
    assertFalse(slot.tryStartJob());
  }

  @Test
  void endJob_shouldAllowNewJobToStart() {
    assertTrue(slot.tryStartJob());
    assertFalse(slot.tryStartJob());

    slot.endJob();
    assertTrue(slot.tryStartJob());
  }

  @Test
  void tryStartJobAndEndJob_multipleCycles_shouldWork() {
    for (int i = 0; i < 5; i++) {
      assertTrue(slot.tryStartJob(), "Cycle " + i + " should start successfully");
      assertFalse(slot.tryStartJob(), "Cycle " + i + " should prevent concurrent start");
      slot.endJob();
    }
  }

  @Test
  void requestRebuildIfNeeded_snapshotNull_shouldTriggerRebuild() {
    AbsoluteDate now = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());

    AtomicInteger callCount = new AtomicInteger(0);

    slot.requestRebuildIfNeeded(
        now, s -> true, // predicate doesn't matter if snapshot is null
        (body, date) -> {
          assertEquals(SolarSystemBody.EARTH, body);
          assertEquals(now, date);
          callCount.incrementAndGet();
        });

    assertEquals(1, callCount.get());
  }

  @Test
  void requestRebuildIfNeeded_snapshotInComfort_shouldNotTriggerRebuild() {
    AbsoluteDate centerDate = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    OrbitSnapshot snapshot =
        earthSnapshot(centerDate, new Vector3D[] {Vector3D.ZERO, Vector3D.PLUS_I}, 1);
    slot.publish(snapshot);

    AbsoluteDate now = centerDate.shiftedBy(50.0);
    AtomicInteger callCount = new AtomicInteger(0);

    slot.requestRebuildIfNeeded(
        now, s -> true, // in comfort
        (body, date) -> callCount.incrementAndGet());

    assertEquals(0, callCount.get());
  }

  @Test
  void requestRebuildIfNeeded_snapshotOutOfComfort_shouldTriggerRebuild() {
    AbsoluteDate centerDate = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    OrbitSnapshot snapshot =
        earthSnapshot(centerDate, new Vector3D[] {Vector3D.ZERO, Vector3D.PLUS_I}, 1);
    slot.publish(snapshot);

    AbsoluteDate now = centerDate.shiftedBy(200.0);
    AtomicInteger callCount = new AtomicInteger(0);

    slot.requestRebuildIfNeeded(
        now, s -> false, // out of comfort
        (body, date) -> {
          assertEquals(SolarSystemBody.EARTH, body);
          assertEquals(now, date);
          callCount.incrementAndGet();
        });

    assertEquals(1, callCount.get());
  }

  @Test
  void requestRebuildIfNeeded_multipleCallsOutOfComfort_shouldOnlyTriggerOnce() {
    AbsoluteDate centerDate = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    OrbitSnapshot snapshot =
        earthSnapshot(centerDate, new Vector3D[] {Vector3D.ZERO, Vector3D.PLUS_I}, 1);
    slot.publish(snapshot);

    AbsoluteDate now = centerDate.shiftedBy(200.0);
    AtomicInteger callCount = new AtomicInteger(0);

    // First call should trigger
    slot.requestRebuildIfNeeded(now, s -> false, (body, date) -> callCount.incrementAndGet());
    assertEquals(1, callCount.get());

    // Second call should NOT trigger (job already in flight)
    slot.requestRebuildIfNeeded(now, s -> false, (body, date) -> callCount.incrementAndGet());
    assertEquals(1, callCount.get());

    // Third call should still NOT trigger
    slot.requestRebuildIfNeeded(now, s -> false, (body, date) -> callCount.incrementAndGet());
    assertEquals(1, callCount.get());
  }

  @Test
  void requestRebuildIfNeeded_afterEndJob_shouldAllowNewRebuild() {
    AbsoluteDate centerDate = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    OrbitSnapshot snapshot =
        earthSnapshot(centerDate, new Vector3D[] {Vector3D.ZERO, Vector3D.PLUS_I}, 1);
    slot.publish(snapshot);

    AbsoluteDate now = centerDate.shiftedBy(200.0);
    AtomicInteger callCount = new AtomicInteger(0);

    // First rebuild
    slot.requestRebuildIfNeeded(now, s -> false, (body, date) -> callCount.incrementAndGet());
    assertEquals(1, callCount.get());

    // End job
    slot.endJob();

    // Second rebuild should now work
    slot.requestRebuildIfNeeded(now, s -> false, (body, date) -> callCount.incrementAndGet());
    assertEquals(2, callCount.get());
  }

  @Test
  void requestRebuildIfNeeded_nullNow_shouldThrowException() {
    assertThrows(
        NullPointerException.class,
        () -> slot.requestRebuildIfNeeded(null, s -> true, (body, date) -> {}));
  }

  @Test
  void requestRebuildIfNeeded_nullPredicate_shouldThrowException() {
    AbsoluteDate now = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    assertThrows(
        NullPointerException.class,
        () -> slot.requestRebuildIfNeeded(now, null, (body, date) -> {}));
  }

  @Test
  void requestRebuildIfNeeded_nullCallback_shouldThrowException() {
    AbsoluteDate now = new AbsoluteDate(2024, 1, 1, 0, 0, 0, TimeScalesFactory.getUTC());
    assertThrows(
        NullPointerException.class, () -> slot.requestRebuildIfNeeded(now, s -> true, null));
  }

  @Test
  void concurrency_newVersion_shouldBeThreadSafe() throws InterruptedException {
    int threadCount = 10;
    int iterationsPerThread = 1000;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      new Thread(
              () -> {
                for (int j = 0; j < iterationsPerThread; j++) {
                  slot.newVersion();
                }
                latch.countDown();
              })
          .start();
    }

    latch.await();
    long expectedVersion = threadCount * iterationsPerThread + 1;
    assertEquals(expectedVersion, slot.newVersion());
  }

  @Test
  void concurrency_tryStartJobAndEndJob_shouldBeThreadSafe() throws InterruptedException {
    int threadCount = 10;
    int iterationsPerThread = 100;
    AtomicInteger successCount = new AtomicInteger(0);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      new Thread(
              () -> {
                for (int j = 0; j < iterationsPerThread; j++) {
                  if (slot.tryStartJob()) {
                    successCount.incrementAndGet();
                    // Simulate some work
                    try {
                      Thread.sleep(1);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                    slot.endJob();
                  }
                }
                latch.countDown();
              })
          .start();
    }

    latch.await();
    assertTrue(successCount.get() > 0);
    assertTrue(successCount.get() <= threadCount * iterationsPerThread);
  }
}
