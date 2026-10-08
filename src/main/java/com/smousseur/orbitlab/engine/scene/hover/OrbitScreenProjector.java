package com.smousseur.orbitlab.engine.scene.hover;

import com.jme3.math.Matrix4f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.RibbonMeshBuilder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Projects the orbit ribbons onto the screen, as polylines in pixels for {@link
 * OrbitHoverDetector}.
 *
 * <p><b>It reads what is drawn, not what was computed.</b> The points come from the ribbon's own
 * position buffer and the transform from the geometry's world transform — which is what carries the
 * Moon's ring, whose node follows the Earth every frame, and the offset the floating origin puts on
 * the far root. No other source holds exactly the drawn polyline: {@code OrbitLineFactory} keeps no
 * copy of the points it writes, and the runtime window rewrites the buffer in place.
 *
 * <p>The buffer holds every polyline point twice, one vertex per edge of the band, and only the
 * vertex shader pushes the two apart; one vertex in {@value RibbonMeshBuilder#VERTICES_PER_POINT}
 * is read. A closed ring ends on a seam pair that repeats its first point, so the last point read
 * closes the ring — and it is always read, whatever the stride.
 *
 * <p><b>Projected by hand rather than through {@link Camera#getScreenCoordinates}.</b> That method
 * divides by a negative {@code w} for a point behind the camera and returns it mirrored onto the
 * screen, and the depth it returns alongside cannot always tell — it rounds back to exactly 1 for
 * distant points once the near plane is small. Here {@code w}, which is the view-space depth for a
 * perspective camera, is tested directly against both planes: a point at or before the near plane,
 * or at or beyond the far one, is not drawn by the GPU and is marked invalid. The far plane is not
 * a formality: zoomed onto the inner planets, the far side of Pluto's ring lies beyond it and still
 * projects inside the screen.
 *
 * <p>An orbit whose effective cull hint is {@link Spatial.CullHint#Always} — its own or one
 * inherited from its orbit node, the orbit layer or the far root — is not drawn and is left out.
 *
 * <p><b>Reuses its outputs.</b> The list and the {@link ProjectedOrbit}s it returns are the same
 * instances on every call and are overwritten by the next one, so a frame allocates nothing once
 * each orbit's array has grown to size. Render thread only.
 */
public final class OrbitScreenProjector {

  private static final SolarSystemBody[] BODIES = SolarSystemBody.values();

  private final Map<SolarSystemBody, ProjectedOrbit> outputs = new EnumMap<>(SolarSystemBody.class);
  private final List<ProjectedOrbit> projected = new ArrayList<>(BODIES.length);
  private final List<ProjectedOrbit> projectedView = Collections.unmodifiableList(projected);
  private final Matrix4f world = new Matrix4f();
  private final Matrix4f clip = new Matrix4f();

  /**
   * Projects every drawn orbit.
   *
   * @param camera the camera the orbits are drawn with — the far camera
   * @param orbits the orbit geometries, by body; the map's own order is irrelevant
   * @param stride read one polyline point in {@code stride}; 1 reads them all
   * @return the drawn orbits in {@link SolarSystemBody} order, valid until the next call
   */
  public List<ProjectedOrbit> project(
      Camera camera, Map<SolarSystemBody, Geometry> orbits, int stride) {
    if (stride < 1) {
      throw new IllegalArgumentException("stride must be at least 1, got " + stride);
    }
    projected.clear();
    for (SolarSystemBody body : BODIES) {
      Geometry geometry = orbits.get(body);
      if (geometry == null || geometry.getCullHint() == Spatial.CullHint.Always) {
        continue;
      }
      ProjectedOrbit orbit = outputs.computeIfAbsent(body, ProjectedOrbit::new);
      projectInto(orbit, camera, geometry, stride);
      projected.add(orbit);
    }
    return projectedView;
  }

  private void projectInto(ProjectedOrbit orbit, Camera camera, Geometry geometry, int stride) {
    Mesh mesh = geometry.getMesh();
    int points = mesh.getVertexCount() / RibbonMeshBuilder.VERTICES_PER_POINT;
    orbit.reset(points / stride + 2);
    if (points == 0) {
      return;
    }
    geometry.getWorldTransform().toTransformMatrix(world);
    camera.getViewProjectionMatrix().mult(world, clip);
    FloatBuffer positions = (FloatBuffer) mesh.getBuffer(VertexBuffer.Type.Position).getData();

    int last = points - 1;
    for (int i = 0; i <= last; i += stride) {
      addPoint(orbit, camera, positions, i);
    }
    if (last % stride != 0) {
      addPoint(orbit, camera, positions, last);
    }
  }

  private void addPoint(ProjectedOrbit orbit, Camera camera, FloatBuffer positions, int point) {
    int offset = point * RibbonMeshBuilder.VERTICES_PER_POINT * 3;
    float x = positions.get(offset);
    float y = positions.get(offset + 1);
    float z = positions.get(offset + 2);

    float w = clip.m30 * x + clip.m31 * y + clip.m32 * z + clip.m33;
    if (!(w > camera.getFrustumNear() && w < camera.getFrustumFar())) {
      orbit.add(Float.NaN, Float.NaN);
      return;
    }
    float ndcX = (clip.m00 * x + clip.m01 * y + clip.m02 * z + clip.m03) / w;
    float ndcY = (clip.m10 * x + clip.m11 * y + clip.m12 * z + clip.m13) / w;
    float screenX =
        ((ndcX + 1f) * (camera.getViewPortRight() - camera.getViewPortLeft()) / 2f
                + camera.getViewPortLeft())
            * camera.getWidth();
    float screenY =
        ((ndcY + 1f) * (camera.getViewPortTop() - camera.getViewPortBottom()) / 2f
                + camera.getViewPortBottom())
            * camera.getHeight();
    orbit.add(screenX, screenY);
  }
}
