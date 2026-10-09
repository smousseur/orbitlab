package com.smousseur.orbitlab.tools.ephemerisgen;

import com.github.luben.zstd.Zstd;
import com.smousseur.orbitlab.core.OrbitlabPath;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.BodySample;
import com.smousseur.orbitlab.simulation.source.DatasetEphemerisSource;
import com.sun.nio.file.ExtendedOpenOption;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;

/**
 * Baseline of the ephemeris dataset the application is to download on first launch, measured on the
 * real files before any of the download exists: what the files weigh, whether they are what the
 * current generator writes, what a zip or a stronger zstd level would save, what hashing them costs
 * read from the disk, and what the reader does without them. NOT a gate: asserts nothing. Run with
 * {@code -Dorbitlab.probe=true --tests '*Tec1DatasetBaselineProbe*'}, twice with {@code cleanTest}
 * in between: the chunk digests table E prints then compare two JVMs.
 *
 * <p><b>The generator is the production one, without its main.</b> The per-body settings come from
 * {@code EphemerisDatasetGeneratorMain.withPvFirstUnder10GoParams} and the Orekit data from {@code
 * EphemerisDatasetGenerator.initOrekitData}, both called by reflection rather than copied: a rename
 * fails this probe instead of letting it measure settings production no longer uses. Only the
 * dataset span is transcribed, since {@code EphemerisDatasetGenerator.generateAll} holds it in
 * local variables; a wrong span shows as a chunk count other than the files' own in table B and as
 * differing chunks in table E.
 *
 * <p><b>What it does not measure</b>: reproducibility on another machine or operating system;
 * whether {@link ExtendedOpenOption#DIRECT} bypasses every cache, beyond the throughput gap it
 * shows; a file left by an interrupted generator, whose header and index are still placeholders.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Tec1DatasetBaselineProbe {

  private static final Logger logger = LogManager.getLogger(Tec1DatasetBaselineProbe.class);

  private static final double MIB = 1024.0 * 1024.0;
  private static final double GIB = 1024.0 * MIB;

  /** GitHub's ceiling for one release asset: every published piece stays strictly below it. */
  private static final long ASSET_LIMIT_BYTES = 2L << 30;

  /** The deflate gain at or above which publishing the files unzipped is reopened. */
  private static final double DEFLATE_REOPEN_GAIN = 0.05;

  private static final byte[] MAGIC = "ORBL_EPH".getBytes(StandardCharsets.US_ASCII);
  private static final int HEADER_LABELS = 5;
  private static final int INDEX_ENTRY_BYTES = 24;
  private static final int BLOCK_HEADER_BYTES = 32;
  private static final int PV_SAMPLE_BYTES = 6 * Double.BYTES;
  private static final int ROT_SAMPLE_BYTES = 4 * Double.BYTES;
  private static final int READ_BUFFER_BYTES = 8 << 20;
  private static final int DEFLATE_BUFFER_BYTES = 1 << 20;
  private static final int CHUNKS_IN_CACHE = 32;

  /** A fixed chunk in the middle of the span, sampled next to the first two and the last one. */
  private static final int MIDDLE_CHUNK = 20_000;

  /**
   * The size of each body file in MiB as extrapolated from 25 chunks per body before the real
   * dataset was at hand, printed next to the real size to show the gap.
   */
  private static final Map<SolarSystemBody, Double> ESTIMATED_MIB = estimatedMib();

  private static final Map<SolarSystemBody, BodyIndex> indexes =
      new EnumMap<>(SolarSystemBody.class);
  private static GeneratorConfigV1 production;
  private static AbsoluteDate datasetStart;
  private static AbsoluteDate datasetEndExclusive;
  private static TimeScale tai;
  private static Path scratch;

  /** Where a read went and what it cost: one pass over one file. */
  private record Pass(long bytes, double seconds, String sha256) {
    double mibPerSecond() {
      return bytes / MIB / seconds;
    }
  }

  /** The PV or rotation block starting at {@code at} in a chunk, as its own header describes it. */
  private record Block(int at, double dt, int samples, int compressedLength) {
    static Block pv(byte[] chunk) {
      return of(chunk, le(chunk).getInt(20));
    }

    static Block rot(byte[] chunk) {
      return of(chunk, le(chunk).getInt(28));
    }

    private static Block of(byte[] chunk, int at) {
      ByteBuffer b = le(chunk);
      return new Block(at, b.getDouble(at + 12), b.getInt(at + 20), b.getInt(at + 24));
    }

    byte[] payload(byte[] chunk) {
      return Arrays.copyOfRange(
          chunk, at + BLOCK_HEADER_BYTES, at + BLOCK_HEADER_BYTES + compressedLength);
    }

    byte[] raw(byte[] chunk, int sampleBytes) {
      return Zstd.decompress(payload(chunk), samples * sampleBytes);
    }
  }

  @BeforeAll
  static void readDataset() throws Exception {
    Path orekitZip =
        Path.of(
            Objects.requireNonNull(
                    Tec1DatasetBaselineProbe.class.getClassLoader().getResource("orekit-data.zip"),
                    "orekit-data.zip is not on the test classpath")
                .toURI());
    invokePrivateStatic(
        EphemerisDatasetGenerator.class,
        "initOrekitData",
        new Class<?>[] {File.class},
        orekitZip.toFile());
    scratch = Files.createTempDirectory("tec1-probe");
    GeneratorConfigV1 defaults = GeneratorConfigV1.defaultV1(orekitZip, scratch.resolve("gen"));
    production =
        (GeneratorConfigV1)
            invokePrivateStatic(
                EphemerisDatasetGeneratorMain.class,
                "withPvFirstUnder10GoParams",
                new Class<?>[] {GeneratorConfigV1.class},
                defaults);
    tai = TimeScalesFactory.getTAI();
    datasetStart = new AbsoluteDate(1990, 1, 1, 0, 0, 0.0, tai);
    datasetEndExclusive = new AbsoluteDate(2100, 12, 31, 0, 0, 0.0, tai);
    for (SolarSystemBody body : SolarSystemBody.values()) {
      indexes.put(
          body, new BodyIndex(body, OrbitlabPath.EPHEMERIS_PATH.resolve(body.name() + ".bin")));
    }
  }

  @AfterAll
  static void deleteScratch() {
    if (scratch == null) {
      return;
    }
    try (Stream<Path> paths = Files.walk(scratch)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    } catch (IOException e) {
      logger.warn("Could not delete the probe's scratch directory {}: {}", scratch, e.getMessage());
    }
  }

  @Test
  @Order(1)
  void aWhatTheFilesWeigh() throws IOException {
    logger.info("=== DATASET / A - what the files weigh ===");
    logger.info(
        "file                    bytes         MiB  design(MiB)      gap   chunk min (id)   median"
            + "   chunk max (id)   spread  pieces<2GiB");
    long total = 0;
    double estimated = 0;
    for (BodyIndex file : indexes.values()) {
      double mib = file.size / MIB;
      double design = ESTIMATED_MIB.get(file.body);
      int smallest = file.smallestChunk();
      int largest = file.largestChunk();
      total += file.size;
      estimated += design;
      logger.info(
          String.format(
              Locale.ROOT,
              "%-13s %15d %11.2f %12.1f %+7.2f%% %8d (%5d) %8d %8d (%5d) %7.1f%% %12d",
              file.path.getFileName(),
              file.size,
              mib,
              design,
              100.0 * (design - mib) / mib,
              file.length(smallest),
              smallest,
              file.medianLength(),
              file.length(largest),
              largest,
              100.0 * (file.length(largest) / (double) file.length(smallest) - 1.0),
              pieces(file.size)));
    }
    logger.info(
        String.format(
            Locale.ROOT,
            "TOTAL ephemeris %13d %11.2f %12.1f %+7.2f%%   = %.4f GiB",
            total,
            total / MIB,
            estimated,
            100.0 * (estimated - total / MIB) / (total / MIB),
            total / GIB));

    logger.info("orbit file                bytes   points   expected bytes");
    long orbits = 0;
    for (Path orbit : orbitFiles()) {
      long size = Files.size(orbit);
      int points = orbitPointCount(orbit);
      orbits += size;
      logger.info(
          String.format(
              Locale.ROOT,
              "%-20s %10d %8d %16d",
              orbit.getFileName(),
              size,
              points,
              Integer.BYTES + (long) points * 3 * Double.BYTES));
    }
    logger.info(
        String.format(
            Locale.ROOT,
            "TOTAL orbits %d bytes; ephemeris + orbits %d bytes = %.4f GiB",
            orbits,
            total + orbits,
            (total + orbits) / GIB));
  }

  @Test
  @Order(2)
  void bWhetherTheFilesAreWhatTheGeneratorWrites() throws IOException {
    logger.info("=== DATASET / B - the files against the current generator ===");
    String orekit = "sha256:" + production.orekitDataIdSha256Hex();
    double span = datasetEndExclusive.durationFrom(datasetStart);
    logger.info(
        String.format(
            Locale.ROOT,
            "production: zstd level %d, span %s -> %s (TAI), orekit data %s",
            production.zstdLevel(),
            datasetStart.toString(tai),
            datasetEndExclusive.toString(tai),
            orekit));
    logger.info(
        "file           magic version body scale start  chunk(s)  chunks/expected  index@ chunks@"
            + "  contiguous end=size orekit");
    Set<List<String>> coverageLabels = new LinkedHashSet<>();
    for (BodyIndex file : indexes.values()) {
      BodyGenerationParams params = production.paramsByBody().get(file.body);
      int expected = (int) Math.ceil(span / params.chunkDurationSeconds());
      coverageLabels.add(file.labels.subList(0, 3));
      logger.info(
          String.format(
              Locale.ROOT,
              "%-13s %6s %5d.%d %5s %5d %5.1f %8.0f %8d/%-8d %7s %7s %11s %8s %6s",
              file.path.getFileName(),
              file.magicMatches ? "ok" : "BAD",
              file.versionMajor,
              file.versionMinor,
              file.bodyId == file.body.ordinal() ? "ok" : String.valueOf(file.bodyId),
              file.timeScale,
              file.startOffset,
              file.chunkDuration,
              file.chunkCount,
              expected,
              file.indexOffset == file.headerLength ? "ok" : "BAD",
              file.chunksOffset == file.indexOffset + (long) file.chunkCount * INDEX_ENTRY_BYTES
                  ? "ok"
                  : "BAD",
              file.contiguous() ? "yes" : "NO",
              file.end() == file.size ? "yes" : "NO",
              orekit.equals(file.labels.get(4)) ? "same" : "DIFF"));
    }
    logger.info("coverage, frame labels written in the headers: " + coverageLabels);

    logger.info(
        "file           production dtPv  dtRot  chunk(s) | read in the sample: dtPv  dtRot"
            + "  chunk(s)  nPv  nRot | match");
    int matching = 0;
    for (BodyIndex file : indexes.values()) {
      BodyGenerationParams params = production.paramsByBody().get(file.body);
      Set<Double> dtPv = new LinkedHashSet<>();
      Set<Double> dtRot = new LinkedHashSet<>();
      Set<Double> durations = new LinkedHashSet<>();
      Set<Integer> pvSamples = new LinkedHashSet<>();
      Set<Integer> rotSamples = new LinkedHashSet<>();
      for (int chunkId : file.sample()) {
        byte[] chunk = file.chunk(chunkId);
        Block pv = Block.pv(chunk);
        Block rot = Block.rot(chunk);
        durations.add(le(chunk).getDouble(12));
        dtPv.add(pv.dt());
        dtRot.add(rot.dt());
        pvSamples.add(pv.samples());
        rotSamples.add(rot.samples());
      }
      boolean match =
          dtPv.equals(Set.of(params.dtPvSeconds()))
              && dtRot.equals(Set.of(params.dtRotSeconds()))
              && durations.equals(Set.of(params.chunkDurationSeconds()));
      if (match) {
        matching++;
      }
      logger.info(
          String.format(
              Locale.ROOT,
              "%-13s %15.0f %6.0f %9.0f | %24s %6s %9s %5s %5s | %s",
              file.path.getFileName(),
              params.dtPvSeconds(),
              params.dtRotSeconds(),
              params.chunkDurationSeconds(),
              dtPv,
              dtRot,
              durations,
              pvSamples,
              rotSamples,
              match ? "yes" : "NO"));
    }
    logger.info(
        String.format(
            Locale.ROOT,
            "VERDICT B: the local files carry the current settings for %d of %d bodies",
            matching,
            indexes.size()));
  }

  @Test
  @Order(3)
  void cWhatCompressionWouldSave() throws Exception {
    logger.info("=== DATASET / C - deflate on the whole files, zstd 19 and 22 on the sample ===");
    List<Path> files = allFiles();
    List<Path> largestFirst = new ArrayList<>(files);
    largestFirst.sort(Comparator.comparingLong(Tec1DatasetBaselineProbe::sizeOf).reversed());
    int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    Map<Path, Future<Long>> standard = new LinkedHashMap<>();
    Map<Path, Future<Long>> best = new LinkedHashMap<>();
    long started = System.nanoTime();
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      for (Path file : largestFirst) {
        standard.put(file, pool.submit(() -> deflatedSize(file, Deflater.DEFAULT_COMPRESSION)));
        best.put(file, pool.submit(() -> deflatedSize(file, Deflater.BEST_COMPRESSION)));
      }
    }
    double seconds = (System.nanoTime() - started) / 1e9;

    logger.info(
        "file                         bytes   deflate(default)    gain       deflate(9)    gain");
    long total = 0;
    long totalStandard = 0;
    long totalBest = 0;
    for (Path file : files) {
      long size = sizeOf(file);
      long deflated = standard.get(file).get();
      long deflatedBest = best.get(file).get();
      total += size;
      totalStandard += deflated;
      totalBest += deflatedBest;
      logger.info(
          String.format(
              Locale.ROOT,
              "%-20s %15d %18d %+6.2f%% %16d %+6.2f%%",
              file.getFileName(),
              size,
              deflated,
              100.0 * gain(size, deflated),
              deflatedBest,
              100.0 * gain(size, deflatedBest)));
    }
    double bestGain = Math.max(gain(total, totalStandard), gain(total, totalBest));
    logger.info(
        String.format(
            Locale.ROOT,
            "TOTAL %29d %18d %+6.2f%% %16d %+6.2f%%   (%d threads, %.1f s wall)",
            total,
            totalStandard,
            100.0 * gain(total, totalStandard),
            totalBest,
            100.0 * gain(total, totalBest),
            threads,
            seconds));
    logger.info(
        String.format(
            Locale.ROOT,
            "VERDICT C: deflate saves at best %.2f%% - %s",
            100.0 * bestGain,
            bestGain < DEFLATE_REOPEN_GAIN
                ? "below 5%, '.bin published unzipped' stands"
                : "5% or more, '.bin published unzipped' is REOPENED"));

    logger.info(
        "file          sampled  ratio z19  ratio z22   extrapolated z19 (MiB)   z22 (MiB)   real (MiB)");
    double total19 = 0;
    double total22 = 0;
    long totalReal = 0;
    for (BodyIndex file : indexes.values()) {
      long current = 0;
      long level19 = 0;
      long level22 = 0;
      for (int chunkId : file.sample()) {
        byte[] chunk = file.chunk(chunkId);
        current += chunk.length;
        level19 += recompressedLength(chunk, 19);
        level22 += recompressedLength(chunk, 22);
      }
      double ratio19 = level19 / (double) current;
      double ratio22 = level22 / (double) current;
      double extrapolated19 = file.chunksOffset + file.chunkBytes() * ratio19;
      double extrapolated22 = file.chunksOffset + file.chunkBytes() * ratio22;
      total19 += extrapolated19;
      total22 += extrapolated22;
      totalReal += file.size;
      logger.info(
          String.format(
              Locale.ROOT,
              "%-13s %7d %10.4f %10.4f %24.1f %11.1f %12.1f",
              file.path.getFileName(),
              file.sample().size(),
              ratio19,
              ratio22,
              extrapolated19 / MIB,
              extrapolated22 / MIB,
              file.size / MIB));
    }
    logger.info(
        String.format(
            Locale.ROOT,
            "TOTAL ephemeris: zstd 19 %.3f GiB, zstd 22 %.3f GiB, real (zstd %d) %.3f GiB",
            total19 / GIB,
            total22 / GIB,
            production.zstdLevel(),
            totalReal / GIB));
  }

  @Test
  @Order(4)
  void dWhatHashingCostsFromTheDisk() throws Exception {
    logger.info("=== DATASET / D - reading and SHA-256 from the disk ===");
    logger.info(
        String.format(
            Locale.ROOT,
            "JVM %s, %d logical processors, %s; buffer %d MiB",
            Runtime.version(),
            Runtime.getRuntime().availableProcessors(),
            System.getProperty("os.name"),
            READ_BUFFER_BYTES >> 20));
    logger.info(
        "file                       MiB  direct(MiB/s)  direct+sha(MiB/s)  normal#1  normal#2"
            + "  same digest  sha256");
    double[] seconds = new double[4];
    long total = 0;
    for (Path file : allFiles()) {
      Pass[] passes = {
        readThrough(file, true, false),
        readThrough(file, true, true),
        readThrough(file, false, true),
        readThrough(file, false, true)
      };
      for (int i = 0; i < passes.length; i++) {
        seconds[i] += passes[i].seconds();
      }
      total += passes[1].bytes();
      boolean same =
          passes[1].sha256().equals(passes[2].sha256())
              && passes[2].sha256().equals(passes[3].sha256());
      logger.info(
          String.format(
              Locale.ROOT,
              "%-20s %10.2f %14.0f %18.0f %9.0f %9.0f %12s  %s",
              file.getFileName(),
              passes[1].bytes() / MIB,
              passes[0].mibPerSecond(),
              passes[1].mibPerSecond(),
              passes[2].mibPerSecond(),
              passes[3].mibPerSecond(),
              same ? "yes" : "NO",
              passes[1].sha256()));
    }
    String[] names = {"direct, no hash", "direct + SHA-256", "normal #1", "normal #2"};
    for (int i = 0; i < names.length; i++) {
      logger.info(
          String.format(
              Locale.ROOT,
              "TOTAL %-17s %8.2f s for %.4f GiB = %.0f MiB/s",
              names[i],
              seconds[i],
              total / GIB,
              total / MIB / seconds[i]));
    }
    logger.info(
        String.format(
            Locale.ROOT, "ADOPTION (direct read + SHA-256 of every file): %.1f s", seconds[1]));
  }

  @Test
  @Order(5)
  void eWhetherTheGeneratorReproducesTheFiles() throws Exception {
    logger.info(
        "=== DATASET / E - chunks recomputed by the current generator against the disk ===");
    logger.info(
        "file          chunk   disk(bytes)    now(bytes)  identical  first diff  crc  pv raw"
            + "  rot raw     ms  sha256(now)");
    int compared = 0;
    int identical = 0;
    for (BodyIndex file : indexes.values()) {
      BodyGenerationParams params = production.paramsByBody().get(file.body);
      for (int chunkId : file.sample()) {
        long started = System.nanoTime();
        BodyFileWriterV1.ChunkResult now =
            new ChunkComputerV1(
                    production, file.body, params, datasetStart, datasetEndExclusive, chunkId)
                .call();
        double ms = (System.nanoTime() - started) / 1e6;
        byte[] disk = file.chunk(chunkId);
        byte[] computed = now.chunkBytes();
        int mismatch = Arrays.mismatch(disk, computed);
        compared++;
        if (mismatch < 0) {
          identical++;
        }
        logger.info(
            String.format(
                Locale.ROOT,
                "%-13s %5d %13d %13d %10s %11s %4s %7s %8s %6.1f  %s",
                file.path.getFileName(),
                chunkId,
                disk.length,
                computed.length,
                mismatch < 0 ? "yes" : "NO",
                mismatch < 0 ? "-" : String.valueOf(mismatch),
                file.crc(chunkId) == now.chunkCrc32() ? "same" : "DIFF",
                sameBytes(
                    Block.pv(disk).raw(disk, PV_SAMPLE_BYTES),
                    Block.pv(computed).raw(computed, PV_SAMPLE_BYTES)),
                sameBytes(
                    Block.rot(disk).raw(disk, ROT_SAMPLE_BYTES),
                    Block.rot(computed).raw(computed, ROT_SAMPLE_BYTES)),
                ms,
                sha256(computed)));
      }
    }
    logger.info(
        String.format(
            Locale.ROOT, "sampled chunks identical to the disk: %d of %d", identical, compared));

    BodyIndex pluto = indexes.get(SolarSystemBody.PLUTO);
    long started = System.nanoTime();
    try (ExecutorService pool = Executors.newFixedThreadPool(production.computeThreads())) {
      new BodyFileWriterV1(
              production,
              SolarSystemBody.PLUTO,
              production.paramsByBody().get(SolarSystemBody.PLUTO),
              datasetStart,
              datasetEndExclusive,
              datasetEndExclusive.durationFrom(datasetStart),
              pool,
              new Semaphore(production.maxChunksInFlightGlobal(), true))
          .generateAndWrite();
    }
    double seconds = (System.nanoTime() - started) / 1e9;
    Path regenerated = production.outputDir().resolve(pluto.path.getFileName());
    long mismatch = Files.mismatch(pluto.path, regenerated);
    logger.info(
        String.format(
            Locale.ROOT,
            "PLUTO.bin regenerated whole in %.1f s: %d bytes (disk %d), sha256 %s (disk %s),"
                + " first diff %s in %s",
            seconds,
            Files.size(regenerated),
            pluto.size,
            readThrough(regenerated, false, true).sha256(),
            readThrough(pluto.path, false, true).sha256(),
            mismatch < 0 ? "-" : String.valueOf(mismatch),
            region(pluto, mismatch)));
    logger.info(
        String.format(
            Locale.ROOT,
            "VERDICT E (this run): %s",
            identical == compared && mismatch < 0
                ? "the disk is reproduced byte for byte"
                : "the disk is NOT reproduced - compare the sha256(now) column with a second run"
                    + " (cleanTest) to tell a drift from non-determinism"));
  }

  @Test
  @Order(6)
  void fWhatTheReaderDoesWithoutData() throws IOException {
    logger.info("=== DATASET / F - the reader without a complete dataset ===");
    BodyIndex pluto = indexes.get(SolarSystemBody.PLUTO);
    long inIndex = (pluto.indexOffset + pluto.chunksOffset) / 2;
    long inChunks = pluto.size / 2;
    describe("absent directory", scratch.resolve("absent"));
    describe("empty directory", Files.createDirectories(scratch.resolve("empty")));
    describe(
        "PLUTO.bin alone, cut inside its index at " + inIndex + " bytes",
        truncatedCopy(pluto, inIndex, "cut-in-index"));
    describe(
        "PLUTO.bin alone, cut halfway through its chunks at " + inChunks + " bytes",
        truncatedCopy(pluto, inChunks, "cut-in-chunks"));
  }

  /** Opens a source on {@code directory} and samples two bodies early and late in the span. */
  private static void describe(String state, Path directory) {
    logger.info("--- " + state + " (" + directory + ")");
    List<AbsoluteDate> dates =
        List.of(
            new AbsoluteDate(2026, 10, 9, 0, 0, 0.0, tai),
            new AbsoluteDate(2090, 1, 1, 0, 0, 0.0, tai));
    try (DatasetEphemerisSource source = new DatasetEphemerisSource(directory, CHUNKS_IN_CACHE)) {
      logger.info("constructor returned");
      for (SolarSystemBody body : List.of(SolarSystemBody.PLUTO, SolarSystemBody.EARTH)) {
        for (AbsoluteDate date : dates) {
          logger.info(
              String.format(
                  Locale.ROOT,
                  "sampleIcrf(%s, %s): %s",
                  body,
                  date.toString(tai),
                  outcome(source, body, date)));
        }
      }
    } catch (RuntimeException e) {
      logger.info("constructor threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private static String outcome(
      DatasetEphemerisSource source, SolarSystemBody body, AbsoluteDate date) {
    try {
      BodySample sample = source.sampleIcrf(body, date);
      return String.format(
          Locale.ROOT, "ok, |r| = %.0f km", sample.pvIcrf().getPosition().getNorm() / 1000.0);
    } catch (RuntimeException e) {
      return "threw " + e.getClass().getSimpleName() + ": " + e.getMessage();
    }
  }

  private static Path truncatedCopy(BodyIndex file, long length, String name) throws IOException {
    Path directory = Files.createDirectories(scratch.resolve(name));
    Path copy = directory.resolve(file.path.getFileName());
    try (FileChannel in = FileChannel.open(file.path, StandardOpenOption.READ);
        FileChannel out =
            FileChannel.open(copy, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      long copied = 0;
      while (copied < length) {
        copied += in.transferTo(copied, length - copied, out);
      }
    }
    return directory;
  }

  /**
   * One sequential pass over a file, timed, optionally hashed. {@code direct} opens it with {@link
   * ExtendedOpenOption#DIRECT}, which requires reads into a buffer aligned on the store's block
   * size and sized in whole blocks. The last read of the file returns the remainder and leaves the
   * position unaligned, so the loop stops on the file's size: one more read to meet the end of file
   * would be refused.
   */
  private static Pass readThrough(Path file, boolean direct, boolean hash)
      throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = hash ? MessageDigest.getInstance("SHA-256") : null;
    int block = (int) Files.getFileStore(file).getBlockSize();
    ByteBuffer buffer = ByteBuffer.allocateDirect(READ_BUFFER_BYTES + block).alignedSlice(block);
    OpenOption[] options =
        direct
            ? new OpenOption[] {StandardOpenOption.READ, ExtendedOpenOption.DIRECT}
            : new OpenOption[] {StandardOpenOption.READ};
    long total = 0;
    long started = System.nanoTime();
    try (FileChannel channel = FileChannel.open(file, options)) {
      long size = channel.size();
      while (total < size) {
        buffer.clear().limit(READ_BUFFER_BYTES);
        int read = channel.read(buffer);
        if (read < 0) {
          throw new IOException("Unexpected end of " + file + " at " + total);
        }
        total += read;
        if (digest != null) {
          buffer.flip();
          digest.update(buffer);
        }
      }
    }
    double seconds = (System.nanoTime() - started) / 1e9;
    return new Pass(
        total, seconds, digest == null ? null : HexFormat.of().formatHex(digest.digest()));
  }

  private static long deflatedSize(Path file, int level) throws IOException {
    Deflater deflater = new Deflater(level, true);
    byte[] in = new byte[DEFLATE_BUFFER_BYTES];
    byte[] out = new byte[DEFLATE_BUFFER_BYTES];
    long total = 0;
    try (InputStream stream = Files.newInputStream(file)) {
      int read = stream.read(in);
      while (read >= 0) {
        deflater.setInput(in, 0, read);
        while (!deflater.needsInput()) {
          total += deflater.deflate(out);
        }
        read = stream.read(in);
      }
      deflater.finish();
      while (!deflater.finished()) {
        total += deflater.deflate(out);
      }
    } finally {
      deflater.end();
    }
    return total;
  }

  /** The chunk's length once its PV and rotation payloads are recompressed at {@code level}. */
  private static long recompressedLength(byte[] chunk, int level) {
    Block pv = Block.pv(chunk);
    Block rot = Block.rot(chunk);
    return (long) chunk.length
        - pv.compressedLength()
        - rot.compressedLength()
        + Zstd.compress(pv.raw(chunk, PV_SAMPLE_BYTES), level).length
        + Zstd.compress(rot.raw(chunk, ROT_SAMPLE_BYTES), level).length;
  }

  private static double gain(long size, long compressed) {
    return 1.0 - compressed / (double) size;
  }

  /** Pieces a file is cut into for each to stay strictly under the asset limit. */
  private static long pieces(long size) {
    return (size + ASSET_LIMIT_BYTES - 2) / (ASSET_LIMIT_BYTES - 1);
  }

  private static String region(BodyIndex file, long position) {
    if (position < 0) {
      return "-";
    }
    if (position < file.headerLength) {
      return "header";
    }
    return position < file.chunksOffset ? "index" : "chunks";
  }

  private static String sameBytes(byte[] a, byte[] b) {
    return Arrays.equals(a, b) ? "same" : "DIFF";
  }

  private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private static List<Path> allFiles() throws IOException {
    List<Path> files = new ArrayList<>();
    for (BodyIndex file : indexes.values()) {
      files.add(file.path);
    }
    files.addAll(orbitFiles());
    return files;
  }

  private static List<Path> orbitFiles() throws IOException {
    try (Stream<Path> files = Files.list(OrbitlabPath.ORBITS_PATH)) {
      return files.filter(p -> p.getFileName().toString().endsWith("-orbit.bin")).sorted().toList();
    }
  }

  /** The point count heading an orbit file, written big-endian by a {@code DataOutputStream}. */
  private static int orbitPointCount(Path orbit) throws IOException {
    try (FileChannel channel = FileChannel.open(orbit, StandardOpenOption.READ)) {
      return read(channel, 0, Integer.BYTES).order(ByteOrder.BIG_ENDIAN).getInt();
    }
  }

  private static long sizeOf(Path file) {
    try {
      return Files.size(file);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot size " + file, e);
    }
  }

  private static ByteBuffer le(byte[] bytes) {
    return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
  }

  private static ByteBuffer read(FileChannel channel, long position, int length)
      throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
    long at = position;
    while (buffer.hasRemaining()) {
      int read = channel.read(buffer, at);
      if (read < 0) {
        throw new IOException("Unexpected end of file at " + at);
      }
      at += read;
    }
    return buffer.flip();
  }

  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  private static Object invokePrivateStatic(
      Class<?> owner, String name, Class<?>[] types, Object... args)
      throws ReflectiveOperationException {
    Method method = owner.getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method.invoke(null, args);
  }

  private static Map<SolarSystemBody, Double> estimatedMib() {
    Map<SolarSystemBody, Double> mib = new EnumMap<>(SolarSystemBody.class);
    mib.put(SolarSystemBody.SUN, 120.4);
    mib.put(SolarSystemBody.MERCURY, 1385.8);
    mib.put(SolarSystemBody.VENUS, 525.1);
    mib.put(SolarSystemBody.EARTH, 680.2);
    mib.put(SolarSystemBody.MARS, 427.6);
    mib.put(SolarSystemBody.JUPITER, 512.5);
    mib.put(SolarSystemBody.SATURN, 430.3);
    mib.put(SolarSystemBody.URANUS, 163.0);
    mib.put(SolarSystemBody.NEPTUNE, 87.3);
    mib.put(SolarSystemBody.PLUTO, 31.7);
    mib.put(SolarSystemBody.MOON, 2853.4);
    return mib;
  }

  /**
   * One body file as its own header and index describe it, read here rather than through the
   * application's parser so that what the parser skips is printed too.
   */
  private static final class BodyIndex {
    final SolarSystemBody body;
    final Path path;
    final long size;
    final boolean magicMatches;
    final int versionMajor;
    final int versionMinor;
    final int bodyId;
    final int timeScale;
    final double startOffset;
    final List<String> labels;
    final double chunkDuration;
    final int chunkCount;
    final long indexOffset;
    final long chunksOffset;
    final int headerLength;
    private final long[] offsets;
    private final int[] lengths;
    private final int[] crcs;

    BodyIndex(SolarSystemBody body, Path path) throws IOException {
      this.body = body;
      this.path = path;
      try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
        size = channel.size();
        ByteBuffer header = read(channel, 0, (int) Math.min(size, 4096));
        byte[] magic = new byte[MAGIC.length];
        header.get(magic);
        magicMatches = Arrays.equals(magic, MAGIC);
        versionMajor = header.getInt();
        versionMinor = header.getInt();
        bodyId = header.getInt();
        timeScale = header.getInt();
        startOffset = header.getDouble();
        List<String> strings = new ArrayList<>();
        for (int i = 0; i < HEADER_LABELS; i++) {
          byte[] bytes = new byte[header.getInt()];
          header.get(bytes);
          strings.add(new String(bytes, StandardCharsets.UTF_8));
        }
        labels = List.copyOf(strings);
        chunkDuration = header.getDouble();
        chunkCount = header.getInt();
        indexOffset = header.getLong();
        chunksOffset = header.getLong();
        headerLength = header.position() + Integer.BYTES;
        ByteBuffer index = read(channel, indexOffset, chunkCount * INDEX_ENTRY_BYTES);
        offsets = new long[chunkCount];
        lengths = new int[chunkCount];
        crcs = new int[chunkCount];
        for (int i = 0; i < chunkCount; i++) {
          index.getDouble();
          offsets[i] = index.getLong();
          lengths[i] = index.getInt();
          crcs[i] = index.getInt();
        }
      }
    }

    int length(int chunkId) {
      return lengths[chunkId];
    }

    int crc(int chunkId) {
      return crcs[chunkId];
    }

    byte[] chunk(int chunkId) throws IOException {
      try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
        return read(channel, offsets[chunkId], lengths[chunkId]).array();
      }
    }

    boolean contiguous() {
      long expected = chunksOffset;
      for (int i = 0; i < chunkCount; i++) {
        if (offsets[i] != expected) {
          return false;
        }
        expected += lengths[i];
      }
      return true;
    }

    long end() {
      return offsets[chunkCount - 1] + lengths[chunkCount - 1];
    }

    long chunkBytes() {
      long total = 0;
      for (int length : lengths) {
        total += length;
      }
      return total;
    }

    int smallestChunk() {
      int smallest = 0;
      for (int i = 1; i < chunkCount; i++) {
        if (lengths[i] < lengths[smallest]) {
          smallest = i;
        }
      }
      return smallest;
    }

    int largestChunk() {
      int largest = 0;
      for (int i = 1; i < chunkCount; i++) {
        if (lengths[i] > lengths[largest]) {
          largest = i;
        }
      }
      return largest;
    }

    int medianLength() {
      int[] sorted = lengths.clone();
      Arrays.sort(sorted);
      return sorted[sorted.length / 2];
    }

    /** The first two chunks, one in the middle, the last, the smallest and the largest. */
    Set<Integer> sample() {
      Set<Integer> ids = new LinkedHashSet<>();
      ids.add(0);
      ids.add(1);
      ids.add(Math.min(MIDDLE_CHUNK, chunkCount - 1));
      ids.add(chunkCount - 1);
      ids.add(smallestChunk());
      ids.add(largestChunk());
      return ids;
    }
  }
}
