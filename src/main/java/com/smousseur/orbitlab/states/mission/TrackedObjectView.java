package com.smousseur.orbitlab.states.mission;

import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.scene.body.BodyRenderConfig;
import com.smousseur.orbitlab.engine.scene.body.LodView;
import com.smousseur.orbitlab.engine.scene.body.lod.Model3dView;
import com.smousseur.orbitlab.engine.scene.spacecraft.SpacecraftPresenter;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import java.util.concurrent.CompletableFuture;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * One drawn object of a mission: a spacecraft model ({@link LodView} + {@link SpacecraftPresenter})
 * and its trajectory ribbon ({@link MissionTrajectoryRenderer}), driven each frame from a
 * pre-computed {@link MissionEphemerisPoint} (PHY-5 / L1, spec {@code
 * docs/multi-objets/03-conception-L1.md} §2.4). It is the unit {@link MissionRenderer} fans out
 * over: the mission's primary object and each jettisoned debris are one of these.
 *
 * <p>It carries only what is common to every tracked object. The mission-level concerns of the
 * <em>primary</em> — the click handler that focuses it, and the eclipse occluder it pushes — stay
 * in {@link MissionRenderer}, which builds the primary with a click handler and pushes the occluder
 * onto its {@link #view()}. A debris is built with no click handler (it is not focusable in L1) and
 * pushes no occluder.
 */
final class TrackedObjectView {

  private final SpacecraftPresenter presenter;
  private final LodView view;
  private final MissionTrajectoryRenderer trajectoryRenderer;

  private TrackedObjectView(
      SpacecraftPresenter presenter, LodView view, MissionTrajectoryRenderer trajectoryRenderer) {
    this.presenter = presenter;
    this.view = view;
    this.trajectoryRenderer = trajectoryRenderer;
  }

  /**
   * Builds a tracked-object view and attaches it to the scene, exactly as the mission's spacecraft
   * was set up before L1: a {@link LodView} on the near bodies node, its 3D model loaded
   * asynchronously, and a trajectory renderer on the near orbits node.
   *
   * @param context the application context
   * @param config the render configuration (id, name, colour, drawn radius, mesh, scale context)
   * @param trajectoryId the discriminator for this object's ribbon geometry name — unique per
   *     object
   * @param trajectoryColor the ribbon colour
   * @param onClick the focus handler, or {@code null} for an object that is not clickable (debris)
   * @return the attached view
   */
  static TrackedObjectView create(
      ApplicationContext context,
      BodyRenderConfig config,
      String trajectoryId,
      ColorRGBA trajectoryColor,
      Runnable onClick) {
    Node guiNode = context.guiGraph().getPlanetBillboardsNode();
    LodView view = new LodView(guiNode, config, context.model3dAttacher(), onClick, null);
    SpacecraftPresenter presenter = new SpacecraftPresenter(config.id(), view);
    presenter.setVisible(true);

    Node anchor = (Node) view.spatial();
    anchor.attachChild(view.nearSpatial());
    context.sceneGraph().nearBodiesNode().attachChild(anchor);

    loadModelAsync(view.getModel3dView(), config.modelPath());

    MissionTrajectoryRenderer trajectoryRenderer =
        new MissionTrajectoryRenderer(trajectoryId, trajectoryColor);
    trajectoryRenderer.initialize(context.sceneGraph().nearOrbitsNode());

    return new TrackedObjectView(presenter, view, trajectoryRenderer);
  }

  /**
   * Loads a GLTF path into a model view off the render thread, twilight-shaded, then attaches it.
   */
  private static void loadModelAsync(Model3dView model3dView, String path) {
    CompletableFuture.supplyAsync(
            () -> model3dView.loadModel(path), AssetFactory.get().assetLoadingExecutor())
        .thenApply(spatial -> AssetFactory.get().applyLambert(spatial, 0.3f))
        .thenAccept(model3dView::onModelLoaded);
  }

  /**
   * Swaps this object's mesh for the one at {@code path} — the live silhouette change the primary
   * makes as it sheds pieces (PHY-5 / L3). The load is asynchronous, and the attach replaces the
   * previous model rather than overlapping it (see {@code OrbitLabApplication.attach}).
   *
   * @param path the GLTF asset path of the new silhouette
   */
  void swapMesh(String path) {
    loadModelAsync(view.getModel3dView(), path);
  }

  /** The LOD view, so {@link MissionRenderer} can push the primary's eclipse occluder onto it. */
  LodView view() {
    return view;
  }

  /**
   * Draws this object from one sample: pose, screen, and trajectory prefix, all in the sample's own
   * render context. The velocity stays in the arc's own frame (see {@code
   * MissionRenderer.updateFromEphemeris}); the position is converted once, here, and serves both
   * the model pose and the ribbon origin.
   *
   * @param point the interpolated sample to draw
   * @param trail this object's display polyline
   * @param upTo index of the last trail vertex flown at the current instant
   * @param cam the active camera
   * @param tpf frame time in seconds
   * @param view the current focus
   */
  void updateFromPoint(
      MissionEphemerisPoint point,
      TrajectoryPolyline trail,
      int upTo,
      Camera cam,
      float tpf,
      FocusView view) {
    SolarSystemBody renderBody = MissionRenderer.renderBodyOf(point, view);
    RenderContext ctx = RenderContext.planet(renderBody);
    Vector3D position = MissionRenderer.renderPositionOf(point, renderBody);
    presenter.updatePose(position, point.velocity(), tpf, ctx);
    this.view.updateScreen(cam, true);
    trajectoryRenderer.update(trail, upTo, position, ctx);
  }

  void setVisible(boolean visible) {
    view.setVisible(visible);
    trajectoryRenderer.setVisible(visible);
  }

  void cleanup() {
    view.spatial().removeFromParent();
    view.detach();
    trajectoryRenderer.cleanup();
  }
}
