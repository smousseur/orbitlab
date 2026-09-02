package com.smousseur.orbitlab.engine.scene.mesh;

/**
 * One geometry found under a loaded model, with whatever could be measured on it: the body-fixed
 * frame of a textured sphere, or the plane of a flat ring, or neither.
 *
 * <p>A geometry the probe cannot measure is reported rather than dropped. Staying silent about a
 * mesh is the exact failure mode this chantier exists to remove: the reader of a report has no way
 * to tell a model with one sphere from a model whose second geometry was quietly skipped. Saturn's
 * ring and Uranus's are the standing cases.
 *
 * @param name the geometry's name, as the asset declares it — what the report prints so a reading
 *     can be traced back to a specific mesh inside a multi-part model
 * @param frame the frame measured on it, expressed in the model root's own axes, or {@code null}
 *     when the geometry is not a lat/long sphere. Test it with {@link #hasFrame()} rather than
 *     against {@code null} at the call site
 * @param ring the plane it lies in, in the same axes, or {@code null} when it is not flat. The two
 *     are exclusive in practice and both are absent on a geometry with nothing to say — a ring is
 *     never a sphere, and neither is a lander or a moon baked into a planet's asset
 */
public record ProbedGeometry(String name, MeshFrame frame, RingPlane ring) {

  /**
   * Whether a frame could be measured on this geometry.
   *
   * @return {@code true} when {@link #frame()} is present
   */
  public boolean hasFrame() {
    return frame != null;
  }

  /**
   * Whether this geometry is flat, and therefore has a plane rather than a frame.
   *
   * @return {@code true} when {@link #ring()} is present
   */
  public boolean isRing() {
    return ring != null;
  }
}
