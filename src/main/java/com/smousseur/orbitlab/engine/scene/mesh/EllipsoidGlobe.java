package com.smousseur.orbitlab.engine.scene.mesh;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.utils.Constants;

/**
 * The Earth's drawn globe, generated rather than loaded: a latitude/longitude grid laid on an
 * oblate ellipsoid, each ring at its geodetic latitude, in the axes and texture convention of the
 * {@code earth.gltf} asset whose mesh it replaces.
 *
 * <p><b>Why generated.</b> A sphere drawn at the equatorial radius leaves a WGS84 ground point up
 * to 21.4 km off its surface, and coarse facets add up to 15.5 km more. Vertices laid on the
 * ellipsoid leave every ground point on or above the drawn surface; and the one class that places
 * the vertices is also the one that says how far below the ellipsoid a facet runs, so the mesh and
 * the ground correction built on it cannot drift apart.
 *
 * <p><b>Orientation.</b> The asset carries texture column {@code u} at azimuth {@code L = 360u −
 * 180} with {@code y = −sin L} — {@code u = 0.25} on {@code +Y}, measured on its raw vertices — and
 * {@code v = 0} on {@code +Z}. The natural {@code (cos L, sin L)} keeps the pole and the prime
 * meridian and still mirrors the map, which no frame check on pole and meridian alone would catch;
 * every position therefore goes through {@link #surface}.
 *
 * <p>Units are the model's own: semi-major axis 1, the scale being carried by the scene graph.
 * Latitudes and azimuths are in degrees, the grid's native unit.
 */
public final class EllipsoidGlobe {

  /**
   * Bands of the Earth grid, for a 1° step: its deepest facet runs 484 m below the ellipsoid, at
   * the centre of an equatorial cell, against 4.9 km for one texel of the 8192-wide map.
   */
  public static final int EARTH_BANDS = 180;

  private static final int BOWRING_ITERATIONS = 3;

  private final double flattening;
  private final double eccentricitySquared;
  private final double secondEccentricitySquared;
  private final double polarRadius;
  private final int bands;
  private final int columns;
  private final double latitudeStep;
  private final double azimuthStep;

  /**
   * A point of the drawn globe's parameter space.
   *
   * @param latitudeDeg geodetic latitude, in degrees
   * @param azimuthDeg texture azimuth {@code L = 360u − 180}, in degrees, in {@code [−180, 180]}
   * @param height height above the ellipsoid along its normal, in model units
   */
  public record Geodetic(double latitudeDeg, double azimuthDeg, double height) {}

  /**
   * Builds a grid on an ellipsoid of semi-major axis 1.
   *
   * @param flattening the ellipsoid's flattening, in {@code [0, 1)}
   * @param bands the number of latitude bands, the grid having twice as many columns
   */
  public EllipsoidGlobe(double flattening, int bands) {
    if (flattening < 0.0 || flattening >= 1.0) {
      throw new IllegalArgumentException("flattening must be in [0, 1), got " + flattening);
    }
    if (bands < 2) {
      throw new IllegalArgumentException("a grid needs at least two bands, got " + bands);
    }
    this.flattening = flattening;
    this.eccentricitySquared = flattening * (2.0 - flattening);
    this.polarRadius = 1.0 - flattening;
    this.secondEccentricitySquared = eccentricitySquared / (polarRadius * polarRadius);
    this.bands = bands;
    this.columns = 2 * bands;
    this.latitudeStep = 180.0 / bands;
    this.azimuthStep = 360.0 / columns;
  }

  /**
   * The Earth's globe: the WGS84 flattening the physics lays its ground on, and {@link
   * #EARTH_BANDS} bands.
   *
   * @return the Earth grid
   */
  public static EllipsoidGlobe earth() {
    return new EllipsoidGlobe(Constants.WGS84_EARTH_FLATTENING, EARTH_BANDS);
  }

  /**
   * The point of the ellipsoid at a geodetic latitude and a texture azimuth.
   *
   * @param latitudeDeg geodetic latitude, in degrees
   * @param azimuthDeg texture azimuth, in degrees
   * @return the point, in model units
   */
  public Vector3D surface(double latitudeDeg, double azimuthDeg) {
    double phi = Math.toRadians(latitudeDeg);
    double azimuth = Math.toRadians(azimuthDeg);
    double sinPhi = Math.sin(phi);
    double cosPhi = Math.cos(phi);
    double nu = 1.0 / Math.sqrt(1.0 - eccentricitySquared * sinPhi * sinPhi);
    return new Vector3D(
        nu * cosPhi * Math.cos(azimuth),
        -nu * cosPhi * Math.sin(azimuth),
        nu * (1.0 - eccentricitySquared) * sinPhi);
  }

