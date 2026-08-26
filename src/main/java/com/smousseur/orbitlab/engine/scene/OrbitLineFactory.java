package com.smousseur.orbitlab.engine.scene;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.simulation.orbit.OrbitPath;
import com.smousseur.orbitlab.simulation.source.EphemerisSource;
import com.smousseur.orbitlab.simulation.source.EphemerisSourceRegistry;
import java.util.List;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

/**
 * Factory for creating and updating the JME3 geometries that represent orbital paths.
 *
 * <p>Supports both heliocentric and body-relative orbit visualizations by converting positions from
 * ICRF meters to JME render units and axes. All methods are static; this class cannot be
 * instantiated.
 *
 * <p><b>Orbits are ribbons, not lines</b> (spec {@code docs/graphics-effects/ribbon-lines.md}). The
 * primitive is a {@code TriangleStrip} expanded to face the camera by {@code
 * MatDefs/Fx/Ribbon.j3md} — because {@code glLineWidth} above 1 is silently clamped back to 1 px in
 * a core profile, so the width these methods used to take was never honoured and ten one-pixel
 * threads were competing with the noise of the skybox.
 *
 * <p>The expansion is in the vertex shader and not here, and that is the whole reason the buffers
 * below are still written once per window rebuild rather than once per frame: the geometry depends
 * only on the data, and only the uniforms depend on the camera (§2).
 */
public final class OrbitLineFactory {

  /** Solar view scale: 1 JME unit = 1e9 meters. */
  public static final double SOLAR_METERS_PER_UNIT = 1.0e9;

  /**
   * Width of a planetary orbit, in screen pixels (§7.4).
   *
   * <p>A starting value, meant to be judged by eye — which the ribbon is the first thing to make
   * possible at all. Below ~1,5 px a ribbon reads as a thread again; above ~4 the ten orbits start
   * to crowd the inner system.
   */
  public static final float ORBIT_WIDTH_PX = 2.5f;

  /**
   * An orbit is a closed ring, and the ribbon is shut accordingly: the first pair of vertices is
   * repeated at the end, and the tangents at the seam are read across it.
   */
  private static final boolean CLOSED = true;

  private OrbitLineFactory() {}

  /**
   * Builds a heliocentric orbit ribbon from an orbit path. The orbit positions are rendered
   * relative to the solar system origin.
   *
   * @param path the orbit path containing heliocentric positions
   * @param color the color of the ribbon
   * @return a JME3 geometry representing the orbit
   */
  public static Geometry buildHeliocentricLineStrip(OrbitPath path, ColorRGBA color) {
    return buildBodyRelativeLineStrip(path, null, color);
  }

  /**
   * Builds a closed orbit ribbon from a list of position vectors, rendered relative to a given
   * solar system body.
   *
   * @param body the solar system body used for naming the geometry
   * @param pts the list of position vectors in meters
   * @param color the color of the ribbon
   * @return a JME3 geometry representing the orbit as a closed ring
   */
  public static Geometry buildBodyRelativeLineStrip(
      SolarSystemBody body, List<Vector3D> pts, ColorRGBA color) {
    Objects.requireNonNull(pts, "pts");
    Objects.requireNonNull(color, "color");
    if (pts.size() < 2) {
      throw new IllegalArgumentException("An orbit must contain at least 2 points");
    }

    RenderContext ctx = RenderContext.solar();
    float[] xyz = new float[pts.size() * 3];
    for (int i = 0; i < pts.size(); i++) {
      putRenderUnits(xyz, i, pts.get(i), ctx);
    }
    return ribbon("OrbitLine-" + body.name(), xyz, pts.size(), color);
  }

