package com.smousseur.orbitlab.simulation.gravity;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import java.util.Objects;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.utils.Constants;

/**
 * The central body a propagation is flown around: everything a force model, an altitude or a node
 * detector needs, and nothing else.
 *
 * <p>Introduced by PHY-4 / L1 (spec {@code docs/multi-corps/03-conception-L1.md}) to turn the
 * central body from a constant read at the bottom of {@link OrekitService} into a datum carried by
 * the stage. In L1 nothing declares anything but the Earth; L4 is where a stage first declares
 * another body.
 *
 * @param body the central body
 * @param mu the gravitational parameter the <b>propagator</b> integrates with (m³/s²)
 * @param inertialFrame the body-centred inertial frame — GCRF for the Earth
 * @param bodyFixedFrame the body-fixed rotating frame — ITRF for the Earth
 * @param shape the reference shape; a sphere is an ellipsoid of zero flattening
 */
public record GravitationalContext(
    SolarSystemBody body,
    double mu,
    Frame inertialFrame,
    Frame bodyFixedFrame,
    OneAxisEllipsoid shape) {

  public GravitationalContext {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(inertialFrame, "inertialFrame");
    Objects.requireNonNull(bodyFixedFrame, "bodyFixedFrame");
    Objects.requireNonNull(shape, "shape");
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
   * The equatorial radius of the reference shape (m), as the re-entry guard's spherical switching
   * function needs it.
   *
   * @return the equatorial radius in meters
   */
  public double equatorialRadius() {
    return shape.getEquatorialRadius();
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
            OrekitService.get().getEarthEllipsoid());
  }
}
