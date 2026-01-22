package com.smousseur.orbitlab.tools.ephemerisgen;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.zip.CRC32;
import org.orekit.time.AbsoluteDate;

final class BodyFileWriterV1 {

  private static final byte[] MAGIC = new byte[] {'O','R','B','L','_','E','P','H'};

  private final GeneratorConfigV1 cfg;
  private final SolarSystemBody body;
  private final BodyGenerationParams params;
  private final AbsoluteDate tStart;
  private final AbsoluteDate tEndExclusive;
  private final double datasetEndOffsetSecondsExclusive;

  private final ExecutorService computePool;
  private final Semaphore globalInFlight;

  BodyFileWriterV1(
      GeneratorConfigV1 cfg,
      SolarSystemBody body,
      BodyGenerationParams params,
      AbsoluteDate tStart,
      AbsoluteDate tEndExclusive,
      double datasetEndOffsetSecondsExclusive,
      ExecutorService computePool,
      Semaphore globalInFlight) {
    this.cfg = Objects.requireNonNull(cfg, "cfg");
    this.body = Objects.requireNonNull(body, "body");
    this.params = Objects.requireNonNull(params, "params");
    this.tStart = Objects.requireNonNull(tStart, "tStart");
    this.tEndExclusive = Objects.requireNonNull(tEndExclusive, "tEndExclusive");
    this.datasetEndOffsetSecondsExclusive = datasetEndOffsetSecondsExclusive;
    this.computePool = Objects.requireNonNull(computePool, "computePool");
    this.globalInFlight = Objects.requireNonNull(globalInFlight, "globalInFlight");
  }

  void generateAndWrite() throws Exception {
    Path out = cfg.outputDir().resolve("ephem").resolve(body.name() + ".bin");
    java.nio.file.Files.createDirectories(out.getParent());

    double chunkDur = params.chunkDurationSeconds();
    int chunkCount = (int) Math.ceil(datasetEndOffsetSecondsExclusive / chunkDur);

    // In-flight permits for this body (bounds memory for its buffered chunkBytes).
    Semaphore bodyInFlight = new Semaphore(cfg.maxChunksInFlightPerBody(), true);

    // Prepare file + reserve header + index placeholders.
    try (FileChannel ch =
        FileChannel.open(
            out,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE)) {

      // Build placeholder header (index offsets unknown yet, but we can write placeholders then rewrite later).
      byte[] headerPlaceholder = buildHeaderBytes(chunkDur, chunkCount, 0L, 0L, /*headerCrc*/0);
      long headerSize = headerPlaceholder.length;

      long chunkIndexOffset = headerSize;
      long chunkIndexSize = (long) chunkCount * (8 + 8 + 4 + 4); // f64 + u64 + u32 + u32
      long chunksOffset = chunkIndexOffset + chunkIndexSize;

      // Write placeholder header + placeholder index.
      ch.write(ByteBuffer.wrap(headerPlaceholder));
      ch.position(chunkIndexOffset);
      ch.write(ByteBuffer.allocate((int) chunkIndexSize)); // zero-filled placeholder

      // Now write chunks sequentially, but compute in parallel with deterministic backpressure.
      long[] fileOffsets = new long[chunkCount];
      int[] byteLengths = new int[chunkCount];
      int[] crcs = new int[chunkCount];
      double[] startOffsets = new double[chunkCount];

      Future<ChunkResult>[] futures = new Future[chunkCount];

      int nextToSubmit = 0;
      int nextToWrite = 0;

      while (nextToWrite < chunkCount) {

        while (nextToSubmit < chunkCount && (nextToSubmit - nextToWrite) < cfg.maxChunksInFlightPerBody()) {
          bodyInFlight.acquire();
          globalInFlight.acquire();

          int chunkId = nextToSubmit;
          ChunkComputerV1 task =
              new ChunkComputerV1(cfg, body, params, tStart, tEndExclusive, chunkId);
          futures[chunkId] = computePool.submit(task);
          nextToSubmit++;
        }

        // Write next chunk strictly in order.
        ChunkResult r = futures[nextToWrite].get();

        startOffsets[nextToWrite] = r.chunkStartOffsetSeconds();
        fileOffsets[nextToWrite] = ch.position();
        byteLengths[nextToWrite] = r.chunkBytes().length;
        crcs[nextToWrite] = r.chunkCrc32();

        ch.write(ByteBuffer.wrap(r.chunkBytes()));

        // Release permits only once the chunk is written (bounds buffered memory).
        globalInFlight.release();
        bodyInFlight.release();

        nextToWrite++;
      }

      // Patch index
      ch.position(chunkIndexOffset);
      ByteBuffer idx = ByteBuffer.allocate((int) chunkIndexSize).order(ByteOrder.LITTLE_ENDIAN);
      for (int chunkId = 0; chunkId < chunkCount; chunkId++) {
        idx.putDouble(startOffsets[chunkId]);
        idx.putLong(fileOffsets[chunkId]);
        idx.putInt(byteLengths[chunkId]);
        idx.putInt(crcs[chunkId]);
      }
      idx.flip();
      ch.write(idx);

      // Rewrite header with correct offsets + CRC.
      byte[] headerFinal = buildHeaderBytes(chunkDur, chunkCount, chunkIndexOffset, chunksOffset, /*headerCrc*/0);
      int headerCrc = crc32(headerFinal, /*excludeLastU32*/true);
      headerFinal = buildHeaderBytes(chunkDur, chunkCount, chunkIndexOffset, chunksOffset, headerCrc);

      ch.position(0);
      ch.write(ByteBuffer.wrap(headerFinal));
      ch.force(true);
    }
  }

  private byte[] buildHeaderBytes(
      double chunkDurSeconds, int chunkCount, long chunkIndexOffset, long chunksOffset, int headerCrc32) {

    LittleEndianWriter w = new LittleEndianWriter(512);

    w.writeBytes(MAGIC);
    w.writeU32(1); // versionMajor
    w.writeU32(0); // versionMinor

    w.writeU32(body.ordinal()); // bodyId (enum stable by code policy)
    w.writeU32(1); // timeScale: 1 = TAI
    w.writeF64(0.0); // tStartTaiOffsetSeconds

    w.writeStringUtf8("1949-12-31T00:00:00 TAI");
    w.writeStringUtf8("2049-12-31T00:00:00 TAI (exclusive)");
    w.writeStringUtf8("ICRF");
    w.writeStringUtf8("IAU/" + body.name());
    w.writeStringUtf8("sha256:" + cfg.orekitDataIdSha256Hex());

    w.writeF64(chunkDurSeconds);
    w.writeU32(chunkCount);

    w.writeU64(chunkIndexOffset);
    w.writeU64(chunksOffset);

    w.writeU32(headerCrc32);

    return w.toByteArray();
  }

  private static int crc32(byte[] bytes, boolean excludeLastU32) {
    CRC32 crc = new CRC32();
    int len = excludeLastU32 ? (bytes.length - 4) : bytes.length;
    crc.update(bytes, 0, len);
    return (int) crc.getValue();
  }

  record ChunkResult(int chunkId, double chunkStartOffsetSeconds, byte[] chunkBytes, int chunkCrc32) {}
}
