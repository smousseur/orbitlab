package com.smousseur.orbitlab.simulation.mission;

import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizerResult;
import java.util.Objects;
import java.util.Optional;
import org.orekit.time.AbsoluteDate;

/**
 * Groups a {@link Mission} with its optimization result and ephemeris. This is a mutable holder
 * because the optimization result and ephemeris are set asynchronously after creation.
 *
 * <p>Thread safety: volatile fields are written from the optimization thread and read from the JME
 * update thread.
 */
public final class MissionEntry {
  private final Mission mission;
  private volatile MissionOptimizerResult optimizerResult;
  private volatile MissionEphemeris ephemeris;
  private volatile boolean visible = false;
  private volatile AbsoluteDate scheduledDate;

  /**
   * Creates a new mission entry for the given mission.
   *
   * @param mission the mission to wrap
   */
  public MissionEntry(Mission mission) {
    this.mission = Objects.requireNonNull(mission, "mission");
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
}
