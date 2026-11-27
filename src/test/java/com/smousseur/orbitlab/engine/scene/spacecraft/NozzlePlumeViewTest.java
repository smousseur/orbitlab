package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.NozzlePlumeConfig;
import com.smousseur.orbitlab.engine.PlumeLook;
import com.smousseur.orbitlab.engine.PlumeShape;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Where a launcher's plumes sit on its drawn body, which fire, how they are shaped, and when. */
class NozzlePlumeViewTest {

  private static final String FH = LauncherAssets.modelPath(Launchers.FALCON_HEAVY.id());
  private static final NozzlePlumeConfig CONFIG = NozzlePlumeConfig.defaults();

  /** A body whose +Y, along which every launcher is authored, is turned onto -X. */
  private static final Quaternion QUARTER_TURN_ABOUT_Z =
      new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Z);

  /** The Falcon Heavy's 70 m stack, at a kilometre to the unit. */
  private static final float STACK_UNITS = 0.07f;

  private static final PlumeDrive BOOSTERS =
      new PlumeDrive(StageRole.BOOSTER, 1f, 12f, 0f, 1.0, 0.81f);

  private Node anchor3d;
  private Node body;
  private NozzlePlumeView view;

  @BeforeAll
  static void initAssetFactory() {
    try {
      AssetFactory.init(new DesktopAssetManager(true));
    } catch (OrbitlabException alreadyInitialized) {
      // Another test class got there first: its asset manager serves just as well.
    }
  }

  @BeforeEach
  void buildLauncher() {
    anchor3d = new Node("anchor3d");
    body = new Node("bucket");
    body.setLocalTranslation(1f, 2f, 3f);
    body.setLocalRotation(QUARTER_TURN_ABOUT_Z);
    anchor3d.attachChild(body);
    view = new NozzlePlumeView(anchor3d, CONFIG);
  }

  /** The core's exit, 0.0013 stack unit off the axis, scaled to the stack, turned and seated. */
  @Test
  void theExitIsDrawnWhereTheBodyPutsIt() {
    LauncherNozzles.Cluster core = LauncherNozzles.clustersOf(FH).get(0);

    Vector3f exit =
        NozzlePlumeView.exitPoint(
            new Vector3f(1f, 2f, 3f), QUARTER_TURN_ABOUT_Z, STACK_UNITS, core);

    Vector3f expected =
        QUARTER_TURN_ABOUT_Z
            .mult(new Vector3f(core.exitX(), core.exitY(), core.exitZ()).multLocal(STACK_UNITS))
            .addLocal(1f, 2f, 3f);
    assertVectorEquals(expected, exit);
  }

  @Test
  void thePivotTakesTheBodysPoseAndTheModelsScale() {
    view.setMesh(FH, STACK_UNITS);

    view.update(BOOSTERS, body, true, new Vector3f(100f, 0f, 0f));

    Spatial pivot = pivot();
    assertVectorEquals(body.getLocalTranslation(), pivot.getLocalTranslation());
    assertEquals(body.getLocalRotation(), pivot.getLocalRotation());
    assertVectorEquals(new Vector3f(STACK_UNITS, STACK_UNITS, STACK_UNITS), pivot.getLocalScale());
  }

  @Test
  void aBoosterBurnLightsEveryClusterOfTheFullStack() {
    view.setMesh(FH, STACK_UNITS);

    view.update(BOOSTERS, body, true, new Vector3f(100f, 0f, 0f));

    assertEquals(Spatial.CullHint.Inherit, pivot().getLocalCullHint());
    List<Geometry> jets = jets();
    assertEquals(3, jets.size());
    assertEquals(0.81f, intensity(jets.get(0)), 1e-6f);
    assertEquals(1f, intensity(jets.get(1)), 0f);
    assertEquals(1f, intensity(jets.get(2)), 0f);
    assertEquals(RenderQueue.Bucket.Transparent, jets.get(0).getQueueBucket());
  }

  @Test
  void aPlumeStaysOutUntilItsMeshIsKnown() {
    view.update(BOOSTERS, body, true, new Vector3f(100f, 0f, 0f));

    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
  }

  @Test
  void aPlumeStaysOutWhileItsBodyIsNotDrawn() {
    view.setMesh(FH, STACK_UNITS);

    view.update(BOOSTERS, body, false, new Vector3f(100f, 0f, 0f));

    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
  }

  @Test
  void betweenBurnsThePlumeIsOut() {
    view.setMesh(FH, STACK_UNITS);

    view.update(PlumeDrive.OFF, body, true, new Vector3f(100f, 0f, 0f));

    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
  }

  @Test
  void puttingThePlumeOutHidesIt() {
    view.setMesh(FH, STACK_UNITS);
    view.update(BOOSTERS, body, true, new Vector3f(100f, 0f, 0f));

    view.putOut();

    assertEquals(Spatial.CullHint.Always, pivot().getLocalCullHint());
  }

  @Test
  void theJetTakesItsSeaLevelShapeInThickAirAndItsVacuumShapeAboveIt() {
    view.setMesh(FH, STACK_UNITS);
    float radius = LauncherNozzles.clustersOf(FH).get(0).radius();
    PlumeShape seaLevel = CONFIG.look(PlumeLook.MERLIN).seaLevel();
    PlumeShape vacuum = CONFIG.look(PlumeLook.MERLIN).vacuum();

    view.update(BOOSTERS, body, true, new Vector3f(100f, 0f, 0f));
    assertEquals(seaLevel.lengthRadii() * radius, param(jets().get(0), "Length"), 1e-6f);

    view.update(
        new PlumeDrive(StageRole.BOOSTER, 1f, 12f, 1f, 1.0, 0.81f),
        body,
        true,
        new Vector3f(100f, 0f, 0f));
    assertEquals(vacuum.lengthRadii() * radius, param(jets().get(0), "Length"), 1e-6f);
    assertEquals(
        FastMath.tan(vacuum.halfAngleDeg() * FastMath.DEG_TO_RAD),
        param(jets().get(0), "TanAlpha"),
        1e-6f);
  }

  @Test
  void theFlameTimeAndTurbulenceFollowTheClock() {
    view.setMesh(FH, STACK_UNITS);

    view.update(
        new PlumeDrive(StageRole.BOOSTER, 1f, 12f, 0f, 20.0, 0.81f),
        body,
        true,
        new Vector3f(100f, 0f, 0f));

    assertEquals(12f, param(jets().get(0), "FlowSeconds"), 0f);
    assertEquals(ReentryFlowClock.turbulence(20.0), param(jets().get(0), "Turbulence"), 0f);
  }

  /** From inside a jet its near wall is behind the camera: only the far wall can be drawn. */
  @Test
  void aCameraInsideAJetDrawsItsFarWall() {
    view.setMesh(FH, STACK_UNITS);
    LauncherNozzles.Cluster core = LauncherNozzles.clustersOf(FH).get(0);
    Vector3f exit =
        NozzlePlumeView.exitPoint(
            body.getLocalTranslation(), body.getLocalRotation(), STACK_UNITS, core);
    Vector3f downTheJet = body.getLocalRotation().mult(new Vector3f(0f, -0.01f, 0f));

    view.update(BOOSTERS, body, true, exit.add(downTheJet));
    assertEquals(RenderState.FaceCullMode.Front, cullOf(jets().get(0)));

    view.update(BOOSTERS, body, true, new Vector3f(100f, 0f, 0f));
    assertEquals(RenderState.FaceCullMode.Back, cullOf(jets().get(0)));
  }

  private Spatial pivot() {
    return anchor3d.getChild(NozzlePlumeView.PIVOT_NAME);
  }

  private List<Geometry> jets() {
    return ((Node) pivot()).getChildren().stream().map(Geometry.class::cast).toList();
  }

  private static float intensity(Geometry jet) {
    return param(jet, "Intensity");
  }

  private static float param(Geometry jet, String name) {
    return (Float) jet.getMaterial().getParamValue(name);
  }

  private static RenderState.FaceCullMode cullOf(Geometry jet) {
    Material material = jet.getMaterial();
    return material.getAdditionalRenderState().getFaceCullMode();
  }

  private static void assertVectorEquals(Vector3f expected, Vector3f actual) {
    assertEquals(expected.x, actual.x, 1e-6f, "x");
    assertEquals(expected.y, actual.y, 1e-6f, "y");
    assertEquals(expected.z, actual.z, 1e-6f, "z");
  }
}
