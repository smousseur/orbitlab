package com.smousseur.orbitlab.simulation.source;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class BodyFile implements Closeable {
  private final SolarSystemBody body;
  private final Path path;
  private final FileChannel ch;

  private final double chunkDurationSeconds;
  final int chunkCount;

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
      EphemerisV1Parser.HeaderV1 hdr = EphemerisV1Parser.readHeaderV1(body, ch, path);
      EphemerisV1Parser.IndexV1 idx = EphemerisV1Parser.readIndexV1(ch, hdr);

      return new BodyFile(
          body,
          path,
          ch,
          hdr.chunkDurationSeconds(),
          hdr.chunkCount(),
          idx.fileOffsets(),
          idx.byteLengths(),
          chunksInCache);
    } catch (Exception e) {
      try {
        ch.close();
      } catch (IOException suppressed) {
        e.addSuppressed(suppressed);
      }
      if (e instanceof IOException ioe) throw ioe;
      throw (RuntimeException) e;
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

    if (pvBlockOffset < EphemerisV1Parser.CHUNK_HEADER_SIZE
        || rotBlockOffset < EphemerisV1Parser.CHUNK_HEADER_SIZE) {
      throw new OrbitlabException(
          "Invalid block offsets in chunk " + chunkId + " for " + body + " (" + path + ")");
    }
    if (pvBlockOffset + pvBlockLen > chunkBytes.length
        || rotBlockOffset + rotBlockLen > chunkBytes.length) {
      throw new OrbitlabException(
          "Invalid block lengths in chunk " + chunkId + " for " + body + " (" + path + ")");
    }

    EphemerisV1Parser.PvBlock pv = EphemerisV1Parser.parsePvBlock(bb, pvBlockOffset, pvBlockLen);
    EphemerisV1Parser.RotBlock rot =
        EphemerisV1Parser.parseRotBlock(bb, rotBlockOffset, rotBlockLen);

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
