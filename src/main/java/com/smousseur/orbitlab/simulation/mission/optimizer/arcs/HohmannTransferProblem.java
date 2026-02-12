package com.smousseur.orbitlab.simulation.mission.optimizer.arcs;

import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

/**
 * Hohmann transfer problem: circular orbit → circular orbit. Guess based on the analytical ΔV of
 * the two-impulse transfer.
 */
public class HohmannTransferProblem extends BurnArcProblem {

  private final double targetRadius;

  public HohmannTransferProblem(
      SpacecraftState initialState,
      AbsoluteDate targetDate,
      PropulsionSystem propulsion,
      double targetRadius) {
    super(initialState, targetDate, propulsion, 2);
    this.targetRadius = targetRadius;
    double mu = initialState.getOrbit().getMu();
    double r0 = initialState.getPVCoordinates().getPosition().getNorm();
    double aTransfer = (r0 + targetRadius) / 2.0;
    double vCirc0 = FastMath.sqrt(mu / r0);
    double vPerigee = FastMath.sqrt(mu * (2.0 / r0 - 1.0 / aTransfer));
    double vApogee = FastMath.sqrt(mu * (2.0 / targetRadius - 1.0 / aTransfer));
    double vCircTarget = FastMath.sqrt(mu / targetRadius);
    double burn1 = FastMath.abs(vPerigee - vCirc0) * initialState.getMass() / propulsion.thrust();
    double burn2 =
        FastMath.abs(vCircTarget - vApogee) * initialState.getMass() / propulsion.thrust();

    this.maxBurnDuration = FastMath.max(FastMath.max(burn1, burn2) * 2.0, 3.0);
  }

  @Override
  public double[] buildInitialGuess() {
    double[] start = new double[getNumVariables()];
    double mu = initialState.getOrbit().getMu();
    double r0 = initialState.getPVCoordinates().getPosition().getNorm();

    // Ellipse de transfert
    double aTransfer = (r0 + targetRadius) / 2.0;
    double tTransfer = FastMath.PI * FastMath.sqrt(aTransfer * aTransfer * aTransfer / mu);

    // ΔV Hohmann
    double vCirc0 = FastMath.sqrt(mu / r0);
    double vPerigee = FastMath.sqrt(mu * (2.0 / r0 - 1.0 / aTransfer));
    double vApogee = FastMath.sqrt(mu * (2.0 / targetRadius - 1.0 / aTransfer));
    double vCircTarget = FastMath.sqrt(mu / targetRadius);

    double dv1 = vPerigee - vCirc0;
    double dv2 = vCircTarget - vApogee;

    double mass = initialState.getMass();
    double thrust = propulsion.thrust();
    double burn1 = FastMath.abs(dv1) * mass / thrust;
    double burn2 = FastMath.abs(dv2) * mass / thrust;

    // Recalibrer maxBurnDuration pour que les burns soient dans une zone
    // sensible de la sigmoïde (~30-70% de la plage)
    double maxBurn = FastMath.max(burn1, burn2) * 3.0; // les burns font ~33% de la plage
    this.maxBurnDuration = FastMath.max(maxBurn, 10.0); // minimum 10s

    // Arc 1 : prograde immédiat
    encodeArc(start, 0, 0.0, burn1, 0.0, 0.0, 1.0);

    // Arc 2 : prograde à l'apogée
    double tStart2 = tTransfer - burn2 / 2.0;
    encodeArc(start, 1, tStart2, burn2, 0.0, 0.0, 1.0);

    System.out.printf(
        "  [Hohmann] dv1=%.1f m/s (burn=%.1fs), dv2=%.1f m/s (burn=%.1fs), maxBurnDuration=%.1fs%n",
        FastMath.abs(dv1), burn1, FastMath.abs(dv2), burn2, maxBurnDuration);

    return start;
  }
}
