package com.smousseur.orbitlab.engine.scene.planet;

import com.jme3.math.Quaternion;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetMeshCorrection;
import com.smousseur.orbitlab.simulation.ephemeris.service.EphemerisServiceRegistry;
import java.util.Optional;
import org.orekit.time.AbsoluteDate;

/**
 * The rotation a planet's globe is <em>drawn</em> with at a given date: the physics body-frame
 * rotation composed with the mesh calibration ({@link PlanetMeshCorrection}) — the exact quaternion
 * {@link PlanetPresenter#updatePose} hands the view.
 *
 * <p>A single source, so everything that must line up with the drawn globe cannot drift: the globe
 * itself, the Earth rotating-frame node, and a debris ground track that has to land on the right
 * continent. Carrying the calibration is exactly what puts a ground point where the texture draws
 * it.
 */
public final class PlanetDrawnRotation {

  private PlanetDrawnRotation() {}

  /**
   * The drawn rotation of {@code body}'s globe at {@code t}.
   *
   * @param body the body
   * @param t the date
   * @return the drawn rotation, or empty while the ephemeris buffer has not reached {@code t}
   */
  public static Optional<Quaternion> at(SolarSystemBody body, AbsoluteDate t) {
    return EphemerisServiceRegistry.get()
        .flatMap(service -> service.trySampleHelioIcrf(body, t))
        .map(
            posRotation ->
                RenderTransform.toRenderQuaternion(
                    posRotation.getValue(), PlanetMeshCorrection.correctionFor(body, t)));
  }

  /**
   * The drawn rotation of {@code body}'s globe at {@code t}, however far {@code t} lies from the
   * ephemeris window — the rotation a landed object needs at its touchdown for as long as it lies
   * there, long after the window has slid past it. Where the window covers {@code t} it is exactly
   * {@link #at}.
   *
   * <p>{@link #at} stays the one to match the globe as drawn <em>now</em>: the globe is posed from
   * the window alone, so a rotation read beyond it could run ahead of the globe while the window is
   * rebuilt after a seek.
   *
   * @param body the body
   * @param t the date
   * @return the drawn rotation, or empty when the ephemeris cannot produce {@code t}
   */
  public static Optional<Quaternion> atAnyDate(SolarSystemBody body, AbsoluteDate t) {
    return EphemerisServiceRegistry.get()
        .flatMap(service -> service.trySampleIcrfOnGrid(body, t))
        .map(
            sample ->
                RenderTransform.toRenderQuaternion(
                    sample.rotationIcrf(), PlanetMeshCorrection.correctionFor(body, t)));
  }
}
