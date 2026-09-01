package com.smousseur.orbitlab.engine.scene;

import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrame;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrameProbe;
import com.smousseur.orbitlab.engine.scene.mesh.PlanetMeshCalibration;
import java.util.Optional;

/**
 * Compares a body's asset, as actually loaded, against what {@link PlanetMeshCorrection} has
 * committed for it (see {@code docs/orientation-planetes/01-decoupage.md}, L1).
 *
 * <p>This is what makes an asset swap <em>detected</em> rather than silently absorbed. Nine of the
 * eleven models are provisional and will be replaced; a correction computed from a frame the asset
 * no longer carries would draw the body turned, with nothing to say so.
 */
public final class MeshGuard {

  /** Beyond this angle the mesh is considered to have moved rather than merely rounded. */
  private static final float MAX_FRAME_DEVIATION_DEG = 0.5f;

  private MeshGuard() {}

  /**
   * Checks a loaded asset against its committed calibration.
   *
   * @param body the body being loaded
   * @param measured the frame measured on the model as loaded
   * @param textureWidth width of the base colour texture bound to the globe
   * @param textureHeight its height
   * @return what diverged, or empty when the asset still matches — and also when the body has no
   *     committed calibration at all, there being nothing to compare against
   */
  public static Optional<MeshDivergence> check(
      SolarSystemBody body, MeshFrame measured, int textureWidth, int textureHeight) {
    PlanetMeshCalibration committed = PlanetMeshCorrection.calibrationFor(body).orElse(null);
    if (committed == null) {
      return Optional.empty();
    }

    float deviation =
        Math.max(
            angleBetweenDeg(committed.measured().pole(), measured.pole()),
            angleBetweenDeg(committed.measured().primeMeridian(), measured.primeMeridian()));
    boolean textureChanged =
        textureWidth != committed.textureWidth() || textureHeight != committed.textureHeight();

    if (deviation <= MAX_FRAME_DEVIATION_DEG && !textureChanged) {
      return Optional.empty();
    }
    return Optional.of(new MeshDivergence(body, deviation, textureChanged));
  }

  /**
   * Measures a model as loaded and checks it against its committed calibration.
   *
   * <p><b>Which geometry is the globe.</b> A model can hold several: Saturn has its ring, Venus a
   * separate atmosphere shell. The calibration describes the globe, and the globe is taken to be
   * the geometry that best behaves as a lat/long sphere — lowest {@code equirectangularResidualDeg}
   * — which picks correctly on every asset in the repo (Saturn's ring measures 10.08° against its
   * sphere's 0.00°, Venus's surface 0.01° against its atmosphere's 0.02°) and is what the
   * calibration means in the first place.
   *
   * <p>Safe to call from the asset-loading thread: it only reads, and the model is not yet
   * attached.
   *
   * @param body the body being loaded
   * @param model the loaded model
   * @return what diverged, or empty when the asset still matches
   */
  public static Optional<MeshDivergence> verify(SolarSystemBody body, Spatial model) {
    model.updateGeometricState();
    Globe globe = new Globe();
    model.depthFirstTraversal(
        new SceneGraphVisitorAdapter() {
          @Override
          public void visit(Geometry geometry) {
            MeshFrameProbe.probe(geometry.getMesh())
                .ifPresent(frame -> globe.offer(geometry, frame));
          }
        });
    if (globe.frame == null) {
      return Optional.empty();
    }
    MeshFrame worldFrame = globe.worldFrame();
    return check(body, worldFrame, globe.textureWidth(), globe.textureHeight());
  }

  /** The best globe candidate seen so far during a traversal. */
  private static final class Globe {
    private Geometry geometry;
    private MeshFrame frame;

    void offer(Geometry candidate, MeshFrame candidateFrame) {
      if (frame == null
          || candidateFrame.equirectangularResidualDeg() < frame.equirectangularResidualDeg()) {
        geometry = candidate;
        frame = candidateFrame;
      }
    }

    MeshFrame worldFrame() {
      return new MeshFrame(
          geometry.getWorldRotation().mult(frame.pole()),
          geometry.getWorldRotation().mult(frame.primeMeridian()),
          frame.equirectangularResidualDeg(),
          frame.azimuthDegreesPerU());
    }

    int textureWidth() {
      Texture texture = diffuse();
      return texture == null || texture.getImage() == null ? 0 : texture.getImage().getWidth();
    }

    int textureHeight() {
      Texture texture = diffuse();
      return texture == null || texture.getImage() == null ? 0 : texture.getImage().getHeight();
    }

    private Texture diffuse() {
      Material material = geometry.getMaterial();
      return material == null ? null : AssetFactory.extractDiffuseTexture(material);
    }
  }

  private static float angleBetweenDeg(Vector3f a, Vector3f b) {
    float cosine = FastMath.clamp(a.normalize().dot(b.normalize()), -1f, 1f);
    return FastMath.acos(cosine) * FastMath.RAD_TO_DEG;
  }
}
