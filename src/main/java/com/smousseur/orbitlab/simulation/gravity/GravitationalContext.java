package com.smousseur.orbitlab.simulation.gravity;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.simulation.OrekitService;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.utils.Constants;

/**
 * The gravitational environment a propagation is flown in: the central body it orbits, the third
 * bodies that perturb it, and everything a force model, an altitude or a node detector needs from
 * either — nothing else.
 *
 * <p>Introduced by PHY-4 / L1 (spec {@code docs/multi-corps/03-conception-L1.md}) to turn the
 * central body from a constant read at the bottom of {@link OrekitService} into a datum carried by
 * the stage. PHY-4 / L2 (spec {@code docs/multi-corps/04-conception-L2.md}) added {@link
 * #perturbers()}: L1 had left open where the third-body list would live, and the answer is here
 * rather than in a second stage declaration, because a lunar arc in L4 will declare a central body
 * and its perturbers together — separating them now would only mean rejoining them later.
 *
 * <p>Nothing in production declares a perturber: L2 is opt-in and no stage opts in (spec L2 §4.1).
 * L6 is the first real declarant.
 *
 * @param body the central body
 * @param mu the gravitational parameter the <b>propagator</b> integrates with (m³/s²)
 * @param inertialFrame the body-centred inertial frame — GCRF for the Earth
 * @param bodyFixedFrame the body-fixed rotating frame — ITRF for the Earth
 * @param shape the reference shape; a sphere is an ellipsoid of zero flattening
 * @param perturbers the third bodies whose attraction perturbs the propagation, possibly empty;
 *     always held as an unmodifiable {@link EnumSet} — see the compact constructor for why
 */
