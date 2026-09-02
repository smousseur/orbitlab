package com.smousseur.orbitlab.engine.scene.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.scene.Geometry;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetMeshCorrection;
import com.smousseur.orbitlab.engine.scene.body.ShellSpin;
import com.smousseur.orbitlab.engine.scene.mesh.AtmosphereShell;
import com.smousseur.orbitlab.engine.scene.mesh.PlanetMeshCalibration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The L4 shell mechanism is wired to Venus's asset by a node name, and a node name is exactly the
 * kind of thing a re-export changes without telling anyone. This ties the committed string to the
 * file on disk: if the name goes, the cloud deck silently stops super-rotating and only this goes
 * red.
 */
class VenusAtmosphereShellFixtureTest {

  @Test
  void theVenusAssetCarriesTheShellNodeItsCalibrationNames() {
    AtmosphereShell shell =
        PlanetMeshCorrection.atmosphereShellFor(SolarSystemBody.VENUS).orElseThrow();
    PlanetMeshCalibration calibration =
        PlanetMeshCorrection.calibrationFor(SolarSystemBody.VENUS).orElseThrow();
    Spatial venus = loadVenus();

    Optional<ShellSpin> spin =
        ShellSpin.isolate(venus, shell.nodeNamePrefix(), calibration.measured().pole());

    assertTrue(spin.isPresent(), "no node named " + shell.nodeNamePrefix() + " in venus.gltf");
  }

  /**
   * Turning the shell must turn the shell and nothing else. A prefix that also caught the ground
   * globe would spin the whole planet at fifty-eight times its rate, which on a body whose surface
   * map is hidden under an opaque atmosphere would be invisible.
   */
  @Test
  void spinningTheShellLeavesTheGroundWhereItWas() {
    AtmosphereShell shell =
        PlanetMeshCorrection.atmosphereShellFor(SolarSystemBody.VENUS).orElseThrow();
    PlanetMeshCalibration calibration =
        PlanetMeshCorrection.calibrationFor(SolarSystemBody.VENUS).orElseThrow();
    Spatial venus = loadVenus();
    venus.updateGeometricState();
    Map<String, Quaternion> before = worldRotations(venus);

    ShellSpin spin =
        ShellSpin.isolate(venus, shell.nodeNamePrefix(), calibration.measured().pole())
            .orElseThrow();
    spin.setAngle(FastMath.HALF_PI);
    venus.updateGeometricState();
    Map<String, Quaternion> after = worldRotations(venus);

    int turned = 0;
    for (Map.Entry<String, Quaternion> entry : before.entrySet()) {
      float dot = Math.abs(entry.getValue().dot(after.get(entry.getKey())));
      boolean moved = dot < 0.999f;
      if (entry.getKey().startsWith(shell.nodeNamePrefix())) {
        assertTrue(moved, entry.getKey() + " should have turned");
        turned++;
      } else {
        assertTrue(!moved, entry.getKey() + " should not have turned");
      }
    }
    assertEquals(1, turned, "exactly the cloud shell should turn, of " + before.keySet());
  }

  private static Map<String, Quaternion> worldRotations(Spatial model) {
    Map<String, Quaternion> rotations = new LinkedHashMap<>();
    model.depthFirstTraversal(
        new SceneGraphVisitorAdapter() {
          @Override
          public void visit(Geometry geometry) {
            rotations.put(geometry.getName(), geometry.getWorldRotation().clone());
          }
        });
    return rotations;
  }

  private static Spatial loadVenus() {
    AssetManager assetManager = new DesktopAssetManager(true);
    return assetManager.loadModel("models/planets/venus/venus.gltf");
  }
}
