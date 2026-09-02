package com.smousseur.orbitlab.engine.scene.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Venus's model carries two shells that do not turn at the same rate (L4 of {@code
 * docs/orientation-planetes/01-decoupage.md}), so one of them has to be turned inside a model the
 * renderer turns as a whole.
 *
 * <p>What these tests are really about is the change of basis. The axis to turn about is known in
 * the model's own axes — it is what the probe measured — but the pivot is spliced deep inside a
 * node chain that has rotations of its own, so the angle has to be expressed in the axes of
 * whatever node happens to be its parent. Getting that wrong tilts the shell instead of spinning
 * it, which on a featureless cloud deck is invisible.
 */
class ShellSpinTest {

  private static final Vector3f MODEL_AXIS = new Vector3f(0f, 0f, 1f);

  @Test
  void spinsTheShellAboutTheModelAxisThroughAnyNodeChain() {
    Node model = modelWithShell();
    Geometry shell = (Geometry) ((Node) model.getChild("atmosphere")).getChild(0);
    model.updateGeometricState();
    Quaternion before = shell.getWorldRotation().clone();

    ShellSpin spin = ShellSpin.isolate(model, "atmosphere", MODEL_AXIS).orElseThrow();
    spin.setAngle(FastMath.QUARTER_PI);
    model.updateGeometricState();

    Quaternion expected =
        new Quaternion().fromAngleAxis(FastMath.QUARTER_PI, MODEL_AXIS).mult(before);
    assertSameRotation(expected, shell.getWorldRotation());
  }

  /**
   * Splicing the pivot in is not itself a change: until an angle is dialled the shell must sit
   * exactly where the asset put it. Otherwise the instrument would move the very thing it is meant
   * to measure.
   */
  @Test
  void insertingThePivotMovesNothing() {
    Node model = modelWithShell();
    model.updateGeometricState();
    Geometry shell = (Geometry) ((Node) model.getChild("atmosphere")).getChild(0);
    Quaternion before = shell.getWorldRotation().clone();

    ShellSpin.isolate(model, "atmosphere", MODEL_AXIS).orElseThrow();
    model.updateGeometricState();

    assertSameRotation(before, shell.getWorldRotation());
  }

  /** A model with no such shell must be left exactly as it is, not half-rebuilt. */
  @Test
  void leavesAModelWithoutTheShellUntouched() {
    Node model = modelWithShell();
    int nodesBefore = countNodes(model);

    Optional<ShellSpin> spin = ShellSpin.isolate(model, "ionosphere", MODEL_AXIS);

    assertTrue(spin.isEmpty());
    assertEquals(nodesBefore, countNodes(model));
  }

  private static int countNodes(Node node) {
    int count = 1;
    for (com.jme3.scene.Spatial child : node.getChildren()) {
      count += child instanceof Node childNode ? countNodes(childNode) : 1;
    }
    return count;
  }

  /**
   * A stand-in for {@code venus.gltf}: two sibling shells hanging off a chain of nodes that each
   * carry a rotation of their own, which is what makes the change of basis necessary.
   */
  private static Node modelWithShell() {
    Node root = new Node("Sketchfab_model");
    root.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
    Node inner = new Node("RootNode");
    inner.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
    root.attachChild(inner);

    Node atmosphere = new Node("atmosphere");
    atmosphere.setLocalRotation(new Quaternion().fromAngleAxis(0.3f, Vector3f.UNIT_Y));
    atmosphere.attachChild(new Geometry("atmosphere_Material_0", new Quad(1f, 1f)));
    inner.attachChild(atmosphere);

    Node venus = new Node("venus");
    venus.attachChild(new Geometry("venus_Material_0", new Quad(1f, 1f)));
    inner.attachChild(venus);
    return root;
  }

  private static void assertSameRotation(Quaternion expected, Quaternion actual) {
    float dot = Math.abs(expected.dot(actual));
    assertEquals(1.0, dot, 1e-4, "expected " + expected + " but was " + actual);
  }
}
