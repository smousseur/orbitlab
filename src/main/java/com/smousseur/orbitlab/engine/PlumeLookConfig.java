package com.smousseur.orbitlab.engine;

import java.util.Objects;

/**
 * Tuning of one engine's plume: its shape at sea level and in vacuum, between which it is blended
 * as the air thins, and the colours of its hot core and of its edge.
 *
 * @param seaLevel the shape fired into the air at the ground
 * @param vacuum the shape fired into no air at all
 * @param core the colour near the exit, at the heart of the jet
 * @param edge the colour toward the jet's edge and tail
 */
public record PlumeLookConfig(PlumeShape seaLevel, PlumeShape vacuum, Rgb core, Rgb edge) {

  /**
   * A colour, each channel in {@code [0, 1]}.
   *
   * @param r the red channel
   * @param g the green channel
   * @param b the blue channel
   */
  public record Rgb(float r, float g, float b) {

    public Rgb {
      for (float channel : new float[] {r, g, b}) {
        if (!(channel >= 0f && channel <= 1f)) {
          throw new IllegalArgumentException("a channel must be in [0, 1], got " + channel);
        }
      }
    }
  }

  public PlumeLookConfig {
    Objects.requireNonNull(seaLevel, "seaLevel");
    Objects.requireNonNull(vacuum, "vacuum");
    Objects.requireNonNull(core, "core");
    Objects.requireNonNull(edge, "edge");
  }

  /**
   * The shape at a given thinness of the air.
   *
   * @param pressureFactor 0 at sea level, 1 in vacuum ({@link NozzlePlumeConfig#pressureFactor})
   * @return the shape between the two
   */
  public PlumeShape shapeAt(float pressureFactor) {
    if (pressureFactor <= 0f) {
      return seaLevel;
    }
    if (pressureFactor >= 1f) {
      return vacuum;
    }
    return seaLevel.blend(vacuum, pressureFactor);
  }
}
