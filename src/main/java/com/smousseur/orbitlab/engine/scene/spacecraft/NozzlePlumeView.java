package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.NozzlePlumeConfig;
import com.smousseur.orbitlab.engine.PlumeLookConfig;
import com.smousseur.orbitlab.engine.PlumeShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The exhaust plumes of a launcher's firing nozzle clusters, up close: one jet per cluster of the
 * mesh drawn, a cone marched in {@code NozzlePlume.frag}.
 *
 * <h2>Where they hang, and which way they point</h2>
 *
 * <p>The launcher's mesh lives in its model bucket, where its orientation and its render-only seat
 * are applied; the bucket is emptied whenever a model is attached, and the eclipse writes its
 * uniforms on everything in it. So the plumes hang under the same 3D anchor, in a pivot beside the
 * bucket, which takes the bucket's seat and rotation every frame, and the model's own scale — the
 * stack's height, the one unit of the nozzle table. Each jet sits at its cluster's exit, in the
 * frame the table is measured in, and runs along the mesh's {@code -Y}: out of the tail of the body
 * as it is drawn.
 *
 * <h2>Which mesh</h2>
 *
 * <p>The jets are those of the mesh actually attached, handed over by the loading thread, not of
 * the silhouette the date calls for: a mesh swap is asynchronous, and jets laid out for the next
 * mesh would fire from nozzles the old one does not have. A mesh the table does not know — a
 * payload — fires none.
 *
 * <h2>When they show</h2>
 *
 * <p>Only when the body itself is drawn this frame — which follows the LOD switch and the hiding
 * before a jettison — and while a burn lights at least one cluster of its mesh.
 */
public final class NozzlePlumeView {

  /** Name of the node the plumes hang under the anchor by. */
  static final String PIVOT_NAME = "NozzlePlume-pivot";

  /**
   * How much wider than the jet's cone the hull is drawn: its 24-sided rings sit inside their
   * circle by under 1 %, and a ray grazing the cone must still meet the hull.
   */
  private static final float HULL_MARGIN = 1.05f;

  /**
   * The clusters of a mesh and the model's scale, handed over together by the loading thread.
   *
   * @param clusters the mesh's nozzle clusters
   * @param modelScale the model's scale, the stack's height in render units
   */
  private record Attached(List<LauncherNozzles.Cluster> clusters, float modelScale) {}

  /** One cluster's jet and the cone its hull was last fitted to. */
  private static final class Jet {
    private final LauncherNozzles.Cluster cluster;
    private final Geometry geometry;
    private float length = Float.NaN;
    private float tanAlpha = Float.NaN;

    private Jet(LauncherNozzles.Cluster cluster, Geometry geometry) {
      this.cluster = cluster;
      this.geometry = geometry;
    }
  }

  private final Node pivot = new Node(PIVOT_NAME);
  private final NozzlePlumeConfig config;
  private final List<Jet> jets = new ArrayList<>();

  /** The mesh attached, set from the loading thread; null until a model has loaded. */
  private volatile Attached attached;

  /** The mesh the jets were last built for; null until the first build. */
  private Attached built;

  /**
   * Builds the plumes of a launcher, put out, and hangs them under its 3D anchor.
   *
   * @param anchor3d the anchor the launcher's model bucket hangs under
   * @param config the plume tuning
   */
  public NozzlePlumeView(Node anchor3d, NozzlePlumeConfig config) {
    Objects.requireNonNull(anchor3d, "anchor3d");
    this.config = Objects.requireNonNull(config, "config");
    pivot.setCullHint(Spatial.CullHint.Always);
    anchor3d.attachChild(pivot);
  }

  /**
   * Hands over the mesh about to be attached. Safe from the loading thread: the jets are rebuilt
   * for it on the render thread, by the next {@link #update}.
   *
   * @param meshPath the GLTF asset path of the mesh
   * @param modelScale the model's scale, the stack's height in render units
   */
  public void setMesh(String meshPath, float modelScale) {
    this.attached = new Attached(LauncherNozzles.clustersOf(meshPath), modelScale);
  }

  /**
   * Poses, shapes and lights the plumes for this frame, or puts them out.
   *
   * @param drive what drives the plumes now, a function of the date
   * @param body the launcher's model bucket, whose local translation is the seat and local rotation
   *     the body's attitude
   * @param bodyVisible whether the launcher's model is drawn this frame
   * @param cameraWorld the camera's position in the world, to tell whether it is inside a jet
   */
  public void update(PlumeDrive drive, Spatial body, boolean bodyVisible, Vector3f cameraWorld) {
    Attached mesh = attached;
    if (!bodyVisible || mesh == null || drive.role() == null) {
      putOut();
      return;
    }
    float[] intensities =
        PlumeLighting.intensities(
            mesh.clusters(), drive.role(), drive.coreThrottle(), drive.ramp());
    if (!anyLit(intensities)) {
      putOut();
      return;
    }
    if (mesh != built) {
      rebuild(mesh);
      built = mesh;
    }
    pivot.setLocalTranslation(body.getLocalTranslation());
    pivot.setLocalRotation(body.getLocalRotation());
    pivot.setLocalScale(mesh.modelScale());
    float turbulence = ReentryFlowClock.turbulence(drive.clockRate());
    for (int i = 0; i < jets.size(); i++) {
      shape(jets.get(i), intensities[i], drive, turbulence, cameraWorld);
    }
    pivot.setCullHint(Spatial.CullHint.Inherit);
  }

