package com.smousseur.orbitlab.engine.scene.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

/**
 * The launcher assets against the launcher catalog, on the one thing the two both describe: the
 * relative width of a strap-on booster and the core it is bolted to. The catalog states it as a
 * cross-section in m², the mesh as a normalized diameter, and neither needs an external source for
 * the comparison — which is what makes open question 1 of {@code
 * docs/v2-preparation/00-preparation.md} §5 answerable from inside the repository at all.
 *
 * <p>The pieces loaded are the detached ones, not the assembled stack: they are what {@code PHY-5}
 * will fly side by side, and the moment the proportion stops being a table entry and becomes
 * something on screen.
 */
class LauncherMeshProportionTest {

  /**
   * The Falcon Heavy is right, so it is pinned as an agreement — its three cores are the same
   * hardware and the catalog gives all three the same 10.5 m². One per cent leaves room for the
   * raceway and stowed legs the bounding box picks up on the centre core and not on a side one.
   */
  private static final double AGREEMENT_TOLERANCE = 0.01;

  /**
   * The Ariane is wrong, so it is pinned as a <em>disagreement</em>, and tightly: replacing the
   * mesh must make this red rather than quietly green, because whoever replaces it also owes an
   * update to {@code DT-18} and to the launcher's javadoc.
   */
  private static final double GAP_TOLERANCE = 0.005;

  @Test
  void theFalconsSideBoosterIsAsWideAsItsCentreCore() {
    assertEquals(
        catalogDiameterRatio(Launchers.FALCON_HEAVY),
        meshDiameterRatio("heavy_falcon"),
        AGREEMENT_TOLERANCE,
        "the Falcon Heavy's mesh and catalog disagree about its booster");
  }

  /**
   * {@code DT-18}, measured. The mesh draws the P120C 0.863 of the core's width where the catalog's
   * own sections — 9.08 m² against 22.9 m², i.e. 3.40 m against 5.40 m — put it at 0.630. It is
   * oversized by 37 %, and in length too: 0.3624 of a 63 m stack is 22.8 m against the P120C's
   * 13.5. No stack height reconciles the two, since reaching 13.5 m would need a 37 m Ariane.
   */
  @Test
  void theArianesBoostersAreOversizedByTheAmountDt18Records() {
    assertEquals(0.630, catalogDiameterRatio(Launchers.ARIANE_64), GAP_TOLERANCE, "catalog");
    assertEquals(0.863, meshDiameterRatio("ariane_64"), GAP_TOLERANCE, "mesh");
  }

  /**
   * What lets a single number per launcher size every piece of its asset set: the stack is exactly
   * one unit tall and stands on the origin, so {@code Model3dView} scaling it by the launcher's
   * height puts each piece at its own fraction of that height. It is the convention {@code
   * LauncherAssets} asks a new mesh to satisfy, checked rather than trusted.
   */
  @Test
  void bothStacksAreNormalizedToOneUnitStandingOnTheOrigin() {
    for (String asset : new String[] {"heavy_falcon", "ariane_64"}) {
      Extent stack = extentOf(asset, "");
      assertEquals(1.0, stack.dy(), 1e-3, asset + " is not one unit tall");
      assertEquals(0.0, stack.minY(), 1e-3, asset + " does not stand on the origin");
    }
  }

  /** The diameter ratio the catalog's unit cross-sections imply, since a section goes as d². */
  private static double catalogDiameterRatio(LauncherModel launcher) {
    return Math.sqrt(
        unitSection(launcher, StageRole.BOOSTER) / unitSection(launcher, StageRole.CORE));
  }

  private static double unitSection(LauncherModel launcher, StageRole role) {
    return launcher.stages().stream()
        .filter(stage -> stage.capabilities().role() == role)
        .findFirst()
        .map(StageModel::unitAerodynamics)
        .orElseThrow(() -> new IllegalStateException(launcher.id() + " has no " + role))
        .crossSection();
  }

  private static double meshDiameterRatio(String asset) {
    return extentOf(asset, "-booster1").dx() / extentOf(asset, "-core").dx();
  }

  /**
   * The extent of an asset's actual vertices, in the model root's axes.
   *
   * <p><b>Not {@code getWorldBound()}</b>, which merges each geometry's own axis-aligned box after
   * rotating it and so reports a box enclosing a box. On the Ariane's booster that inflates the
   * width from 0.0798 to 0.0928 — enough to turn a 37 % error into an apparent agreement, which is
   * the one outcome this fixture must not produce.
   */
  private static Extent extentOf(String asset, String piece) {
    AssetManager assetManager = new DesktopAssetManager(true);
    Spatial model =
        assetManager.loadModel("models/vehicles/" + asset + "/" + asset + piece + ".gltf");
    model.updateGeometricState();

    float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
    float[] hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    model.depthFirstTraversal(
        new SceneGraphVisitorAdapter() {
          @Override
          public void visit(Geometry geometry) {
            FloatBuffer positions = geometry.getMesh().getFloatBuffer(VertexBuffer.Type.Position);
            if (positions == null) {
              return;
            }
            Vector3f point = new Vector3f();
            for (int i = 0; i < positions.limit(); i += 3) {
              point.set(positions.get(i), positions.get(i + 1), positions.get(i + 2));
              geometry.getWorldTransform().transformVector(point, point);
              float[] xyz = {point.x, point.y, point.z};
              for (int axis = 0; axis < 3; axis++) {
                lo[axis] = Math.min(lo[axis], xyz[axis]);
                hi[axis] = Math.max(hi[axis], xyz[axis]);
              }
            }
          }
        });
    return new Extent(hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2], lo[1]);
  }

  private record Extent(double dx, double dy, double dz, double minY) {}
}