  /**
   * The ellipsoid's outward unit normal — the geodetic up — at a latitude and an azimuth.
   *
   * @param latitudeDeg geodetic latitude, in degrees
   * @param azimuthDeg texture azimuth, in degrees
   * @return the unit normal
   */
  public Vector3D normal(double latitudeDeg, double azimuthDeg) {
    double phi = Math.toRadians(latitudeDeg);
    double azimuth = Math.toRadians(azimuthDeg);
    double cosPhi = Math.cos(phi);
    return new Vector3D(cosPhi * Math.cos(azimuth), -cosPhi * Math.sin(azimuth), Math.sin(phi));
  }

  /**
   * Converts a model-space point to its geodetic latitude, texture azimuth and height.
   *
   * <p>Bowring's iteration on the parametric latitude: three passes are exact to double precision
   * from the ground to far beyond the heights this is asked about, and the height formula {@code
   * r·cos φ + z·sin φ − √(1 − e²·sin²φ)} holds at the poles, where {@code r / cos φ} does not.
   *
   * @param point the point, in model units
   * @return its geodetic coordinates
   */
  public Geodetic geodetic(Vector3D point) {
    double x = point.getX();
    double y = point.getY();
    double z = point.getZ();
    double r = Math.hypot(x, y);
    double beta = Math.atan2(z, polarRadius * r);
    double phi = beta;
    for (int i = 0; i < BOWRING_ITERATIONS; i++) {
      double sinBeta = Math.sin(beta);
      double cosBeta = Math.cos(beta);
      phi =
          Math.atan2(
              z + secondEccentricitySquared * polarRadius * sinBeta * sinBeta * sinBeta,
              r - eccentricitySquared * cosBeta * cosBeta * cosBeta);
      beta = Math.atan2(polarRadius * Math.sin(phi), Math.cos(phi));
    }
    double sinPhi = Math.sin(phi);
    double height =
        r * Math.cos(phi) + z * sinPhi - Math.sqrt(1.0 - eccentricitySquared * sinPhi * sinPhi);
    return new Geodetic(Math.toDegrees(phi), Math.toDegrees(Math.atan2(-y, x)), height);
  }

  /**
   * How far below the ellipsoid the drawn surface runs, along the ellipsoid's normal, at a latitude
   * and an azimuth: the height a ground point floats above the facet under it.
   *
   * <p>The cell comes straight from the indices, no ray cast; its triangle from the side of the
   * cell's diagonal, as {@link #toMesh} splits it (both triangles of a cell are coplanar, its four
   * corners forming an isosceles trapezoid on a surface of revolution, so the side only matters at
   * the caps). The depth is where the normal line through the ground point meets that plane.
   *
   * @param latitudeDeg geodetic latitude, in degrees
   * @param azimuthDeg texture azimuth, in degrees, any turn
   * @return the depth, in model units, zero on the vertices and never negative beyond rounding
   */
  public double facetDepth(double latitudeDeg, double azimuthDeg) {
    double wrapped = azimuthDeg - 360.0 * Math.floor((azimuthDeg + 180.0) / 360.0);
    double rowCoordinate = (90.0 - latitudeDeg) / latitudeStep;
    int row = Math.min(bands - 1, Math.max(0, (int) Math.floor(rowCoordinate)));
    double columnCoordinate = (wrapped + 180.0) / azimuthStep;
    int col = Math.min(columns - 1, Math.max(0, (int) Math.floor(columnCoordinate)));
    Vector3D[] facet = facet(row, col, rowCoordinate - row, columnCoordinate - col);

    Vector3D ground = surface(latitudeDeg, wrapped);
    Vector3D up = normal(latitudeDeg, wrapped);
    Vector3D planeNormal = facet[1].subtract(facet[0]).crossProduct(facet[2].subtract(facet[0]));
    return -facet[0].subtract(ground).dotProduct(planeNormal) / up.dotProduct(planeNormal);
  }

  /** The triangle of cell {@code (row, col)} holding the cell-local fractions {@code (s, t)}. */
  private Vector3D[] facet(int row, int col, double s, double t) {
    if (row == 0) {
      return new Vector3D[] {gridPoint(0, col), gridPoint(1, col + 1), gridPoint(1, col)};
    }
    if (row == bands - 1) {
      return new Vector3D[] {
        gridPoint(bands - 1, col), gridPoint(bands - 1, col + 1), gridPoint(bands, col)
      };
    }
    if (s >= t) {
      return new Vector3D[] {
        gridPoint(row, col), gridPoint(row + 1, col + 1), gridPoint(row + 1, col)
      };
    }
    return new Vector3D[] {
      gridPoint(row, col), gridPoint(row, col + 1), gridPoint(row + 1, col + 1)
    };
  }

