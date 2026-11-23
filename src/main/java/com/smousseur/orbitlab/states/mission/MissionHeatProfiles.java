package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.engine.ReentryFxConfig;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.mission.ephemeris.DebrisTrack;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The re-entry heat profiles of one mission — its debris' and its primary's — built off the render
 * thread and served by the identity of the debris list and of the primary ephemeris they were built
 * for.
 *
 * <p>A profile costs tens of milliseconds per mission — over a frame's budget — so it is computed
 * on an executor when the renderer rebuilds its debris, and the renderer reads whatever has been
 * published since. The debris list and the primary ephemeris are published together by a
 * computation, and each is matched by identity, as the renderer itself detects a recomputation: a
 * result built for another list or another ephemeris, however equal, is never served, and a result
 * overtaken by a newer request is dropped rather than published over it. Until its profile is
 * ready, an object reads an empty profile, whose intensity is zero everywhere: its plasma simply
 * stays off.
 *
 * <p>A debris whose trajectory never enters the heating domain — an upper stage left in orbit — is
 * given the empty profile without being built: walking its long ephemeris is most of what a profile
 * costs, for samples that would all be zero. A primary is built only when its trajectory ends on
 * the ground — a deorbited payload. Every other primary enters the domain all the same: its
 * launch-pad sample counts as descending by rounding, and a transfer can dip to 108 km. Glowing
 * nowhere at today's threshold, measured, it is still left out, so a mission that does not deorbit
 * is drawn as it was whatever the threshold becomes, without the cost of walking its ephemeris.
 */
final class MissionHeatProfiles {

  private static final Logger logger = LogManager.getLogger(MissionHeatProfiles.class);

  /** The profile of an object that does not heat, or whose profiles are not ready yet. */
  private static final ReentryHeatProfile NO_HEAT =
      new ReentryHeatProfile(List.of(), null, 1.0, 2.0);

  /**
   * The objects a request was made for.
   *
   * @param debris the very debris list
   * @param primary the very primary ephemeris
   */
  private record Source(List<DebrisTrack> debris, MissionEphemeris primary) {}

  /**
   * Profiles built for a request.
   *
   * @param source the very objects they were built for
   * @param debris one profile per debris of the source list, in its order
   * @param primary the primary's profile
   */
  private record Published(
      Source source, List<ReentryHeatProfile> debris, ReentryHeatProfile primary) {}

  private Source requested = new Source(List.of(), null);
  private volatile Published published = new Published(requested, List.of(), NO_HEAT);

  /**
   * Starts building the profiles of a mission's objects, replacing any request still running.
   *
   * @param debris the debris list the renderer has just rebuilt its views from
   * @param primary the primary's raw ephemeris, published with that list
   * @param model the atmosphere the mission flies, read from the entry now
   * @param config the threshold and ceiling of the intensity
   * @param executor where the profiles are built
   */
  void request(
      List<DebrisTrack> debris,
      MissionEphemeris primary,
      AtmosphereModel model,
      ReentryFxConfig config,
      Executor executor) {
    Source source = new Source(debris, Objects.requireNonNull(primary, "primary"));
    synchronized (this) {
      requested = source;
    }
    try {
      CompletableFuture.supplyAsync(() -> build(source, model, config), executor)
          .whenComplete(
              (built, failure) -> {
                if (failure != null) {
                  logger.error("Re-entry heat profiles could not be built", failure);
                  return;
                }
                publish(built);
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
    return snapshot.source().debris() == current ? snapshot.debris().get(index) : NO_HEAT;
  }

  /**
   * The profile of the primary being drawn.
   *
   * @param currentDebris the debris list the renderer is drawing this frame
   * @param currentPrimary the primary ephemeris the renderer is drawing this frame
   * @return its profile, or an empty one when none has been built for this very list and this very
   *     ephemeris yet
   */
  ReentryHeatProfile primaryProfile(
      List<DebrisTrack> currentDebris, MissionEphemeris currentPrimary) {
    Published snapshot = published;
    Source source = snapshot.source();
    return source.debris() == currentDebris && source.primary() == currentPrimary
        ? snapshot.primary()
        : NO_HEAT;
  }

  private synchronized void publish(Published built) {
    if (requested == built.source()) {
      published = built;
    }
  }

  private static Published build(Source source, AtmosphereModel model, ReentryFxConfig config) {
    long start = System.nanoTime();
    List<ReentryHeatProfile> profiles = new ArrayList<>(source.debris().size());
    int heating = 0;
    for (DebrisTrack track : source.debris()) {
      if (ReentryHeatProfile.mayHeat(track.ephemeris())) {
        heating++;
        profiles.add(profileOf(track.ephemeris(), model, config));
      } else {
        profiles.add(NO_HEAT);
      }
    }
    boolean primaryFalls = MissionRenderer.endsOnTheGround(source.primary());
    ReentryHeatProfile primary =
        primaryFalls ? profileOf(source.primary(), model, config) : NO_HEAT;
    logger.info(
        "Re-entry heat profiles: {} of {} debris may heat, primary {}, built in {} ms",
        heating,
        source.debris().size(),
        primaryFalls ? "falls" : "stays up",
        String.format("%.1f", (System.nanoTime() - start) / 1e6));
    return new Published(source, List.copyOf(profiles), primary);
  }

  private static ReentryHeatProfile profileOf(
      MissionEphemeris ephemeris, AtmosphereModel model, ReentryFxConfig config) {
    return ReentryHeatProfile.forMission(
        ephemeris, model, config.thresholdWm2(), config.ceilingWm2());
  }
}
