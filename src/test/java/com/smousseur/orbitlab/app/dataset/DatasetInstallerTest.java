package com.smousseur.orbitlab.app.dataset;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.AttemptFailed;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.FileCompleted;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.FileStarted;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.Finished;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.Phase;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.PhaseStarted;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The installation against a local server shaped like GitHub: every download address answers a
 * {@code 302} to where the file really is, which honours {@code Range} with a {@code 206}. Faults
 * are set per file: a range ignored, a response cut after some bytes, a silence once the headers
 * are sent, no response at all, a changed byte, a forced status, a response held until released.
 *
 * <p>The dataset has three files in four pieces: {@code A.bin}, {@code MOON.bin} in two pieces of
 * 500 bytes, and {@code B-orbit.bin}. Timeouts are in milliseconds.
 */
class DatasetInstallerTest {

  private static final String TAG = "dataset-v9";
  private static final String MOON_1 = "MOON.bin.001";
  private static final String MOON_2 = "MOON.bin.002";

  @TempDir Path root;

  private final Map<String, byte[]> served = new ConcurrentHashMap<>();
  private final List<String> requests = new CopyOnWriteArrayList<>();
  private final AtomicInteger redirects = new AtomicInteger();
  private final Set<String> ignoreRange = ConcurrentHashMap.newKeySet();
  private final Set<String> silent = ConcurrentHashMap.newKeySet();
  private final Set<String> mute = ConcurrentHashMap.newKeySet();
  private final Set<String> corrupt = ConcurrentHashMap.newKeySet();
  private final Map<String, Integer> forcedStatus = new ConcurrentHashMap<>();
  private final Map<String, Integer> cutAfter = new ConcurrentHashMap<>();
  private final Map<String, Integer> holdAfter = new ConcurrentHashMap<>();
  private final CountDownLatch release = new CountDownLatch(1);
  private final List<DatasetInstallEvent> events = new CopyOnWriteArrayList<>();

  private volatile long usableSpace = Long.MAX_VALUE;
  private Duration responseTimeout = Duration.ofSeconds(5);
  private Duration stallTimeout = Duration.ofMillis(300);

  private ExecutorService executor;
  private HttpServer server;
  private URI base;
  private byte[] a;
  private byte[] moon;
  private byte[] b;
  private DatasetManifest manifest;
  private DatasetChecker checker;

  @BeforeEach
  void startServer() throws IOException {
    Random random = new Random(5);
    a = new byte[300];
    random.nextBytes(a);
    moon = new byte[1000];
    random.nextBytes(moon);
    b = new byte[100];
    random.nextBytes(b);
    byte[] first = Arrays.copyOfRange(moon, 0, 500);
    byte[] second = Arrays.copyOfRange(moon, 500, 1000);
    served.put("A.bin", a);
    served.put(MOON_1, first);
    served.put(MOON_2, second);
    served.put("B-orbit.bin", b);
    manifest =
        new DatasetManifest(
            DatasetManifest.CURRENT_FORMAT_VERSION,
            TAG,
            List.of(
                whole("ephemeris/A.bin", a),
                new DatasetFile(
                    "ephemeris/MOON.bin",
                    1000,
                    sha256(moon),
                    List.of(
                        new DatasetPiece(MOON_1, 500, sha256(first)),
                        new DatasetPiece(MOON_2, 500, sha256(second)))),
                whole("orbits/B-orbit.bin", b)));
    checker = new DatasetChecker(manifest, root);

    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    server.createContext("/download/", this::redirect);
    server.createContext("/assets/", this::serveAsset);
    server.start();
  }

  @AfterEach
  void stopServer() {
    release.countDown();
    server.stop(0);
    executor.shutdownNow();
  }

  private static DatasetFile whole(String path, byte[] bytes) {
    String name = path.substring(path.lastIndexOf('/') + 1);
    return new DatasetFile(
        path,
        bytes.length,
        sha256(bytes),
        List.of(new DatasetPiece(name, bytes.length, sha256(bytes))));
  }

