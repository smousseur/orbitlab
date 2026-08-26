package com.smousseur.orbitlab.simulation.gravity;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * Crossing a sphere-of-influence boundary: which gravitational context the trajectory continues in,
 * and the same physical state expressed in it.
 *
 * <p>Introduced by PHY-4 / L4 (spec {@code docs/multi-corps/06-conception-L4.md} §4.2). Two
 * operations, and nothing else — the orchestration that calls them lives in {@code StageLegRunner}.
 */
public final class ArcTransition {

  private ArcTransition() {}

  /**
   * The context the trajectory continues in after crossing the boundary of {@code boundaryBody}'s
   * sphere of influence.
   *
   * <p><b>One rule, and it makes L4's perturbation decision a consequence rather than a second
   * rule:</b>
   *
   * <pre>
   *   central(new)     = the crossed body, or its primary when it is the central body being left
   *   perturbers(new)  = perturbers(old) − {new central} + {old central}
   * </pre>
   *
   * <p>An Earth arc declared {@code withPerturbers(MOON, SUN)} therefore becomes {@code
   * moon().withPerturbers(EARTH, SUN)}: the Sun crosses without being named, and the body just left
   * keeps perturbing the one now flown around. Measured, that is what makes the two sides of a
   * boundary the same physics — 0.246 m apart after six hours, against 7 249 m of solar tide when
   * the Sun is dropped on one side (spec L4 §2.2).
   *
   * <p>Removing the new central body from the perturbers is not cosmetic: {@link
   * GravitationalContext} <b>throws</b> when the central body is among its own perturbers (spec L2
   * §2.2), which is precisely the mistake this method exists to make impossible.
   *
   * @param from the context flown up to the boundary
   * @param boundaryBody the body whose sphere of influence was crossed
   * @return the context to fly on the other side
   */
  public static GravitationalContext across(
      GravitationalContext from, SolarSystemBody boundaryBody) {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(boundaryBody, "boundaryBody");

    SolarSystemBody newCentral =
        boundaryBody == from.body() ? SphereOfInfluence.of(boundaryBody).primary() : boundaryBody;
    if (newCentral == from.body()) {
      throw new IllegalArgumentException(
          "crossing " + boundaryBody + "'s SOI does not change the central body " + from.body());
    }

    List<SolarSystemBody> perturbers = new ArrayList<>(from.perturbers());
    perturbers.remove(newCentral);
    perturbers.add(from.body());

    return contextFor(newCentral).withPerturbers(perturbers.toArray(new SolarSystemBody[0]));
  }

  /**
   * The same physical state, expressed in {@code to}'s inertial frame and rebased on its
   * gravitational parameter.
   *
   * <p><b>Exact, and that is a property of the frames rather than of this code.</b> Both inertial
   * frames are ICRF-oriented, so the transform between them is a pure translation: the round trip
   * measures 0 m in position and 8.5e-14 m/s in velocity (spec L4 §1.2-C). The découpage asked for
   * the millimetre and the µm/s; this is several orders tighter, and the reason is written rather
   * than the tolerance being negotiated.
   *
   * <p>The orbit is rebuilt as a {@link CartesianOrbit}: a lunar approach is hyperbolic about the
   * Moon, which the equinoctial and Keplerian types cannot represent.
   *
   * <p>The attitude is re-expressed in the new frame rather than dropped. {@code SpacecraftState}
   * refuses an attitude whose reference frame differs from its orbit's, so the alternative would be
   * an unspecified default attitude — silently discarding whatever the outgoing leg was holding.
   * Nothing in L4 reads it (a switch only happens on a non-propulsive stage, spec §3.3), which is
   * exactly why it must be carried rather than quietly reset.
   *
   * @param state the state to convert, in the outgoing context's frame
   * @param to the context to express it in
   * @return the same state, in {@code to}
   */
  public static SpacecraftState convert(SpacecraftState state, GravitationalContext to) {
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(to, "to");

    if (state.getFrame() == to.inertialFrame()) {
      return state;
    }

    TimeStampedPVCoordinates pv =
        state
            .getFrame()
            .getTransformTo(to.inertialFrame(), state.getDate())
            .transformPVCoordinates(state.getPVCoordinates());

    return new SpacecraftState(
        new CartesianOrbit(pv, to.inertialFrame(), to.mu()),
        state.getAttitude().withReferenceFrame(to.inertialFrame()),
        state.getMass());
  }

  /** The known context of a body. PHY-4 needs exactly two. */
  private static GravitationalContext contextFor(SolarSystemBody body) {
    return switch (body) {
      case EARTH -> GravitationalContext.earth();
      case MOON -> GravitationalContext.moon();
      default ->
          throw new IllegalArgumentException(
              "no gravitational context for " + body + "; PHY-4 covers the Earth and the Moon");
    };
  }
}
