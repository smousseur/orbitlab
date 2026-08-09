package com.smousseur.orbitlab.states.fx;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.renderer.ViewPort;
import com.jme3.system.AppSettings;
import com.jme3.texture.FrameBuffer;
import java.util.List;
import java.util.Objects;

/**
 * Application state that owns the scene's screen-space post-processing chain.
 *
 * <p>It carries a single effect today — a narrow bloom — but it is meant to be the one place where
 * the other screen-space effects land (tone mapping, god rays), so that they share a single {@link
 * FilterPostProcessor} and therefore a single offscreen framebuffer and a single resolve.
 *
 * <p>The bloom is <em>not</em> what gives the Sun its halo. That is {@code CoronaView}, geometry
 * attached to the star itself, and the division of labour is deliberate: a screen-space blur works
 * in pixels, so its reach and its resolution floor are the same number, and a halo wide enough for
 * a close-up cannot also be fine enough for a distant body — which is how the Sun ended up with a
 * square around it. Geometry has no floor. What is left here is the one thing geometry cannot do,
 * bleed inwards over the limb to soften the edge of the disc, and it needs so little reach that the
 * floor is never met. See {@code CoronaView} for the arithmetic.
 *
 * <h2>Which viewport carries the filter</h2>
 *
 * <p>The bloom is attached to the <strong>near</strong> viewport, not the far one. The naming is
 * misleading: {@code LodView} splits a body in two, a position anchor with no geometry under the
 * far bodies node and the actual 3D model under the near one, and it is the near node that {@code
 * FloatingOriginAppState} un-culls for the focused body. The Sun's disc is therefore drawn by
 * {@code NearView}; the far viewport only ever draws orbit lines. A processor on the far viewport
 * would have nothing to glow.
 *
 * <h2>Why the upstream viewports are re-routed</h2>
 *
 * <p>A {@link FilterPostProcessor} renders its viewport's scene into an offscreen framebuffer, then
 * composites the filter chain onto the viewport's original output — and that composite pass clears
 * the colour buffer before drawing its fullscreen quad ({@code
 * FilterPostProcessor.renderProcessing} calls {@code clearBuffers(true, true, true)}). Whatever an
 * <em>earlier</em> viewport had already drawn to the screen is gone. Here that is everything: the
 * starfield comes from the {@code SkyView} pre-view and the orbit lines from the far viewport, both
 * of which render before {@code NearView}.
 *
 * <p>So the frame has to be assembled inside the filter's framebuffer rather than on the screen.
 * Each frame this state reads the framebuffer the processor has bound to the near viewport and
 * points the upstream viewports at it, so sky → far → near all accumulate into the same target and
 * the composite blits the whole frame at once, bloom included. The GUI post-view still renders to
 * the screen afterwards and is therefore left untouched by the filter, which is what we want.
 *
 * <p>Two consequences of that re-routing are worth knowing:
 *
 * <ul>
 *   <li>The framebuffer only exists once the processor has run, so the very first frame renders the
 *       sky and the orbits to the screen and then has them cleared away. The same one-frame gap
 *       reappears after a window resize, when the processor discards its framebuffer and builds a
 *       new one.
 *   <li>Depth and multisampling now come from the processor's framebuffer instead of the window's.
 *       The depth attachment is 24-bit as before — the depth budget reasoned about in {@code
 *       specs/graphics-effects/spacecraft-view-artefacts.md} §5.3 is unchanged — and the sample
 *       count is taken from the {@link AppSettings} so the MSAA level stays the one asked for at
 *       startup.
 * </ul>
 *
 * <h2>Why {@code GlowMode.Objects}</h2>
 *
 * <p>{@code GlowMode.Scene} extracts the bloom from the rendered image above a luminance threshold,
 * which means the threshold has to be tuned against the background. With a starfield behind the
 * scene that is a losing game: push it low enough for the Sun to bloom convincingly and the stars
 * bloom with it, and the whole sky turns milky. {@code GlowMode.Objects} re-renders the scene
 * through the materials' {@code Glow} technique instead, so only what a material explicitly
 * declares as emissive can glow. The Sun is the only body that declares anything (see {@code
 * PlanetPoseAppState}); the planets carry {@code WrapLighting}, which has no {@code Glow} technique
 * at all and is skipped, and the orbit lines carry {@code Unshaded} without a {@code GlowColor},
 * whose {@code Glow} technique outputs black.
 *
 * <p>The corona is excluded on the same principle, and on purpose: {@code Corona.j3md} declares no
 * {@code Glow} technique, so the glow it already is does not get blurred and added back on top of
 * itself.
 */
public final class PostFxAppState extends BaseAppState {

  /**
   * Strength of the halo, as the multiplier in the composite {@code bloom * intensity + scene}.
   *
   * <p>Deliberately modest now that {@code CoronaView} carries the Sun's halo. What is left for the
   * bloom is the one thing geometry cannot do — bleed <em>inwards</em> over the limb and soften the
   * edge of the disc — and that needs a suggestion, not a wash. Raising it blows the disc out, since
   * the composite is an addition onto something already near the top of the range, and the channels
   * clip one by one until the granulation is gone.
   */
  private static final float BLOOM_INTENSITY = 1.2f;

  /**
   * Spacing of the blur's nine taps, and the reason it is 1.
   *
   * <p>{@code HGaussianBlur.frag} computes {@code blurSize = m_Scale / m_Size} with {@code m_Size}
   * the width of the downsampled buffer, so this value <em>is</em> the tap spacing in texels of that
   * buffer. At 1 the nine taps are contiguous and the kernel is the Gaussian it claims to be. Above
   * that they skip texels and each output texel becomes a weighted sum of nine point samples off a
   * sparse grid — a comb rather than a blur, leaving periodic structure at the tap spacing.
   */
  private static final float BLUR_SCALE = 1f;

