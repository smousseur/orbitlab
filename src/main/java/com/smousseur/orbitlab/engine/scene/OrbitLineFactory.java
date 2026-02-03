package com.smousseur.orbitlab.engine.scene;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.simulation.orbit.OrbitPath;
import com.smousseur.orbitlab.simulation.source.EphemerisSource;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Objects;

import com.smousseur.orbitlab.simulation.source.EphemerisSourceRegistry;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

public final class OrbitLineFactory {

  /** Solar view scale: 1 JME unit = 1e9 meters. */
  public static final double SOLAR_METERS_PER_UNIT = 1.0e9;

  private OrbitLineFactory() {}

  public static Geometry buildHeliocentricLineStrip(
      OrbitPath path, ColorRGBA color, float lineWidth) {
    return buildBodyRelativeLineStrip(path, null, color, lineWidth);
  }

  public static Geometry buildBodyRelativeLineStrip(
      SolarSystemBody body, List<Vector3D> pts, ColorRGBA color, float lineWidth) {
    Objects.requireNonNull(pts, "pts");
    Objects.requireNonNull(color, "color");
    FloatBuffer pb = BufferUtils.createFloatBuffer(pts.size() * 3);
    for (Vector3D pt : pts) {
      Vector3D jmePt = RenderTransform.toRenderUnitsJmeAxes(pt, null, RenderContext.solar());
      pb.put((float) jmePt.getX());
      pb.put((float) jmePt.getY());
      pb.put((float) jmePt.getZ());
    }
    pb.flip();

    Mesh mesh = new Mesh();
    mesh.setMode(Mesh.Mode.LineLoop);
    mesh.setBuffer(VertexBuffer.Type.Position, 3, pb);
    mesh.updateBound();
    mesh.updateCounts();

    Material mat = AssetFactory.get().material(color);
    mat.setColor("Color", color);
    mat.getAdditionalRenderState().setLineWidth(lineWidth);

    Geometry geom = new Geometry("OrbitLine-" + body.name(), mesh);
    geom.setMaterial(mat);
    return geom;
  }

  public static Geometry buildBodyRelativeLineStrip(
      OrbitPath path, SolarSystemBody centerBody, ColorRGBA color, float lineWidth) {

    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(color, "color");

    List<Vector3D> pts = path.positionsHelioMeters();
    if (pts.size() < 2) {
      throw new IllegalArgumentException("OrbitPath must contain at least 2 points");
    }

    RenderContext ctx = RenderContext.solar();
    FloatBuffer pb = BufferUtils.createFloatBuffer(pts.size() * 3);

    AbsoluteDate t = path.start();
    double step = path.stepSeconds();

    EphemerisSource source =
        EphemerisSourceRegistry.get()
            .orElseThrow(() -> new OrbitlabException("Cannot get Ephemeris source"));

    for (Vector3D pHelio : pts) {
      Vector3D centerHelio = new Vector3D(0, 0, 0);
      if (centerBody != null) {
        // TODO call sampleIcrfSafe instead of sampleIcrf
        centerHelio = source.sampleIcrf(centerBody, t).pvIcrf().getPosition();
      }
      Vector3D pLocalMeters = pHelio.subtract(centerHelio);

      Vector3D pJmeUnitsJmeAxes = RenderTransform.toRenderUnitsJmeAxes(pLocalMeters, null, ctx);
      pb.put((float) pJmeUnitsJmeAxes.getX());
      pb.put((float) pJmeUnitsJmeAxes.getY());
      pb.put((float) pJmeUnitsJmeAxes.getZ());
    }
    pb.flip();

    Mesh mesh = new Mesh();
    mesh.setMode(Mesh.Mode.LineStrip);
    mesh.setBuffer(VertexBuffer.Type.Position, 3, pb);
    mesh.updateBound();
    mesh.updateCounts();

    Material mat = AssetFactory.get().material(color);
    mat.setColor("Color", color);
    mat.getAdditionalRenderState().setLineWidth(lineWidth);

    Geometry geom = new Geometry("OrbitLine-" + path.body().name(), mesh);
    geom.setMaterial(mat);
    return geom;
  }
}