  private static String sha256(byte[] bytes) {
    MessageDigest digest = Sha256.newDigest();
    digest.update(bytes);
    return Sha256.hex(digest);
  }

  private static void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static String lastSegment(HttpExchange exchange) {
    String path = exchange.getRequestURI().getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }

  private void redirect(HttpExchange exchange) throws IOException {
    redirects.incrementAndGet();
    exchange.getResponseHeaders().add("Location", base + "assets/" + lastSegment(exchange));
    exchange.sendResponseHeaders(302, -1);
    exchange.close();
  }

  /**
   * Serves a piece, from the requested offset with a {@code 206} unless ranges are ignored for it.
   * Closing an exchange whose announced length was not written closes the connection, which the
   * client sees as a cut.
   */
  private void serveAsset(HttpExchange exchange) throws IOException {
    String name = lastSegment(exchange);
    String range = exchange.getRequestHeaders().getFirst("Range");
    requests.add(range == null ? name : name + " " + range);
    try (exchange) {
      respond(exchange, name, range);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void respond(HttpExchange exchange, String name, String range)
      throws IOException, InterruptedException {
    if (mute.contains(name)) {
      pause(3_000);
    }
    byte[] body = served.get(name);
    Integer status = forcedStatus.get(name);
    if (status != null || body == null) {
      exchange.sendResponseHeaders(status == null ? 404 : status, -1);
      return;
    }
    if (corrupt.contains(name)) {
      body = body.clone();
      body[body.length / 2] ^= 1;
    }
    int from = 0;
    if (range != null && !ignoreRange.contains(name)) {
      from = Integer.parseInt(range.substring("bytes=".length(), range.length() - 1));
      exchange
          .getResponseHeaders()
          .add("Content-Range", "bytes " + from + "-" + (body.length - 1) + "/" + body.length);
      exchange.sendResponseHeaders(206, body.length - from);
    } else {
      exchange.sendResponseHeaders(200, body.length);
    }
    try (OutputStream out = exchange.getResponseBody()) {
      if (silent.contains(name)) {
        out.flush();
        pause(3_000);
        return;
      }
      int count = body.length - from;
      Integer hold = holdAfter.get(name);
      if (hold != null && hold < count) {
        out.write(body, from, hold);
        out.flush();
        release.await(10, TimeUnit.SECONDS);
        from += hold;
        count -= hold;
      }
      int sent = Math.min(count, cutAfter.getOrDefault(name, count));
      out.write(body, from, sent);
      out.flush();
      if (sent < count) {
        // The JDK's response stream throws as soon as the cut reaches it, dropping the bytes it
        // received but the reader had not taken yet: the pause makes the bytes before a cut
        // certain.
        pause(100);
      }
    } catch (IOException cut) {
      // Closing a body shorter than its announced length cuts the connection, as intended; a
      // client that went away fails the same way.
    }
  }

  private DatasetInstaller installer() {
    return new DatasetInstaller(
        checker,
        new DatasetInstallConfig(
            base.resolve("download/"),
            Duration.ofSeconds(5),
            responseTimeout,
            stallTimeout,
            List.of(Duration.ofMillis(10), Duration.ofMillis(20)),
            directory -> usableSpace));
  }

  private DatasetInstallOutcome install() {
    return installer().install(events::add);
  }

  private void put(String path, byte[] bytes) throws IOException {
    Path target = root.resolve(path);
    Files.createDirectories(target.getParent());
    Files.write(target, bytes);
  }

  private void assertInstalled() throws IOException {
    assertArrayEquals(a, Files.readAllBytes(root.resolve("ephemeris/A.bin")));
    assertArrayEquals(moon, Files.readAllBytes(root.resolve("ephemeris/MOON.bin")));
    assertArrayEquals(b, Files.readAllBytes(root.resolve("orbits/B-orbit.bin")));
    for (DatasetFile file : manifest.files()) {
      assertFalse(Files.exists(checker.partOf(file)), file.path());
    }
    assertEquals(manifest, DatasetManifestCodec.read(Files.readString(checker.marker())));
    assertTrue(checker.check().ready());
  }

  private List<String> requestsFor(String name) {
    return requests.stream().filter(r -> r.startsWith(name)).toList();
  }

  private <T extends DatasetInstallEvent> List<T> eventsOf(Class<T> type) {
    return events.stream().filter(type::isInstance).map(type::cast).toList();
  }

  private FileStarted moonStarted() {
    return eventsOf(FileStarted.class).stream()
        .filter(e -> "ephemeris/MOON.bin".equals(e.file().path()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void installsEveryFileThroughRedirectsAndWritesTheMarker() throws IOException {
    DatasetInstallOutcome outcome = install();

    assertEquals(new DatasetInstallOutcome.Completed(), outcome);
    assertInstalled();
    assertEquals(List.of("A.bin", MOON_1, MOON_2, "B-orbit.bin"), requests);
    assertEquals(4, redirects.get());
    assertEquals(new PhaseStarted(Phase.DOWNLOADING, 3, 1400), events.getFirst());
    assertEquals(3, eventsOf(FileCompleted.class).size());
    assertEquals(new Finished(outcome), events.getLast());
    assertTrue(eventsOf(AttemptFailed.class).isEmpty());
  }

  @Test
  void resumesInsideTheFirstPieceFromThePartialDownload() throws IOException {
    put("ephemeris/MOON.bin.part", Arrays.copyOfRange(moon, 0, 200));

    assertEquals(new DatasetInstallOutcome.Completed(), install());

    assertInstalled();
    assertEquals(List.of(MOON_1 + " bytes=200-", MOON_2), requestsFor("MOON"));
    assertEquals(200, moonStarted().presentBytes());
    assertEquals(new PhaseStarted(Phase.DOWNLOADING, 3, 1200), events.getFirst());
  }

  @Test
  void resumesInsideTheSecondPieceWithoutRequestingTheFirst() throws IOException {
    put("ephemeris/MOON.bin.part", Arrays.copyOfRange(moon, 0, 650));

    assertEquals(new DatasetInstallOutcome.Completed(), install());

    assertInstalled();
    assertEquals(List.of(MOON_2 + " bytes=150-"), requestsFor("MOON"));
    assertEquals(650, moonStarted().presentBytes());
  }

  @Test
  void cutsAPartialDownloadAtTheStartOfACompletePieceThatDoesNotMatch() throws IOException {
    byte[] damaged = Arrays.copyOfRange(moon, 0, 650);
    damaged[10] ^= 1;
    put("ephemeris/MOON.bin.part", damaged);

    assertEquals(new DatasetInstallOutcome.Completed(), install());

    assertInstalled();
    assertEquals(List.of(MOON_1, MOON_2), requestsFor("MOON"));
    assertEquals(0, moonStarted().presentBytes());
  }

  @Test
  void downloadsAgainFromItsStartAPieceWhoseResumedEndDoesNotMatch() throws IOException {
    byte[] damaged = Arrays.copyOfRange(moon, 0, 650);
    damaged[600] ^= 1;
    put("ephemeris/MOON.bin.part", damaged);

    assertEquals(new DatasetInstallOutcome.Completed(), install());

    assertInstalled();
    assertEquals(List.of(MOON_2 + " bytes=150-", MOON_2), requestsFor("MOON"));
    AttemptFailed failure = eventsOf(AttemptFailed.class).getFirst();
    assertEquals(DatasetFailureCause.CORRUPT, failure.cause());
    assertEquals(1, failure.failuresWithoutProgress());
  }

  @Test
  void restartsThePieceWhenTheServerIgnoresTheRange() throws IOException {
    ignoreRange.add(MOON_2);
    put("ephemeris/MOON.bin.part", Arrays.copyOfRange(moon, 0, 650));

    assertEquals(new DatasetInstallOutcome.Completed(), install());

    assertInstalled();
    assertEquals(List.of(MOON_2 + " bytes=150-"), requestsFor("MOON"));
  }

  @Test
  void aConnectionCutAgainAndAgainStillEndsTheDownloadWhileItMovesForward() throws IOException {
    cutAfter.put(MOON_1, 120);
    cutAfter.put(MOON_2, 120);

    assertEquals(new DatasetInstallOutcome.Completed(), install());

    assertInstalled();
    assertEquals(
        List.of(
            MOON_1,
            MOON_1 + " bytes=120-",
            MOON_1 + " bytes=240-",
            MOON_1 + " bytes=360-",
            MOON_1 + " bytes=480-"),
        requestsFor(MOON_1));
    assertEquals(5, requestsFor(MOON_2).size());
    List<AttemptFailed> failures = eventsOf(AttemptFailed.class);
    assertEquals(8, failures.size());
    assertTrue(
        failures.stream().allMatch(f -> f.failuresWithoutProgress() == 0), failures.toString());
    assertTrue(failures.stream().allMatch(f -> f.cause() == DatasetFailureCause.NETWORK));
  }

  @Test
  void aServerSilentAfterItsHeadersFailsOnNetworkAfterThreeAttempts() {
    silent.add("A.bin");

    DatasetInstallOutcome outcome = install();

    DatasetInstallOutcome.Failed failed =
        assertInstanceOf(DatasetInstallOutcome.Failed.class, outcome);
    assertEquals(DatasetFailureCause.NETWORK, failed.cause());
    assertEquals("ephemeris/A.bin", failed.file().path());
    assertTrue(failed.detail().contains("no byte received"), failed.detail());
    assertEquals(List.of("A.bin", "A.bin", "A.bin"), requests);
    List<AttemptFailed> failures = eventsOf(AttemptFailed.class);
    assertEquals(
        List.of(1, 2), failures.stream().map(AttemptFailed::failuresWithoutProgress).toList());
    assertEquals(
        List.of(Duration.ofMillis(10), Duration.ofMillis(20)),
        failures.stream().map(AttemptFailed::retryIn).toList());
    assertFalse(Files.exists(checker.marker()));
  }

  @Test
  void aServerThatNeverAnswersFailsOnNetworkAfterThreeAttempts() {
    mute.add("A.bin");
    responseTimeout = Duration.ofMillis(300);

    DatasetInstallOutcome outcome = install();

    DatasetInstallOutcome.Failed failed =
        assertInstanceOf(DatasetInstallOutcome.Failed.class, outcome);
    assertEquals(DatasetFailureCause.NETWORK, failed.cause());
    assertTrue(failed.detail().contains("no response"), failed.detail());
    assertEquals(3, redirects.get());
  }

  @Test
  void repeatedServerErrorsFailOnNetwork() {
    forcedStatus.put("B-orbit.bin", 503);

    DatasetInstallOutcome outcome = install();

    DatasetInstallOutcome.Failed failed =
        assertInstanceOf(DatasetInstallOutcome.Failed.class, outcome);
    assertEquals(DatasetFailureCause.NETWORK, failed.cause());
    assertEquals("orbits/B-orbit.bin", failed.file().path());
    assertTrue(failed.detail().contains("HTTP 503"), failed.detail());
    assertEquals(3, requestsFor("B-orbit.bin").size());
  }

  @Test
  void aByteChangedEveryTimeFailsAsCorrupt() throws IOException {
    corrupt.add("A.bin");

    DatasetInstallOutcome outcome = install();

    DatasetInstallOutcome.Failed failed =
        assertInstanceOf(DatasetInstallOutcome.Failed.class, outcome);
    assertEquals(DatasetFailureCause.CORRUPT, failed.cause());
    assertEquals(3, requestsFor("A.bin").size());
    assertEquals(0, Files.size(checker.partOf(manifest.files().getFirst())));
  }

  @Test
  void aFileMissingFromTheReleaseFailsAtOnce() {
    served.remove(MOON_2);

    DatasetInstallOutcome outcome = install();

    DatasetInstallOutcome.Failed failed =
        assertInstanceOf(DatasetInstallOutcome.Failed.class, outcome);
    assertEquals(DatasetFailureCause.NOT_FOUND, failed.cause());
    assertEquals("ephemeris/MOON.bin", failed.file().path());
    assertEquals(List.of(MOON_2), requestsFor(MOON_2));
    assertTrue(eventsOf(AttemptFailed.class).isEmpty());
  }

  @Test
  void notEnoughSpaceFailsBeforeAnyRequest() {
    usableSpace = 1399;

    DatasetInstallOutcome outcome = install();

    DatasetInstallOutcome.Failed failed =
        assertInstanceOf(DatasetInstallOutcome.Failed.class, outcome);
    assertEquals(DatasetFailureCause.DISK_FULL, failed.cause());
    assertFalse(failed.hasFile());
    assertEquals(0, redirects.get());
    assertTrue(eventsOf(PhaseStarted.class).isEmpty());
  }

  @Test
  void adoptsAMatchingFileWithoutDownloadingItAndReplacesOneThatDoesNotMatch() throws IOException {
    byte[] wrong = b.clone();
    wrong[0] ^= 1;
    put("ephemeris/A.bin", a);
    put("orbits/B-orbit.bin", wrong);

    assertEquals(new DatasetInstallOutcome.Completed(), install());

    assertInstalled();
    assertEquals(List.of(MOON_1, MOON_2, "B-orbit.bin"), requests);
    List<PhaseStarted> phases = eventsOf(PhaseStarted.class);
    assertEquals(
        List.of(
            new PhaseStarted(Phase.VERIFYING, 2, 400),
            new PhaseStarted(Phase.DOWNLOADING, 2, 1100)),
        phases);
    List<String> completed =
        eventsOf(FileCompleted.class).stream().map(e -> e.file().path()).toList();
    assertEquals(List.of("ephemeris/A.bin", "ephemeris/MOON.bin", "orbits/B-orbit.bin"), completed);
  }

  @Test
  void cancellingKeepsThePartialDownloadAndTheNextInstallationResumesIt() throws Exception {
    holdAfter.put(MOON_2, 100);
    stallTimeout = Duration.ofSeconds(10);
    DatasetInstaller installer = installer();
    Path part = checker.partOf(manifest.files().get(1));
    ExecutorService runner = Executors.newSingleThreadExecutor();
    try {
      Future<DatasetInstallOutcome> running = runner.submit(() -> installer.install(events::add));
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while ((!Files.exists(part) || Files.size(part) < 600) && System.nanoTime() < deadline) {
        pause(5);
      }
      assertEquals(600, Files.size(part));

      long cancelledAt = System.nanoTime();
      installer.cancel();
      DatasetInstallOutcome outcome = running.get(5, TimeUnit.SECONDS);

      assertEquals(new DatasetInstallOutcome.Cancelled(), outcome);
      assertTrue(System.nanoTime() - cancelledAt < TimeUnit.SECONDS.toNanos(2));
      assertEquals(600, Files.size(part));
      assertFalse(Files.exists(checker.marker()));
    } finally {
      runner.shutdownNow();
    }

    holdAfter.clear();
    requests.clear();
    events.clear();
    assertEquals(new DatasetInstallOutcome.Completed(), install());

    assertInstalled();
    assertEquals(List.of(MOON_2 + " bytes=100-", "B-orbit.bin"), requests);
    assertEquals(600, moonStarted().presentBytes());
  }

  @Test
  void aReadyDatasetIsLeftAloneAndAnUnmarkedOneIsAdoptedWithoutAnyRequest() throws IOException {
    assertEquals(new DatasetInstallOutcome.Completed(), install());
    requests.clear();
    events.clear();
    redirects.set(0);

    assertEquals(new DatasetInstallOutcome.Completed(), install());
    assertEquals(List.of(new Finished(new DatasetInstallOutcome.Completed())), events);

    Files.delete(checker.marker());
    events.clear();
    assertEquals(new DatasetInstallOutcome.Completed(), install());

    assertEquals(0, redirects.get());
    assertEquals(List.of(new PhaseStarted(Phase.VERIFYING, 3, 1400)), eventsOf(PhaseStarted.class));
    assertInstalled();
  }
}