  /**
   * Exponent applied to the glow before it is blurred — and it must stay at 1, i.e. neutral.
   *
   * <p>In {@code GlowMode.Objects} the extract shader compiles down to a single line, {@code color
   * = pow(glowColor, ExposurePow)}: the {@code DO_EXTRACT} branch is not defined, so {@code
   * ExposureCutOff} is dead and this exponent applies straight to the Sun's own colour. That
   * exponent exists to separate the bright from the dim in {@code GlowMode.Scene}, where the pass
   * holds the whole rendered image. Here the pass holds nothing but the Sun, so there is nothing to
   * separate — all it can do is distort it.
   *
   * <p>And distort it it does, unevenly, because a power is applied per channel. On the Sun's
   * orange ≈ {@code (0.95, 0.45, 0.15)}, cubing gives {@code (0.86, 0.09, 0.003)}: red survives,
   * green drops fivefold, blue is annihilated. The halo comes out dark red around an orange disc,
   * and dies within a few pixels of the limb because the two channels that carried its reach are
   * gone.
   */
  private static final float EXPOSURE_POWER = 1f;

  /**
   * Resolution divider of the glow and blur passes, and with it the reach of the bloom: the kernel
   * is nine taps one texel apart, so the halo spans about {@code ±4 × BLUR_SCALE ×
   * DOWN_SAMPLING_FACTOR} screen pixels — some ±8 px here, at any window size.
   *
   * <p>That is far narrower than this used to be, and the narrowness is the point. Every texel of
   * this buffer is magnified back up at the composite, so it is also a <em>floor</em>: no halo finer
   * than a texel can be represented, and a magnified single texel is a square tent. Reach and floor
   * are the same number, which is the trap the Sun's halo fell into when the bloom had to carry it —
   * wide enough for a close-up meant a floor of some 16 px, and any body projecting smaller than
   * that got the magnification footprint instead of a halo: a square. {@code CoronaView} now carries
   * the reach in world units, where no floor exists, which frees this to sit low enough that its own
   * floor is never met.
   *
   * <p>Not animatable, whatever the temptation: {@code setDownSamplingFactor} calls {@code
   * reInitFilter}, which reallocates the filter's framebuffers.
   */
  private static final float DOWN_SAMPLING_FACTOR = 2f;

  private final ViewPort bloomViewport;
  private final List<ViewPort> upstreamViewports;

  private FilterPostProcessor processor;
  private BloomFilter bloom;

  /**
   * Creates the post-processing state.
   *
   * @param bloomViewport the viewport that draws the glowing geometry and will carry the filter
   *     chain — the near viewport, see the class documentation
   * @param upstreamViewports the viewports rendered before it whose output must be redirected into
   *     the filter's framebuffer, in render order
   */
  public PostFxAppState(ViewPort bloomViewport, ViewPort... upstreamViewports) {
    this.bloomViewport = Objects.requireNonNull(bloomViewport, "bloomViewport");
    this.upstreamViewports =
        List.of(Objects.requireNonNull(upstreamViewports, "upstreamViewports"));
  }

  @Override
  protected void initialize(Application app) {
    processor = new FilterPostProcessor(app.getAssetManager());
    AppSettings settings = app.getContext().getSettings();
    processor.setNumSamples(settings == null ? 1 : Math.max(1, settings.getSamples()));

    bloom = new SmoothBloomFilter(BloomFilter.GlowMode.Objects);
    // The down-sampling factor sizes the filter's own framebuffers, so it must be set before the
    // processor is attached to a viewport and initialises them.
    bloom.setDownSamplingFactor(DOWN_SAMPLING_FACTOR);
    bloom.setBlurScale(BLUR_SCALE);
    bloom.setExposurePower(EXPOSURE_POWER);
    bloom.setBloomIntensity(BLOOM_INTENSITY);
    processor.addFilter(bloom);
  }

  /**
   * Points the upstream viewports at the framebuffer the processor has just rendered into, so that
   * the next frame is assembled there instead of on the screen.
   *
   * <p>Runs after the frame rather than before it because the processor only binds that framebuffer
   * from inside its own {@code preFrame}: reading it here is what makes the value available, one
   * frame late, to viewports that render ahead of it.
   */
  @Override
  public void postRender() {
    FrameBuffer sceneBuffer = bloomViewport.getOutputFrameBuffer();
    if (sceneBuffer == null) {
      return;
    }
    for (ViewPort viewPort : upstreamViewports) {
      if (viewPort.getOutputFrameBuffer() != sceneBuffer) {
        viewPort.setOutputFrameBuffer(sceneBuffer);
      }
    }
  }

  @Override
  protected void onEnable() {
    bloomViewport.addProcessor(processor);
  }

  @Override
  protected void onDisable() {
    // Removing the processor restores the near viewport's own output; the upstream viewports are
    // ours to reset, otherwise they would keep drawing into a framebuffer nothing composites.
    bloomViewport.removeProcessor(processor);
    renderUpstreamToScreen();
  }

  @Override
  protected void cleanup(Application app) {
    renderUpstreamToScreen();
    processor = null;
    bloom = null;
  }

  private void renderUpstreamToScreen() {
    for (ViewPort viewPort : upstreamViewports) {
      viewPort.setOutputFrameBuffer(null);
    }
  }
}
