package com.smousseur.orbitlab.simulation.mission;

import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizerResult;
import java.util.Objects;
import java.util.Optional;
import org.orekit.time.AbsoluteDate;

/**
 * Groups a {@link Mission} with its optimization result and runtime preparation state. This is a
 * mutable holder because the optimization result is set asynchronously after creation.
 *
 * <p>Thread safety: {@code optimizerResult} and {@code playerPrepared} are {@code volatile} because
 * they are written from the optimization thread and read from the JME update thread.
 */
public final class MissionEntry {
  private final Mission mission;
  private volatile MissionOptimizerResult optimizerResult;
  private volatile boolean playerPrepared;
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
   * Returns whether the mission player has been prepared (optimization results injected into
   * stages).
   *
   * @return {@code true} if player preparation is complete
   */
  public boolean isPlayerPrepared() {
    return playerPrepared;
  }

  /**
   * Marks whether the mission player has been prepared.
   *
   * @param prepared {@code true} if player preparation is complete
   */
  public void setPlayerPrepared(boolean prepared) {
    this.playerPrepared = prepared;
  }

  /**
   * Sets the planned launch date for this mission. If not set before optimization, it will be
   * defaulted to the current simulation clock time at the moment optimization is submitted.
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