  /**
   * Builds an orbit ribbon from an orbit path, optionally centered on a given body. When a center
   * body is provided, each position is adjusted by subtracting the center body's heliocentric
   * position at the corresponding time step.
   *
   * @param path the orbit path containing heliocentric positions and timing data
   * @param centerBody the body to use as the reference center, or {@code null} for heliocentric
   *     coordinates
   * @param color the color of the ribbon
   * @return a JME3 geometry representing the orbit
   */
  public static Geometry buildBodyRelativeLineStrip(
      OrbitPath path, SolarSystemBody centerBody, ColorRGBA color) {

    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(color, "color");

    List<Vector3D> pts = path.positionsHelioMeters();
    if (pts.size() < 2) {
      throw new IllegalArgumentException("OrbitPath must contain at least 2 points");
    }

    RenderContext ctx = RenderContext.solar();
    float[] xyz = new float[pts.size() * 3];

    AbsoluteDate t = path.start();

    EphemerisSource source =
        EphemerisSourceRegistry.get()
            .orElseThrow(() -> new OrbitlabException("Cannot get Ephemeris source"));

    for (int i = 0; i < pts.size(); i++) {
      Vector3D centerHelio = Vector3D.ZERO;
      if (centerBody != null) {
        // TODO(DT-11): sampleIcrf falls back to the raw ephemeris source outside the dataset's
        // span, where sampleIcrfSafe would carry on with a Keplerian propagator. Doing it here
        // needs a propagator this method does not have and OrbitRuntimeAppState already builds —
        // which is the real fix, and not one to make in passing.
        centerHelio = source.sampleIcrf(centerBody, t).pvIcrf().getPosition();
      }
      putRenderUnits(xyz, i, pts.get(i).subtract(centerHelio), ctx);
    }

    return ribbon("OrbitLine-" + path.body().name(), xyz, pts.size(), color);
  }

  /**
   * Rewrites an existing orbit geometry's ribbon, positions <b>and tangents</b>, without rebuilding
   * the geometry or its material. Assumes positions are heliocentric meters.
   *
   * <p>The tangents are the part that is new and the part that cannot be skipped: a ribbon's
   * orientation is derived from them, so leaving them at the previous window's values would twist
   * the band by however much the orbit has moved. They are recomputed by {@link RibbonMeshBuilder},
   * from the same points, in the same pass.
   *
   * @param geom the orbit geometry to update
   * @param positionsHelioMeters the new ring, in heliocentric meters
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

    RenderContext ctx = RenderContext.solar();
    float[] xyz = new float[positionsHelioMeters.length * 3];
    for (int i = 0; i < positionsHelioMeters.length; i++) {
      putRenderUnits(xyz, i, positionsHelioMeters[i], ctx);
    }

    RibbonMeshBuilder.ensureCapacity(mesh, positionsHelioMeters.length, CLOSED);
    RibbonMeshBuilder.write(mesh, xyz, positionsHelioMeters.length, CLOSED);
  }

  /** Converts one ICRF position in meters into render units on JME axes, into slot {@code i}. */
  private static void putRenderUnits(float[] xyz, int i, Vector3D icrfMeters, RenderContext ctx) {
    Vector3D jme = RenderTransform.toRenderUnitsJmeAxes(icrfMeters, null, ctx);
    xyz[i * 3] = (float) jme.getX();
    xyz[i * 3 + 1] = (float) jme.getY();
    xyz[i * 3 + 2] = (float) jme.getZ();
  }

  /**
   * Assembles the ribbon mesh, material and geometry.
   *
   * <p>The transparent bucket is not decoration: the edge fade is an alpha ramp, and an alpha-
   * blended geometry left in the opaque bucket is drawn before the bodies that should occlude it.
   */
  private static Geometry ribbon(String name, float[] xyz, int pointCount, ColorRGBA color) {
    Mesh mesh = RibbonMeshBuilder.allocate(pointCount, CLOSED, false);
    RibbonMeshBuilder.write(mesh, xyz, pointCount, CLOSED);

    Material mat = AssetFactory.get().createRibbon(color, ORBIT_WIDTH_PX, false);

    Geometry geom = new Geometry(name, mesh);
    geom.setMaterial(mat);
    geom.setQueueBucket(RenderQueue.Bucket.Transparent);
    return geom;
  }
}
