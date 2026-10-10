package com.smousseur.orbitlab.app.dataset;

import java.util.Objects;

/** How an installation of the dataset ended. */
public sealed interface DatasetInstallOutcome {

  /** Every file is verified and the marker is written: the dataset is ready. */
  record Completed() implements DatasetInstallOutcome {}

  /**
   * The installation was cancelled. Partial downloads are kept, and the next installation resumes
   * them.
   */
  record Cancelled() implements DatasetInstallOutcome {}

  /**
   * The installation gave up.
   *
   * @param cause why, in the terms the user is told
   * @param file the file being installed when it gave up, or {@code null} when the failure is not
   *     about one file, as a lack of space measured before any download
   * @param detail what actually happened, with the underlying error when there is one
   */
  record Failed(DatasetFailureCause cause, DatasetFile file, String detail)
      implements DatasetInstallOutcome {

    /** Rejects a missing cause or detail; the file may be absent. */
    public Failed {
      Objects.requireNonNull(cause, "cause");
      Objects.requireNonNull(detail, "detail");
    }

    /**
     * Tells whether the failure is about one file.
     *
     * @return true when {@link #file()} is not null
     */
    public boolean hasFile() {
      return file != null;
    }
  }
}
