package com.smousseur.orbitlab.app.view;

import org.hipparchus.geometry.euclidean.threed.Vector3D;

import java.util.Objects;

/**
 * Pure math utilities to convert positions between: - Orekit/Hipparchus meters (ICRF axes) -
 * "render positions" in JME units (kept as Vector3D/double for precision)
 *
 * <p>This class does NOT depend on jME types; convert to Vector3f at the very edge of rendering.
 */
public final class RenderTransform {

  private RenderTransform() {}

  /**
   * Converts an absolute object position in ICRF meters into a render-space position (JME units),
   * still expressed in ICRF axes, according to the provided RenderContext.
   *
   * <p>For SOLAR/HELIOCENTRIC: returns objectIcrfMeters scaled.
   *
   * <p>For PLANET/RELATIVE: returns (object - target) scaled, so target must be provided.
   */
  public static Vector3D toRenderUnitsIcrfAxes(
      Vector3D objectIcrfMeters, Vector3D targetIcrfMeters, RenderContext ctx) {

    Objects.requireNonNull(objectIcrfMeters, "objectIcrfMeters");
    Objects.requireNonNull(ctx, "ctx");

    Vector3D meters;
    switch (ctx.frame()) {
      case HELIOCENTRIC_ICRF -> meters = objectIcrfMeters;
      case PLANETOCENTRIC_RELATIVE_ICRF -> {
        Objects.requireNonNull(targetIcrfMeters, "targetIcrfMeters");
        meters = objectIcrfMeters.subtract(targetIcrfMeters);
      }
      default -> throw new IllegalStateException("Unhandled frame: " + ctx.frame());
    }

    return scaleMetersToUnits(meters, ctx);
  }

  /**
   * Same as {@link #toRenderUnitsIcrfAxes(Vector3D, Vector3D, RenderContext)} but expressed in JME
   * axes (still double precision).
   */
  public static Vector3D toRenderUnitsJmeAxes(
      Vector3D objectIcrfMeters, Vector3D targetIcrfMeters, RenderContext ctx) {
    Vector3D unitsIcrf = toRenderUnitsIcrfAxes(objectIcrfMeters, targetIcrfMeters, ctx);
    return ctx.axisConvention().icrfToJme(unitsIcrf);
  }

  /**
   * Converts a render-space position (JME units in ICRF axes) back into absolute ICRF meters.
   *
   * <p>For SOLAR/HELIOCENTRIC: returns meters.
   *
   * <p>For PLANET/RELATIVE: returns target + meters, so target must be provided.
   */
  public static Vector3D toIcrfMetersFromRenderUnitsIcrfAxes(
      Vector3D renderUnitsIcrfAxes, Vector3D targetIcrfMeters, RenderContext ctx) {

    Objects.requireNonNull(renderUnitsIcrfAxes, "renderUnitsIcrfAxes");
    Objects.requireNonNull(ctx, "ctx");

    Vector3D meters = scaleUnitsToMeters(renderUnitsIcrfAxes, ctx);

    return switch (ctx.frame()) {
      case HELIOCENTRIC_ICRF -> meters;
      case PLANETOCENTRIC_RELATIVE_ICRF -> {
        Objects.requireNonNull(targetIcrfMeters, "targetIcrfMeters");
        yield targetIcrfMeters.add(meters);
      }
    };
  }

  /** Converts a render-space position (JME units in JME axes) back into absolute ICRF meters. */
  public static Vector3D toIcrfMetersFromRenderUnitsJmeAxes(
      Vector3D renderUnitsJmeAxes, Vector3D targetIcrfMeters, RenderContext ctx) {
    Objects.requireNonNull(renderUnitsJmeAxes, "renderUnitsJmeAxes");
    Objects.requireNonNull(ctx, "ctx");
    Vector3D renderUnitsIcrfAxes = ctx.axisConvention().jmeToIcrf(renderUnitsJmeAxes);
    return toIcrfMetersFromRenderUnitsIcrfAxes(renderUnitsIcrfAxes, targetIcrfMeters, ctx);
  }

  /** Scales a vector expressed in meters into JME units for the given context. */
  public static Vector3D scaleMetersToUnits(Vector3D meters, RenderContext ctx) {
    Objects.requireNonNull(meters, "meters");
    Objects.requireNonNull(ctx, "ctx");
    double upm = ctx.unitsPerMeter();
    return new Vector3D(meters.getX() * upm, meters.getY() * upm, meters.getZ() * upm);
  }

  /** Scales a vector expressed in JME units back into meters for the given context. */
  public static Vector3D scaleUnitsToMeters(Vector3D units, RenderContext ctx) {
    Objects.requireNonNull(units, "units");
    Objects.requireNonNull(ctx, "ctx");
    double mpu = ctx.metersPerUnit();
    return new Vector3D(units.getX() * mpu, units.getY() * mpu, units.getZ() * mpu);
  }
}
