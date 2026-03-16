package com.smousseur.orbitlab.app.view;

import com.smousseur.orbitlab.core.SolarSystemBody;

import java.util.Objects;
import java.util.Optional;

/**
 * Logical rendering context used to drive: - Orekit -> render-frame conversion (absolute vs
 * relative positions) - meters -> JME units scaling
 *
 * <p>This intentionally does NOT depend on jME types (Camera, ViewPort, Vector3f...).
 */
public sealed interface RenderContext permits RenderContext.Solar, RenderContext.Planet {

  /** Global scale: how many meters correspond to 1 JME unit in this view. */
  double metersPerUnit();

  /** How positions must be interpreted before scaling. */
  RenderFrame frame();

  /** Logical view mode. */
  ViewMode mode();

  /** Target body for PLANET view; empty for SOLAR view. */
  Optional<SolarSystemBody> targetBody();

  /**
   * Axis mapping used when converting ICRF-axis vectors into JME-axis vectors.
   *
   * <p>Default is the OrbitLab convention: (jmeX, jmeY, jmeZ) = (icrfX, icrfZ, -icrfY)
   */
  default AxisConvention axisConvention() {
    return AxisConvention.ICRF_TO_JME_Y_UP;
  }

  /** Convenience: units per meter (inverse of metersPerUnit). */
  default double unitsPerMeter() {
    return 1.0d / metersPerUnit();
  }

  static double ratioSolarToPlanetPerUnit() {
    return Solar.SOLAR_METERS_PER_UNIT / Planet.PLANET_METERS_PER_UNIT;
  }

  /** Factory for the Solar (heliocentric) view. */
  static Solar solar() {
    return new Solar();
  }

  /** Factory for the Planet-centered view. */
  static Planet planet(SolarSystemBody targetBody) {
    return new Planet(targetBody);
  }

  /**
   * Solar view context: positions are heliocentric in ICRF, scaled at 1 JME unit = 1e9 meters (1
   * million km).
   */
  record Solar() implements RenderContext {
    public static final double SOLAR_METERS_PER_UNIT = 1e9;

    @Override
    public double metersPerUnit() {
      return SOLAR_METERS_PER_UNIT;
    }

    @Override
    public RenderFrame frame() {
      return RenderFrame.HELIOCENTRIC_ICRF;
    }

    @Override
    public ViewMode mode() {
      return ViewMode.SOLAR;
    }

    @Override
    public Optional<SolarSystemBody> targetBody() {
      return Optional.empty();
    }
  }

  /**
   * Planet view context: positions are relative to a target body in ICRF axes, scaled at 1 JME unit
   * = 1e3 meters (1 km).
   *
   * @param body the target solar system body that serves as the coordinate origin
   */
  record Planet(SolarSystemBody body) implements RenderContext {
    public static final double PLANET_METERS_PER_UNIT = 1e3;

    public Planet {
      Objects.requireNonNull(body, "body");
    }

    @Override
    public double metersPerUnit() {
      return PLANET_METERS_PER_UNIT;
    }

    @Override
    public RenderFrame frame() {
      return RenderFrame.PLANETOCENTRIC_RELATIVE_ICRF;
    }

    @Override
    public ViewMode mode() {
      return ViewMode.PLANET;
    }

    @Override
    public Optional<SolarSystemBody> targetBody() {
      return Optional.of(body);
    }
  }
}
