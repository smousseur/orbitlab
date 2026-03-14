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

/**
 * Factory for creating and updating JME3 line-strip geometries that represent orbital paths.
 *
 * <p>Supports both heliocentric and body-relative orbit visualizations by converting
 * positions from ICRF meters to JME render units and axes. All methods are static;
 * this class cannot be instantiated.
 */
public final class OrbitLineFactory {

  /** Solar view scale: 1 JME unit = 1e9 meters. */
  public static final double SOLAR_METERS_PER_UNIT = 1.0e9;

  private OrbitLineFactory() {}

  /**
   * Builds a heliocentric line-strip geometry from an orbit path.
   * The orbit positions are rendered relative to the solar system origin.
   *
   * @param path      the orbit path containing heliocentric positions
   * @param color     the color of the line strip
   * @param lineWidth the width of the rendered line
   * @return a JME3 geometry representing the orbit as a line strip
   */
  public static Geometry buildHeliocentricLineStrip(
      OrbitPath path, ColorRGBA color, float lineWidth) {
    return buildBodyRelativeLineStrip(path, null, color, lineWidth);
  }

  /**
   * Builds a closed line-loop geometry from a list of position vectors, rendered
   * relative to a given solar system body.
   *
   * @param body      the solar system body used for naming the geometry
   * @param pts       the list of position vectors in meters
   * @param color     the color of the line loop
   * @param lineWidth the width of the rendered line
   * @return a JME3 geometry representing the orbit as a closed line loop
   */
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

  /**
   * Builds a line-strip geometry from an orbit path, optionally centered on a given body.
   * When a center body is provided, each position is adjusted by subtracting the center
   * body's heliocentric position at the corresponding time step.
   *
   * @param path       the orbit path containing heliocentric positions and timing data
   * @param centerBody the body to use as the reference center, or {@code null} for heliocentric coordinates
   * @param color      the color of the line strip
   * @param lineWidth  the width of the rendered line
   * @return a JME3 geometry representing the orbit as a line strip
   */
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

  /**
   * Updates an existing orbit Geometry position buffer (no rebuild of Geometry/Mesh). Assumes
   * positions are heliocentric meters.
   */
  public static void updateGeometryPositionsHelioMeters(
      Geometry geom, Vector3D[] positionsHelioMeters) {
    Objects.requireNonNull(geom, "geom");
    Objects.requireNonNull(positionsHelioMeters, "positionsHelioMeters");
    if (positionsHelioMeters.length < 2) {
      throw new IllegalArgumentException("positionsHelioMeters must contain at least 2 points");
    }

    Mesh mesh = geom.getMesh();
    if (mesh == null) {
      throw new IllegalStateException("Geometry has no mesh: " + geom.getName());
    }

    VertexBuffer vb = mesh.getBuffer(VertexBuffer.Type.Position);
    FloatBuffer fb = null;

    if (vb != null && vb.getData() instanceof FloatBuffer existing) {
      fb = existing;
      int required = positionsHelioMeters.length * 3;
      if (fb.capacity() < required) {
        fb = BufferUtils.createFloatBuffer(required);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, fb);
      } else {
        fb.clear();
      }
    } else {
      fb = BufferUtils.createFloatBuffer(positionsHelioMeters.length * 3);
      mesh.setBuffer(VertexBuffer.Type.Position, 3, fb);
    }

    RenderContext ctx = RenderContext.solar();
    for (Vector3D pHelio : positionsHelioMeters) {
      Vector3D jme = RenderTransform.toRenderUnitsJmeAxes(pHelio, null, ctx);
      fb.put((float) jme.getX()).put((float) jme.getY()).put((float) jme.getZ());
    }
    fb.flip();

    mesh.updateBound();
    mesh.updateCounts();

    VertexBuffer pos = mesh.getBuffer(VertexBuffer.Type.Position);
    if (pos != null) {
      pos.setUpdateNeeded();
    }
  }
}
