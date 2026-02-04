package com.smousseur.orbitlab.app;

import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SimulationClockTest {

  @Test
  void pausedDoesNotAdvanceOnUpdate() {
    SimulationClock clock = new SimulationClock(AbsoluteDate.J2000_EPOCH);
    clock.pause();

    AbsoluteDate t0 = clock.now();
    clock.update(1.0);
    assertEquals(t0, clock.now());
  }

  @Test
  void playAdvancesTimeAccordingToTpfAndSpeed() {
    SimulationClock clock = new SimulationClock(AbsoluteDate.J2000_EPOCH);
    clock.setSpeed(10.0);
    clock.play();

    AbsoluteDate t0 = clock.now();
    clock.update(0.5); // 0.5s app time * 10 = 5s sim time
    AbsoluteDate t1 = clock.now();

    assertEquals(t0.shiftedBy(5.0), t1);
  }

  @Test
  void negativeSpeedRewinds() {
    SimulationClock clock = new SimulationClock(AbsoluteDate.J2000_EPOCH.shiftedBy(100.0));
    clock.setSpeed(-2.0);
    clock.play();

    AbsoluteDate t0 = clock.now();
    clock.update(3.0); // rewind 6 seconds
    assertEquals(t0.shiftedBy(-6.0), clock.now());
  }

  @Test
  void seekEmitsSeekPerformedAndTimeChangedUser() {
    SimulationClock clock = new SimulationClock(AbsoluteDate.J2000_EPOCH);
    List<SimulationClock.ClockEvent> events = new ArrayList<>();
    clock.subscribe(events::add);

    AbsoluteDate target = AbsoluteDate.J2000_EPOCH.shiftedBy(42.0);
    clock.seek(target);

    assertEquals(target, clock.now());
    assertEquals(2, events.size());

    assertInstanceOf(SimulationClock.SeekPerformed.class, events.get(0));
    assertInstanceOf(SimulationClock.TimeChanged.class, events.get(1));

    SimulationClock.SeekPerformed seek = (SimulationClock.SeekPerformed) events.get(0);
    assertEquals(AbsoluteDate.J2000_EPOCH, seek.oldTime());
    assertEquals(target, seek.newTime());

    SimulationClock.TimeChanged tc = (SimulationClock.TimeChanged) events.get(1);
    assertEquals(SimulationClock.ChangeCause.USER, tc.cause());
    assertEquals(AbsoluteDate.J2000_EPOCH, tc.oldTime());
    assertEquals(target, tc.newTime());
  }

  @Test
  void speedAndPlayEmitEvents() {
    SimulationClock clock = new SimulationClock(AbsoluteDate.J2000_EPOCH);
    List<SimulationClock.ClockEvent> events = new ArrayList<>();
    clock.subscribe(events::add);

    clock.setSpeed(100.0);
    clock.play();

    assertEquals(1, events.size());
    assertInstanceOf(SimulationClock.SpeedChanged.class, events.get(0));

    SimulationClock.SpeedChanged sc = (SimulationClock.SpeedChanged) events.get(0);
    assertEquals(1.0, sc.oldSpeed());
    assertEquals(100.0, sc.newSpeed());
  }

  @Test
  void nowIsReadableFromOtherThreadsWhileUpdating() throws Exception {
    SimulationClock clock = new SimulationClock(AbsoluteDate.J2000_EPOCH);
    clock.setSpeed(60.0);
    clock.play();

    try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
      AtomicBoolean stop = new AtomicBoolean(false);
      CountDownLatch started = new CountDownLatch(1);

      Future<?> reader =
          pool.submit(
              () -> {
                started.countDown();
                AbsoluteDate last = clock.now();
                while (!stop.get()) {
                  AbsoluteDate cur = clock.now();
                  assertNotNull(cur);
                  // Not asserting monotonic because negative speeds could exist; here speed is
                  // positive.
                  assertTrue(cur.compareTo(last) >= 0);
                  last = cur;
                }
              });

      started.await(1, TimeUnit.SECONDS);

      // Simulate a bunch of JME frames
      for (int i = 0; i < 200; i++) {
        clock.update(1.0 / 60.0);
      }

      stop.set(true);
      reader.get(1, TimeUnit.SECONDS);

      // the final time should be advanced ~200/60 * 60 = 200 seconds
      assertEquals(AbsoluteDate.J2000_EPOCH.shiftedBy(200.0), clock.now());
      pool.awaitTermination(2, TimeUnit.SECONDS);
    }
  }
}
