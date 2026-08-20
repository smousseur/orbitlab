package com.smousseur.orbitlab.simulation.mission.vehicle.model;

/**
 * The two numbers a drag force needs from a piece of hardware: how much of it the flow sees, and
 * how badly that shape pushes back.
 *
 * <p>Introduced by PHY-1 / L1 (spec {@code docs/atmosphere/04-conception-L1.md} §2). It lives here,
 * at the smallest common ancestor of {@link stage.StageModel} and {@link PayloadModel}, because
 * both declare one and neither owns the other.
 *
 * <p><b>The surface comes first, and that is a defect-prevention decision rather than a stylistic
 * one</b> (spec §2, point 4). Orekit's consumer is {@code IsotropicDrag(crossSection, dragCoeff)}:
 * two adjacent {@code double}s whose transposition no unit test can see, and which would falsify
 * the drag of a launcher stage by a factor near 30. Aligning the record on the constructor it feeds
 * makes the transposition impossible to commit.
 *
 * @param crossSection the reference frontal area the drag is computed against (m²)
 * @param dragCoefficient the dimensionless drag coefficient referred to that same area
 */
public record AerodynamicProperties(double crossSection, double dragCoefficient) {

  public AerodynamicProperties {
    if (!(crossSection > 0) || !Double.isFinite(crossSection)) {
      throw new IllegalArgumentException(
          "crossSection must be positive and finite: " + crossSection);
    }
    if (!(dragCoefficient > 0) || !Double.isFinite(dragCoefficient)) {
      throw new IllegalArgumentException(
          "dragCoefficient must be positive and finite: " + dragCoefficient);
    }
  }

  /**
   * The ballistic coefficient {@code m / (Cd · S)} of a body of the given mass carrying these
   * properties (kg/m²) — the single number that decides how fast an orbit decays.
   *
   * @param massKg the current mass (kg)
   * @return the ballistic coefficient in kg/m²
   */
  public double ballisticCoefficient(double massKg) {
    return massKg / (dragCoefficient * crossSection);
  }
}
