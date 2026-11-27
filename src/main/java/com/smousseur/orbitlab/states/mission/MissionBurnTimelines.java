package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The primary's burn timeline, built off the render thread and served by the identity of the
 * ephemeris it was built for.
 *
 * <p>Reading the burns walks the whole raw ephemeris — over a hundred thousand samples on a GEO
 * mission — and its cost was measured from 1 ms to 65 ms on the same mission, cause unknown: over a
 * frame's budget, so it is built on an executor and the renderer reads whatever has been published
 * since. A result is served only for the very ephemeris it was built from, as the renderer itself
 * detects a recomputation, and a result overtaken by a newer request is dropped rather than
 * published over it. Until the timeline is ready, nothing fires.
 */
final class MissionBurnTimelines {

  private static final Logger logger = LogManager.getLogger(MissionBurnTimelines.class);

  /**
   * A timeline and the very ephemeris it was read from.
   *
   * @param source the ephemeris, matched by identity
   * @param timeline its burns
   */
  private record Published(MissionEphemeris source, BurnTimeline timeline) {}

  private MissionEphemeris requested;
  private volatile Published published;

  /**
   * Starts reading the burns of a primary's ephemeris, replacing any request still running.
   *
   * @param primary the primary's raw ephemeris
   * @param vehicle the vehicle that flew it
   * @param executor where the timeline is built
   */
  void request(MissionEphemeris primary, Vehicle vehicle, Executor executor) {
    Objects.requireNonNull(primary, "primary");
    Objects.requireNonNull(vehicle, "vehicle");
    synchronized (this) {
      requested = primary;
    }
    try {
      CompletableFuture.supplyAsync(
              () -> new Published(primary, BurnTimeline.of(primary, vehicle)), executor)
          .whenComplete(
              (built, failure) -> {
                if (failure != null) {
                  logger.error("The burn timeline could not be built", failure);
                  return;
                }
                publish(built);
              });
    } catch (RejectedExecutionException e) {
      logger.warn("Burn timeline not built: the executor refused the task", e);
    }
  }

  /**
   * The timeline of the primary ephemeris being drawn.
   *
   * @param current the primary ephemeris the renderer is drawing this frame
   * @return its timeline, or empty when none has been built for this very ephemeris yet
   */
  Optional<BurnTimeline> timelineFor(MissionEphemeris current) {
    Published snapshot = published;
    return snapshot != null && snapshot.source() == current
        ? Optional.of(snapshot.timeline())
        : Optional.empty();
  }

  private synchronized void publish(Published built) {
    if (requested == built.source()) {
      published = built;
    }
  }
}
