package com.smousseur.orbitlab.simulation.mission;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable, globally unique identity of a mission. This is the key every registry, event and UI
 * callback uses to designate a mission; the mission <em>name</em> is a display label only and may
 * be duplicated or (later) renamed without breaking any lookup.
 *
 * <p>The value is a random UUID rather than a counter because mission files are meant to be
 * exchanged: two missions created independently on two installations can end up in the same
 * library, and no counter can guarantee uniqueness without a coordination point that does not exist
 * here.
 *
 * <p>Identity is carried by {@link com.smousseur.orbitlab.simulation.mission.context.MissionEntry},
 * not by {@link Mission} or {@link
 * com.smousseur.orbitlab.simulation.mission.operation.MissionSpec}: both of those are recomposable
 * artifacts <em>under</em> a stable identity. {@code MissionEntry.setOptimizationType} replaces the
 * {@code Mission} object on every FAST↔PRECISE toggle, and {@code MissionSpec.withLauncherLoads}
 * mints one spec per candidate during the propellant sweep — an id living on either would change
 * (or be duplicated) mid-flight and desynchronize the renderer and the camera focus.
 */
public record MissionId(UUID value) {

  public MissionId {
    Objects.requireNonNull(value, "value");
  }

  /**
   * Mints a fresh identity. The single point of generation in the codebase, so a future load path
   * only has to bypass this method rather than reconcile scattered constructors.
   *
   * @return a new, never-before-used mission id
   */
  public static MissionId newId() {
    return new MissionId(UUID.randomUUID());
  }

  /**
   * Rebuilds an identity from its canonical text form, as written by {@link #toString()}. Intended
   * for the save/load path.
   *
   * @param text the canonical 36-character form
   * @return the parsed mission id
   * @throws IllegalArgumentException if the text is not a valid UUID
   */
  public static MissionId parse(String text) {
    Objects.requireNonNull(text, "text");
    return new MissionId(UUID.fromString(text));
  }

  /**
   * Returns the first 8 characters, for display and log disambiguation. <strong>Not unique</strong>
   * and never to be persisted or used as a key — use {@link #toString()} for that.
   *
   * @return an abbreviated, human-scannable form
   */
  public String shortForm() {
    return value.toString().substring(0, 8);
  }

  /**
   * Returns the canonical 36-character form. This is deliberately the full value rather than the
   * abbreviation: {@code toString()} is what gets called by accident — string concatenation, a
   * Log4j {@code {}} placeholder — so the form that lands there must be the lossless one.
   *
   * @return the canonical text form
   */
  @Override
  public String toString() {
    return value.toString();
  }
}
