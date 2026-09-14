package com.smousseur.orbitlab.simulation.flight;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import java.lang.reflect.Field;
import org.orekit.forces.drag.AbstractDragForceModel;
import org.orekit.forces.drag.DragForce;
import org.orekit.models.earth.atmosphere.Atmosphere;

/**
 * Reaches the {@link Atmosphere} a propagation flies against — the one PHY-1 / L2 reads its
 * densities from — without a test rebuilding its own {@code HarrisPriester} and checking its own
 * arithmetic against Orekit's rather than checking what the mission flies.
 */
final class AtmosphereProbe {

  private AtmosphereProbe() {}

  /**
   * The shared atmosphere {@code OrekitService} resolves for this model around the Earth — the very
   * instance every production propagator's drag force computes against.
   *
   * <p>Since PHY-3 this is a direct call to {@link OrekitService#atmosphere(AtmosphereModel,
   * GravitationalContext)}: it used to fabricate a propagator with a dummy drag force purely to
   * extract the atmosphere behind it, which the public seam makes needless.
   *
   * @param model the model to resolve; never {@code NONE}, which carries no atmosphere at all
   * @return the atmosphere the production propagators use
   */
  static Atmosphere of(AtmosphereModel model) {
    return OrekitService.get().atmosphere(model, GravitationalContext.earth());
  }

  /**
   * The atmosphere a given <em>mounted</em> force computes against — distinct from {@link
   * #of(AtmosphereModel)}, which fetches the shared instance by model: this one verifies the force
   * a propagator actually built, so it still has to read the private field the force holds.
   *
   * @param force the mounted drag force
   * @return its atmosphere
   */
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  static Atmosphere behind(DragForce force) {
    try {
      Field field = AbstractDragForceModel.class.getDeclaredField("atmosphere");
      field.setAccessible(true);
      return (Atmosphere) field.get(force);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
