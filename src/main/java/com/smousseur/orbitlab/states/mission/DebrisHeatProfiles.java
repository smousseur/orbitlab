package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.engine.ReentryFxConfig;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.mission.ephemeris.DebrisTrack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The re-entry heat profiles of one mission's debris, built off the render thread and served by the
 * identity of the debris list they were built for.
 *
 * <p>A profile costs tens of milliseconds per mission — over a frame's budget — so it is computed
 * on an executor when the renderer rebuilds its debris, and the renderer reads whatever has been
 * published since. A list is matched by identity, as the renderer itself detects a recomputation: a
 * result built for another list, however equal, is never served, and a result overtaken by a newer
 * request is dropped rather than published over it. Until its profiles are ready, a debris reads an
 * empty profile, whose intensity is zero everywhere: its plasma simply stays off.
 *
 * <p>A debris whose trajectory never enters the heating domain — an upper stage left in orbit — is
 * given the empty profile without being built: walking its long ephemeris is most of what a profile
 * costs, for samples that would all be zero.
 */
final class DebrisHeatProfiles {

  private static final Logger logger = LogManager.getLogger(DebrisHeatProfiles.class);

  /** The profile of a debris that does not heat, or whose profiles are not ready yet. */
  private static final ReentryHeatProfile NO_HEAT =
      new ReentryHeatProfile(List.of(), null, 1.0, 2.0);

  /**
   * Profiles built for a debris list.
   *
   * @param source the very list they were built for
   * @param profiles one profile per debris of {@code source}, in its order
   */
  private record Published(List<DebrisTrack> source, List<ReentryHeatProfile> profiles) {}

  private List<DebrisTrack> requested = List.of();
  private volatile Published published = new Published(List.of(), List.of());

  /**
   * Starts building the profiles of a debris list, replacing any request still running.
   *
   * @param debris the debris list the renderer has just rebuilt its views from
   * @param model the atmosphere the mission flies, read from the entry now
   * @param config the threshold and ceiling of the intensity
   * @param executor where the profiles are built
   */
  void request(
      List<DebrisTrack> debris, AtmosphereModel model, ReentryFxConfig config, Executor executor) {
    synchronized (this) {
      requested = debris;
    }
    try {
      CompletableFuture.supplyAsync(() -> build(debris, model, config), executor)
          .whenComplete(
              (profiles, failure) -> {
                if (failure != null) {
                  logger.error("Re-entry heat profiles could not be built", failure);
                  return;
                }
                publish(debris, profiles);
              });
    } catch (RejectedExecutionException e) {
      logger.warn("Re-entry heat profiles not built: the executor refused the task", e);
    }
  }

  /**
   * The profile of one debris of the list being drawn.
   *
   * @param current the debris list the renderer is drawing this frame
   * @param index the debris' index in {@code current}
   * @return its profile, or an empty one — zero intensity everywhere — when none has been built for
   *     this very list yet
   */
  ReentryHeatProfile profileFor(List<DebrisTrack> current, int index) {
    Published snapshot = published;
    return snapshot.source() == current ? snapshot.profiles().get(index) : NO_HEAT;
  }

  private synchronized void publish(List<DebrisTrack> debris, List<ReentryHeatProfile> profiles) {
    if (requested == debris) {
      published = new Published(debris, profiles);
    }
  }

  private static List<ReentryHeatProfile> build(
      List<DebrisTrack> debris, AtmosphereModel model, ReentryFxConfig config) {
    long start = System.nanoTime();
    List<ReentryHeatProfile> profiles = new ArrayList<>(debris.size());
    int heating = 0;
    for (DebrisTrack track : debris) {
      if (ReentryHeatProfile.mayHeat(track.ephemeris())) {
        heating++;
        profiles.add(
            ReentryHeatProfile.forMission(
                track.ephemeris(), model, config.thresholdWm2(), config.ceilingWm2()));
      } else {
        profiles.add(NO_HEAT);
      }
    }
    logger.info(
        "Re-entry heat profiles: {} of {} debris may heat, built in {} ms",
        heating,
        debris.size(),
        String.format("%.1f", (System.nanoTime() - start) / 1e6));
    return List.copyOf(profiles);
  }
}