  private Vector3D gridPoint(int ring, int col) {
    return surface(latitudeOf(ring), azimuthOf(col));
  }

  /**
   * Builds the drawn mesh: positions on the ellipsoid, the ellipsoid's own normals so the lighting
   * shows no facet, and UVs in the asset's equirectangular convention. Each pole is a fan with one
   * vertex per column, its {@code u} at the column's centre as the asset does, so the cap carries
   * no degenerate triangle and no texture swirl. Every triangle faces outward, counter-clockwise,
   * as the asset's do.
   *
   * @return a new mesh, in model units
   */
  public Mesh toMesh() {
    int vertexCount = 2 * columns + (bands - 1) * (columns + 1);
    float[] positions = new float[3 * vertexCount];
    float[] normals = new float[3 * vertexCount];
    float[] uvs = new float[2 * vertexCount];
    for (int col = 0; col < columns; col++) {
      float u = (col + 0.5f) / columns;
      putVertex(positions, normals, uvs, northIndex(col), 90.0, 0.0, u, 0f);
      putVertex(positions, normals, uvs, southIndex(col), -90.0, 0.0, u, 1f);
    }
    for (int ring = 1; ring < bands; ring++) {
      for (int col = 0; col <= columns; col++) {
        putVertex(
            positions,
            normals,
            uvs,
            ringIndex(ring, col),
            latitudeOf(ring),
            azimuthOf(col),
            (float) col / columns,
            (float) ring / bands);
      }
    }

    int[] indices = new int[3 * 2 * columns * (bands - 1)];
    int k = 0;
    for (int col = 0; col < columns; col++) {
      k = putTriangle(indices, k, northIndex(col), ringIndex(1, col + 1), ringIndex(1, col));
    }
    for (int ring = 1; ring < bands - 1; ring++) {
      for (int col = 0; col < columns; col++) {
        int p00 = ringIndex(ring, col);
        int p10 = ringIndex(ring + 1, col);
        int p01 = ringIndex(ring, col + 1);
        int p11 = ringIndex(ring + 1, col + 1);
        k = putTriangle(indices, k, p00, p11, p10);
        k = putTriangle(indices, k, p00, p01, p11);
      }
    }
    for (int col = 0; col < columns; col++) {
      k =
          putTriangle(
              indices,
              k,
              ringIndex(bands - 1, col),
              ringIndex(bands - 1, col + 1),
              southIndex(col));
    }

    Mesh mesh = new Mesh();
    mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
    mesh.setBuffer(VertexBuffer.Type.Normal, 3, normals);
    mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, uvs);
    mesh.setBuffer(VertexBuffer.Type.Index, 3, indices);
    mesh.updateBound();
    mesh.setStatic();
    return mesh;
  }

  private void putVertex(
      float[] positions,
      float[] normals,
      float[] uvs,
      int index,
      double latitudeDeg,
      double azimuthDeg,
      float u,
      float v) {
    Vector3D p = surface(latitudeDeg, azimuthDeg);
    Vector3D n = normal(latitudeDeg, azimuthDeg);
    positions[3 * index] = (float) p.getX();
    positions[3 * index + 1] = (float) p.getY();
    positions[3 * index + 2] = (float) p.getZ();
    normals[3 * index] = (float) n.getX();
    normals[3 * index + 1] = (float) n.getY();
    normals[3 * index + 2] = (float) n.getZ();
    uvs[2 * index] = u;
    uvs[2 * index + 1] = v;
  }

  private static int putTriangle(int[] indices, int at, int a, int b, int c) {
    indices[at] = a;
    indices[at + 1] = b;
    indices[at + 2] = c;
    return at + 3;
  }

  private double latitudeOf(int ring) {
    return 90.0 - ring * latitudeStep;
  }

  private double azimuthOf(int col) {
    return -180.0 + col * azimuthStep;
  }

  private int northIndex(int col) {
    return col;
  }

  private int ringIndex(int ring, int col) {
    return columns + (ring - 1) * (columns + 1) + col;
  }

  private int southIndex(int col) {
    return columns + (bands - 1) * (columns + 1) + col;
  }

  /**
   * The flattening this globe was built with.
   *
   * @return the flattening
   */
  public double flattening() {
    return flattening;
  }

  /**
   * The number of latitude bands.
   *
   * @return the bands
   */
  public int bands() {
    return bands;
  }

  /**
   * The number of longitude columns, twice the bands.
   *
   * @return the columns
   */
  public int columns() {
    return columns;
  }
}
