package com.smousseur.orbitlab.simulation.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.forces.ForceModel;
import org.orekit.forces.drag.DragForce;
import org.orekit.forces.drag.DragSensitive;
import org.orekit.forces.drag.IsotropicDrag;
import org.orekit.models.earth.atmosphere.NRLMSISE00;

/**
 * The structural non-regression of PHY-1 / L1 (spec {@code docs/atmosphere/04-conception-L1.md}
 * §5.2): drag off mounts the force list of before the lot, to the type and to the order; drag on
 * mounts that same list plus one {@link DragForce}.
 *
 * <p><b>This is what makes the four gates a confirmation rather than the proof.</b> {@code drag ==
 * null} adds nothing at all — not a zero force, not an identity term — so "unchanged to the bit" is
 * a property of the type, the same demonstration {@code addPerturbers} already carries for an empty
 * perturber set.
 *
 * <p>The literal lists below were <b>measured</b>, not guessed: a {@code NumericalPropagator}
 * always carries a central {@link org.orekit.forces.gravity.NewtonianAttraction} of its own and
 * returns it last, whatever else was added.
 */
class DragForceMountingTest {

  private static final AerodynamicProperties AERO = new AerodynamicProperties(31.6, 0.4);

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  @DisplayName("Drag off mounts exactly the force list of before the lot")
  void dragOff_mountsTheListOfBeforeTheLot() {
    assertEquals(
        List.of("HolmesFeatherstoneAttractionModel", "NewtonianAttraction"),
        namesOf(optimization(FlightContext.earth())));
    assertEquals(List.of("NewtonianAttraction"), namesOf(test(FlightContext.earth())));
  }

  @Test
  @DisplayName("Drag on mounts the same list, plus one DragForce")
  void dragOn_mountsTheSameListPlusADragForce() {
    List<ForceModel> off = optimization(FlightContext.earth());
    List<ForceModel> on = optimization(earthWithDrag(AtmosphereModel.HARRIS_PRIESTER));

    assertEquals(off.size() + 1, on.size(), "exactly one force is added");
    assertEquals(
        List.of("HolmesFeatherstoneAttractionModel", "DragForce", "NewtonianAttraction"),
        namesOf(on));

    DragForce drag = (DragForce) on.get(1);
    IsotropicDrag spacecraft = (IsotropicDrag) drag.getSpacecraft();

    // The two numbers reach Orekit in their own roles. The drag itself cannot tell them apart —
    // it depends on the product Cd·S — which is exactly why the check has to be made here, on the
    // wiring, and why AerodynamicProperties orders its components after IsotropicDrag's.
    assertEquals(
        AERO.dragCoefficient(),
        spacecraft.getDragParametersDrivers().stream()
            .filter(driver -> DragSensitive.DRAG_COEFFICIENT.equals(driver.getName()))
            .findFirst()
            .orElseThrow()
            .getValue(),
        1e-9,
        "the coefficient reaches IsotropicDrag as a coefficient");
    assertEquals(
        AERO.crossSection(),
        (double) fieldOf(spacecraft, IsotropicDrag.class, "crossSection"),
        1e-9,
        "and the surface as a surface");
  }

  /**
   * The test factory has no caller in {@code src/main}, and mounts the drag all the same: letting
   * it ignore a {@link DragContext} would re-open the failure mode "the drag was asked for and was
   * not flown".
   */
  @Test
  @DisplayName("The test factory mounts the drag too")
  void theTestFactoryMountsTheDragToo() {
    assertEquals(
        List.of("DragForce", "NewtonianAttraction"),
        namesOf(test(earthWithDrag(AtmosphereModel.HARRIS_PRIESTER))));
  }

  /**
   * Both models actually build. PHY-1 proves nothing about the <em>values</em> a model returns, but
   * it must not leave PHY-2 to discover that one of them cannot be constructed at all: {@link
   * NRLMSISE00} is driven by the CSSI space-weather file, which the resolution reads from {@code
   * orekit-data.zip}.
   */
  @Test
  @DisplayName("Both catalogued models resolve against the Earth")
  void bothModelsResolve() {
    for (AtmosphereModel model :
        new AtmosphereModel[] {AtmosphereModel.HARRIS_PRIESTER, AtmosphereModel.NRLMSISE}) {
      assertEquals(
          List.of("HolmesFeatherstoneAttractionModel", "DragForce", "NewtonianAttraction"),
          namesOf(optimization(earthWithDrag(model))),
          model + " must mount a drag force");
    }
  }

  /**
   * The legacy the découpage §3.6 handed to MIS-5, closed: a lunar context <em>carrying</em> a drag
   * context mounts no {@link DragForce}, because the model resolves against the central body and
   * the Moon has no atmosphere. That is why the aerodynamic half can cross a boundary untouched.
   */
  @Test
  @DisplayName("A lunar context carrying a DragContext mounts no DragForce")
  void aLunarContextCarryingADragContext_mountsNothing() {
    FlightContext lunar =
        new FlightContext(
            GravitationalContext.moon(), new DragContext(AERO, AtmosphereModel.NRLMSISE));

    assertTrue(lunar.hasDrag(), "the context does carry one");
    assertEquals(
        List.of("NewtonianAttraction"),
        namesOf(optimization(lunar)),
        "the Moon has neither a harmonic field here nor an atmosphere");
  }

  /** The atmosphere is shared, like the gravity models — an invariant, not an optimisation. */
  @Test
  @DisplayName("The same model around the same body is the same atmosphere instance")
  void theAtmosphereIsSharedAcrossPropagators() {
    DragForce first =
        (DragForce) optimization(earthWithDrag(AtmosphereModel.HARRIS_PRIESTER)).get(1);
    DragForce second =
        (DragForce) optimization(earthWithDrag(AtmosphereModel.HARRIS_PRIESTER)).get(1);

    assertSame(atmosphereOf(first), atmosphereOf(second));
  }

  // ════════════════════════════════════════════════════════════════════════
  // Fixtures
  // ════════════════════════════════════════════════════════════════════════

  private static FlightContext earthWithDrag(AtmosphereModel model) {
    return FlightContext.earth().withDrag(new DragContext(AERO, model));
  }

  private static List<ForceModel> optimization(FlightContext context) {
    return OrekitService.get()
        .createOptimizationPropagator(context, OrekitService.COAST_MAX_STEP)
        .getAllForceModels();
  }

  private static List<ForceModel> test(FlightContext context) {
    return OrekitService.get()
        .createTestPropagator(context, OrekitService.COAST_MAX_STEP)
        .getAllForceModels();
  }

  private static List<String> namesOf(List<ForceModel> forces) {
    return forces.stream().map(f -> f.getClass().getSimpleName()).toList();
  }

  private static Object atmosphereOf(DragForce force) {
    return fieldOf(force, org.orekit.forces.drag.AbstractDragForceModel.class, "atmosphere");
  }

  /** Reads a private Orekit field — neither type exposes what this test has to compare. */
  private static Object fieldOf(Object target, Class<?> owner, String name) {
    try {
      java.lang.reflect.Field field = owner.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(target);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
