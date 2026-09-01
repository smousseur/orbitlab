package com.smousseur.orbitlab.engine.scene.mesh;

/**
 * One geometry found under a loaded model, with the frame it carries — or without, when it carries
 * none.
 *
 * <p>A geometry the probe cannot measure is reported rather than dropped. Staying silent about a
 * mesh is the exact failure mode this chantier exists to remove: the reader of a report has no way
 * to tell a model with one sphere from a model whose second geometry was quietly skipped. Saturn's
 * ring and Uranus's are the standing cases.
 *
 * @param name the geometry's name, as the asset declares it — what the report prints so a reading
 *     can be traced back to a specific mesh inside a multi-part model
 * @param frame the frame measured on it, expressed in the model root's own axes, or {@code null}
 *     when the geometry carries nothing measurable — no UV map, or a degenerate one. Test it with
 *     {@link #hasFrame()} rather than against {@code null} at the call site
 */
public record ProbedGeometry(String name, MeshFrame frame) {

  /**
   * Whether a frame could be measured on this geometry.
   *
   * @return {@code true} when {@link #frame()} is present
   */
  public boolean hasFrame() {
    return frame != null;
  }
}
