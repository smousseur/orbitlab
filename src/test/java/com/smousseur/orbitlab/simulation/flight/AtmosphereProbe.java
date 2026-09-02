package com.smousseur.orbitlab.simulation.flight;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import java.lang.reflect.Field;
import org.orekit.forces.ForceModel;
import org.orekit.forces.drag.AbstractDragForceModel;
import org.orekit.forces.drag.DragForce;
import org.orekit.models.earth.atmosphere.Atmosphere;

/**
 * Reaches the {@link Atmosphere} a propagator was actually built with — the one PHY-1 / L2 reads
 * its densities from.
 *
 * <p><b>Why the atmosphere is fetched and not rebuilt.</b> {@code OrekitService} resolves the pair
 * (model, central body) behind a private cache, so a test that instantiated its own {@code
 * HarrisPriester} would be checking its own arithmetic against Orekit's rather than checking what
 * the mission flies. Going through a factory-built propagator keeps the measured ρ on the
 * production path; the price is one reflective read, which neither {@link DragForce} nor {@link
 * AbstractDragForceModel} makes avoidable.
 */
final class AtmosphereProbe {

  /** Any positive pair: the drag force has to be mounted, its cross-section is not read here. */
  private static final AerodynamicProperties PROBE = new AerodynamicProperties(1.0, 2.2);

  private AtmosphereProbe() {}

  /**
   * The shared atmosphere {@code OrekitService} resolves for this model around the Earth.
   *
   * @param model the model to resolve; never {@code NONE}, which carries no atmosphere at all
   * @return the atmosphere the production propagators use
   */
  static Atmosphere of(AtmosphereModel model) {
    ForceModel drag =
        OrekitService.get()
            .createOptimizationPropagator(
                FlightContext.earth().withDrag(new DragContext(PROBE, model)),
                OrekitService.COAST_MAX_STEP)
            .getAllForceModels()
            .stream()
            .filter(DragForce.class::isInstance)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no DragForce mounted for " + model));
    return behind((DragForce) drag);
  }

  /**
   * The atmosphere a given mounted force computes against.
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
