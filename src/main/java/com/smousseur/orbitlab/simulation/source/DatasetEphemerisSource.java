package com.smousseur.orbitlab.simulation.source;

import com.github.luben.zstd.Zstd;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.BodySample;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.PVCoordinates;

/**
 * EphemerisSource backed by the on-disk dataset (*.bin), using FileChannel random access.
 *
 * <p>Threading: designed for single-worker usage (like current SlidingWindowEphemerisBuffer
 * rebuilds). If you later need multi-threaded rebuilds, wrap caches with synchronization or use
 * per-thread instances.
 */
public final class DatasetEphemerisSource
    implements EphemerisSource, PrefetchingEphemerisSource, AutoCloseable {

  private static final byte[] MAGIC = new byte[] {'O', 'R', 'B', 'L', '_', 'E', 'P', 'H'};

  // Header is written with LittleEndianWriter(512) => we assume fixed 512 bytes V1.
  private static final int HEADER_SIZE_BYTES = 512;

  // Chunk header fixed size in writer
  private static final int CHUNK_HEADER_SIZE = 40;

  // Index entry: f64 startOffset + u64 fileOffset + u32 byteLen + u32 crc = 24 bytes
  private static final int INDEX_ENTRY_SIZE = 24;

  // Dataset coverage (as requested)
  private static final TimeScale TAI = TimeScalesFactory.getTAI();
  private static final AbsoluteDate DATASET_T_START = new AbsoluteDate(1990, 1, 1, 0, 0, 0.0, TAI);
  private static final AbsoluteDate DATASET_T_END_EXCL =
      new AbsoluteDate(2101, 1, 1, 0, 0, 0.0, TAI);

  private final Path datasetDir;
  private final int chunksInCachePerBody;

  private final EnumMap<SolarSystemBody, BodyFile> bodyFiles = new EnumMap<>(SolarSystemBody.class);

  public DatasetEphemerisSource(Path datasetDir, int chunksInCachePerBody) {
    this.datasetDir = Objects.requireNonNull(datasetDir, "datasetDir").toAbsolutePath().normalize();
    if (chunksInCachePerBody < 1) {
      throw new IllegalArgumentException("chunksInCachePerBody must be >= 1");
    }
    this.chunksInCachePerBody = chunksInCachePerBody;

    if (!Files.isDirectory(this.datasetDir)) {
      throw new OrbitlabException("Ephemeris dataset directory not found: " + this.datasetDir);
    }

    // Open & index all bodies upfront (fail-fast).
    for (SolarSystemBody b : SolarSystemBody.values()) {
      Path p = this.datasetDir.resolve(b.name() + ".bin");
      if (!Files.isRegularFile(p)) {
        throw new OrbitlabException("Missing ephemeris dataset file: " + p);
      }
      try {
        bodyFiles.put(b, BodyFile.open(b, p, chunksInCachePerBody));
      } catch (IOException e) {
        throw new OrbitlabException("Failed to open ephemeris dataset file: " + p + " (" + e + ")");
      } catch (RuntimeException e) {
        throw new OrbitlabException(
            "Invalid ephemeris dataset file: " + p + " (" + e.getMessage() + ")");
      }
    }
  }

  @Override
  public BodySample sampleIcrf(SolarSystemBody body, AbsoluteDate date) {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(date, "date");

    if (date.compareTo(DATASET_T_START) < 0 || date.compareTo(DATASET_T_END_EXCL) >= 0) {
      throw new OrbitlabException(
          "Date outside ephemeris dataset range: "
              + fmtDate(date)
              + " (expected ["
              + fmtDate(DATASET_T_START)
              + ", "
              + fmtDate(DATASET_T_END_EXCL)
              + "))");
    }

    BodyFile f = bodyFiles.get(body);
    if (f == null) {
      throw new OrbitlabException("No dataset file loaded for body=" + body);
    }

    double offsetSeconds = date.durationFrom(DATASET_T_START);
    int chunkId = f.chunkIdForOffset(offsetSeconds);

    DecodedChunk chunk = f.getDecodedChunk(chunkId);

    // Prefetch neighbor chunk in the "forward" direction by default; actual speed-aware prefetch
    // can be added later (e.g. by exposing speed into the source or by a small prefetch API called
    // by the worker).
    f.prefetchDecodedChunk(chunkId + 1);

    PVCoordinates pv = chunk.samplePv(offsetSeconds);
    Rotation rot = chunk.sampleRot(offsetSeconds);

    return new BodySample(date, pv, rot);
  }

  @Override
  public void prefetch(SolarSystemBody body, AbsoluteDate start, AbsoluteDate end, double speed) {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");

    BodyFile f = bodyFiles.get(body);
    if (f == null) return;

    AbsoluteDate a = (start.compareTo(end) <= 0) ? start : end;
    AbsoluteDate b = (start.compareTo(end) <= 0) ? end : start;

    if (b.compareTo(DATASET_T_START) < 0 || a.compareTo(DATASET_T_END_EXCL) >= 0) {
      return; // outside dataset => ignore hint
    }

    double aOff = Math.max(0.0, a.durationFrom(DATASET_T_START));
    double bOff = Math.max(0.0, b.durationFrom(DATASET_T_START));

    int c0 = f.chunkIdForOffset(aOff);
    int c1 = f.chunkIdForOffset(bOff);

    // Margin: tune this. 2-4 is usually enough to hide stalls.
    int margin = 3;

    int from = Math.min(c0, c1);
    int to = Math.max(c0, c1);

    if (speed >= 0.0) {
      to = Math.min(f.chunkCount - 1, to + margin);
    } else {
      from = Math.max(0, from - margin);
    }

    for (int c = from; c <= to; c++) {
      f.prefetchDecodedChunk(c);
    }
  }

  @Override
  public void close() {
    for (BodyFile bf : bodyFiles.values()) {
      try {
        bf.close();
      } catch (Exception ignored) {
        // ignore
      }
    }
    bodyFiles.clear();
  }

  private static String fmtDate(AbsoluteDate d) {
    // Human readable without depending on Orekit formatting utilities.
    // This is best-effort; exact formatting isn't critical for an error message.
    double taiOffset = d.durationFrom(AbsoluteDate.J2000_EPOCH);
    return "TAI+(" + taiOffset + "s from J2000)";
  }

  // ---- internals

  private static final class BodyFile implements Closeable {
    private final SolarSystemBody body;
    private final Path path;
    private final FileChannel ch;

    private final double chunkDurationSeconds;
    private final int chunkCount;

    private final long[] chunkFileOffsets;
    private final int[] chunkByteLengths;

    private final LruCache<Integer, DecodedChunk> decodedChunkCache;

    private BodyFile(
        SolarSystemBody body,
        Path path,
        FileChannel ch,
        double chunkDurationSeconds,
        int chunkCount,
        long[] chunkFileOffsets,
        int[] chunkByteLengths,
        int chunksInCache) {
      this.body = body;
      this.path = path;
      this.ch = ch;
      this.chunkDurationSeconds = chunkDurationSeconds;
      this.chunkCount = chunkCount;
      this.chunkFileOffsets = chunkFileOffsets;
      this.chunkByteLengths = chunkByteLengths;
      this.decodedChunkCache = new LruCache<>(chunksInCache);
    }

    static BodyFile open(SolarSystemBody body, Path path, int chunksInCache) throws IOException {
      FileChannel ch = FileChannel.open(path, StandardOpenOption.READ);

      try {
        HeaderV1 hdr = readHeaderV1(body, ch, path);
        IndexV1 idx = readIndexV1(ch, hdr);

        return new BodyFile(
            body,
            path,
            ch,
            hdr.chunkDurationSeconds,
            hdr.chunkCount,
            idx.fileOffsets,
            idx.byteLengths,
            chunksInCache);
      } catch (RuntimeException e) {
        try {
          ch.close();
        } catch (IOException ignored) {
          // ignore
        }
        throw e;
      }
    }

    int chunkIdForOffset(double offsetSeconds) {
      int id = (int) Math.floor(offsetSeconds / chunkDurationSeconds);
      if (id < 0) return 0;
      if (id >= chunkCount) return chunkCount - 1;
      return id;
    }

    DecodedChunk getDecodedChunk(int chunkId) {
      DecodedChunk cached = decodedChunkCache.get(chunkId);
      if (cached != null) return cached;

      DecodedChunk decoded = decodeChunk(chunkId);
      decodedChunkCache.put(chunkId, decoded);
      return decoded;
    }

    void prefetchDecodedChunk(int chunkId) {
      if (chunkId < 0 || chunkId >= chunkCount) return;
      if (decodedChunkCache.containsKey(chunkId)) return;

      // Best-effort prefetch: decode now (still on worker thread in V1)
      // Later we can make this async.
      try {
        DecodedChunk decoded = decodeChunk(chunkId);
        decodedChunkCache.put(chunkId, decoded);
      } catch (RuntimeException ignored) {
        // ignore prefetch failures; the real access will throw with better context
      }
    }

    private DecodedChunk decodeChunk(int chunkId) {
      if (chunkId < 0 || chunkId >= chunkCount) {
        throw new OrbitlabException("ChunkId out of range for " + body + ": " + chunkId);
      }

      long off = chunkFileOffsets[chunkId];
      int len = chunkByteLengths[chunkId];

      byte[] chunkBytes = readFully(off, len);

      ByteBuffer bb = ByteBuffer.wrap(chunkBytes).order(ByteOrder.LITTLE_ENDIAN);

      int gotChunkId = bb.getInt(0);
      if (gotChunkId != chunkId) {
        throw new OrbitlabException(
            "Corrupted chunk header for "
                + body
                + " in "
                + path
                + ": expected chunkId="
                + chunkId
                + " got="
                + gotChunkId);
      }

      double chunkStartOffset = bb.getDouble(4);
      double chunkDur = bb.getDouble(12);

      int pvBlockOffset = bb.getInt(20);
      int pvBlockLen = bb.getInt(24);
      int rotBlockOffset = bb.getInt(28);
      int rotBlockLen = bb.getInt(32);

      if (pvBlockOffset < CHUNK_HEADER_SIZE || rotBlockOffset < CHUNK_HEADER_SIZE) {
        throw new OrbitlabException(
            "Invalid block offsets in chunk " + chunkId + " for " + body + " (" + path + ")");
      }
      if (pvBlockOffset + pvBlockLen > chunkBytes.length
          || rotBlockOffset + rotBlockLen > chunkBytes.length) {
        throw new OrbitlabException(
            "Invalid block lengths in chunk " + chunkId + " for " + body + " (" + path + ")");
      }

      PvBlock pv = parsePvBlock(bb, pvBlockOffset, pvBlockLen);
      RotBlock rot = parseRotBlock(bb, rotBlockOffset, rotBlockLen);

      return new DecodedChunk(body, chunkStartOffset, chunkDur, pv, rot);
    }

    private byte[] readFully(long fileOffset, int length) {
      try {
        ByteBuffer dst = ByteBuffer.allocate(length);
        int read = 0;
        while (read < length) {
          int r = ch.read(dst, fileOffset + read);
          if (r < 0) {
            throw new OrbitlabException("Unexpected EOF reading " + body + " (" + path + ")");
          }
          read += r;
        }
        return dst.array();
      } catch (IOException e) {
        throw new OrbitlabException("I/O error reading " + body + " (" + path + "): " + e);
      }
    }

    @Override
    public void close() throws IOException {
      ch.close();
    }
  }

  private record HeaderV1(double chunkDurationSeconds, int chunkCount, long chunkIndexOffset) {}

  private record IndexV1(long[] fileOffsets, int[] byteLengths) {}

  private static HeaderV1 readHeaderV1(SolarSystemBody expectedBody, FileChannel ch, Path path)
      throws IOException {

    ch.position(0);

    CountingReader r = new CountingReader(ch);

    byte[] magic = r.readBytes(8);
    for (int i = 0; i < MAGIC.length; i++) {
      if (magic[i] != MAGIC[i]) {
        throw new OrbitlabException("Invalid magic for " + expectedBody + " (" + path + ")");
      }
    }

    int versionMajor = r.readU32();
    int versionMinor = r.readU32();
    if (versionMajor != 1 || versionMinor != 0) {
      throw new OrbitlabException(
          "Unsupported ephemeris dataset version for "
              + expectedBody
              + " ("
              + path
              + "): "
              + versionMajor
              + "."
              + versionMinor);
    }

    int bodyId = r.readU32();
    if (bodyId != expectedBody.ordinal()) {
      throw new OrbitlabException(
          "BodyId mismatch for "
              + expectedBody
              + " ("
              + path
              + "): header bodyId="
              + bodyId
              + " expected="
              + expectedBody.ordinal());
    }

    int timeScaleId = r.readU32(); // writer uses 1 = TAI
    if (timeScaleId != 1) {
      throw new OrbitlabException("Unsupported timeScaleId=" + timeScaleId + " in " + path);
    }

    r.readF64(); // tStartTaiOffsetSeconds (unused in V1)

    // Skip variable-length strings (same order as writer)
    r.readStringUtf8(); // datasetStart
    r.readStringUtf8(); // datasetEndExclusive
    r.readStringUtf8(); // frameId ("ICRF")
    r.readStringUtf8(); // bodyFixedFrameId ("IAU/...")
    r.readStringUtf8(); // orekitDataId ("sha256:...")

    double chunkDur = r.readF64();
    long chunkCountUnsigned = Integer.toUnsignedLong(r.readU32());
    long chunkIndexOffset = r.readU64();

    r.readU64(); // chunksOffset (unused)
    r.readU32(); // headerCrc32 (unused)

    if (!Double.isFinite(chunkDur) || chunkDur <= 0.0) {
      throw new OrbitlabException(
          "Invalid chunk duration for " + expectedBody + " (" + path + "): " + chunkDur);
    }
    if (chunkCountUnsigned < 1 || chunkCountUnsigned > Integer.MAX_VALUE) {
      throw new OrbitlabException(
          "Invalid chunk count for " + expectedBody + " (" + path + "): " + chunkCountUnsigned);
    }

    long fileSize = ch.size();
    if (chunkIndexOffset <= 0 || chunkIndexOffset >= fileSize) {
      throw new OrbitlabException(
          "Invalid chunk index offset for "
              + expectedBody
              + " ("
              + path
              + "): "
              + chunkIndexOffset);
    }

    return new HeaderV1(chunkDur, (int) chunkCountUnsigned, chunkIndexOffset);
  }

  private static final class CountingReader {
    private final FileChannel ch;

    CountingReader(FileChannel ch) {
      this.ch = ch;
    }

    byte[] readBytes(int n) throws IOException {
      ByteBuffer bb = ByteBuffer.allocate(n);
      readFully(ch, bb);
      return bb.array();
    }

    int readU32() throws IOException {
      ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
      readFully(ch, bb);
      bb.flip();
      return bb.getInt();
    }

    long readU64() throws IOException {
      ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
      readFully(ch, bb);
      bb.flip();
      return bb.getLong();
    }

    double readF64() throws IOException {
      ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
      readFully(ch, bb);
      bb.flip();
      return bb.getDouble();
    }

    String readStringUtf8() throws IOException {
      long len = Integer.toUnsignedLong(readU32());
      if (len > 10_000_000L) {
        throw new OrbitlabException("Suspicious string length in header: " + len);
      }
      byte[] b = readBytes((int) len);
      return new String(b, StandardCharsets.UTF_8);
    }

    private static void readFully(FileChannel ch, ByteBuffer dst) throws IOException {
      while (dst.hasRemaining()) {
        int r = ch.read(dst);
        if (r < 0) {
          throw new OrbitlabException("Unexpected EOF while reading dataset file");
        }
      }
    }
  }

  private static IndexV1 readIndexV1(FileChannel ch, HeaderV1 hdr) throws IOException {
    long indexSize = (long) hdr.chunkCount * INDEX_ENTRY_SIZE;

    if (indexSize > Integer.MAX_VALUE) {
      throw new OrbitlabException("Chunk index too large: " + indexSize + " bytes");
    }

    ByteBuffer idx = ByteBuffer.allocate((int) indexSize).order(ByteOrder.LITTLE_ENDIAN);

    int read = 0;
    while (read < (int) indexSize) {
      int r = ch.read(idx, hdr.chunkIndexOffset + read);
      if (r < 0) break;
      read += r;
    }
    if (read != (int) indexSize) {
      throw new OrbitlabException("Failed to read full chunk index");
    }

    idx.flip();

    long[] fileOffsets = new long[hdr.chunkCount];
    int[] byteLengths = new int[hdr.chunkCount];

    for (int i = 0; i < hdr.chunkCount; i++) {
      idx.getDouble(); // startOffsetSeconds (unused in V1 reader)
      fileOffsets[i] = idx.getLong();
      byteLengths[i] = idx.getInt();
      idx.getInt(); // crc (unused in V1 reader)
    }

    return new IndexV1(fileOffsets, byteLengths);
  }

  private record PvBlock(double t0, double dt, int n, double[] raw) {}

  private record RotBlock(double t0, double dt, int n, double[] rawQuat) {}

  private static PvBlock parsePvBlock(ByteBuffer chunk, int off, int len) {
    ByteBuffer bb = slice(chunk, off, len).order(ByteOrder.LITTLE_ENDIAN);

    int codecId = bb.getInt();
    if (codecId != 1) {
      throw new OrbitlabException("Unsupported PV codecId=" + codecId);
    }

    double t0 = bb.getDouble();
    double dt = bb.getDouble();
    int n = bb.getInt();

    int compLen = bb.getInt();
    bb.getInt(); // compCrc (ignored in V1)

    if (n < 2) {
      throw new OrbitlabException("PV block too small (n=" + n + ")");
    }
    if (compLen < 0 || compLen > bb.remaining()) {
      throw new OrbitlabException("Invalid PV compressed length=" + compLen);
    }

    byte[] compressed = new byte[compLen];
    bb.get(compressed);

    int rawBytesLen = n * 6 * 8;
    byte[] rawBytes = Zstd.decompress(compressed, rawBytesLen);

    double[] raw = new double[n * 6];
    ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer().get(raw);

    return new PvBlock(t0, dt, n, raw);
  }

  private static RotBlock parseRotBlock(ByteBuffer chunk, int off, int len) {
    ByteBuffer bb = slice(chunk, off, len).order(ByteOrder.LITTLE_ENDIAN);

    int codecId = bb.getInt();
    if (codecId != 1) {
      throw new OrbitlabException("Unsupported ROT codecId=" + codecId);
    }

    double t0 = bb.getDouble();
    double dt = bb.getDouble();
    int n = bb.getInt();

    int compLen = bb.getInt();
    bb.getInt(); // compCrc (ignored in V1)

    if (n < 2) {
      throw new OrbitlabException("ROT block too small (n=" + n + ")");
    }
    if (compLen < 0 || compLen > bb.remaining()) {
      throw new OrbitlabException("Invalid ROT compressed length=" + compLen);
    }

    byte[] compressed = new byte[compLen];
    bb.get(compressed);

    int rawBytesLen = n * 4 * 8;
    byte[] rawBytes = Zstd.decompress(compressed, rawBytesLen);

    double[] raw = new double[n * 4];
    ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer().get(raw);

    return new RotBlock(t0, dt, n, raw);
  }

  private static ByteBuffer slice(ByteBuffer parent, int off, int len) {
    ByteBuffer dup = parent.duplicate();
    dup.position(off);
    dup.limit(off + len);
    return dup.slice();
  }

  private static final class DecodedChunk {
    private final SolarSystemBody body;
    private final double chunkStartOffsetSeconds;
    private final double chunkDurationSeconds;

    private final PvBlock pv;
    private final RotBlock rot;

    DecodedChunk(
        SolarSystemBody body,
        double chunkStartOffsetSeconds,
        double chunkDurationSeconds,
        PvBlock pv,
        RotBlock rot) {
      this.body = body;
      this.chunkStartOffsetSeconds = chunkStartOffsetSeconds;
      this.chunkDurationSeconds = chunkDurationSeconds;
      this.pv = pv;
      this.rot = rot;
    }

    PVCoordinates samplePv(double offsetSeconds) {
      int i0 = (int) Math.floor((offsetSeconds - pv.t0) / pv.dt);
      i0 = clamp(i0, 0, pv.n - 2);
      int i1 = i0 + 1;

      double t0 = pv.t0 + i0 * pv.dt;
      double tau = (offsetSeconds - t0) / pv.dt;

      Vector3D p0 = vec3(pv.raw, i0 * 6);
      Vector3D v0 = vec3(pv.raw, i0 * 6 + 3);
      Vector3D p1 = vec3(pv.raw, i1 * 6);
      Vector3D v1 = vec3(pv.raw, i1 * 6 + 3);

      Vector3D p = hermitePosition(p0, v0, p1, v1, pv.dt, tau);
      Vector3D v = hermiteVelocity(p0, v0, p1, v1, pv.dt, tau);

      return new PVCoordinates(p, v);
    }

    Rotation sampleRot(double offsetSeconds) {
      int i0 = (int) Math.floor((offsetSeconds - rot.t0) / rot.dt);
      i0 = clamp(i0, 0, rot.n - 2);
      int i1 = i0 + 1;

      double t0 = rot.t0 + i0 * rot.dt;
      double tau = (offsetSeconds - t0) / rot.dt;

      Rotation r0 = quat(rot.rawQuat, i0 * 4);
      Rotation r1 = quat(rot.rawQuat, i1 * 4);

      return slerp(r0, r1, tau);
    }

    private static Vector3D vec3(double[] a, int off) {
      return new Vector3D(a[off], a[off + 1], a[off + 2]);
    }

    private static Rotation quat(double[] a, int off) {
      return new Rotation(a[off], a[off + 1], a[off + 2], a[off + 3], true);
    }

    private static int clamp(int x, int lo, int hi) {
      if (x < lo) return lo;
      if (x > hi) return hi;
      return x;
    }

    private static Vector3D hermitePosition(
        Vector3D p0, Vector3D v0, Vector3D p1, Vector3D v1, double dt, double t) {
      double t2 = t * t;
      double t3 = t2 * t;

      double h00 = 2 * t3 - 3 * t2 + 1;
      double h10 = t3 - 2 * t2 + t;
      double h01 = -2 * t3 + 3 * t2;
      double h11 = t3 - t2;

      return new Vector3D(h00, p0, h10 * dt, v0, h01, p1, h11 * dt, v1);
    }

    private static Vector3D hermiteVelocity(
        Vector3D p0, Vector3D v0, Vector3D p1, Vector3D v1, double dt, double t) {
      double t2 = t * t;

      double dh00 = 6 * t2 - 6 * t;
      double dh10 = 3 * t2 - 4 * t + 1;
      double dh01 = -6 * t2 + 6 * t;
      double dh11 = 3 * t2 - 2 * t;

      double invDt = 1.0 / dt;

      return new Vector3D(dh00 * invDt, p0, dh10, v0, dh01 * invDt, p1, dh11, v1);
    }

    private static Rotation slerp(Rotation r0, Rotation r1, double t) {
      if (t <= 0.0) return r0;
      if (t >= 1.0) return r1;

      double q00 = r0.getQ0(), q01 = r0.getQ1(), q02 = r0.getQ2(), q03 = r0.getQ3();
      double q10 = r1.getQ0(), q11 = r1.getQ1(), q12 = r1.getQ2(), q13 = r1.getQ3();

      double dot = q00 * q10 + q01 * q11 + q02 * q12 + q03 * q13;
      if (dot < 0.0) {
        dot = -dot;
        q10 = -q10;
        q11 = -q11;
        q12 = -q12;
        q13 = -q13;
      }

      if (dot > 0.9995) {
        double a = 1.0 - t;
        double b = t;
        double s0 = a * q00 + b * q10;
        double s1 = a * q01 + b * q11;
        double s2 = a * q02 + b * q12;
        double s3 = a * q03 + b * q13;
        return new Rotation(s0, s1, s2, s3, true);
      }

      double theta0 = Math.acos(dot);
      double sinTheta0 = Math.sin(theta0);

      double theta = theta0 * t;
      double sinTheta = Math.sin(theta);

      double sA = Math.sin(theta0 - theta) / sinTheta0;
      double sB = sinTheta / sinTheta0;

      double s0 = sA * q00 + sB * q10;
      double s1 = sA * q01 + sB * q11;
      double s2 = sA * q02 + sB * q12;
      double s3 = sA * q03 + sB * q13;

      return new Rotation(s0, s1, s2, s3, true);
    }
  }

  private static final class LruCache<K, V> {
    private final int maxSize;
    private final LinkedHashMap<K, V> map;

    LruCache(int maxSize) {
      this.maxSize = maxSize;
      this.map =
          new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
              return size() > LruCache.this.maxSize;
            }
          };
    }

    V get(K k) {
      return map.get(k);
    }

    boolean containsKey(K k) {
      return map.containsKey(k);
    }

    void put(K k, V v) {
      map.put(k, v);
    }
  }
}
