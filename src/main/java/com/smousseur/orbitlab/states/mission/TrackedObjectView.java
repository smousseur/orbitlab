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
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
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

  /**
   * Whether this object shows its far-range icon on top of its close-range 3D mesh. Always {@code
   * true} for the primary; a debris sets it from the global "show debris" toggle, so a decluttered
   * debris is only its 3D mesh up close (PHY-5 / L7).
   */
  private boolean secondaryDisplay = true;

  /**
   * Whether this object draws its inertial trajectory ribbon. {@code true} for the primary (the
   * mission trajectory); {@code false} for a debris, whose trajectory is a ground track drawn
   * separately in the Earth rotating frame, or nothing at all when it is orbital (PHY-5 / L7 §D3).
   */
  private boolean inertialTrail = true;

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
   * @param parent the scene node to hang the object's anchor under — the near bodies node for the
   *     primary, the primary's own anchor for a debris so it can be placed relative to it (PHY-5,
   *     the GEO "tremble" fix)
   * @return the attached view
   */
  static TrackedObjectView create(
      ApplicationContext context,
      BodyRenderConfig config,
      String trajectoryId,
      ColorRGBA trajectoryColor,
      Runnable onClick,
      Node parent) {
    Node guiNode = context.guiGraph().getPlanetBillboardsNode();
    LodView view = new LodView(guiNode, config, context.model3dAttacher(), onClick, null);
    SpacecraftPresenter presenter = new SpacecraftPresenter(config.id(), view);
    presenter.setVisible(true);

    Node anchor = (Node) view.spatial();
    anchor.attachChild(view.nearSpatial());
    parent.attachChild(anchor);

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
   * Loads a mesh at a given real-world size, off the render thread, then attaches it — the payload
   * silhouette the primary shrinks to (PHY-5 / L5), whose size comes from the catalog rather than
   * from the launcher's radius.
   */
  private static void loadModelNormalizedAsync(
      Model3dView model3dView, String path, double sizeMeters) {
    CompletableFuture.supplyAsync(
            () -> model3dView.loadModelNormalized(path, sizeMeters),
            AssetFactory.get().assetLoadingExecutor())
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

  /**
   * Swaps this object's mesh for the one at {@code path}, drawn at {@code drawnSizeMeters} rather
   * than at the launcher's scale — the primary shrinking to its payload (PHY-5 / L5). The mesh is
   * normalized by its own bounding box, so a third-party asset with any intrinsic scale lands at
   * the right size.
   *
   * @param path the GLTF asset path of the payload mesh
   * @param drawnSizeMeters the size, in metres, the payload's largest dimension should span
   */
  void swapMesh(String path, double drawnSizeMeters) {
    loadModelNormalizedAsync(view.getModel3dView(), path, drawnSizeMeters);
  }

  /** The LOD view, so {@link MissionRenderer} can push the primary's eclipse occluder onto it. */
  LodView view() {
    return view;
  }

  /**
   * Draws this object from one sample: pose, screen, and trajectory prefix, all in the sample's own
   * render context. The velocity stays in the arc's own frame (see {@code
   * MissionRenderer.updateFromEphemeris}); the position is converted once, here, and serves both
   * the model pose and the ribbon tip.
   *
   * <p>The {@code seat} (PHY-5 / L6, spec {@code docs/multi-objets/08-conception-L6.md}) is
   * <em>not</em> added to the anchor's position — that is left on the propagated point, which is
   * what the floating origin cancels, so the anchor keeps full precision far from Earth. It is
   * applied instead as a small near-frame offset on the model itself ({@link
   * BodyView#setModelOffset}) and on the ribbon tip: each piece is authored base-at-origin, so
   * without a seat every piece and every shrunk silhouette would pile on the one propagated point.
   * Render-only — the sample's stored position is untouched, keeping the gated trajectory clean.
   *
   * @param point the interpolated sample to draw
   * @param seat the body-frame seat offset in metres (in the arc's frame), from {@link StackSeat};
   *     {@link Vector3D#ZERO} for an unseated object
   * @param referencePoint the object this one is drawn relative to — the primary, whose scene
   *     anchor a debris hangs under — or {@code null} for the primary itself, placed absolutely
   * @param trail this object's display polyline
   * @param upTo index of the last trail vertex flown at the current instant
   * @param cam the active camera
   * @param tpf frame time in seconds
   * @param view the current focus
   */
  void updateFromPoint(
      MissionEphemerisPoint point,
      Vector3D seat,
      MissionEphemerisPoint referencePoint,
      TrajectoryPolyline trail,
      int upTo,
      Camera cam,
      float tpf,
      FocusView view) {
    SolarSystemBody renderBody = MissionRenderer.renderBodyOf(point, view);
    RenderContext ctx = RenderContext.planet(renderBody);
    Vector3D position = MissionRenderer.renderPositionOf(point, renderBody);
    // The model is placed relative to a reference object — a debris relative to its primary, whose
    // scene anchor it now hangs under — so the two large GCRF coordinates cancel in double before
    // the float conversion. Otherwise, far from Earth (GEO), the float subtraction the scene graph
    // does between two independently-rounded coordinates jitters the model by metres each frame
    // (PHY-5, the debris "tremble"). The primary passes no reference: it is placed absolutely and
    // cancels the near-frame offset exactly, as before.
    Vector3D modelPosition =
        referencePoint == null
            ? position
            : position.subtract(MissionRenderer.renderPositionOf(referencePoint, renderBody));
    presenter.updatePose(modelPosition, point.velocity(), tpf, ctx);
    this.view.setModelOffset(JmeVectorAdapter.toJmeBodyRelativePosition(seat, ctx));
    this.view.setIconFallbackEnabled(secondaryDisplay);
    this.view.updateScreen(cam, true);
    if (inertialTrail) {
      trajectoryRenderer.setVisible(true);
      trajectoryRenderer.update(trail, upTo, position, seat, ctx);
    } else {
      trajectoryRenderer.setVisible(false);
    }
  }

  /**
   * Sets whether this object shows its far-range icon. The primary keeps the default {@code true};
   * a debris is driven from the global "show debris" toggle (PHY-5 / L7).
   *
   * @param on whether the far-range icon is shown
   */
  void setSecondaryDisplay(boolean on) {
    this.secondaryDisplay = on;
  }

  /**
   * Sets whether this object draws its inertial trajectory ribbon. The primary keeps the default
   * {@code true}; a debris sets it {@code false}, its trajectory being a ground track drawn
   * separately (PHY-5 / L7 §D3).
   *
   * @param on whether the inertial ribbon is drawn
   */
  void setInertialTrail(boolean on) {
    this.inertialTrail = on;
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
