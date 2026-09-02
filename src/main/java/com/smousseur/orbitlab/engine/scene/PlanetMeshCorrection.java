package com.smousseur.orbitlab.engine.scene;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.mesh.AtmosphereShell;
import com.smousseur.orbitlab.engine.scene.mesh.MeshConformance;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrame;
import com.smousseur.orbitlab.engine.scene.mesh.PlanetMeshCalibration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.orekit.time.AbsoluteDate;

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
 *
 * <h2>Where λ0 stands, body by body (L3)</h2>
 *
 * <p>λ0 is a fact about an <em>image</em>: which body-fixed longitude its left-hand column carries.
 * No inspection of a file can establish it — two maps with identical pixels dimensions and
 * identical geometry can disagree by any angle — so it is settled by looking, with the instrument
 * L2 provides ({@code MeshCalibrationAppState}, the <b>G</b> key).
 *
 * <ul>
 *   <li><b>Settled.</b> The Earth and the Moon. They are declared correct, and the chain paints
 *       their column 0 at {@link #CONVENTIONAL_COLUMN_ZERO_LONGITUDE_DEG} — so that <em>is</em>
 *       their maps' origin, measured rather than assumed. The Moon is the stronger of the two: in
 *       synchronous rotation, a near side that faces the Earth cannot coexist with a wrong
 *       meridian.
 *   <li><b>Conventional, and awaiting a look.</b> Every other body. They are given the same origin,
 *       which is the standard for planetary maps, and it is a starting point rather than a result.
 *   <li><b>Out of scope.</b> Uranus, whose globe is not a lat/long unwrap at all, and which
 *       therefore has no calibration here.
 * </ul>
 *
 * <p>Where a body has a defined anchor, that is what the look should be checked against, since it
 * is exact by definition rather than by observation: Airy-0 for Mars, Hun Kal at 20° W for Mercury,
 * the central peak of Ariadne for Venus's ground, the mean sub-Earth point for the Moon, the
 * sub-Charon meridian for Pluto. The giants and the Sun have none — their prime meridian is a radio
 * convention with no visible feature on it — so their λ0 will stay a documented house value.
 */
public final class PlanetMeshCorrection {

  /**
   * Body-fixed longitude the chain paints the texture's column {@code u = 0} at when the correction
   * is the identity, and therefore the value of λ0 that asks for no turn at all.
   *
   * <p><b>It is 180°, and that is not an arbitrary zero point.</b> It is the standard planetary map
   * origin — an equirectangular map centred on the prime meridian starts its left-hand column at
   * 180° west — so an asset drawn on a standard map needs no longitude term whatsoever. That is
   * <em>why</em> the Earth and the Moon come out right under the identity; before L2 measured it,
   * the two facts looked like a coincidence.
   *
   * <p><b>And the turn runs backwards</b>, hence the subtraction in {@link #correctionFor}: {@link
   * MeshConformance#REFERENCE_POLE} is the direction of the map's {@code v = 0} edge, which this
   * chain paints at the body's <em>south</em> pole (these textures are stored south-row-first —
   * measured on {@code earth}'s own map, whose band at {@code v = 0.2} is 96 % ocean and can only
   * be 54° south). Turning about a southward axis moves longitude the other way.
   *
   * <p>Both statements are pinned by {@code TexturePaintingTest}, which replays the whole chain
   * against Orekit's own body frame rather than trusting either of them in prose.
   */
  public static final float CONVENTIONAL_COLUMN_ZERO_LONGITUDE_DEG = 180f;

  /** Rate of a body's own frame, in degrees per day, as Orekit's IAU model turns it. */
  private static final double JUPITER_SYSTEM_III_DEG_PER_DAY = 870.5360000;

  private static final double SATURN_SYSTEM_III_DEG_PER_DAY = 810.7939024;

  /**
   * Rate of the layer the texture actually depicts. Jupiter's belts and its Great Red Spot are
   * catalogued in System II; Saturn's equatorial deck turns in System I, one turn in 10 h 14 min.
   * Both are published rates, not fits.
   */
  private static final double JUPITER_SYSTEM_II_DEG_PER_DAY = 870.2700000;

  /**
   * Longitude Jupiter's asset carries on its texture column {@code u = 0} at J2000 — the first λ0
   * on this table established by measurement rather than left at {@link
   * #CONVENTIONAL_COLUMN_ZERO_LONGITUDE_DEG}.
   *
   * <p><b>How it was obtained.</b> Two captures of the same instant, 2026-09-02 21:45 UTC: one from
   * NASA's Eyes on the Solar System, one from this application. In each, the disc's limb was fitted
   * (an ellipse, since Eyes renders the real oblateness), the terminator was detected line by line
   * and the solar direction recovered as the plane through those points, and the Great Red Spot's
   * angular distance from the sub-solar point was read off. That distance is the one quantity in
   * the comparison that depends on neither camera: it is the spot's local solar time. Eyes gives
   * <b>28.3°</b>, this application gave <b>70.3°</b>, so the spot was carried 42.0° too far from
   * local noon — which at its latitude of 19.8° south is 48.4° of longitude, and that is what has
   * been added to the house value of 180°.
   *
   * <p>The 70.3° is corroborated three ways: the arithmetic on the calibration HUD's own readout
   * ({@code λ0 + 360·u} against the spot at column 0.365 of the base colour map), the same
   * pixel-fitting pipeline applied to this application's capture, and the spot's position read
   * against the L2 graticule's labelled meridians, which puts it at 119° where the arithmetic
   * predicts 119.8°.
   *
   * <p><b>Uncertainty is about ±5°</b>, dominated by locating the spot's centre — in the map, and
   * on a rendered disc where it sits near the limb and the projection is compressed. It is not
   * worth quoting more precisely than that until a second reading at a distant date separates this
   * offset from a possible error in {@link #JUPITER_SYSTEM_II_DEG_PER_DAY}: one epoch cannot tell
   * the two apart, and at −0.266°/day a year of baseline is 97° of lever arm.
   */
  private static final float JUPITER_LAMBDA0_DEG = 228.4f;

  private static final double SATURN_SYSTEM_I_DEG_PER_DAY = 360.0 * 86400.0 / 36840.0;

  /**
   * Venus's cloud tops lap the planet retrograde in about four Earth days, against the 243 days its
   * ground takes — the super-rotation, and the largest disagreement between a texture and its body
   * frame anywhere in the solar system.
   */
  private static final double VENUS_CLOUD_TOP_DEG_PER_DAY = -360.0 / 4.2;

  private static final double VENUS_SYSTEM_III_DEG_PER_DAY = -1.4813688;

  /**
   * Shells that turn at their own rate inside a body's model. Venus is the only one: what its globe
   * geometry carries is the radar map of the ground, and the cloud deck over it is a separate mesh
   * the exporter named {@code atmosphere}.
   */
  private static final Map<SolarSystemBody, AtmosphereShell> ATMOSPHERE_SHELLS =
      new EnumMap<>(
          Map.of(
              SolarSystemBody.VENUS,
              new AtmosphereShell(
                  "atmosphere",
                  CONVENTIONAL_COLUMN_ZERO_LONGITUDE_DEG,
                  VENUS_CLOUD_TOP_DEG_PER_DAY - VENUS_SYSTEM_III_DEG_PER_DAY)));

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
              // Its map is the equatorial cloud deck, which runs 25 minutes a turn ahead of the
              // radio period Orekit spins the body at (L4).
              Map.entry(
                  SolarSystemBody.SATURN,
                  calibration(
                      referenceFrame(),
                      SATURN_SYSTEM_I_DEG_PER_DAY - SATURN_SYSTEM_III_DEG_PER_DAY,
                      4096,
                      2048)),
              // Pole in the reference family, texture column 0 a quarter turn away. This is the
              // Great Red Spot symptom that opened the chantier. Its λ0 is measured, not assumed —
              // see JUPITER_LAMBDA0_DEG.
              Map.entry(
                  SolarSystemBody.JUPITER,
                  calibration(
                      frame(0f, 0f, 1f, 0f, -1f, 0f, 0.00f, -360.0f),
                      JUPITER_LAMBDA0_DEG,
                      JUPITER_SYSTEM_II_DEG_PER_DAY - JUPITER_SYSTEM_III_DEG_PER_DAY,
                      4096,
                      2048)),
              // Pole on -Y instead of +Z: the whole axis is wrong, not just the longitude. The
              // frame is the ground globe's, the lower of the model's two residuals; the cloud
              // shell over it measures the same frame and turns at its own rate, see
              // ATMOSPHERE_SHELLS.
              Map.entry(
                  SolarSystemBody.VENUS,
                  calibration(frame(0f, -1f, 0f, -1f, 0f, 0f, 0.01f, -360.0f), 8192, 4096)),
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
                      1024)),
              Map.entry(
                  SolarSystemBody.URANUS,
                  calibration(
                      frame(-0.250f, -0.793f, -0.555f, -0.942f, 0.067f, 0.329f, 0.00f, -360.0f),
                      2048,
                      1024))));

  private PlanetMeshCorrection() {}

  /** The frame the reference assets carry, and therefore the target of every correction. */
  private static MeshFrame referenceFrame() {
    return frame(0f, 0f, 1f, -1f, 0f, 0f, 0.00f, -360.0f);
  }

  private static PlanetMeshCalibration calibration(
      MeshFrame measured, int textureWidth, int textureHeight) {
    return calibration(measured, 0.0, textureWidth, textureHeight);
  }

  private static PlanetMeshCalibration calibration(
      MeshFrame measured, double driftDegPerDay, int textureWidth, int textureHeight) {
    return calibration(
        measured,
        CONVENTIONAL_COLUMN_ZERO_LONGITUDE_DEG,
        driftDegPerDay,
        textureWidth,
        textureHeight);
  }

  /**
   * The overload for an asset whose column 0 has actually been measured, rather than assumed to sit
   * at the standard map origin. Taking this one is the declaration that a body's λ0 is a reading;
   * the shorter overloads say it is still the house value.
   */
  private static PlanetMeshCalibration calibration(
      MeshFrame measured,
      float lambda0Deg,
      double driftDegPerDay,
      int textureWidth,
      int textureHeight) {
    return new PlanetMeshCalibration(
        measured, lambda0Deg, driftDegPerDay, textureWidth, textureHeight);
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
   * Returns the corrective rotation for the given body's 3D model at a given instant.
   *
   * <p><b>Why it takes a date.</b> For a solid surface the correction is a constant, and the date
   * is ignored. For a cloud deck it cannot be: the map depicts a layer that slips within the frame
   * Orekit turns the body in, so a constant is only right on one day — Jupiter's belts move a
   * degree a fortnight and come full circle in under four years.
   *
   * @param body the solar system body
   * @param date the instant being rendered
   * @return the rotation to apply to the raw mesh, identity when the asset needs no correction
   */
  public static Quaternion correctionFor(SolarSystemBody body, AbsoluteDate date) {
    PlanetMeshCalibration calibration = CALIBRATIONS.get(body);
    if (calibration == null) {
      return new Quaternion();
    }
    return correctionFor(calibration, calibration.lambda0DegAt(daysSinceJ2000(calibration, date)));
  }

  /**
   * Same rotation, with λ0 supplied rather than read from the table — the seam the L2 instrument
   * turns to try a longitude out without editing the table and restarting.
   *
   * @param calibration the body's committed calibration
   * @param lambda0Deg the longitude to place the texture's column {@code u = 0} at
   * @return the rotation to apply to the raw mesh
   */
  public static Quaternion correctionFor(PlanetMeshCalibration calibration, double lambda0Deg) {
    // λ0 turns about the reference pole, so it is applied after the alignment, not before: a
    // longitude offset only means anything once the axis is where it belongs. The turn is measured
    // from the conventional origin and runs backwards, both for the reason given on
    // CONVENTIONAL_COLUMN_ZERO_LONGITUDE_DEG.
    // Brought back into [-180, 180] before it meets a float. A drifting body's λ0 grows without
    // bound — Saturn's deck is some 320 000 degrees past its epoch by 2026, where a float's step is
    // already 0.03 deg — so casting the raw difference quantises the body's orientation, coarsening
    // as the simulation date advances. The turn is modulo a full circle anyway.
    double turnDeg = Math.IEEEremainder(CONVENTIONAL_COLUMN_ZERO_LONGITUDE_DEG - lambda0Deg, 360.0);
    return new Quaternion()
        .fromAngleAxis((float) turnDeg * FastMath.DEG_TO_RAD, MeshConformance.REFERENCE_POLE)
        .mult(MeshConformance.alignment(calibration.measured()));
  }

  /**
   * Returns the independently turning shell of the given body's model, if it has one.
   *
   * @param body the solar system body
   * @return its shell, or empty for the ten bodies whose model is a single globe
   */
  public static Optional<AtmosphereShell> atmosphereShellFor(SolarSystemBody body) {
    return Optional.ofNullable(ATMOSPHERE_SHELLS.get(body));
  }

  /**
   * The angle by which a body's shell has to be turned inside the model, on top of the rotation the
   * renderer applies to the model as a whole.
   *
   * <p>It is the difference between the two layers' longitudes, and it is a difference precisely
   * because the model already carries the globe's own correction: turning the shell by the surplus
   * is the same as having given it a correction of its own, and it needs no knowledge of the frame
   * conversions to be right.
   *
   * @param body the solar system body
   * @param date the instant being rendered
   * @return the angle in radians about the body's measured pole, zero when there is no shell
   */
  public static float atmosphereShellSpinRad(SolarSystemBody body, AbsoluteDate date) {
    AtmosphereShell shell = ATMOSPHERE_SHELLS.get(body);
    PlanetMeshCalibration calibration = CALIBRATIONS.get(body);
    if (shell == null || calibration == null) {
      return 0f;
    }
    double days = date.durationFrom(AbsoluteDate.J2000_EPOCH) / 86400.0;
    double surplusDeg =
        calibration.lambda0DegAt(days) - (shell.lambda0Deg() + shell.driftDegPerDay() * days);
    return (float) Math.IEEEremainder(surplusDeg, 360.0) * FastMath.DEG_TO_RAD;
  }

  /**
   * Elapsed days since J2000, or zero for a body that does not drift.
   *
   * <p>The short-circuit is not an optimisation. It keeps every body whose map shows a solid
   * surface — which is all but three — clear of a subtraction of two dates thousands of turns
   * apart, and clear of needing a date at all.
   */
  private static double daysSinceJ2000(PlanetMeshCalibration calibration, AbsoluteDate date) {
    if (!calibration.hasVisibleLayerDrift()) {
      return 0.0;
    }
    return date.durationFrom(AbsoluteDate.J2000_EPOCH) / 86400.0;
  }
}
