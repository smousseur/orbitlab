package com.smousseur.orbitlab.tools.datasetpack;

import com.smousseur.orbitlab.app.dataset.DatasetFile;
import com.smousseur.orbitlab.app.dataset.DatasetManifest;
import com.smousseur.orbitlab.app.dataset.DatasetPiece;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks a published dataset release against a manifest, the way a user's download will see it.
 *
 * <p>Every piece is downloaded from its deduced address and streamed through SHA-256, without being
 * written anywhere; its size and fingerprint are compared to the manifest. For a file published in
 * several pieces, the pieces' bytes are also chained into the whole file's digest, which proves on
 * the real release that concatenating them gives back the file. Two requests to the repository's
 * public API complete the check: the release holds exactly the manifest's pieces, nothing missing
 * and nothing more, and it is not the repository's latest release — which would send the README's
 * download link to a release without the application.
 *
 * <p>Only the wait for each response's headers is bounded; a download's body is not, however long
 * it lasts, since the largest pieces take minutes on an ordinary connection. Nothing is retried: a
 * failed check is reported, and the verification is run again.
 */
public final class DatasetVerifier {

  private static final int READ_BUFFER_BYTES = 1 << 20;

  private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(60);

  private static final ObjectMapper MAPPER = JsonMapper.builder().build();

  /**
   * One verified item.
   *
   * @param subject what was checked: a piece, a file, or the release
   * @param ok whether it matches the manifest
   * @param detail what was measured, and what differs when it does not match
   */
  public record Check(String subject, boolean ok, String detail) {}

  /**
   * Everything a verification checked.
   *
   * @param checks the checks, in the order they were made
   */
  public record Report(List<Check> checks) {

    /** Keeps an immutable copy of the checks. */
    public Report {
      checks = List.copyOf(checks);
    }

    /**
     * Tells whether the release matches the manifest.
     *
     * @return true when every check passed
     */
    public boolean ok() {
      return checks.stream().allMatch(Check::ok);
    }

    /**
     * Counts the failed checks.
     *
     * @return the number of checks that did not pass
     */
    public long failures() {
      return checks.stream().filter(check -> !check.ok()).count();
    }
  }

  private final HttpClient client;
  private final URI downloadBase;
  private final URI repositoryApi;
  private final Duration responseTimeout;

  /**
   * Creates a verifier.
   *
   * @param client the HTTP client, which must follow redirects: release files are served from
   *     another host than their address
   * @param downloadBase the folder holding one sub-folder per tag, ending with {@code /}
   * @param repositoryApi the repository's API address, ending with {@code /}
   */
  public DatasetVerifier(HttpClient client, URI downloadBase, URI repositoryApi) {
    this(client, downloadBase, repositoryApi, RESPONSE_TIMEOUT);
  }

  /**
   * Creates a verifier with another bound on the wait for a response.
   *
   * @param client the HTTP client, which must follow redirects
   * @param downloadBase the folder holding one sub-folder per tag, ending with {@code /}
   * @param repositoryApi the repository's API address, ending with {@code /}
   * @param responseTimeout how long to wait for a response's headers; a download's body, however
   *     long, is not bounded
   */
  DatasetVerifier(
      HttpClient client, URI downloadBase, URI repositoryApi, Duration responseTimeout) {
    this.client = Objects.requireNonNull(client, "client");
    this.downloadBase = Objects.requireNonNull(downloadBase, "downloadBase");
    this.repositoryApi = Objects.requireNonNull(repositoryApi, "repositoryApi");
    this.responseTimeout = Objects.requireNonNull(responseTimeout, "responseTimeout");
  }

  /**
   * Verifies the release a manifest describes.
   *
   * @param manifest the manifest the release must match
   * @return every check made
   * @throws InterruptedException if the thread is interrupted while waiting for a response
   */
  public Report verify(DatasetManifest manifest) throws InterruptedException {
    List<Check> checks = new ArrayList<>();
    checks.add(checkReleaseFiles(manifest));
    checks.add(checkNotLatest(manifest));
    for (DatasetFile file : manifest.files()) {
      MessageDigest whole = Sha256.newDigest();
      long wholeBytes = 0;
      for (DatasetPiece piece : file.pieces()) {
        PieceResult result = download(manifest, piece, whole);
        checks.add(result.check());
        wholeBytes += result.bytes();
      }
      if (file.pieces().size() > 1) {
        String sha = Sha256.hex(whole);
        boolean ok = wholeBytes == file.size() && sha.equals(file.sha256());
        checks.add(
            new Check(
                file.path() + " (pieces joined)",
                ok,
                String.format(Locale.ROOT, "%,d bytes, sha256 %s", wholeBytes, sha)
                    + (ok ? "" : mismatch(file.size(), file.sha256()))));
      }
    }
    return new Report(checks);
  }

  private record PieceResult(Check check, long bytes) {}

