package com.smousseur.orbitlab.engine.scene.calibration;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.control.BillboardControl;
import com.jme3.util.BufferUtils;
import com.simsilica.lemur.Label;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrame;
import com.smousseur.orbitlab.ui.UiLayers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * L2's instrument on screen: a labelled graticule riding on the body's own texture, and a marker on
 * the sub-solar point placed from the physics (see {@code
 * docs/orientation-planetes/01-decoupage.md}).
 *
 * <p><b>The two halves are deliberately fed from different places.</b> The grid hangs off the model
 * bucket, so it takes the whole render chain with it and its labels say what the application
 * believes; the marker is placed from the direction of the Sun, which is a position and owes
 * nothing to any rotation. Reading a feature against the grid gives the application's belief about
 * that feature's longitude, and the difference from a catalogued value is the correction to apply.
 * The marker is the check that the belief is at least self-consistent: if it lands somewhere the
 * grid disagrees with, the defect is in the chain and no longitude read off the grid means
 * anything.
 *
 * <p>This is what replaces comparing a screenshot to a reference image, which cannot settle
 * anything: the camera's azimuth is a free parameter, and turning it makes any longitude coincide
 * with any other.
 */
public final class GraticuleView {

  /**
   * The grid is lifted off the surface by three parts in a thousand. Below roughly this the line
   * and the globe land on the same depth values and the grid breaks into dashes; above it the
   * offset starts to show as a halo at the limb. Depth testing is kept, so the far side of the grid
   * is hidden by the globe, which is what makes the near hemisphere readable.
   */
  private static final float SURFACE_OFFSET = 1.003f;

  /** Labels and the marker are pushed further out so they clear the globe's silhouette. */
  private static final float LABEL_RADIUS = 1.06f;

  /** Half-width of the sub-solar cross, as a fraction of the globe's radius. */
  private static final float MARKER_HALF_SIZE = 0.06f;

  private static final ColorRGBA MARKER_COLOUR = new ColorRGBA(1f, 0.35f, 0.2f, 1f);
  private static final ColorRGBA LABEL_COLOUR = new ColorRGBA(0.6f, 0.95f, 1f, 1f);
  private static final ColorRGBA POLE_LABEL_COLOUR = new ColorRGBA(1f, 0.85f, 0.4f, 1f);

  private final Node modelBucket;
  private final Node guiNode;
  private final MeshFrame frame;
  private final float radius;

  private final Geometry grid;
  private final Node marker;
  private final List<Label> meridianLabels = new ArrayList<>();
  private final Label vZeroLabel;
  private final Label vOneLabel;

  /**
   * Builds the instrument and attaches it.
   *
   * @param modelBucket the node carrying the body's model, and therefore its render rotation
   * @param guiNode the GUI node the labels are drawn in
   * @param frame the frame the body's mesh was measured to carry
   * @param radiusUnits the globe's radius, in the units of {@code modelBucket}
   */
  public GraticuleView(Node modelBucket, Node guiNode, MeshFrame frame, float radiusUnits) {
    this.modelBucket = Objects.requireNonNull(modelBucket, "modelBucket");
    this.guiNode = Objects.requireNonNull(guiNode, "guiNode");
    this.frame = Objects.requireNonNull(frame, "frame");
    this.radius = radiusUnits;

    grid = new Geometry("Graticule", GraticuleMesh.build(frame, radiusUnits * SURFACE_OFFSET));
    grid.setMaterial(AssetFactory.get().material(ColorRGBA.White));
    grid.getMaterial().setBoolean("VertexColor", true);
    grid.getMaterial().getAdditionalRenderState().setDepthWrite(false);
    grid.setQueueBucket(RenderQueue.Bucket.Transparent);
    modelBucket.attachChild(grid);

    marker = buildMarker(radiusUnits);
    modelBucket.attachChild(marker);

    for (int meridian = 0; meridian < GraticuleMesh.MERIDIANS; meridian++) {
      meridianLabels.add(newLabel(LABEL_COLOUR));
    }
    vZeroLabel = newLabel(POLE_LABEL_COLOUR);
    vOneLabel = newLabel(POLE_LABEL_COLOUR);
  }

