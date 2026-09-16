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
 * continent (PHY-5 / L7, spec {@code docs/multi-objets/09-conception-L7.md}). Carrying the
 * calibration is exactly what puts a ground point where the texture draws it.
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
}
