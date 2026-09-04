package com.smousseur.orbitlab.engine.scene.mesh;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Finds the part of a loaded model an asset-specific mechanism is about, by the name its exporter
 * wrote.
 *
 * <p>Two mechanisms need this and neither can hard-code a path: Venus's cloud deck, which turns at
 * its own rate ({@link com.smousseur.orbitlab.engine.scene.body.ShellSpin}), and the ring systems
 * of Saturn and Uranus, which receive their planet's shadow. The eleven assets come from different
 * hands and have wildly different node chains for the same result, so the only thing they can be
 * addressed by is a name — which is exactly why those names are committed data in {@code
 * PlanetMeshCorrection} and pinned by a fixture test.
 */
public final class ModelNodes {

  private ModelNodes() {}

  /**
   * The first node whose name starts with {@code namePrefix}, searching parents before children.
   *
   * <p>That order matters: an exporter names both a part's group node and the geometry under it
   * from the same source name, and the group is the one that carries the whole part.
   *
   * @param root the model to search
   * @param namePrefix prefix of the node's name, as the asset names it
   * @return the node, or empty when the model carries no such name
   */
  public static Optional<Spatial> firstNamed(Spatial root, String namePrefix) {
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(namePrefix, "namePrefix");
    if (root.getName() != null && root.getName().startsWith(namePrefix)) {
      return Optional.of(root);
    }
    if (!(root instanceof Node node)) {
      return Optional.empty();
    }
    for (Spatial child : node.getChildren()) {
      Optional<Spatial> found = firstNamed(child, namePrefix);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  /**
   * Every geometry under a spatial, itself included when it is one.
   *
   * <p><b>Geometries, never their materials.</b> A caller collecting these during the asset load
   * holds them across {@code AssetFactory.applyLambert}, which runs later in the same chain and
   * calls {@code setMaterial} on each geometry. Geometry identity survives that; a material
   * reference captured beforehand would be replaced without a word, and whatever was written
   * through it would land on an object nothing draws.
   *
   * @param root the subtree to collect
   * @return its geometries, in traversal order, empty when it holds none
   */
  public static List<Geometry> geometriesUnder(Spatial root) {
    Objects.requireNonNull(root, "root");
    List<Geometry> geometries = new ArrayList<>();
    root.depthFirstTraversal(
        new SceneGraphVisitorAdapter() {
          @Override
          public void visit(Geometry geometry) {
            geometries.add(geometry);
          }
        });
    return geometries;
  }
}
