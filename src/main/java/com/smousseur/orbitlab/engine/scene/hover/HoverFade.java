package com.smousseur.orbitlab.engine.scene.hover;

import com.smousseur.orbitlab.engine.HoverConfig;

/**
 * The hover intensity of one planet or one mission icon, in {@code [0,1]}: 0 at rest, 1 fully lit.
 *
 * <p>It is an exponential smoothing toward its target, {@code h += (target − h)·(1 − e^(−dt/τ))},
 * with a shorter time constant in than out, and it lands exactly on its target once within {@code
 * snap} of it — so that a resting icon stops being touched. A change of target mid-fade turns back
 * from wherever the intensity is, with no jump.
 *
 * <p>It advances in real time, the frame's own {@code tpf}: the hover answers the cursor, so it
 * keeps moving while the simulation is paused.
 */
public final class HoverFade {

  private final float fadeInTauSec;
  private final float fadeOutTauSec;
  private final float snap;
  private float intensity;

  /**
   * @param fadeInTauSec time constant toward lit, in seconds
   * @param fadeOutTauSec time constant toward unlit, in seconds
   * @param snap distance to the target under which the intensity lands on it
   */
  public HoverFade(float fadeInTauSec, float fadeOutTauSec, float snap) {
    this.fadeInTauSec = fadeInTauSec;
    this.fadeOutTauSec = fadeOutTauSec;
    this.snap = snap;
  }

  /**
   * Creates a fade at rest, tuned by a hover configuration.
   *
   * @param config the hover configuration
   * @return a fade at intensity 0
   */
  public static HoverFade of(HoverConfig config) {
    return new HoverFade(config.fadeInTauSec(), config.fadeOutTauSec(), config.fadeSnap());
  }

  /**
   * Moves the intensity one frame toward lit or unlit.
   *
   * @param lit whether the target is 1 rather than 0
   * @param dt the frame time, in seconds; zero leaves the intensity where it is
   * @return the new intensity
   */
  public float advance(boolean lit, float dt) {
    if (!(dt > 0f)) {
      return intensity;
    }
    float target = lit ? 1f : 0f;
    float tau = lit ? fadeInTauSec : fadeOutTauSec;
    intensity += (target - intensity) * (1f - (float) Math.exp(-dt / tau));
    if (Math.abs(target - intensity) < snap) {
      intensity = target;
    }
    return intensity;
  }

  /**
   * @return the current intensity, in {@code [0,1]}
   */
  public float intensity() {
    return intensity;
  }
}
