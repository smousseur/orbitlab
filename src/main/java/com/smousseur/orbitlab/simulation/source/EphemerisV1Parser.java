package com.smousseur.orbitlab.simulation.source;

import com.github.luben.zstd.Zstd;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Binary format parser for V1 ephemeris dataset files. */
final class EphemerisV1Parser {

  static final byte[] MAGIC = new byte[] {'O', 'R', 'B', 'L', '_', 'E', 'P', 'H'};

  // Header is written with LittleEndianWriter(512) => fixed 512 bytes V1.
  static final int HEADER_SIZE_BYTES = 512;

  // Chunk header fixed size in writer
  static final int CHUNK_HEADER_SIZE = 40;

  // Index entry: f64 startOffset + u64 fileOffset + u32 byteLen + u32 crc = 24 bytes
  static final int INDEX_ENTRY_SIZE = 24;

  private EphemerisV1Parser() {}

  /**
   * Parsed header from a V1 ephemeris dataset file.
   *
   * @param chunkDurationSeconds the duration each chunk covers in seconds
   * @param chunkCount the total number of chunks in the file
   * @param chunkIndexOffset the byte offset of the chunk index within the file
   */
  record HeaderV1(double chunkDurationSeconds, int chunkCount, long chunkIndexOffset) {}

  /**
   * Parsed chunk index from a V1 ephemeris dataset file, providing the file offset and byte length
   * of each chunk.
   *
   * @param fileOffsets the byte offset of each chunk within the file
   * @param byteLengths the byte length of each chunk
   */
  record IndexV1(long[] fileOffsets, int[] byteLengths) {}

  /**
   * Decoded position/velocity data block from a chunk.
   *
   * <p>The {@code raw} array stores interleaved position and velocity components as
   * {@code [px, py, pz, vx, vy, vz]} for each of the {@code n} samples.
   *
   * @param t0 the start time offset of this block in seconds from the dataset epoch
   * @param dt the time step between consecutive samples in seconds
   * @param n the number of samples in this block
   * @param raw the flat array of interleaved position and velocity values (length {@code n * 6})
   */
  record PvBlock(double t0, double dt, int n, double[] raw) {}

  /**
   * Decoded rotation data block from a chunk.
   *
   * <p>The {@code rawQuat} array stores quaternion components as {@code [q0, q1, q2, q3]} for
   * each of the {@code n} samples.
   *
   * @param t0 the start time offset of this block in seconds from the dataset epoch
   * @param dt the time step between consecutive samples in seconds
   * @param n the number of samples in this block
   * @param rawQuat the flat array of quaternion components (length {@code n * 4})
   */
  record RotBlock(double t0, double dt, int n, double[] rawQuat) {}

  /**
   * Reads and validates the V1 header from the beginning of an ephemeris dataset file.
   *
   * <p>Validates the magic bytes, format version, body identity, time scale, and structural
   * integrity of the header fields.
   *
   * @param expectedBody the body this file is expected to describe (used for validation)
   * @param ch the file channel positioned at or near the start of the file
   * @param path the file path (used in error messages)
   * @return the parsed header
   * @throws IOException if an I/O error occurs during reading
   * @throws OrbitlabException if the header is invalid or does not match expectations
   */
  static HeaderV1 readHeaderV1(SolarSystemBody expectedBody, FileChannel ch, Path path)
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

  /**
   * Reads the chunk index from the file, using the offset and count from the given header.
   *
   * @param ch the file channel to read from
   * @param hdr the previously parsed header containing the index offset and chunk count
   * @return the parsed chunk index with file offsets and byte lengths for each chunk
   * @throws IOException if an I/O error occurs during reading
   * @throws OrbitlabException if the index data is truncated or invalid
   */
  static IndexV1 readIndexV1(FileChannel ch, HeaderV1 hdr) throws IOException {
    long indexSize = (long) hdr.chunkCount() * INDEX_ENTRY_SIZE;

    if (indexSize > Integer.MAX_VALUE) {
      throw new OrbitlabException("Chunk index too large: " + indexSize + " bytes");
    }

    ByteBuffer idx = ByteBuffer.allocate((int) indexSize).order(ByteOrder.LITTLE_ENDIAN);

    int read = 0;
    while (read < (int) indexSize) {
      int r = ch.read(idx, hdr.chunkIndexOffset() + read);
      if (r < 0) break;
      read += r;
    }
    if (read != (int) indexSize) {
      throw new OrbitlabException("Failed to read full chunk index");
    }

    idx.flip();

    long[] fileOffsets = new long[hdr.chunkCount()];
    int[] byteLengths = new int[hdr.chunkCount()];

    for (int i = 0; i < hdr.chunkCount(); i++) {
      idx.getDouble(); // startOffsetSeconds (unused in V1 reader)
      fileOffsets[i] = idx.getLong();
      byteLengths[i] = idx.getInt();
      idx.getInt(); // crc (unused in V1 reader)
    }

    return new IndexV1(fileOffsets, byteLengths);
  }

  /**
   * Parses a position/velocity data block from a chunk's byte buffer.
   *
   * <p>The block is Zstandard-compressed and is decompressed during parsing.
   *
   * @param chunk the byte buffer of the entire chunk
   * @param off the byte offset of the PV block within the chunk
   * @param len the byte length of the PV block
   * @return the decoded PV block
   * @throws OrbitlabException if the codec is unsupported or the data is invalid
   */
  static PvBlock parsePvBlock(ByteBuffer chunk, int off, int len) {
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

  /**
   * Parses a rotation data block from a chunk's byte buffer.
   *
   * <p>The block is Zstandard-compressed and is decompressed during parsing.
   *
   * @param chunk the byte buffer of the entire chunk
   * @param off the byte offset of the rotation block within the chunk
   * @param len the byte length of the rotation block
   * @return the decoded rotation block
   * @throws OrbitlabException if the codec is unsupported or the data is invalid
   */
  static RotBlock parseRotBlock(ByteBuffer chunk, int off, int len) {
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

  /**
   * Creates a new byte buffer that is a slice of the parent buffer at the specified offset and
   * length. The parent buffer's position and limit are not modified.
   *
   * @param parent the source byte buffer
   * @param off the starting offset within the parent
   * @param len the number of bytes to include in the slice
   * @return a new byte buffer sharing the parent's content at the given range
   */
  static ByteBuffer slice(ByteBuffer parent, int off, int len) {
    ByteBuffer dup = parent.duplicate();
    dup.position(off);
    dup.limit(off + len);
    return dup.slice();
  }

  /**
   * Sequential reader for a {@link FileChannel} that reads primitive values in little-endian byte
   * order. Each read advances the channel position by the number of bytes consumed.
   */
  static final class CountingReader {
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
}
