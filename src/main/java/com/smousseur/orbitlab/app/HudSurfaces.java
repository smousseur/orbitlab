package com.smousseur.orbitlab.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.Logger;

/**
 * Registry of the surfaces {@code ESC} can send away, held by {@link ApplicationContext}.
 *
 * <p>It exists because the key is owned by one app state while the surfaces belong to others, and
 * {@code CLAUDE.md} forbids an app state reaching for another through {@code getState}. Each owner
 * registers its own surface and keeps the truth about it.
 *
 * <p>Registration order carries no meaning except as a tie-break: the ranking is the layer, which
 * is the same scale that stacks the surfaces on screen. What {@code ESC} sends away is therefore
 * always what the user sees in front.
 *
 * <p>Not thread-safe, and deliberately so: registration and dismissal both happen on the render
 * thread.
 */
public final class HudSurfaces {

  private final List<HudSurface> surfaces = new ArrayList<>();

  /**
   * Registers a surface.
   *
   * @param surface the surface to register
   * @return a handle that unregisters it, to be closed in the owner's {@code cleanup()} — the same
   *     contract as a clock subscription
   */
  public AutoCloseable register(HudSurface surface) {
    Objects.requireNonNull(surface, "surface");
    surfaces.add(surface);
    return () -> surfaces.remove(surface);
  }

  /**
   * Returns the open surface with the highest layer, ties going to the most recently registered —
   * of two surfaces sharing a layer, the one opened last is in front.
   *
   * @return the topmost open surface, or empty when nothing is open
   */
  public Optional<HudSurface> topmostOpen() {
    HudSurface topmost = null;
    for (HudSurface surface : surfaces) {
      if (surface.isOpen() && (topmost == null || surface.layer() >= topmost.layer())) {
        topmost = surface;
      }
    }
    return Optional.ofNullable(topmost);
  }

  /**
   * Tells whether a named surface is currently open. Lets one app state read another's surface
   * without reaching for it through {@code getState}.
   *
   * @param name the name the surface was registered under
   * @return {@code true} if a surface of that name is registered and open
   */
  public boolean isOpen(String name) {
    for (HudSurface surface : surfaces) {
      if (surface.name().equals(name) && surface.isOpen()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Sends away the topmost open surface.
   *
   * @return {@code true} if a surface was dismissed, {@code false} when nothing was open
   */
  public boolean dismissTopmost() {
    Optional<HudSurface> topmost = topmostOpen();
    topmost.ifPresent(HudSurface::dismiss);
    return topmost.isPresent();
  }

  /**
   * Closes a registration handle without letting teardown fail. A handle's {@code close()} only
   * removes an entry from this registry and cannot actually throw, but it is declared to, and an
   * escaping exception during an app state's {@code cleanup()} would strand whatever that state had
   * left to release.
   *
   * @param handle the handle to close, or {@code null}
   * @param logger the caller's logger, used if the impossible happens
   */
  public static void closeQuietly(AutoCloseable handle, Logger logger) {
    if (handle == null) return;
    try {
      handle.close();
    } catch (Exception e) {
      logger.warn("Failed to unregister a HUD surface", e);
    }
  }
}
