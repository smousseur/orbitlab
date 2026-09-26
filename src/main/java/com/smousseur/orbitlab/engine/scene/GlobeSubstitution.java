package com.smousseur.orbitlab.engine.scene;

import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.mesh.EllipsoidGlobe;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Swaps the Earth asset's globe mesh for the generated WGS84 grid ({@link EllipsoidGlobe}), while
 * the model is still unattached and private to the loading thread.
 *
 * <p>The material and its texture, and the asset's node chain and rotations, stay as authored, so
 * λ0 and the committed calibration keep meaning what they meant. Only the scale is made exact: the
 * generated mesh is drawn at precisely {@link #ROOT_RELATIVE_SCALE} of the model root, because the
 * asset's node carries a Blender export noise of up to 1.5e-6 — ten metres at the Earth's radius —
 * and the ground correction brings objects onto the ellipsoid this class generates, not onto one
 * ten metres wider. It runs before {@link MeshGuard#verify}, which then measures the generated
 * mesh: the guard checks what is drawn, not what the file holds.
 */
public final class GlobeSubstitution {

  /**
   * The scale the globe geometry must carry relative to the model root: {@code loadModel} scales a
   * planet's root by its diameter, the asset convention being a unit-diameter globe, while {@link
   * EllipsoidGlobe} has a semi-major axis of 1.
   */
  static final float ROOT_RELATIVE_SCALE = 0.5f;

  private static final Logger logger = LogManager.getLogger(GlobeSubstitution.class);

  private GlobeSubstitution() {}

  /**
   * Replaces the Earth globe's mesh; leaves every other body alone.
   *
   * @param body the body being loaded
   * @param model its freshly loaded model, not yet attached
   * @return whether the mesh was replaced — {@code false} for any other body, and for an Earth
   *     asset that no longer holds exactly one geometry, which is logged and left as authored
   *     rather than failing the load and losing the globe
   */
  public static boolean apply(SolarSystemBody body, Spatial model) {
    if (body != SolarSystemBody.EARTH) {
      return false;
    }
    List<Geometry> geometries = new ArrayList<>();
    model.depthFirstTraversal(
        new SceneGraphVisitorAdapter() {
          @Override
          public void visit(Geometry geometry) {
            geometries.add(geometry);
          }
        });
    if (geometries.size() != 1) {
      logger.error(
          "Earth asset holds {} geometries instead of one; its globe is left as authored, and"
              + " objects on the ground will float above or sink into it.",
          geometries.size());
      return false;
    }
    Geometry globe = geometries.get(0);
    globe.setMesh(EllipsoidGlobe.earth().toMesh());
    Vector3f chain = scaleBelow(model, globe);
    globe.setLocalScale(
        globe
            .getLocalScale()
            .mult(
                new Vector3f(
                    ROOT_RELATIVE_SCALE / chain.x,
                    ROOT_RELATIVE_SCALE / chain.y,
                    ROOT_RELATIVE_SCALE / chain.z)));
    model.updateModelBound();
    return true;
  }

  /** The product of the local scales from {@code geometry} up to, but excluding, {@code root}. */
  private static Vector3f scaleBelow(Spatial root, Geometry geometry) {
    Vector3f scale = new Vector3f(1f, 1f, 1f);
    for (Spatial node = geometry; node != null && node != root; node = node.getParent()) {
      scale.multLocal(node.getLocalScale());
    }
    return scale;
  }
}
