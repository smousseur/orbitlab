package com.smousseur.orbitlab.simulation.mission.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.LofOffset;
import org.orekit.forces.gravity.HolmesFeatherstoneAttractionModel;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.forces.maneuvers.propulsion.BasicConstantThrustPropulsionModel;
import org.orekit.forces.maneuvers.trigger.DateBasedManeuverTriggers;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.LOFType;
import org.orekit.orbits.*;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.*;

public class OptimizationReplayTest {

  private final double mu = Constants.WGS84_EARTH_MU;
  private final double re = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private final double thrust = 2000.0;
  private final double isp = 300.0;
  private final double initialMass = 25000.0;

  @BeforeAll
  static void setUp() {
    OrekitService.get().initialize();
  }

  @Test
  public void testOptimizationAndRealTimeReplay() {

    AbsoluteDate initialDate = new AbsoluteDate(2025, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    Frame inertialFrame = FramesFactory.getEME2000();

    double a1 = re + 200_000.0;
    Orbit orbit1 =
        new CircularOrbit(
            a1, 0.0, 0.0, 0.0, 0.0, 0.0, PositionAngleType.MEAN, inertialFrame, initialDate, mu);
    SpacecraftState state0 = new SpacecraftState(orbit1).withMass(initialMass);

    double targetA = re + 400_000.0;

    // =====================================================================
    // 1. CALCUL ANALYTIQUE DU TRANSFERT HOHMANN
    // =====================================================================
    double r1 = a1;
    double r2 = targetA;
    double v1 = FastMath.sqrt(mu / r1);
    double vTransferPeri = FastMath.sqrt(mu * (2.0 / r1 - 2.0 / (r1 + r2)));
    double dv1 = vTransferPeri - v1;

    double vTransferApo = FastMath.sqrt(mu * (2.0 / r2 - 2.0 / (r1 + r2)));
    double v2 = FastMath.sqrt(mu / r2);
    double dv2 = v2 - vTransferApo;

    double aTransfer = (r1 + r2) / 2.0;
    double periodTransfer = 2.0 * FastMath.PI * FastMath.sqrt(FastMath.pow(aTransfer, 3) / mu);
    double durationTransfer = periodTransfer / 2.0;

    // Durée des burns (poussée finie)
    double g0 = Constants.G0_STANDARD_GRAVITY;
    double mdot = thrust / (g0 * isp);
    double burnDuration1 = initialMass * (1.0 - FastMath.exp(-dv1 / (g0 * isp))) / mdot;
    double massAfterBurn1 = initialMass - mdot * burnDuration1;
    double burnDuration2 = massAfterBurn1 * (1.0 - FastMath.exp(-dv2 / (g0 * isp))) / mdot;

    System.out.println("=== TRANSFERT HOHMANN (poussée finie) ===");
    System.out.printf("  ΔV1 = %.2f m/s, Burn1 = %.1f s%n", dv1, burnDuration1);
    System.out.printf("  Coast = %.1f s (%.1f min)%n", durationTransfer, durationTransfer / 60);
    System.out.printf("  ΔV2 = %.2f m/s, Burn2 = %.1f s%n", dv2, burnDuration2);

    // =====================================================================
    // 2. DATES DES MANŒUVRES
    // =====================================================================
    // Burn 1 centré autour du périgée
    AbsoluteDate burn1Start = initialDate;
    AbsoluteDate burn1End = burn1Start.shiftedBy(burnDuration1);
    AbsoluteDate burn2Start = burn1End.shiftedBy(durationTransfer);
    AbsoluteDate burn2End = burn2Start.shiftedBy(burnDuration2);

    // =====================================================================
    // 3. PROPAGATION AVEC MANŒUVRES
    // =====================================================================
    AttitudeProvider velocityAligned = new LofOffset(inertialFrame, LOFType.TNW);

    NumericalPropagator propagator = createPropagator();
    propagator.setInitialState(state0);
    propagator.setAttitudeProvider(velocityAligned);

    // Burn 1 : prograde
    propagator.addForceModel(
        new ConstantThrustManeuver(
            velocityAligned,
            new DateBasedManeuverTriggers("Burn1", burn1Start, burnDuration1),
            new BasicConstantThrustPropulsionModel(thrust, isp, Vector3D.PLUS_I, "Burn1")));

    // Burn 2 : prograde
    propagator.addForceModel(
        new ConstantThrustManeuver(
            velocityAligned,
            new DateBasedManeuverTriggers("Burn2", burn2Start, burnDuration2),
            new BasicConstantThrustPropulsionModel(thrust, isp, Vector3D.PLUS_I, "Burn2")));

    SpacecraftState finalState = propagator.propagate(burn2End.shiftedBy(10.0));

    // =====================================================================
    // 4. RÉSULTATS
    // =====================================================================
    double finalA = finalState.getOrbit().getA();
    double finalE = finalState.getOrbit().getE();
    double finalAlt = (finalA - re) / 1000.0;

    System.out.println("=== RÉSULTAT ===");
    System.out.printf("  Altitude Finale : %.1f km (cible: 400 km)%n", finalAlt);
    System.out.printf("  Excentricité    : %.6f%n", finalE);
    System.out.printf("  Masse Restante  : %.1f kg%n", finalState.getMass());

    // On accepte une tolérance large car la poussée finie
    // ne donne pas exactement le même résultat que l'impulsif
    assertEquals(targetA, finalA, 5000.0, "SMA dans les 5 km");
    assertTrue(finalE < 0.015, "Excentricité quasi-circulaire");

    System.out.println("=== TEST RÉUSSI ===");
  }

  private NumericalPropagator createPropagator() {
    double[] absTol = {1.0, 1.0, 1.0, 1e-3, 1e-3, 1e-3, 1e-2};
    double[] relTol = {1e-10, 1e-10, 1e-10, 1e-10, 1e-10, 1e-10, 1e-10};

    NumericalPropagator prop =
        new NumericalPropagator(new DormandPrince853Integrator(1e-3, 300.0, absTol, relTol));
    prop.addForceModel(
        new HolmesFeatherstoneAttractionModel(
            FramesFactory.getITRF(IERSConventions.IERS_2010, true),
            GravityFieldFactory.getNormalizedProvider(8, 8)));
    prop.setOrbitType(OrbitType.CARTESIAN);
    prop.setMu(mu);
    return prop;
  }
}
