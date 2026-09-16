package com.smousseur.orbitlab.states.mission;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Sphere;
import com.jme3.util.BufferUtils;
import com.smousseur.orbitlab.engine.AssetFactory;
import java.util.List;

/**
 * Draws a landing debris' <em>ground track</em> — the whole fall curve, from separation to impact —
 * plus an impact marker, hung under the Earth rotating-frame node so it sticks to the turning globe
 * and answers "where does it come down?" (PHY-5 / L7, spec {@code
 * docs/multi-objets/09-conception-L7.md} §D3).
 *
 * <p>The vertices are the {@link DebrisGroundTrack}'s seam-local coordinates (near-view units about
 * the geocentre), so the geometry is static: only the seam node's rotation turns it with the globe.
 */
final class DebrisGroundTrackView {

  /**
   * Radius of the impact marker, in near-view units (≈ km). A few tens of km so it reads on a ~6371
   * unit globe; tunable, cosmetic.
   */
  private static final float IMPACT_MARKER_RADIUS_UNITS = 25f;

  private final Node group;

  DebrisGroundTrackView(
      Node earthRotatingFrame, DebrisGroundTrack track, ColorRGBA color, String id) {
    group = new Node("DebrisGroundTrack-" + id);
    group.attachChild(line(track.seamLocalVertices(), color, id));
    group.attachChild(impactMarker(track.impactSeamLocal(), color, id));
    earthRotatingFrame.attachChild(group);
  }

  private static Geometry line(List<Vector3f> vertices, ColorRGBA color, String id) {
    Mesh mesh = new Mesh();
    mesh.setMode(Mesh.Mode.LineStrip);
    mesh.setBuffer(
        VertexBuffer.Type.Position,
        3,
        BufferUtils.createFloatBuffer(vertices.toArray(new Vector3f[0])));
    mesh.updateBound();
    Geometry geometry = new Geometry("DebrisGroundLine-" + id, mesh);
    geometry.setMaterial(AssetFactory.get().material(color));
    return geometry;
  }

  private static Geometry impactMarker(Vector3f at, ColorRGBA color, String id) {
    Geometry marker =
        new Geometry("DebrisImpact-" + id, new Sphere(8, 8, IMPACT_MARKER_RADIUS_UNITS));
    marker.setMaterial(AssetFactory.get().material(color));
    marker.setLocalTranslation(at);
    return marker;
  }

  void setVisible(boolean visible) {
    group.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
  }

  void cleanup() {
    group.removeFromParent();
  }
}
