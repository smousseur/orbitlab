package com.smousseur.orbitlab.tools.datasetpack;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.app.dataset.DatasetFile;
import com.smousseur.orbitlab.app.dataset.DatasetManifest;
import com.smousseur.orbitlab.app.dataset.DatasetPiece;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The release check against a local server shaped like GitHub: every download address answers a
 * {@code 302} to where the file really is, and a small API lists the release's files and names the
 * latest release. A release that matches passes; a changed byte, a missing file, a file too many or
 * a data release marked latest each fail.
 */
class DatasetVerifierTest {

  private static final String TAG = "dataset-v1";

  private final Map<String, byte[]> served = new LinkedHashMap<>();
  private final List<String> listed = new ArrayList<>();
  private String latestTag = "v2.0.0";
  private HttpServer server;
  private URI base;
  private DatasetManifest manifest;

  @BeforeEach
  void startServer() throws IOException {
    Random random = new Random(7);
    byte[] whole = new byte[300];
    random.nextBytes(whole);
    byte[] split = new byte[201];
    random.nextBytes(split);
    byte[] first = Arrays.copyOfRange(split, 0, 101);
    byte[] second = Arrays.copyOfRange(split, 101, 201);
    served.put("X.bin", whole);
    served.put("Y.bin.001", first);
    served.put("Y.bin.002", second);
    listed.addAll(served.keySet());
    manifest =
        new DatasetManifest(
            DatasetManifest.CURRENT_FORMAT_VERSION,
            TAG,
            List.of(
                new DatasetFile(
                    "ephemeris/X.bin",
                    300,
                    sha256(whole),
                    List.of(new DatasetPiece("X.bin", 300, sha256(whole)))),
                new DatasetFile(
                    "ephemeris/Y.bin",
                    201,
                    sha256(split),
                    List.of(
                        new DatasetPiece("Y.bin.001", 101, sha256(first)),
                        new DatasetPiece("Y.bin.002", 100, sha256(second))))));

    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    server.createContext("/download/", this::redirect);
    server.createContext("/assets/", this::serveAsset);
    server.createContext("/api/releases/tags/", this::serveRelease);
    server.createContext("/api/releases/latest", this::serveLatest);
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private static String sha256(byte[] bytes) {
    MessageDigest digest = Sha256.newDigest();
    digest.update(bytes);
    return Sha256.hex(digest);
  }

  private static String lastSegment(HttpExchange exchange) {
    String path = exchange.getRequestURI().getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }

  private void redirect(HttpExchange exchange) throws IOException {
    exchange.getResponseHeaders().add("Location", base + "assets/" + lastSegment(exchange));
    exchange.sendResponseHeaders(302, -1);
    exchange.close();
  }

  private void serveAsset(HttpExchange exchange) throws IOException {
    byte[] body = served.get(lastSegment(exchange));
    if (body == null) {
      exchange.sendResponseHeaders(404, -1);
    } else {
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    }
    exchange.close();
  }

  private void serveRelease(HttpExchange exchange) throws IOException {
    List<String> assets = new ArrayList<>();
    for (String name : listed) {
      byte[] body = served.get(name);
      assets.add("{\"name\":\"" + name + "\",\"size\":" + (body == null ? 1 : body.length) + "}");
    }
    sendJson(
        exchange,
        "{\"tag_name\":\""
            + lastSegment(exchange)
            + "\",\"assets\":["
            + String.join(",", assets)
            + "]}");
  }

  private void serveLatest(HttpExchange exchange) throws IOException {
    sendJson(exchange, "{\"tag_name\":\"" + latestTag + "\"}");
  }

  private static void sendJson(HttpExchange exchange, String json) throws IOException {
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
    exchange.close();
  }

  private DatasetVerifier.Report verify() throws InterruptedException {
    try (HttpClient client =
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
      return new DatasetVerifier(client, base.resolve("download/"), base.resolve("api/"))
          .verify(manifest);
    }
  }

  private static DatasetVerifier.Check check(DatasetVerifier.Report report, String subject) {
    return report.checks().stream()
        .filter(c -> c.subject().equals(subject))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no check for " + subject + ": " + report));
  }

  @Test
  void passesAReleaseThatMatchesTheManifestThroughRedirects() throws InterruptedException {
    DatasetVerifier.Report report = verify();

    assertTrue(report.ok(), report.toString());
    assertEquals(6, report.checks().size(), report.toString());
    assertTrue(check(report, "ephemeris/Y.bin (pieces joined)").ok());
  }

  @Test
  void failsAPieceAndItsJoinedFileWhenAByteDiffers() throws InterruptedException {
    served.get("Y.bin.002")[50] ^= 1;

    DatasetVerifier.Report report = verify();

    assertFalse(report.ok());
    assertTrue(check(report, "X.bin").ok());
    assertTrue(check(report, "Y.bin.001").ok());
    assertFalse(check(report, "Y.bin.002").ok());
    assertFalse(check(report, "ephemeris/Y.bin (pieces joined)").ok());
    assertEquals(2, report.failures(), report.toString());
  }

  @Test
  void failsAPieceTheReleaseDoesNotServe() throws InterruptedException {
    served.remove("X.bin");

    DatasetVerifier.Report report = verify();

    DatasetVerifier.Check missing = check(report, "X.bin");
    assertFalse(missing.ok());
    assertTrue(missing.detail().contains("HTTP 404"), missing.detail());
  }

  @Test
  void failsAReleaseListingAFileTooManyOrTooFew() throws InterruptedException {
    listed.add("extra.bin");
    listed.remove("Y.bin.001");

    DatasetVerifier.Check files = check(verify(), "release " + TAG + " files");

    assertFalse(files.ok());
    assertTrue(files.detail().contains("missing Y.bin.001"), files.detail());
    assertTrue(files.detail().contains("not in the manifest: extra.bin"), files.detail());
  }

  @Test
  void failsWhenTheDataReleaseIsTheLatestOne() throws InterruptedException {
    latestTag = TAG;

    DatasetVerifier.Check latest = check(verify(), "latest release");

    assertFalse(latest.ok());
  }
}
