package com.smousseur.orbitlab.app.dataset;

/**
 * Sink an installation of the dataset reports its advancement to. Called on the thread running the
 * installation, which waits for it: a listener must return quickly, and one read from another
 * thread must publish what it keeps safely, as {@link DatasetProgress} does.
 */
@FunctionalInterface
public interface DatasetInstallListener {

  /**
   * Reports what just happened.
   *
   * @param event the event
   */
  void onEvent(DatasetInstallEvent event);
}
