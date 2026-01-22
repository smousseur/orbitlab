package com.smousseur.orbitlab.engine.scene;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.simulation.orbit.OrbitPath;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

public final class OrbitLineFactory {

  /** Solar view scale: 1 JME unit = 1e9 meters. */
  public static final double SOLAR_METERS_PER_UNIT = 1.0e9;

  private OrbitLineFactory() {}

  public static Geometry buildHeliocentricLineStrip(
      AssetManager assetManager, OrbitPath path, ColorRGBA color, float lineWidth) {

    Objects.requireNonNull(assetManager, "assetManager");
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(color, "color");

    List<Vector3D> pts = path.positionsHelioMeters();
    if (pts.size() < 2) {
      throw new IllegalArgumentException("OrbitPath must contain at least 2 points");
    }

    RenderContext ctx = RenderContext.solar();
    FloatBuffer pb = BufferUtils.createFloatBuffer(pts.size() * 3);

    for (Vector3D pIcrfMetersHelio : pts) {
      Vector3D pJmeUnitsJmeAxes = RenderTransform.toRenderUnitsJmeAxes(pIcrfMetersHelio, null, ctx);
      pb.put((float) pJmeUnitsJmeAxes.getX());
      pb.put((float) pJmeUnitsJmeAxes.getY());
      pb.put((float) pJmeUnitsJmeAxes.getZ());
    }
    pb.flip();

    Mesh mesh = new Mesh();
    mesh.setMode(Mesh.Mode.LineLoop);
    mesh.setBuffer(VertexBuffer.Type.Position, 3, pb);
    mesh.updateBound();
    mesh.updateCounts();

    Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setColor("Color", color);
    mat.getAdditionalRenderState().setLineWidth(lineWidth);

    Geometry geom = new Geometry("OrbitLine-" + path.body().name(), mesh);
    geom.setMaterial(mat);
    return geom;
  }
}