  /**
   * Puts the plumes out without waiting for the next {@link #update} — for a launcher hidden whole,
   * which is not updated again until it shows.
   */
  public void putOut() {
    pivot.setCullHint(Spatial.CullHint.Always);
  }

  /**
   * Where a cluster's exit is drawn: the body's seat, plus its attitude applied to the exit of the
   * table, scaled to the stack.
   *
   * @param seat the body's translation under the anchor
   * @param bodyRotation the body's rotation under the anchor
   * @param modelScale the model's scale, the stack's height in render units
   * @param cluster the cluster
   * @return the exit's centre, under the anchor
   */
  static Vector3f exitPoint(
      Vector3f seat, Quaternion bodyRotation, float modelScale, LauncherNozzles.Cluster cluster) {
    Vector3f exit = new Vector3f(cluster.exitX(), cluster.exitY(), cluster.exitZ());
    return bodyRotation.mult(exit.multLocal(modelScale)).addLocal(seat);
  }

  private void rebuild(Attached mesh) {
    pivot.detachAllChildren();
    jets.clear();
    for (LauncherNozzles.Cluster cluster : mesh.clusters()) {
      Material material = AssetFactory.get().createNozzlePlume(config);
      PlumeLookConfig look = config.look(cluster.look());
      material.setColor("CoreColor", colour(look.core()));
      material.setColor("EdgeColor", colour(look.edge()));
      material.setFloat("ExitRadius", cluster.radius());
      Geometry geometry = new Geometry("NozzlePlume-" + cluster.block(), PlumeHullMesh.create());
      geometry.setMaterial(material);
      geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
      geometry.setLocalTranslation(cluster.exitX(), cluster.exitY(), cluster.exitZ());
      pivot.attachChild(geometry);
      jets.add(new Jet(cluster, geometry));
    }
  }

  private void shape(
      Jet jet, float intensity, PlumeDrive drive, float turbulence, Vector3f cameraWorld) {
    float radius = jet.cluster.radius();
    PlumeShape shape = config.look(jet.cluster.look()).shapeAt(drive.pressureFactor());
    float length = shape.lengthRadii() * radius;
    float tanAlpha = FastMath.tan(shape.halfAngleDeg() * FastMath.DEG_TO_RAD);
    Geometry geometry = jet.geometry;
    if (length != jet.length || tanAlpha != jet.tanAlpha) {
      PlumeHullMesh.reshape(
          geometry.getMesh(),
          HULL_MARGIN * radius,
          HULL_MARGIN * (radius + length * tanAlpha),
          HULL_MARGIN * length);
      geometry.updateModelBound();
      jet.length = length;
      jet.tanAlpha = tanAlpha;
    }
    Material material = geometry.getMaterial();
    material.setFloat("Length", length);
    material.setFloat("TanAlpha", tanAlpha);
    material.setFloat("Brightness", shape.brightness());
    material.setFloat("Opacity", shape.opacity());
    material.setFloat("Diamonds", shape.diamonds());
    material.setFloat("Intensity", intensity);
    material.setFloat("FlowSeconds", drive.flowSeconds());
    material.setFloat("Turbulence", turbulence);
    material
        .getAdditionalRenderState()
        .setFaceCullMode(
            insideHull(geometry, cameraWorld, radius, length, tanAlpha)
                ? RenderState.FaceCullMode.Front
                : RenderState.FaceCullMode.Back);
  }

  /** Whether the camera is inside a jet's hull, where its near wall is behind it. */
  private static boolean insideHull(
      Geometry geometry, Vector3f cameraWorld, float radius, float length, float tanAlpha) {
    Vector3f local = geometry.worldToLocal(cameraWorld, new Vector3f());
    float s = -local.y;
    if (s < 0f || s > HULL_MARGIN * length) {
      return false;
    }
    float across = FastMath.sqrt(local.x * local.x + local.z * local.z);
    return across <= HULL_MARGIN * (radius + s * tanAlpha);
  }

  private static boolean anyLit(float[] intensities) {
    for (float intensity : intensities) {
      if (intensity > 0f) {
        return true;
      }
    }
    return false;
  }

  private static ColorRGBA colour(PlumeLookConfig.Rgb rgb) {
    return new ColorRGBA(rgb.r(), rgb.g(), rgb.b(), 1f);
  }
}
