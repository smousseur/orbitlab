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
   * Updates the spacecraft's position in the view. The position is provided in GCRF meters
   * (geocentric, Earth at origin). Since GCRF is already geocentric, no target subtraction is
   * needed — only scaling to render units and ICRF-to-JME axis conversion are applied.
   *
   * @param positionGcrfMeters the spacecraft position in GCRF frame, in meters
   * @param ctx the render context defining the scale (typically Planet with 1 unit = 1 km)
   */
  public void updatePose(Vector3D positionGcrfMeters, RenderContext ctx) {
    // GCRF positions are already geocentric (Earth = origin), same axis orientation as ICRF.
    // We only need to scale (meters -> render units) and convert axes (ICRF -> JME Y-up).
    Vector3D scaled = RenderTransform.scaleMetersToUnits(positionGcrfMeters, ctx);
    Vector3D jme = ctx.axisConvention().icrfToJme(scaled);
    Vector3f p = JmeVectorAdapter.toVector3f(jme);
    view.setPositionWorld(p);
  }
}
