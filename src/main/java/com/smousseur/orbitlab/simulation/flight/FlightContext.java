package com.smousseur.orbitlab.simulation.flight;

import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import java.util.Objects;

/**
 * The environment a propagation is flown in, whole: what pulls on the vehicle, and what rubs
 * against it.
 *
 * <p>Introduced by PHY-1 / L1 (spec {@code docs/atmosphere/04-conception-L1.md} §2). It
 * <b>composes</b> {@link GravitationalContext} instead of extending or renaming it: that record
 * says exactly what it is about, its two invariants keep meaning what they meant, and every site
 * that only needs gravity keeps reading a type that promises nothing else.
 *
 * <p><b>This pair is the propagator's force list.</b> The gravitational half becomes {@code setMu}
 * + the non-central field + the third bodies; the aerodynamic half is precisely the couple Orekit
 * asks for in {@code DragForce(Atmosphere, DragSensitive)}. So {@code drag == null} means no {@code
 * DragForce} is mounted at all — not a zero force, not an identity term — and the "unchanged to the
 * bit" property of this lot is a consequence of the type rather than of a convention someone has to
 * go and check (spec §1.1).
 *
 * <p><b>Nothing is delegated.</b> There is no {@code mu()}, no {@code body()}, no {@code
 * equatorialRadius()} here: a caller needing those writes {@code .gravity().mu()}. One datum, one
 * path — a delegation would open a second and invite the two to drift.
 *
 * @param gravity the gravitational environment; never null
 * @param drag the aerodynamic environment, or {@code null} when the vehicle flies through nothing —
 *     read through {@link #hasDrag()}
 */
public record FlightContext(GravitationalContext gravity, DragContext drag) {

  public FlightContext {
    Objects.requireNonNull(gravity, "gravity");
  }

  /**
   * Gravity only, no atmosphere — what every propagation flies until PHY-2 turns one on.
   *
   * @param gravity the gravitational environment
   */
  public FlightContext(GravitationalContext gravity) {
    this(gravity, null);
  }

  /**
   * The Earth context, drag off.
   *
   * <p><b>Deliberately not held in a holder of its own.</b> {@link GravitationalContext#earth()} is
   * lazy for a precise reason — Orekit frames must not resolve before {@code
   * OrekitService.initialize()} — and delegating inherits that laziness rather than duplicating the
   * mechanism. It also keeps the shared instance shared: {@code FlightContext.earth().gravity()} is
   * the very object {@code GravitationalContext.earth()} returns, which is what the L2 baseline
   * asserts on.
   *
   * @return the Earth flight context, without drag
   */
  public static FlightContext earth() {
    return new FlightContext(GravitationalContext.earth());
  }

  /**
   * The Moon context, drag off — and there is no other kind: the Moon has no atmosphere to resolve,
   * so a drag context carried across its boundary mounts nothing (spec §1.2).
   *
   * @return the lunar flight context
   */
  public static FlightContext moon() {
    return new FlightContext(GravitationalContext.moon());
  }

  /**
   * @return {@code true} when this context carries an atmosphere to fly against
   */
  public boolean hasDrag() {
    return drag != null;
  }

  /**
   * The same flight, around another body. This is how a sphere-of-influence crossing is applied:
   * {@code context.withGravity(ArcTransition.across(context.gravity(), body))}.
   *
   * <p><b>The aerodynamic half crosses untouched</b>, and that is the correct behaviour rather than
   * a leak: it names a model, and the model is resolved against the new central body's shape when
   * the next propagator is built. An Earth-to-Moon crossing therefore mounts nothing on the far
   * side, and the return crossing mounts drag again.
   *
   * @param gravity the gravitational environment to continue in
   * @return a context identical to this one but for its gravitational half
   */
  public FlightContext withGravity(GravitationalContext gravity) {
    return new FlightContext(gravity, drag);
  }

  /**
   * The same environment, flown against an atmosphere.
   *
   * @param drag the aerodynamic environment; never null — the absence is {@code new
   *     FlightContext(gravity)}
   * @return a context identical to this one but for its aerodynamic half
   */
  public FlightContext withDrag(DragContext drag) {
    return new FlightContext(gravity, Objects.requireNonNull(drag, "drag"));
  }
}
