package com.smousseur.orbitlab.simulation.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.forces.ForceModel;
import org.orekit.forces.gravity.HolmesFeatherstoneAttractionModel;
import org.orekit.forces.gravity.NewtonianAttraction;
import org.orekit.frames.Frame;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * PHY-4 / L4 §7.6 — the lunar gravitational context, and the silent defect it closes.
 *
 * <p>Before this lot, {@code createOptimizationPropagator} mounted the <b>Earth's</b> 8×8 field
 * expressed in ITRF for any central body, because {@code orekit-data.zip} carries exactly one
 * potential file and it is terrestrial. A lunar propagation ran with it and produced plausible
 * numbers — the only defect of L4 able to yield a wrong trajectory that looks right (spec {@code
 * docs/multi-corps/06-conception-L4.md} §1.2-D).
 */
class LunarContextTest {

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
  }

  @Test
  @DisplayName("A lunar propagator mounts no Earth harmonic field, only the central term")
  void lunarPropagatorHasNoTerrestrialField() {
    NumericalPropagator propagator =
        OrekitService.get()
            .createOptimizationPropagator(FlightContext.moon(), OrekitService.COAST_MAX_STEP);

    for (ForceModel force : propagator.getAllForceModels()) {
      assertFalse(
          force instanceof HolmesFeatherstoneAttractionModel,
          "a lunar propagator must not carry a harmonic field: orekit-data has none for the Moon,"
              + " and asking for one hands back the Earth's, expressed in ITRF");
    }

    assertEquals(
        1,
        propagator.getAllForceModels().size(),
        "point-mass gravity and nothing else for an unperturbed lunar context");
    assertTrue(
        propagator.getAllForceModels().get(0) instanceof NewtonianAttraction,
        "the central term is still whole: setMu mounts it when no attraction model is present");
  }

  @Test
  @DisplayName("The Earth keeps its 8x8 field, and the very same shared instance")
  void earthPropagatorIsUnchanged() {
    NumericalPropagator first =
        OrekitService.get()
            .createOptimizationPropagator(FlightContext.earth(), OrekitService.COAST_MAX_STEP);
    NumericalPropagator second =
        OrekitService.get()
            .createOptimizationPropagator(FlightContext.earth(), OrekitService.COAST_MAX_STEP);

    assertEquals(2, first.getAllForceModels().size(), "harmonic field plus central term");
    assertTrue(first.getAllForceModels().get(0) instanceof HolmesFeatherstoneAttractionModel);
    // The shared instance is what makes the L1 gate's 0.0 tolerance achievable (spec L1 §3.3).
    assertSame(first.getAllForceModels().get(0), second.getAllForceModels().get(0));
  }

  @Test
  @DisplayName("The lunar context is what §2.1 declares")
  void lunarContextComponents() {
    GravitationalContext moon = GravitationalContext.moon();

    assertEquals(SolarSystemBody.MOON, moon.body());
    assertEquals(CelestialBodyFactory.getMoon().getGM(), moon.mu());
    assertEquals(1_737_400.0, moon.equatorialRadius());
    assertEquals(0.0, moon.shape().getFlattening(), "a sphere, deliberately");
    assertTrue(moon.perturbers().isEmpty(), "unperturbed, like earth(); the declarant adds them");
    assertTrue(moon.inertialFrame().isPseudoInertial(), "must be usable as a propagation frame");
  }

  @Test
  @DisplayName("The selenocentric frame has ICRF axes, not the lunar pole")
  void selenocentricFrameIsIcrfParallel() {
    Frame gcrf = OrekitService.get().gcrf();
    Frame moonIcrf = OrekitService.get().bodyCentredIcrfFrame(SolarSystemBody.MOON);
    AbsoluteDate date = new AbsoluteDate(2026, 3, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());

    assertEquals(
        0.0,
        gcrf.getTransformTo(moonIcrf, date).getRotation().getAngle(),
        "a pure translation: no rotation at all, which is what makes the arc conversion exact");

    // Orekit's own lunar frame is the control: 22.08 degrees away, and drifting.
    double orekitAngle =
        gcrf.getTransformTo(CelestialBodyFactory.getMoon().getInertiallyOrientedFrame(), date)
            .getRotation()
            .getAngle();
    assertTrue(
        Math.toDegrees(orekitAngle) > 20.0,
        "control: Orekit's Moon/inertial is the IAU pole frame, measured 22.08 deg from GCRF");

    // The origin really is the Moon centre.
    Vector3D moonInGcrf = CelestialBodyFactory.getMoon().getPosition(date, gcrf);
    Vector3D originOffset = gcrf.getTransformTo(moonIcrf, date).getTranslation().add(moonInGcrf);
    assertEquals(0.0, originOffset.getNorm(), "the translation is exactly minus the Moon position");
  }

  @Test
  @DisplayName("The Earth resolves to the GCRF instance itself, not a copy")
  void earthResolvesToGcrfInstance() {
    // Reference equality, not equals: the leg runner skips the conversion when the frames are the
    // same instance, which is what keeps the L1 gate bit-identical (spec L4 §3.5).
    assertSame(
        OrekitService.get().gcrf(),
        OrekitService.get().bodyCentredIcrfFrame(SolarSystemBody.EARTH));
    assertSame(OrekitService.get().gcrf(), GravitationalContext.earth().inertialFrame());
  }

  @Test
  @DisplayName("The selenocentric frame is cached: one instance per body")
  void frameIsCached() {
    Frame first = OrekitService.get().bodyCentredIcrfFrame(SolarSystemBody.MOON);
    Frame second = OrekitService.get().bodyCentredIcrfFrame(SolarSystemBody.MOON);
    assertNotNull(first);
    assertSame(first, second);
  }
}