  private PieceResult download(DatasetManifest manifest, DatasetPiece piece, MessageDigest whole)
      throws InterruptedException {
    URI uri = manifest.pieceUri(downloadBase, piece);
    long started = System.nanoTime();
    long bytes = 0;
    // Not HttpRequest.timeout: once a request is redirected, as every GitHub release download is,
    // Java 21's client keeps that timer running through the body and cuts any download that lasts
    // longer. Only the wait for the response headers is bounded here.
    CompletableFuture<HttpResponse<InputStream>> pending =
        client.sendAsync(
            HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
    HttpResponse<InputStream> response;
    try {
      response = pending.get(responseTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      pending.cancel(true);
      return new PieceResult(
          new Check(
              piece.name(),
              false,
              "no response from " + uri + " within " + responseTimeout.toSeconds() + " s"),
          0);
    } catch (ExecutionException e) {
      return new PieceResult(
          new Check(piece.name(), false, "request failed: " + describe(e.getCause())), 0);
    }
    try (InputStream body = response.body()) {
      if (response.statusCode() != 200) {
        return new PieceResult(
            new Check(piece.name(), false, "HTTP " + response.statusCode() + " from " + uri), 0);
      }
      MessageDigest digest = Sha256.newDigest();
      byte[] buffer = new byte[READ_BUFFER_BYTES];
      int read = body.read(buffer);
      while (read >= 0) {
        digest.update(buffer, 0, read);
        whole.update(buffer, 0, read);
        bytes += read;
        read = body.read(buffer);
      }
      double seconds = (System.nanoTime() - started) / 1e9;
      String sha = Sha256.hex(digest);
      boolean ok = bytes == piece.size() && sha.equals(piece.sha256());
      String detail =
          String.format(
                  Locale.ROOT,
                  "%,d bytes in %.1f s (%.1f MiB/s), sha256 %s",
                  bytes,
                  seconds,
                  bytes / (1024.0 * 1024.0) / Math.max(seconds, 1e-9),
                  sha)
              + (ok ? "" : mismatch(piece.size(), piece.sha256()));
      return new PieceResult(new Check(piece.name(), ok, detail), bytes);
    } catch (IOException e) {
      return new PieceResult(
          new Check(
              piece.name(), false, "download failed after " + bytes + " bytes: " + describe(e)),
          bytes);
    }
  }

  /**
   * An exception and its cause: the JDK's response stream reports every failure as "closed", with
   * what actually happened only in the cause.
   */
  private static String describe(Throwable e) {
    return e.getCause() == null ? e.toString() : e + " (caused by " + e.getCause() + ")";
  }

  private Check checkReleaseFiles(DatasetManifest manifest) throws InterruptedException {
    String subject = "release " + manifest.tag() + " files";
    URI uri = repositoryApi.resolve("releases/tags/" + manifest.tag());
    JsonNode release;
    try {
      ApiResponse response = getJson(uri);
      if (response.status() != 200) {
        return new Check(subject, false, "HTTP " + response.status() + " from " + uri);
      }
      release = response.json();
    } catch (IOException e) {
      return new Check(subject, false, "cannot read " + uri + ": " + describe(e));
    }
    Map<String, Long> published = new LinkedHashMap<>();
    for (JsonNode asset : release.path("assets")) {
      published.put(asset.path("name").asString(), asset.path("size").asLong(-1));
    }
    Map<String, Long> expected = new LinkedHashMap<>();
    for (DatasetFile file : manifest.files()) {
      for (DatasetPiece piece : file.pieces()) {
        expected.put(piece.name(), piece.size());
      }
    }
    List<String> differences = new ArrayList<>();
    for (Map.Entry<String, Long> entry : expected.entrySet()) {
      Long size = published.get(entry.getKey());
      if (size == null) {
        differences.add("missing " + entry.getKey());
      } else if (!size.equals(entry.getValue())) {
        differences.add(entry.getKey() + " is " + size + " bytes, not " + entry.getValue());
      }
    }
    for (String name : published.keySet()) {
      if (!expected.containsKey(name)) {
        differences.add("not in the manifest: " + name);
      }
    }
    return differences.isEmpty()
        ? new Check(subject, true, published.size() + " files, as in the manifest")
        : new Check(subject, false, String.join("; ", differences));
  }

  private Check checkNotLatest(DatasetManifest manifest) throws InterruptedException {
    String subject = "latest release";
    URI uri = repositoryApi.resolve("releases/latest");
    try {
      ApiResponse response = getJson(uri);
      if (response.status() == 404) {
        return new Check(subject, true, "the repository has no latest release");
      }
      if (response.status() != 200) {
        return new Check(subject, false, "HTTP " + response.status() + " from " + uri);
      }
      String latest = response.json().path("tag_name").asString();
      return manifest.tag().equals(latest)
          ? new Check(subject, false, manifest.tag() + " is the latest release of the repository")
          : new Check(subject, true, latest + ", not " + manifest.tag());
    } catch (IOException e) {
      return new Check(subject, false, "cannot read " + uri + ": " + describe(e));
    }
  }

  private record ApiResponse(int status, JsonNode json) {}

  /** A small JSON answer, not redirected: a request timeout bounds the whole exchange safely. */
  private ApiResponse getJson(URI uri) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(responseTimeout)
            .header("Accept", "application/vnd.github+json")
            .GET()
            .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      return new ApiResponse(response.statusCode(), null);
    }
    try {
      return new ApiResponse(200, MAPPER.readTree(response.body()));
    } catch (JacksonException e) {
      throw new IOException("unreadable JSON: " + e.getMessage(), e);
    }
  }

  private static String mismatch(long size, String sha256) {
    return String.format(Locale.ROOT, "; expected %,d bytes, sha256 %s", size, sha256);
  }
}
