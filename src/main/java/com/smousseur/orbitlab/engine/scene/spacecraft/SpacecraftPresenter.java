package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.engine.scene.body.BodyView;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * MVC presenter that drives a {@link BodyView} by applying a spacecraft's position from mission
 * propagation data. Unlike {@link
 * com.smousseur.orbitlab.engine.scene.planet.PlanetPresenter} which fetches positions from
 * ephemeris, this presenter receives positions directly from the mission's {@code SpacecraftState}.
 *
 * @param id unique identifier for this spacecraft
 * @param view the view that renders the spacecraft
 */
public record SpacecraftPresenter(String id, BodyView view) {

  public SpacecraftPresenter {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(view, "view");
  }

  /**
   * Sets the visibility of the spacecraft view.
   *
   * @param v {@code true} to make the spacecraft visible, {@code false} to hide it
   */
  public void setVisible(boolean v) {
    view.setVisible(v);
  }

  /**
   * Updates the spacecraft's position in the view. The position is provided directly in GCRF meters
   * and scaled/transformed to JME render units.
   *
   * @param positionGcrfMeters the spacecraft position in GCRF frame, in meters
   * @param ctx the render context defining scale and frame conversion
   */
  public void updatePose(Vector3D positionGcrfMeters, RenderContext ctx) {
    Vector3D jme = RenderTransform.toRenderUnitsJmeAxes(positionGcrfMeters, null, ctx);
    Vector3f p = JmeVectorAdapter.toVector3f(jme);
    view.setPositionWorld(p);
  }
}
