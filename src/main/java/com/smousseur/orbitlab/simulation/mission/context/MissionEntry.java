package com.smousseur.orbitlab.simulation.mission.context;

import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.progress.MissionProgress;
import com.smousseur.orbitlab.simulation.mission.operation.MissionComposer;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.runtime.AchievedOrbit;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizerResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionPerformanceReport;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionSolutions;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.time.AbsoluteDate;

/**
 * Groups a {@link Mission} with its optimization result and ephemeris. This is a mutable holder
 * because the optimization result and ephemeris are set asynchronously after creation.
 *
 * <p>This is also where mission <em>identity</em> lives: the entry is the only object whose
 * lifetime matches "the mission as the user manages it". Both the {@link Mission} and the {@link
 * MissionSpec} are replaced or copied during a run — see {@link MissionId} for why an id on either
 * would be unstable. Everything that designates a mission (registries, events, UI callbacks) keys
 * on {@link #id()}; the mission name is a display label and may be duplicated.
 *
 * <p>Thread safety: volatile fields are written from the optimization thread and read from the JME
 * update thread.
 */
public final class MissionEntry {
  private static final Logger logger = LogManager.getLogger(MissionEntry.class);

  private final MissionId id = MissionId.newId();
  private volatile MissionSpec spec;
  private volatile Mission mission;
  private volatile OptimizationType optimizationType = OptimizationType.FAST;
  private volatile MissionOptimizerResult optimizerResult;
  private volatile MissionEphemeris ephemeris;
  private volatile AchievedOrbit achievedOrbit;
  private volatile MissionPerformanceReport performanceReport;
  private volatile String lastError;
  private volatile MissionProgress progress;
  private volatile boolean visible = false;
  private volatile AbsoluteDate scheduledDate;
  private volatile ColorRGBA color;

  /**
   * How many times {@link #mission} has been replaced. Read by the UI as a "everything derived from
   * the mission is stale" marker: a wizard edit or a mode toggle rebuilds the name, the type, the
   * launch site and the whole stage chain at once, and none of those show up in the status,
   * visibility or result fields a panel would otherwise watch. Enumerating the changed fields
   * instead would have to be extended every time a panel renders one more of them.
   */
  private final AtomicInteger compositionRevision = new AtomicInteger();

  /**
   * The per-stage launcher loads the last computation actually flew (kg), or {@code null} when they
   * were the budgeted ones. Only a {@code PRECISE} sizing sweep <em>searches</em> for loads; in
   * every other mode {@code PropellantBudget} derives them and recomputing costs nothing, so there
   * is nothing worth remembering (spec {@code docs/scenario/01-persistance-missions.md} §2.3).
   */
  private volatile double[] flownLauncherLoads;

  /**
   * Solved vectors waiting to be flown instead of searched for, posted by a scenario load and read
   * by {@code MissionPlanOptimizer} on the next computation. {@code null} for every mission that
   * was not loaded from a file.
   */
  private volatile MissionSolutions pendingSolutions;

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
   * Returns this mission's stable identity. Unique across installations and unchanged for the whole
   * life of the entry, including across optimization-mode toggles and propellant-sizing sweeps.
   *
   * @return the mission id
   */
  public MissionId id() {
    return id;
  }

