package com.smousseur.orbitlab.tools.datasetpack;

import com.smousseur.orbitlab.app.dataset.DatasetManifest;
import com.smousseur.orbitlab.app.dataset.DatasetManifestCodec;
import com.smousseur.orbitlab.core.OrbitlabException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Checks the published dataset release against the manifest embedded in this build.
 *
 * <p>Run through the Gradle task, once the release is published:
 *
 * <pre>
 *   ./gradlew datasetVerify
 * </pre>
 *
 * <p>The whole release is downloaded, about as much data as a user's first launch, but nothing is
 * written to disk. The process ends with an exception, and the task fails, if any check does.
 */
public final class DatasetVerifyMain {

  private static final Logger LOGGER = LogManager.getLogger(DatasetVerifyMain.class);

  /** The public API of the repository the dataset is released from. */
  static final URI GITHUB_REPOSITORY_API =
      URI.create("https://api.github.com/repos/smousseur/orbitlab/");

  private DatasetVerifyMain() {}

  /**
   * Entry point.
   *
   * @param args none
   * @throws InterruptedException if the thread is interrupted while downloading
   */
  public static void main(String[] args) throws InterruptedException {
    DatasetManifest manifest = DatasetManifestCodec.readEmbedded();
    LOGGER.info(
        String.format(
            Locale.ROOT,
            "Verifying release %s: %d files, %,d bytes",
            manifest.tag(),
            manifest.files().size(),
            manifest.totalSize()));
    DatasetVerifier.Report report;
    try (HttpClient client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build()) {
      report =
          new DatasetVerifier(client, DatasetManifest.GITHUB_DOWNLOAD_BASE, GITHUB_REPOSITORY_API)
              .verify(manifest);
    }
    for (DatasetVerifier.Check check : report.checks()) {
      LOGGER.info("{}  {}  {}", check.ok() ? "OK" : "KO", check.subject(), check.detail());
    }
    if (!report.ok()) {
      throw new OrbitlabException(
          "Release "
              + manifest.tag()
              + " does not match the embedded manifest: "
              + report.failures()
              + " of "
              + report.checks().size()
              + " checks failed");
    }
    LOGGER.info(
        "Release {} matches the embedded manifest: {} checks passed",
        manifest.tag(),
        report.checks().size());
  }
}
