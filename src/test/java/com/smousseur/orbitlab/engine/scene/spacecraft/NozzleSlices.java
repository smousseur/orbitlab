package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads a nozzle cluster off a launcher mesh, in the frame the silhouettes are drawn in — one unit
 * is the full stack, the base at the origin: its exit plane is the lowest vertex read, its centre
 * the middle of the box of the vertices within a thin band above it, and its radius the widest of
 * those about that centre. The band is the cluster: a Falcon Heavy core's named engine node holds
 * its centre Merlin alone, the other eight being an unnamed mesh beside it.
 *
 * <p>A payload mesh is read the same way once mounted, in its own native units: see {@link
 * #payloadEngine}.
 */
final class NozzleSlices {

  /** Height of the bottom slice, in stack units: about 0.2 m on either launcher. */
  static final float BAND = 0.003f;

  /**
   * A vertex in the model's world frame, and the geometry it belongs to.
   *
   * @param at the vertex position
   * @param geometry the name of the geometry holding it
   */
  record Tagged(Vector3f at, String geometry) {}

  /**
   * One cluster's reading.
   *
   * @param exitY the exit plane's height (stack units)
   * @param topY the top of the vertices read (stack units)
   * @param centreX the centre's x (stack units)
   * @param centreZ the centre's z (stack units)
   * @param radius the widest vertex about the centre, in the XZ plane (stack units)
   * @param vertices how many vertices were read
   * @param meshes the geometries they come from, with their vertex counts
   */
  record Reading(
      float exitY,
      float topY,
      float centreX,
      float centreZ,
      float radius,
      int vertices,
      String meshes) {}

  private NozzleSlices() {}

  /** Every vertex under a spatial, in the model's world frame, tagged with its geometry. */
  static List<Tagged> vertices(Spatial spatial) {
    List<Tagged> out = new ArrayList<>();
    spatial.depthFirstTraversal(
        new SceneGraphVisitorAdapter() {
          @Override
          public void visit(Geometry geometry) {
            Mesh mesh = geometry.getMesh();
            VertexBuffer positions = mesh.getBuffer(VertexBuffer.Type.Position);
            if (positions == null) {
              return;
            }
            FloatBuffer data = (FloatBuffer) positions.getData();
            Transform world = geometry.getWorldTransform();
            Vector3f local = new Vector3f();
            for (int i = 0; i + 2 < data.limit(); i += 3) {
              local.set(data.get(i), data.get(i + 1), data.get(i + 2));
              out.add(new Tagged(world.transformVector(local, new Vector3f()), geometry.getName()));
            }
          }
        });
    return out;
  }

  /**
   * The vertices standing over a disc of the XZ plane: those of a cluster, picked out of a whole
   * stack by where the cluster is expected.
   *
   * @param vertices the stack's vertices
   * @param centreX the disc's centre x (stack units)
   * @param centreZ the disc's centre z (stack units)
   * @param radius the disc's radius (stack units)
   * @return the vertices over the disc
   */
  static List<Tagged> over(List<Tagged> vertices, float centreX, float centreZ, float radius) {
    List<Tagged> out = new ArrayList<>();
    for (Tagged t : vertices) {
      float dx = t.at().x - centreX;
      float dz = t.at().z - centreZ;
      if (dx * dx + dz * dz <= radius * radius) {
        out.add(t);
      }
    }
    return out;
  }

  /**
   * The vertices of a mounted payload mesh's engine, in its own frame — engine down, exit at the
   * origin. No payload mesh names its engine, so each is picked out its own way: {@code goes} and
   * {@code lro} by a disc about the vertical axis, past the engine's rim and short of what hangs
   * beside it (on {@code goes}, a mast 0.24 off the axis that reaches further down than the
   * engine); {@code landsat8} by the materials of its ring of thrusters, since a disc wide enough
   * to hold the ring also holds a fitting that reaches lower than the ring, by 0.0044 of the mesh's
   * native units.
   *
   * @param path the payload mesh's asset path
   * @param mounted the mesh, mounted and with its geometric state updated
   * @return the engine's vertices
   */
  static List<Tagged> payloadEngine(String path, Spatial mounted) {
    if (path.endsWith("landsat8.gltf")) {
      return verticesOfMaterial(mounted, "thruster");
    }
    float pick = path.endsWith("goes.gltf") ? 0.1f : 0.06f;
    return over(vertices(mounted), 0f, 0f, pick);
  }

  private static List<Tagged> verticesOfMaterial(Spatial spatial, String fragment) {
    List<Tagged> out = new ArrayList<>();
    spatial.depthFirstTraversal(
        new SceneGraphVisitorAdapter() {
          @Override
          public void visit(Geometry geometry) {
            String material =
                geometry.getMaterial() == null ? null : geometry.getMaterial().getName();
            if (material != null && material.contains(fragment)) {
              out.addAll(vertices(geometry));
            }
          }
        });
    return out;
  }

  /** The vertices within {@link #BAND} of the lowest one, read as a cluster. */
  static Reading sliceOf(List<Tagged> vertices) {
    float lowest = Float.POSITIVE_INFINITY;
    for (Tagged v : vertices) {
      lowest = Math.min(lowest, v.at().y);
    }
    List<Tagged> band = new ArrayList<>();
    for (Tagged v : vertices) {
      if (v.at().y <= lowest + BAND) {
        band.add(v);
      }
    }
    return read(band);
  }

  /** The vertices read whole as a cluster. */
  static Reading read(List<Tagged> vertices) {
    float minX = Float.POSITIVE_INFINITY;
    float maxX = Float.NEGATIVE_INFINITY;
    float minY = Float.POSITIVE_INFINITY;
    float maxY = Float.NEGATIVE_INFINITY;
    float minZ = Float.POSITIVE_INFINITY;
    float maxZ = Float.NEGATIVE_INFINITY;
    Map<String, Integer> meshes = new LinkedHashMap<>();
    for (Tagged t : vertices) {
      Vector3f v = t.at();
      minX = Math.min(minX, v.x);
      maxX = Math.max(maxX, v.x);
      minY = Math.min(minY, v.y);
      maxY = Math.max(maxY, v.y);
      minZ = Math.min(minZ, v.z);
      maxZ = Math.max(maxZ, v.z);
      meshes.merge(t.geometry(), 1, Integer::sum);
    }
    float centreX = 0.5f * (minX + maxX);
    float centreZ = 0.5f * (minZ + maxZ);
    float radius = 0f;
    for (Tagged t : vertices) {
      float dx = t.at().x - centreX;
      float dz = t.at().z - centreZ;
      radius = Math.max(radius, (float) Math.sqrt(dx * dx + dz * dz));
    }
    String composition =
        meshes.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(4)
                .map(e -> e.getKey() + "x" + e.getValue())
                .collect(Collectors.joining(", "))
            + (meshes.size() > 4 ? ", +" + (meshes.size() - 4) + " more" : "");
    return new Reading(minY, maxY, centreX, centreZ, radius, vertices.size(), composition);
  }
}
