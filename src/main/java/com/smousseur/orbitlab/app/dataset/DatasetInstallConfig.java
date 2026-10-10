package com.smousseur.orbitlab.app.dataset;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Where an installation downloads from, how long it waits, and how it measures free space.
 *
 * <p>There is deliberately no bound on a download's duration: the largest pieces take minutes on an
 * ordinary connection. What is bounded is the wait for a response's headers, and the silence while
 * a body flows. The response wait is applied to the future of an asynchronous request, never as the
 * request's own timeout: once a request is redirected, as every GitHub release download is, Java
 * 21's client keeps that timer running through the body and cuts any download that lasts longer.
 *
 * @param downloadBase the folder holding one sub-folder per tag, ending with {@code /}
 * @param connectTimeout how long to wait for a connection
 * @param responseTimeout how long to wait for a response's headers
 * @param stallTimeout how long a download may go without receiving a single byte before it is cut
 * @param retryWaits the waits before each new attempt, the n-th one after the n-th failure in a row
 *     without progress; one failure more than there are waits gives up
 * @param usableSpace how the free space of the volume holding a directory is measured
 */
public record DatasetInstallConfig(
    URI downloadBase,
    Duration connectTimeout,
    Duration responseTimeout,
    Duration stallTimeout,
    List<Duration> retryWaits,
    UsableSpace usableSpace) {

  /** Measures the space a directory's volume has left. */
  @FunctionalInterface
  public interface UsableSpace {

    /**
     * Measures the space left.
     *
     * @param directory a directory, which may not exist yet
     * @return the bytes the volume holding it can still store
     * @throws IOException if the volume cannot be queried
     */
    long of(Path directory) throws IOException;
  }

  /** Rejects missing components, an empty list of waits, and keeps an immutable copy of it. */
  public DatasetInstallConfig {
    Objects.requireNonNull(downloadBase, "downloadBase");
    Objects.requireNonNull(connectTimeout, "connectTimeout");
    Objects.requireNonNull(responseTimeout, "responseTimeout");
    Objects.requireNonNull(stallTimeout, "stallTimeout");
    retryWaits = List.copyOf(Objects.requireNonNull(retryWaits, "retryWaits"));
    Objects.requireNonNull(usableSpace, "usableSpace");
    if (retryWaits.isEmpty()) {
      throw new IllegalArgumentException("at least one retry wait is needed");
    }
  }

  /**
   * The configuration of the application: the published release, a 30 s connection timeout, 60 s
   * for the headers, 30 s of silence at most, and waits of 2 s then 4 s, so the third failure in a
   * row without progress gives up.
   *
   * @return the default configuration
   */
  public static DatasetInstallConfig defaults() {
    return new DatasetInstallConfig(
        DatasetManifest.GITHUB_DOWNLOAD_BASE,
        Duration.ofSeconds(30),
        Duration.ofSeconds(60),
        Duration.ofSeconds(30),
        List.of(Duration.ofSeconds(2), Duration.ofSeconds(4)),
        DatasetInstallConfig::fileStoreUsableSpace);
  }

  /**
   * How many failures in a row without progress make an installation give up.
   *
   * @return one more than the number of waits
   */
  public int maxFailuresWithoutProgress() {
    return retryWaits.size() + 1;
  }

  /**
   * The usable space of the volume holding a directory, measured on its nearest existing ancestor,
   * so measuring creates nothing.
   */
  private static long fileStoreUsableSpace(Path directory) throws IOException {
    Path existing = directory.toAbsolutePath();
    while (existing != null && !Files.exists(existing)) {
      existing = existing.getParent();
    }
    if (existing == null) {
      throw new IOException("No existing directory above " + directory);
    }
    return Files.getFileStore(existing).getUsableSpace();
  }
}
