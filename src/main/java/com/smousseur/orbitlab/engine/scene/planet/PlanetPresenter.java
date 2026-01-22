package com.smousseur.orbitlab.engine.scene.planet;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.app.view.AxisConvention;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import com.smousseur.orbitlab.simulation.ephemeris.service.EphemerisService;
import com.smousseur.orbitlab.simulation.ephemeris.service.EphemerisServiceRegistry;
import com.smousseur.orbitlab.simulation.source.PvSource;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

public record PlanetPresenter(SolarSystemBody body, PlanetView view) {

  public PlanetPresenter(SolarSystemBody body, PlanetView view) {
    this.body = Objects.requireNonNull(body, "body");
    this.view = Objects.requireNonNull(view, "view");
  }

  public void setColor(ColorRGBA color) {
    view.setColor(color);
  }

  public void setVisible(boolean v) {
    view.setVisible(v);
  }

  public void updatePose(AbsoluteDate t) {
    // Heliocentrique: pos = planetICRF - sunICRF (interpolation via buffers)
    EphemerisService ephemerisService =
        EphemerisServiceRegistry.get()
            .orElseThrow(() -> new OrbitlabException("Cannot get EphemerisService"));
    ephemerisService
        .tryPositionHelioIcrf(body, t)
        .ifPresent(
            pos -> {
              // Convertir en JME units/axes selon le contexte SOLAR
              Vector3D jmeUnitsJmeAxes =
                  RenderTransform.toRenderUnitsJmeAxes(pos, null, RenderContext.solar());
              Vector3f p = JmeVectorAdapter.toVector3f(jmeUnitsJmeAxes);
              view.setPositionWorld(p);
            });
  }
}
