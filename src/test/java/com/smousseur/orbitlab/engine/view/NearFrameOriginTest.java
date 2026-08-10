package com.smousseur.orbitlab.engine.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.body.BodyView;
import com.smousseur.orbitlab.engine.scene.spacecraft.SpacecraftPresenter;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The near-view origin must land <em>exactly</em> on the focused spacecraft, because that is the
 * position everything downstream measures against — the LOD distance in {@code
 * LodView.updateScreen} first of all (spec {@code
 * specs/graphics-effects/spacecraft-view-artefacts.md} §3).
 *
 * <p>Two independent producers write that origin every frame: {@code SpacecraftPresenter} places
 * the anchor at {@code +p}, and {@code FloatingOriginAppState} translates the near frame by {@code
 * −p}. These tests pin the two properties that make the sum vanish:
 *
 * <ol>
 *   <li>Both go through {@link JmeVectorAdapter#toJmeBodyRelativePosition}, so the two {@code
 *       float} triples are bit-for-bit opposite and cancel exactly — not to within a tolerance. At
 *       LEO magnitudes one {@code ulp} is already half a metre, so "almost" would not do.
 *   <li>The offset has to be written <em>before</em> the world position is read back. The second
 *       test measures what an out-of-order frame costs; the production guard against it is the
 *       attach order in {@code OrbitLabApplication}, which is not exercisable headlessly.
 * </ol>
 *
 * <p>No JME application here: {@link Node} is pure math, and the view is a stub that mirrors what
 * {@code LodView.setPositionWorld} does to the anchor.
 */
class NearFrameOriginTest {

  /** A LEO position, ~6778 km from the geocentre — where {@code float} spacing is ~0.49 m. */
  private static final Vector3D LEO_GCRF = new Vector3D(4_512_758.3, -3_981_204.7, 2_874_611.9);

  private final RenderContext ctx = RenderContext.planet(SolarSystemBody.EARTH);

  private Node nearFrame;
  private Node anchor;
  private SpacecraftPresenter presenter;

  @BeforeEach
  void setUp() {
    // nearRoot → nearFrame → nearBodiesNode → anchor, as assembled in SceneGraph and
    // MissionRenderer.
    nearFrame = new Node("nearFrame");
    Node nearBodiesNode = new Node("nearBodiesNode");
    anchor = new Node("Anchor-mission");
    new Node("nearRoot").attachChild(nearFrame);
    nearFrame.attachChild(nearBodiesNode);
    nearBodiesNode.attachChild(anchor);

    presenter = new SpacecraftPresenter("test-sc", new AnchorBodyView(anchor));
  }

  /** What {@code FloatingOriginAppState.nearFrameOffset} computes, once the ephemeris is read. */
  private Vector3f offsetFor(Vector3D positionGcrf) {
    return JmeVectorAdapter.toJmeBodyRelativePosition(positionGcrf, ctx).negateLocal();
  }

  @Test
  void offsetWrittenFirst_theSpacecraftSitsExactlyOnTheNearOrigin() {
    nearFrame.setLocalTranslation(offsetFor(LEO_GCRF)); // FloatingOriginAppState
    presenter.updatePose(LEO_GCRF, new Vector3D(0, 7_500, 0), 0.016f, ctx); // then the orchestrator

    Vector3f world = anchor.getWorldTranslation();
    assertEquals(0f, world.x, "exact cancellation, no tolerance");
    assertEquals(0f, world.y, "exact cancellation, no tolerance");
    assertEquals(0f, world.z, "exact cancellation, no tolerance");
  }

  @Test
  void offsetWrittenLast_theSpacecraftMeasuresAWholeFrameOfTravelAway() {
    // The defect this ordering exists to prevent: the near frame still carries the offset computed
    // for the previous frame while the anchor already holds the current position. At ×300 the clock
    // advances ~5 s per frame, so the spacecraft measures ~38 km off an origin it is sitting on,
    // its
    // projected radius collapses under the LOD threshold, and the 3D model drops to its 2D icon.
    Vector3D previousFrame = LEO_GCRF;
    Vector3D currentFrame = LEO_GCRF.add(new Vector3D(0, 0, 38_000.0)); // ~5 s of LEO travel

    nearFrame.setLocalTranslation(offsetFor(previousFrame)); // stale: written last frame
    presenter.updatePose(currentFrame, new Vector3D(0, 7_500, 0), 0.016f, ctx);

    float measuredKm = anchor.getWorldTranslation().length();
    assertEquals(38f, measuredKm, 0.1f, "the spurious distance is the frame's travel");
    assertTrue(measuredKm > 16f, "beyond 16 km the LOD drops the 3D model for its icon");
  }

  /** Mirrors {@code LodView}: the position is applied to the anchor node, nothing else. */
  private record AnchorBodyView(Node anchor) implements BodyView {

    @Override
    public Spatial spatial() {
      return anchor;
    }

    @Override
    public Spatial nearSpatial() {
      return anchor;
    }

    @Override
    public void setPositionWorld(Vector3f position) {
      anchor.setLocalTranslation(position);
    }

    @Override
    public void setRotationWorld(Quaternion rotation) {}

    @Override
    public void updateScreen(Camera cam, boolean allow3d) {}

    @Override
    public void setVisible(boolean visible) {}

    @Override
    public void detach() {}
  }
}
