package com.smousseur.orbitlab.engine.scene.body;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.engine.scene.mesh.ModelNodes;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns one shell of a model independently of the model it belongs to.
 *
 * <p>Venus is the case that calls for it (L4 of {@code docs/orientation-planetes/01-decoupage.md}):
 * its asset holds a surface globe and a separate atmosphere shell, and the two do not turn at the
 * same rate — the cloud deck laps the ground every four days while the ground itself takes eight
 * months. The renderer applies one rotation to the whole model, so the difference has to be applied
 * inside it.
 *
 * <p><b>Why a pivot rather than a rotation on the shell's own node.</b> The shell node already
 * carries the rotation the exporter gave it, which is not ours to overwrite. Splicing an
 * identity-transformed pivot above it leaves the asset's own chain intact and gives us a node whose
 * local rotation is entirely ours.
 *
 * @param pivot the node spliced above the shell, whose local rotation is the spin
 * @param axis the spin axis expressed in the pivot's <em>parent</em> axes, which is where a local
 *     rotation acts. It is not the axis the caller gave: that one is in the model's axes, and the
 *     nodes in between have rotations of their own
 */
public record ShellSpin(Node pivot, Vector3f axis) {

  public ShellSpin {
    Objects.requireNonNull(pivot, "pivot");
    Objects.requireNonNull(axis, "axis");
  }

  /**
   * Splices a pivot above the first node whose name starts with {@code namePrefix}, as {@link
   * ModelNodes#firstNamed} finds it.
   *
   * <p>Call before the model is attached: the axis conversion reads world rotations, which are the
   * model's own only while the model is its own root. It is otherwise safe on the asset-loading
   * thread, being a rearrangement of a tree nothing else can see yet.
   *
   * @param model the loaded model, not yet attached
   * @param namePrefix prefix of the shell node's name, as the asset names it
   * @param axisInModelAxes the spin axis in the model's own axes — for a planet, the pole the probe
   *     measured
   * @return the spin, or empty when the model carries no such shell
   */
  public static Optional<ShellSpin> isolate(
      Spatial model, String namePrefix, Vector3f axisInModelAxes) {
    Objects.requireNonNull(model, "model");
    Objects.requireNonNull(namePrefix, "namePrefix");
    Objects.requireNonNull(axisInModelAxes, "axisInModelAxes");

    model.updateGeometricState();
    Spatial shell = ModelNodes.firstNamed(model, namePrefix).orElse(null);
    if (shell == null || shell.getParent() == null) {
      return Optional.empty();
    }

    Node parent = shell.getParent();
    Node pivot = new Node(shell.getName() + "-spin");
    parent.detachChild(shell);
    pivot.attachChild(shell);
    parent.attachChild(pivot);

    // A local rotation on the pivot acts in its parent's axes, so the axis has to be carried back
    // there: conjugating by the parent's world rotation is what makes a turn of theta about this
    // axis come out as a turn of theta about axisInModelAxes once the chain above is composed.
    Vector3f axis =
        parent.getWorldRotation().inverse().mult(axisInModelAxes.normalize()).normalizeLocal();
    return Optional.of(new ShellSpin(pivot, axis));
  }

  /**
   * Sets the shell's spin.
   *
   * @param angleRad the angle, in radians, about the model-space axis this was isolated on
   */
  public void setAngle(float angleRad) {
    pivot.setLocalRotation(new Quaternion().fromAngleAxis(angleRad, axis));
  }
}
