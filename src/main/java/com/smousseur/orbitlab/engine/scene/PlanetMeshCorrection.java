package com.smousseur.orbitlab.engine.scene;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.mesh.MeshConformance;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrame;
import com.smousseur.orbitlab.engine.scene.mesh.PlanetMeshCalibration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Provides the per-body corrective rotation applied to a planet's 3D mesh before it is oriented by
 * the physical body-fixed rotation (see {@code docs/bugs.md}, BUG-3, and the chantier it opened,
 * {@code docs/orientation-planetes/01-decoupage.md}).
 *
 * <p>The rendering chain used to apply a single global mesh correction for all eleven GLTF models
 * ({@code RenderTransform#toRenderQuaternion}), which is only correct if every asset shares exactly
 * the same axis convention and the same prime meridian. It does not: measured, the eleven split
 * into two families of pole axis that one global rotation cannot satisfy at once.
 *
 * <p><b>Nothing here is tuned by hand.</b> Each body declares what its asset was <em>measured</em>
 * to carry — pole, direction of texture column {@code u = 0}, residual, chirality, copied verbatim
 * from {@code ./gradlew meshProbe} — and the rotation is composed from it. Where the previous
 * design had a quaternion nobody could check, the committed values are now directions a reader can
 * read off the report.
 *
 * <p><b>The identity is the expected value, not a placeholder.</b> The Earth and the Moon carry the
 * reference frame and are declared frozen; Mars and Saturn measure identical to them. A body with a
 * non-identity correction is an asset that has not been brought onto the export convention yet
 * (§4.2 of the chantier), and each says so below.
 */
public final class PlanetMeshCorrection {

  /**
   * What each asset was measured to carry, on 2026-09-01, by {@code ./gradlew meshProbe}. Uranus is
   * absent on purpose: its globe is not an equirectangular unwrap at all (residual 49.4°), so no
   * frame can be recovered from it and none is invented — it stays out of scope until an asset that
   * can be measured replaces it.
   *
   * <p>The residual and chirality are carried along even though the rotation does not use them: a
   * reader has to be able to tell how much the two directions can be trusted (Mercury's mesh is
   * irregular enough to measure 0.87°, where every other sphere sits at 0.00°), and the startup
   * guard compares against them.
   */
  private static final Map<SolarSystemBody, PlanetMeshCalibration> CALIBRATIONS =
      new EnumMap<>(
          Map.ofEntries(
              // Reference assets, frozen: the convention is what these two carry.
              Map.entry(SolarSystemBody.EARTH, calibration(referenceFrame(), 8192, 4096)),
              Map.entry(SolarSystemBody.MOON, calibration(referenceFrame(), 8192, 4096)),
              // Measured identical to the reference; not observed on screen, see §2.6.
              Map.entry(SolarSystemBody.MARS, calibration(referenceFrame(), 8192, 4096)),
              Map.entry(SolarSystemBody.SATURN, calibration(referenceFrame(), 4096, 2048)),
              // Pole in the reference family, texture column 0 a quarter turn away. This is the
              // Great Red Spot symptom that opened the chantier.
              Map.entry(
                  SolarSystemBody.JUPITER,
                  calibration(frame(0f, 0f, 1f, 0f, -1f, 0f, 0.00f, -360.0f), 4096, 2048)),
              // Pole on -Y instead of +Z: the whole axis is wrong, not just the longitude.
              Map.entry(
                  SolarSystemBody.VENUS,
                  calibration(frame(0f, -1f, 0f, -1f, 0f, 0f, 0.02f, -360.0f), 8192, 4096)),
              Map.entry(
                  SolarSystemBody.PLUTO,
                  calibration(frame(0f, -1f, 0f, -1f, 0f, 0f, 0.31f, -360.0f), 4096, 2048)),
              Map.entry(
                  SolarSystemBody.SUN,
                  calibration(frame(0f, -1f, 0f, 0f, 0f, -1f, 0.00f, -360.0f), 1024, 512)),
              // Same family as the Sun, but its export is 4.5 deg off true on the meridian. Its
              // texture is square where the mesh is an exact lat/long unwrap: a 2:1 map resampled
              // to 1:1, so latitude resolution is spent for nothing.
              Map.entry(
                  SolarSystemBody.NEPTUNE,
                  calibration(
                      frame(0f, -1f, 0f, -0.078f, 0f, -0.997f, 0.00f, -360.0f), 2048, 2048)),
              // Baked-rotated mesh: neither its pole nor its meridian lands on an axis.
              Map.entry(
                  SolarSystemBody.MERCURY,
                  calibration(
                      frame(0.383f, -0.905f, 0.186f, -0.815f, -0.471f, -0.337f, 0.87f, -362.1f),
                      2048,
                      1024))));

  private PlanetMeshCorrection() {}

  /** The frame the reference assets carry, and therefore the target of every correction. */
  private static MeshFrame referenceFrame() {
    return frame(0f, 0f, 1f, -1f, 0f, 0f, 0.00f, -360.0f);
  }

  private static PlanetMeshCalibration calibration(
      MeshFrame measured, int textureWidth, int textureHeight) {
    return new PlanetMeshCalibration(measured, 0f, textureWidth, textureHeight);
  }

  private static MeshFrame frame(
      float poleX,
      float poleY,
      float poleZ,
      float meridianX,
      float meridianY,
      float meridianZ,
      float residualDeg,
      float azimuthDegreesPerU) {
    return new MeshFrame(
        new Vector3f(poleX, poleY, poleZ),
        new Vector3f(meridianX, meridianY, meridianZ),
        residualDeg,
        azimuthDegreesPerU);
  }

  /**
   * Returns what the given body's asset was measured to carry.
   *
   * @param body the solar system body
   * @return its calibration, or empty for a body whose asset carries nothing measurable
   */
  public static Optional<PlanetMeshCalibration> calibrationFor(SolarSystemBody body) {
    return Optional.ofNullable(CALIBRATIONS.get(body));
  }

  /**
   * Returns the corrective rotation for the given body's 3D model.
   *
   * @param body the solar system body
   * @return the rotation to apply to the raw mesh, identity when the asset needs no correction
   */
  public static Quaternion correctionFor(SolarSystemBody body) {
    PlanetMeshCalibration calibration = CALIBRATIONS.get(body);
    if (calibration == null) {
      return new Quaternion();
    }
    // λ0 turns about the reference pole, so it is applied after the alignment, not before: a
    // longitude offset only means anything once the axis is where it belongs.
    return new Quaternion()
        .fromAngleAxis(
            calibration.lambda0Deg() * FastMath.DEG_TO_RAD, MeshConformance.REFERENCE_POLE)
        .mult(MeshConformance.alignment(calibration.measured()));
  }
}
