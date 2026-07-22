package com.smousseur.orbitlab.simulation.mission.vehicle;

/**
 * Propellant accounting of one physical stage of a {@link VehicleStack}: what was loaded at
 * lift-off and what is left in that stage's own tanks.
 *
 * <p>Distinct from {@code StagePerformance}, which accounts one <em>mission</em> stage (a flight
 * phase). This record accounts one <em>vehicle</em> stage, and its {@link #stageIndex()} matches
 * the launcher stage order (index 0 = bottom stage, the payload sitting last).
 *
 * @param stageIndex zero-based index of the stage in the vehicle stack
 * @param loaded the propellant loaded into this stage at lift-off (kg)
 * @param residual the propellant left in this stage when it stopped flying — at mission end for
 *     the final active stage, at jettison for a stage dropped early, zero for a stage burnt to its
 *     depletion floor (kg)
 */
public record StagePropellant(int stageIndex, double loaded, double residual) {

  /** Propellant actually burnt by this stage (kg). */
  public double consumed() {
    return Math.max(0.0, loaded - residual);
  }

  /** Residual as a fraction of this stage's own load (0 when nothing was loaded). */
  public double residualRatio() {
    return loaded > 0 ? residual / loaded : 0.0;
  }
}