  /**
   * Places the marker and the labels for the current frame.
   *
   * @param camera the camera the globe is drawn with — the near viewport's, not the far one's
   * @param painting where the chain paints the map, which is what the labels report
   * @param vZeroIsNorth whether the chain paints the map's {@code v = 0} edge at the body's north
   *     pole. Passed in rather than assumed: a map stored the wrong way up is a defect the geometry
   *     cannot show, and naming the two ends is how it becomes visible
   * @param subSolarModelDirection unit vector toward the Sun, in the model's own axes
   */
  public void update(
      Camera camera,
      TexturePainting painting,
      boolean vZeroIsNorth,
      Vector3f subSolarModelDirection) {
    marker.setLocalTranslation(subSolarModelDirection.mult(radius * LABEL_RADIUS));

    for (int meridian = 0; meridian < meridianLabels.size(); meridian++) {
      double column = (double) meridian / GraticuleMesh.MERIDIANS;
      place(
          meridianLabels.get(meridian),
          camera,
          TexturePainting.directionOf(frame, column, 0.5),
          String.format(Locale.ROOT, "%.0f", painting.longitudeOfColumn(column)));
    }
    place(
        vZeroLabel,
        camera,
        TexturePainting.directionOf(frame, 0.0, 0.02),
        vZeroIsNorth ? "N" : "S");
    place(
        vOneLabel, camera, TexturePainting.directionOf(frame, 0.0, 0.98), vZeroIsNorth ? "S" : "N");
  }

  /** Takes the instrument off the scene, grid, marker and labels together. */
  public void detach() {
    grid.removeFromParent();
    marker.removeFromParent();
    meridianLabels.forEach(Spatial::removeFromParent);
    vZeroLabel.removeFromParent();
    vOneLabel.removeFromParent();
  }

  /**
   * Projects one anchor, given in the model's own axes, onto the screen.
   *
   * <p>Hidden when it falls on the far side of the globe. That is a hemisphere test against the
   * camera, not a depth test: the labels live in the GUI bucket, which knows nothing of the scene's
   * depth, so without it every meridian would show through the planet and the near ones could not
   * be told from the far ones.
   */
  private void place(Label label, Camera camera, Vector3f modelDirection, String text) {
    Vector3f world = modelBucket.localToWorld(modelDirection.mult(radius * LABEL_RADIUS), null);
    Vector3f centre = modelBucket.getWorldTranslation();
    if (world.subtract(centre).dot(camera.getLocation().subtract(centre)) < 0f) {
      label.setCullHint(Spatial.CullHint.Always);
      return;
    }
    Vector3f screen = camera.getScreenCoordinates(world);
    if (screen.z < 0f || screen.z > 1f) {
      label.setCullHint(Spatial.CullHint.Always);
      return;
    }
    label.setCullHint(Spatial.CullHint.Inherit);
    label.setText(text);
    Vector3f size = label.getPreferredSize();
    label.setLocalTranslation(screen.x - size.x * 0.5f, screen.y + size.y * 0.5f, UiLayers.HUD);
  }

  private Label newLabel(ColorRGBA colour) {
    Label label = new Label("");
    label.setColor(colour);
    // No font size set on purpose: a size the bundled bitmap font does not carry fails silently.
    label.setCullHint(Spatial.CullHint.Always);
    guiNode.attachChild(label);
    return label;
  }

  /**
   * A cross rather than a dot: it marks a point without hiding the very texture the point is there
   * to identify. Billboarded, so it keeps its size and shape wherever the camera is.
   */
  private static Node buildMarker(float radiusUnits) {
    float half = radiusUnits * MARKER_HALF_SIZE;
    Mesh mesh = new Mesh();
    mesh.setMode(Mesh.Mode.Lines);
    mesh.setBuffer(
        VertexBuffer.Type.Position,
        3,
        BufferUtils.createFloatBuffer(
            new Vector3f(-half, -half, 0f),
            new Vector3f(half, half, 0f),
            new Vector3f(-half, half, 0f),
            new Vector3f(half, -half, 0f)));
    mesh.updateBound();

    Geometry cross = new Geometry("SubSolarMarker", mesh);
    cross.setMaterial(AssetFactory.get().alphaMaterial(MARKER_COLOUR));
    cross.setQueueBucket(RenderQueue.Bucket.Transparent);

    Node pivot = new Node("SubSolarMarker-pivot");
    pivot.attachChild(cross);
    pivot.addControl(new BillboardControl());
    return pivot;
  }
}
