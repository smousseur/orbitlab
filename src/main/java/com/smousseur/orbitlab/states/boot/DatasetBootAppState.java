package com.smousseur.orbitlab.states.boot;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.AttemptFailed;
import com.smousseur.orbitlab.app.dataset.DatasetInstallOutcome;
import com.smousseur.orbitlab.app.dataset.DatasetInstaller;
import com.smousseur.orbitlab.app.dataset.DatasetProgress;
import com.smousseur.orbitlab.app.dataset.DatasetProgress.Snapshot;
import com.smousseur.orbitlab.ui.boot.DatasetBootScreen;
import com.smousseur.orbitlab.ui.boot.DatasetProgressText;
import java.time.Duration;
import java.util.Objects;

/**
 * Installs the dataset at start-up when it is not ready, behind {@link DatasetBootScreen}, then
 * starts the application.
 *
 * <p>The installation runs on a daemon thread named {@value #INSTALL_THREAD_NAME}; the render
 * thread reads its {@link DatasetProgress} once a frame and is the only one to touch the scene.
 * Each attempt, the first or one the user retries, follows a progress of its own.
 *
 * <ul>
 *   <li><b>Completed</b>: the card goes, this state detaches itself, and the start action runs at
 *       the start of the next frame, before the state manager's update, where {@code simpleInitApp}
 *       ran: the states it attaches are initialized in that same frame, as they were.
 *   <li><b>Cancel</b>: the installation is cancelled and the application stops once it has; partial
 *       downloads are kept for the next start.
 *   <li><b>Failure</b>: the card turns to its error face; Retry installs again, Quit stops.
 * </ul>
 *
 * <p>Closing the window, or ESC, which is still the application's exit key at this point, cancels
 * the installation through {@link #cleanup}.
 *
 * <p>An exception escaping the installation is a defect, not a failure the user can act on: it is
 * thrown again on the render thread, where the application reports it as it would any other.
 */
public final class DatasetBootAppState extends BaseAppState {

  /** Name of the installing thread, so a thread dump taken during the download explains itself. */
  public static final String INSTALL_THREAD_NAME = "dataset-install";

  private final DatasetInstaller installer;
  private final Node guiNode;
  private final Runnable onReady;

  private DatasetBootScreen screen;
  private DatasetProgress progress;
  private volatile RuntimeException crash;
  private DatasetInstallOutcome handled;
  private boolean cancelling;

  /** The failed attempt the speed line announces, the phase's bytes then, and when it was seen. */
  private AttemptFailed notice;

  private long noticeBytes;
  private long noticeNanos;

  /**
   * Creates the state; the installation starts when it is attached.
   *
   * @param installer the installer of this build's dataset
   * @param guiNode the GUI node to draw the card in
   * @param onReady what starts the application once the dataset is ready
   */
  public DatasetBootAppState(DatasetInstaller installer, Node guiNode, Runnable onReady) {
    this.installer = Objects.requireNonNull(installer, "installer");
    this.guiNode = Objects.requireNonNull(guiNode, "guiNode");
    this.onReady = Objects.requireNonNull(onReady, "onReady");
  }

  @Override
  protected void initialize(Application app) {
    screen = new DatasetBootScreen();
    screen.setOnCancel(this::cancel);
    screen.setOnRetry(this::start);
    screen.setOnQuit(app::stop);
    screen.attachTo(guiNode);
    start();
  }

  @Override
  public void update(float tpf) {
    RuntimeException thrown = crash;
    if (thrown != null) {
      throw new IllegalStateException("The dataset installation crashed", thrown);
    }
    Camera cam = getApplication().getCamera();
    screen.center(cam.getWidth(), cam.getHeight());
    Snapshot snapshot = progress.snapshot();
    if (snapshot.finished()) {
      end(snapshot);
    } else if (!cancelling) {
      show(snapshot);
    }
  }

  @Override
  protected void cleanup(Application app) {
    installer.cancel();
    screen.detach();
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}

  private void start() {
    handled = null;
    cancelling = false;
    notice = null;
    DatasetProgress attempt = new DatasetProgress();
    progress = attempt;
    screen.showProgress(attempt.snapshot());
    Thread thread = new Thread(() -> install(attempt), INSTALL_THREAD_NAME);
    thread.setDaemon(true);
    thread.start();
  }

  private void install(DatasetProgress attempt) {
    try {
      installer.install(attempt);
    } catch (RuntimeException e) {
      crash = e;
    }
  }

  private void cancel() {
    if (!cancelling) {
      cancelling = true;
      screen.showCancelling();
      installer.cancel();
    }
  }

  /**
   * Shows the snapshot, or the failed attempt it carries for as long as the phase's bytes stay
   * where they were when it was reported: the next attempt moving them is what ends the notice.
   */
  private void show(Snapshot snapshot) {
    AttemptFailed failure = snapshot.lastFailure();
    if (failure != notice) {
      notice = failure;
      noticeBytes = snapshot.phaseBytes();
      noticeNanos = System.nanoTime();
    }
    if (notice != null && snapshot.phaseBytes() == noticeBytes) {
      Duration since = Duration.ofNanos(System.nanoTime() - noticeNanos);
      screen.showRetry(snapshot, DatasetProgressText.retryLine(notice, since));
    } else {
      screen.showProgress(snapshot);
    }
  }

  private void end(Snapshot snapshot) {
    DatasetInstallOutcome outcome = snapshot.outcome();
    if (outcome == handled) {
      return;
    }
    handled = outcome;
    switch (outcome) {
      case DatasetInstallOutcome.Completed c -> {
        screen.detach();
        getStateManager().detach(this);
        getApplication().enqueue(onReady);
      }
      case DatasetInstallOutcome.Cancelled c -> getApplication().stop();
      case DatasetInstallOutcome.Failed f -> screen.showFailure(snapshot, f);
    }
  }
}
