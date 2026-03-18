package com.smousseur.orbitlab.engine.scene.planet;

import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.body.BodyView;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import com.smousseur.orbitlab.simulation.ephemeris.service.EphemerisService;
import com.smousseur.orbitlab.simulation.ephemeris.service.EphemerisServiceRegistry;
import java.util.Objects;

import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

/**
 * MVC presenter that drives a {@link BodyView} by computing and applying the planet's
 * position and rotation from ephemeris data at a given simulation time.
 *
 * @param body the solar system body this presenter manages
 * @param view the view that renders the planet
 */
public record PlanetPresenter(SolarSystemBody body, BodyView view) {

  public PlanetPresenter(SolarSystemBody body, BodyView view) {
    this.body = Objects.requireNonNull(body, "body");
    this.view = Objects.requireNonNull(view, "view");
  }

  /**
   * Sets the visibility of the planet view.
   *
   * @param v {@code true} to make the planet visible, {@code false} to hide it
   */
  public void setVisible(boolean v) {
    view.setVisible(v);
  }

  /**
   * Updates the planet's position and rotation in the view based on its heliocentric ICRF ephemeris
   * at the given simulation time.
   *
   * @param t the simulation time at which to compute the planet's pose
   */
  public void updatePose(AbsoluteDate t) {
    // Heliocentric: pos = planetICRF - sunICRF (interpolation via buffers)
    EphemerisService ephemerisService =
        EphemerisServiceRegistry.get()
            .orElseThrow(() -> new OrbitlabException("Cannot get EphemerisService"));
    ephemerisService
        .trySampleHelioIcrf(body, t)
        .ifPresent(
            posRotation -> {
              // Convertir en JME units/axes selon le contexte SOLAR
              Vector3D pos = posRotation.getKey();
              Vector3D jmeUnitsJmeAxes =
                  RenderTransform.toRenderUnitsJmeAxes(pos, null, RenderContext.solar());
              Vector3f p = JmeVectorAdapter.toVector3f(jmeUnitsJmeAxes);
              view.setPositionWorld(p);
              Rotation rotation = posRotation.getValue();
              view.setRotationWorld(RenderTransform.toRenderQuaternion(rotation));
            });
  }
}
