package com.smousseur.orbitlab.app.view;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.SolarSystemBody;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;

class RenderTransformTest {

  @Test
  void axisConventionIsInvolutiveRoundTrip() {
    AxisConvention axes = AxisConvention.ICRF_TO_JME_Y_UP;

    Vector3D icrf = new Vector3D(1.25, -2.5, 10.0);
    Vector3D jme = axes.icrfToJme(icrf);
    Vector3D icrf2 = axes.jmeToIcrf(jme);

    assertEquals(icrf.getX(), icrf2.getX(), 0.0);
    assertEquals(icrf.getY(), icrf2.getY(), 0.0);
    assertEquals(icrf.getZ(), icrf2.getZ(), 0.0);
  }

  @Test
  void solarScaleMetersToUnitsIsCorrect() {
    RenderContext ctx = RenderContext.solar();

    Vector3D meters = new Vector3D(1e9, 2e9, -3e9);
    Vector3D units = RenderTransform.scaleMetersToUnits(meters, ctx);

    assertEquals(1.0, units.getX(), 1e-12);
    assertEquals(2.0, units.getY(), 1e-12);
    assertEquals(-3.0, units.getZ(), 1e-12);

    Vector3D back = RenderTransform.scaleUnitsToMeters(units, ctx);
    assertEquals(meters.getX(), back.getX(), 1e-9);
    assertEquals(meters.getY(), back.getY(), 1e-9);
    assertEquals(meters.getZ(), back.getZ(), 1e-9);
  }

  @Test
  void planetScaleMetersToUnitsIsCorrect() {
    RenderContext ctx = RenderContext.planet(SolarSystemBody.EARTH);

    Vector3D meters = new Vector3D(1000.0, -2000.0, 0.0);
    Vector3D units = RenderTransform.scaleMetersToUnits(meters, ctx);

    assertEquals(1.0, units.getX(), 1e-12);
    assertEquals(-2.0, units.getY(), 1e-12);
    assertEquals(0.0, units.getZ(), 1e-12);

    Vector3D back = RenderTransform.scaleUnitsToMeters(units, ctx);
    assertEquals(meters.getX(), back.getX(), 1e-9);
    assertEquals(meters.getY(), back.getY(), 1e-9);
    assertEquals(meters.getZ(), back.getZ(), 1e-9);
  }

  @Test
  void planetRelativeTransformUsesTargetPosition() {
    RenderContext ctx = RenderContext.planet(SolarSystemBody.EARTH);

    Vector3D target = new Vector3D(10_000.0, 0.0, 0.0); // meters
    Vector3D object = new Vector3D(11_500.0, 2_000.0, -500.0); // meters

    Vector3D unitsIcrfAxes = RenderTransform.toRenderUnitsIcrfAxes(object, target, ctx);
    // delta meters = (1500, 2000, -500) => units = (1.5, 2.0, -0.5)
    assertEquals(1.5, unitsIcrfAxes.getX(), 1e-12);
    assertEquals(2.0, unitsIcrfAxes.getY(), 1e-12);
    assertEquals(-0.5, unitsIcrfAxes.getZ(), 1e-12);
  }
}
