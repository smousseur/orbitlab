package com.smousseur.orbitlab.engine.scene.hover;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.Objects;

/**
 * One orbit as it lands on the screen: a polyline in pixels, with its bounding box.
 *
 * <p><b>A point that could not be projected breaks the polyline.</b> It is stored as {@code NaN},
 * and no segment touching it is ever measured: a point outside the camera's depth range is not
 * drawn, and a segment reaching it would be measured against a position the screen does not show.
 * The bounding box covers the valid points only.
 *
 * <p><b>Mutable, and reused.</b> {@link OrbitScreenProjector} refills the same instance on every
 * call, so that once the coordinates array has grown to the orbit's size a frame costs no
 * allocation — the projection runs every frame for ten rings of 4 096 points.
 */
public final class ProjectedOrbit {

  private final SolarSystemBody body;
  private float[] xy = new float[0];
  private int count;
  private float minX;
  private float minY;
  private float maxX;
  private float maxY;

  ProjectedOrbit(SolarSystemBody body) {
    this.body = Objects.requireNonNull(body, "body");
    reset(0);
  }

  /**
   * Empties the polyline and makes room for {@code capacity} points, growing the array only when it
   * is too small.
   *
   * @param capacity the number of points about to be added
   */
  void reset(int capacity) {
    if (xy.length < capacity * 2) {
      xy = new float[capacity * 2];
    }
    count = 0;
    minX = Float.POSITIVE_INFINITY;
    minY = Float.POSITIVE_INFINITY;
    maxX = Float.NEGATIVE_INFINITY;
    maxY = Float.NEGATIVE_INFINITY;
  }

  /**
   * Appends a point, in pixels; {@code NaN} marks a point that could not be projected.
   *
   * @param x horizontal screen coordinate
   * @param y vertical screen coordinate, from the bottom of the screen as in JME
   */
  void add(float x, float y) {
    xy[count * 2] = x;
    xy[count * 2 + 1] = y;
    count++;
    if (!Float.isNaN(x) && !Float.isNaN(y)) {
      minX = Math.min(minX, x);
      minY = Math.min(minY, y);
      maxX = Math.max(maxX, x);
      maxY = Math.max(maxY, y);
    }
  }

  /**
   * @return the body this orbit belongs to
   */
  public SolarSystemBody body() {
    return body;
  }

  /**
   * @return the number of points in the polyline, invalid ones included
   */
  public int pointCount() {
    return count;
  }

  /**
   * @param i the point index, below {@link #pointCount()} — the array beyond it still holds what an
   *     earlier, longer projection left there
   * @return its horizontal screen coordinate, {@code NaN} for an invalid point
   */
  public float x(int i) {
    return xy[Objects.checkIndex(i, count) * 2];
  }

  /**
   * @param i the point index, below {@link #pointCount()}
   * @return its vertical screen coordinate, {@code NaN} for an invalid point
   */
  public float y(int i) {
    return xy[Objects.checkIndex(i, count) * 2 + 1];
  }

  /**
   * Whether a screen point lies inside the bounding box of the valid points, grown by {@code
   * marginPx} on every side. Always false for a polyline without a valid point.
   *
   * @param px horizontal screen coordinate
   * @param py vertical screen coordinate
   * @param marginPx the margin added around the box
   * @return true when the point is inside the grown box
   */
  public boolean isNear(float px, float py, float marginPx) {
    return px >= minX - marginPx
        && px <= maxX + marginPx
        && py >= minY - marginPx
        && py <= maxY + marginPx;
  }

  /**
   * Distance from a screen point to the nearest segment whose two ends are valid.
   *
   * @param px horizontal screen coordinate
   * @param py vertical screen coordinate
   * @return the distance in pixels, or {@link Float#POSITIVE_INFINITY} when no segment is valid
   */
  public float distanceTo(float px, float py) {
    float best = Float.POSITIVE_INFINITY;
    for (int i = 1; i < count; i++) {
      float ax = xy[i * 2 - 2];
      float ay = xy[i * 2 - 1];
      float bx = xy[i * 2];
      float by = xy[i * 2 + 1];
      if (Float.isNaN(ax) || Float.isNaN(ay) || Float.isNaN(bx) || Float.isNaN(by)) {
        continue;
      }
      best = Math.min(best, squaredDistanceToSegment(px, py, ax, ay, bx, by));
    }
    return (float) Math.sqrt(best);
  }

  private static float squaredDistanceToSegment(
      float px, float py, float ax, float ay, float bx, float by) {
    float dx = bx - ax;
    float dy = by - ay;
    float lengthSquared = dx * dx + dy * dy;
    float t = lengthSquared > 0f ? ((px - ax) * dx + (py - ay) * dy) / lengthSquared : 0f;
    t = Math.max(0f, Math.min(1f, t));
    float cx = ax + t * dx - px;
    float cy = ay + t * dy - py;
    return cx * cx + cy * cy;
  }
}
