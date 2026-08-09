package com.smousseur.orbitlab.engine.scene.body;

import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.control.BillboardControl;
import com.jme3.scene.shape.Quad;
import com.smousseur.orbitlab.engine.AssetFactory;
import java.util.Objects;

/**
 * Renders a star's corona as a camera-facing quad carrying a procedural radial glow.
 *
 * <h2>Why geometry rather than a screen-space bloom</h2>
 *
 * <p>The halo of a star is a property of the star, so expressing it in the star's own coordinates
 * makes it correct at every zoom for free. A post-processed bloom has to be told: its blur is a
 * fixed number of screen pixels, so the radius has to be driven from the projected radius, and even
 * then it bottoms out. {@code BloomFilter} blurs at a fraction of the screen resolution, so no halo
 * narrower than a texel of that buffer can be represented — and a magnified single texel is a
 * square tent. Between roughly ten and forty pixels of projected radius the halo asked for is finer
 * than that floor, and what gets drawn is the magnification footprint rather than the profile: a
 * square around a small disc. Below that band the LOD swaps the model for a 2D icon and the
 * question goes away; above it the blur has room. The band in between is not a tuning failure, it
 * is the resolution floor, and the only parameter that would move it — the down-sampling factor —
 * cannot be animated, since changing it reallocates the filter's framebuffers.
 *
 * <p>A quad has no such floor. It is sized in world units from the stellar radius, so it shrinks
 * exactly as the star does, and its profile is evaluated per fragment rather than sampled, so it is
 * round at any size. It also disappears with the model at the LOD switch, which is what should
 * happen. The screen-space bloom is kept alongside it for the one thing geometry cannot do — bleed
 * <em>inwards</em> over the limb — but it no longer carries the halo's reach and can therefore run
 * narrow enough never to meet its own floor.
 *
 * <h2>Construction</h2>
 *
 * <p>The mesh is a {@link Quad} of {@link #QUAD_RADIUS_RATIO} stellar radii, offset so the star's
 * centre is the origin its {@link BillboardControl} turns about, and the material is told where the
 * limb falls as the reciprocal of that same ratio — one measurement, written on both sides. It is
 * attached to the model bucket rather than the anchor above it so that the LOD culling that hides
 * the model hides the glow with it; the billboard resolves the bucket's rotation on its own.
 */
public final class CoronaView {

  /**
   * Size of the quad, in stellar radii, measured from the star's centre. This is where the glow is
   * fully spent — past it the mesh would be transparent, so it is pure overdraw.
   */
  private static final float QUAD_RADIUS_RATIO = 1.5f;

  private final Node pivot;

  /**
   * Builds a corona and attaches it to the given node.
   *
   * @param parent the node the glow should follow, normally the body's model bucket
   * @param starRadiusUnits the star's radius in the units of the viewport that draws it
   * @param color glow colour; its alpha is the overall strength
   * @param falloff exponent of the radial decay past the limb
   */
  public CoronaView(Node parent, double starRadiusUnits, ColorRGBA color, float falloff) {
    Objects.requireNonNull(parent, "parent");
    Objects.requireNonNull(color, "color");

    float halfWidth = (float) (starRadiusUnits * QUAD_RADIUS_RATIO);
    Geometry geometry = new Geometry("Corona", new Quad(halfWidth * 2f, halfWidth * 2f));
    // A Quad spans (0,0) to (size,size), so its origin is a corner. Shift it by half so the star's
    // centre is the point the billboard rotates about — otherwise the glow would swing around the
    // star as the camera moves instead of staying centred on it.
    geometry.setLocalTranslation(-halfWidth, -halfWidth, 0f);
    geometry.setMaterial(AssetFactory.get().createCorona(color, 1f / QUAD_RADIUS_RATIO, falloff));
    // Transparent, so it is drawn after the opaque bucket and can test against the star's depth.
    geometry.setQueueBucket(RenderQueue.Bucket.Transparent);

    pivot = new Node("Corona-pivot");
    pivot.attachChild(geometry);
    pivot.addControl(new BillboardControl());
    parent.attachChild(pivot);
  }
}
