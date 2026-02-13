package com.smousseur.orbitlab.simulation.mission.optimizer.arcs;

import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

public class SubOrbitalTransferProblem extends BurnArcProblem {
  private final double targetRadius;

  public SubOrbitalTransferProblem(
      SpacecraftState initialState,
      AbsoluteDate targetDate,
      PropulsionSystem propulsion,
      double targetRadius) {
    super(initialState, targetDate, propulsion, 3);
    this.targetRadius = targetRadius;
  }

  @Override
  public double[] buildInitialGuess() {
    double[] start = new double[numArcs * PARAMS_PER_ARC];
    Vector3D pos = initialState.getPVCoordinates().getPosition();
    Vector3D vel = initialState.getPVCoordinates().getVelocity();
    double mu = initialState.getOrbit().getMu();
    double r0 = pos.getNorm();

    double targetR = targetRadius;

    // ---Transfert ellipse (perigee = r0, apogee = targetR) ---
    // Si r0 > targetR, inverser les rôles
    double rPerigee = FastMath.min(r0, targetR);
    double rApogee = FastMath.max(r0, targetR);
    double aTransfer = (rPerigee + rApogee) / 2.0;

    // Speed at perigee on the transfert ellipse (vis-viva)
    double vPerigee = FastMath.sqrt(mu * (2.0 / r0 - 1.0 / aTransfer));

    // Desired tangential direction
    Vector3D rHat = pos.normalize();
    double vRadial = Vector3D.dotProduct(vel, rHat);
    Vector3D vTangVec = vel.subtract(rHat.scalarMultiply(vRadial));
    double vTangNorm = vTangVec.getNorm();

    // Unit tangent vector
    // If the tangential velocity is near zero, use the angular momentum
    Vector3D tHat;
    if (vTangNorm > 1.0) {
      tHat = vTangVec.normalize();
    } else {
      // Angular momentum h = r × v → direction normal to the orbital plane
      Vector3D hVec = Vector3D.crossProduct(pos, vel);
      if (hVec.getNorm() > 1e-6) {
        tHat = Vector3D.crossProduct(hVec, pos).normalize();
      } else {
        // Degenerate case: purely radial velocity, choose an arbitrary tangent
        tHat = computeArbitraryTangent(rHat);
      }
    }

    // --- ΔV₁: Injection onto the transfer ellipse ---
    Vector3D vTarget1 = tHat.scalarMultiply(vPerigee);
    Vector3D dv1 = vTarget1.subtract(vel);

    // --- ΔV₂: Circularization at apogee ---
    double vApogee = FastMath.sqrt(mu * (2.0 / targetR - 1.0 / aTransfer));
    double vCirc = FastMath.sqrt(mu / targetR);
    double dv2Mag = vCirc - vApogee; // prograde

    // --- Flight time perigee → apogee ---
    double tTransfer = FastMath.PI * FastMath.sqrt(aTransfer * aTransfer * aTransfer / mu);

    // --- Burn durations ---
    double thrust = propulsion.thrust();
    double mass = initialState.getMass();
    double burnDuration1 = dv1.getNorm() * mass / thrust;
    double burnDuration2 = FastMath.abs(dv2Mag) * mass / thrust;

    System.out.printf(
        "  [Analytical] ΔV1=%.1f m/s (burn=%.0fs), ΔV2=%.1f m/s (burn=%.0fs)%n",
        dv1.getNorm(), burnDuration1, FastMath.abs(dv2Mag), burnDuration2);
    System.out.printf(
        "  [Analytical] Transfer time=%.0fs, a_transfer=%.0f km%n", tTransfer, aTransfer / 1000);

    // --- Arc 1 : ΔV₁ (immediate) ---
    double[] alphaDelta1 = computeLvlhAngles(dv1, initialState);
    double tStart1 = 0.0;
    encodeArc(start, 0, tStart1, burnDuration1, alphaDelta1[0], alphaDelta1[1], 1.0);

    // --- Arc 2 : ΔV₂ (at apogee, after tTransfer) ---
    double tStart2 = tTransfer - burnDuration2 / 2.0; // center burn at apogee
    tStart2 = FastMath.max(tStart1 + burnDuration1, tStart2); // no overlap with arc 1
    encodeArc(start, 1, tStart2, burnDuration2, 0.0, 0.0, 1.0);

    // --- Arc 3 (if available): residual correction ---
    if (numArcs >= 3) {
      // Small correction arc, prograde, after circularization
      double tStart3 = tStart2 + burnDuration2 + 60.0; // 60s après
      double burnDuration3 = 10.0; // court
      encodeArc(start, 2, tStart3, burnDuration3, 0.0, 0.0, 0.3);
    }

    // Additional arcs: near-zero throttle (free degrees of freedom)
    for (int arc = 3; arc < numArcs; arc++) {
      double tStartN = totalDuration * ((double) arc / numArcs);
      encodeArc(start, arc, tStartN, 10.0, 0.0, 0.0, 0.05);
    }

    return start;
  }

  /**
   * Computes the angles alpha (in-plane) and delta (out-of-plane) of a ΔV vector in the spacecraft
   * LVLH frame.
   */
  private double[] computeLvlhAngles(Vector3D deltaV, SpacecraftState state) {
    Vector3D vel = state.getPVCoordinates().getVelocity();
    Vector3D pos = state.getPVCoordinates().getPosition();

    // Repère LVLH : x=prograde, y=normal, z=complète le trièdre
    Vector3D vHat = vel.normalize();
    Vector3D hVec = Vector3D.crossProduct(pos, vel);
    Vector3D nHat = hVec.getNorm() > 1e-6 ? hVec.normalize() : computeArbitraryNormal(vHat);
    Vector3D wHat = Vector3D.crossProduct(vHat, nHat);

    double dvPrograde = Vector3D.dotProduct(deltaV, vHat);
    double dvNormal = Vector3D.dotProduct(deltaV, nHat);
    double dvRadial = Vector3D.dotProduct(deltaV, wHat);

    double alpha = FastMath.atan2(dvNormal, dvPrograde);
    double delta =
        FastMath.atan2(dvRadial, FastMath.sqrt(dvPrograde * dvPrograde + dvNormal * dvNormal));

    return new double[] {alpha, delta};
  }

  private static Vector3D computeArbitraryTangent(Vector3D rHat) {
    // Choose a vector that is not collinear with rHat
    Vector3D seed = FastMath.abs(rHat.getX()) < 0.9 ? Vector3D.PLUS_I : Vector3D.PLUS_J;
    return Vector3D.crossProduct(rHat, seed).normalize();
  }

  private static Vector3D computeArbitraryNormal(Vector3D vHat) {
    Vector3D seed = FastMath.abs(vHat.getX()) < 0.9 ? Vector3D.PLUS_I : Vector3D.PLUS_J;
    return Vector3D.crossProduct(vHat, seed).normalize();
  }
}
