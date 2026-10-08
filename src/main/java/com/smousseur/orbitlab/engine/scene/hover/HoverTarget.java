package com.smousseur.orbitlab.engine.scene.hover;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.Objects;

/**
 * The planet the hover designates, and the handle it was designated by.
 *
 * <p>The planet lights up the same way whichever the handle; the handle only decides who answers a
 * click — the icon answers its own, the scene sensor answers one on an orbit.
 *
 * @param body the designated planet
 * @param handle what the cursor is on
 */
public record HoverTarget(SolarSystemBody body, Handle handle) {

  public HoverTarget {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(handle, "handle");
  }

  /** What the cursor designates a planet by. */
  public enum Handle {
    /** The planet's own icon. */
    ICON,
    /** The planet's orbit ribbon, within the detection band. */
    ORBIT
  }
}