  /**
   * Returns the spec this entry currently flies — the one it was built from, or the last one {@link
   * #applySpec(MissionSpec)} accepted. Its presence is also what makes the entry editable at all.
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
    compositionRevision.incrementAndGet();
  }

  /**
   * How many times the wrapped mission has been replaced — by a wizard edit, a mode toggle, or the
   * adoption of the mission a sizing sweep actually flew. A UI that caches anything read off {@link
   * #mission()} or {@link #spec()} can compare this against the value it drew with to know its copy
   * is stale, without watching each displayed field individually.
   *
   * @return the composition revision, starting at 0
   */
  public int compositionRevision() {
    return compositionRevision.get();
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
   * Returns the orbit the last computation achieved, in both conventions.
   *
   * @return an optional containing the achieved orbit, or empty when nothing was computed
   */
  public Optional<AchievedOrbit> getAchievedOrbit() {
    return Optional.ofNullable(achievedOrbit);
  }

  /**
   * Stores the orbit reported by the computation.
   *
   * @param achievedOrbit the achieved orbit
   */
  public void setAchievedOrbit(AchievedOrbit achievedOrbit) {
    this.achievedOrbit = achievedOrbit;
  }

  /**
   * Returns the mass and ΔV accounting of the last computation.
   *
   * @return an optional containing the report, or empty when nothing was computed
   */
  public Optional<MissionPerformanceReport> getPerformanceReport() {
    return Optional.ofNullable(performanceReport);
  }

  /**
   * Stores the performance report produced by the computation.
   *
   * @param performanceReport the report
   */
  public void setPerformanceReport(MissionPerformanceReport performanceReport) {
    this.performanceReport = performanceReport;
  }

  /**
   * Returns the failure that put this mission in {@code FAILED}, already formatted for display.
   *
   * @return an optional containing the message, or empty when the last attempt did not fail
   */
  public Optional<String> getLastError() {
    return Optional.ofNullable(lastError);
  }

  /**
   * Records a failure message. Callers format it through {@link #describeFailure(Throwable)}.
   *
   * @param lastError the message to record
   */
  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  /** Forgets the previous failure, so a relaunched computation does not display a stale error. */
  public void clearLastError() {
    this.lastError = null;
  }

  /**
   * Returns the advancement of the computation currently attached to this mission.
   *
   * <p>Absent whenever no computation is attached — and legitimately absent for an instant
   * <em>during</em> one, since the orchestrator clears it after the terminal status is published.
   * A reader seeing {@code COMPUTING} with no progress must fall back rather than wait.
   *
   * @return an optional containing the live progress, or empty
   */
  public Optional<MissionProgress> getProgress() {
    return Optional.ofNullable(progress);
  }

  /**
   * Attaches or detaches the advancement of a computation.
   *
   * @param progress the progress to attach, or {@code null} to detach
   */
  public void setProgress(MissionProgress progress) {
    this.progress = progress;
  }

  /**
   * Formats a failure for display, identically wherever it is caught.
   *
   * <p>Deliberately the raw exception rather than a table of friendly messages: a mapping keyed on
   * exception classes would be an association resolved at runtime, and a case that drifted out of
   * sync would state something false about a mission. The raw text is never wrong.
   *
   * @param e the failure
   * @return {@code "SimpleName: message"}, or the exception's {@code toString()} when it carries no
   *     message — Orekit rebuilds exceptions without one often enough to matter
   */
  public static String describeFailure(Throwable e) {
    String message = e.getMessage();
    return message == null || message.isBlank()
        ? e.toString()
        : e.getClass().getSimpleName() + ": " + message;
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
   * Returns the per-stage launcher loads the last computation flew, when they were searched for
   * rather than derived.
   *
   * @return the flown loads in kilograms, or {@code null} outside {@code PRECISE}
   */
  public double[] getFlownLauncherLoads() {
    double[] loads = flownLauncherLoads;
    return loads == null ? null : loads.clone();
  }

  /**
   * Records the loads a sizing sweep resolved, so a scenario can be saved on the vehicle that
   * actually flew rather than on the one the budget would rebuild today.
   *
   * @param flownLauncherLoads the flown loads in kilograms, or {@code null} to forget them
   */
  public void setFlownLauncherLoads(double[] flownLauncherLoads) {
    this.flownLauncherLoads = flownLauncherLoads == null ? null : flownLauncherLoads.clone();
  }

  /**
   * Returns the solutions a scenario load left for the next computation to fly.
   *
   * @return an optional containing the pending solutions, or empty for an ordinary mission
   */
  public Optional<MissionSolutions> getPendingSolutions() {
    return Optional.ofNullable(pendingSolutions);
  }

  /**
   * Posts solutions to be replayed instead of searched for on the next computation.
   *
   * <p>Set after the mode has been applied, never before: {@link
   * #setOptimizationType(OptimizationType)} recomposes and therefore drops them, together with
   * everything else the previous composition produced.
   *
   * @param pendingSolutions the solutions to replay, or {@code null} to drop them
   */
  public void setPendingSolutions(MissionSolutions pendingSolutions) {
    this.pendingSolutions = pendingSolutions;
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
   * retained mission {@code FAILED}, the same signal the orchestrator uses when a computation blows
   * up. A fresh {@code OPTIMIZE} clears it. Mirrors the {@code RuntimeException} net around
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
    Mission recomposed = recompose(spec, optimizationType, "Mode switch to " + optimizationType);
    if (recomposed == null) {
      return;
    }
    // Published only once composition succeeded, so a failed toggle leaves a coherent entry.
    this.optimizationType = optimizationType;
    publish(recomposed);
  }

  /**
   * Replaces the spec this entry was built from and recomposes the mission for the current mode.
   * This is the wizard's edit path: the user reopens a mission, changes its targets, vehicle or
   * launch site, and the entry keeps its identity — {@link #id()}, color, visibility and telemetry
   * focus all survive — while everything derived from the previous spec is dropped, exactly as a
   * mode toggle does.
   *
   * <p>The mission type is not editable (the wizard locks the type step in edit mode), so a spec of
   * another type is a programming error rather than something a user can do.
   *
   * <p>Same contract as {@link #setOptimizationType(OptimizationType)} on failure: this runs on the
   * JME update thread, so a composition failure must not escape. The previous spec, mission, result
   * and ephemeris are all kept and the retained mission is marked {@code FAILED}.
   *
   * @param spec the edited spec, of the same {@link MissionSpec#type() type} as the current one
   * @return {@code true} when the mission was recomposed, {@code false} when composition failed
   * @throws IllegalStateException if this entry carries no spec (legacy entry)
   * @throws IllegalArgumentException if the given spec changes the mission type
   */
  public boolean applySpec(MissionSpec spec) {
    Objects.requireNonNull(spec, "spec");
    MissionSpec current = this.spec;
    if (current == null) {
      throw new IllegalStateException(
          "Mission [" + id.shortForm() + "] carries no spec and cannot be edited");
    }
    if (spec.type() != current.type()) {
      throw new IllegalArgumentException(
          "Mission type is not editable: " + current.type() + " -> " + spec.type());
    }
    Mission recomposed = recompose(spec, optimizationType, "Edit");
    if (recomposed == null) {
      return false;
    }
    this.spec = spec;
    publish(recomposed);
    return true;
  }

  /**
   * Composes {@code spec} for {@code mode}, absorbing a failure rather than letting it escape to
   * the render loop.
   *
   * @param change what was being applied, for the failure log ("Edit", "Mode switch to PRECISE")
   * @return the recomposed mission, or {@code null} when composition failed
   */
  private Mission recompose(MissionSpec spec, OptimizationType mode, String change) {
    try {
      return MissionComposer.compose(spec, mode);
    } catch (RuntimeException e) {
      logger.error(
          "{} failed for mission '{}' [{}], keeping the previous composition (mode {}): {}",
          change,
          mission.getName(),
          id.shortForm(),
          optimizationType,
          e.getMessage(),
          e);
      this.lastError = describeFailure(e);
      mission.setStatus(MissionStatus.FAILED);
      return null;
    }
  }

  /**
   * Adopts a freshly composed mission and invalidates everything the previous one produced. The
   * recomposed mission starts in {@code DRAFT}, so status-gated consumers stop rendering it until a
   * fresh {@code OPTIMIZE} recomputes it — and the detail view must not keep reporting an orbit
   * that is no longer flown, nor an error from a composition that no longer exists.
   */
  private void publish(Mission recomposed) {
    this.mission = recomposed;
    compositionRevision.incrementAndGet();
    this.optimizerResult = null;
    this.ephemeris = null;
    this.achievedOrbit = null;
    this.performanceReport = null;
    this.lastError = null;
    // Both derive from the composition being replaced. Keeping them would replay the vectors of a
    // composition that no longer exists — and MissionSolutions.covers() would not always catch it,
    // since two compositions can share their stage keys.
    this.flownLauncherLoads = null;
    this.pendingSolutions = null;
  }
}
