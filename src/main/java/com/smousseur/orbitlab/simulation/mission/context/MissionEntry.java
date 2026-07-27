package com.smousseur.orbitlab.simulation.mission.context;

import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.operation.MissionComposer;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizerResult;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.time.AbsoluteDate;

/**
 * Groups a {@link Mission} with its optimization result and ephemeris. This is a mutable holder
 * because the optimization result and ephemeris are set asynchronously after creation.
 *
 * <p>Thread safety: volatile fields are written from the optimization thread and read from the JME
 * update thread.
 */
public final class MissionEntry {
  private static final Logger logger = LogManager.getLogger(MissionEntry.class);

  // Non-null only when the entry was built from a spec: that is what lets setOptimizationType
  // recompose the mission for a new mode. Entries wrapping a pre-built mission (legacy path) leave
  // it null and cannot recompose.
  private final MissionSpec spec;
  // Volatile + non-final: replaced on the JME thread when the mode toggles, read on the
  // mission-optimizer thread.
  private volatile Mission mission;
  private volatile OptimizationType optimizationType = OptimizationType.FAST;
  private volatile MissionOptimizerResult optimizerResult;
  private volatile MissionEphemeris ephemeris;
  private volatile boolean visible = false;
  private volatile AbsoluteDate scheduledDate;
  private volatile ColorRGBA color;

  /**
   * Creates a mission entry from a spec, composing the mission for the default {@link
   * OptimizationType#FAST} mode. This is the path that supports recomposition when the mode
   * toggles.
   *
   * @param spec the mission spec (targets, vehicle, site)
   */
  public MissionEntry(MissionSpec spec) {
    this.spec = Objects.requireNonNull(spec, "spec");
    this.mission = MissionComposer.compose(spec, optimizationType);
  }

  /**
   * Creates a mission entry wrapping a pre-built mission (legacy path). The entry carries no spec,
   * so {@link #setOptimizationType(OptimizationType)} records the mode but cannot recompose the
   * stages.
   *
   * @param mission the mission to wrap
   */
  public MissionEntry(Mission mission) {
    this.spec = null;
    this.mission = Objects.requireNonNull(mission, "mission");
  }

  /**
   * Returns the spec this entry was built from, if any.
   *
   * @return an optional containing the spec, or empty for legacy pre-built-mission entries
   */
  public Optional<MissionSpec> spec() {
    return Optional.ofNullable(spec);
  }

  /**
   * Returns the wrapped mission.
   *
   * @return the mission
   */
  public Mission mission() {
    return mission;
  }

  /**
   * Replaces the wrapped mission with the one actually flown by the optimizer. In PRECISE the
   * propellant-sizing sweep flies internal missions at scaled loads; adopting the winning one keeps
   * {@code mission()} consistent with the rendered ephemeris for consumers that read mission-level
   * data (vehicle loads, solved stages) rather than the trajectory. The spec is unchanged, so a
   * later mode toggle still recomposes from the original budgeted loads.
   *
   * @param mission the flown mission to adopt
   */
  public void setMission(Mission mission) {
    this.mission = Objects.requireNonNull(mission, "mission");
  }

  /**
   * Sets the optimization result after the optimizer completes.
   *
   * @param result the optimization result
   */
  public void setOptimizerResult(MissionOptimizerResult result) {
    this.optimizerResult = result;
  }

  /**
   * Returns the optimization result, if available.
   *
   * @return an optional containing the optimizer result, or empty if not yet optimized
   */
  public Optional<MissionOptimizerResult> getOptimizerResult() {
    return Optional.ofNullable(optimizerResult);
  }

  /**
   * Returns the pre-computed mission ephemeris, if available.
   *
   * @return an optional containing the ephemeris, or empty if not yet computed
   */
  public Optional<MissionEphemeris> getEphemeris() {
    return Optional.ofNullable(ephemeris);
  }

  /**
   * Sets the pre-computed mission ephemeris.
   *
   * @param ephemeris the ephemeris to store
   */
  public void setEphemeris(MissionEphemeris ephemeris) {
    this.ephemeris = ephemeris;
  }

  /**
   * Returns whether this mission should be visible in the 3D scene.
   *
   * @return {@code true} if the mission is visible
   */
  public boolean isVisible() {
    return visible;
  }

  /**
   * Sets whether this mission should be visible in the 3D scene.
   *
   * @param visible {@code true} to show the mission
   */
  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  /**
   * Sets the planned launch date for this mission.
   *
   * @param date the planned launch date
   */
  public void setScheduledDate(AbsoluteDate date) {
    this.scheduledDate = date;
  }

  /**
   * Returns the planned launch date, if set.
   *
   * @return an optional containing the scheduled date, or empty if not yet defined
   */
  public Optional<AbsoluteDate> getScheduledDate() {
    return Optional.ofNullable(scheduledDate);
  }

  /**
   * Returns the color used to render this mission's trajectory and spacecraft.
   *
   * @return the color, or {@code null} if not yet assigned
   */
  public ColorRGBA getColor() {
    return color;
  }

  /**
   * Sets the color used to render this mission's trajectory and spacecraft.
   *
   * @param color the color to assign
   */
  public void setColor(ColorRGBA color) {
    this.color = color;
  }

  /**
   * Gets optimization type.
   *
   * @return the optimization type
   */
  public OptimizationType getOptimizationType() {
    return optimizationType;
  }

  /**
   * Sets the optimization type. When the mode actually changes and this entry carries a {@link
   * MissionSpec}, the mission is recomposed for the new mode and any previous computation is
   * invalidated: the recomposed mission starts in {@code DRAFT}, so status-gated consumers stop
   * rendering it until it is recomputed (via a fresh {@code OPTIMIZE} action).
   *
   * <p>This runs on the JME update thread (the panel's mode control calls it directly), so a
   * composition failure must not escape: the toggle is rolled back — previous mission, previous
   * mode, previous result and ephemeris all kept — and the failure is surfaced by marking the
   * retained mission {@code FAILED}, the same signal the orchestrator uses when a computation
   * blows up. A fresh {@code OPTIMIZE} clears it. Mirrors the {@code RuntimeException} net around
   * composition in {@code MissionWizardAppState.createMission()}.
   *
   * @param optimizationType the optimization type
   */
  public void setOptimizationType(OptimizationType optimizationType) {
    Objects.requireNonNull(optimizationType, "optimizationType");
    if (optimizationType == this.optimizationType) {
      return;
    }
    if (spec == null) {
      // Legacy entry: nothing to recompose, the mode is only recorded.
      this.optimizationType = optimizationType;
      return;
    }
    Mission recomposed;
    try {
      recomposed = MissionComposer.compose(spec, optimizationType);
    } catch (RuntimeException e) {
      logger.error(
          "Mode switch to {} failed for mission '{}', keeping mode {}: {}",
          optimizationType,
          mission.getName(),
          this.optimizationType,
          e.getMessage(),
          e);
      mission.setStatus(MissionStatus.FAILED);
      return;
    }
    // Published only once composition succeeded, so a failed toggle leaves a coherent entry.
    this.optimizationType = optimizationType;
    this.mission = recomposed;
    this.optimizerResult = null;
    this.ephemeris = null;
  }
}
