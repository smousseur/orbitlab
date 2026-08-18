package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowCandidate;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowProblem;
import org.orekit.time.AbsoluteDate;

import java.time.Duration;

/**
 * The Earth-Moon problem: what a translunar injection costs at a given epoch, read off the
 * closed-form Lambert seed with no propagation at all.
 *
 * <p><b>The cost varies for one reason, and it is worth naming</b>: the lunar distance swings from
 * 363 300 to 405 500 km over an anomalistic month, which the vis-viva turns into a few tens of m/s
 * on the injection. That, plus the epochs TranslunarInjectionPlan#transferPlaneNormal refuses
 * outright, is the whole shape of the merit function — monthly, smooth, with one minimum per
 * revolution around perigee of the lunar orbit.
 *
 * <p><b>What this problem does <em>not</em> yet model</b>, and the honest limit of the first
 * increment: {@link TranslunarInjectionPlan} builds its parking plane <em>to fit</em> the Moon at
 * arrival, so it has no plane to wait for. A real MIS-4 launches from a site into a plane fixed by
 * the ascent, and the dominant criterion becomes the alignment of that fixed plane with the Moon's
 * direction at arrival — twice per sidereal month per site. That criterion is a second
 * implementation of this same interface, and the reason the interface exists.
 */
public class TranslunarInjectionPlanWindowProblem implements LaunchWindowProblem {

  /**
   * Sweep step. Six hours resolves a monthly criterion by a factor of a hundred and twenty, and
   * keeps a sixty-day search at 240 closed-form evaluations — milliseconds.
   */
  private static final Duration COARSE_STEP = Duration.ofHours(6);

  private final double mass;

  /**
   * @param mass the spacecraft mass at injection (kg)
   */
  public TranslunarInjectionPlanWindowProblem(double mass) {
    this.mass = mass;
  }

  @Override
  public String name() {
    return "Translunar injection";
  }

  @Override
  public Duration coarseStep() {
    return COARSE_STEP;
  }

  @Override
  public LaunchWindowCandidate evaluate(AbsoluteDate epoch) {
    try {
      return LaunchWindowCandidate.of(
          epoch, TranslunarInjectionPlan.keplerianInjectionDeltaV(epoch, mass));
    } catch (OrbitlabException refused) {
      // The declination guard, and any other closed-form refusal: data, not a failure.
      return LaunchWindowCandidate.refused(epoch, refused.getMessage());
    }
  }
}