public record GravitationalContext(
    SolarSystemBody body,
    double mu,
    Frame inertialFrame,
    Frame bodyFixedFrame,
    OneAxisEllipsoid shape,
    Set<SolarSystemBody> perturbers) {

  /**
   * Validates and canonicalises the perturber set.
   *
   * <p><b>The set is copied into an {@link EnumSet}, and that is a numerical decision, not a
   * tidiness one.</b> The order force models are added to a propagator is the order their
   * accelerations are summed, which decides the last bit. An {@code EnumSet} iterates in ordinal
   * order, so {@code withPerturbers(SUN, MOON)} and {@code withPerturbers(MOON, SUN)} build the
   * same propagator to the float. A {@code List} would make a numerical result depend on the order
   * of the caller's arguments, for nothing gained.
   *
   * <p><b>The central body is rejected, not ignored.</b> Declaring the Earth as a perturber of a
   * geocentric propagation is a caller bug, and it is the easiest one to commit in L4 by copying an
   * Earth context to adapt it to a lunar arc. Silently dropping it would hide that.
   */
  public GravitationalContext {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(inertialFrame, "inertialFrame");
    Objects.requireNonNull(bodyFixedFrame, "bodyFixedFrame");
    Objects.requireNonNull(shape, "shape");
    Objects.requireNonNull(perturbers, "perturbers");
    if (perturbers.contains(body)) {
      throw new IllegalArgumentException(
          "the central body " + body + " cannot be its own perturber");
    }
    perturbers = Collections.unmodifiableSet(canonical(perturbers));
  }

  /**
   * The Earth context: exactly what every site read before L1, from the same places.
   *
   * <p><b>Lazily resolved, deliberately.</b> The frames and the ellipsoid are built by Orekit, so a
   * {@code static final} field would resolve them at class-initialisation time — possibly before
   * {@code OrekitService.get().initialize()}, which the tests call in {@code @BeforeAll}. The
   * holder defers that to first read.
   *
   * @return the shared Earth context
   */
  public static GravitationalContext earth() {
    return Holder.EARTH;
  }

  /**
   * The Moon context: point-mass gravity, a selenocentric frame with ICRF axes, and a spherical
   * reference shape.
   *
   * <p>Introduced by PHY-4 / L4 (spec {@code docs/multi-corps/06-conception-L4.md} §2.1). Three of
   * its five components are not what one would guess:
   *
   * <ul>
   *   <li>the frame is {@link OrekitService#bodyCentredIcrfFrame}, <b>not</b> {@code
   *       CelestialBody.getInertiallyOrientedFrame()} — see that method for the three measurements
   *       that decided it;
   *   <li>{@code mu} is Orekit's own {@code getGM()}, so the propagator's central term and the
   *       {@link org.orekit.forces.gravity.ThirdBodyAttraction} another arc would mount for the
   *       Moon agree by construction;
   *   <li>the shape is a sphere — an ellipsoid of zero flattening, which is what L1 §2.2 chose the
   *       concrete {@code OneAxisEllipsoid} type for.
   * </ul>
   *
   * <p>Unperturbed, like {@link #earth()}: the declaring stage adds the perturbers through {@link
   * #withPerturbers}, and a switch derives them mechanically (spec L4 §4.2).
   *
   * @return the shared Moon context
   */
  public static GravitationalContext moon() {
    return Holder.MOON;
  }

  /**
   * The equatorial radius of the reference shape (m), as the re-entry guard's spherical switching
   * function needs it.
   *
   * @return the equatorial radius in meters
   */
  public double equatorialRadius() {
    return shape.getEquatorialRadius();
  }

  /**
   * The same central body, perturbed by the given third bodies. This is how a stage opts in: it
   * overrides {@code MissionStage.gravitationalContext(Mission)} and returns {@code
   * earth().withPerturbers(MOON, SUN)}. No site of propagator construction has to change — the
   * context already travels there through the seam L1 installed.
   *
   * @param bodies the perturbing bodies; none of them may be the central body
   * @return a context identical to this one but for its perturbers
   */
  public GravitationalContext withPerturbers(SolarSystemBody... bodies) {
    return new GravitationalContext(
        body, mu, inertialFrame, bodyFixedFrame, shape, canonical(Arrays.asList(bodies)));
  }

  /**
   * Copies into an {@link EnumSet}, which {@code EnumSet.copyOf} cannot do for an empty non-enum
   * collection — hence the explicit branch rather than a bare {@code copyOf}.
   */
  private static EnumSet<SolarSystemBody> canonical(Iterable<SolarSystemBody> bodies) {
    EnumSet<SolarSystemBody> set = EnumSet.noneOf(SolarSystemBody.class);
    for (SolarSystemBody perturber : bodies) {
      set.add(Objects.requireNonNull(perturber, "perturber"));
    }
    return set;
  }

  private static final class Holder {
    private static final GravitationalContext EARTH =
        new GravitationalContext(
            SolarSystemBody.EARTH,
            // The propagator's mu, NOT the potential provider's. OrbitElements.mean() deliberately
            // rebases on the provider's mu instead: mixing the two shifts the elements by about a
            // metre, which reads as J2 (spec orbit-reporting/01 section 3.3). L1 must not "unify"
            // them — that would invalidate the L0 baseline with nothing to attribute it to.
            Constants.WGS84_EARTH_MU,
            OrekitService.get().gcrf(),
            OrekitService.get().itrf(),
            OrekitService.get().getEarthEllipsoid(),
            // Unperturbed, and that is what every existing mission flies. This instance is no
            // longer the only Earth context — withPerturbers derives others — but it is still the
            // only one WITHOUT a perturber, which is what keeps the L1 gate's 0.0 tolerance
            // meaningful (spec L2 §2.3).
            EnumSet.noneOf(SolarSystemBody.class));

    private static final GravitationalContext MOON =
        new GravitationalContext(
            SolarSystemBody.MOON,
            OrekitService.get().body(SolarSystemBody.MOON).getGM(),
            OrekitService.get().bodyCentredIcrfFrame(SolarSystemBody.MOON),
            OrekitService.get().body(SolarSystemBody.MOON).getBodyOrientedFrame(),
            new OneAxisEllipsoid(
                PlanetRadius.radiusFor(SolarSystemBody.MOON),
                // A sphere. The Moon's flattening is about 1/830 and nothing in PHY-4 reads it:
                // the shape serves the altitude of a sample and the re-entry guard's radius, and
                // both are within metres of the spherical answer at the altitudes flown.
                0.0,
                OrekitService.get().body(SolarSystemBody.MOON).getBodyOrientedFrame()),
            EnumSet.noneOf(SolarSystemBody.class));
  }
}
