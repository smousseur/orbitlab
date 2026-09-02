package com.smousseur.orbitlab.engine.scene.calibration;

import com.jme3.math.Quaternion;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrame;
import java.util.Locale;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * One reading of L2's instrument on one body at one instant: what the application paints, and what
 * the physics says is there (see {@code docs/orientation-planetes/01-decoupage.md}).
 *
 * <p>The two are computed from unrelated data. Where the map lands comes from the render chain
 * evaluated forward — measured frame, alignment, the longitude term, the drift, the conversions of
 * {@code RenderTransform}; where the Sun is comes from the body's <em>position</em>, which no
 * rotation enters. The check they make on each other is therefore real, and it is the check {@link
 * #chainOffsetDeg()} reports.
 *
 * <p>What it cannot do is tell whether the image is drawn where it claims. Only an eye can say the
 * Great Red Spot is where the grid says it is — which is what the grid and the sub-solar marker are
 * drawn for, and why L3 is a body-by-body pass rather than a computation.
 *
 * @param body the body read
 * @param lambda0Deg the longitude the texture's column 0 is being placed at, at this instant, drift
 *     included
 * @param painting where the chain paints the map, in body-fixed longitudes
 * @param subSolarLongitudeDeg body-fixed longitude of the sub-solar point, from the physics alone
 * @param subSolarColumn the texture column the chain is painting at that point, in {@code [0, 1)} —
 *     the column of the map an observer is looking at when they look at the centre of the lit disc
 */
public record CalibrationReading(
    SolarSystemBody body,
    double lambda0Deg,
    TexturePainting painting,
    double subSolarLongitudeDeg,
    double subSolarColumn) {

  public CalibrationReading {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(painting, "painting");
  }

  /**
   * Takes a reading.
   *
   * @param body the body being read
   * @param frame the frame its mesh was measured to carry
   * @param lambda0Deg the longitude column 0 is being placed at, at this instant
   * @param renderRotation the rotation the renderer is applying to the model, as produced by {@code
   *     RenderTransform#toRenderQuaternion}
   * @param rotationIcrf the body's ICRF-to-body-frame rotation at the same instant
   * @param sunDirectionIcrf unit vector from the body toward the Sun, in ICRF axes
   * @return the reading
   */
  public static CalibrationReading take(
      SolarSystemBody body,
      MeshFrame frame,
      double lambda0Deg,
      Quaternion renderRotation,
      Rotation rotationIcrf,
      Vector3D sunDirectionIcrf) {
    TexturePainting painting = TexturePainting.measure(frame, renderRotation, rotationIcrf);
    double subSolar = TexturePainting.bodyFixedLongitudeDeg(sunDirectionIcrf, rotationIcrf);
    return new CalibrationReading(
        body, lambda0Deg, painting, subSolar, painting.columnAtLongitude(subSolar));
  }

  /**
   * How far the chain paints column 0 from where the longitude term says it should be, in degrees.
   *
   * <p>Zero, always, when the composition is sound — the term <em>is</em> the longitude of column
   * 0, so this compares a value against itself through the whole render chain and Orekit's own
   * frame. It is the one number here that needs no eye, and the first to look at: a non-zero
   * reading means the defect is in the code, and nothing seen on the globe can be trusted until it
   * is zero.
   *
   * @return the offset, in {@code (-180, 180]}
   */
  public double chainOffsetDeg() {
    return TexturePainting.wrap(painting.columnZeroLongitudeDeg() - lambda0Deg);
  }

  /**
   * The reading as one line of ASCII, for the heads-up display and the log.
   *
   * <p>Deliberately no degree sign and no typographic minus: the HUD's bitmap font drops a glyph it
   * does not carry without a word of complaint.
   *
   * @return the line
   */
  public String format() {
    return String.format(
        Locale.ROOT,
        "%s  lambda0 %+.1f  sub-solar %+.1f (column %.3f)  chain offset %+.2f",
        body.displayName(),
        TexturePainting.wrap(lambda0Deg),
        subSolarLongitudeDeg,
        subSolarColumn,
        chainOffsetDeg());
  }
}
