package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * How each payload mesh is mounted to be drawn on the launcher's convention: its engine firing
 * along {@code -Y}, so the thrust axis is the {@code +Y} every piece flies first, and the engine's
 * exit centre at the origin, the way a launcher piece stands on its base. A render concern only:
 * nothing in the propagation knows which way a mesh was authored.
 *
 * <p>The payload meshes are third-party assets that follow no convention: their origins sit
 * anywhere in the body, and their engines face {@code -Z} ({@code goes}), {@code -X} ({@code
 * landsat8}) or {@code +Y} ({@code lro}). None of them names its engine, which was identified on
 * renders of the mesh: on {@code goes} the small cone at the centre of the ring of its {@code -Z}
 * face; on {@code landsat8} the ring of thrusters of its {@code -X} face, the one part whose
 * materials say {@code thruster}; on {@code lro} the central object of the crown of its {@code +Y}
 * face, opposite the CRaTER instrument.
 *
 * <p>Each mount turns its mesh by the simplest quarter or half turn about one of its axes that puts
 * the engine's axis on {@code +Y}; the roll about that axis is whatever that turn leaves. Every
 * value is measured on the asset, in its native units, and pinned by a test against it: a
 * re-exported mesh owes the table a new measurement.
 */
public final class PayloadMounts {

  /**
   * One mesh's mount.
   *
   * @param rotation the turn that puts the engine's axis on {@code +Y}, the jet on {@code -Y}
   * @param exitX the engine's exit centre, {@code x}, in the mesh's own native frame — before the
   *     turn
   * @param exitY the same, {@code y}
   * @param exitZ the same, {@code z}
   * @param heightAboveExitFraction how far the mounted mesh reaches above its engine's exit plane,
   *     as a fraction of its largest extent — the share of its drawn size that stands above the
   *     origin
   */
  public record Mount(
      Quaternion rotation, float exitX, float exitY, float exitZ, float heightAboveExitFraction) {

    /** Copies the rotation, which JME leaves mutable. */
    public Mount {
      rotation = rotation.clone();
    }

    @Override
    public Quaternion rotation() {
      return rotation.clone();
    }

    /**
     * Mounts a freshly loaded model: turns it, then shifts it so its engine's exit centre lies at
     * the origin. The shift is scaled by the model's own (uniform) scale, so a model already
     * normalized to its drawn size is mounted at that size.
     *
     * @param model the loaded model, its scale set, its rotation and translation still the loader's
     *     identity
     */
    public void applyTo(Spatial model) {
      float scale = model.getLocalScale().y;
      model.setLocalRotation(rotation);
      model.setLocalTranslation(
          rotation.mult(new Vector3f(exitX, exitY, exitZ)).negateLocal().multLocal(scale));
    }
  }

  /**
   * Measured on each asset: the exit centre is the middle of the engine's lowest 0.003 native units
   * once turned, and the height is the mounted mesh's top above that plane.
   */
  private static final Map<String, Mount> MOUNTS =
      Map.of(
          PayloadAssets.GOES_MESH,
          new Mount(turn(-FastMath.HALF_PI, Vector3f.UNIT_X), 0f, 0f, -0.10597f, 0.38547f),
          PayloadAssets.LANDSAT8_MESH,
          new Mount(
              turn(FastMath.HALF_PI, Vector3f.UNIT_Z), -0.49515f, -0.00791f, 0.00361f, 0.40367f),
          PayloadAssets.LRO_MESH,
          new Mount(turn(FastMath.PI, Vector3f.UNIT_X), 0.00007f, 0.38035f, 0f, 0.70290f));

  private PayloadMounts() {}

  /**
   * The mount of a payload mesh.
   *
   * @param meshPath the GLTF asset path, or {@code null}
   * @return its mount, or {@link Optional#empty()} for a mesh that is not a payload's (a launcher
   *     piece is authored on the convention already) or a {@code null} path
   */
  public static Optional<Mount> mountOf(String meshPath) {
    return meshPath == null ? Optional.empty() : Optional.ofNullable(MOUNTS.get(meshPath));
  }

  /**
   * Mounts a freshly loaded model of a mesh, if the mesh has a mount; leaves it as loaded
   * otherwise. Call it before anything measures the model or draws it, so they all see it mounted.
   *
   * @param meshPath the GLTF asset path the model was loaded from
   * @param model the model, scale set, rotation and translation still the loader's identity
   * @return the same model
   */
  public static Spatial mount(String meshPath, Spatial model) {
    mountOf(meshPath).ifPresent(mount -> mount.applyTo(model));
    return model;
  }

  /**
   * Every mesh the table mounts.
   *
   * @return their GLTF asset paths
   */
  public static Set<String> meshPaths() {
    return MOUNTS.keySet();
  }

  private static Quaternion turn(float angle, Vector3f axis) {
    return new Quaternion().fromAngleAxis(angle, axis);
  }
}
