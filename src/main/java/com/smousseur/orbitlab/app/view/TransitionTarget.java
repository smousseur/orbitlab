package com.smousseur.orbitlab.app.view;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import java.util.Objects;

/**
 * What a camera transition is heading towards: the focus state {@link FocusView} will be put into
 * once the animation completes.
 *
 * <p>Each variant carries exactly what the matching {@code FocusView} mutator needs, so applying a
 * target is a single dispatch with no extra lookup.
 */
public sealed interface TransitionTarget {

  /**
   * The body the rendered frame will be centred on once this target is applied. This is what the
   * camera pivot has to reach for the switch to be visually continuous — for a spacecraft it is the
   * parent body, the spacecraft's own offset being added on top by the caller.
   *
   * @return the anchor body of the target
   */
  SolarSystemBody anchorBody();

  /**
   * The view mode this target will put the focus into. Read while a transition is still playing, to
   * decide what the scene has to show <em>before</em> the focus actually flips.
   *
   * @return the target view mode
   */
  ViewMode mode();

  /** The whole solar system, seen from the default distance and centred on the Sun. */
  record Solar() implements TransitionTarget {
    @Override
    public SolarSystemBody anchorBody() {
      return SolarSystemBody.SUN;
    }

    @Override
    public ViewMode mode() {
      return ViewMode.SOLAR;
    }
  }

  /**
   * A single celestial body.
   *
   * @param body the body to focus
   */
  record Planet(SolarSystemBody body) implements TransitionTarget {
    public Planet {
      Objects.requireNonNull(body, "body");
    }

    @Override
    public SolarSystemBody anchorBody() {
      return body;
    }

    @Override
    public ViewMode mode() {
      return ViewMode.PLANET;
    }
  }

  /**
   * A mission's spacecraft, followed in the planet-scale context of the body it orbits.
   *
   * @param missionId the mission to follow
   * @param parentBody the body that mission's trajectory is expressed about
   */
  record Spacecraft(MissionId missionId, SolarSystemBody parentBody) implements TransitionTarget {
    public Spacecraft {
      Objects.requireNonNull(missionId, "missionId");
      Objects.requireNonNull(parentBody, "parentBody");
    }

    @Override
    public SolarSystemBody anchorBody() {
      return parentBody;
    }

    @Override
    public ViewMode mode() {
      return ViewMode.SPACECRAFT;
    }
  }
}
