package com.smousseur.orbitlab.app.dataset;

import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.AttemptFailed;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.BytesProcessed;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.FileCompleted;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.FileStarted;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.Finished;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.Phase;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.PhaseStarted;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Brings the dataset directory to what the manifest expects: adopts the files already there,
 * downloads the missing ones from the release, resumes partial downloads, and writes the marker
 * that lets the next start-up trust the directory without reading it.
 *
 * <p>{@link #install} blocks the calling thread until it ends, in this order:
 *
 * <ol>
 *   <li><b>Adoption.</b> Every file present at its size without a matching marker entry is read
 *       through SHA-256. A match is kept; anything else is deleted, since keeping it next to its
 *       new download would take its room twice.
 *   <li><b>Disk space.</b> Files of the wrong size and partial downloads larger than their file are
 *       deleted, then the volume must have room for every byte left to download, or the
 *       installation stops before any request.
 *   <li><b>Downloads</b>, one file at a time in the manifest's order. Each file is written into a
 *       single {@code <file>.part} next to it, its pieces one after the other, so its size alone
 *       says which piece and which byte to resume from. Each piece is checked at its end, the whole
 *       file at its end; the {@code .part} is then flushed to the disk and renamed atomically.
 *   <li><b>Marker.</b> The manifest is written, through a temporary file renamed atomically, as the
 *       marker {@link DatasetChecker} reads.
 * </ol>
 *
 * <p><b>Resuming.</b> A partial download is read again to rebuild its fingerprints, and a complete
 * piece among it that does not match is cut off. The request then asks for the rest of the piece
 * with {@code Range}; a server that ignores it and sends the whole piece restarts that piece.
 *
 * <p><b>Failures.</b> Only failures that leave the download where it was count: an attempt that
 * moved the {@code .part} forward resets the count, so a connection cut again and again still ends
 * the download. Network errors, missing responses, silences, server errors, rate limits and pieces
 * whose fingerprint does not match are retried after the configured waits; the failure after the
 * last wait gives up. A 404 or 410, a full disk or a local write error stops at once.
 *
 * <p><b>Timeouts.</b> The wait for each response's headers is bounded on the future of an
 * asynchronous request; a download's body is not bounded in duration, only in silence: a watchdog
 * closes the stream once no byte has arrived for the configured time.
 *
 * <p><b>Cancelling.</b> {@link #cancel()}, from any thread, closes the stream being read, abandons
 * the request being waited for, and stops a fingerprint computation between two blocks. Partial
 * downloads are kept: installing again resumes them.
 */
public final class DatasetInstaller {

  private static final Logger LOGGER = LogManager.getLogger(DatasetInstaller.class);

  private static final int BUFFER_BYTES = 1 << 20;

  private static final long EVENT_INTERVAL_NANOS = Duration.ofMillis(250).toNanos();

  private static final long MAX_WATCHDOG_PERIOD_NANOS = Duration.ofSeconds(1).toNanos();

  private final DatasetChecker checker;
  private final DatasetInstallConfig config;
  private final AtomicReference<Run> current = new AtomicReference<>();

  /**
   * Creates an installer.
   *
   * @param checker the manifest to install and the directory to install it into
   * @param config where to download from, and how long to wait
   */
  public DatasetInstaller(DatasetChecker checker, DatasetInstallConfig config) {
    this.checker = Objects.requireNonNull(checker, "checker");
    this.config = Objects.requireNonNull(config, "config");
  }

  /**
   * Installs the dataset, or returns at once without any request when it is already ready.
   *
   * @param listener told of every step, on this thread; its last event is {@link Finished}
   * @return how the installation ended
   * @throws IllegalStateException if another installation is running on this installer
   */
  public DatasetInstallOutcome install(DatasetInstallListener listener) {
    Objects.requireNonNull(listener, "listener");
    Run run = new Run(listener);
    if (!current.compareAndSet(null, run)) {
      throw new IllegalStateException("An installation of the dataset is already running");
    }
    try {
      DatasetInstallOutcome outcome = run.execute();
      switch (outcome) {
        case DatasetInstallOutcome.Failed f ->
            LOGGER.warn(
                "Dataset installation failed ({}{}): {}",
                f.cause(),
                f.hasFile() ? ", " + f.file().path() : "",
                f.detail());
        case DatasetInstallOutcome.Cancelled c -> LOGGER.info("Dataset installation cancelled");
        case DatasetInstallOutcome.Completed c ->
            LOGGER.info("Dataset {} ready in {}", checker.manifest().tag(), checker.root());
      }
      listener.onEvent(new Finished(outcome));
      return outcome;
    } finally {
      current.compareAndSet(run, null);
    }
  }

  /**
   * Cancels the running installation, if any; it then returns {@link
   * DatasetInstallOutcome.Cancelled}.
   */
  public void cancel() {
    Run run = current.get();
    if (run != null) {
      run.cancel();
    }
  }

  /** Ends an installation early with the outcome it carries. */
  private static final class Stop extends Exception {

    @Serial private static final long serialVersionUID = 1L;

    private final transient DatasetInstallOutcome outcome;

    Stop(DatasetInstallOutcome outcome) {
      this(outcome, null);
    }

    Stop(DatasetInstallOutcome outcome, Throwable cause) {
      super(null, cause, false, false);
      this.outcome = outcome;
    }
  }

  /** Why an attempt on a piece failed, and whether another attempt may succeed. */
  private record Failure(DatasetFailureCause cause, String detail, boolean retriable) {}

  /** The stream of one attempt, as the watchdog and {@link #cancel()} see it. */
  private static final class Attempt {
    private final InputStream body;
    private volatile long lastByteNanos = System.nanoTime();
    private volatile boolean stalled;

    Attempt(InputStream body) {
      this.body = body;
    }
  }

  /**
   * What a {@code .part} holds, all of it checked: its length, the piece that length falls in, and
   * the fingerprints of the file so far, at the start of that piece, and of that piece so far.
   *
   * <p>One instance follows one file and never leaves the installing thread, which is why its
   * digests can be fields.
   */
  @SuppressWarnings("PMD.AvoidMessageDigestField")
  private static final class PartState {
    private final DatasetFile file;
    private long length;
    private int piece;
    private long pieceStart;
    private MessageDigest whole = Sha256.newDigest();
    private MessageDigest wholeAtPieceStart = copy(whole);
    private final MessageDigest pieceDigest = Sha256.newDigest();

    PartState(DatasetFile file) {
      this.file = file;
    }

    boolean complete() {
      return piece == file.pieces().size();
    }

    DatasetPiece currentPiece() {
      return file.pieces().get(piece);
    }

    long pieceEnd() {
      return pieceStart + currentPiece().size();
    }

    void append(byte[] bytes, int count) {
      whole.update(bytes, 0, count);
      pieceDigest.update(bytes, 0, count);
      length += count;
    }

    /**
     * Completes the current piece, whose last byte was just appended: moves on to the next one if
     * it matches its fingerprint, or goes back to its start otherwise.
     *
     * @return the piece's fingerprint
     */
    String finishPiece() {
      String sha = Sha256.hex(pieceDigest);
      if (sha.equals(currentPiece().sha256())) {
        pieceStart = pieceEnd();
        piece++;
        wholeAtPieceStart = copy(whole);
      } else {
        rewind();
      }
      return sha;
    }

    /** Forgets the current piece's bytes; the {@code .part} must be cut at {@link #pieceStart}. */
    void rewind() {
      length = pieceStart;
      whole = copy(wholeAtPieceStart);
      pieceDigest.reset();
    }

    private static MessageDigest copy(MessageDigest digest) {
      try {
        return (MessageDigest) digest.clone();
      } catch (CloneNotSupportedException e) {
        throw new IllegalStateException("The platform's SHA-256 cannot be copied", e);
      }
    }
  }

  /** One call to {@link #install}, and everything it shares with {@link #cancel()}. */
  private final class Run {
    private final DatasetInstallListener listener;
    private final CountDownLatch cancelled = new CountDownLatch(1);
    private volatile Attempt attempt;
    private volatile CompletableFuture<?> pending;
    private HttpClient client;
    private long phaseBytes;
    private long fileBase;
    private long filePresent;
    private long lastEventNanos;

    Run(DatasetInstallListener listener) {
      this.listener = listener;
    }

    void cancel() {
      cancelled.countDown();
      Attempt a = attempt;
      if (a != null) {
        closeQuietly(a.body);
      }
      CompletableFuture<?> p = pending;
      if (p != null) {
        p.cancel(true);
      }
    }

    private boolean isCancelled() {
      return cancelled.getCount() == 0;
    }

    private void checkCancelled() throws Stop {
      if (isCancelled()) {
        throw new Stop(new DatasetInstallOutcome.Cancelled());
      }
    }

    DatasetInstallOutcome execute() {
      DatasetCheck check = checker.check();
      if (check.ready()) {
        return new DatasetInstallOutcome.Completed();
      }
      try {
        List<DatasetFile> rejected = adopt(check.filesWith(DatasetCheck.FileStatus.TO_VERIFY));
        Set<DatasetFile> missing =
            new HashSet<>(check.filesWith(DatasetCheck.FileStatus.TO_DOWNLOAD));
        missing.addAll(rejected);
        List<DatasetFile> toDownload =
            checker.manifest().files().stream().filter(missing::contains).toList();
        if (!toDownload.isEmpty()) {
          download(toDownload, reserveSpace(toDownload));
        }
        writeMarker();
        return new DatasetInstallOutcome.Completed();
      } catch (Stop stop) {
        return stop.outcome;
      }
    }

    private List<DatasetFile> adopt(List<DatasetFile> files) throws Stop {
      if (files.isEmpty()) {
        return List.of();
      }
      long total = files.stream().mapToLong(DatasetFile::size).sum();
      LOGGER.info("Verifying {} dataset files already on disk ({} bytes)", files.size(), total);
      listener.onEvent(new PhaseStarted(Phase.VERIFYING, files.size(), total));
      phaseBytes = 0;
      List<DatasetFile> rejected = new ArrayList<>();
      for (int i = 0; i < files.size(); i++) {
        DatasetFile file = files.get(i);
        checkCancelled();
        listener.onEvent(new FileStarted(file, i + 1, files.size(), 0));
        lastEventNanos = System.nanoTime();
        String sha = hashFile(file);
        if (sha.equals(file.sha256())) {
          listener.onEvent(new FileCompleted(file));
        } else {
          LOGGER.warn(
              "{} has sha256 {}, expected {}: deleted, to download",
              file.path(),
              sha,
              file.sha256());
          delete(checker.fileOf(file), file);
          rejected.add(file);
        }
      }
      return rejected;
    }

    private String hashFile(DatasetFile file) throws Stop {
      Path path = checker.fileOf(file);
      MessageDigest digest = Sha256.newDigest();
      ByteBuffer buffer = ByteBuffer.allocate(BUFFER_BYTES);
      long fileBytes = 0;
      try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
        int read = channel.read(buffer);
        while (read >= 0) {
          checkCancelled();
          digest.update(buffer.array(), 0, read);
          fileBytes += read;
          phaseBytes += read;
          reportBytes(file, fileBytes, false);
          buffer.clear();
          read = channel.read(buffer);
        }
      } catch (IOException e) {
        throw failed(
            DatasetFailureCause.LOCAL_IO, file, "cannot read " + path + ": " + describe(e), e);
      }
      reportBytes(file, fileBytes, true);
      return Sha256.hex(digest);
    }

    /**
     * Deletes what can never be used — a file of the wrong size, a partial download larger than its
     * file — and checks the volume has room for the rest.
     *
     * @return the bytes left to download
     */
    private long reserveSpace(List<DatasetFile> files) throws Stop {
      long needed = 0;
      for (DatasetFile file : files) {
        delete(checker.fileOf(file), file);
        Path part = checker.partOf(file);
        long partBytes = 0;
        try {
          if (Files.isRegularFile(part)) {
            partBytes = Files.size(part);
          }
        } catch (IOException e) {
          throw failed(
              DatasetFailureCause.LOCAL_IO, file, "cannot read " + part + ": " + describe(e), e);
        }
        if (partBytes > file.size()) {
          delete(part, file);
          partBytes = 0;
        }
        needed += file.size() - partBytes;
      }
      long usable;
      try {
        usable = config.usableSpace().of(checker.root());
      } catch (IOException e) {
        throw failed(
            DatasetFailureCause.LOCAL_IO,
            null,
            "cannot measure the free space for " + checker.root() + ": " + describe(e),
            e);
      }
      if (usable < needed) {
        throw failed(
            DatasetFailureCause.DISK_FULL,
            null,
            String.format(
                Locale.ROOT,
                "%,d bytes to download, %,d available for %s",
                needed,
                usable,
                checker.root()));
      }
      return needed;
    }

    private void download(List<DatasetFile> files, long bytes) throws Stop {
      LOGGER.info("Downloading {} dataset files ({} bytes)", files.size(), bytes);
      listener.onEvent(new PhaseStarted(Phase.DOWNLOADING, files.size(), bytes));
      phaseBytes = 0;
      client =
          HttpClient.newBuilder()
              .followRedirects(HttpClient.Redirect.NORMAL)
              .connectTimeout(config.connectTimeout())
              .build();
      ScheduledExecutorService watchdog =
          Executors.newSingleThreadScheduledExecutor(
              task -> {
                Thread thread = new Thread(task, "dataset-download-watchdog");
                thread.setDaemon(true);
                return thread;
              });
      try {
        long period =
            Math.clamp(config.stallTimeout().toNanos() / 10, 1_000_000L, MAX_WATCHDOG_PERIOD_NANOS);
        watchdog.scheduleAtFixedRate(this::cutIfSilent, period, period, TimeUnit.NANOSECONDS);
        for (int i = 0; i < files.size(); i++) {
          downloadFile(files.get(i), i + 1, files.size());
        }
      } finally {
        watchdog.shutdownNow();
        client.shutdownNow();
      }
    }

    private void cutIfSilent() {
      Attempt a = attempt;
      if (a != null && System.nanoTime() - a.lastByteNanos > config.stallTimeout().toNanos()) {
        a.stalled = true;
        closeQuietly(a.body);
      }
    }

    private void downloadFile(DatasetFile file, int index, int count) throws Stop {
      checkCancelled();
      Path part = checker.partOf(file);
      try {
        Files.createDirectories(part.getParent());
      } catch (IOException e) {
        throw failed(
            DatasetFailureCause.LOCAL_IO,
            file,
            "cannot create " + part.getParent() + ": " + describe(e),
            e);
      }
      try (FileChannel channel =
          FileChannel.open(
              part, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
        PartState state = resume(file, channel);
        fileBase = phaseBytes;
        filePresent = state.length;
        if (state.length > 0) {
          LOGGER.info("Resuming {} at byte {}", file.path(), state.length);
        }
        listener.onEvent(new FileStarted(file, index, count, state.length));
        lastEventNanos = System.nanoTime();
        while (!state.complete()) {
          fetchPiece(state, channel);
        }
        String sha = Sha256.hex(state.whole);
        if (!sha.equals(file.sha256())) {
          channel.truncate(0);
          throw failed(
              DatasetFailureCause.CORRUPT,
              file,
              "its pieces joined have sha256 " + sha + ", expected " + file.sha256());
        }
        phaseBytes = fileBase + file.size() - filePresent;
        reportBytes(file, state.length, true);
        channel.force(true);
      } catch (IOException e) {
        throw failed(
            DatasetFailureCause.LOCAL_IO, file, "cannot use " + part + ": " + describe(e), e);
      }
      Path target = checker.fileOf(file);
      try {
        Files.move(
            part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        throw failed(
            DatasetFailureCause.LOCAL_IO,
            file,
            "cannot rename " + part + " to " + target + ": " + describe(e),
            e);
      }
      LOGGER.info("{} downloaded and verified", file.path());
      listener.onEvent(new FileCompleted(file));
    }

    /**
     * Reads a partial download again to rebuild its fingerprints, and cuts it at the start of the
     * first complete piece that does not match.
     */
    private PartState resume(DatasetFile file, FileChannel channel) throws IOException, Stop {
      PartState state = new PartState(file);
      long size = channel.size();
      if (size > file.size()) {
        channel.truncate(0);
        size = 0;
      }
      ByteBuffer buffer = ByteBuffer.allocate(BUFFER_BYTES);
      while (state.length < size) {
        checkCancelled();
        buffer
            .clear()
            .limit((int) Math.min(BUFFER_BYTES, Math.min(size, state.pieceEnd()) - state.length));
        int read = channel.read(buffer, state.length);
        if (read < 0) {
          throw new IOException("the partial download ended before its size");
        }
        state.append(buffer.array(), read);
        if (state.length == state.pieceEnd()) {
          DatasetPiece piece = state.currentPiece();
          String sha = state.finishPiece();
          if (!sha.equals(piece.sha256())) {
            LOGGER.warn(
                "{} in the partial download of {} has sha256 {}, expected {}: downloaded again",
                piece.name(),
                file.path(),
                sha,
                piece.sha256());
            channel.truncate(state.length);
            size = state.length;
          }
        }
      }
      return state;
    }

    private void fetchPiece(PartState state, FileChannel channel) throws Stop {
      DatasetFile file = state.file;
      int failures = 0;
      while (true) {
        checkCancelled();
        long before = state.length;
        Failure failure = attempt(state, channel);
        if (failure == null) {
          return;
        }
        checkCancelled();
        if (!failure.retriable()) {
          throw failed(failure.cause(), file, failure.detail());
        }
        failures = state.length > before ? 0 : failures + 1;
        if (failures >= config.maxFailuresWithoutProgress()) {
          throw failed(
              failure.cause(),
              file,
              failure.detail()
                  + "; gave up after "
                  + failures
                  + " failures in a row without progress");
        }
        Duration wait = config.retryWaits().get(Math.max(failures, 1) - 1);
        LOGGER.warn(
            "{} failed ({}, {} in a row without progress), next attempt in {} ms: {}",
            state.currentPiece().name(),
            failure.cause(),
            failures,
            wait.toMillis(),
            failure.detail());
        listener.onEvent(
            new AttemptFailed(file, failure.cause(), failure.detail(), failures, wait));
        try {
          if (cancelled.await(wait.toNanos(), TimeUnit.NANOSECONDS)) {
            throw new Stop(new DatasetInstallOutcome.Cancelled());
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new Stop(new DatasetInstallOutcome.Cancelled(), e);
        }
      }
    }

    /**
     * Downloads the rest of the current piece once.
     *
     * @return null when the piece is complete and matches its fingerprint, why it failed otherwise
     */
    private Failure attempt(PartState state, FileChannel channel) throws Stop {
      DatasetPiece piece = state.currentPiece();
      long offset = state.length - state.pieceStart;
      URI uri = checker.manifest().pieceUri(config.downloadBase(), piece);
      if (offset == piece.size()) {
        return finishPiece(state, channel, uri);
      }
      HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET();
      if (offset > 0) {
        request.header("Range", "bytes=" + offset + "-");
      }
      CompletableFuture<HttpResponse<InputStream>> future =
          client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofInputStream());
      pending = future;
      HttpResponse<InputStream> response;
      try {
        if (isCancelled()) {
          future.cancel(true);
        }
        response = future.get(config.responseTimeout().toNanos(), TimeUnit.NANOSECONDS);
      } catch (TimeoutException e) {
        future.cancel(true);
        return retry(
            DatasetFailureCause.NETWORK,
            "no response from " + uri + " within " + config.responseTimeout().toMillis() + " ms");
      } catch (ExecutionException e) {
        return retry(
            DatasetFailureCause.NETWORK,
            "request to " + uri + " failed: " + describe(e.getCause()));
      } catch (CancellationException e) {
        return retry(DatasetFailureCause.NETWORK, "request to " + uri + " cancelled");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        future.cancel(true);
        throw new Stop(new DatasetInstallOutcome.Cancelled(), e);
      } finally {
        pending = null;
      }
      Attempt current = new Attempt(response.body());
      attempt = current;
      long received = 0;
      try (InputStream body = response.body()) {
        if (isCancelled()) {
          return retry(DatasetFailureCause.NETWORK, "cancelled");
        }
        int status = response.statusCode();
        if (status == 404 || status == 410) {
          return new Failure(
              DatasetFailureCause.NOT_FOUND, "HTTP " + status + " from " + uri, false);
        }
        if (status == 206) {
          String range = response.headers().firstValue("Content-Range").orElse("");
          String expected = "bytes " + offset + "-" + (piece.size() - 1) + "/" + piece.size();
          if (!expected.equals(range)) {
            return retry(
                DatasetFailureCause.NETWORK,
                "HTTP 206 from "
                    + uri
                    + " with Content-Range '"
                    + range
                    + "', expected '"
                    + expected
                    + "'");
          }
        } else if (status == 200) {
          if (offset > 0) {
            LOGGER.info(
                "{} ignored the range request: {} downloaded from its start", uri, piece.name());
            rewind(state, channel);
          }
        } else {
          return retry(DatasetFailureCause.NETWORK, "HTTP " + status + " from " + uri);
        }
        long pieceEnd = state.pieceEnd();
        byte[] buffer = new byte[BUFFER_BYTES];
        while (state.length < pieceEnd) {
          int read = body.read(buffer, 0, (int) Math.min(BUFFER_BYTES, pieceEnd - state.length));
          if (read < 0) {
            return retry(
                DatasetFailureCause.NETWORK,
                String.format(
                    Locale.ROOT,
                    "the connection to %s ended after %,d bytes, %,d short of the piece's end",
                    uri,
                    received,
                    pieceEnd - state.length));
          }
          current.lastByteNanos = System.nanoTime();
          write(channel, buffer, read, state);
          state.append(buffer, read);
          received += read;
          phaseBytes = fileBase + Math.max(0, state.length - filePresent);
          reportBytes(state.file, state.length, false);
        }
        if (body.read() >= 0) {
          rewind(state, channel);
          return retry(
              DatasetFailureCause.CORRUPT, uri + " is longer than " + piece.size() + " bytes");
        }
        return finishPiece(state, channel, uri);
      } catch (IOException e) {
        if (current.stalled) {
          return retry(
              DatasetFailureCause.NETWORK,
              String.format(
                  Locale.ROOT,
                  "no byte received from %s for %d ms, after %,d bytes",
                  uri,
                  config.stallTimeout().toMillis(),
                  received));
        }
        return retry(
            DatasetFailureCause.NETWORK,
            String.format(
                Locale.ROOT,
                "download from %s failed after %,d bytes: %s",
                uri,
                received,
                describe(e)));
      } finally {
        attempt = null;
      }
    }

    /**
     * Checks the current piece, whose bytes are all in the {@code .part}.
     *
     * @return null when it matches its fingerprint; otherwise its bytes are cut off and the failure
     *     is returned
     */
    private Failure finishPiece(PartState state, FileChannel channel, URI uri) throws Stop {
      DatasetPiece piece = state.currentPiece();
      String sha = state.finishPiece();
      if (sha.equals(piece.sha256())) {
        return null;
      }
      cut(state, channel);
      return retry(
          DatasetFailureCause.CORRUPT,
          piece.name() + " from " + uri + " has sha256 " + sha + ", expected " + piece.sha256());
    }

    private void rewind(PartState state, FileChannel channel) throws Stop {
      state.rewind();
      cut(state, channel);
    }

    /** Cuts the {@code .part} at the length its state holds. */
    private void cut(PartState state, FileChannel channel) throws Stop {
      try {
        channel.truncate(state.length);
      } catch (IOException e) {
        throw failed(
            DatasetFailureCause.LOCAL_IO,
            state.file,
            "cannot cut the partial download back to byte " + state.length + ": " + describe(e),
            e);
      }
    }

    private void write(FileChannel channel, byte[] bytes, int count, PartState state) throws Stop {
      ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, count);
      long position = state.length;
      try {
        while (buffer.hasRemaining()) {
          position += channel.write(buffer, position);
        }
      } catch (IOException e) {
        throw writeFailure(state, e);
      }
    }

    /**
     * Tells a full disk from another write error by measuring the free space again: the exception's
     * message depends on the system's language.
     */
    private Stop writeFailure(PartState state, IOException e) {
      long left = state.file.size() - state.length;
      Path directory = checker.partOf(state.file).getParent();
      try {
        long usable = config.usableSpace().of(directory);
        if (usable < left) {
          return failed(
              DatasetFailureCause.DISK_FULL,
              state.file,
              String.format(
                  Locale.ROOT,
                  "%,d bytes left to write, %,d available: %s",
                  left,
                  usable,
                  describe(e)),
              e);
        }
      } catch (IOException measuring) {
        LOGGER.warn("Cannot measure the free space for {}: {}", directory, measuring.getMessage());
      }
      return failed(
          DatasetFailureCause.LOCAL_IO,
          state.file,
          "cannot write " + checker.partOf(state.file) + ": " + describe(e),
          e);
    }

    private void writeMarker() throws Stop {
      Path marker = checker.marker();
      Path temporary = marker.resolveSibling(DatasetChecker.MARKER_NAME + ".tmp");
      try {
        Files.createDirectories(checker.root());
        Files.writeString(
            temporary, DatasetManifestCodec.write(checker.manifest()) + System.lineSeparator());
        Files.move(
            temporary, marker, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        throw failed(
            DatasetFailureCause.LOCAL_IO, null, "cannot write " + marker + ": " + describe(e), e);
      }
    }

    private void reportBytes(DatasetFile file, long fileBytes, boolean last) {
      long now = System.nanoTime();
      if (last || now - lastEventNanos >= EVENT_INTERVAL_NANOS) {
        lastEventNanos = now;
        listener.onEvent(new BytesProcessed(file, fileBytes, phaseBytes));
      }
    }

    private void delete(Path path, DatasetFile file) throws Stop {
      try {
        Files.deleteIfExists(path);
      } catch (IOException e) {
        throw failed(
            DatasetFailureCause.LOCAL_IO, file, "cannot delete " + path + ": " + describe(e), e);
      }
    }
  }

  private static Failure retry(DatasetFailureCause cause, String detail) {
    return new Failure(cause, detail, true);
  }

  private static Stop failed(DatasetFailureCause cause, DatasetFile file, String detail) {
    return new Stop(new DatasetInstallOutcome.Failed(cause, file, detail));
  }

  private static Stop failed(
      DatasetFailureCause cause, DatasetFile file, String detail, Throwable error) {
    return new Stop(new DatasetInstallOutcome.Failed(cause, file, detail), error);
  }

  /**
   * An exception and its cause: the JDK's response stream reports every failure as "closed", with
   * what actually happened only in the cause.
   */
  private static String describe(Throwable e) {
    return e.getCause() == null ? e.toString() : e + " (caused by " + e.getCause() + ")";
  }

  private static void closeQuietly(InputStream stream) {
    try {
      stream.close();
    } catch (IOException e) {
      LOGGER.debug("Closing a download stream failed: {}", e.toString());
    }
  }
}
